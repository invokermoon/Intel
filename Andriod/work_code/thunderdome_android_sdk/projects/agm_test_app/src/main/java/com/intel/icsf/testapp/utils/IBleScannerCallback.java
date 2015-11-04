package com.intel.icsf.testapp.utils;

import android.bluetooth.BluetoothDevice;

public interface IBleScannerCallback {
    /**
     * Callback corresponding to startLeScan(...)
     * @param device The information about the discovered device
     */
    public void onDeviceDiscovered(final BluetoothDevice device);
}
