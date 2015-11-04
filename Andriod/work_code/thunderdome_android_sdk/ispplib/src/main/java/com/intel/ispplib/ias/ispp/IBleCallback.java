package com.intel.ispplib.ias.ispp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * Interface to be implemented by a callback managed by ispp to get
 * information about Ble connection
 */
public interface IBleCallback {
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState);
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic);
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic, int status);
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                  int status);
}
