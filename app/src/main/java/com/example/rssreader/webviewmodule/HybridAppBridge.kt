package com.example.rssreader.webviewmodule

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.Toast
import org.json.JSONObject

data class DeviceInfo(
    var macAddress: String = "",
    var deviceName: String = ""
)

class HybridAppBridge(private val webView: WebView) {

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

        // WebView 클라이언트 설정
        webView.webViewClient = CustomWebViewClient()
        webView.webChromeClient = WebChromeClient()

        // WebView에 JavaScript 인터페이스 추가 [ 다른 webView 세팅이 끝나고 마지막에 해야함 ]
        webView.addJavascriptInterface(WebAppInterface(context), "AndroidInterface")
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

    fun resConnect(deviceInfo: DeviceInfo) {
        // JSON 객체 생성
        val jsonObject = makeJsonMsgProcess(deviceInfo)

        // 현재 함수 이름 가져오기
        val functionName = object {}.javaClass.enclosingMethod?.name ?: "unknownFunction"

        // WebView를 통해 JavaScript 함수 호출
        webView.post {
            val jsonString = jsonObject.toString() // JSON 객체를 문자열로 변환
            Log.d(BridgeLogTag, "Sending JSON to JS: $jsonString") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName($jsonString)") { result ->
                Log.d(BridgeLogTag, "Result from JavaScript: $result")
            }
        }
    }

    // JSON 객체 생성 로직
    private fun makeJsonMsgProcess(deviceInfo: DeviceInfo): JSONObject {
        val jsonObject = JSONObject()

        // DeviceInfo 데이터를 JSON 객체에 추가
        jsonObject.put("macAddress", deviceInfo.macAddress)
        jsonObject.put("deviceName", deviceInfo.deviceName)

        // 값이 빈 문자열인 경우 로그 출력
        val emptyValueKeys = mutableListOf<String>()
        if (deviceInfo.macAddress.isEmpty()) emptyValueKeys.add("macAddress")
        if (deviceInfo.deviceName.isEmpty()) emptyValueKeys.add("deviceName")

        if (emptyValueKeys.isNotEmpty()) {
            Log.w(BridgeLogTag, "해당 key의 Value 값이 '' >> $emptyValueKeys")
        }

        return jsonObject
    }

    /**
====================================================================================================
     * Web(client) -> APP(server) API 호출
     * Web에서 App으로 데이터를 전달받는 인터페이스
     */
    class WebAppInterface(private val context: Context) {
        private val BridgeLogTag = " - HybridAppBridge"
        @JavascriptInterface
        fun andShowToast(message: String) {
            // Web에서 전달받은 메시지를 Toast로 표시
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun logMessage(message: String) {
            // Web에서 전달받은 메시지를 로그로 출력
            Log.d(BridgeLogTag, message)
        }
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
