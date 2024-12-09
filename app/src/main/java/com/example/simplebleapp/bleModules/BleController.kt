package com.example.simplebleapp.bleModules

// Operator Pack
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.Manifest
import android.app.Activity
import android.os.Handler
import android.os.Build

// UI Pack
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import android.widget.Toast
import android.content.Context
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher

// BLE Pack
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import androidx.lifecycle.LiveData

// dataType Pack
import androidx.lifecycle.MutableLiveData
import java.lang.reflect.Method

// Util Pack
import java.util.UUID

// Custom Package

data class PermissionStatus(
    var bluetoothEnabled: Boolean = false,
    var locationPermission: Boolean = false,
    var bluetoothScanPermission: Boolean = false,
    var bluetoothConnectPermission: Boolean = false
)

data class BleDeviceInfo(
    var device: BluetoothDevice? = null,
    var deviceName: String? = null,
    var gatt: BluetoothGatt? = null,
    var writeCharacteristic: BluetoothGattCharacteristic? = null,
    var readCharacteristic: BluetoothGattCharacteristic? = null
)

class BleController(private val applicationContext: Context) {
    // BLE 관련 멤버 변수
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    // 수신 데이터를 LiveData로 관리
    private var _readData = MutableLiveData<Any>() // 내부에서만 수정 가능
    val readData: LiveData<Any> get() = _readData // 외부에서는 읽기만 가능

//    // UUID는 GATT 서비스와 특성을 식별하는 데 사용됩니다.
//    private val SERVICE_UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455") //
//    private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3") //
//    private val READ_CHARACTERISTIC_UUID = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616") //

//    // Microchip Keyboard
//    private val SERVICE_UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb") //
//    private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("00002a4e-0000-1000-8000-00805f9b34fb") //
//    private val READ_CHARACTERISTIC_UUID = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb") //

//    // pic32cx-bz 읽기 특성
//    private val SERVICE_UUID =              UUID.fromString("4d434850-5255-42d0-aef8-881facf4ceea")
//    private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("4d434850-5255-42d0-aef8-881fccf4ceea")
//    private val READ_CHARACTERISTIC_UUID =  UUID.fromString("4d434850-5255-42d0-aef8-881fbcf4ceea")

    // TRS Service ( peri_uart )
    private val serviceUuid = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
    private val writeCharacteristicUuid = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")
    private val readCharacteristicUuid = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")

    // ActivityResultLauncher를 클래스의 멤버 변수로 선언
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    // 스캔 제한 시간
    private val scanPeriod: Long = 10000
    private val logTagBleController = " - BleController"

    // 권한 상태를 저장하는 Map
    val permissionStatus = PermissionStatus()
    private var bluetoothGattMap: MutableMap<String, BleDeviceInfo> = mutableMapOf()

    /**
     * BLE 모듈 초기화
     */
    fun setBleModules() {
        // getSystemService는 Context가 완전히 초기화된 후에 호출되어야 함
        bluetoothManager = applicationContext.getSystemService(BluetoothManager::class.java)
//        bluetoothAdapter   = bluetoothManager.getAdapter()  // 이전 버전
        bluetoothAdapter = bluetoothManager.adapter
//        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner()   // 이전 버전
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    /**
     * BLE 권한 요청 런처 등록
     */
    fun setBlePermissionLauncher(launcher: ActivityResultLauncher<Intent>) {
        enableBluetoothLauncher = launcher
    }

    /**
     * BLE 지원 여부 확인
     */
    fun checkBleOperator() {
        val bluetoothLEAvailable =
            applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!bluetoothLEAvailable) {
            useToastOnSubThread("BLUETOOTH_LE 기능을 지원 안함")
        }
    }

    /**
     * BLE 권한 요청
     */
    fun requestBlePermission(activity: Activity): Boolean {
        //        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        hasBluetoothConnectPermission()

        // 1. 블루투스 활성화 요청 <System Setting>
        if(!permissionStatus.bluetoothEnabled){
            // BLE 권한 요청 런처 실행
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent) // registerForActivityResult로 처리
            permissionStatus.bluetoothEnabled = false // 활성화 여부는 런처 결과에서 처리
        }

        // 2. Android 12(API 31) 이상에서 BLE 스캔 권한 요청 <Permission Request>
        if(!permissionStatus.bluetoothScanPermission){
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                101 // 요청 코드
            )
            permissionStatus.bluetoothScanPermission = false
        }

        // 3. 위치 정보 권한 요청 <Permission Request>
        if(!permissionStatus.locationPermission){
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                102 // 요청 코드
            )
            permissionStatus.locationPermission = false
        }

        // 4. 블루투스 연결 권한 요청 <Permission Request>
        if(!permissionStatus.bluetoothConnectPermission){
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                103 // 요청 코드
            )
            permissionStatus.bluetoothConnectPermission = false
        }

        return hasBluetoothConnectPermission()
    }

    /**
     * BLE Scan 창 팝업
     */
    fun <T> bleScanPopup(scanCallback: ScanCallback, popupContainer: T) {
        startBleScan(scanCallback)

        // 10초 후 스캔 중지
        Log.i(logTagBleController, "스캔 타임아웃 제한시간 : ${scanPeriod / 1000}초 ")
        when (popupContainer) {
            is LinearLayout -> {  // 특정 팝업창에 Text로 UI 표현하는 경우
                popupContainer.postDelayed({
                    stopBleScan(scanCallback)
                }, scanPeriod)
            }

            is Handler -> {  // 일반 화면에 Text로 UI 표현하는 경우
                popupContainer.postDelayed({ // SCAN_PERIOD 시간후에 발동되는 지연 함수
                    Log.w(logTagBleController, "--스캔 타임아웃-- ")
                    stopBleScan(scanCallback)
                }, scanPeriod)
            }
        }
    }

    /**
     * BLE 스캔 시작
     */
    fun startBleScan(scanCallback: ScanCallback) {
        try {
            if (hasBluetoothConnectPermission()) {
                bluetoothLeScanner.startScan(scanCallback)
            }
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "startBleScan Failed: ${e.message}")
            // 권한이 없을 때의 처리 로직 추가
        }
    }

    /**
     * BLE 스캔 중지
     */
    fun stopBleScan(scanCallback: ScanCallback) {
        try {
            if (hasBluetoothConnectPermission()) {
                bluetoothLeScanner.stopScan(scanCallback)
            }
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "stopBleScan Failed: ${e.message}")
            // 권한이 없을 때의 처리 로직 추가
        }
    }

    /**
     * BLE 장치 연결
     */
    fun connectToDevice(device: BluetoothDevice, onConnectionStateChange: (Boolean) -> Unit) {
        // 블루투스 연결 권한 확인
        if (!hasBluetoothConnectPermission()) {
            return
        }
        try {
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                device.createBond() // 페어링 시도
            }
            // GATT 서버에 연결 시도
            device.connectGatt(applicationContext, false, object : BluetoothGattCallback() {
                // GATT 연결 상태가 변경되었을 때 호출되는 콜백
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    super.onConnectionStateChange(gatt, status, newState)
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            // GATT 서버에 연결 성공
                            Log.d(logTagBleController, "GATT 서버에 연결되었습니다.")

                            val bleDeviceInfo = BleDeviceInfo()
                            bleDeviceInfo.device = device
                            bleDeviceInfo.deviceName = device.name
                            bleDeviceInfo.gatt = gatt
                            bluetoothGattMap[device.address] = bleDeviceInfo
                            onConnectionStateChange(true)

                            if (hasBluetoothConnectPermission()) {
                                gatt.discoverServices() // GATT 서비스 검색
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            // GATT 서버 연결 해제
                            Log.d(logTagBleController, "GATT 서버 연결이 해제되었습니다.")
                            bluetoothGattMap.remove(device.address)
                            onConnectionStateChange(false)
                        }
                        BluetoothProfile.STATE_CONNECTING -> { }
                        BluetoothProfile.STATE_DISCONNECTING -> { }
                        else -> {
                            Log.w(logTagBleController, "알 수 없는 GATT 상태: $newState")
                            useToastOnSubThread( "알 수 없는 GATT 상태: $newState")
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    /**
                     * gatt.discoverServices() // GATT 서비스 검색 << 이후 발동 되는 함수
                     * */
                    super.onServicesDiscovered(gatt, status)

                    // GATT 서비스 검색
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(logTagBleController, "GATT 서비스 검색 실패: $status")
                        useToastOnSubThread("GATT 서비스 검색 실패: $status")
                        return
                    }
                    Log.d(logTagBleController, "GATT 서비스 검색 성공")

                    val charList = mutableListOf<Any>()
                    // 검색된 모든 서비스와 특성을 로그로 출력
                    for (service in gatt.services) {
                        Log.d(logTagBleController, "서비스 UUID: ${service.uuid}")
                        charList.add("서비스 : ${service.uuid}")
                        for (characteristic in service.characteristics) {
                            charList.add("${getPropertiesString(characteristic.properties)} >> ${characteristic.uuid}")
                            Log.d(logTagBleController, "  특성 UUID: ${characteristic.uuid}")
                        }
                    }
                    updateReadData(charList)

                    // 서비스 UUID 찾기
                    val service = gatt.getService(serviceUuid)
                    if (service == null) {
                        Log.e(logTagBleController, "특정 서비스를 찾을 수 없습니다: $serviceUuid")
                        useToastOnSubThread("특정 서비스를 찾을 수 없습니다: $serviceUuid")
                        return // service 없으면 read 고 write 고 특성 찾기 종료 처리
                    }
                    Log.d(logTagBleController, "특정 서비스 발견: $serviceUuid")

                    val bleInfo: BleDeviceInfo = bluetoothGattMap[device.address]!!
                    // 쓰기 특성 등록
                    bleInfo.writeCharacteristic = service.getCharacteristic(writeCharacteristicUuid).apply {
                        if (this ==null) Log.e(logTagBleController, "쓰기 특성을 찾을 수 없습니다.")
                    }

                    // 읽기 특성 등록
                    bleInfo.readCharacteristic = service.getCharacteristic(readCharacteristicUuid).apply {
                        if (this ==null) Log.e(logTagBleController, "일기 특성을 찾을 수 없습니다.")
                    }

                    // Notification Subscribe 기능 사용 할 일이 없으므로 주석처리
//                    // CCCD 설정 // Notification Subscribe
//                    if (bleInfo.readCharacteristic != null) {
//                        gatt.setCharacteristicNotification(
//                            bleInfo.readCharacteristic, true)
//
//                        // CCCD 설정 // Subscribe 요청 --> IoT
//                        val descriptor =
//                            bleInfo.readCharacteristic!!.getDescriptor(
//                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
//                        descriptor!!.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//                        gatt.writeDescriptor(descriptor)
//                        Log.d(logTagBleController, "읽기 특성에 대한 Notification 활성화 완료")
//                    }
                }

                // ( 트리거 : IoT기기 ) 기기가 Data 전송 > App 이 읽음
                // 읽기 특성에 대한 알림(Notification)이 활성화된 경우, 데이터가 수신될 때 호출되는 콜백
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    recievedData: ByteArray
                ) {
                    super.onCharacteristicChanged(gatt, characteristic, recievedData)
                    if (characteristic.uuid == readCharacteristicUuid) {
                        val receivedString = String(recievedData) // ByteArray를 문자열로 변환
                        Log.i(logTagBleController, "수신된 데이터: $recievedData")
                        // LiveData 업데이트
                        updateReadData(receivedString)
                        // Toast로 수신된 데이터 표시
                        useToastOnSubThread("Received Data: $receivedString")
                    }
                }

                // ( 트리거 : APP ) App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음
                // 기기에 Info Request 를 해서 받는 Read Data
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    recievedData: ByteArray,
                    status: Int
                ) {
                    super.onCharacteristicRead(gatt, characteristic, recievedData, status)
                    Log.i(logTagBleController, "수신된 데이터: $recievedData \n" +
                            "status : $status")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // 읽은 데이터 가져오기
                        val data = recievedData

                        // ByteArray를 문자열로 변환
                        val byteArrayString = String(data) // 기본적으로 UTF-8로 변환
                        Log.i(logTagBleController, "수신된 데이터 (String): $byteArrayString")

                        // UTF-8로 변환
                        val utf8String = String(data, Charsets.UTF_8)
                        Log.i(logTagBleController, "수신된 데이터 (UTF-8): $utf8String")

//                    // EUC-KR로 변환
//                    val eucKrString = String(data, Charsets.EUC_KR)
//                    Log.i(BLECONT_LOG_TAG, "수신된 데이터 (EUC-KR): $eucKrString")

                        // ASCII로 변환
                        val asciiString = String(data, Charsets.US_ASCII)
                        Log.i(logTagBleController, "수신된 데이터 (ASCII): $asciiString")

                        // Hexadecimal로 출력
                        val hexString = data.joinToString(" ") { String.format("%02X", it) }
                        Log.i(logTagBleController, "수신된 데이터 (Hex): $hexString")

                        updateReadData(hexString)
                        // UI 스레드에서 Toast 표시
                        useToastOnSubThread("읽은 데이터: $hexString")
                    } else {
                        Log.e(logTagBleController, "데이터 읽기 실패: $status")
                    }
                }

                // 데이터를 썼을 때 호출되는 콜백
                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    super.onCharacteristicWrite(gatt, characteristic, status)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // 데이터 전송 성공
                        Log.i(logTagBleController, "데이터 전송 성공: ${String(characteristic.value)}")
                    } else {
                        // 데이터 전송 실패
                        Log.e(logTagBleController, "데이터 전송 실패: $status")
                    }
                }

            })
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "블루투스 연결 시도 중 보안 예외 발생: ${e.message}")
        }
    }

    /**
     * 데이터 쓰기
     */
    fun writeData(data: ByteArray, macAddress: String ) {
        try {
            val bleInfo: BleDeviceInfo = bluetoothGattMap[macAddress] ?: return
            bleInfo.gatt ?: return

            if (hasBluetoothConnectPermission()) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bleInfo.gatt!!.writeCharacteristic(
                            bleInfo.writeCharacteristic!!,
                            data,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        )
                    } else {
                        // Android 13 (API 33) 미만
                        bleInfo.writeCharacteristic!!.value = data
                        bleInfo.writeCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        bleInfo.gatt!!.writeCharacteristic(bleInfo.writeCharacteristic!!)
                    }

                } catch (e: Exception) {
                    Log.e(logTagBleController, "데이터 전송 실패 ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "블루투스 연결 시도 중 보안 예외 발생: ${e.message}")
        }
    }

    /**
     * App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음
     * --> onCharacteristicRead 오버라이드 함수 호출
     */
    fun requestReadData(macAddress: String) {
        try {
            val bleInfo: BleDeviceInfo = bluetoothGattMap[macAddress] ?: return
            bleInfo.gatt ?: return

            if (hasBluetoothConnectPermission()) {
                try {
                    bleInfo.gatt!!.readCharacteristic(bleInfo.readCharacteristic) // 읽기 요청
                } catch (e: Exception) {
                    Log.e(logTagBleController, "Indicator 실패 : ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "블루투스 연결 시도 중 보안 예외 발생: ${e.message}")
        }
    }

    /**
     * 페어링된 기기 목록 가져오기
     */
    fun getBondedDevices(): Set<BluetoothDevice>? {
        if(!::bluetoothAdapter.isInitialized){
            return null
        }
        if (hasBluetoothConnectPermission()){
            try {
                return bluetoothAdapter.bondedDevices
            } catch (e: SecurityException) {
                Log.e(logTagBleController, "bluetoothAdapter.bondedDevices Failed : ${e.message}")
                return null
            }
        }
        return null
    }

    /**
     * 현재 연결된 BLE 기기 목록 가져오기
     */
    fun getConnectedDevices(): List<BluetoothDevice> {
        if(!::bluetoothManager.isInitialized){
            return emptyList()
        }
        if (hasBluetoothConnectPermission()){
            try {
                return bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            } catch (e: SecurityException) {
                Log.e(logTagBleController, "getConnectedDevices : ${e.message}")
                return emptyList()
            }
        }
        return emptyList()
    }

//    /**
//     * 특정 기기 연결 해제
//     */
//    fun disconnectDevice(device: BluetoothDevice) {
//        val gatt:BluetoothGatt = bluetoothGattMap[device] ?: return
//        if (hasBluetoothConnectPermission()){
//            try {
//                gatt.disconnect()
//                gatt.close()
//                bluetoothGattMap.remove(device)
//            } catch (e: SecurityException) {
//                Log.e(logTagBleController, "disconnectDevice Failed ${e.message}")
//            }
//        }
//    }

    /**
     * 모든 기기 연결 해제
     */
    fun disconnectAllDevices() {
        Log.i(logTagBleController, "모든 기기 연결 해제 시도 : $bluetoothGattMap")
        useToastOnSubThread("모든 기기 연결 해제 시도 : $bluetoothGattMap")

        val iterator = bluetoothGattMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val device = entry.key
            val deviceInfo:BleDeviceInfo = entry.value

            if (hasBluetoothConnectPermission()) {
                try {
                    deviceInfo.gatt?.disconnect()
                    deviceInfo.gatt?.close()
                    iterator.remove() // Iterator의 remove() 메서드를 사용하여 안전하게 제거
                } catch (e: SecurityException) {
                    Log.e(
                        logTagBleController, "disconnectAllDevices Failed ${e.message}" +
                                "해제 실패 : $device"
                    )
                }
            }
        }

        Log.i(logTagBleController, "모든 기기 연결 해제 결과 : $bluetoothGattMap")
    }

    /**
     * 페어링된 기기 삭제
     */
    fun removeBond(macAddress: String): Boolean {
        try {
            val bleInfo: BleDeviceInfo = bluetoothGattMap[macAddress] ?: return false
            bleInfo.device ?: return false

            val method: Method = bleInfo.device!!.javaClass.getMethod("removeBond")
            return method.invoke(bleInfo.device) as Boolean
        } catch (e: Exception) {
            Log.e(logTagBleController, "페어링된 기기 삭제 실패: ${e.message}")
            return false
        }
    }

    /**
     * Bluetooth 활성화 요청 결과 처리
     */
    fun handleBluetoothIntentResult(result: ActivityResult) {
        // 특정 작업(예: 권한 요청, 다른 Activity 호출 등)의 결과를 처리할 Callback 함수
        if (result.resultCode == Activity.RESULT_OK) {
            Log.i(logTagBleController, "블루투스가 활성화되었습니다.")
            permissionStatus.bluetoothEnabled = true
        } else {
            Log.i(logTagBleController, "블루투스 활성화가 취소되었습니다.")
            permissionStatus.bluetoothEnabled = false
        }
    }

    /**
     * 유틸성 함수들
     */
    fun useToastOnSubThread(msg: String) {
        // Toast 같은 UI 제어 함수들은 꼭 UI스레드(Main쓰레드) 에서 호출되야 발동한다.
        //Handler(applicationContext.mainLooper).post { ... } << applicationContext.mainLooper(UI쓰레드) 를 가져옴
        Handler(applicationContext.mainLooper).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 데이터를 업데이트하는 메서드
    fun updateReadData(data: Any) {
        _readData.postValue(data) // LiveData에 새로운 데이터 설정
    }

    fun getPropertiesString(properties: Int): String {

        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) {
            return "BROADCAST"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            return "READ"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            return "WRITE_NO_RESPONSE"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            return "WRITE"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            return "NOTIFY"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            return "INDICATE"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
            return "SIGNED_WRITE"
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) {
            return "EXTENDED_PROPS"
        }
        return "Unknown"
    }

    // 권한 확인 함수
    fun hasBluetoothConnectPermission(): Boolean {
        /**
         * 안드로이드 S 이상 (최신)
         *  - 필요 권한 : 블루투스 활성화, ACCESS_FINE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT
         * 안드로이드 S 미만 (구형)
         *  - 필요 권한 : 블루투스 활성화, ACCESS_FINE_LOCATION
         *  - 불필요 권한 : BLUETOOTH_SCAN, BLUETOOTH_CONNECT
         * */

        // 블루투스 활성화
        permissionStatus.bluetoothEnabled = bluetoothAdapter.isEnabled

        // ACCESS_FINE_LOCATION
        permissionStatus.locationPermission = ContextCompat.checkSelfPermission( applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // 최신폰 일 경우
            // BLUETOOTH_SCAN
            permissionStatus.bluetoothScanPermission = ContextCompat.checkSelfPermission( applicationContext,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

            // BLUETOOTH_CONNECT
            permissionStatus.bluetoothConnectPermission = ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        } else{ // Android S 미만인 구형 폰 인 경우
            // BLUETOOTH_SCAN
            permissionStatus.bluetoothScanPermission = true
            // BLUETOOTH_CONNECT
            permissionStatus.bluetoothConnectPermission = true
        }

        // 모든 권한이 허용되었는지 확인
        val hasAllPermissions = listOf(
            permissionStatus.bluetoothEnabled,
            permissionStatus.locationPermission,
            permissionStatus.bluetoothScanPermission,
            permissionStatus.bluetoothConnectPermission
        ).all { it == true }

        if (!hasAllPermissions) {
            // 권한이 하나라도 부족한 경우 Toast 메시지 표시
            val missingPermissions = listOf(
                "블루투스 활성화" to permissionStatus.bluetoothEnabled,
                "ACCESS_FINE_LOCATION" to permissionStatus.locationPermission,
                "BLUETOOTH_SCAN" to permissionStatus.bluetoothScanPermission,
                "BLUETOOTH_CONNECT" to permissionStatus.bluetoothConnectPermission
            ).filter { !it.second }
                .map { it.first }
                .joinToString(", ")

            // useToastOnSubThread 함수 사용
            useToastOnSubThread("$missingPermissions 권한 필요함")
        }

        return hasAllPermissions
    }
}