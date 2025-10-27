package com.napcat.monitor.data

import kotlinx.serialization.Serializable

// 监控项数据模型
@Serializable
data class MonitorItem(
    val id: String,
    val apiUrl: String,
    val token: String,
    val uin: String, // 手动配置的 QQ 号
    val intervalSec: Int,
    val enabled: Boolean,
    val lastStatus: String = "Online",
    val lastUin: String? = null
)


