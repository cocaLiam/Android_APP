package com.example.cocaBot

import android.app.Application

class MyContextData : Application() {
    companion object {
        lateinit var instance: MyContextData
            private set
//        val handler = Handler(Looper.getMainLooper())  // 전역 handler 추가
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

