package com.napcat.monitor

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化通知
        NotificationHelper.ensureChannels(this)
    }
}


