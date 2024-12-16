package com.example.cocaBot.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object LiveDataManager {
    // 내부에서만 수정 가능한 MutableLiveData
    private val _observeData = MutableLiveData<Map<String,String>>()

    // 외부에서 읽기만 가능한 LiveData
    val observeData: LiveData<Map<String,String>> get() = _observeData

    // 데이터 업데이트 함수
    fun updateObserveData(map: Map<String,String>) {
        _observeData.value = map
    }
}


// 사용법 :

//var tmp = 1
//debuggingButton.setOnClickListener {
//    startBleScan()
//    LiveDataManager.updateObserveData(mapOf("aa" to (tmp++).toString()))
//}
//
//// LiveData 관찰
///*LiveDataManager.updateObserveData(mapOf("aa" to "bb")) 로 업데이트 할 때 마다
//* webAppInterface.subObserveData(newData) 호출되서 APP -> WEB Data 전송
//*/
//LiveDataManager.observeData.observe(this, Observer { newData ->
//    // 데이터가 변경되면 UI 업데이트
//    webAppInterface.subObserveData(newData)
//})