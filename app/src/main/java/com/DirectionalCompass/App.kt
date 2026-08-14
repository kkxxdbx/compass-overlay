package com.DirectionalCompass

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 兜底初始化设置存取：系统在冷启动后因 START_STICKY 恢复
        // OverlayService 时不会经过 MainActivity，此处不初始化会导致崩溃
        Prefs.init(this)
    }
}
