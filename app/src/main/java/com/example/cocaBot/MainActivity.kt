package com.example.cocaBot

// Operator Pack

// UI Pack

// BLE Pack

// WebView Pack

// dataType Pack

// Job Service Pack

// Util Pack

// 비동기 전용 라이브러리

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.example.cocaBot.bleModules.BleController
import com.example.cocaBot.utils.LiveDataManager
import com.example.cocaBot.webViewModules.DeviceInfo
import com.example.cocaBot.webViewModules.DeviceList
import com.example.cocaBot.webViewModules.HybridAppBridge
import com.example.cocaBot.webViewModules.WebAppInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import com.example.cocaBot.services.OnDestroyJobService
import com.example.cocaBot.services.OnResumeJobService
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    // BLE 관련 변수들
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private lateinit var bleController: BleController
    private var scanList:DeviceList = DeviceList(mutableListOf<DeviceInfo>())
    private var scannedBluetoothDevice = mutableListOf<BluetoothDevice>()

    // WebView 관련 변수들
    private lateinit var webView: WebView
    private lateinit var hybridAppBridge: HybridAppBridge
    private lateinit var webAppInterface: WebAppInterface
    // TODO: 페이지 로딩 완료 신호 받기 용도, 코드작업 필요
    private val _pageLoadComplete = MutableLiveData<Boolean>()
    private val pageLoadComplete: LiveData<Boolean> = _pageLoadComplete

    // 비동기 작업을 위한 코루틴 스코프(코루틴 전용스레드 공간 정도) 선언
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    // 기타 등등 변수들
    private val MAIN_LOG_TAG = " - MainActivity "

    // JobService 객체
    private lateinit var jobScheduler: JobScheduler
    private lateinit var onDestroyJobInfo: JobInfo
    private lateinit var onResumeJobInfo: JobInfo

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
    private fun webViewResume(tmp:WebView){
        tmp.onResume()
        tmp.resumeTimers()
    }
    private fun webViewCreate(context: Context, webViewId: Int): WebView{
        val tmp = (context as Activity).findViewById<WebView>(webViewId)
        return tmp
    }

    private fun appCreate(){
        // BLE 초기화 ---------------------------------------------------------------------------------------
        bleController = BleController(this) // MainActivity는 Context를 상속받음

        // 1. BluetoothManager 및 BluetoothAdapter 초기화
        bleController.setBleModules()

        // 2_1. 권한요청 Launcher 등록
    //        registerForActivityResult 설명 >>
    //        Activity나 Fragment의 생명주기에 맞춰 실행되는 결과 처리 메커니즘을 제공하는 함수
    //        특정 작업(예: 권한 요청, 다른 Activity 호출 등)의 결과를 비동기적으로 처리하기 위해 사용됨
        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            { result: ActivityResult ->
                // 결과를 bleController로 전달
                bleController.handleBluetoothIntentResult(result)
            }
        )

        // 2_2. BLE 권한 요청 런처 BleController 에 전달
        bleController.setBlePermissionLauncher(enableBluetoothLauncher)

        // 3. BLE 지원 여부 확인
        bleController.checkBleOperator()
    // BLE 초기화 ---------------------------------------------------------------------------------------

    // WebView 초기화 -----------------------------------------------------------------------------------
//        webView = findViewById(R.id.webView) // activity_main.xml에 정의된 WebView ID
        webView = webViewCreate(this, R.id.webView)

        // WindowInsetsListener를 설정하여 시스템 소프트키 영역 계산
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            // WindowInsetsCompat로 시스템 제스처 영역 가져오기
            val systemGestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val softKeyHeight = systemGestureInsets.bottom // 소프트키 높이 가져오기

            // WebView의 패딩 또는 마진을 조정 (bottom에 소프트키 높이 반영)
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = softKeyHeight
            view.layoutParams = params // 변경된 LayoutParams 적용

            insets // 반환
        }
    //        webView.setOnApplyWindowInsetsListener { view, insets ->
    //            val systemGestureInsets = insets.systemGestureInsets
    //            val softKeyHeight = systemGestureInsets.bottom // 소프트키 높이 가져오기
    //
    //            // WebView의 패딩 또는 마진을 조정 (bottom에 소프트키 높이 반영)
    //            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
    //                bottomMargin = softKeyHeight // WebView 높이 줄임
    //            }
    //            insets // 반환
    //        }

        webAppInterface = WebAppInterface(webView, bleController, this@MainActivity, coroutineScope)
        hybridAppBridge = HybridAppBridge(webView, bleController, this@MainActivity, webAppInterface)
    //        webAppInterface = WebAppInterface.initialize(webView, bleController, this@MainActivity)

        // WebView 설정
        hybridAppBridge.initializeWebView()

        // 특정 URL 로드
        val url = "http://192.168.45.219:3000?timestamp=${System.currentTimeMillis()}"
    //        val url = "http://192.168.45.193:3000"
    //        val url = "app.cocabot.com"
        hybridAppBridge.loadUrl(url)

    // WebView 초기화 -----------------------------------------------------------------------------------

    // UI 초기화 ---------------------------------------------------------------------------------------
        // activity_main.xml의 루트 레이아웃 가져오기
        val rootLayout = findViewById<LinearLayout>(R.id.root_layout) // activity_main.xml의 루트 레이아웃 ID

        // 팝업을 루트 레이아웃에 추가
        bleController.registerScanCallback(scanCallback)
        bleController.requestBlePermission(this@MainActivity)

        // LiveData 관찰 [ 폴딩 전용 ]
        LiveDataManager.observeData.observe(this, Observer { newData ->
            // 데이터가 변경되면 UI 업데이트
            webAppInterface.subObserveData(newData)
        })
    // UI 초기화 ---------------------------------------------------------------------------------------

    // Job Service 초기화 ------------------------------------------------------------------------------
        jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        // MainActivity 인스턴스를 JobService에 전달
        OnDestroyJobService.setMainActivity(this)
        OnResumeJobService.setMainActivity(this)

        // Job 설정
        onDestroyJobInfo = JobInfo.Builder(OnDestroyJobService.JOB_ID,
            ComponentName(this, OnDestroyJobService::class.java))
//            .setMinimumLatency(10 * 60 * 1000)  // 10 분
//            .setMinimumLatency(10 * 1000)  // 10초
            .setMinimumLatency(5 * 1000)  // 최소 지연 시간 : 5초
            .setOverrideDeadline(10 * 1000)  // 최대 지연 시간 : 10초
            .build()

        onResumeJobInfo = JobInfo.Builder(OnResumeJobService.JOB_ID,
            ComponentName(this, OnResumeJobService::class.java))
            .setMinimumLatency(5 * 100)  // 최소 지연 시간 : 0.5초
            .setOverrideDeadline(10 * 100)  // 최대 지연 시간 : 1초
            .build()
/*
.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) : 작업 실행에 필요한 네트워크 조건을 지정 (WiFi만, 아무 네트워크나 등)
.setPeriodic(15 * 60 * 1000) : 작업을 주기적으로 반복 실행할 시간 간격 설정 (최소 15분)
.setMinimumLatency(10 * 60 * 1000) // 최소 지연 시간 (이 시간 이후에 실행) ( 10분 )
.setRequiresBatteryNotLow() : 배터리가 부족하지 않을 때만 작업 실행되도록 설정
.setPersisted(true) : 기기 재부팅 후에도 작업 일정 유지할지 설정
.setRequiresCharging(true) : 충전 중일 때만 작업 실행되도록 설정
.setRequiresDeviceIdle() : 기기가 대기 상태일 때만 작업 실행되도록 설정
* */
    // Job Service 초기화 ------------------------------------------------------------------------------

    }

    private fun appOpen(){
        // OnDestroyJobService JobService 취소 처리
        jobScheduler.cancel(OnDestroyJobService.JOB_ID)

//        jobScheduler.cancel(OnResumeJobService.JOB_ID)

        webViewResume(webView)
    }

    fun appPause(){
        // OnResumeJobService JobService 취소 처리
        jobScheduler.cancel(OnResumeJobService.JOB_ID)

//        jobScheduler.cancel(OnDestroyJobService.JOB_ID)

        webViewPause(webView)
    }

    fun appDestroy(){
        Log.i(MAIN_LOG_TAG, "appDestroy 실행 ")
        coroutineScope.launch {
            bleController.disconnectAllDevices()
            Log.i(MAIN_LOG_TAG, "appDestroy 종료 ")
        }
        webViewDestroy(webView)
    }

    fun jobServiceOnDestroy(){
        Log.i(OnDestroyJobService.jobServiceLogTag, "onDestroyJobInfo SERVICE 실행")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            finish()
        }else{
            appDestroy()
        }
        Log.i(OnDestroyJobService.jobServiceLogTag, "onDestroyJobInfo SERVICE 실행 완료")
    }

    fun jobServiceOnResume(){
        Log.i(OnResumeJobService.jobServiceLogTag, "onResumeJobInfo SERVICE 실행")
        val pairedDevices = bleController.getParingDevices() ?: mutableSetOf<BluetoothDevice>()
        if (pairedDevices.isNotEmpty()) {
            Log.i(OnResumeJobService.jobServiceLogTag, "페어링된 기기 발견: ${pairedDevices}")
            // 바로 연결 시도
            for (pairedDevice in pairedDevices) {
                val conDeviceList = bleController.getConnectedDevices()
//                if(!(pairedDevice in conDeviceList)){}
                if (!conDeviceList.contains(pairedDevice)) {
                    // 이미 연결되어 있다면 재연결 시도 X
                    connectToDeviceWithPermissionCheck(pairedDevice,true)
                }
            }
        } else {
            Log.w(OnResumeJobService.jobServiceLogTag, "페어링된 기기 없음")
            // 필요한 경우 여기서 다른 처리
        }
        Log.i(OnResumeJobService.jobServiceLogTag, "onResumeJobInfo SERVICE 실행 완료")
    }

/*
구형 폰
--Android 10(API 29) 이전-- 0~9
=========SM-G930S, And 8, API 26=========
앱 최초 실행 / 빌드시
    onCreate() → onStart() → onResume()
OverView 버튼 클릭시 ( 잠금화면 시 )
    onPause() → onStop()
앱 강제 종료
    OverView 버튼 클릭시 상황 이후 그냥 종료됨
OverView 에서 다시 앱 킬 시
    onStart() → onResume()
뒤로가기 버튼 앱 종료시
    onPause() → onStop() → onDestroy()
뒤로가기 후 다시 킬 시
    onCreate() → onStart() → onResume()
화면 회전 시
    onPause() → onStop() → onDestroy() → onCreate() → onStart() → onResume()

신형 폰
--Android 10 이후-- 10~11
=========SM-N960N, And 10, API 29=========
앱 최초 실행 / 빌드시
    onCreate() → onStart() → onResume()
OverView 버튼 클릭시 ( 잠금화면 시 )
    onPause() → onStop()
앱 강제 종료
    OverView 버튼 클릭시 상황 이후 그냥 종료됨
OverView 에서 다시 앱 킬 시
    onStart() → onResume()
뒤로가기 버튼 앱 종료시
    onPause() → onStop() → onDestroy()
뒤로가기 후 다시 킬 시
    onCreate() → onStart() → onResume()
화면 회전 시
    onPause() → onStop() → onDestroy() → onCreate() → onStart() → onResume()

--Android 12(API 31) 이후-- 12~
=========SM-A325N, And 13, API 33=========
( finish() 함수 호출시 onDestroy 콜백 ON ! )
앱 최초 실행 / 빌드시
    onCreate() → onStart() → onResume()
OverView 버튼 클릭시 ( 잠금화면 시 )
    onPause() → onStop()
앱 강제 종료
    OverView 버튼 클릭시 상황 이후 그냥 종료됨
OverView 에서 다시 앱 킬 시
    onStart() → onResume()
뒤로가기 버튼 앱 종료시
    onPause() → onStop()
뒤로가기 후 다시 킬 시
    onCreate() → onStart() → onResume()
화면 회전 시
    onPause() → onStop() → onDestroy() → onCreate() → onStart() → onResume()?
* */


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        Log.i(MAIN_LOG_TAG, "onCreate")
//        Toast.makeText(this,"onCreate", Toast.LENGTH_SHORT).show()

        appCreate()
    }

    override fun onStart() {
        super.onStart()
        // 액티비티가 사용자에게 보여지기 직전에 호출
        // APP 시작시 자동 실행하는 작업들
        Log.i(MAIN_LOG_TAG, "onStart")
//        Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        Log.i(MAIN_LOG_TAG, "onResume")
//        Toast.makeText(this, "onResume", Toast.LENGTH_SHORT).show()

        appOpen()

        if (jobScheduler.schedule(onResumeJobInfo) == JobScheduler.RESULT_SUCCESS) {
            Log.d(OnResumeJobService.jobServiceLogTag, "onResumeJobInfo 스케줄링 성공")
        } else {
            Log.d(OnResumeJobService.jobServiceLogTag, "onResumeJobInfo 스케줄링 실패")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(MAIN_LOG_TAG, "onPause")
//        Toast.makeText(this,"onPause", Toast.LENGTH_SHORT).show()

        appPause()
    }

    /*Android 12 이상
    ** APP 종료 수순
    * onPause [appPause() : OnResumeJobService 취소 처리, webViewPause()] →
        onStop [onDestroyJobInfo() : 10분 지연 finish()] →
        onDestroy [ appDestroy() ]
    ** APP 시작 수순
    * onCreate() [appCreate()] →
        onStart() [ OverView, 잠금화면 상황은 여기부터 시작함 ] →
        onResume() [ appOpen() : onDestroyJobInfo 취소 처리, webViewResume()
            onResumeJobInfo() : 0.5초 지연 + 권한 검사 후, 페어링기기 자동연결 ]
    * */

    /*Android 12 미만
    ** APP 종료 수순
    * onPause [appPause() : OnResumeJobService 취소 처리, webViewPause()] →
        onStop →
        onDestroy [ onDestroyJobInfo() : 10분 지연 appDestroy() ]
      *
    * onPause[appPause()] -> onStop -> onDestroy -> jobService [ appDestroy() ]
    ** APP 시작 수순
    * onCreate() [appCreate()] →
        onStart() [ OverView, 잠금화면 상황은 여기부터 시작함 ] →
        onResume() [ appOpen() : onDestroyJobInfo 취소 처리, webViewResume()
            onResumeJobInfo() : 0.5초 지연 + 권한 검사 후, 페어링기기 자동연결 ]
    * */
    override fun onStop() {
        super.onStop()
        Log.i(MAIN_LOG_TAG, "onStop")
//        Toast.makeText(this,"onStop", Toast.LENGTH_SHORT).show()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (jobScheduler.schedule(onDestroyJobInfo) == JobScheduler.RESULT_SUCCESS) {
                Log.d(OnDestroyJobService.jobServiceLogTag, "onDestroyJobInfo 스케줄링 성공")
            } else {
                Log.d(OnDestroyJobService.jobServiceLogTag, "onDestroyJobInfo 스케줄링 실패")
            }
        }else{
            // Android 12 미만은 자동으로 onDestroy 빠지니까 onDestroy서 처리
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(MAIN_LOG_TAG, "onDestroy")
//        Toast.makeText(this,"onDestroy", Toast.LENGTH_SHORT).show()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 이미 10분 기다리고 온경우
            appDestroy()
        }else{
            // 이제 10분 기다리고 동작하는 경우
            if (jobScheduler.schedule(onDestroyJobInfo) == JobScheduler.RESULT_SUCCESS) {
                Log.d(OnDestroyJobService.jobServiceLogTag, "onDestroyJobInfo 스케줄링 성공")
            } else {
                Log.d(OnDestroyJobService.jobServiceLogTag, "onDestroyJobInfo 스케줄링 실패")
            }
        }
    }

    override fun onBackPressed() {
        Log.i(MAIN_LOG_TAG, "onBackPressed :  ${webView.canGoBack()}")
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }

        // TODO: 김쿤네 회사 onBackPressed 작업 List
//        if (_popupList.size() !== 0) {
//            val popup: WebView = _popupList.get(_listSortNum)
//
//            if (_locationCaseUrl != null) {
//                if (popup.url.equals(_locationCaseUrl, ignoreCase = true)) {
//                    popup.loadUrl("javascript:window.webViewBackCase()")
//                }
//            } else if (popup.canGoBack()) {
//                popup.goBack()
//            } else {
//                popup.loadUrl("javascript:window.close()")
//            }
//        } else if (_popupList.size() === 0) {
//            if (_webView.canGoBack()) {
//                _webView.goBack()
//            } else {
//                _webView.loadUrl("javascript:requestHwBack()")
//            }
//        }
    }

    //    // BLE Connect 권한 검사 메서드
    @SuppressLint("MissingPermission")
    fun connectToDeviceWithPermissionCheck(tmp: Any, isAutoConnection: Boolean) {
        val targetDevice:BluetoothDevice
        targetDevice = when (tmp) {
            is String -> { bleController.bluetoothAdapter.getRemoteDevice(tmp)}
            is BluetoothDevice -> {tmp}
            else -> { return }
        }
        if(!(bleController.requestBlePermission(this@MainActivity))){
            return
        }

        // 블루투스 권한 통과
        Log.i(MAIN_LOG_TAG, " targetDevice : ${targetDevice}")
        bleController.connectToDevice(targetDevice, { isConnected ->
            Log.i(MAIN_LOG_TAG,"isConnected : $isConnected")
            if (isConnected) {
                if(isAutoConnection){
                    // WEB 에서 reqConnectedDevices 호출로 화면 갱신
                    // <- 그대로 이용해서 AutoConnection 시 WEB UI 화면 갱신 용도
                    Log.i(MAIN_LOG_TAG,"isConnected : $isConnected")
                    webAppInterface.reqConnectedDevices()
//                        webAppInterface.resAutoConnect(DeviceInfo(
//                            macAddress = selectedDevice.address,
//                            deviceType = selectedDevice.name))
                }else{
                    webAppInterface.resConnect(DeviceInfo(
                        macAddress = targetDevice.address,
                        deviceType = targetDevice.name))
                }
            } else {
                Log.w(MAIN_LOG_TAG, "${targetDevice.name} 기기 연결 끊어짐")
            }
        })

    }

//    @SuppressLint("MissingPermission")
//    private fun connectToDeviceWithPermissionCheck(selectedDevice: BluetoothDevice, isAutoConnection: Boolean) {
//        if(bleController.requestBlePermission(this@MainActivity)){
//            // 블루투스 권한이 이미 있는 경우
//            Log.i(mainLogTag, "${selectedDevice?.name} ${selectedDevice?.address} 2. 기기 추가 resConnect")
//            bleController.connectToDevice(selectedDevice, { isConnected ->
//                if (isConnected) {
//                    Log.i(mainLogTag, "${isConnected}, ${isAutoConnection}3. 기기 추가 resConnect")
//                    if(isAutoConnection){
//                        Log.i(mainLogTag, "${selectedDevice.name} ${selectedDevice.address} AUTO CONNECT 시도")
//                        // WEB 에서 reqConnectedDevices 호출로 화면 갱신
//                        // <- 그대로 이용해서 AutoConnection 시 WEB UI 화면 갱신 용도
////                        webAppInterface.reqConnectedDevices()
//
////                        webAppInterface.resAutoConnect(DeviceInfo(
////                            macAddress = selectedDevice.address,
////                            deviceType = selectedDevice.name))
//                    }else{
//                        Log.i(mainLogTag, "${selectedDevice.name} ${selectedDevice.address} 기기 추가 resConnect")
//                        webAppInterface.resConnect(DeviceInfo(
//                            macAddress = selectedDevice.address,
//                            deviceType = selectedDevice.name))
//                    }
//                } else {
//                    Log.w(mainLogTag, "${selectedDevice.name} 기기 연결 끊어짐")
////                    webAppInterface.resConnect(DeviceInfo(
////                        macAddress = "",
////                        deviceType = selectedDevice.name))
//                }
//            })
//        }
//    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                101 -> { // BLE 스캔 권한 요청 결과
                    Log.i(MAIN_LOG_TAG, "블루투스 스캔 권한이 허용되었습니다.")
                    bleController.permissionStatus.bluetoothScanPermission = true
                }
                102 -> { // 위치 권한 요청 결과
                    Log.i(MAIN_LOG_TAG, "위치 권한이 허용되었습니다.")
                    bleController.permissionStatus.locationPermission = true
                }
                103 -> { // BLE 연결 권한 요청 결과
                    Log.i(MAIN_LOG_TAG, "블루투스 연결 권한이 허용되었습니다.")
                    bleController.permissionStatus.bluetoothConnectPermission = true
                }
            }
        } else {
            // 권한이 거부된 경우
            when (requestCode) {
                101 -> {
                    Log.e(MAIN_LOG_TAG, "블루투스 스캔 권한이 거부되었습니다.")
                    bleController.permissionStatus.bluetoothScanPermission = false
                    Toast.makeText(this, "블루투스 스캔 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
                102 -> {
                    Log.e(MAIN_LOG_TAG, "위치 권한이 거부되었습니다.")
                    bleController.permissionStatus.locationPermission = false
                    Toast.makeText(this, "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
                103 -> {
                    Log.e(MAIN_LOG_TAG, "블루투스 연결 권한이 거부되었습니다.")
                    bleController.permissionStatus.bluetoothConnectPermission = false
                    Toast.makeText(this, "블루투스 연결 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // TODO: 추가작업 부분
    // bleController.bleScanPopup()
    // bleController.stopBleScan()
    fun reqBleScan(){
        bleController.startBleScan()
    }

    fun reqBleStop(){
        bleController.stopBleScan()
        scanList = DeviceList(mutableListOf<DeviceInfo>())
        webAppInterface.resScanStop()
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            val device = result.device
//            val deviceName = result.scanRecord?.deviceName ?: "Unknown Device"
//            scanListAdapter.addDeviceToAdapt(device)

            val device:BluetoothDevice = result.device

            // 기기 이름 확인 (필터링 조건)
            if (device.name.isNullOrEmpty() || device.name == "Unknown Device") {
                // 이름이 없거나 "Unknown Device"인 경우, 추가하지 않음
                return
            }

            // 스캔 리스트에 이미 MAC 주소가 있는지 확인
            val isAlreadyAdded = scanList.deviceList.any { it.macAddress == device.address }
            if (isAlreadyAdded) {
                return // 이미 리스트에 존재하면 추가하지 않음
            }

            scanList.deviceList.add(DeviceInfo(macAddress = device.address, deviceType = device.name))
            scannedBluetoothDevice.add(device)
            webAppInterface.resScanStart(scanList)

            Log.d(MAIN_LOG_TAG,"scanList >>>> $scanList")
        }


        override fun onScanFailed(errorCode: Int) {
            Log.e(MAIN_LOG_TAG, "onScanFailed called with errorCode: $errorCode")
            when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> Log.e(MAIN_LOG_TAG, "Scan already started")
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(MAIN_LOG_TAG, "App registration failed")
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> Log.e(MAIN_LOG_TAG, "Internal error")
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(MAIN_LOG_TAG, "Feature unsupported")
            }
            Toast.makeText(this@MainActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

}
