package com.example.rssreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

import android.webkit.WebView
import android.widget.Button
import com.example.rssreader.webviewmodule.HybridAppBridge
import org.json.JSONObject
import android.util.Log
import com.example.rssreader.webviewmodule.DeviceInfo

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var hybridAppBridge: HybridAppBridge
    private lateinit var buttonSendData: Button
    private val MAIN_LOG_TAG = " - MainActivity "


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // WebView 초기화
        webView = findViewById(R.id.webView) // activity_main.xml에 정의된 WebView ID
        hybridAppBridge = HybridAppBridge(webView)
        buttonSendData = findViewById(R.id.buttonSendData)

        // WebView 설정
        hybridAppBridge.initializeWebView(this)

        // 특정 URL 로드
        val url = "http://192.168.45.160:3000"
        hybridAppBridge.loadUrl(url)

        // 캐시가 남아 있으면
        webView.clearCache(true)

        buttonSendData.setOnClickListener{
            // Web으로 데이터 전달 예제
            hybridAppBridge.resConnect(
                DeviceInfo(macAddress = "11:22:33:44:55",
                    deviceName = "DEVICE_NAME")
            )
        }
//        buttonSendData.setOnClickListener{
//            // Web으로 데이터 전달 예제
//            sendDataToWeb(
//                "key" to "value",
//                "key2" to "value2")
//        }

    }

    override fun onStart() {
        super.onStart()
        // 액티비티가 화면에 나타날 준비가 되었을 때 수행할 작업
        //TODO : 시작시 회사 로고 보이게
    }

    override fun onResume() {
        super.onResume()
        // UI 업데이트나 리스너 등록
        //TODO : 앱이 백그라운드 -> 포그라운드때 핸들링
    }

    //TODO : (SharedPreferences 사용)로그인정보 기억하다가 앱실행시, 자동 로그인

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == PERMISSION_REQUEST_CODE) {
//            // 권한 요청 결과 처리
//            //TODO : 앱에서 권한이 필요 할 때 요청하는 핸들러
//        }
//    }

    /**
     * APP(client) -> Web(server) 데이터 전달
     */
    private fun sendDataToWeb(vararg pairs: Pair<String, Any?>) {
        /* 사용법 [ Map 처럼 생성해 JSON 생성 ]
        * sendDataToWebExample(
        *     "key" to "value",
        *     "key2" to "value2"  ...
        * )
        * */
        val functionName = "receiveDataFromApp" // JavaScript 함수 이름
        val jsonObject = JSONObject()
        for ((key, value) in pairs) {
            jsonObject.put(key, value)
        }

        hybridAppBridge.sendDataToWeb(functionName, jsonObject)
    }

    /**
     * 뒤로가기 버튼 처리
     */
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

}
