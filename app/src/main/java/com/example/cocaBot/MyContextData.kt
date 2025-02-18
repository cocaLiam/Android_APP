package com.example.cocaBot

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
//import com.example.cocaBot.bleModules.BleDeviceInfo
import java.util.concurrent.ConcurrentHashMap

//// 비동기 전용 라이브러리
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel


class MyContextData : Application() {
    companion object {
        lateinit var instance: MyContextData
            private set
        val handler = Handler(Looper.getMainLooper())  // 전역 handler 추가
//        val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

//    var bluetoothGattMap: ConcurrentHashMap<String, BleDeviceInfo> = ConcurrentHashMap()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

//    override fun onTerminate() {
//        super.onTerminate()
////        applicationScope.cancel()
//    }
}

