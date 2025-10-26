package com.napcat.monitor

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化通知渠道（应用启动时执行一次）
        NotificationHelper.ensureChannels(this)
    }
}


