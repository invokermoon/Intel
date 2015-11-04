package com.intel.ispplib.connection.device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.intel.ispplib.connection.device.utils.DeviceUtils;
import com.intel.ispplib.ias.ispp.IBleCallback;
import com.intel.ispplib.ias.ispp.IsppUtils;
import com.intel.ispplib.utils.LogUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Class implementing BLE communication (used by BleConnection)
 * by enqueuing all the ble requests and waiting for the corresponding callbacks
 * to process the next one.
 */
public class DeviceBleConnection implements  IBleConnection{
    private CONNECTION_STATE connectionState;


    public enum CONNECTION_STATE {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    public enum BOND_STATE {
        PAIRING,
        PAIRED,
        UNPAIRING,
        UNPAIRED
    }

    public enum EXECUTE_STATE {
        EXECUTING_TASK,
        IDLE
    }

    public enum TYPE {
        CONNECT,
        DISCONNECT,
        READCHAR,
        WRITECHAR,
        DISCOVERSERVICES,
        SUBSCRIBE,
        PAIR,
        UNPAIR
    }


    private Runnable mConnectionRunnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.LOGI(TAG, "Connection timeout reached");
            mConnectionState = CONNECTION_STATE.DISCONNECTED;
            mBluetoothGatt.disconnect();
            //Returning with gatt error
            mCallback.onConnectionStateChange(null, 0x0085, BluetoothProfile.STATE_DISCONNECTED);
        }
    };

    private Runnable mDisconnectionRunnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.LOGI(TAG, "Disconnection timeout reached");
            //Even with errors getting here from connected state,
            // we consider the connection state to be disconnected
            //Returning with gatt error
            mCallback.onConnectionStateChange(null, 0x0085, BluetoothProfile.STATE_DISCONNECTED);
            mConnectionState = CONNECTION_STATE.DISCONNECTED;
        }
    };

    private class PairReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            LogUtils.LOGI(TAG, "Received: intent:  " + intent.getAction());
            if(intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final int state =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                BluetoothDevice.ERROR);
                if(state == BluetoothDevice.BOND_BONDED &&
                        prevState == BluetoothDevice.BOND_BONDING){
                    LogUtils.LOGI(TAG, "The device is bonded!");
                    mBondState = BOND_STATE.PAIRED;
                    if(mCallback != null) {
                        mCallback.onBondStateChanged(BOND_STATE.PAIRED);
                    }
                    if(currentExecutingTask != null &&
                            currentExecutingTask.getType().equals(TYPE.PAIR)) {
                        if(waitForCallback != null) waitForCallback.countDown();
                    }
                    mContext.unregisterReceiver(mPairReceiver);
                } else if((state == BluetoothDevice.BOND_NONE)) {
                    LogUtils.LOGI(TAG, "The device is unpaired");
                    mBondState = BOND_STATE.UNPAIRED;
                    if(mCallback != null) {
                        mCallback.onBondStateChanged(BOND_STATE.UNPAIRED);
                    }
                    if(currentExecutingTask != null &&
                            currentExecutingTask.getType().equals(TYPE.UNPAIR)) {
                        if(waitForCallback != null) waitForCallback.countDown();
                    }
                    mContext.unregisterReceiver(mPairReceiver);
                } else if((state == BluetoothDevice.BOND_BONDING)) {
                    if(mCallback != null) {
                        mCallback.onBondStateChanged(BOND_STATE.PAIRING);
                    }
                }
            }

        }
    }

    private class LocalDeviceBleConnectionGattCallback extends  BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(currentExecutingTask != null) {
                if((newState == BluetoothProfile.STATE_CONNECTED &&
                        currentExecutingTask.getType().equals(TYPE.CONNECT)) ||
                        newState == BluetoothProfile.STATE_DISCONNECTED &&
                        currentExecutingTask.getType().equals(TYPE.DISCONNECT)) {
                    LogUtils.LOGI(TAG, "Connection state changed: counting down latch");
                    if(waitForCallback != null) waitForCallback.countDown();
                }
            }
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionHandler.removeCallbacks(mConnectionRunnable);
                if(status == BluetoothGatt.GATT_SUCCESS ) {
                    mConnectionState = CONNECTION_STATE.CONNECTED;
                } else {
                    //Considered to be disconnected so that a new connection attempt can be made
                    mConnectionState = CONNECTION_STATE.DISCONNECTED;
                }
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = CONNECTION_STATE.DISCONNECTED;
                mConnectionHandler.removeCallbacks(mDisconnectionRunnable);
            }
            if(mCallback != null) {
                mCallback.onConnectionStateChange(gatt, status, newState);
            }
            if(isppCallback != null) {
                isppCallback.onConnectionStateChange(gatt, status, newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(currentExecutingTask != null &&
                    currentExecutingTask.getType().equals(TYPE.DISCOVERSERVICES)) {
                LogUtils.LOGI(TAG, "Services discovered: counting down latch");
                if(waitForCallback != null) waitForCallback.countDown();
            }
            if(mCallback != null) {
                mCallback.onServicesDiscovered(gatt, status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if(currentExecutingTask != null &&
                    currentExecutingTask.getType().equals(TYPE.READCHAR)) {
                LogUtils.LOGI(TAG, "characteristic read: counting down latch");
                if(waitForCallback != null) waitForCallback.countDown();
            }
            if(mCallback != null) {
                mCallback.onCharacteristicRead(gatt, characteristic, status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if(currentExecutingTask != null &&
                    currentExecutingTask.getType().equals(TYPE.WRITECHAR)) {
                LogUtils.LOGI(TAG, "characteristic write received: counting down latch");
                if(waitForCallback != null) waitForCallback.countDown();
            }
            if(mCallback != null) {
                mCallback.onCharacteristicWrite(gatt, characteristic, status);
            }
            if(isppCallback != null) {
                LogUtils.LOGI(TAG, "Calling oncharacteristic write for ispp");
                isppCallback.onCharacteristicWrite(gatt, characteristic, status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            LogUtils.LOGI(TAG, "On characteristic changed message received: " +
                    characteristic.getUuid());
            if(mCallback != null) {
                mCallback.onCharacteristicChanged(gatt, characteristic);
            }
            if(isppCallback != null) {
                LogUtils.LOGI(TAG, "Calling char changed on ispp: " + characteristic.getUuid());
                isppCallback.onCharacteristicChanged(gatt, characteristic);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                     BluetoothGattDescriptor descriptor, int status) {
            if(mCallback != null) {
                mCallback.onDescriptorRead(gatt, descriptor, status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if(currentExecutingTask != null &&
                    currentExecutingTask.getType().equals(TYPE.SUBSCRIBE)) {
                if(waitForCallback != null) waitForCallback.countDown();
                LogUtils.LOGI(TAG, "Subscribe callback received, countting down latch");
            }
            if(mCallback != null) {
                mCallback.onDescriptorWrite(gatt, descriptor, status);
            }
            if(isppCallback != null) {
                LogUtils.LOGI(TAG, "calling ondescriptor write ispp");
                isppCallback.onDescriptorWrite(gatt, descriptor, status);
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            if(mCallback != null) {
                mCallback.onReliableWriteCompleted(gatt, status);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if(mCallback != null) {
                mCallback.onReadRemoteRssi(gatt, rssi, status);
            }
        }
    }

    private class BleTask {
        private TYPE type;

        public BleTask(TYPE type) {
            this.type = type;
        }

        public TYPE getType() {
            return type;
        }

        public void setType(TYPE type) {
            this.type = type;
        }

        public void execute(long millis) {
            waitForCallback = new CountDownLatch(1);
            try {
                LogUtils.LOGI(TAG, "Creating countdown latch, with duration: " + millis);
                boolean result = waitForCallback.await(millis, TimeUnit.MILLISECONDS);
                if(!result) Log.i(TAG, "Timeout expired");
                mExecutionState = EXECUTE_STATE.IDLE;
                processNextMessage();
            } catch (InterruptedException e) {
                Log.e(TAG, "Problem while awaiting coundown latch.");
            }
        }

        public void execute() {
            waitForCallback = new CountDownLatch(1);
            try {
                LogUtils.LOGI(TAG, "Creating countdown latch, with duration: " + 500);
               boolean result = waitForCallback.await(500, TimeUnit.MILLISECONDS);
                if(!result) Log.i(TAG, "Timeout expired");
                mExecutionState = EXECUTE_STATE.IDLE;
                processNextMessage();
            } catch (InterruptedException e) {
                Log.e(TAG, "Problem while awaiting coundown latch.");
            }
        }

        public void executeNextWithoutWaiting() {
            mExecutionState = EXECUTE_STATE.IDLE;
            processNextMessage();
        }
    }

    private class ConnectTask extends  BleTask {
        private String deviceAddress;
        private boolean autoConnect;
        public ConnectTask(String deviceAddress, boolean autoConnect) {
            this(TYPE.CONNECT);
            this.deviceAddress = deviceAddress;
            this.autoConnect = autoConnect;
        }
        private ConnectTask(TYPE type) {
            super(type);
        }

        public String getDeviceAddress() {
            return deviceAddress;
        }

        public boolean isAutoConnect() {
            return autoConnect;
        }

        @Override
        public void execute() {
            if (bluetoothAdapter != null) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                if (device == null) { //The address does not match any remote device
                    executeNextWithoutWaiting();
                } else {
                    mConnectedDevice = device;
                    if(mConnectionState != null && mConnectedDevice.equals(device) &&
                            mBluetoothGatt != null) {
                        LogUtils.LOGI(TAG, "Cleaning previous connection" + device.getAddress());
                        mBluetoothGatt.close();

                    }
                    LogUtils.LOGI(TAG, "Connecting to device: " +
                            device.getAddress());
                    mBluetoothGatt = device.connectGatt(mContext, autoConnect, mLocalGattCallback);
                    mConnectionHandler.postDelayed(mConnectionRunnable, CONNECTION_DELAY);
                    super.execute(CONNECTION_DELAY);
                }
            } else {
                executeNextWithoutWaiting();
            }
        }
    }

    private class DisconnectTask extends BleTask {
        private String deviceAddress;
        public DisconnectTask(String deviceAddress) {
            this(TYPE.DISCONNECT);
            this.deviceAddress = deviceAddress;
        }
        private DisconnectTask(TYPE type) {
            super(type);
        }

        public String getDeviceAddress() {
            return deviceAddress;
        }
        @Override
        public void execute() {
            LogUtils.LOGI(TAG, "Executing disconnect");
            if(mBluetoothGatt == null) {
                LogUtils.LOGI(TAG, "Gatt is null");
                executeNextWithoutWaiting();
            } else {
                LogUtils.LOGI(TAG, "Disconnecting now");
                mBluetoothGatt.disconnect();
                mConnectionHandler.postDelayed(mDisconnectionRunnable, CONNECTION_DELAY);
                super.execute(CONNECTION_DELAY);
            }

        }
    }

    private class ReadCharTask extends BleTask {
        private UUID service;
        private UUID characteristic;

        public ReadCharTask(UUID service, UUID characteristic) {
            this(TYPE.READCHAR);
            this.service = service;
            this.characteristic = characteristic;
        }
        private ReadCharTask(TYPE type) {
            super(type);
        }

        public UUID getService() {
            return service;
        }

        public UUID getCharacteristic() {
            return characteristic;
        }

        @Override
        public void execute() {
            if (mBluetoothGatt != null) {
                BluetoothGattService gattService = mBluetoothGatt.getService(service);
                if (gattService != null) {
                    BluetoothGattCharacteristic gattCharacteristic =
                            gattService.getCharacteristic(characteristic);
                    if (gattCharacteristic != null) {
                        LogUtils.LOGI(TAG, "Executing read characteristic");
                        mBluetoothGatt.readCharacteristic(gattCharacteristic);
                        super.execute();
                    } else {
                        executeNextWithoutWaiting();
                    }
                } else {
                    executeNextWithoutWaiting();
                }
            } else {
                executeNextWithoutWaiting();
            }
        }

    }

    private class WriteCharTask extends BleTask {
        private UUID service;
        private UUID characteristic;
        private byte [] value;
        private boolean noResponse;

        public WriteCharTask(UUID service, UUID characteristic, byte [] value, boolean noResponse) {
            this(TYPE.WRITECHAR);
            this.service = service;
            this.characteristic = characteristic;
            this.value = value;
            this.noResponse = noResponse;
        }

        private WriteCharTask(TYPE type) {
            super(type);
        }

        public UUID getService() {
            return service;
        }

        public UUID getCharacteristic() {
            return characteristic;
        }

        public boolean isNoResponse() {
            return noResponse;
        }

        public byte[] getValue() {
            return value;
        }

        @Override
        public void execute() {
            if (mBluetoothGatt != null) {
                BluetoothGattService gattService = mBluetoothGatt.getService(service);
                if (gattService != null) {
                    LogUtils.LOGI(TAG, "Executing write char task");
                    BluetoothGattCharacteristic gattCharacteristic =
                            gattService.getCharacteristic(characteristic);
                    if (gattCharacteristic != null) {
                        if(!noResponse) gattCharacteristic.setWriteType(BluetoothGattCharacteristic.
                                WRITE_TYPE_DEFAULT);
                        gattCharacteristic.setValue(value);
                        boolean result = mBluetoothGatt.writeCharacteristic(gattCharacteristic);
                        LogUtils.LOGI(TAG, "Write char task sent: " + result);
                        super.execute();
                    } else {
                        executeNextWithoutWaiting();
                    }
                } else {
                    executeNextWithoutWaiting();
                }
            } else {
                executeNextWithoutWaiting();
            }
        }
    }

    private class DiscoverServicesTask extends BleTask {

        public DiscoverServicesTask() {
            this(TYPE.DISCOVERSERVICES);
        }
        private DiscoverServicesTask(TYPE type) {
            super(type);
        }

        @Override
        public void execute() {
            if(mBluetoothGatt != null) {
                LogUtils.LOGI(TAG, "Executing services discovery");
                mBluetoothGatt.discoverServices();
                super.execute(10000);
            } else {
                executeNextWithoutWaiting();
            }
        }
    }

    private class SubscribeTask extends BleTask {
        private UUID service;
        private UUID characteristic;
        private boolean subscribe;

        public SubscribeTask(UUID service, UUID characteristic, boolean subscribe) {
            this(TYPE.SUBSCRIBE);
            this.service = service;
            this.characteristic = characteristic;
            this.subscribe = subscribe;
        }

        private SubscribeTask(TYPE type) {
            super(type);
        }

        public UUID getService() {
            return service;
        }

        public UUID getCharacteristic() {
            return characteristic;
        }

        public boolean isSubscribe() {
            return subscribe;
        }

        @Override
        public void execute() {
            LogUtils.LOGI(TAG, "Subscribing to characteristic");
            if(mBluetoothGatt != null) {
                BluetoothGattService gattService = mBluetoothGatt.getService(service);
                if(gattService != null) {
                    BluetoothGattCharacteristic gattCharacteristic =
                            gattService.getCharacteristic(characteristic);
                    if(gattCharacteristic != null) {
                        mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, subscribe);
                        BluetoothGattDescriptor descriptor =
                                gattCharacteristic.getDescriptor(IsppUtils.CLIENT_CHARACTERISTIC_CONFIG_UUID);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);
                        super.execute();
                    } else {
                        executeNextWithoutWaiting();
                    }
                } else {
                    executeNextWithoutWaiting();
                }
            } else {
                executeNextWithoutWaiting();
            }
        }
    }

    private class PairTask extends BleTask {
        private String deviceAddress;

        public PairTask(String deviceAddress) {
            this(TYPE.PAIR);
            this.deviceAddress = deviceAddress;
        }

        private PairTask(TYPE type) {
            super(type);
        }

        public String getDeviceAddress() {
            return deviceAddress;
        }

        @Override
        public void execute() {
            if(Build.VERSION.SDK_INT<Build.VERSION_CODES.JELLY_BEAN_MR2){
                LogUtils.LOGI(TAG, "Pairing for ble device is not supported on this system");
                return;
            }
            if(bluetoothAdapter != null) {
                final BluetoothDevice device =
                        bluetoothAdapter.getRemoteDevice(deviceAddress);
                if (device != null) {
                    if(device.getBondState() == BluetoothDevice.BOND_NONE) {
                        Log.i(TAG, "start bonding..");
                        Method localMethod = null;
                        try {
                            localMethod = device.getClass().
                                    getMethod("createBond", new Class[0]);
                        } catch (NoSuchMethodException e) {
                            Log.e(TAG, "No removeBond method");
                        }
                        if (localMethod != null) {
                            try {
                                final boolean result = (boolean) localMethod.invoke(device);
                            } catch (IllegalAccessException e) {
                                Log.e(TAG, "Exception invoking createBond: " + e.getMessage());

                            } catch (InvocationTargetException e) {
                                Log.e(TAG, "Exception invoking createBond: " + e.getMessage());
                            }
                            IntentFilter intent = new IntentFilter();
                            intent.addAction(BluetoothDevice.ACTION_FOUND);
                            intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                            intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                            intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                            LogUtils.LOGI(TAG, "Registering receiver");
                            mContext.registerReceiver(mPairReceiver, intent);
                            super.execute(PAIRING_DELAY);
                        }
                    }
                } else {
                    executeNextWithoutWaiting();
                }
            } else {
                executeNextWithoutWaiting();
            }

        }
    }

    private class UnpairTask extends BleTask {
        private String deviceAddress;

        public UnpairTask(String deviceAddress) {
            this(TYPE.UNPAIR);
            this.deviceAddress = deviceAddress;
        }

        private UnpairTask(TYPE type) {
            super(type);
        }

        @Override
        public void execute() {
            if(Build.VERSION.SDK_INT<Build.VERSION_CODES.JELLY_BEAN_MR2){
                LogUtils.LOGI(TAG, "UnPairing for ble device is not supported on this system");
                return;
            }
            if(bluetoothAdapter != null) {
                final BluetoothDevice device =
                        bluetoothAdapter.getRemoteDevice(deviceAddress);
                if (device != null) {
                    if(device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        Log.i(TAG, "Start unpairing...");
                        Method localMethod = null;
                        try {
                            localMethod = device.getClass().
                                    getMethod("removeBond", new Class[0]);
                        } catch (NoSuchMethodException e) {
                            Log.e(TAG, "No removeBond method");
                        }
                        if (localMethod != null) {
                            try {
                                final boolean result = (boolean) localMethod.invoke(device);
                            } catch (IllegalAccessException e) {
                                Log.e(TAG, "Exception invoking removeBond: " + e.getMessage());

                            } catch (InvocationTargetException e) {
                                Log.e(TAG, "Exception invoking removeBond: " + e.getMessage());
                            }
                            IntentFilter intent = new IntentFilter();
                            intent.addAction(BluetoothDevice.ACTION_FOUND);
                            intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                            intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                            intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                            mContext.registerReceiver(mPairReceiver, intent);
                            super.execute(1000);
                        }
                    }
                } else {
                    executeNextWithoutWaiting();
                }
            } else {
                executeNextWithoutWaiting();
            }
        }
    }
    private DeviceBleConnectionGattCallback mCallback;
    private BluetoothAdapter bluetoothAdapter;
    private Context mContext;
    private LinkedBlockingQueue<BleTask> bleTasks;
    private EXECUTE_STATE mExecutionState;
    private CONNECTION_STATE mConnectionState;
    private BOND_STATE mBondState;
    private BluetoothDevice mConnectedDevice;
    private CountDownLatch waitForCallback;
    private Handler mHandler;
    private Handler mConnectionHandler;
    private static final String TAG = "DeviceBleConnection";
    private BleTask currentExecutingTask;
    private LocalDeviceBleConnectionGattCallback mLocalGattCallback;
    private BluetoothGatt mBluetoothGatt;
    private PairReceiver mPairReceiver;
    private static long CONNECTION_DELAY = 10000;
    private static long PAIRING_DELAY = 10000;
    private IBleCallback isppCallback;
    private String configuredDeviceAddress;

    public DeviceBleConnection(Context context, DeviceBleConnectionGattCallback callback) {
        this.mContext = context;
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        mCallback = callback;
        bleTasks = new LinkedBlockingQueue<BleTask>();
        initHandler();
        mExecutionState = EXECUTE_STATE.IDLE;
        mConnectionState = CONNECTION_STATE.DISCONNECTED;
        mBondState = BOND_STATE.UNPAIRED;
        mLocalGattCallback = new LocalDeviceBleConnectionGattCallback();
        mPairReceiver = new PairReceiver();
        mConnectionHandler = new Handler();
    }
    private final class DeviceBleConnectionHandlerCallback implements Handler.Callback {

        public static final int MESSAGE_PROCESS_NEXT_MESSAGE = 1;

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_PROCESS_NEXT_MESSAGE:
                    sendNextMessage();
                    break;
            }
            return false;
        }

        private void sendNextMessage() {
            if(mExecutionState == EXECUTE_STATE.IDLE) {
                BleTask task = bleTasks.poll();
               if(task != null) {
                   mExecutionState = EXECUTE_STATE.EXECUTING_TASK;
                   currentExecutingTask = task;
                   task.execute();
               }
            }
        }
    }

    private void initHandler() {
        final DeviceBleConnectionHandlerCallback isspHandlerCallback =
                new DeviceBleConnectionHandlerCallback();
        final HandlerThread thread = new HandlerThread(DeviceBleConnection.class.getName(),
                android.os.Process.THREAD_PRIORITY_AUDIO);
        thread.start();
        mHandler = new Handler(thread.getLooper(), isspHandlerCallback);
    }

    private void setPairedState() {
        if(DeviceUtils.getInstance(mContext).isDeviceBonded(configuredDeviceAddress)) {
            mBondState = BOND_STATE.PAIRED;
        }
        else {
            if(mBondState != BOND_STATE.PAIRING && mBondState != BOND_STATE.UNPAIRING)
                mBondState = BOND_STATE.UNPAIRED;
        }
    }

    private void setConnectedState() {
        if(DeviceUtils.getInstance(mContext).isDeviceConnected(configuredDeviceAddress)) {
            mConnectionState = CONNECTION_STATE.CONNECTED;
        }
        else {
            if(mConnectionState != CONNECTION_STATE.CONNECTING &&
                    mConnectionState != CONNECTION_STATE.DISCONNECTING)
                mConnectionState = CONNECTION_STATE.DISCONNECTED;
        }
    }

    private boolean sendConnectTask(String address, boolean autoConnect) {
        mConnectionState = CONNECTION_STATE.CONNECTING;
        //We onserved that connecting state callback is not called by the ble stack
        mCallback.onConnectionStateChange(null, BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_CONNECTING);
        LogUtils.LOGI(TAG, "Connecting");
        ConnectTask connectTask = new ConnectTask(address, autoConnect);
        bleTasks.add(connectTask);
        processNextMessage();
        return true;
    }

    private boolean sendDisconnectTask() {
        mConnectionState = CONNECTION_STATE.DISCONNECTING;
        mCallback.onConnectionStateChange(null, BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTING);
        LogUtils.LOGI(TAG, "Disconnecting");
        DisconnectTask disconnectTask = new DisconnectTask(mConnectedDevice.getAddress());
        bleTasks.add(disconnectTask);
        processNextMessage();
        return true;
    }

    public boolean isConnected() {
        if(configuredDeviceAddress == null)
            return false;
        return DeviceUtils.getInstance(mContext).isDeviceConnected(configuredDeviceAddress);
    }

    public boolean isConnectedToDevice(String address) {
        if(configuredDeviceAddress != null &&
                configuredDeviceAddress.equals(address)) {
            return DeviceUtils.getInstance(mContext).
                    isDeviceConnected(configuredDeviceAddress);
        }
        return false;
    }

    public boolean isPairedToDevice(String address) {
        if(configuredDeviceAddress != null &&
                configuredDeviceAddress.equals(address)) {
            return DeviceUtils.getInstance(mContext).
                    isDeviceBonded(configuredDeviceAddress);
        }
        return false;
    }

    @Override
    public boolean connect(String address, boolean autoConnect) {
        if(configuredDeviceAddress == null || configuredDeviceAddress.equals(address) ||
                (!DeviceUtils.getInstance(mContext).isDeviceBonded(configuredDeviceAddress) &&
                        !DeviceUtils.getInstance(mContext).
                                isDeviceConnected(configuredDeviceAddress))) {
            configuredDeviceAddress = address;
            setPairedState();
            if(DeviceUtils.getInstance(mContext).isDeviceConnected(address)) {
                mConnectionState = CONNECTION_STATE.CONNECTED;
                return false;
            }
            else { //Device is not connected, connect!
                return sendConnectTask(address, autoConnect);
            }
        }
        return false;
    }

    @Override
    public boolean disconnect() {
        LogUtils.LOGI(TAG, "Disconnecting from DeviceBleConnection, configuredDeviceAddress is: " +
                configuredDeviceAddress);
        if(configuredDeviceAddress != null) {
            if(DeviceUtils.getInstance(mContext).isDeviceConnected(configuredDeviceAddress)) {
                return sendDisconnectTask();
            } else {
                LogUtils.LOGI(TAG, "The device: " + configuredDeviceAddress +
                        "is not considered as connected");
            }
        }
        return false;
    }

    private boolean sendPairTask(String deviceAddress) {
        mBondState = BOND_STATE.PAIRING;
        PairTask pairTask = new PairTask(deviceAddress);
        bleTasks.add(pairTask);
        processNextMessage();
        return true;
    }

    private boolean sendUnpairTask(String deviceAddress) {
        mBondState = BOND_STATE.UNPAIRING;
        UnpairTask unpairTask = new UnpairTask(deviceAddress);
        bleTasks.add(unpairTask);
        processNextMessage();
        return true;
    }

    @Override
    public boolean pair(String address) {
        if(configuredDeviceAddress == null || configuredDeviceAddress.equals(address) ||
                (!DeviceUtils.getInstance(mContext).isDeviceBonded(configuredDeviceAddress) &&
                        !DeviceUtils.getInstance(mContext).
                                isDeviceConnected(configuredDeviceAddress))) {
            configuredDeviceAddress = address;
            setConnectedState();
            if(DeviceUtils.getInstance(mContext).isDeviceBonded(address)) {
                mBondState = BOND_STATE.PAIRED;
                return false;
            } else {
                return sendPairTask(address);
            }
        }
        return false;
    }

    @Override
    public boolean unpair(String address) {
        if(configuredDeviceAddress != null && configuredDeviceAddress.equals(address)) {
            if(DeviceUtils.getInstance(mContext).isDeviceBonded(configuredDeviceAddress)) {
                return sendUnpairTask(address);
            }
        }
        return false;
    }

    @Override
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value) {
        if(mBluetoothGatt != null && isConnected() && characteristicExists(service, characteristic)) {
            WriteCharTask writeCharTask = new WriteCharTask(service, characteristic, value, true);
            bleTasks.add(writeCharTask);
            processNextMessage();
            return true;
        }
        return false;
    }

    @Override
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value,
                                       boolean noResponse) {
        if(mBluetoothGatt != null && isConnected() && characteristicExists(service, characteristic)) {
            WriteCharTask writeCharTask = new WriteCharTask(service, characteristic, value, noResponse);
            bleTasks.add(writeCharTask);
            processNextMessage();
            return true;
        }
        return false;
    }

    @Override
    public boolean readCharacteristic(UUID service, UUID characteristic) {
        if(mBluetoothGatt != null && isConnected() && characteristicExists(service, characteristic)) {
            ReadCharTask readCharTask = new ReadCharTask(service, characteristic);
            bleTasks.add(readCharTask);
            processNextMessage();
            return true;
        }
        return false;
    }

    @Override
    public boolean discoverServices() {
        if(mBluetoothGatt != null && isConnected()) {
            DiscoverServicesTask discoverServicesTask = new DiscoverServicesTask();
            bleTasks.add(discoverServicesTask);
            processNextMessage();
            return true;
        }
        return false;
    }

    @Override
    public boolean subscribeToCharacteristic(UUID service, UUID characteristic, boolean enabled) {
        if(mBluetoothGatt != null && isConnected() &&
                characteristicExists(service, characteristic)) {
            SubscribeTask subscribeTask = new SubscribeTask(service, characteristic, enabled);
            bleTasks.add(subscribeTask);
            processNextMessage();
            return true;
        }
        return false;
    }

    public boolean characteristicExists(UUID service, UUID characteristic) {
        for(BluetoothGattService aService: getServices()) {
            if(aService.getUuid().equals(service)) {
                if(aService.getCharacteristic(characteristic)!= null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void setBleCallbackForIspp(IBleCallback bleCallback) {
        isppCallback = bleCallback;
    }

    private void processNextMessage() {
        if (!mHandler.sendMessage(
                mHandler.obtainMessage(DeviceBleConnectionHandlerCallback.
                        MESSAGE_PROCESS_NEXT_MESSAGE))) {
            Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET message!!");
        }
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null &&
                bluetoothAdapter.isEnabled();
    }

    public List<BluetoothGattService> getServices() {
        if(mBluetoothGatt != null && isConnected()) {
            return mBluetoothGatt.getServices();
        }
        return null;
    }
    public CONNECTION_STATE getConnectionState() {
        return mConnectionState;
    }

    public void close() {
        if(mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }
    }

}
