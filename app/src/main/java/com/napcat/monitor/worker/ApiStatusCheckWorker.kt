package com.napcat.monitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.napcat.monitor.NotificationHelper
import com.napcat.monitor.data.readMonitorsFlow
import com.napcat.monitor.data.saveMonitors
import com.napcat.monitor.data.MonitorItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.pm.ServiceInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import java.security.MessageDigest
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ApiStatusCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 先升为前台任务，创建临时通知
        setForeground(createForegroundInfo())

        val monitorId = inputData.getString(KEY_MONITOR_ID)
        if (monitorId.isNullOrBlank()) {
            // 兼容旧逻辑：遍历所有
            val monitors = readMonitorsFlow(applicationContext).first()
            if (monitors.isEmpty()) return Result.success()
            val updatedAll = mutableListOf<MonitorItem>()
            for (m in monitors) {
                if (!m.enabled) { updatedAll.add(m); continue }
                val result = checkApi(m.apiUrl, m.token)
                val currentStatus = if (result.first == true) "Online" else "Offline"
                if (currentStatus == "Offline" && m.lastStatus == "Online") {
                    NotificationHelper.sendApiDownNotification(applicationContext)
                }
                updatedAll.add(m.copy(lastStatus = currentStatus))
            }
            saveMonitors(applicationContext, updatedAll)
            return Result.success()
        } else {
            // 单监控模式：只处理指定监控并按其 intervalSec 重新调度
            val monitors = readMonitorsFlow(applicationContext).first()
            val current = monitors.firstOrNull { it.id == monitorId } ?: return Result.success()
            if (!current.enabled) {
                // 已关闭则不再调度
                return Result.success()
            }
            val result = checkApi(current.apiUrl, current.token)
            val status = if (result.first == true) "Online" else "Offline"
            if (status == "Offline" && current.lastStatus == "Online") {
                NotificationHelper.sendApiDownNotification(applicationContext)
            }
            val updated = monitors.map { m -> if (m.id == monitorId) m.copy(lastStatus = status) else m }
            saveMonitors(applicationContext, updated)

            // 重新按 intervalSec 调度下一次
            val delaySec = if (current.intervalSec > 0) current.intervalSec.toLong() else 60L
            val next = OneTimeWorkRequestBuilder<ApiStatusCheckWorker>()
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_MONITOR_ID to monitorId))
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                uniqueNameFor(monitorId),
                ExistingWorkPolicy.REPLACE,
                next
            )
            return Result.success()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.buildForegroundNotification(applicationContext)
        // 使用与通知无关的固定 ID，WorkManager 会管理 lifecycle
        return ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun checkApi(baseUrl: String, token: String): Pair<Boolean?, String?> = withContext(Dispatchers.IO) {
        try {
            val client = com.napcat.monitor.network.HttpClientProvider.client
            val credential = loginAndGetCredential(client, baseUrl, token)
            if (credential.isNullOrBlank()) return@withContext (false to null)
            val online = queryLoginInfoOnline(client, baseUrl, credential)
            return@withContext (online to null)
        } catch (t: Throwable) {
            false to null
        }
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith('/')) base.dropLast(1) else base
        val p = if (path.startsWith('/')) path else "/$path"
        return b + p
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // 登录获取 Base64 Credential
    private fun loginAndGetCredential(client: OkHttpClient, baseUrl: String, token: String): String? {
        val url = joinUrl(baseUrl, "/api/auth/login")
        val hash = sha256Hex(token + ".napcat")
        val media = "application/json; charset=utf-8".toMediaType()
        val body: RequestBody = ("{" + "\"hash\":\"" + hash + "\"}").toRequestBody(media)
        val req = Request.Builder()
            .url(url)
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
            return extractCredential(parsed)
        }
    }

    private fun extractCredential(root: JsonElement): String? {
        if (root is JsonObject) {
            // 可能顶层或 data 内包含 Credential
            val direct = root["Credential"]?.jsonPrimitive?.contentOrNull
            if (!direct.isNullOrBlank()) return direct
            val data = root["data"]
            if (data is JsonObject) {
                val inner = data["Credential"]?.jsonPrimitive?.contentOrNull
                if (!inner.isNullOrBlank()) return inner
            }
        }
        return null
    }

    // 查询在线状态，返回 true=在线 / false=离线 / null=未知
    private fun queryLoginInfoOnline(client: OkHttpClient, baseUrl: String, credentialB64: String): Boolean? {
        val url = joinUrl(baseUrl, "/api/QQLogin/GetQQLoginInfo")
        val body: RequestBody = "".toRequestBody(null)
        val req = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $credentialB64")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val text = resp.body?.string() ?: return null
            val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            val data = root["data"] as? JsonObject ?: return null
            val online = data["online"]?.jsonPrimitive?.booleanOrNull
            return online
        }
    }
}

private const val KEY_MONITOR_ID = "monitorId"
private fun uniqueNameFor(id: String) = "api_monitor_" + id


