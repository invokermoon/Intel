/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.ispplib.connection.device.service;

import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;

import com.intel.ispplib.connection.device.BleIasConnection;
import com.intel.ispplib.connection.device.DeviceBleConnection;
import com.intel.ispplib.connection.device.IBleConnection;
import com.intel.ispplib.connection.device.IBleConnectionCallBack;
import com.intel.ispplib.ias.ispp.IsppRxCallBack;
import com.intel.ispplib.ias.ispp.IsppUtils;
import com.intel.ispplib.utils.LogUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service managing one (and soon many){@link BleIasConnection}(s).
 * It allows for many clients to bind to this service and call its methods. It sends callbacks through
 * broadcast intents. The service gives the possibility to execute simple Bluetooth low energy
 * requests but also open and use ispp communication pipes.
 * For Ble requests, remember to consider the gatt status: any status that is not
 * IBleConnection.GATT_STATUS.GATT_SUCCESS is suspicious
 * and you can consider the request as failed, even if it gives some results.
 */
public class BleIasService extends Service {
    private final static String TAG = "BLeIasService";
    /**
     * Broadcast intent action indicating that the connection state has changed
     */
    public final static String ACTION_STATUS_CHANGED =
            "com.intel.icsf.connection.device.service.BLeIasService.ACTION_STATUS_CHANGED";
    /**
     * Broadcast intent action indicating that a characteristic has been read
     */
    public final static String ACTION_ON_CHARACTERISTIC_READ =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ON_CHARACTERISTIC_READ";
    /**
     * Broadcast intent action indicating that the value of a subscribed characteristic has changed
     */
    public final static String ACTION_ON_CHARACTERISTIC_CHANGED =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ON_CHARACTERISTIC_CHANGED";
    /**
     * Broadcast intent action indicating that a characteristic has been written
     */
    public final static String ACTION_ON_CHARACTERISTIC_WRITE =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ON_CHARACTERISTIC_WRITE";
    /**
     * Broadcast intent action indicating that a subscription or unsubscription to a characteristic
     * occured.
     */
    public final static String ACTION_ON_CHARACTERISTIC_SUBSCRIBE =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ON_CHARACTERISTIC_SUBSCRIBE";
    /**
     * Broadcast intent action indicating that services have been discovered.
     */
    public final static String ACTION_ON_SERVICES_DISCOVERED =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ON_SERVICES_DISCOVERED";
    /**
     * Broadcast intent action indicating that the pair state has changed
     */
    public final static String ACTION_ON_BOND_STATE_CHANGED =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ON_BOND_STATE_CHANGED";

    /**
     * Broadcast intent action indicating that ispp connection has been established.
     */
    public final static String ACTION_ISPP_CONNECTION_ESTABLISHED =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ISPP_CONNECTION_ESTABLISHED";
    /**
     * Broadcast intent action indicating that ispp connection has been lost
     */
    public final static String ACTION_ISPP_CONNECTION_LOST =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ISPP_CONNECTION_LOST";
    /**
     * Broadcast intent action indicating that an ispp message has been received from the other
     * end of the ispp connection
     */
    public final static String ACTION_ISPP_PACKET_RECEIVED =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ISPP_PACKET_RECEIVED";
    /**
     * Broadcast intent action indicating that an ispp message sent from this end of ispp connection
     * has been transmitted on the other end (there is no guarantee that the message has been delivered
     * to its consumer and that the consumer has processed the message)
     */
    public final static String ACTION_ISPP_PACKET_SENT =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "ACTION_ISPP_PACKET_SENT";

    /**
     * Extra string representing a BLE characteristic {@link UUID}
     */
    public final static String EXTRA_CHAR_UUID =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_CHAR_UUID";
    /**
     * Extra string representing a BLE service {@link UUID}
     */
    public final static String EXTRA_CHAR_SERVICE_UUID =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_CHAR_SERVICE_UUID";
    /**
     * Extra string representing a BLE characteristic value (byte array)
     */
    public final static String EXTRA_CHAR_VALUE =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_CHAR_VALUE";
    /**
     * Extra string representing a gatt status
     * ({@link IBleConnection.GATT_STATUS})
     */
    public final static String EXTRA_STATUS =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_STATUS";
    /**
     * Extra string representing a connection status
     * ({@link IBleConnection.CONNECTION_STATUS})
     */
    public final static String EXTRA_CONNECTION_STATUS =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_CONNECTION_STATUS";
    /**
     * Extra string representing a bond (pair) status
     * ({@link DeviceBleConnection.BOND_STATE})
     */
    public final static String EXTRA_BOND_STATUS =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_CONNECTION_STATUS";
    /**
     * Extra string representing whether a BLE characteristic was subscribed (true) or unsubcribed (false)
     * (boolean)
     */
    public final static String EXTRA_SUBSCRIBED =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_SUBSCRIBED";
    /**
     * Extra string representing a list of discovered services
     * ({@link List<UUID>})
     */
    public final static String EXTRA_SERVICES =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_SERVICES";
    /**
     * Extra string representing an mtu
     * (maximum size of the byte array that can be sent in one message)
     * (int)
     */
    public final static String EXTRA_MTU =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_MTU";
    /**
     * Extra string representing the write identifier, to check that a message
     * was transmitted on the other end of the connection.
     */
    public final static String EXTRA_WRITE_ID =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_WRITE_ID";
    /**
     * Extra string representing a received message (byte array)
     */
    public final static String EXTRA_PACKET =
            "com.intel.icsf.connection.device.service.BLeIasService." +
                    "EXTRA_PACKET";

    private BleIasConnection bleIasConnection;

    private class MyIsppCallback implements IsppRxCallBack {

        @Override
        public void onIsppConnectionEstablished(int mtu) {
            LogUtils.LOGI(TAG, "The connection is established, with mtu: "  + mtu);
            broadcastConnectionEstablished(ACTION_ISPP_CONNECTION_ESTABLISHED, mtu);
        }

        @Override
        public void onIsppConnectionLost() {
            broadcastUpdate(ACTION_ISPP_CONNECTION_LOST);
        }

        @Override
        public void onIsppPacketReceived(byte[] packet) {
            LogUtils.LOGI(TAG, "Ispp packet received");
            broadcastPacketAvailable(ACTION_ISPP_PACKET_RECEIVED, packet);

        }
    }

    private final IBleConnectionCallBack mBleConnectionCallback = new IBleConnectionCallBack() {
        @Override
        public void onStatusChanged(IBleConnection.CONNECTION_STATUS status,
                                    IBleConnection.GATT_STATUS
                                            gattStatus) {
            broadcastUpdate(ACTION_STATUS_CHANGED, gattStatus, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGattCharacteristic characteristic,
                                         IBleConnection.GATT_STATUS status) {
            broadcastUpdate(ACTION_ON_CHARACTERISTIC_READ, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic,
                                            IBleConnection.GATT_STATUS status) {
            broadcastUpdate(ACTION_ON_CHARACTERISTIC_CHANGED, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic,
                                          IBleConnection.GATT_STATUS status) {
            broadcastUpdate(ACTION_ON_CHARACTERISTIC_WRITE, characteristic, status);
        }

        @Override
        public void onCharacteristicSubscribe(BluetoothGattCharacteristic characteristic,
                                              IBleConnection.GATT_STATUS status,
                                              boolean subscribe) {
            broadcastUpdate(ACTION_ON_CHARACTERISTIC_SUBSCRIBE, characteristic, status, subscribe);

        }

        @Override
        public void onServicesDiscovered(List<BluetoothGattService> services,
                                         IBleConnection.GATT_STATUS status) {
            broadcastUpdate(ACTION_ON_SERVICES_DISCOVERED, status, services);
        }

        @Override
        public void onBondStateChanged(DeviceBleConnection.BOND_STATE bondState) {
            LogUtils.LOGI(TAG, "The new bond state is: " + bondState);
            broadcastUpdate(bondState);
        }
    };

    private void broadcastUpdate(String action, IBleConnection.GATT_STATUS status,
                                 List<BluetoothGattService> services) {
        final Intent intent = new Intent(action);
        List<UUID> servicesList = new ArrayList<>();
        for(BluetoothGattService service: services) {
            servicesList.add(service.getUuid());
        }
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_SERVICES, (Serializable) servicesList);
        sendBroadcast(intent);
    }
    private void broadcastConnectionEstablished(String action, int mtu) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_MTU, mtu);
        sendBroadcast(intent);
    }


    private void broadcastPacketAvailable(String action, byte[] packet) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_PACKET, packet);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(String action,
                                 BluetoothGattCharacteristic characteristic,
                                 IBleConnection.GATT_STATUS status, boolean subscribe) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_CHAR_SERVICE_UUID, characteristic.getService().getUuid());
        intent.putExtra(EXTRA_CHAR_UUID, characteristic.getUuid());
        intent.putExtra(EXTRA_CHAR_VALUE, characteristic.getValue());
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_SUBSCRIBED, subscribe);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, IBleConnection.GATT_STATUS status,
                                 IBleConnection.CONNECTION_STATUS connectionStatus) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_CONNECTION_STATUS, connectionStatus);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(DeviceBleConnection.BOND_STATE bondStateStatus) {
        final Intent intent = new Intent(ACTION_ON_BOND_STATE_CHANGED);
        intent.putExtra(EXTRA_BOND_STATUS, bondStateStatus);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, IBleConnection.GATT_STATUS status) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_STATUS, status);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic,
                                 IBleConnection.GATT_STATUS status) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_CHAR_SERVICE_UUID, characteristic.getService().getUuid());
        intent.putExtra(EXTRA_CHAR_UUID, characteristic.getUuid());
        intent.putExtra(EXTRA_CHAR_VALUE, characteristic.getValue());
        intent.putExtra(EXTRA_STATUS, status);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BleIasService getService() {
            return BleIasService.this;
        }
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public boolean onUnbind(Intent intent) {
        LogUtils.LOGI(TAG, "Unbinding");
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();


    /**
     * Initializes the service. Should be called by a client right after it has bound to the service.
     * @return false If initialization was not successful. False if not.
     * If initialization fails, the service is unusable and this also means that the ble feature
     * is not supported on this android platform.
     */
    public boolean initialize() {
        if(bleIasConnection == null) {
            bleIasConnection = BleIasConnection.getInstance(this);
            bleIasConnection.setCallback(mBleConnectionCallback);
        }
        return getPackageManager().
                hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Method for establishing a BLE connection with the specified device
     * The new connection status is sent as a broadcast intent with action
     * {@link #ACTION_STATUS_CHANGED}, with extra status EXTRA_CONNECTION_STATUS {@link
     * IBleConnection.CONNECTION_STATUS}
     * . The gatt status is given also as an extra {@link #EXTRA_STATUS}
     * {@link IBleConnection.GATT_STATUS}
     * A connection is considered successful  only if the gattStatus value is GATT_SUCCESS
     * Prior to the connection, the device with the specified address should be discovered
     * using ble scan (see public boolean startLeScan of {@link android.bluetooth.BluetoothAdapter}).
     *
     * @param address The bd address of the device
     * @param autoConnect The autoconnect flag specifies whether the BLE stack always
     *                    tries to connect
     *                    to the device. If set to true, The BLE stack will connect to device as soon
     *                    as it becomes available. If set to false it will attempt a
     *                    one shot connection.
     * @return true if the connection request was successfully submitted, false if not.
     * If a connection with the specified device has already been established, this method will
     * return false and the connection not executed.
     * As the first version of the service supports only one device, if a pairing with a device
     * exists, the connect method can only be called with the same device's address.
     */
    public boolean connect(String address, boolean autoConnect) {
        return bleIasConnection.connect(address, autoConnect);
    }

    /**
     * Method for disconnecting from a  previously connected device.
     * The new connection status is sent as a broadcast intent with action
     * {@link #ACTION_STATUS_CHANGED}, with extra status EXTRA_CONNECTION_STATUS {@link
     * IBleConnection.CONNECTION_STATUS}
     *. The gatt status is given also as an extra {@link #EXTRA_STATUS}
     * {@link IBleConnection.GATT_STATUS}
     *
     * @return true if the connection request was successfully submitted, false if not.
     * If a previous connection was not established, this method will return false and the
     * disconnection not executed.
     */
    public boolean disconnect() {
        LogUtils.LOGI(TAG, "Disconneting from service");
       return  bleIasConnection.disconnect();
    }

    /**
     * Method for starting an ispp connection.
     * Ispp (Intel Serial Port Protocol) is for creating a virtual pipe over a ble connection. When
     * an ispp connection is opened, both connection ends can send and receive messages.
     * The messages content should fit inside the protocol mtu (maximum bytes that can be sent
     * in one isppWrite (...) call).
     * Before establishing ispp connection, the user has to bind to this service,
     * connect to a device and discover its services.
     * The connection will be established only if the ISPP service is among the discovered services.
     * The new ispp connection status is sent as a broadcast intent with action
     * {@link #ACTION_ISPP_CONNECTION_ESTABLISHED}, with extra int parameter, {@link #EXTRA_MTU},
     * representing the mtu.
     * @return true if the request was emitted successfully, false if not
     */
    public boolean startIsppConnection() {
        if(!bleIasConnection.isConnected() ||
                bleIasConnection.getIspp().isConnectionEstablished() ||
                !characTeristicExists(IsppUtils.ISSP_SERVICE_UUID, IsppUtils.ISSP_CONTROL_CHAR)) {
            return false;
        }
        return bleIasConnection.startIsppConnection(new MyIsppCallback());

    }

    /**
     * Method indicating whether an active BLE connection is currently
     * established with the specified device.
     * @param address The device bd address
     * @return true if there is an active connection, false if not.
     */
    public boolean isConnectedToDevice(String address) {
        return bleIasConnection.isConnectedToDevice(address);
    }

    /**
     * Method indicating whether a pairing with the specified device has been established.
     * @param address The device bd address
     * @return true if a pairing with the device has been successfully established.
     */
    public boolean isPairedToDevice(String address) {
        return bleIasConnection.isPairedToDevice(address);
    }

    /**
     * Method for getting the ispp mtu (max payload that can be sent in one ispp message)
     * @return The length of the byte array that can be given as parameter of isppWrite(...) call.
     * If no ispp connection is established, this method will return -1.
     */
    public int getIsppMtu() {
        return bleIasConnection.getIspp().getMtu();
    }

    /**
     * Method indicating whether an active ispp connection is currently established.
     * @return true whether an ispp connection is currently active, false if not.
     */
    public boolean isIsppConnected() {
        return bleIasConnection.getIspp().isConnectionEstablished();
    }

    /**
     * Method for writing an ispp message.
     * To be able to send an ispp message,
     * an ispp connection should first be successfully established.
     * The data length should fit in the ispp mtu.
     *
     * The user can provide the writeId parameter, uniquely identifying the write command.
     * When the full data is transmitted by the ispp protocol on the other end, a broadcast intent is sent
     * with action {@link #ACTION_ISPP_PACKET_SENT}, and extra write identifier : {@link #EXTRA_WRITE_ID}.
     * @param writeId
     * @param data The data to write
     * @return true if the request to write the message was successfully submitted, false if not
     */
    public boolean isppWrite(int writeId, byte [] data) {
        if(!bleIasConnection.getIspp().isConnectionEstablished()) {
            return false;
        }
        return bleIasConnection.getIspp().writeData(data);
    }

    /**
     * Method for writing an ispp message.
     * To be able to send an ispp message,
     * an ispp connection should first be successfully established.
     * The data length should fit in the ispp mtu.
     *
     * @param data The data to write
     * @return true if the request to write the message was successfully submitted, false if not
     */
    public boolean isppWrite(byte [] data) {
        if(!bleIasConnection.getIspp().isConnectionEstablished()) {
            return false;
        }
        return bleIasConnection.getIspp().writeData(data);
    }

    /**
     * Start the pairing process with the specified device.
     * This is an asynchronous call. Register
     * for {@link #ACTION_ON_BOND_STATE_CHANGED} intents to be notified about the bonding process.
     * The pairing status will be added as an extra ({@link #EXTRA_BOND_STATUS}), as {@link com.intel
     * .icsf.connection.device.DeviceBleConnection.BOND_STATE}s
     * @param address The address of the device to pair to.
     * As the first version of the service supports only one device, if a connection with a device
     * exists, the pair method can only be called with the same device's address.
     * @return true if the pair request has been successfully submitted, false if not
     */
    public boolean pair(String address) {
        return bleIasConnection.pair(address);

    }

    /**
     * Unpair from an already paired device.
     * This is an asynchronous call. Register for {@link #ACTION_ON_BOND_STATE_CHANGED}
     * intents to be notified about the bonding process.
     * The pairing status will be added as an extra ({@link #EXTRA_BOND_STATUS}), as {@link com.intel
     * .icsf.connection.device.DeviceBleConnection.BOND_STATE}s
     * @param address The address of the device to unpair from.
     * @return true if the unpair request has been successfully submitted, false if not
     */
    public boolean unpair(String address) {
        return bleIasConnection.unpair(address);
    }

    /**
     * Method for writing a value to a BLE characteristic.
     * Before being able to write to a characteristic, a connection must be established and services
     * must be discovered.
     * This is an asynchronous call. Register for {@link #ACTION_ON_CHARACTERISTIC_WRITE} intents
     * to be notified about the write status. Extras will contain:
     * -The service UUID ({@link #EXTRA_CHAR_SERVICE_UUID})
     * -The characteristic UUID ({@link #EXTRA_CHAR_UUID})
     * -The characteristic value (({@link #EXTRA_CHAR_VALUE}))
     * -The gatt status ({@link #EXTRA_STATUS} as a
     * {@link IBleConnection.GATT_STATUS})
     * @param service The unique identifier of the BLE service
     * @param characteristic The unique identifier of the BLE characteristic
     * @param value The value to write
     * @return true if the write request has been successfully emitted, false if not
     * If the specified service is not among the discovered services, the write request will not
     *              be sent and the method will return false.
     */
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value) {
       return  bleIasConnection.writeCharacteristic(service, characteristic, value);
    }

    /**
     * Method for writing a value to a BLE characteristic.
     * Before being able to write to a characteristic, a connection must be established and services
     * must be discovered.
     * This is an asynchronous call. Register for {@link #ACTION_ON_CHARACTERISTIC_WRITE} intents
     * to be notified about the write status. Extras will contain:
     * -The service UUID ({@link #EXTRA_CHAR_SERVICE_UUID})
     * -The characteristic UUID ({@link #EXTRA_CHAR_UUID})
     * -The characteristic value (({@link #EXTRA_CHAR_VALUE}))
     * -The gatt status ({@link #EXTRA_STATUS} as a
     * {@link IBleConnection.GATT_STATUS})
     * @param service The unique identifier of the BLE service
     * @param characteristic The unique identifier of the BLE characteristic
     * @param value The value to write
     * @param noResponse Flag that specifies the write type to execute: true means no response write
     *                   false means acknowledged write.
     * @return true if the write request has been successfully emitted, false if not
     * If the specified service is not among the discovered services, the write request will not
     *              be sent and the method will return false.
     */
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value,
                                    boolean noResponse) {
        return bleIasConnection.writeCharacteristic(service, characteristic, value, noResponse);
    }

    /**
     * Method for reading the value of a BLE characteristic.
     * Before being able to read a characteristic value, a connection must be established and services
     * must be discovered.
     * This is an asynchronous call. Register for {@link #ACTION_ON_CHARACTERISTIC_READ} intents
     * to be notified about the write status. Extras will contain:
     * -The service UUID ({@link #EXTRA_CHAR_SERVICE_UUID})
     * -The characteristic UUID ({@link #EXTRA_CHAR_UUID})
     * -The characteristic value (({@link #EXTRA_CHAR_VALUE}))
     * -The gatt status ({@link #EXTRA_STATUS} as a
     * {@link IBleConnection.GATT_STATUS})
     * Remember to consider the gatt status: any status that is not com.intel.icsf.connection.device.
     * IBleConnection.GATT_STATUS.GATT_SUCCESS is suspicious.
     * @param service The unique identifier of the BLE service
     * @param characteristic The unique identifier of the BLE characteristic
     * @return true if the read request has been successfully emitted, false if not
     *     * If the specified service is not among the discovered services, the read request will not
     *              be sent and the method will return false.
     */
    public boolean readCharacteristic(UUID service, UUID characteristic) {
        return bleIasConnection.readCharacteristic(service, characteristic);
    }

    /**
     * Method for discovering BLE services of an active connection.
     * Before being able to discover the services, a successful BLE connection has to be established.
     * This is an asynchronous call. Register for {@link #ACTION_ON_SERVICES_DISCOVERED} intents
     * to get the discovered services list. The services UUID list can be retrieved using the
     * {@link #EXTRA_SERVICES} extra. The gatt status is provided using ({@link #EXTRA_STATUS} as a
     * {@link IBleConnection.GATT_STATUS})
     * @return true if the discover services request was successfully emitted, false if not
     */
    public boolean discoverServices() {
        return bleIasConnection.discoverServices();
    }

    /**
     * Method for subscribing to a BLE characteristic.
     * Before being able to subscribe to a characteristic, a successful connection must be
     * established and services must be discovered.
     * This is an asynchronous call. Register for {@link #ACTION_ON_CHARACTERISTIC_CHANGED} intents
     * to be notified about the write status. Extras will contain:
     * The service UUID ({@link #EXTRA_CHAR_SERVICE_UUID})
     * -The characteristic UUID ({@link #EXTRA_CHAR_UUID})
     * -The characteristic value (({@link #EXTRA_CHAR_VALUE}))
     * -The gatt status ({@link #EXTRA_STATUS} as a
     * {@link IBleConnection.GATT_STATUS})
     * @param service The unique identifier of the BLE service
     * @param characteristic The unique identifier of the BLE characteristic
     * @param enabled Flag indicating whether subscription should be enabled (true is enabled, false if not)
     * @return true if the subscribe request was successfully emitted, false if not
     * If the specified service is not among the discovered services, the subscribe request will not
     * be sent and the method will return false.
     */
    public boolean subscribeToCharacteristic(UUID service, UUID characteristic, boolean enabled) {
        return bleIasConnection.subscribeToCharacteristic(service, characteristic, enabled);
    }

    /**
     * Method returning the discovered services.
     * This method can be called after establishing a successful connection and discovering
     * the services (discoverServices(...))
     * @return The UUID list of discovered services if services were discovered, null if not
     */
    public List<UUID> getServices() {
        List<UUID> resultServices = new ArrayList<>();
        for(BluetoothGattService service: bleIasConnection.getServices()) {
            resultServices.add(service.getUuid());
        }
        return resultServices;
    }

    /**
     * Check that the specified BLE characteristic was discovered among services
     * A BLE connection must be established and services should be discovered.
     * @param service The service unique identifier
     * @param characteristic The characteristic unique identifier
     * @return true is the characteristic was discovered, false if not
     */
    public boolean characTeristicExists(UUID service, UUID characteristic) {
        return bleIasConnection.characTeristicExists(service, characteristic);
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void onDestroy() {
        LogUtils.LOGI(TAG, "OnDestroy is being called");
        super.onDestroy();
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void onCreate() {
        LogUtils.LOGI(TAG, "Oncreate is being called");
        super.onCreate();
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void onRebind(Intent intent) {
        LogUtils.LOGI(TAG, "Onrebind called");
        super.onRebind(intent);
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void onTaskRemoved(Intent rootIntent) {
        LogUtils.LOGI(TAG, "Ontaskremoved called");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.LOGI(TAG, "Starting service, should not be detroyed even if unbound");
        return super.onStartCommand(intent, flags, startId);

    }
}
