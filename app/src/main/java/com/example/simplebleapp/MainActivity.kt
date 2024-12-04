package com.example.simplebleapp

// Operator Pack
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

// UI Pack
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

// BLE Pack
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent

// Util Pack
import android.util.Log
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer

// Custom Package
import com.example.simplebleapp.bleModules.ScanListAdapter
import com.example.simplebleapp.bleModules.BleController

class MainActivity : AppCompatActivity() {
    // 1. ActivityResultLauncher를 클래스의 멤버 변수로 선언합니다.
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private val bleController = BleController(this) // MainActivity는 Context를 상속받음
//    private val handler = Handler()

    private var scanListAdapter: ScanListAdapter = ScanListAdapter()
    private var isPopupVisible = false

    private val MAIN_LOG_TAG = " - MainActivity "

    // View 변수 선언
    private lateinit var btnScanStart: Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

// BLE 초기화 완료 -----------------------------------------------------------------------------------
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

        // 2_2. Launcher 객체 BleController 에 전달
        bleController.setBlePermissionLauncher(enableBluetoothLauncher)

        // 3. BLE 기능 검사
        bleController.checkBleOperator()

        // 권한 요청 메서드 수정
        fun permissionRequest(onPermissionGranted: () -> Unit) {
            val permissionOk = bleController.requestBlePermission(this)
            var allPermissionsGranted = true

            for ((key, value) in permissionOk) {
                if (!value) {
                    Log.e(MAIN_LOG_TAG, "권한 없음 : $key")
                    Toast.makeText(this, "${key}이 활성화되지 않았습니다.", Toast.LENGTH_SHORT).show()
                    allPermissionsGranted = false
                }
            }

            if (allPermissionsGranted) {
                // 모든 권한이 허용된 경우 콜백 실행
                onPermissionGranted()
            } else {
                Log.i(MAIN_LOG_TAG, "권한 요청 중... ${permissionOk}")
            }
        }


//        fun permissionRequest(): Boolean {
//            // 4. Bluetooth 가 비활성화 된 경우 활성화 요청
//            val permissionOk = bleController.requestBlePermission(this)
//            for ((key, value) in permissionOk) {
//                if (!value){
//                    Log.e(MAIN_LOG_TAG, "권한 없음 : ${key}")
//                    Toast.makeText(this, "${key}이 활성화되지 않았습니다.", Toast.LENGTH_SHORT).show()
//                    return false
//                }
//            }
//            return true
//        }

// BLE 초기화 완료 -----------------------------------------------------------------------------------

// UI 초기화 완료 ------------------------------------------------------------------------------------
        // activity_main.xml의 View 초기화
        btnScanStart = findViewById(R.id.btn_scan_start)

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

        // Scan Start 버튼 클릭 리스너
        btnScanStart.setOnClickListener {
            permissionRequest {
                // 권한이 허용된 경우에만 BLE 스캔 시작
                startBleScan()
            }
        }

        // 데이터 전송 버튼 클릭 리스너
        btnSendData.setOnClickListener {
            val inputData = etInputData.text.toString() // EditText에서 입력된 데이터 가져오기
            if (inputData.isNotEmpty()) {
                val dataToSend = inputData.toByteArray() // 문자열을 ByteArray로 변환
//                val hexString = dataToSend.joinToString(" ") { "%02x".format(it) }
                bleController.writeData(dataToSend) // BLE로 데이터 전송
                Toast.makeText(this, "Data Sent: $inputData", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter data to send", Toast.LENGTH_SHORT).show()
            }
        }

        // ( 트리거 : APP )
        // 기기에 Info Request 를 해서 받는 Read Data
        btnRequestReadData.setOnClickListener {
            bleController.requestReadData()
//            etOutputData.setText(bleController.getReadData())
        }

        // LiveData 관찰 설정
        bleController.readData.observe(this, Observer { newData ->
            // 데이터가 변경되면 UI 업데이트
            etOutputData.setText(newData)
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
                // 권한이 허용된 경우 BLE 장치 연결
                if (ActivityCompat.checkSelfPermission(  // 블루투스 커넥트 권한 검사
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {  // 블루투스 권한이 이미 있는 경우
                    bleController.connectToDevice(selectedDevice, { isConnected ->
                        if (isConnected) {
                            Log.i(MAIN_LOG_TAG, "BLE 장치에 성공적으로 연결되었습니다.")
                        } else {
                            Log.i(MAIN_LOG_TAG, "BLE 장치 연결이 해제되었습니다.")
                        }
                    })
                } else {  // 블루투스 권한이 없는 경우 권한 요청 후 다시 Connect
                    permissionRequest {
                        bleController.connectToDevice(selectedDevice, { isConnected ->
                            if (isConnected) {
                                Log.i(MAIN_LOG_TAG, "BLE 장치에 성공적으로 연결되었습니다.")
                            } else {
                                Log.i(MAIN_LOG_TAG, "BLE 장치 연결이 해제되었습니다.")
                            }
                        })
                    }
                }
                // Connect 후 팝업창 종료 + Scan 종료
                stopBleScanAndClearScanList()
            } else {
                Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show()
            }
        }
// UI 초기화 완료 ------------------------------------------------------------------------------------
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
                }
                102 -> { // 위치 권한 요청 결과
                    Log.i(MAIN_LOG_TAG, "위치 권한이 허용되었습니다.")
                }
                103 -> { // BLE 연결 권한 요청 결과
                    Log.i(MAIN_LOG_TAG, "블루투스 연결 권한이 허용되었습니다.")
                }
            }
        } else {
            // 권한이 거부된 경우
            when (requestCode) {
                101 -> {
                    Log.e(MAIN_LOG_TAG, "블루투스 스캔 권한이 거부되었습니다.")
                    Toast.makeText(this, "블루투스 스캔 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                102 -> {
                    Log.e(MAIN_LOG_TAG, "위치 권한이 거부되었습니다.")
                    Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
                103 -> {
                    Log.e(MAIN_LOG_TAG, "블루투스 연결 권한이 거부되었습니다.")
                    Toast.makeText(this, "블루투스 연결 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(MAIN_LOG_TAG, "onDestroy")
        bleController.disconnect()
        Toast.makeText(this, "onDestroy", Toast.LENGTH_SHORT).show()
        stopBleScanAndClearScanList()
        isPopupVisible = popupView.visibility == View.VISIBLE // 팝업 상태 저장
    }

    override fun onPause() {
        super.onPause()
        Log.i(MAIN_LOG_TAG, "onPause")
//        Toast.makeText(this, "onPause", Toast.LENGTH_SHORT).show()
        stopBleScanAndClearScanList()
        isPopupVisible = popupView.visibility == View.VISIBLE // 팝업 상태 저장
    }

    override fun onResume() { //TODO : 앱 켜지면 자동으로 스캔해서 연결까지 동작
        super.onResume()
        Log.i(MAIN_LOG_TAG, "onResume")
//        Toast.makeText(this, "onResume", Toast.LENGTH_SHORT).show()
//        if (isPopupVisible) { // 팝업 상태 복구
//            popupView.visibility = View.VISIBLE
//            popupContainer.visibility = View.VISIBLE
//            btnScanStart.visibility = View.GONE
//        } else if (scanResults.isEmpty()) { // 스캔 결과가 없으면 스캔 재개
//            startBleScan()
//        }
    }

    private fun startBleScan() {
        try {
            bleController.startBleScan(scanCallback, popupContainer)
            btnScanStart.visibility = View.GONE
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
