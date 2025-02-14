package com.example.cocaBot.webViewModules

// Android 기본 패키지
import android.content.Context

// UI Pack
import android.widget.Toast

// BLE Pack
import com.example.cocaBot.bleModules.BleController

// WebView Pack
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

// DataClass Pack
import com.example.cocaBot.webViewModules.DeviceInfo
import com.example.cocaBot.webViewModules.DeviceList
import com.example.cocaBot.webViewModules.ReadData
import com.example.cocaBot.webViewModules.WriteData
import com.example.cocaBot.webViewModules.JsonValidationResult

// Util Pack
import android.util.Log
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject

// Custom Package
import com.example.cocaBot.MainActivity
import com.example.cocaBot.webViewModules.WebAppInterface

class HybridAppBridge(
    private val webView: WebView,
    private val bleController: BleController,
    private val mainActivity: MainActivity,
    private val webAppInterface: WebAppInterface
) {

    /**
     * WebView 초기화
     * Web(client) -> APP(server) WebView 설정
     */
    // Member value
    private val bridgeLogTag = " - HybridAppBridge"
    fun initializeWebView() {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // JavaScript 활성화 1
        webSettings.setJavaScriptEnabled(true); // JavaScript 활성화 2
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
        webView.addJavascriptInterface(webAppInterface, "AndroidInterface")
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
     * WebViewClient 커스터마이징
     */
    private class CustomWebViewClient : WebViewClient() {
        private val bridgeLogTag = " - HybridAppBridge"
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            // 모든 URL을 WebView에서 처리
            return false  // return true 면 외부 앱에서 처리
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = cookieManager.getCookie(url)
            Log.d(bridgeLogTag, "쿠키: $cookies")
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            Log.d(bridgeLogTag, "리소스 로드: $url")
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.e(bridgeLogTag, "에러 발생: $description ($failingUrl)")
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            Log.e(bridgeLogTag, "HTTP 에러 발생: ${errorResponse?.statusCode}")
            // HTTP 에러 처리 (예: 404 페이지 표시)
        }

    }
}
