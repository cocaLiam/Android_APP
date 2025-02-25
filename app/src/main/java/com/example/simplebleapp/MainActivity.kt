package com.example.simplebleapp

// Operator Pack
import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
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
import android.content.ComponentName
import android.content.Context
import android.os.Build

// dataType Pack

// Util Pack
import android.util.Log
import android.widget.EditText
import android.widget.ToggleButton
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer

// Custom Package
import com.example.simplebleapp.bleModules.ScanListAdapter
import com.example.simplebleapp.bleModules.BleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.simplebleapp.services.OnDestroyJobService
import com.example.simplebleapp.services.OnResumeJobService

class MainActivity : AppCompatActivity() {
    // 1. ActivityResultLauncher를 클래스의 멤버 변수로 선언합니다.
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private val bleController = BleController(this) // MainActivity는 Context를 상속받음

    private var scanListAdapter: ScanListAdapter = ScanListAdapter()

    private val MAIN_LOG_TAG = " - MainActivity "

    // View 변수 선언
    private lateinit var btnScanStart: Button
    private lateinit var btnParingCheck: Button
    private lateinit var btnRemoveParing: Button
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

    // 비동기 작업을 위한 코루틴 스코프(코루틴 전용스레드 공간 정도) 선언
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())

    // JobService 객체
    private lateinit var jobScheduler: JobScheduler
    private lateinit var onDestroyJobInfo: JobInfo
    private lateinit var onResumeJobInfo: JobInfo

    @SuppressLint("MissingPermission")
    private fun appCreate(){
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
        btnRemoveParing  = findViewById(R.id.btn_removeParing)
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
            val bondedDevices: Set<BluetoothDevice> = bleController.getParingDevices()
            Log.i(MAIN_LOG_TAG, "bondedDevices : $bondedDevices")
            Log.i(MAIN_LOG_TAG, "getConnectedDevices : ${bleController.getConnectedDevices()}")
            if (bondedDevices.isNotEmpty()){
                bleController.updateReadData("페어링된 기기 리스트 : ${bondedDevices} \n" +
                        "현재 연결된 기기 리스트 : ${bleController.getConnectedDevices()}")
            }else{
                bleController.updateReadData("페어링된 기기 리스트 : ${bondedDevices} \n" +
                        "현재 연결된 기기 리스트 : ${bleController.getConnectedDevices()}")
            }
        }

        // RemoveParing 버튼 클릭 리스너
        btnRemoveParing.setOnClickListener{
            val paringSet = bleController.getParingDevices()
            val target = paringSet.toList()[0]
            coroutineScope.launch {
                if(paringSet.isNotEmpty()){
                    bleController.removeParing(target.address)
                }
            }
            Toast.makeText(this, "ParingRemoved ${target.name}", Toast.LENGTH_SHORT).show()
        }


        // Disconnect 버튼 클릭 리스너
        btnDisconnect.setOnClickListener{
            coroutineScope.launch {
                bleController.disconnectAllDevices()
            }
            Toast.makeText(this, "Disconnect ALL", Toast.LENGTH_SHORT).show()
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
                coroutineScope.launch {
                    bleController.writeData(dataToSend, macAddress.address)
                }
//                bleController.writeData(dataToSend, macAddress.address) // BLE로 데이터 전송
                Toast.makeText(this, "Data Sent: $inputData", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter data to send", Toast.LENGTH_SHORT).show()
            }
        }

        // ( 트리거 : APP )
        // 기기에 Info Request 를 해서 받는 Read Data
        btnRequestReadData.setOnClickListener {
            for(tmp in bleController.getConnectedDevices()){
//                bleController.requestReadData(tmp.address)


                // onCharacteristicRead 에서 호출될 Lamda 함수 등록
                bleController.onRequestDataListener = { device, byteArrayString, status ->
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d("LambdaHandler", "READ DATA SUCCESS")
                    }else{
                        Log.w("LambdaHandler", "READ DATA FAIL")
                    }
                }

                coroutineScope.launch {
                    bleController.requestReadData(tmp.address)
                }
            }
//            val macAddress: BluetoothDevice = bleController.getConnectedDevices()[0]
//            bleController.requestReadData(macAddress.address)
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
// Job Service 초기화 ------------------------------------------------------------------------------
        jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

        // MainActivity 인스턴스를 JobService에 전달
        OnDestroyJobService.setMainActivity(this)
        OnResumeJobService.setMainActivity(this)

        // Job 설정
        onDestroyJobInfo = JobInfo.Builder(OnDestroyJobService.JOB_ID,
            ComponentName(this, OnDestroyJobService::class.java)
        )
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
// Job Service 초기화 ------------------------------------------------------------------------------
    }

    private fun appOpen(){
        // OnDestroyJobService JobService 취소 처리
        jobScheduler.cancel(OnDestroyJobService.JOB_ID)

        jobScheduler.cancel(OnResumeJobService.JOB_ID)
    }

    fun appPause(){
        // OnResumeJobService JobService 취소 처리
        jobScheduler.cancel(OnResumeJobService.JOB_ID)

        jobScheduler.cancel(OnDestroyJobService.JOB_ID)
    }

    fun appDestroy(){
        Log.i(MAIN_LOG_TAG, "appDestroy 실행 ")
        coroutineScope.launch {
            bleController.disconnectAllDevices()
        }
        // BLE SCAN STOP + recycleView Clear
        scanListAdapter.clearDevices()
        stopBleScanAndClearScanList()
        Log.i(MAIN_LOG_TAG, "appDestroy 종료 ")
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
                    connectToDeviceWithPermissionCheck(pairedDevice)
                }
            }
        } else {
            Log.w(OnResumeJobService.jobServiceLogTag, "페어링된 기기 없음")
            // 필요한 경우 여기서 다른 처리
        }
        Log.i(OnResumeJobService.jobServiceLogTag, "onResumeJobInfo SERVICE 실행 완료")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        Log.i(MAIN_LOG_TAG, "onCreate")
        Toast.makeText(this,"onCreate", Toast.LENGTH_SHORT).show()

        appCreate()
    }

    override fun onStart() {
        super.onStart()
        // 액티비티가 사용자에게 보여지기 직전에 호출
        // APP 시작시 자동 실행하는 작업들
        Log.i(MAIN_LOG_TAG, "onStart")
        Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        Log.i(MAIN_LOG_TAG, "onResume")
        Toast.makeText(this, "onResume", Toast.LENGTH_SHORT).show()

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
        Toast.makeText(this,"onPause", Toast.LENGTH_SHORT).show()

        appPause()
    }

    override fun onStop() {
        super.onStop()
        Log.i(MAIN_LOG_TAG, "onStop")
        Toast.makeText(this,"onStop", Toast.LENGTH_SHORT).show()

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
        Toast.makeText(this,"onDestroy", Toast.LENGTH_SHORT).show()

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
                ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> Log.e(MAIN_LOG_TAG, "OUT_OF_HARDWARE_RESOURCES")
                ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> Log.e(MAIN_LOG_TAG, "SCANNING_TOO_FREQUENTLY")
            }
            Toast.makeText(this@MainActivity, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }
    // BLE Connect 권한 검사 메서드
    @SuppressLint("MissingPermission")
    private fun connectToDeviceWithPermissionCheck(selectedDevice: BluetoothDevice) {
        if(bleController.requestBlePermission(this)){
            // 블루투스 권한이 이미 있는 경우
            val gtServer = bleController.connectToDevice(selectedDevice, { isConnected ->
                if (isConnected) {
                    Log.i(MAIN_LOG_TAG, "${selectedDevice.name} 기기 연결 성공")
//                    for(tmp in bleController.getConnectedDevices()){
//                        bleController.requestReadData(tmp.address)
//                    }
                } else {
                    Log.w(MAIN_LOG_TAG, "${selectedDevice.name} 기기 연결 실패")
                }
            })
            if(gtServer !=null) bleController.gtMap[selectedDevice.address] = gtServer
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
}
