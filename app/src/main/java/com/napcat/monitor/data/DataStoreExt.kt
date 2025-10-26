package com.napcat.monitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// DataStore 扩展与键值定义（存储 API URL 与最后状态）
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PrefKeys {
    val API_URL = stringPreferencesKey("api_url")
    val LAST_STATUS = stringPreferencesKey("last_known_status")
    val MONITORS_JSON = stringPreferencesKey("monitors_json")
}

fun readApiUrlFlow(context: Context): Flow<String> =
    context.dataStore.data.map { it[PrefKeys.API_URL] ?: "" }

fun readLastStatusFlow(context: Context): Flow<String> =
    context.dataStore.data.map { it[PrefKeys.LAST_STATUS] ?: "Online" }

suspend fun saveApiUrl(context: Context, url: String) {
    context.dataStore.edit { it[PrefKeys.API_URL] = url }
}

suspend fun saveLastStatus(context: Context, status: String) {
    context.dataStore.edit { it[PrefKeys.LAST_STATUS] = status }
}

// 多监控：存/取 JSON 列表
fun readMonitorsFlow(context: Context): Flow<List<MonitorItem>> =
    context.dataStore.data.map {
        val raw = it[PrefKeys.MONITORS_JSON]
        if (raw.isNullOrBlank()) emptyList() else runCatching { Json.decodeFromString<List<MonitorItem>>(raw) }.getOrElse { emptyList() }
    }

suspend fun saveMonitors(context: Context, monitors: List<MonitorItem>) {
    val json = Json.encodeToString(monitors)
    context.dataStore.edit { it[PrefKeys.MONITORS_JSON] = json }
}


