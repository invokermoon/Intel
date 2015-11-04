package com.intel.ispplib.connection.device;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;

import java.util.List;

/**
 * Callback, registered by the application,
 * to get status about the ble connection
 */
public interface IBleConnectionCallBack {

    /**
     * Callback for connection status change (connect (...), disconect(...), pair(...), unpair(...))
     * @param connectionStatus The status, see {@link IBleConnection.CONNECTION_STATUS}
     * @param gattStatusatus The returned gatt status
     */
    public void onStatusChanged(IBleConnection.CONNECTION_STATUS connectionStatus,
                                IBleConnection.GATT_STATUS gattStatusatus);

    /**
     * Callback corresponding to readCharacteristic(...)
     * @param characteristic The characteristic that has been read
     * @param status The gatt status, see {@link IBleConnection.GATT_STATUS}
     */
    public void onCharacteristicRead(BluetoothGattCharacteristic characteristic,
                                     IBleConnection.GATT_STATUS status);

    /**
     * Callback corresponding to a characteristic notification from the ble connection
     * @param characteristic the characteristic that has been updated
     * @param status The gatt status, see {@link IBleConnection.GATT_STATUS}
     */
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic,
                                        IBleConnection.GATT_STATUS status);

    /**
     * Callback corresponding to writeCharacteristic(...)
     * @param characteristic The characteristic that has been written
     * @param status The gatt status, see {@link IBleConnection.GATT_STATUS}
     */
    public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic,
                                      IBleConnection.GATT_STATUS status);

    /**
     * Callback corresponding to subscribeToCharacteristic(...)
     * @param characteristic the characteristic
     * @param status The gatt status, see {@link IBleConnection.GATT_STATUS}
     * @param subscribed boolean indicating whether notifications are enabled
     */
    public void onCharacteristicSubscribe(BluetoothGattCharacteristic characteristic,
                                          IBleConnection.GATT_STATUS status, boolean subscribed);

    /**
     * Callback corresponding to discoverServices()
     * @param services The list of discovered services
     * @param status The gatt status, see {@link IBleConnection.GATT_STATUS}
     */
    public void onServicesDiscovered(List<BluetoothGattService> services,
                                     IBleConnection.GATT_STATUS status);

    /**
     * Callback corresponding to pair(...), unpair(...) calls
     * @param bondState The new bond state with the device
     */
    public void onBondStateChanged(DeviceBleConnection.BOND_STATE bondState);

}
