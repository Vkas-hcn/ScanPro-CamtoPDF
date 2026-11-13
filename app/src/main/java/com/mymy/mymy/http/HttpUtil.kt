package com.mymy.mymy.http

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


object HttpUtil {

    @Volatile // 关键：禁止指令重排序，保证多线程立即可见
    private var mClient: OkHttpClient? =null
    fun getClient(): OkHttpClient {
        // 第一次检查（不加锁，提升性能）
        var client = mClient
        if (client==null){
            synchronized(this) {
                // 第二次检查（加锁，确保线程安全）
                client = mClient
                if (client == null) {
                    val builder: OkHttpClient.Builder = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .callTimeout(55, TimeUnit.SECONDS)
                        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES)) // 默认就有连接池
                    client = builder.build()
                    mClient = client
                    return client
                } else {
                    return client
                }
            }
        }else {
            return client
        }
    }

}