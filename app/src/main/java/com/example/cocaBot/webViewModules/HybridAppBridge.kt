package com.example.cocaBot.webViewModules

// Android 기본 패키지
import android.app.Activity
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
import android.view.View
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject

// Custom Package
import com.example.cocaBot.MainActivity
import com.example.cocaBot.webViewModules.WebAppInterface

private fun webViewResume(tmp:WebView){
    tmp.onResume()
    tmp.resumeTimers()
}
private fun webViewCreate(context: Context, webViewId: Int): WebView{
    val tmp = (context as Activity).findViewById<WebView>(webViewId)
    return tmp
}

private fun webViewPause(tmp:WebView){
    tmp.onPause()
    tmp.pauseTimers()
}
private fun webViewDestroy(tmp:WebView){
    // WebView 완전 종료
    tmp.clearHistory()
    tmp.clearCache(true)
    tmp.loadUrl("about:blank")
    tmp.removeAllViews()
    tmp.destroyDrawingCache()
    tmp.destroy()
}

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

// 크롬 개발자 도구를 통한 원격 디버깅 활성화
        WebView.setWebContentsDebuggingEnabled(true)
// 자바스크립트 실행 허용 (두 방식 모두 동일한 기능)
        webView.settings.javaScriptEnabled = true
        webView.settings.setJavaScriptEnabled(true)
// 웹 스토리지(localStorage, sessionStorage) 사용 허용 - 토큰 저장 등에 필요
        webView.settings.domStorageEnabled = true
// 웹뷰에서 파일 시스템 접근 허용
        webView.settings.allowFileAccess = true
// 웹뷰에서 콘텐츠 프로바이더 접근 허용
        webView.settings.allowContentAccess = true
// file:// URL에서 다른 file:// URL 접근 허용
        webView.settings.allowFileAccessFromFileURLs = true
// file:// URL에서 모든 리소스 접근 허용
        webView.settings.allowUniversalAccessFromFileURLs = true
// 자바스크립트의 window.open() 등으로 새 창 열기 허용
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
// 웹뷰 캐시 사용 안함 (항상 새로운 리소스 로드)
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        // -- 새롭게 추가한 설정들 --
// 웹뷰 확대/축소 컨트롤 숨김
        webView.settings.displayZoomControls = false
// 모바일 웹뷰에서 PC 버전 웹사이트를 정상적으로 표시하기 위한 설정
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
// 멀티 윈도우 지원 설정
        webView.settings.setSupportMultipleWindows(true)
// HTTP와 HTTPS 콘텐츠 혼용 허용
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
// 웹뷰 내에서 새 창 열기 비활성화
        webView.settings.setSupportMultipleWindows(false)
// 웹뷰의 기본 텍스트 인코딩 설정
        webView.settings.defaultTextEncodingName = "UTF-8"
// 하드웨어 가속 사용으로 렌더링 성능 향상
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
// 스크롤바 숨김 설정
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
// 웹뷰의 페이지 로딩과 URL 처리를 담당할 커스텀 클라이언트 설정
        webView.webViewClient = CustomWebViewClient()
// 자바스크립트 얼럿, 파일 선택 등 UI 요소를 처리할 크롬 클라이언트 설정
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
