// Operator Pack
package com.example.simplebleapp.bleModules

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
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


// Util Pack
import java.util.UUID

// Custom Package


class BleController(private val applicationContext: Context) {
    // BLE 관련 멤버 변수
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    // GATT 특성
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var receivedString: String = ""

    // 수신 데이터를 LiveData로 관리
    private val _readData = MutableLiveData<String>() // 내부에서만 수정 가능
    val readData: LiveData<String> get() = _readData // 외부에서는 읽기만 가능

    /*
    * 49535343-FE7D-4AE5-8FA9-9FAFD205E455 - Svc
    * 49535343-1E4D-4BD9-BA61-23C647249616 - Write
    * 49535343-8841-43F4-A8D4-ECBE34729BB3 - Read ( Notify )
    * 49535343-4C8A-39B3-2F49-511CFF073B7E - CP UUID
    * */
    // TRS Service ( peri_uart )
    private val SERVICE_UUID                = UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")
    private val WRITE_CHARACTERISTIC_UUID   = UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")
    private val READ_CHARACTERISTIC_UUID    = UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")

    // ActivityResultLauncher를 클래스의 멤버 변수로 선언
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    // 스캔 제한 시간
    private val SCAN_PERIOD: Long = 10000
    private val BLECONT_LOG_TAG = " - BleController"

    // 권한 상태를 저장하는 Map
    private val permissionStatus = mutableMapOf(
        "블루투스 활성화" to false,
        "블루투스 스캔 권한" to false,
        "위치 권한" to false,
        "블루투스 연결 권한" to false
    )

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
     * BLE 권한 요청 런처 설정
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
            Log.e(BLECONT_LOG_TAG, "기기의 BLE 지원 여부 확인 : $bluetoothLEAvailable")
            // TODO: BLE 미지원 기기에 대한 에러 처리 필요
        }

//        if (bluetoothAdapter == null) {
//            Log.e(BLECONT_LOG_TAG, "기기의 BLE 및 Bluetooth Classic 기능 지원 여부 확인 : $bluetoothAdapter")
//            // TODO: Bluetooth 미지원 기기에 대한 에러 처리 필요
//        }
    }

    /**
     * BLE 권한 요청
     */
    fun requestBlePermission(activity: Activity): MutableMap<String, Boolean> {
        //        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        // 1. 블루투스 활성화 요청 <System Setting>
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent) // registerForActivityResult로 처리
            permissionStatus["블루투스 활성화"] = false // 활성화 여부는 런처 결과에서 처리
        } else {
            permissionStatus["블루투스 활성화"] = true
        }

        // 2. Android 12(API 31) 이상에서 BLE 스캔 권한 요청 <Permission Request>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                    101 // 요청 코드
                )
                permissionStatus["블루투스 스캔 권한"] = false
            } else permissionStatus["블루투스 스캔 권한"] = true
        } else permissionStatus["블루투스 스캔 권한"] = true

        // 3. 위치 정보 권한 요청 <Permission Request>
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                102 // 요청 코드
            )
            permissionStatus["위치 권한"] = false
        } else {
            permissionStatus["위치 권한"] = true
        }

        // 4. 블루투스 연결 권한 요청 <Permission Request>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    103 // 요청 코드
                )
                permissionStatus["블루투스 연결 권한"] = false
            } else permissionStatus["블루투스 연결 권한"] = true
        } else permissionStatus["블루투스 연결 권한"] = true

        return permissionStatus
    }

    /**
     * BLE 스캔 시작
     */
    fun <T> startBleScan(scanCallback: ScanCallback, popupContainer: T) {
        bluetoothLeScanner.startScan(scanCallback)

        // 10초 후 스캔 중지
        Log.i(BLECONT_LOG_TAG, "스캔 타임아웃 제한시간 : ${SCAN_PERIOD / 1000}초 ")
        when (popupContainer) {
            is LinearLayout -> {  // 특정 팝업창에 Text로 UI 표현하는 경우
                popupContainer.postDelayed({
                    stopBleScan(scanCallback)
                }, SCAN_PERIOD)
            }

            is Handler -> {  // 일반 화면에 Text로 UI 표현하는 경우
                popupContainer.postDelayed({ // SCAN_PERIOD 시간후에 발동되는 지연 함수
                    Log.w(BLECONT_LOG_TAG, "--스캔 타임아웃-- ")
                    bluetoothLeScanner.stopScan(scanCallback)
                }, SCAN_PERIOD)
            }
        }
    }


    /**
     * BLE 스캔 중지
     */
    fun stopBleScan(scanCallback: ScanCallback) {
        try {
            bluetoothLeScanner.stopScan(scanCallback)
            Log.e(BLECONT_LOG_TAG, "블루투스 스캔 정지")
        } catch (e: Exception) {
            Log.e(BLECONT_LOG_TAG, "Failed to stop BLE scan: ${e.message}")
        }
    }

    /**
     * BLE 장치 연결
     */
    @RequiresPermission(value = Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice, onConnectionStateChange: (Boolean) -> Unit) {
        // 장치의 본딩 상태 확인 (본딩: BLE 장치와의 신뢰 관계를 설정하는 과정)
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> Log.i(BLECONT_LOG_TAG, "해당 장치가 본딩되어 있습니다.")
            BluetoothDevice.BOND_BONDING -> Log.i(BLECONT_LOG_TAG, "해당 장치가 본딩 중입니다.")
            BluetoothDevice.BOND_NONE -> Log.i(BLECONT_LOG_TAG, "해당 장치가 본딩되지 않았습니다.")
        }
        // GATT 서버에 연결 시도
        bluetoothGatt =
            device.connectGatt(applicationContext, false, object : BluetoothGattCallback() {

                // GATT 연결 상태가 변경되었을 때 호출되는 콜백
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    super.onConnectionStateChange(gatt, status, newState)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        // GATT 서버에 연결 성공
                        Log.d(BLECONT_LOG_TAG, "GATT 서버에 연결되었습니다.")
                        onConnectionStateChange(true)

                        // GATT 서비스 검색 (권한 확인 후 실행)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                try {
                                    gatt.discoverServices() // GATT 서비스 검색
                                } catch (e: SecurityException) {
                                    Log.e(
                                        BLECONT_LOG_TAG,
                                        "BLUETOOTH_CONNECT 권한이 없어 discoverServices()를 호출할 수 없습니다."
                                    )
                                }
                            } else {
                                Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없습니다.")
                            }
                        } else {
                            try {
                                gatt.discoverServices() // GATT 서비스 검색
                            } catch (e: SecurityException) {
                                Log.e(
                                    BLECONT_LOG_TAG,
                                    "BLUETOOTH_CONNECT 권한이 없어 discoverServices()를 호출할 수 없습니다."
                                )
                            }
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // GATT 서버 연결 해제
                        Log.d(BLECONT_LOG_TAG, "GATT 서버 연결이 해제되었습니다.")
                        onConnectionStateChange(false)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // GATT 서비스 검색 성공
                        Log.d(BLECONT_LOG_TAG, "GATT 서비스 검색 성공")

                        // 검색된 모든 서비스와 특성을 로그로 출력
                        for (service in gatt.services) {
                            Log.d(BLECONT_LOG_TAG, "서비스 UUID: ${service.uuid}")
                            for (characteristic in service.characteristics) {
                                Log.d(BLECONT_LOG_TAG, "  특성 UUID: ${characteristic.uuid}")
                            }
                        }

                        // 특정 서비스와 특성 찾기
                        val service = gatt.getService(SERVICE_UUID)
                        if (service != null) {
                            Log.d(BLECONT_LOG_TAG, "특정 서비스 발견: $SERVICE_UUID")

                            // 쓰기 특성 초기화
                            writeCharacteristic =
                                service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
                            if (writeCharacteristic != null) {
                                Log.d(BLECONT_LOG_TAG, "쓰기 특성 발견: $WRITE_CHARACTERISTIC_UUID")
                            } else {
                                Log.e(BLECONT_LOG_TAG, "쓰기 특성을 찾을 수 없습니다.")
                                useToastOnSubThread("쓰기 특성을 찾을 수 없습니다.")
                            }

                            // 읽기 특성 초기화
                            readCharacteristic = service.getCharacteristic(READ_CHARACTERISTIC_UUID)
                            if (readCharacteristic != null) {
                                Log.d(BLECONT_LOG_TAG, "읽기 특성 발견: $READ_CHARACTERISTIC_UUID")

                                // 읽기 특성에 대해 알림(Notification) 활성화
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if (ContextCompat.checkSelfPermission(
                                            applicationContext,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        try {
                                            gatt.setCharacteristicNotification(
                                                readCharacteristic,
                                                true
                                            )

                                            // CCCD 설정 // Subscribe 요청 --> IoT
                                            val descriptor =
                                                readCharacteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                            descriptor.value =
                                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                            gatt.writeDescriptor(descriptor)
                                            Log.d(BLECONT_LOG_TAG, "읽기 특성에 대한 Notification 활성화 완료")

                                        } catch (e: SecurityException) {
                                            Log.e(
                                                BLECONT_LOG_TAG,
                                                "BLUETOOTH_CONNECT 권한이 없어 알림을 활성화할 수 없습니다."
                                            )
                                            useToastOnSubThread("BLUETOOTH_CONNECT 권한이 없어 알림을 활성화할 수 없습니다.")
                                        }
                                    } else {
                                        Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없습니다.")
                                        useToastOnSubThread("BLUETOOTH_CONNECT 권한이 없습니다.")
                                    }
                                } else {
                                    try {
                                        gatt.setCharacteristicNotification(readCharacteristic, true)

                                        // CCCD 설정 // Subscribe 요청 --> IoT
                                        val descriptor =
                                            readCharacteristic!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                        descriptor.value =
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        gatt.writeDescriptor(descriptor)
                                        Log.d(BLECONT_LOG_TAG, "읽기 특성에 대한 Notification 활성화 완료")

                                    } catch (e: SecurityException) {
                                        Log.e(
                                            BLECONT_LOG_TAG,
                                            "BLUETOOTH_CONNECT 권한이 없어 알림을 활성화할 수 없습니다."
                                        )
                                        useToastOnSubThread("BLUETOOTH_CONNECT 권한이 없어 알림을 활성화할 수 없습니다.")
                                    }
                                }
                            } else {
                                Log.e(BLECONT_LOG_TAG, "읽기 특성을 찾을 수 없습니다.")
                                useToastOnSubThread("읽기 특성을 찾을 수 없습니다.")
                            }
                        } else {
                            Log.e(BLECONT_LOG_TAG, "특정 서비스를 찾을 수 없습니다: $SERVICE_UUID")
                            useToastOnSubThread("특정 서비스를 찾을 수 없습니다: $SERVICE_UUID")
                        }
                    } else {
                        // GATT 서비스 검색 실패
                        Log.e(BLECONT_LOG_TAG, "GATT 서비스 검색 실패: $status")
                        useToastOnSubThread("GATT 서비스 검색 실패: $status")
                    }
                }

                // ( 트리거 : IoT기기 ) 기기가 Data 전송 > App 이 읽음
                // 읽기 특성에 대한 알림(Notification)이 활성화된 경우, 데이터가 수신될 때 호출되는 콜백
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    super.onCharacteristicChanged(gatt, characteristic)
                    if (characteristic.uuid == READ_CHARACTERISTIC_UUID) {
                        val receivedData = characteristic.value // 수신된 데이터
                        receivedString = String(receivedData) // ByteArray를 문자열로 변환
                        Log.i(BLECONT_LOG_TAG, "수신된 데이터: $characteristic")
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
                    status: Int
                ) {
                    super.onCharacteristicRead(gatt, characteristic, status)
                    Log.i(BLECONT_LOG_TAG, "수신된 데이터: $characteristic status : ${status}")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // 읽은 데이터 가져오기
                        val data = characteristic.value

//                    // ByteArray를 문자열로 변환
//                    receivedString = String(data) // 기본적으로 UTF-8로 변환
//                    Log.i(BLECONT_LOG_TAG, "수신된 데이터 (String): $receivedString")

                        // UTF-8로 변환
                        val utf8String = String(data, Charsets.UTF_8)
                        Log.i(BLECONT_LOG_TAG, "수신된 데이터 (UTF-8): $utf8String")

//                    // EUC-KR로 변환
//                    val eucKrString = String(data, Charsets.EUC_KR)
//                    Log.i(BLECONT_LOG_TAG, "수신된 데이터 (EUC-KR): $eucKrString")

                        // ASCII로 변환
                        val asciiString = String(data, Charsets.US_ASCII)
                        Log.i(BLECONT_LOG_TAG, "수신된 데이터 (ASCII): $asciiString")

                        // Hexadecimal로 출력
                        val hexString = data.joinToString(" ") { String.format("%02X", it) }
                        Log.i(BLECONT_LOG_TAG, "수신된 데이터 (Hex): $hexString")

                        receivedString = hexString
                        updateReadData(receivedString)
                        // UI 스레드에서 Toast 표시
                        useToastOnSubThread("읽은 데이터: $receivedString")
                    } else {
                        Log.e(BLECONT_LOG_TAG, "데이터 읽기 실패: $status")
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
                        Log.i(BLECONT_LOG_TAG, "데이터 전송 성공: ${String(characteristic.value)}")
                    } else {
                        // 데이터 전송 실패
                        Log.e(BLECONT_LOG_TAG, "데이터 전송 실패: $status")
                    }
                }

            })
    }

    /**
     * 데이터 쓰기
     */
    fun writeData(data: ByteArray) {
        if (writeCharacteristic != null) {
            writeCharacteristic?.value = data

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        bluetoothGatt?.writeCharacteristic(writeCharacteristic)
                    } catch (e: SecurityException) {
                        Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없어 데이터를 전송할 수 없습니다.")
                    }
                } else {
                    Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없습니다.")
                }
            } else {
                try {
                    bluetoothGatt?.writeCharacteristic(writeCharacteristic)
                } catch (e: SecurityException) {
                    Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없어 데이터를 전송할 수 없습니다.")
                }
            }
        } else {
            Log.e(BLECONT_LOG_TAG, "쓰기 특성이 초기화되지 않았습니다.")
        }
    }

    /**
     * App 이 Read 요청 > 기기가 Data 전송 > App 이 읽음
     * --> onCharacteristicRead 오버라이드 함수 호출
     */
    fun requestReadData() {
        if (readCharacteristic != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        bluetoothGatt?.readCharacteristic(readCharacteristic) // 읽기 요청
                        Log.i(BLECONT_LOG_TAG, "읽기 요청을 하였습니다.")
                    } catch (e: SecurityException) {
                        Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없어 데이터를 읽을 수 없습니다.")
                    }
                } else {
                    Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없습니다.")
                }
            } else {
                try {
                    bluetoothGatt?.readCharacteristic(readCharacteristic) // 읽기 요청
                    Log.i(BLECONT_LOG_TAG, "읽기 요청을 하였습니다.")
                } catch (e: SecurityException) {
                    Log.e(BLECONT_LOG_TAG, "BLUETOOTH_CONNECT 권한이 없어 데이터를 읽을 수 없습니다.")
                }
            }
        } else {
            Log.e(BLECONT_LOG_TAG, "읽기 특성이 초기화되지 않았습니다.")
        }
    }

    /**
     * 읽은 데이터 반환
     */
    fun getReadData(): String {
        return receivedString
    }


    /**
     * BLE 연결 해제
     */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }


// Callback or 비슷한 함수 들
    /**
     * Bluetooth 활성화 요청 결과 처리
     */
    fun handleBluetoothIntentResult(result: ActivityResult) {
        // 특정 작업(예: 권한 요청, 다른 Activity 호출 등)의 결과를 처리할 Callback 함수
        if (result.resultCode == Activity.RESULT_OK) {
            Log.i(BLECONT_LOG_TAG, "블루투스가 활성화되었습니다.")
            permissionStatus["블루투스 권한"] = true
        } else {
            Log.i(BLECONT_LOG_TAG, "블루투스 활성화가 취소되었습니다.")
            permissionStatus["블루투스 권한"] = false
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
    fun updateReadData(data: String) {
        _readData.postValue(data) // LiveData에 새로운 데이터 설정
    }

}