package com.napcat.monitor.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// 统一 OkHttpClient 提供者，避免重复创建占用内存的连接池/线程池
object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}


