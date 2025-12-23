package com.flux.recorder

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FluxRecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
