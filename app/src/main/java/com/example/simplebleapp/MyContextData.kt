package com.example.simplebleapp

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class MyContextData : Application() {
    companion object {
        lateinit var instance: MyContextData
            private set
        val handler = Handler(Looper.getMainLooper())  // 전역 handler 추가
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

