package com.napcat.monitor

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.napcat.monitor.data.PrefKeys
import com.napcat.monitor.data.dataStore
import com.napcat.monitor.data.readMonitorsFlow
import com.napcat.monitor.data.saveMonitors
import com.napcat.monitor.data.MonitorItem
import com.napcat.monitor.worker.ApiStatusCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// 主界面的 ViewModel，注入 WorkManager 与 DataStore
class MainViewModel(
    private val app: Application,
    private val workManager: WorkManager,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _status = MutableStateFlow("Unknown")
    val status: StateFlow<String> = _status.asStateFlow()

    // 读取存储的 API URL（用于 Compose 显示）
    val apiUrlFlow = dataStore.data.map { it[PrefKeys.API_URL] ?: "" }
    val monitorsFlow = readMonitorsFlow(app)

    // 启动周期监控任务（15 分钟），并配置为加急工作（超额则按普通执行）
    fun startMonitoring() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ApiStatusCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "api_status_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // 停止周期监控任务
    fun stopMonitoring() {
        workManager.cancelUniqueWork("api_status_check")
    }

    // 将 DataStore 中的最后状态同步到 UI
    fun updateStatusFromStore() {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[PrefKeys.LAST_STATUS] ?: "Online" }
                .collect { _status.value = it }
        }
    }

    // ViewModel 工厂，负责注入依赖
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val wm = WorkManager.getInstance(app)
            val ds = app.dataStore
            return MainViewModel(app, wm, ds) as T
        }
    }

    // 添加或更新监控项，并启动周期任务
    fun addOrUpdateMonitor(id: String?, apiUrl: String, token: String, intervalSec: Int, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = readMonitorsFlow(app).first()
            val newId = id ?: java.util.UUID.randomUUID().toString()
            val updated = list.filter { it.id != newId } + MonitorItem(
                id = newId,
                apiUrl = apiUrl,
                token = token,
                intervalSec = intervalSec,
                enabled = enabled
            )
            saveMonitors(app, updated)
            // 始终确保调度已存在
            startMonitoring()
        }
    }
}


