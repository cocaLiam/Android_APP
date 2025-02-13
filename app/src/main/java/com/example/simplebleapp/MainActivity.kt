package com.example.simplebleapp

// Operator Pack
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// UI Pack
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import android.content.SharedPreferences
import android.content.Intent

// BLE Pack
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build

// dataType Pack

// Util Pack
import android.util.Log
import android.widget.EditText
import android.widget.ToggleButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer

// Custom Package
import com.example.simplebleapp.bleModules.ScanListAdapter
import com.example.simplebleapp.bleModules.BleController

class MainActivity : AppCompatActivity() {
    // 1. ActivityResultLauncher를 클래스의 멤버 변수로 선언합니다.
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private val bleController = BleController(this) // MainActivity는 Context를 상속받음

    private var scanListAdapter: ScanListAdapter = ScanListAdapter()

    private val MAIN_LOG_TAG = " - MainActivity "

    // View 변수 선언
    private lateinit var btnScanStart: Button
    private lateinit var btnParingCheck: Button
    private lateinit var btnDisconnect : Button
    private lateinit var toggleBtnAutoConnect : ToggleButton
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var btnConnect: Button
    private lateinit var btnClose: Button
    private lateinit var popupContainer: LinearLayout
    private lateinit var recyclerScanList: RecyclerView
    private lateinit var popupView: View
    // Data Send, receive Button 및 Text 입력창
    private lateinit var etInputData: EditText
    private lateinit var etOutputData: EditText
    private lateinit var btnSendData: Button
    private lateinit var btnRequestReadData: Button

    // AutoConnection 처리 용도 ( onResume 에서 x초 후 연결 시도, onDestroy 에서 자동 연결 시도 콜백 취소 처리 )
    private val handler = MyContextData.handler
    private val delayOnResumeCallback = Runnable {
        val pairedDevices = bleController.getParingDevices() ?: return@Runnable
        if (pairedDevices.isNotEmpty()) {
            Log.i(MAIN_LOG_TAG, "페어링된 기기 발견: ${pairedDevices}")
            // 바로 연결 시도
            for (pairedDevice in pairedDevices) {
                val conDeviceList = bleController.getConnectedDevices()
//                if(!(pairedDevice in conDeviceList)){}
                if (!conDeviceList.contains(pairedDevice)) {
                    // 이미 연결되어 있다면 재연결 시도 X
                    connectToDeviceWithPermissionCheck(pairedDevice)
                }
            }
        } else {
            Log.i(MAIN_LOG_TAG, "페어링된 기기 없음")
            // 필요한 경우 여기서 다른 처리
        }
        Log.i(MAIN_LOG_TAG, "onResume END")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

// BLE 초기화 완료 -----------------------------------------------------------------------------------
//        // 1. AutoConnect 지연용 Handler 객체 선언
//        handler = Handler(Looper.getMainLooper())

        // 2. BluetoothManager 및 BluetoothAdapter 초기화
        bleController.setBleModules()

        // 3. 권한요청 Launcher 등록
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

        // 4. BLE 권한 요청 런처 BleController 에 전달
        bleController.setBlePermissionLauncher(enableBluetoothLauncher)

        // 5. BLE 지원 여부 확인
        bleController.checkBleOperator()

// BLE 초기화 완료 -----------------------------------------------------------------------------------

// UI 초기화 완료 ------------------------------------------------------------------------------------
        // activity_main.xml의 View 초기화
        btnScanStart = findViewById(R.id.btn_scan_start)
        btnParingCheck = findViewById(R.id.btn_paring_check)
        btnDisconnect  = findViewById(R.id.btn_disconnect)
        toggleBtnAutoConnect = findViewById(R.id.toggle_auto_connect)
        sharedPreferences = getSharedPreferences("ToggleStatusStorage", MODE_PRIVATE)

        // activity_main.xml의 루트 레이아웃 가져오기
        val rootLayout =
            findViewById<RelativeLayout>(R.id.root_layout) // activity_main.xml의 루트 레이아웃 ID

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

        // 데이터 입력창 및 버튼 초기화
        btnSendData = findViewById(R.id.btn_send_data)
        etInputData = findViewById(R.id.et_input_data)
        btnRequestReadData = findViewById(R.id.btn_request_read_data)
        etOutputData = findViewById(R.id.et_output_data)

        // RecyclerView 초기화
        scanListAdapter.setupRecyclerView(recyclerScanList, this@MainActivity)

        // 팝업을 루트 레이아웃에 추가
        rootLayout.addView(popupView)

        bleController.requestBlePermission(this)
        // Scan Start 버튼 클릭 리스너
        btnScanStart.setOnClickListener {
            if(bleController.requestBlePermission(this)){
                // 권한이 허용된 경우에만 BLE 스캔 시작
                startBleScan()
            }
        }

        // Paring check 버튼 클릭 리스너
        btnParingCheck.setOnClickListener {
            val bondedDevices: Set<BluetoothDevice>? = bleController.getParingDevices()
            Log.i(MAIN_LOG_TAG, "bondedDevices : $bondedDevices")
            Log.i(MAIN_LOG_TAG, "getConnectedDevices : ${bleController.getConnectedDevices()}")
            if (bondedDevices == null){
                bleController.updateReadData("")
            }else{
                bleController.updateReadData("페어링된 기기 리스트 : ${bondedDevices} \n" +
                        "현재 연결된 기기 리스트 : ${bleController.getConnectedDevices()}")
            }
        }

        // Disconnect 버튼 클릭 리스너
        btnDisconnect.setOnClickListener{
            bleController.disconnectAllDevices()
        }

        // 저장된 상태 불러오기
        val toggleState = sharedPreferences.getBoolean("toggleAccessKey", false)
        toggleBtnAutoConnect.isChecked = toggleState
        // ToggleButton의 상태 변경 리스너 설정
        toggleBtnAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            // 상태 저장
            with(sharedPreferences.edit()) {
                putBoolean("toggleAccessKey", isChecked)
                apply()
            }

            if (isChecked) {
                // ToggleButton이 ON 상태일 때 실행할 코드
                Toast.makeText(this, "자동 연결 모드 ON", Toast.LENGTH_SHORT).show()
            } else {
                // ToggleButton이 OFF 상태일 때 실행할 코드
                Toast.makeText(this, "자동 연결 모드 OFF", Toast.LENGTH_SHORT).show()
            }
        }

        // 데이터 전송 버튼 클릭 리스너
        btnSendData.setOnClickListener {
            val inputData = etInputData.text.toString() // EditText에서 입력된 데이터 가져오기
            if (inputData.isNotEmpty()) {
                val dataToSend = inputData.toByteArray() // 문자열을 ByteArray로 변환
                val macAddress: BluetoothDevice = bleController.getConnectedDevices()[0]
                bleController.writeData(dataToSend, macAddress.address) // BLE로 데이터 전송
                Toast.makeText(this, "Data Sent: $inputData", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter data to send", Toast.LENGTH_SHORT).show()
            }
        }

        // ( 트리거 : APP )
        // 기기에 Info Request 를 해서 받는 Read Data
        btnRequestReadData.setOnClickListener {
            val macAddress: BluetoothDevice = bleController.getConnectedDevices()[0]
            bleController.requestReadData(macAddress.address)
        }

        // LiveData 관찰 설정
        bleController.readData.observe(this, Observer { newData ->
            // 데이터가 변경되면 UI 업데이트
            if ( newData is String){
                etOutputData.setText(newData)
            }else{
                etOutputData.setText(newData.toString())
            }
        })

        // Close 버튼 클릭 리스너
        btnClose.setOnClickListener {
            stopBleScanAndClearScanList()
        }

        // Connect 버튼 클릭 리스너
        btnConnect.setOnClickListener {
            val selectedDevice = scanListAdapter.getSelectedDevice()
            if (selectedDevice != null) {  // unknown Device 의 경우
                Toast.makeText(this, "Selected: ${selectedDevice.name}", Toast.LENGTH_SHORT).show()
                connectToDeviceWithPermissionCheck(selectedDevice)
                // Connect 후 팝업창 종료 + Scan 종료
                stopBleScanAndClearScanList()
            } else {
                Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
            }
        }

// UI 초기화 완료 ------------------------------------------------------------------------------------
    }

    // BLE Connect 권한 검사 메서드
    private fun connectToDeviceWithPermissionCheck(selectedDevice: BluetoothDevice) {
        if(bleController.requestBlePermission(this)){
            // 블루투스 권한이 이미 있는 경우
            bleController.connectToDevice(selectedDevice, { isConnected ->
                if (isConnected) {
                    Log.i(MAIN_LOG_TAG, "${selectedDevice.name} 기기 연결 성공")
                } else {
                    Log.w(MAIN_LOG_TAG, "${selectedDevice.name} 기기 연결 실패")
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
                    Toast.makeText(this, "블루투스 스캔 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                102 -> {
                    Log.e(MAIN_LOG_TAG, "위치 권한이 거부되었습니다.")
                    bleController.permissionStatus.locationPermission = false
                    Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                103 -> {
                    Log.e(MAIN_LOG_TAG, "블루투스 연결 권한이 거부되었습니다.")
                    bleController.permissionStatus.bluetoothConnectPermission = false
                    Toast.makeText(this, "블루투스 연결 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

//    @RequiresApi(Build.VERSION_CODES.P)
    override fun onDestroy() {
        super.onDestroy()
        Log.i(MAIN_LOG_TAG, "onDestroy START")
        Toast.makeText(this, "onDestroy", Toast.LENGTH_SHORT).show()

/*
* onResume -> onDestroy 시에는 onResume의 delayOnResumeCallback 지연 함수가 잘 취소가 되는데
* onDestroy -> onResume시에는 onDestroy 의 delayOnDestroyCallback 지연 함수는 취소가 안됨
* ( 이유 : onResume 하면서 delayOnDestroyCallback 의 참조가 새로 할당 되므로 )
* */
        // onResume 자동 연결 콜백 취소 처리
        handler.removeCallbacks(delayOnResumeCallback)

        // Callback 메모리 해제 + handler 객체
        bleController.disconnectAllDevices()
        handler.removeCallbacksAndMessages(null)
//        handler = null

        // BLE SCAN STOP + recycleView Clear
        scanListAdapter.clearDevices()
        stopBleScanAndClearScanList()
        Log.i(MAIN_LOG_TAG, "onDestroy END")
    }

    override fun onPause() {
        super.onPause()
        Log.i(MAIN_LOG_TAG, "onPause")
        stopBleScanAndClearScanList()
    }

//    @RequiresApi(Build.VERSION_CODES.P)
    override fun onResume() { //TODO : 앱 켜지면 자동으로 스캔해서 연결까지 동작
        super.onResume()
        Log.i(MAIN_LOG_TAG, "onResume START")
        Toast.makeText(this, "onResume", Toast.LENGTH_SHORT).show()

        if (!(toggleBtnAutoConnect.isChecked)){
            return
        }

        // onDestroy 연결 유지 콜백 취소 처리
//        handler.removeCallbacks(delayOnDestroyCallback)
//        handler.removeCallbacksAndMessages("onDestroy")
        // onResume 자동 연결 콜백
        handler.postDelayed(delayOnResumeCallback, 2000)
//        handler.postDelayed(delayOnResumeCallback,"onResume", 2000)
    }

    private fun startBleScan() {
        try {
            bleController.bleScanPopup(scanCallback, popupContainer)
            btnScanStart.visibility = View.GONE
            btnParingCheck.visibility = View.GONE
            toggleBtnAutoConnect.visibility = View.GONE
            popupView.visibility = View.VISIBLE
            popupContainer.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(MAIN_LOG_TAG, "Failed to stop BLE scan: ${e.message}")
        }
    }

    private fun stopBleScanAndClearScanList() {
        try {
            bleController.stopBleScan(scanCallback)
            Log.i(MAIN_LOG_TAG, "블루투스 스캔 정지 ")
            btnScanStart.visibility = View.VISIBLE // Scan Start 버튼 활성화
            btnParingCheck.visibility = View.VISIBLE
            toggleBtnAutoConnect.visibility = View.VISIBLE
            popupView.visibility = View.GONE // 팝업 숨김
            popupContainer.visibility = View.GONE // 팝업 컨테이너 숨김
            scanListAdapter.clearDevices()
        } catch (e: Exception) {
            Log.e(MAIN_LOG_TAG, "Failed to stop BLE scan: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (result.scanRecord?.deviceName == null){
                // DeviceName 이 Null 인 경우, 스캔리스트에 추가 X
                return
            }else{
                scanListAdapter.addDeviceToAdapt(device)
            }
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
