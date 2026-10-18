package com.par9uet.jm.retrofit

import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ApiHttpClients {
    val api = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    // 复用探测建立的连接与 TLS 配置，但不共享 Cookie 或业务拦截器。
    val probe = api.newBuilder()
        // 正式请求等待故障探测时，探测不能排在这些请求后面。
        .dispatcher(Dispatcher())
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
}
