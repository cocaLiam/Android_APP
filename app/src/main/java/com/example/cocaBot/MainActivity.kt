package com.example.cocaBot

// Operator Pack

// UI Pack

// BLE Pack

// WebView Pack

// dataType Pack

// Util Pack
import android.annotation.SuppressLint
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
import androidx.core.view.updateLayoutParams
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
import com.example.cocaBot.webViewModules.JsonValidationResult
import com.example.cocaBot.webViewModules.WebAppInterface


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

    // 기타 등등 변수들
    private val mainLogTag = " - MainActivity "

    // AutoConnection 처리 용도 ( onResume 에서 x초 후 연결 시도, onDestroy 에서 자동 연결 시도 콜백 취소 처리 )
    private val handler = MyContextData.handler
    private val delayOnResumeCallback = Runnable {
        val pairedDevices = bleController.getParingDevices() ?: return@Runnable
        if (pairedDevices.isNotEmpty()) {
            Log.i(mainLogTag, "페어링된 기기 발견: ${pairedDevices}")
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
            Log.i(mainLogTag, "페어링된 기기 없음")
            // 필요한 경우 여기서 다른 처리
        }
        Log.i(mainLogTag, "onResume END")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        Toast.makeText(this,"onCreate", Toast.LENGTH_SHORT).show()
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
        webView = findViewById(R.id.webView) // activity_main.xml에 정의된 WebView ID

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

        webAppInterface = WebAppInterface(webView, bleController, this@MainActivity)
        hybridAppBridge = HybridAppBridge(webView, bleController, this@MainActivity, webAppInterface)
//        webAppInterface = WebAppInterface.initialize(webView, bleController, this@MainActivity)

        // WebView 설정
        hybridAppBridge.initializeWebView()

        // 특정 URL 로드
        val url = "http://192.168.45.76:3000?timestamp=${System.currentTimeMillis()}"
//        val url = "http://192.168.45.193:3000"
//        val url = "app.cocabot.com"
        hybridAppBridge.loadUrl(url)

        WebView.setWebContentsDebuggingEnabled(true)

        // 캐시가 남아 있으면
        webView.clearCache(true)
        webView.clearHistory()

//        // FrontEnd 디버깅 로그 출력
//        webView.webChromeClient = object : WebChromeClient() {
//            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
//                Log.d(mainLogTag, "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
//                return super.onConsoleMessage(consoleMessage)
//            }
//        }

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
    }
/*
앱 최초 실행 시:
    onCreate() → onStart() → onResume()
앱 종료 시:
    onPause() → onStop() → onDestroy()
화면 회전 시:
    onPause() → onStop() → onDestroy() → onCreate() → onStart() → onResume()
다이얼로그나 팝업이 화면 일부를 가릴 때:
    onPause() (다시 전체화면으로 돌아올 때: onResume())
백 버튼으로 앱 종료 시:
    onPause() → onStop() → onDestroy()
앱이 메모리 부족으로 강제 종료될 때:
    onPause() → onStop() → onDestroy() (다시 실행 시: onCreate()부터 새로 시작)
* */
    override fun onStart() {
        super.onStart()
        // 액티비티가 사용자에게 보여지기 직전에 호출
        // APP 시작시 자동 실행하는 작업들
        Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show()

        handler.postDelayed(delayOnResumeCallback,  500)
    }

    override fun onResume() {
        super.onResume()
        Log.i(mainLogTag, "onResume START")
//        Toast.makeText(this, "onResume", Toast.LENGTH_SHORT).show()

//        webView.reload()  // 새로고침처럼됨
//        webView.resumeTimers()  // 갤럭시 s1/s2의 경우 필요함 ( webView는 google Timers에 의해 내부적으로 동작한다 )
//        Log.i(mainLogTag, "웹뷰 리로드")
    }

    override fun onPause() {
        super.onPause()

        Log.i(mainLogTag, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(mainLogTag, "onDestroy START")
        Toast.makeText(this,"onDestroy", Toast.LENGTH_SHORT).show()
        /*
        * onResume -> onDestroy 시에는 onResume의 delayOnResumeCallback 지연 함수가 잘 취소가 되는데
        * onDestroy -> onResume시에는 onDestroy 의 delayOnDestroyCallback 지연 함수는 취소가 안됨
        * ( 이유 : onResume 하면서 delayOnDestroyCallback 의 참조가 새로 할당 되므로 )
        * */
        // onResume 자동 연결 콜백 취소 처리
        handler.removeCallbacks(delayOnResumeCallback)

        // Callback 메모리 해제 + handler 객체
        Log.d(mainLogTag, "디버깅중 < 모든 기기 연결 해제 시도 11111 ")
        bleController.disconnectAllDevices()
        handler.removeCallbacksAndMessages(null)
//        handler = null

        webView.clearHistory();
        // NOTE: clears RAM cache,
        webView.clearCache(true);
        webView.loadUrl("about:blank");
        webView.onPause();
        webView.removeAllViews();
        webView.destroyDrawingCache();
        // If you create another WebView after calling this,
        // make sure to call mWebView.resumeTimers().
        webView.pauseTimers();
        // NOTE: This can occasionally cause a segfault below API 17 (4.2)
        webView.destroy();

        Log.i(mainLogTag, "onDestroy END")
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
        Log.i(mainLogTag, " targetDevice : ${targetDevice}")
        bleController.connectToDevice(targetDevice, { isConnected ->
            Log.i(mainLogTag,"isConnected : $isConnected")
            if (isConnected) {
                if(isAutoConnection){
                    // WEB 에서 reqConnectedDevices 호출로 화면 갱신
                    // <- 그대로 이용해서 AutoConnection 시 WEB UI 화면 갱신 용도
                    Log.i(mainLogTag,"isConnected : $isConnected")
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
                Log.w(mainLogTag, "${targetDevice.name} 기기 연결 끊어짐")
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
                    Log.i(mainLogTag, "블루투스 스캔 권한이 허용되었습니다.")
                    bleController.permissionStatus.bluetoothScanPermission = true
                }
                102 -> { // 위치 권한 요청 결과
                    Log.i(mainLogTag, "위치 권한이 허용되었습니다.")
                    bleController.permissionStatus.locationPermission = true
                }
                103 -> { // BLE 연결 권한 요청 결과
                    Log.i(mainLogTag, "블루투스 연결 권한이 허용되었습니다.")
                    bleController.permissionStatus.bluetoothConnectPermission = true
                }
            }
        } else {
            // 권한이 거부된 경우
            when (requestCode) {
                101 -> {
                    Log.e(mainLogTag, "블루투스 스캔 권한이 거부되었습니다.")
                    bleController.permissionStatus.bluetoothScanPermission = false
                    Toast.makeText(this, "블루투스 스캔 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
                102 -> {
                    Log.e(mainLogTag, "위치 권한이 거부되었습니다.")
                    bleController.permissionStatus.locationPermission = false
                    Toast.makeText(this, "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
                103 -> {
                    Log.e(mainLogTag, "블루투스 연결 권한이 거부되었습니다.")
                    bleController.permissionStatus.bluetoothConnectPermission = false
                    Toast.makeText(this, "블루투스 연결 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

            Log.d(mainLogTag,"scanList >>>> $scanList")
/*
scanResult >>>>
ScanResult{
    device=5F:29:7E:A2:21:88,  <<-요거 BluetoothDevice 타입임
    scanRecord=ScanRecord [mAdvertiseFlags=26, mServiceUuids=null,
        mManufacturerSpecificData={76=[16, 5, 57, 28, 38, 20, 120]},
        mServiceData={}, mTxPowerLevel=12, mDeviceName=Microchip],
    rssi=-62, timestampNanos=801393765211516, eventType=27, primaryPhy=1, secondaryPhy=0, advertisingSid=255, txPower=127, periodicAdvertisingInterval=0}

scanResult >>>>
ScanResult{
    device=9C:95:6E:40:0F:75,  <<-요거 BluetoothDevice 타입임
    scanRecord=ScanRecord [mAdvertiseFlags=5, mServiceUuids=null,
        mManufacturerSpecificData={}, mServiceData={0000feda-0000-1000-8000-00805f9b34fb=[-1, 1]},
        mTxPowerLevel=-2147483648, mDeviceName=kang
    ],
    rssi=-48,
    timestampNanos=801393356507170,
    eventType=27,
    primaryPhy=1,
    secondaryPhy=0,
    advertisingSid=255,
    txPower=127,
    periodicAdvertisingInterval=0
}
*/
        }


        override fun onScanFailed(errorCode: Int) {
            Log.e(mainLogTag, "onScanFailed called with errorCode: $errorCode")
            when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> Log.e(mainLogTag, "Scan already started")
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> Log.e(mainLogTag, "App registration failed")
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> Log.e(mainLogTag, "Internal error")
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> Log.e(mainLogTag, "Feature unsupported")
            }
            Toast.makeText(this@MainActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

}
