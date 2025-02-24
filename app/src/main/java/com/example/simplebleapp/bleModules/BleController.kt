package com.example.simplebleapp.bleModules

// Android 기본 패키지
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.app.Activity
import android.os.Handler
import android.os.Build
import android.content.Context

// 비동기 함수 ( await 용 )
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay


// UI Pack
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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

// WebView Pack

// DataClass Pack

// Util Pack
import android.annotation.SuppressLint
import android.net.MacAddress
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredFunctions

// Custom Packag

class BleController(private val context: Context) {
    // BLE 관련 멤버 변수
    private lateinit var bluetoothManager: BluetoothManager
    lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner

    // 수신 데이터를 LiveData로 관리
    private var _readData = MutableLiveData<Any>() // 내부에서만 수정 가능
    val readData: LiveData<Any> get() = _readData // 외부에서는 읽기만 가능

    //    // UUID는 GATT 서비스와 특성을 식별하는 데 사용됩니다.
    // TRS Service ( peri_uart )
    private val pushBotUuid = PushBotUuid()
    private val serviceUuid = UUID.fromString(pushBotUuid.serviceUuid)
    private val writeCharacteristicUuid = UUID.fromString(pushBotUuid.writeCharacteristicUuid)
    private val readCharacteristicUuid = UUID.fromString(pushBotUuid.readCharacteristicUuid)

//    // Service : 2
//    private val serviceUuid = UUID.fromString("40327de3-c2a8-6691-4a49-68859ff6075c")
//    private val readCharacteristicUuid = UUID.fromString("40327de3-c2a8-6691-4a49-68859ff6076c")
//    private val writeCharacteristicUuid = UUID.fromString("40327de3-c2a8-6691-4a49-68859ff6077c")

    // ActivityResultLauncher를 클래스의 멤버 변수로 선언
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    // 스캔 제한 시간
    private val scanPeriod: Long = 5000   // 1000당 1초
    private val logTagBleController = " - BleController"

    // 권한 상태를 저장하는 Map
    val permissionStatus = PermissionStatus()
    var onRequestDataListener: ((device: BluetoothDevice, receivedData: String, status: Int) -> Unit)? =
        null

    // 스레드 안전한 맵 [ 자체적으로 Lock 기능 ( Auto 뮤텍스 기능 정도 ) ]
    //    private var bluetoothGattMap: ConcurrentHashMap<String, BleDeviceInfo> = ConcurrentHashMap()
    // <application 단에서 다루는 Context Data ( 앱이 완전히 종료 되기 전까지 Data 를 보유함 )
    private var gtMap: ConcurrentHashMap<String, BluetoothGatt> = ConcurrentHashMap()

    /**
     * BLE 모듈 초기화
     */
    fun setBleModules() {
        // getSystemService는 Context가 완전히 초기화된 후에 호출되어야 함
        bluetoothManager = context.getSystemService(BluetoothManager::class.java)
//        bluetoothAdapter   = bluetoothManager.getAdapter()  // 이전 버전
        bluetoothAdapter = bluetoothManager.adapter
//        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner()   // 이전 버전
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        /*
BluetoothManager
    블루투스 전반을 관리하는 시스템 서비스
    주요 기능:
    블루투스 어댑터 관리
    연결된 기기들의 상태 관리
    블루투스 프로필 관리
    getConnectedDevices(): 현재 연결된 기기 목록 조회
    getConnectionState(): 특정 기기의 연결 상태 확인
BluetoothAdapter
    실제 블루투스 하드웨어와의 통신을 담당
    주요 기능:
    enable()/disable(): 블루투스 켜기/끄기
    isEnabled(): 블루투스 활성화 상태 확인
    getRemoteDevice(): MAC 주소로 블루투스 기기 객체 가져오기
    startDiscovery()/cancelDiscovery(): 기기 검색 시작/중지
    getBondedDevices(): 페어링된 기기 목록 조회
BluetoothLeScanner
    BLE(Bluetooth Low Energy) 기기 스캔을 전문적으로 담당
    주요 기능:
    startScan(): BLE 기기 스캔 시작
    stopScan(): 스캔 중지
    스캔 필터 설정
    스캔 주기 설정
    스캔 모드 설정 (저전력/균형/저지연)
    스캔 결과 콜백 처리
        * */
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
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (!bluetoothLEAvailable) {
            useToastOnSubThread("BLUETOOTH_LE 기능을 지원 안함")
        }
    }

    /**
     * BLE 권한 요청
     */
    @SuppressLint("InlinedApi")
    fun requestBlePermission(mainActivity: Activity): Boolean {
        hasBluetoothConnectPermission()

        // 1. 블루투스 활성화 요청 <System Setting>
        if(!permissionStatus.bluetoothEnabled){
            // BLE 권한 요청 런처 실행
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent) // registerForActivityResult로 처리
            permissionStatus.bluetoothEnabled = false // 활성화 여부는 런처 결과에서 처리
        }

        // 2. Android 12(API 31) 이상에서 BLE 스캔 권한 요청 <Permission Request>
        if (!permissionStatus.bluetoothScanPermission) {
            ActivityCompat.requestPermissions(
                mainActivity,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                101 // 요청 코드
            )
            permissionStatus.bluetoothScanPermission = false
        }

        // 3. 위치 정보 권한 요청 <Permission Request>
        if (!permissionStatus.locationPermission) {
            ActivityCompat.requestPermissions(
                mainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                102 // 요청 코드
            )
            permissionStatus.locationPermission = false
        }

        // 4. 블루투스 연결 권한 요청 <Permission Request>
        if (!permissionStatus.bluetoothConnectPermission) {
            ActivityCompat.requestPermissions(
                mainActivity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                103 // 요청 코드
            )
            permissionStatus.bluetoothConnectPermission = false
        }

        return hasBluetoothConnectPermission()
    }

    /* GET 함수 -----------------------------------------------------------------------------------*/
    fun getBluetoothDevice(macAddress: String): BluetoothDevice? {
        return try {
            bluetoothAdapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            Log.e(logTagBleController, "Get 함수 실패 : ${macAddress} << getBluetoothDevice")
            return null
        }
    }

    fun removeGattServer(macAddress: String, msg: String) {
        Log.i(logTagBleController,"삭제 요청자 MSG : $msg")
        Log.i(logTagBleController, "GATT MAP 삭제 시도 : ${gtMap.keys().toList()}")
        try {
            gtMap[macAddress]?.disconnect()
            gtMap[macAddress]?.close()  // DISCONNECT 콜백에서 호출로 변경
            gtMap.remove(macAddress)
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return
        } catch (e:Exception){
            Log.e(logTagBleController, "removeGattServer 함수 실패 : ${macAddress} : ${e.message}")
            return
        }
        Log.i(logTagBleController, "GATT MAP 삭제 결과 : ${gtMap.keys().toList()}")
    }

    fun reDiscoverGattService(gt: BluetoothGatt){
        try {
            gt.discoverServices()
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return
        } catch (e:Exception){
            Log.e(logTagBleController, "reDiscoverGattService 함수 실패 : ${e.message}")
            return
        }
    }

    fun getGattServer(macAddress: String): BluetoothGatt? {
        try {
            val device: BluetoothDevice = getBluetoothDevice(macAddress) ?: throw Exception("getBluetoothDevice 실패")
            val gt: BluetoothGatt? = gtMap[macAddress]

            //TODO : 디버깅중 << 무조건 새로 GATT 연결하게끔 해서 테스트중
            // 기존 연결이 있고 정상적으로 연결된 상태인 경우
//            if (gtServer?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
            if (bluetoothManager.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
                if(gt != null){
                    Log.i(logTagBleController,"GATT 기존 서버 사용 $gt")
                    return gt
                } else Log.w(logTagBleController,"GATT 서버 재접속 $gt")
            }

            // 연결이 없거나 끊어진 경우 새로 연결
            device.let { bleDevice ->
                val newGt: BluetoothGatt? = connectToDevice(bleDevice, { isConnected ->
                    Log.i(logTagBleController, "isConnected : $isConnected")
                    if (isConnected) {
                        Log.i(logTagBleController, "${device.name} 기기 연결 성공")
                    } else {
                        Log.e(logTagBleController, "${device.name} 기기 연결 끊어짐")
                    }
                })

                Log.d(logTagBleController,"gtMap $gtMap")
                if (newGt != null) {
                    gtMap[macAddress] = newGt
                    return newGt
                }else return null
            }

        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return null
        } catch (e:Exception){
            Log.e(logTagBleController, "Get 함수 실패 : ${macAddress} << getGattServer ${e.message}")
            return null
        }
    }

    suspend fun getWriteCharacteristic(macAddress: String): BluetoothGattCharacteristic? {
        return try {
            val gt = getGattServer(macAddress) ?: return null
            Log.d(logTagBleController,"디버깅중 < gt :${gt}, gt.services : ${gt.services}")

            // 서비스 검색이 완료될 때까지 대기
            var attempts = 0
//            while (!isServiceDiscovered && attempts < 5) {  <- onServicesDiscovered 쪽에 플래그 사용
            while ((gt.services).isEmpty() && attempts < 20) {
                Log.d(logTagBleController,"디버깅중 < GATT 서버 연결 기다리는중 :${gt.services}")
                attempts++
                delay(100)  // 0.1초씩 최대 2초 대기
            }

            val service = gt.getService(serviceUuid)

            Log.d(logTagBleController,"디버깅중 < service :${service} ${serviceUuid}")

            val writeChar = service?.getCharacteristic(writeCharacteristicUuid)
            Log.d(logTagBleController,"디버깅중 < writeChar :${writeChar} ${writeCharacteristicUuid}")

            writeChar
        } catch (e: Exception) {
            Log.e(logTagBleController, "Get 함수 실패 : ${macAddress} << getWriteCharacteristic")
            return null
        }
    }

    suspend fun getReadCharacteristic(macAddress: String): BluetoothGattCharacteristic? {
        return try {
            val gt = getGattServer(macAddress) ?: return null
            Log.d(logTagBleController,"디버깅중 < gt :${gt}, gt.services : ${gt.services}")

            // 서비스 검색이 완료될 때까지 대기
            var attempts = 0
//            while (!isServiceDiscovered && attempts < 5) {  <- onServicesDiscovered 쪽에 플래그 사용
            while ((gt.services).isEmpty() && attempts < 20) {
                Log.d(logTagBleController,"디버깅중 < GATT 서버 연결 기다리는중 :${gt.services}")
                attempts++
                delay(100)  // 0.1초씩 최대 2초 대기
            }

            val service = gt.getService(serviceUuid)
            Log.d(logTagBleController,"디버깅중 < service :${service} ${serviceUuid}")

            val readChar = service?.getCharacteristic(readCharacteristicUuid)
            Log.d(logTagBleController,"디버깅중 < readChar :${readChar} ${readCharacteristicUuid}")

            readChar
        } catch (e: Exception) {
            Log.e(logTagBleController, "Get 함수 실패 : ${macAddress} << getReadCharacteristic")
            return null
        }
    }
    /* GET 함수 -----------------------------------------------------------------------------------*/

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
    fun connectToDevice(
        device: BluetoothDevice,
        onGattServiceResultCallback: (Boolean) -> Unit
    ): BluetoothGatt? {
//    fun connectToDevice(device: BluetoothDevice, onGattServiceResultCallback: BluetoothGattCallback):BluetoothGatt? {
        Log.d(logTagBleController,"connectToDevice >> ${device}")
        // 블루투스 연결 권한 확인
        hasBluetoothConnectPermission() ?: return null
        try {
            if (device.bondState == BluetoothDevice.BOND_NONE) {
                device.createBond() // 페어링 시도
            }

            // GATT 서버에 연결 시도
            val gtServer: BluetoothGatt = device.connectGatt(
                context, false, object : BluetoothGattCallback() {
                    // GAP 연결 시도 결과 콜백
                    override fun onConnectionStateChange(
                        gatt: BluetoothGatt,
                        status: Int,
                        newState: Int
                    ) {
                        super.onConnectionStateChange(gatt, status, newState)
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                // GAP 서버에 연결 성공
                                Log.d(logTagBleController, "GAP 서버에 연결되었습니다.${newState}")

                                Log.d(logTagBleController, "GATT 연결 시도중 ...${gatt}")
                                // GATT 전송 버퍼 크기 지정
                                gatt.requestMtu(247)
                                if (!gatt.discoverServices()) { // GATT 서비스 검색 실패
                                    throw Exception("GATT Service 검색 실패")
                                }

                                onGattServiceResultCallback(true)
                            }

                            BluetoothProfile.STATE_DISCONNECTED -> {
                                // GATT 서버 연결 해제
                                Log.d(logTagBleController, "GAP 서버 연결이 해제되었습니다. : $gatt")
                                onGattServiceResultCallback(false)
                            }

                            else -> {
                                Log.w(logTagBleController, "알 수 없는 GAP 상태: $newState")
                                useToastOnSubThread("알 수 없는 GAP 상태: $newState")
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
//                            Log.e(logTagBleController, "GATT CLOSE ${gatt.close()}")
                            useToastOnSubThread("GATT 서비스 검색 실패: $status")
                            return
                        }
                        Log.d(logTagBleController, "GATT 서비스 검색 성공")
                        gtMap[device.address] = gatt

                        val charList = mutableListOf<Any>()
                        // 검색된 모든 서비스와 특성을 로그로 출력
                        for (service in gatt.services) {
                            Log.d(logTagBleController, "서비스 UUID: ${service.uuid}")
                            charList.add("서비스 : ${service.uuid}")
                            for (characteristic in service.characteristics) {
                                charList.add("${getPropertiesString(characteristic.properties)} >> ${characteristic.uuid}")
                                Log.d(logTagBleController, "  특성 UUID: ${characteristic.uuid}")
                                charList.add(" - ")
                            }
                            charList.add(" ========= ")
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

                        // 쓰기 특성 등록
                        service.getCharacteristic(writeCharacteristicUuid).apply {
                            if (this == null) Log.e(logTagBleController, "쓰기 특성을 찾을 수 없습니다.")
                        }

                        // 읽기 특성 등록
                        service.getCharacteristic(readCharacteristicUuid).apply {
                            if (this == null) Log.e(logTagBleController, "읽기 특성을 찾을 수 없습니다.")
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

//                    onGattServiceResultCallback(true)
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
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        receivedData: ByteArray,
                        status: Int
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Android 13 이상에서 실행
                            super.onCharacteristicRead(gatt, characteristic, receivedData, status)
                            Log.i(
                                logTagBleController,
                                "신형 READ FROM ${device.name} : ${device.address}"
                            )
                            handleCharacteristicRead(device, receivedData, status)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            // Android 13 미만에서 실행
                            super.onCharacteristicRead(gatt, characteristic, status)
                            Log.i(
                                logTagBleController,
                                "구형 READ FROM ${device.name} : ${device.address}"
                            )
                            if (characteristic.value == null) {
                                Log.e(logTagBleController, "characteristic value is NULL")
                            } else {
                                handleCharacteristicRead(device, characteristic.value, status)
                            }
                        }
                    }

//                    // ( 트리거 : APP ) App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음
//                    override fun onCharacteristicRead(
//                        // 구형 안드로이드 버전의 경우
//                        gatt: BluetoothGatt,
//                        characteristic: BluetoothGattCharacteristic,
//                        status: Int
//                    ) {
//                        super.onCharacteristicRead(gatt, characteristic, status)
//                        Log.i(
//                            logTagBleController,
//                            " 구형 READ FROM ${device.name} : ${device.address}"
//                        )
//                        if (characteristic.value == null) {
//                            Log.e(logTagBleController, "characteristic value is NULL")
//                        } else {
//                            handleCharacteristicRead(device, characteristic.value, status)
//                        }
//                    }
//
//                    override fun onCharacteristicRead(
//                        // 일반 안드로이드 버전의 경우
//                        gatt: BluetoothGatt,
//                        characteristic: BluetoothGattCharacteristic,
//                        receivedData: ByteArray,
//                        status: Int
//                    ) {
//                        super.onCharacteristicRead(gatt, characteristic, receivedData, status)
//                        Log.i(
//                            logTagBleController,
//                            " 신형 READ FROM ${device.name} : ${device.address}"
//                        )
//                        handleCharacteristicRead(device, receivedData, status)
//                    }

                    // 데이터를 썼을 때 호출되는 콜백
                    override fun onCharacteristicWrite(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        super.onCharacteristicWrite(gatt, characteristic, status)
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i(logTagBleController, "데이터 전송 성공: $status")
                        } else {
                            Log.e(logTagBleController, "데이터 전송 실패: $status")
                            Log.d(logTagBleController, "디버깅중 < writeData 실패로 인해 GATT 서비스 재검색")
                            reDiscoverGattService(gatt)
                        }
                    }
                }
            )
            return gtServer
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(logTagBleController, "블루투스 연결 시도 중 보안 예외 발생: ${e.message}")
            return null
        }
    }


    /**
     * 데이터 쓰기
     */
    suspend fun writeData(data: ByteArray, macAddress: String ) {
        try {
            hasBluetoothConnectPermission() ?: return
            val gt = getGattServer(macAddress)
            val writeChar = getWriteCharacteristic(macAddress)
            if( gt === null || writeChar === null){
                throw Exception("GATT 서버 : $gt , write 특성 $writeChar << 구성되어 있지 않음")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result:Int = gt.writeCharacteristic(
                    writeChar,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            }else{
                writeChar.value = data
                writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                val result:Boolean = gt.writeCharacteristic(writeChar)
            }
        } catch (e: SecurityException){
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
        } catch (e: Exception) {
            Log.e(logTagBleController, "WriteData 에러: ${e.message}")
        }
    }

    /**
     * App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음
     * --> onCharacteristicRead 오버라이드 함수 호출
     */
    suspend fun requestReadData(macAddress: String) {
        try {
            hasBluetoothConnectPermission() ?: return
            val gt = getGattServer(macAddress)
            val readChar = getReadCharacteristic(macAddress)

            Log.d(logTagBleController, "디버깅중 < requestReadData : $readChar , gt : $gt")

            if (gt === null|| readChar ===null) {
                throw Exception("GATT 서버 : $gt , read 특성 $readChar << 구성되어 있지 않음")
            }

            val success = gt.readCharacteristic(readChar)
            Log.d(logTagBleController, "디버깅중 < 읽기 요청 결과: $success , GATT MAP : ${gtMap}")
            if (!success) {
                Log.d(logTagBleController, "디버깅중 < requestReadData 실패로 인해 GATT 서비스 재검색")
                reDiscoverGattService(gt)
            }
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
        } catch (e: Exception) {
            Log.e(logTagBleController, "ReadData 에러: ${e.message}")
        }
    }

    // ( 트리거 : APP ) App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음
    private fun handleCharacteristicRead(device: BluetoothDevice, receivedData: ByteArray, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // 데이터를 성공적으로 읽었을 때 처리
            Log.i(logTagBleController, "App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음")
//            Log.i(logTagBleController, "수신된 데이터: $receivedData \n" + "status : $status")

            // 읽은 데이터 가져오기
            val data = receivedData

            // ByteArray를 문자열로 변환
            val byteArrayString = String(data) // 기본적으로 UTF-8로 변환

            // UTF-8로 변환
            val utf8String = String(data, Charsets.UTF_8)

//                    // EUC-KR로 변환
//                    val eucKrString = String(data, Charsets.EUC_KR)
//                    Log.i(BLECONT_LOG_TAG, "수신된 데이터 (EUC-KR): $eucKrString")

            // ASCII로 변환
            val asciiString = String(data, Charsets.US_ASCII)

            // Hexadecimal로 출력
            val hexString = data.joinToString(" ") { String.format("%02X", it) }

            // UI 스레드에서 Toast 표시
            Log.i(logTagBleController,
                "(String) : $byteArrayString \n" +
                        "(UTF-8)  : $utf8String \n" +
                        "(ASCII)  : $asciiString \n" +
                        "(Hex)    : $hexString \n"
            )
//                {Status: 1, Battery: 100}
//                {Status: 0, Battery: 100}
            // Lambda 호출
//                onRequestDataListener(receivedData, status)
            Log.i(logTagBleController, "onRequestDataListener 할당 값 : ${onRequestDataListener}")

            updateReadData(
                "(String) : $byteArrayString \n" +
                        "(UTF-8)  : $utf8String \n" +
                        "(ASCII)  : $asciiString \n" +
                        "(Hex)    : $hexString \n"
            )

            onRequestDataListener?.invoke(device, byteArrayString, status)

        } else {
            // 에러 처리
            Log.e("BLE", "Characteristic Read Failed, status: $status")
        }
    }

    /**
     * 페어링된 기기 목록 가져오기
     */
    fun getParingDevices(): Set<BluetoothDevice> {
        hasBluetoothConnectPermission() ?: return emptySet()
        if(!::bluetoothAdapter.isInitialized){
            return emptySet()
        }
        try {
            return bluetoothAdapter.bondedDevices
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "bluetoothAdapter.bondedDevices Failed : ${e.message}")
            return emptySet()
        }
    }

    /**
     * 현재 연결된 BLE 기기 목록 가져오기
     */
    fun getConnectedDevices(): List<BluetoothDevice> {
        hasBluetoothConnectPermission() ?: return emptyList()
        if(!::bluetoothManager.isInitialized){
            return emptyList()
        }
        try {
            return bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "getConnectedDevices : ${e.message}")
            return emptyList()
        }
    }

    /**
     * 특정 기기 연결 해제
     */
    fun disconnectDevice(macAddress: String) : Boolean {
        hasBluetoothConnectPermission() ?: return false
        val gt = getGattServer(macAddress)
        gt ?: return false
        try {
            removeGattServer(macAddress, "disconnectDevice")
            return true
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(logTagBleController, "disconnectDevice Failed ${e.message}")
            return false
        }
    }

    /**
     * 모든 기기 연결 해제
     */
    fun disconnectAllDevices() {
        hasBluetoothConnectPermission() ?: return
        try {
            val connectedDevices = getConnectedDevices()
            Log.d(logTagBleController, "연결된 기기 수: ${connectedDevices.size}")
            if(connectedDevices.isEmpty()) {
                Log.i(logTagBleController, "연결된 기기가 없습니다.")
                return
            }

            for (bleDevice: BluetoothDevice in connectedDevices) {
                val gt = getGattServer(bleDevice.address)
                gt ?: throw Exception("${bleDevice.address}, ${bleDevice.name} 의 GATT 서버 구성이 안되어 있음")
                Log.d(logTagBleController, "디버깅중 <: Disconnect 시 service 확인 , ${gt.services}")
                removeGattServer(bleDevice.address, "disconnectAllDevices")
            }
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return
        } catch (e: Exception) {
            Log.e(logTagBleController, "disconnectAllDevices Failed ${e.message}")
            return
        }
    }

    /**
     * 페어링된 기기 삭제
     */
    fun removeParing(macAddress: String): Boolean {
        hasBluetoothConnectPermission() ?: return false

        val gt = getGattServer(macAddress)
        gt ?: return false
        try {
            for (bleDevice in getParingDevices()){
                if (macAddress == bleDevice.address){
                    removeGattServer(macAddress,"removeParing")
                    // Kotlin 리플렉션을 사용하여 메서드 호출
                    val method = bleDevice::class.declaredFunctions.find { it.name == "removeBond" }
                    return method?.call(bleDevice) as? Boolean ?: false
                }
            }
            return true
        } catch (e: SecurityException) {
            Log.e(logTagBleController, "SecurityException 에러 : ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(logTagBleController, "removeParing Failed ${e.message}")
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
        //Handler(context.mainLooper).post { ... } << context.mainLooper(UI쓰레드) 를 가져옴
        Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
        permissionStatus.locationPermission = ContextCompat.checkSelfPermission( context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // 최신폰 일 경우
            // BLUETOOTH_SCAN
            permissionStatus.bluetoothScanPermission = ContextCompat.checkSelfPermission( context,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

            // BLUETOOTH_CONNECT
            permissionStatus.bluetoothConnectPermission = ContextCompat.checkSelfPermission(context,
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