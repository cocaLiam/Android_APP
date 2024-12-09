package com.example.cocaBot.webViewModules

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

class HybridAppBridge(private val webView: WebView) {

    /**
     * WebView 초기화
     * Web(client) -> APP(server) WebView 설정
     */
    // Member value
    private val BRIDGE_LOG_TAG = " - HybridAppBridge"
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
    fun resConnect(vararg pairs: Pair<String, Any?>) {
//        // 키 검사
//        val keys = pairs.map { it.first } // pairs에서 key만 추출
//        if (!keys.contains("macAddress") || !keys.contains("deviceName")) {
//            Log.e("KeyCheck", "macAddress 또는 deviceName 키가 누락되었습니다.")
//            throw IllegalArgumentException("macAddress 또는 deviceName 키가 필요합니다.")
//        }

        // 필수 키 목록
        val requiredKeys = setOf("macAddress", "deviceName")

        // 키 검사
        val keys = pairs.map { it.first }.toSet() // Set으로 변환
        if (!keys.containsAll(requiredKeys)) {
            Log.e("KeyCheck", "macAddress 또는 deviceName 키가 누락되었습니다.")
            throw IllegalArgumentException("macAddress 또는 deviceName 키가 필요합니다.")
        }
        val jsonObject = JSONObject()
        for ((key, value) in pairs) {
            jsonObject.put(key, value)
        }
        val functionName = object {}.javaClass.enclosingMethod?.name
        webView.post {
            Log.d("DEBUG", "Sending JSON to JS: $jsonObject") // 디버깅 로그 추가
            webView.evaluateJavascript("javascript:$functionName(${jsonObject})") { result ->
                Log.d("BRIDGE_LOG_TAG", "Result from JavaScript: $result")
            }
        }
    }

    /**
====================================================================================================
     * Web(client) -> APP(server) API 호출
     * Web에서 App으로 데이터를 전달받는 인터페이스
     */
    class WebAppInterface(private val context: Context) {

        @JavascriptInterface
        fun andShowToast(message: String) {
            // Web에서 전달받은 메시지를 Toast로 표시
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        @JavascriptInterface
        fun logMessage(message: String) {
            // Web에서 전달받은 메시지를 로그로 출력
            Log.d(" - HybridAppBridge", message)
        }
    }

    /**
====================================================================================================
     * WebViewClient 커스터마이징
     */
    private class CustomWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            // 모든 URL을 WebView에서 처리
            return false  // return true 면 외부 앱에서 처리
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            Log.d(" - HybridAppBridge", "쿠키: $cookies")
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            Log.d("CustomWebViewClient", "리소스 로드: $url")
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e("CustomWebViewClient", "에러 발생: $description ($failingUrl)")
            // 사용자에게 에러 메시지 표시
            Toast.makeText(view?.context, "페이지 로딩 실패: $description", Toast.LENGTH_SHORT).show()
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.e("CustomWebViewClient", "HTTP 에러 발생: ${errorResponse?.statusCode}")
            // HTTP 에러 처리 (예: 404 페이지 표시)
        }

    }
}
