package com.compassoverlay

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics.init(this)
    }
}
