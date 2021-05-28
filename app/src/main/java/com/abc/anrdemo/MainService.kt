package com.abc.anrdemo

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.concurrent.Executors

class MainService : Service() {
    private val mediaProjectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    private var mediaRecorder: MediaRecorder? = null

    private var projection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        val notification = configureNotification()
        startForeground(222, notification)
    }

    private fun configureNotification(): Notification {
        createNotificationChannel(this)
        return NotificationCompat.Builder(applicationContext, "anr_channel_id")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "anr_channel"
            val descriptionText = "anr_channel_desc"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("anr_channel_id", name, importance).apply {
                description = descriptionText
                setSound(null, null)
                enableLights(false)
                lightColor = Color.BLUE
                enableVibration(false)

            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Start recording
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Executors.newSingleThreadExecutor().submit {
            intent?.let {
                val resultCode =
                    intent.getIntExtra(MainActivity.EXTRA_RESULT_CODE, Activity.RESULT_OK)
                val resultData = intent.getParcelableExtra<Intent>(MainActivity.EXTRA_RESULT_DATA)
                if (resultData != null) {
                    projection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                    /**
                     * Uncomment prepareMediaRecorder() and mediaRecorder?.start() to enable encoder + muxer
                     */
                    prepareMediaRecorder()
                    val surface = mediaRecorder?.surface ?: MediaCodec.createPersistentInputSurface()
                    configureMediaProjection(getFullScreenSize(), surface) // create virtual display to capture screen frame
                    mediaRecorder?.start()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        stopRecorder()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun stopRecorder() {
        try {
            projection?.stop()
            mediaRecorder?.stop()
            mediaRecorder?.release()
            projection = null
            mediaRecorder = null
        } catch (e: java.lang.Exception) {
        }
    }

    private fun configureMediaProjection(size: Size, surface: Surface) {
        projection?.createVirtualDisplay(
            getString(R.string.app_name),
            1,
            1,
            1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )
    }

    private fun prepareMediaRecorder(): Size {
        val outFile = createVideoFile()
        val fullScreenSize = getFullScreenSize()
        val screenWidth = fullScreenSize.width
        val screenHeight = fullScreenSize.height
        val screenRatio = screenWidth / screenHeight.toFloat()

        val candidateWidths = listOf(1920, 1440, 1280, 720, 640, 320)

        for (candidateWidth in candidateWidths) {
            val candidateHeight = (candidateWidth / screenRatio).toInt()
            val isValidCandidate = candidateWidth < screenWidth && candidateHeight < screenHeight
            if (isValidCandidate) {
                val width = ensureEvenSize(candidateWidth)
                val height = ensureEvenSize(candidateHeight)
                try {
                    mediaRecorder = createMediaRecorder()
                    mediaRecorder?.setOutputFile(outFile.path)
                    mediaRecorder?.setVideoSize(width, height)
                    mediaRecorder?.prepare()
                    return Size(width, height)
                } catch (e: Exception) {
                    try {
                        mediaRecorder?.release()
                    } catch (_: Exception) {
                    }

                    mediaRecorder = null
                    // try next candidate width
                }
            }
        }

        throw IllegalStateException("Fullscreen recorder: can not prepare media recorder")

    }

    private fun createMediaRecorder(): MediaRecorder {
        val canRecordAudio = false
        val audioChannels = 1
        val audioBitrate = 128000
        val audioSampleRate = 44100

        val videoFrameRate = 30
        val videoBitrate = 4 * 1000 * 1000

        return MediaRecorder().apply {
            if (canRecordAudio) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (canRecordAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(audioChannels)
                setAudioEncodingBitRate(audioBitrate)
                setAudioSamplingRate(audioSampleRate)
            }
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoFrameRate(videoFrameRate)
            setVideoEncodingBitRate(videoBitrate)
        }
    }

    private fun getFullScreenSize(): Size {
        /* Use DeviceManager to avoid android.os.strictmode.IncorrectContextUseViolation when StrictMode is enabled on API 30. */
        val display =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val displayManager =
                    getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            } else {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay
            }
        val size = Point()
        display.getRealSize(size)
        val width = ensureEvenSize(size.x)
        val height = ensureEvenSize(size.y)
        return Size(width, height)
    }

    private fun ensureEvenSize(size: Int): Int {
        return if (size % 2 == 0) {
            size
        } else {
            size - 1
        }
    }

    private fun createVideoFile() = File(getVideoDirectory(), "${generateVideoFilename()}.mp4")

    private fun getVideoDirectory(): File {
        val path =
            File("${Environment.getExternalStorageDirectory().absolutePath}/${getString(R.string.app_name)}")
        if (!path.exists()) {
            path.mkdirs()
        }
        return path
    }

    private fun generateVideoFilename(): String {
        val currentTime = System.currentTimeMillis()
        return "video_$currentTime"
    }
}