package com.intel.ispplib.connection.device;


import android.bluetooth.BluetoothGattCallback;

public abstract class DeviceBleConnectionGattCallback extends BluetoothGattCallback {
    public abstract void onBondStateChanged(DeviceBleConnection.BOND_STATE bondState);
}
