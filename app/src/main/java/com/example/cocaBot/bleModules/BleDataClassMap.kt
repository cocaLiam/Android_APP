package com.example.cocaBot.bleModules

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

data class PermissionStatus(
    var bluetoothEnabled: Boolean = false,
    var locationPermission: Boolean = false,
    var bluetoothScanPermission: Boolean = false,
    var bluetoothConnectPermission: Boolean = false
)

data class BleDeviceInfo(
    var device: BluetoothDevice? = null,
    var deviceType: String? = null,
    var gatt: BluetoothGatt? = null,
    var writeCharacteristic: BluetoothGattCharacteristic? = null,
    var readCharacteristic: BluetoothGattCharacteristic? = null
)

data class PushBotUuid(
    val serviceUuid            : String = "49535343-fe7d-4ae5-8fa9-9fafd205e455",
    val writeCharacteristicUuid: String = "49535343-8841-43F4-A8D4-ECBE34729BB3",
    val readCharacteristicUuid : String = "49535343-1e4d-4bd9-ba61-23c647249616"
)
