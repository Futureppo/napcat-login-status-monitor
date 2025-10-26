package com.napcat.monitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.napcat.monitor.NotificationHelper
import com.napcat.monitor.data.PrefKeys
import com.napcat.monitor.data.dataStore
import com.napcat.monitor.data.readMonitorsFlow
import com.napcat.monitor.data.saveMonitors
import com.napcat.monitor.data.MonitorItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request

class ApiStatusCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 先升为前台任务，创建临时通知
        setForeground(createForegroundInfo())

        val monitors = readMonitorsFlow(applicationContext).first()
        if (monitors.isEmpty()) return Result.success()

        val updated = mutableListOf<MonitorItem>()
        for (m in monitors) {
            if (!m.enabled) { updated.add(m); continue }
            val currentStatus = checkApi(m.apiUrl)
            if (currentStatus == "Offline" && m.lastStatus == "Online") {
                NotificationHelper.sendApiDownNotification(applicationContext)
            }
            updated.add(m.copy(lastStatus = currentStatus))
        }
        saveMonitors(applicationContext, updated)

        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.buildForegroundNotification(applicationContext)
        // 使用与通知无关的固定 ID，WorkManager 会管理 lifecycle
        return ForegroundInfo(1001, notification)
    }

    private suspend fun checkApi(url: String): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) "Online" else "Offline"
            }
        } catch (t: Throwable) {
            "Offline"
        }
    }
}


