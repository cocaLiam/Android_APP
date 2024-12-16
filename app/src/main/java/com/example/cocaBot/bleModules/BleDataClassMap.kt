package com.example.cocaBot.bleModules

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

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