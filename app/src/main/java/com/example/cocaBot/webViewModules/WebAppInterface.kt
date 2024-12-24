package com.example.cocaBot.webViewModules
// Android 기본 패키지
import android.annotation.SuppressLint
import android.content.Context

// UI Pack
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import android.util.Log

// BLE Pack
import com.example.cocaBot.bleModules.BleController

// WebView Pack
import com.example.cocaBot.webViewModules.WebAppInterface

// DataClass Pack
import com.example.cocaBot.webViewModules.DeviceInfo
import com.example.cocaBot.webViewModules.DeviceList
import com.example.cocaBot.webViewModules.ReadData
import com.example.cocaBot.webViewModules.WriteData
import com.example.cocaBot.webViewModules.JsonValidationResult

// Util Pack
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.reflect.full.memberProperties
import kotlin.reflect.KProperty1

// Custom Package
import com.example.cocaBot.MainActivity

class WebAppInterface private constructor(
    webView: WebView,
    private val bleController: BleController,
    private val mainActivity: MainActivity,
) {
    private val webAppInterFaceTag = " - WebAppInterface"
    private val webViewRef = WeakReference(webView) // WebView를 WeakReference로 감싸기
//    private val context = WeakReference(context)

    companion object {
        @Volatile
        private var instance: WebAppInterface? = null

        fun initialize(
            webView: WebView,
            bleController: BleController,
            mainActivity: MainActivity,
        ): WebAppInterface {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = WebAppInterface(webView, bleController, mainActivity)
                    }
                }
            }
            return instance!!
        }

        fun getInstance(): WebAppInterface {
            return instance
                ?: throw IllegalStateException("WebAppInterface is not initialized, call initialize() method first.")
        }
    }

/**
====================================================================================================
 * Web(client) -> APP(server) API 호출
 * Web에서 App으로 데이터를 전달받는 인터페이스
 */
    @JavascriptInterface
    fun reqConnect() {
        // Web에서 전달받은 메시지를 Toast로 표시
        mainActivity.runOnUiThread {
            mainActivity.startBleScan()
        }
    }

    @JavascriptInterface
    fun reqRemoveParing(jsonString: String) {
        Log.i(webAppInterFaceTag,"reqRemoveParing UP")
        try {
            Log.d("jsonString : ",jsonString)
            // 전달된 JSON 문자열을 DeviceInfo 객체로 변환
            val gson = Gson()
            val deviceInfo: DeviceInfo = gson.fromJson(jsonString, DeviceInfo::class.java)

            if(bleController.removeParing(deviceInfo.macAddress)){
                resRemoveParing(deviceInfo)
            }else{
                deviceInfo.deviceName = ""
                deviceInfo.macAddress = ""
                resRemoveParing(deviceInfo)
            }
        } catch (e: Exception) {
            Log.e(webAppInterFaceTag, "reqRemoveParing JSON 변환 중 오류 발생: ${e.message}")
        }
        Log.i(webAppInterFaceTag,"reqRemoveParing Down")
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun reqParingInfo() {
        Log.i(webAppInterFaceTag,"reqParingInfo UP")
        val bleDeviceSet = bleController.getParingDevices()
        Log.i(webAppInterFaceTag,"bleDeviceSet $bleDeviceSet")
        val deviceInfoList = mutableListOf<DeviceInfo>()

        if (bleDeviceSet.isEmpty()){
            resParingInfo(DeviceList(deviceList=null))
            return
        }

        bleDeviceSet.forEach { bluetoothDevice ->
            val deviceInfo = DeviceInfo(
                deviceName = bluetoothDevice.name,
                macAddress = bluetoothDevice.address
            )
            Log.i(webAppInterFaceTag,"deviceInfo $deviceInfo")
            deviceInfoList.add(deviceInfo)
        }

        resParingInfo(DeviceList(deviceList = deviceInfoList))

        Log.i(webAppInterFaceTag,"reqParingInfo Down")
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun reqConnectedDevices() {
        Log.i(webAppInterFaceTag,"reqConnectedDevices UP")
        val bleDeviceList = bleController.getConnectedDevices()
        Log.i(webAppInterFaceTag,"bleDeviceList $bleDeviceList")
        val deviceInfoList = mutableListOf<DeviceInfo>()

        if (bleDeviceList.isEmpty()){
            resConnectedDevices(DeviceList(deviceList=null))
            return
        }

        bleDeviceList.forEach { bluetoothDevice ->
            val deviceInfo = DeviceInfo(
                deviceName = bluetoothDevice.name,
                macAddress = bluetoothDevice.address
            )
            deviceInfoList.add(deviceInfo)
        }

        resConnectedDevices(DeviceList(deviceList = deviceInfoList))

        Log.i(webAppInterFaceTag,"reqConnectedDevices Down")
    }

    @JavascriptInterface
    fun reqReadData(jsonString: String) {
        Log.i(webAppInterFaceTag, "reqReadData UP")
        try {
            // 전달된 JSON 문자열을 DeviceInfo 객체로 변환
            val gson = Gson()
            val deviceInfo: DeviceInfo = gson.fromJson(jsonString, DeviceInfo::class.java)

            // BLE Controller에서 데이터 읽기 요청
            bleController.requestReadData(deviceInfo.macAddress)

        } catch (e: Exception) {
            Log.e(webAppInterFaceTag, "reqReadData JSON 변환 중 오류 발생: ${e.message}")
        }
        Log.i(webAppInterFaceTag, "reqReadData Down")
    }

    @JavascriptInterface
    fun pubToasting(message: String) {
        // Web에서 전달받은 메시지를 Toast로 표시
        Toast.makeText(mainActivity, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun pubDisconnectAllDevice() {
        Log.i(webAppInterFaceTag,"pubDisconnectAllDevice UP")
        bleController.disconnectAllDevices()
        Log.i(webAppInterFaceTag,"pubDisconnectAllDevice Down")
    }

    @JavascriptInterface
    fun pubSendData(jsonString: String) {
        try {
            // 전달된 JSON 문자열을 로그로 출력
            Log.d(webAppInterFaceTag, "Received data: $jsonString")

            // Gson 객체 생성
            val gson = Gson()

            // JSON 문자열을 WriteData 객체로 변환
            val writeData: WriteData = gson.fromJson(jsonString, WriteData::class.java)
            Log.d(webAppInterFaceTag, "Changed data: $writeData")

            // WriteData 객체의 데이터 접근
            Log.d(webAppInterFaceTag, "DeviceInfo - MAC Address: ${writeData.deviceInfo.macAddress}")
            Log.d(webAppInterFaceTag, "DeviceInfo - Device Name: ${writeData.deviceInfo.deviceName}")
            Log.d(webAppInterFaceTag, "Message 1: ${writeData.msg}")
            Log.d(webAppInterFaceTag, "Type of writeData.msg: ${writeData.msg::class.java.name}")
            Log.d(webAppInterFaceTag, "Message 2: ${writeData.msg.keys}")
            Log.d(webAppInterFaceTag, "Message 3: ${writeData.msg.values}")
            Log.d(webAppInterFaceTag, "Message 4: ${writeData.msg[writeData.msg.keys.firstOrNull()]}")

            // 필요 시 JSONObject로 변환
            val jsonObject = JSONObject(jsonString)
            Log.d(webAppInterFaceTag, "JSONObject: $jsonObject")
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.e(webAppInterFaceTag, "Failed to parse JSON: " + e.message)
        }
        // Web에서 전달받은 메시지를 로그로 출력
        Log.d(webAppInterFaceTag, "pubSendData CALL , jsonString : $jsonString")
    }


    /**
====================================================================================================
 * APP(client) -> Web(server) 데이터 전달
 * 1. Response 타입 , 2. Subscribe 타입
 * 1. Response 타입 : Web(req) --> App(res) --> Web
 * 2. Subscribe 타입 : App(publish) --> Web
 */
    fun resConnect(dataToSend: DeviceInfo) {
        val jsonValidationResult: JsonValidationResult = makeJsonMsgProcess(dataToSend)
        // JSON 객체 생성
        val jsonObject = jsonValidationResult.jsonObject

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webViewRef.get()?.post {
            Log.d(webAppInterFaceTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webViewRef.get()?.evaluateJavascript("javascript:$functionName(${jsonObject})")
            { result ->
                Log.d(webAppInterFaceTag, "Result from JavaScript: $result")
            }
        }
    }

    fun resRemoveParing(dataToSend: DeviceInfo) {
        val jsonValidationResult: JsonValidationResult = makeJsonMsgProcess(dataToSend)
        // JSON 객체 생성
        val jsonObject = jsonValidationResult.jsonObject

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webViewRef.get()?.post {
            Log.d(webAppInterFaceTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webViewRef.get()?.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(webAppInterFaceTag, "Result from JavaScript: $result")
            }
        }
    }

    fun resParingInfo(dataToSend: DeviceList) {
        val jsonValidationResult: JsonValidationResult = makeJsonMsgProcess(dataToSend)
        // JSON 객체 생성
        val jsonObject = jsonValidationResult.jsonObject

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webViewRef.get()?.post {
            Log.d(webAppInterFaceTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webViewRef.get()?.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(webAppInterFaceTag, "Result from JavaScript: $result")
            }
        }
    }

    fun resConnectedDevices(dataToSend: DeviceList) {
        val jsonValidationResult: JsonValidationResult = makeJsonMsgProcess(dataToSend)
        // JSON 객체 생성
        val jsonObject = jsonValidationResult.jsonObject

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webViewRef.get()?.post {
            Log.d(webAppInterFaceTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webViewRef.get()?.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(webAppInterFaceTag, "Result from JavaScript: $result")
            }
        }
    }

    fun resReadData(dataToSend: ReadData) {
        val jsonValidationResult: JsonValidationResult = makeJsonMsgProcess(dataToSend)
        // JSON 객체 생성
        val jsonObject = jsonValidationResult.jsonObject

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webViewRef.get()?.post {
            Log.d(webAppInterFaceTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webViewRef.get()?.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(webAppInterFaceTag, "Result from JavaScript: $result")
            }
        }
    }

    // TODO : Observe 기능 이용해서 값이 바뀌면 자동으로 App -> Web 쏘는 기능
    fun subObserveData(dataToSend: Map<String,String>){
        // 사용방법 : webAppInterface.subObserveData(mapOf("aa" to "bb"))
        // Gson 객체 생성
        val gson = Gson()

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webViewRef.get()?.post {
            // data class -> JSON 변환
            val jsonString = gson.toJson(dataToSend)

            Log.d(webAppInterFaceTag, "Call JS function: $functionName($jsonString)") // 디버깅 로그 추가
            webViewRef.get()?.evaluateJavascript("javascript:$functionName($jsonString)")
            { result ->
                Log.d(webAppInterFaceTag, "Result from JavaScript: $result")
            }
        }
    }

    // JSON 객체 생성 로직
    private fun makeJsonMsgProcess(dataToSend: Any): JsonValidationResult {
        val gson = Gson()
        val jsonObject = JSONObject()
        val emptyValueKeys = mutableListOf<String>()
        val missingKeys = mutableListOf<String>()

        try {
            // data class -> JSON 문자열 변환
            val jsonString = gson.toJson(dataToSend)

            // JSON 문자열 -> JSONObject 변환
            val tempJsonObject = JSONObject(jsonString)

            // data class의 프로퍼티 가져오기 (Kotlin Reflection 사용)
            val dataClassProperties = dataToSend::class.memberProperties
                .filterIsInstance<KProperty1<Any, *>>()
                .map { it.name }

            // 빈 값 검사 및 누락된 키 확인
            val jsonKeys = tempJsonObject.keys().asSequence().toSet()
            for (key in dataClassProperties) {
                if (!jsonKeys.contains(key)) {
                    missingKeys.add(key)
                } else {
                    val value = tempJsonObject.opt(key)
                    if (value == null|| (value is String && value.isEmpty()) ||
                        (value is Collection<*> && value.isEmpty()) ||
                        (value is Map<*, *> && value.isEmpty())) {
                        emptyValueKeys.add(key)
                    }
                }
            }

            if(missingKeys.isNotEmpty()) Log.e(webAppInterFaceTag, "Missing Keys: $missingKeys")
            if(emptyValueKeys.isNotEmpty()) Log.e(webAppInterFaceTag, "Empty Value Keys: $emptyValueKeys")
            // 최종적으로 JSON 객체 반환
            for (key in tempJsonObject.keys()) {
                jsonObject.put(key, tempJsonObject.get(key))
            }
        } catch (e: Exception) {
//            throw IllegalArgumentException("JSON 변환 중 오류 발생: ${e.message}", e)
            Log.e(webAppInterFaceTag, "JSON 변환 중 오류 발생: ${e.message}")
        }

        // 결과 반환
        return JsonValidationResult(jsonObject, emptyValueKeys, missingKeys)
    }
}
