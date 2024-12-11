package com.example.cocaBot.webViewModules

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.cocaBot.MainActivity
import com.example.cocaBot.bleModules.BleController
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject


data class DeviceInfo(
    var macAddress: String = "",
    var deviceName: String = ""
)

data class ReadData(
    var deviceInfo: DeviceInfo,
    var msg: Map<String, Any> // msg를 JSON 타입으로 변경
)

data class WriteData(
    var deviceInfo: DeviceInfo,
    var msg: Map<String, Any> // msg를 JSON 타입으로 변경
)

data class DeviceList(
    val deviceList: MutableList<DeviceInfo>
)

data class JsonValidationResult(
    val jsonObject: JSONObject,
    val emptyValueKeys: List<String>,
    val missingKeys: List<String>
)


class HybridAppBridge(private val webView: WebView, private val bleController: BleController) {

    /**
     * WebView 초기화
     * Web(client) -> APP(server) WebView 설정
     */
    // Member value
    private val BridgeLogTag = " - HybridAppBridge"
    fun initializeWebView(context: Context) {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // JavaScript 활성화
        webSettings.domStorageEnabled = true // DOM storage 활성화 // 로컬 스토리지 사용( Token 때문에 필요함 )
        webSettings.allowFileAccess = true // 파일 접근 허용
        webSettings.allowContentAccess = true
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true // JavaScript로 새 창 열기 허용
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE

        // WebView 클라이언트 설정
        webView.webViewClient = CustomWebViewClient()
        webView.webChromeClient = WebChromeClient()

        // WebView에 JavaScript 인터페이스 추가 [ 다른 webView 세팅이 끝나고 마지막에 해야함 ]
        webView.addJavascriptInterface(WebAppInterface(context, this, bleController), "AndroidInterface")
    }

    /**
     * 특정 URL 로드
     * @param url 로드할 URL
     */
    fun loadUrl(url: String) {
        webView.post {
            webView.loadUrl(url)
        }
    }

    /**
     * WebView 뒤로 가기
     */
    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }


    /**
    ====================================================================================================
     * Web(client) -> APP(server) API 호출
     * Web에서 App으로 데이터를 전달받는 인터페이스
     */
    class WebAppInterface(
        private val context: Context,
        private val hybridAppBridge: HybridAppBridge,
        private val bleController: BleController
    ) {
        private val BridgeLogTag = " - HybridAppBridge"

        @JavascriptInterface
        fun logMessage(message: String) {
            // Web에서 전달받은 메시지를 로그로 출력
            Log.d(BridgeLogTag, message)
        }

        @JavascriptInterface
        fun pubToasting(message: String) {
            // Web에서 전달받은 메시지를 Toast로 표시
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun pubDisconnectAllDevice() {
            // Web에서 전달받은 메시지를 Toast로 표시
            Log.i(BridgeLogTag,"pubDisconnectAllDevice CALL")
        }

        @JavascriptInterface
        fun pubSendData(dataFromWeb:String) {
            try {
                // 전달된 JSON 문자열을 로그로 출력
                Log.d(BridgeLogTag, "Received data: $dataFromWeb")

                // Gson 객체 생성
                val gson = Gson()

                // JSON 문자열을 WriteData 객체로 변환
                val writeData: WriteData = gson.fromJson(dataFromWeb, WriteData::class.java)
                Log.d(BridgeLogTag, "Changed data: $writeData")

                // WriteData 객체의 데이터 접근
                Log.d(BridgeLogTag, "DeviceInfo - MAC Address: ${writeData.deviceInfo.macAddress}")
                Log.d(BridgeLogTag, "DeviceInfo - Device Name: ${writeData.deviceInfo.deviceName}")
                Log.d(BridgeLogTag, "Message 1: ${writeData.msg}")
                Log.d(BridgeLogTag, "Message 2: ${writeData.msg.keys}")
                Log.d(BridgeLogTag, "Message 3: ${writeData.msg.values}")
                Log.d(BridgeLogTag, "Message 4: ${writeData.msg[writeData.msg.keys.firstOrNull()]}")

                // 필요 시 JSONObject로 변환
                val jsonObject = JSONObject(dataFromWeb)
                Log.d(BridgeLogTag, "JSONObject: $jsonObject")
            } catch (e: JSONException) {
                e.printStackTrace()
                Log.e(BridgeLogTag, "Failed to parse JSON: " + e.message)
            }
            // Web에서 전달받은 메시지를 로그로 출력
            Log.d(BridgeLogTag, "pubSendData CALL , dataFromWeb : $dataFromWeb")
        }

        @JavascriptInterface
        fun reqConnect() {
            // Web에서 전달받은 메시지를 Toast로 표시
            Log.i(BridgeLogTag,"pubDisconnectAllDevice CALL")

//            hybridAppBridge.resConnect()
        }
    }

    /**
====================================================================================================
     * APP(client) -> Web(server) 데이터 전달
     * 1. Response 타입 , 2. Subscribe 타입
     * 1. Response 타입 : Web(req) --> App(res) --> Web
     * 2. Subscribe 타입 : App(publish) --> Web
     */
    fun sendDataToWeb(jsFunctionName: String, sendingDataToWeb: JSONObject) {
        webView.post {
            // JSON 문자열을 안전하게 이스케이프 처리
            webView.evaluateJavascript("javascript:$jsFunctionName('$sendingDataToWeb')",
                //                            javascript:#JS화살표 함수명#(#####함수 인자값#####)
                { result ->
                    Log.d(BridgeLogTag, "Result from JavaScript: $result")
                })
        }
    }

    fun resConnect(dataToSend: DeviceInfo) {
        val jsonValidationResult: JsonValidationResult = makeJsonMsgProcess(dataToSend)
        // JSON 객체 생성
        val jsonObject = jsonValidationResult.jsonObject

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webView.post {
            Log.d(BridgeLogTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName(${jsonObject})")
            { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
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
        webView.post {
            Log.d(BridgeLogTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
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
        webView.post {
            Log.d(BridgeLogTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
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
        webView.post {
            Log.d(BridgeLogTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
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
        webView.post {
            Log.d(BridgeLogTag, "Call JS function: $functionName($jsonObject)") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName($jsonObject)")
            { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
            }
        }
    }

    // 이 Data 는 Key 와 Value 가 어떤식으로든 가기 때문에 키 검사나 검증 과정이 없음
    fun subObserveData(dataToSend: Map<String,String>){
        // Gson 객체 생성
        val gson = Gson()

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webView.post {
            // data class -> JSON 변환
            val jsonString = gson.toJson(dataToSend)
//
//            // JSON 문자열을 다시 JSONObject로 변환
//            val jsonObject = JSONObject(jsonString)
//
//            // gson 으로 Json 변환시, '"nameValuePairs" : JsonObject' 으로 변환함
//            if (jsonObject.has("nameValuePairs")) {
//                jsonString = jsonObject.getJSONObject("nameValuePairs").toString()
//            }
            Log.d(BridgeLogTag, "Call JS function: $functionName($jsonString)") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName($jsonString)")
            { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
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
            val dataClassProperties = dataToSend::class.members
                .filterIsInstance<kotlin.reflect.KProperty1<Any, *>>()
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

            // 최종적으로 JSON 객체 반환
            for (key in tempJsonObject.keys()) {
                jsonObject.put(key, tempJsonObject.get(key))
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON 변환 중 오류 발생: ${e.message}", e)
        }

        // 결과 반환
        return JsonValidationResult(jsonObject, emptyValueKeys, missingKeys)
    }

    /**
====================================================================================================
     * WebViewClient 커스터마이징
     */
    private class CustomWebViewClient : WebViewClient() {
        private val BridgeLogTag = " - HybridAppBridge"
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            // 모든 URL을 WebView에서 처리
            return false  // return true 면 외부 앱에서 처리
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            Log.d(BridgeLogTag, "쿠키: $cookies")
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            Log.d(BridgeLogTag, "리소스 로드: $url")
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e(BridgeLogTag, "에러 발생: $description ($failingUrl)")
            // 사용자에게 에러 메시지 표시
            Toast.makeText(view?.context, "페이지 로딩 실패: $description", Toast.LENGTH_SHORT).show()
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.e(BridgeLogTag, "HTTP 에러 발생: ${errorResponse?.statusCode}")
            // HTTP 에러 처리 (예: 404 페이지 표시)
        }

    }
}
