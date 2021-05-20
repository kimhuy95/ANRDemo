package com.abc.anrdemo

import android.app.Application
import android.content.res.Configuration
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val builder = VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .penaltyDeath()

        StrictMode.setVmPolicy(builder.build())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("ggwp", "onConfigurationChanged from app")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.d("ggwp", "onLowMemory from app")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("ggwp", "onTerminate")
    }
}