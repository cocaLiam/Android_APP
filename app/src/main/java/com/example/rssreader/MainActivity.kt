package com.example.rssreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

import android.webkit.WebView
import android.widget.Button
import com.example.rssreader.webviewmodule.HybridAppBridge
import org.json.JSONObject
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import com.example.rssreader.webviewmodule.DeviceInfo
import com.example.rssreader.webviewmodule.DeviceList
import com.example.rssreader.webviewmodule.ReadData

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var hybridAppBridge: HybridAppBridge
    private lateinit var buttonSendData: Button
    private val mainLogTag = " - MainActivity "


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
        val url = "http://192.168.45.246:3000"
        hybridAppBridge.loadUrl(url)

        // 캐시가 남아 있으면
        webView.clearCache(true)

//        // FrontEnd 디버깅 로그 출력
//        webView.webChromeClient = object : WebChromeClient() {
//            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
//                Log.d(mainLogTag, "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
//                return super.onConsoleMessage(consoleMessage)
//            }
//        }

        buttonSendData.setOnClickListener{
//             Web으로 데이터 전달 예제
            hybridAppBridge.resConnect(
                DeviceInfo(
                    macAddress = "11:22:33:44:55",
                    deviceName = "DEVICE_NAME"
                )
            )
            hybridAppBridge.resParingInfo(
                DeviceList(
                    deviceList = mutableListOf(
                        DeviceInfo(
                            macAddress = "11:22:33:44:55",
                            deviceName = "DEVICE_NAME_1"
                        ),
                        DeviceInfo(
                            macAddress = "22:33:44:55:66",
                            deviceName = "DEVICE_NAME_2"
                        )
                    )
                )
            )
            hybridAppBridge.resReadData(
                ReadData(
                    deviceInfo = DeviceInfo(
                        macAddress = "11:22:33:44:55",
                        deviceName = "DEVICE_NAME"
                    ),
                    msg = mapOf("key_From_App" to "val_From_App")
                )
            )
            hybridAppBridge.subObserveData(
                    mapOf(
                        "subObserveData key" to "val",
                        "Any_Key_From_App" to "Any_Value_From_App"
                    )
            )
        }
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
