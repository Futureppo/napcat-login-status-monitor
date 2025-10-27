package com.napcat.monitor.data

import kotlinx.serialization.Serializable

// 监控项数据模型（用于保存到 DataStore JSON）
@Serializable
data class MonitorItem(
    val id: String,
    val apiUrl: String,
    val token: String,
    val intervalSec: Int,
    val enabled: Boolean,
    val lastStatus: String = "Online",
    val lastUin: String? = null
)


