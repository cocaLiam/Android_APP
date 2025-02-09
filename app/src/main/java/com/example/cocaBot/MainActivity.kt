package com.example.cocaBot

// Operator Pack
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge

// UI Pack
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.content.Intent

// BLE Pack
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.example.cocaBot.bleModules.ScanListAdapter
import com.example.cocaBot.bleModules.BleController

// WebView Pack
import android.webkit.WebView
import com.example.cocaBot.webViewModules.HybridAppBridge
import com.example.cocaBot.webViewModules.WebAppInterface

// dataType Pack
import com.example.cocaBot.webViewModules.DeviceInfo
import com.example.cocaBot.webViewModules.DeviceList
import com.example.cocaBot.webViewModules.ReadData
import com.example.cocaBot.webViewModules.WriteData
import com.example.cocaBot.webViewModules.JsonValidationResult
import org.json.JSONObject

// Util Pack
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.cocaBot.utils.LiveDataManager
import androidx.lifecycle.Observer
import android.util.Log


class MainActivity : AppCompatActivity() {
    // BLE 관련 변수들
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private lateinit var bleController: BleController
    private var isScanning = false

    // WebView 관련 변수들
    private lateinit var webView: WebView
    private lateinit var hybridAppBridge: HybridAppBridge
    private lateinit var webAppInterface: WebAppInterface

    // UI 관련 변수들
    private val handler = Handler(Looper.getMainLooper())
//    private lateinit var debuggingButton: Button
    private lateinit var btnConnect: Button
    private lateinit var btnClose: Button

    // 팝업 UI 관련 변수들
    private lateinit var popupContainer: LinearLayout
    private lateinit var recyclerScanList: RecyclerView
    private lateinit var popupView: View
    private var scanListAdapter: ScanListAdapter = ScanListAdapter()

    // 기타 등등 변수들
    private val mainLogTag = " - MainActivity "

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

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

        hybridAppBridge = HybridAppBridge(webView, bleController, this@MainActivity)
        webAppInterface = WebAppInterface.initialize(webView, bleController, this@MainActivity)

        // WebView 설정
        hybridAppBridge.initializeWebView()

        // 특정 URL 로드
        val url = "http://192.168.45.206:3000"
//        val url = "app.cocabot.com"
        hybridAppBridge.loadUrl(url)

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
        val rootLayout =
            findViewById<LinearLayout>(R.id.root_layout) // activity_main.xml의 루트 레이아웃 ID

        // popup_scan_list.xml을 inflate
        popupView = LayoutInflater.from(this)
            .inflate(
                R.layout.popup_scan_list,
                rootLayout,
                false
            )

        // popup_scan_list.xml 내부 View 초기
        btnConnect = popupView.findViewById(R.id.btn_connect)
        btnClose = popupView.findViewById(R.id.btn_close)
        popupContainer = popupView.findViewById(R.id.popup_container)
        recyclerScanList = popupView.findViewById(R.id.recycler_scan_list)

        // RecyclerView 초기화
        scanListAdapter.setupRecyclerView(recyclerScanList, this)

        // View가 트리에 추가되었는지 확인하기 위한 로그 추가
        Log.i(mainLogTag, "popupView: ${popupView.visibility}, " +
                "recyclerScanList: ${recyclerScanList.visibility}, " +
                "adapter: ${recyclerScanList.adapter}")

//        debuggingButton = findViewById(R.id.debuggingButton)

        // 팝업을 루트 레이아웃에 추가
        rootLayout.addView(popupView)
        bleController.registerScanCallback(scanCallback)
        bleController.registerPopupContainer(popupContainer)
        bleController.requestBlePermission(this@MainActivity)

//        debuggingButton.setOnClickListener {
////            startBleScan()
//            webAppInterface.subObserveData(mapOf("subObserveData Key" to "subObserveData Value"))
//        }

        // LiveData 관찰
        /*LiveDataManager.updateObserveData(mapOf("aa" to "bb")) 로 업데이트 할 때 마다
        * webAppInterface.subObserveData(newData) 호출되서 APP -> WEB Data 전송
        */
        LiveDataManager.observeData.observe(this, Observer { newData ->
            // 데이터가 변경되면 UI 업데이트
            webAppInterface.subObserveData(newData)
        })

//        // reqConnect
//        btnScanStart.setOnClickListener {
//            if(bleController.requestBlePermission(this)){
//                // 권한이 허용된 경우에만 BLE 스캔 시작
//                startBleScan()
//                connectToDeviceWithPermissionCheck(DeviceInfo)
//            }
//        }
//
//        // ( 트리거 : APP )
//        // reqReadData
//        btnRequestReadData.setOnClickListener {
//            val macAddress: BluetoothDevice = bleController.getConnectedDevices()[0]
//            bleController.requestReadData(macAddress.address)
//        }
//
//        // reqParingInfo
//        btnParingCheck.setOnClickListener {
//            val bondedDevices: Set<BluetoothDevice>? = bleController.getParingDevices()
//            Log.i(mainLogTag, "bondedDevices : $bondedDevices")
//            Log.i(mainLogTag, "getConnectedDevices : ${bleController.getConnectedDevices()}")
//            if (bondedDevices == null){
//                bleController.updateReadData("")
//            }else{
//                bleController.updateReadData("페어링된 기기 리스트 : ${bondedDevices} \n" +
//                        "현재 연결된 기기 리스트 : ${bleController.getConnectedDevices()}")
//            }
//        }
//
//        // reqConnectedDevices
//        bleController.getConnectedDevices()
//
//        // reqRemoveParing
//        bleController.removeParing(DeviceInfo)
//
//        pubDisconnectAllDevice
//        bleController.disconnectAllDevices()
//
//        // pubSendData
//        btnSendData.setOnClickListener {
//            val inputData = etInputData.text.toString() // EditText에서 입력된 데이터 가져오기
//            if (inputData.isNotEmpty()) {
//                val dataToSend = inputData.toByteArray() // 문자열을 ByteArray로 변환
//                val macAddress: BluetoothDevice = bleController.getConnectedDevices()[0]
//                bleController.writeData(dataToSend, macAddress.address) // BLE로 데이터 전송
//                Log.i(mainLogTag,"Data Sent: $inputData")
//            } else {
//                Log.i(mainLogTag,"Data Sent: $inputData")
//            }
//        }
//
//        // TODO: 쓸지 말지 고민
//        // subObserveData
//        // LiveData 관찰 설정
//        bleController.readData.observe(this, Observer { newData ->
//            // 데이터가 변경되면 UI 업데이트
//            if ( newData is String){
//                etOutputData.setText(newData)
//            }else{
//                etOutputData.setText(newData.toString())
//            }
//        })

        // Close 버튼 클릭 리스너
        btnClose.setOnClickListener {
            stopBleScanAndClearScanList()
        }

        // Connect 버튼 클릭 리스너
        btnConnect.setOnClickListener {
            val selectedDevice = scanListAdapter.getSelectedDevice()
            if (selectedDevice != null) {  // 아무 Radio Button 을 누르지 않은 경우
                connectToDeviceWithPermissionCheck(selectedDevice,false)
                // Connect 후 팝업창 종료 + Scan 종료
                stopBleScanAndClearScanList()
            }
        }

// UI 초기화 ---------------------------------------------------------------------------------------
    }

    // BLE Connect 권한 검사 메서드
    @SuppressLint("MissingPermission")
    private fun connectToDeviceWithPermissionCheck(selectedDevice: BluetoothDevice, isAutoConnection: Boolean) {
        if(bleController.requestBlePermission(this@MainActivity)){
            // 블루투스 권한이 이미 있는 경우
            bleController.connectToDevice(selectedDevice, { isConnected ->
                if (isConnected) {
                     Log.i(mainLogTag, "${selectedDevice.name} 기기 연결 성공")
                    if(isAutoConnection){
//                        webAppInterface.resAutoConnect(DeviceInfo(
//                            macAddress = selectedDevice.address,
//                            deviceType = selectedDevice.name))
                        webAppInterface.reqConnectedDevices()
                    }else{
                        webAppInterface.resConnect(DeviceInfo(
                            macAddress = selectedDevice.address,
                            deviceType = selectedDevice.name))
                    }
                } else {
                    Log.w(mainLogTag, "${selectedDevice.name} 기기 연결 끊어짐")
//                    webAppInterface.resConnect(DeviceInfo(
//                        macAddress = "",
//                        deviceType = selectedDevice.name))
                }
            })
        }
    }

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


    override fun onPause() {
        super.onPause()

        // 캐시를 삭제해야 화면에 UI 가 업데이트 됨
        webView.clearCache(true)
        webView.clearHistory()
        Log.i(mainLogTag, "캐시 삭제")

        Log.i(mainLogTag, "onPause")
        Toast.makeText(this,"onPause", Toast.LENGTH_SHORT).show()
        if (isScanning) {  // 스캔 상태 확인
            stopBleScanAndClearScanList()
        }
        isScanning = false  // 상태 강제 초기화
        runOnUiThread {
            popupView.visibility = View.GONE
            popupContainer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 캐시를 삭제해야 화면에 UI 가 업데이트 됨
        webView.clearCache(true)
        webView.clearHistory()
        Log.i(mainLogTag, "캐시 삭제")

        Log.i(mainLogTag, "onDestroy")
        Toast.makeText(this,"onDestroy", Toast.LENGTH_SHORT).show()
        bleController.disconnectAllDevices()
        scanListAdapter.clearDevices()
        if (isScanning) {  // 스캔 상태 확인
            stopBleScanAndClearScanList()
        }
        isScanning = false  // 상태 강제 초기화
        runOnUiThread {
            popupView.visibility = View.GONE
            popupContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        webView.reload()
        webView.resumeTimers()  // 갤럭시 s1/s2의 경우 필요함 ( webView는 google Timers에 의해 내부적으로 동작한다 )
        Log.i(mainLogTag, "웹뷰 리로드")

        Log.i(mainLogTag, "onResume")
        Toast.makeText(this,"onResume", Toast.LENGTH_SHORT).show()

        if (true) {
            val pairedDevices = bleController.getParingDevices() ?: return

            // 페어링된 기기가 있는 경우
            if (pairedDevices.isNotEmpty()) {
                Log.i(mainLogTag, "페어링된 기기 발견: ${pairedDevices}")
                // 바로 연결 시도
                for (pairedDevice in pairedDevices) {
                    connectToDeviceWithPermissionCheck(pairedDevice, true)
                }
            } else {
                Log.i(mainLogTag, "페어링된 기기 없음")
                // 필요한 경우 여기서 다른 처리
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
    }

//    fun startBleScan() {
//        try {
//            bleController.bleScanPopup()
//        } catch (e: Exception) {
//            Log.e(mainLogTag, "Failed to start BLE scan: ${e.message}")
//        } finally {
//            popupView.visibility = View.VISIBLE
//            popupContainer.visibility = View.VISIBLE
//            Log.i(mainLogTag, "------------------------------------------------" +
//                    "START startBleScan 호출 및 popupView 상태: ${popupView.visibility}, ${popupContainer.visibility}" +
//                    "------------------------------------------------")
//        }
//    }
//    fun startBleScan() {
//        if (isScanning) {
//            Log.w(mainLogTag, "Scan already in progress")
//            return
//        }
//
//        try {
//            bleController.bleScanPopup()
//        } catch (e: Exception) {
//            Log.e(mainLogTag, "Failed to start BLE scan: ${e.message}")
//            isScanning = false
//        } finally {
//            isScanning = true
//            runOnUiThread {
//                popupView.visibility = View.VISIBLE
//                popupContainer.visibility = View.VISIBLE
//            }
//            Log.i(mainLogTag, "------------------------------------------------" +
//                    "START startBleScan 호출 및 popupView 상태: ${popupView.visibility}, ${popupContainer.visibility}" +
//                    "------------------------------------------------")
//        }
//    }

    fun startBleScan() {
        if (isScanning) {
            Log.w(mainLogTag, "Scan already in progress")
            return
        }

        runOnUiThread {
            try {
                // UI 변경
                popupView.visibility = View.VISIBLE
                popupContainer.visibility = View.VISIBLE

                // UI 변경 후 즉시 상태 확인
                if (popupView.visibility == View.VISIBLE &&
                    popupContainer.visibility == View.VISIBLE) {
                    // UI가 정상적으로 변경된 것을 확인한 후 스캔 시작
                    bleController.bleScanPopup()
                    isScanning = true
                }
            } catch (e: Exception) {
                Log.e(mainLogTag, "Failed to start BLE scan: ${e.message}")
                isScanning = false
                popupView.visibility = View.GONE
                popupContainer.visibility = View.GONE
            }
        }
    }


//    fun stopBleScanAndClearScanList() {
//        try {
//            bleController.stopBleScan()
//            if (scanListAdapter.getDeviceList().isNotEmpty()) {
//                scanListAdapter.clearDevices() // 데이터가 있을 때만 초기화
//            }
//        } catch (e: Exception) {
//            Log.e(mainLogTag, "Failed to stop BLE scan: ${e.message}")
//        } finally {
//            popupView.visibility = View.GONE // 팝업 숨김
//            popupContainer.visibility = View.GONE // 팝업 컨테이너 숨김
//            Log.i(mainLogTag, "------------------------------------------------" +
//                    "STOP stopBleScanAndClearScanList 호출 및 popupView 상태: ${popupView.visibility}" +
//                    "------------------------------------------------")
//        }
//    }
//    fun stopBleScanAndClearScanList() {
//        if (!isScanning) {
//            Log.w(mainLogTag, "No scan in progress")
//            return
//        }
//
//        try {
//            bleController.stopBleScan()
//            if (scanListAdapter.getDeviceList().isNotEmpty()) {
//                scanListAdapter.clearDevices()
//            }
//        } catch (e: Exception) {
//            Log.e(mainLogTag, "Failed to stop BLE scan: ${e.message}")
//        } finally {
//            isScanning = false
//            runOnUiThread {
//                popupView.visibility = View.GONE
//                popupContainer.visibility = View.GONE
//            }
//            Log.i(mainLogTag, "------------------------------------------------" +
//            "STOP stopBleScanAndClearScanList 호출 및 popupView 상태: ${popupView.visibility}, ${popupContainer.visibility}" +
//            "------------------------------------------------")
//        }
//    }

    fun stopBleScanAndClearScanList() {
        if (!isScanning) {
            Log.w(mainLogTag, "No scan in progress")
            return
        }

        runOnUiThread {
            try {
                // 스캔 중지 및 리스트 클리어
                bleController.stopBleScan()
                if (scanListAdapter.getDeviceList().isNotEmpty()) {
                    scanListAdapter.clearDevices()
                }

                // UI 변경
                popupView.visibility = View.GONE
                popupContainer.visibility = View.GONE
                isScanning = false

            } catch (e: Exception) {
                Log.e(mainLogTag, "Failed to stop BLE scan: ${e.message}")
            } finally {
                Log.i(mainLogTag, "------------------------------------------------" +
                        "STOP stopBleScanAndClearScanList 호출 및 popupView 상태: ${popupView.visibility}, ${popupContainer.visibility}" +
                        "------------------------------------------------")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            val device = result.device
//            val deviceName = result.scanRecord?.deviceName ?: "Unknown Device"
//            scanListAdapter.addDeviceToAdapt(device)

            // TODO: Unknown Device 를 ScanList 에 포함 하지 않는 코드 사용 여부 결정 필요
            val device = result.device
            if (result.scanRecord?.deviceName == null){
                // deviceName 이 Null 인 경우, 스캔리스트에 추가 X
                return
//            if (result.scanRecord?.deviceName == null){
//                // DeviceName 이 Null 인 경우, 스캔리스트에 추가 X
//                return
            }else if (result.scanRecord?.deviceName == "Unknown Device"){
                return
            }else{
                scanListAdapter.addDeviceToAdapt(device)
            }
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
