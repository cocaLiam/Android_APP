package com.example.cocaBot.webViewModules

import org.json.JSONObject

data class DeviceInfo(
    var macAddress: String = "",
    var deviceName: String = ""
)

data class DeviceList(
    // deviceList = null 인 경우 , 아무 기기도 없음을 의미
    val deviceList: MutableList<DeviceInfo>?
)

data class ReadData(
    var deviceInfo: DeviceInfo,
    var msg: Map<String, Any> // msg를 JSON 타입으로 변경
//    var msg: JSONObject // msg를 JSON 타입으로 변경  // gson.fromJson 하는 과정에서 JSONObject
)

data class WriteData(
    var deviceInfo: DeviceInfo,
    var msg: Map<String, Any> // msg를 JSON 타입으로 변경
//    var msg: JSONObject // msg를 JSON 타입으로 변경
)

data class JsonValidationResult(
    val jsonObject: JSONObject,
    val emptyValueKeys: List<String>,
    val missingKeys: List<String>
)