package com.intel.ispplib.connection.device;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.intel.ispplib.ias.ispp.IBleCallback;
import com.intel.ispplib.ias.ispp.Ispp;
import com.intel.ispplib.ias.ispp.IsppRxCallBack;
import com.intel.ispplib.utils.LogUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BleIasConnection implements IBleConnection {

    private IBleConnectionCallBack mBleConnectionCallBack;
    private BluetoothGattCallback mGattCallback;
    private DeviceBleConnection mDeviceBleConnection;
    private LocalGattCallback mLocalGattCallback;
    private Ispp ispp;
    private static final String TAG = "BleConnection";
    private static BleIasConnection sInstance = null;
    @NonNull
    private static final Object sLock = new Object();

    public static BleIasConnection getInstance(Context context) {
        BleIasConnection bleIasConnection = sInstance;
        if (bleIasConnection == null) {
            synchronized (sLock) {
                if (bleIasConnection == null) {
                    sInstance = bleIasConnection = new BleIasConnection(context);
                }
            }
        }
        return bleIasConnection;
    }

    public void close() {
        mDeviceBleConnection.close();
    }

    public boolean isPairedToDevice(String address) {
        return mDeviceBleConnection.isPairedToDevice(address);
    }

    public boolean characTeristicExists(UUID service, UUID characteristic) {
        return mDeviceBleConnection.characteristicExists(service, characteristic);
    }

    private class LocalGattCallback extends DeviceBleConnectionGattCallback {
        public LocalGattCallback() {
            super();
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "Connection state changed: status: "+ status + " newstate: " + newState);
            if(mGattCallback != null) {
                mGattCallback.onConnectionStateChange(gatt, status, newState);
            }
            if(mBleConnectionCallBack != null) {
                    switch (newState) {
                        case BluetoothProfile.STATE_CONNECTED:
                            mBleConnectionCallBack.onStatusChanged(CONNECTION_STATUS.CONNECTED,
                                    statusToGattStatusEnum(status));
                            break;
                        case BluetoothProfile.STATE_DISCONNECTED:
                            mBleConnectionCallBack.onStatusChanged(CONNECTION_STATUS.DISCONNECTED,
                                    statusToGattStatusEnum(status));
                            break;
                        case BluetoothProfile.STATE_CONNECTING:
                            mBleConnectionCallBack.onStatusChanged(CONNECTION_STATUS.CONNECTING,
                                    statusToGattStatusEnum(status));
                            break;
                        case BluetoothProfile.STATE_DISCONNECTING:
                            mBleConnectionCallBack.onStatusChanged(CONNECTION_STATUS.DISCONNECTING,
                                    statusToGattStatusEnum(status));
                            break;
                    }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(mGattCallback != null) {
                mGattCallback.onServicesDiscovered(gatt, status);
            }
            if(mBleConnectionCallBack != null) {
                mBleConnectionCallBack.onServicesDiscovered(gatt.getServices(),
                        statusToGattStatusEnum(status));
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            if(mGattCallback != null) {
                mGattCallback.onCharacteristicRead(gatt, characteristic, status);
            }
            if(mBleConnectionCallBack != null) {
                mBleConnectionCallBack.onCharacteristicRead(characteristic,
                        statusToGattStatusEnum(status));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if(mGattCallback != null) {
                mGattCallback.onCharacteristicWrite(gatt, characteristic, status);
            }
            if(mBleConnectionCallBack != null) {
                mBleConnectionCallBack.onCharacteristicWrite(characteristic,
                        statusToGattStatusEnum(status));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            LogUtils.LOGI(TAG, "Char changed: " + characteristic.getUuid());
            if(mGattCallback != null) {
                mGattCallback.onCharacteristicChanged(gatt, characteristic);
            }
            if(mBleConnectionCallBack != null) {
                mBleConnectionCallBack.onCharacteristicChanged(characteristic,
                        GATT_STATUS.GATT_SUCCESS);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                     int status) {
            if(mGattCallback != null) {
                mGattCallback.onDescriptorRead(gatt, descriptor, status);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            if(mGattCallback != null) {
                mGattCallback.onDescriptorWrite(gatt, descriptor, status);
            }
            if(mBleConnectionCallBack != null) {
                mBleConnectionCallBack.onCharacteristicSubscribe(descriptor.getCharacteristic(),
                        statusToGattStatusEnum(status),
                        Arrays.equals(descriptor.getValue(),
                                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE));
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            if(mGattCallback != null) {
                mGattCallback.onReliableWriteCompleted(gatt, status);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if(mGattCallback != null) {
                mGattCallback.onReadRemoteRssi(gatt, rssi, status);
            }
        }

        @Override
        public void onBondStateChanged(DeviceBleConnection.BOND_STATE bondState) {
            if(mBleConnectionCallBack != null) {
                mBleConnectionCallBack.onBondStateChanged(bondState);

            }
        }
    }

    private BleIasConnection(Context context) {
        mLocalGattCallback = new LocalGattCallback();
        mDeviceBleConnection = new DeviceBleConnection(context, mLocalGattCallback);
        ispp = new Ispp();
    }

    public void setCallback(IBleConnectionCallBack bleConnectionCallBack) {
        mBleConnectionCallBack = bleConnectionCallBack;
    }

    public void setCallback(BluetoothGattCallback bleConnectionCallBack) {
        mGattCallback = bleConnectionCallBack;
    }

    @Override
    public boolean connect(String address, boolean autoConnect) {
        return mDeviceBleConnection.connect(address, autoConnect);
    }

    @Override
    public boolean disconnect() {
        return mDeviceBleConnection.disconnect();
    }

    @Override
    public boolean pair(String address) {
        return mDeviceBleConnection.pair(address);
    }

    @Override
    public boolean unpair(String address) {
       return  mDeviceBleConnection.unpair(address);
    }

    @Override
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value) {
        return mDeviceBleConnection.writeCharacteristic(service, characteristic, value);
    }

    @Override
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value,
                                       boolean noResponse) {
        return mDeviceBleConnection.writeCharacteristic(service, characteristic, value, noResponse);
    }

    @Override
    public boolean readCharacteristic(UUID service, UUID characteristic) {
        return mDeviceBleConnection.readCharacteristic(service, characteristic);
    }

    @Override
    public boolean discoverServices() {
        return mDeviceBleConnection.discoverServices();
    }

    @Override
    public boolean subscribeToCharacteristic(UUID service, UUID characteristic, boolean enabled) {
        return mDeviceBleConnection.subscribeToCharacteristic(service, characteristic, enabled);
    }

    @Override
    public void setBleCallbackForIspp(IBleCallback bleCallback) {
        //Not needed at this level
    }

    public boolean isBluetoothAvailable() {
        return mDeviceBleConnection.isBluetoothAvailable();
    }
    public  boolean isBluetoothEnabled() {
        return mDeviceBleConnection.isBluetoothEnabled();
    }

    public List<BluetoothGattService> getServices() {
        return mDeviceBleConnection.getServices();
    }

    public boolean startIsppConnection(IsppRxCallBack callBack) {
        if(ispp.isConnectionEstablished()) {
            LogUtils.LOGI(TAG, "Ispp connection already established");
            return false;
        }
        ispp.startConnection(callBack, mDeviceBleConnection);
        return true;
    }

    public Ispp getIspp() {
        return ispp;
    }

    public boolean isConnected() {
        return mDeviceBleConnection.isConnected();
    }

    public boolean isConnectedToDevice(String address) {
       return  mDeviceBleConnection.isConnectedToDevice(address);
    }
    private GATT_STATUS statusToGattStatusEnum(int status) {
        switch(status) {
            case 0x0000:
                return GATT_STATUS.GATT_SUCCESS;
            case 0x0001:
                return GATT_STATUS.GATT_INVALID_HANDLE;
            case 0x0002:
                return GATT_STATUS.GATT_READ_NOT_PERMIT;
            case 0x0003:
                return GATT_STATUS.GATT_WRITE_NOT_PERMIT;
            case 0x0004:
                return GATT_STATUS.GATT_INVALID_PDU;
            case 0x0005:
                return GATT_STATUS.GATT_INSUF_AUTHENTICATION;
            case 0x0006:
                return GATT_STATUS.GATT_REQ_NOT_SUPPORTED;
            case 0x0007:
                return GATT_STATUS.GATT_INVALID_OFFSET;
            case 0x0008:
                return GATT_STATUS.GATT_INSUF_AUTHORIZATION;
            case 0x0009:
                return GATT_STATUS.GATT_PREPARE_Q_FULL;
            case 0x000a:
                return GATT_STATUS.GATT_NOT_FOUND;
            case 0x000b:
                return GATT_STATUS.GATT_NOT_LONG;
            case 0x000c:
                return GATT_STATUS.GATT_INSUF_KEY_SIZE;
            case 0x000d:
                return GATT_STATUS.GATT_INVALID_ATTR_LEN;
            case 0x000e:
                return GATT_STATUS.GATT_ERR_UNLIKELY;
            case 0x000f:
                return GATT_STATUS.GATT_INSUF_ENCRYPTION;
            case 0x0010:
                return GATT_STATUS.GATT_UNSUPPORT_GRP_TYPE;
            case 0x0011:
                return GATT_STATUS.GATT_INSUF_RESOURCE;
            case 0x0087:
                return GATT_STATUS.GATT_ILLEGAL_PARAMETER;
            case 0x0080:
                return GATT_STATUS.GATT_NO_RESOURCES;
            case 0x0081:
                return GATT_STATUS.GATT_INTERNAL_ERROR;
            case 0x0082:
                return GATT_STATUS.GATT_WRONG_STATE;
            case 0x0083:
                return GATT_STATUS.GATT_DB_FULL;
            case 0x0084:
                return GATT_STATUS.GATT_BUSY;
            case 0x0085:
                return GATT_STATUS.GATT_ERROR;
            case 0x0086:
                return GATT_STATUS.GATT_CMD_STARTED;
            case 0x0088:
                return GATT_STATUS.GATT_PENDING;
            case 0x0089:
                return GATT_STATUS.GATT_AUTH_FAIL;
            case 0x008a:
                return GATT_STATUS.GATT_MORE;
            case 0x008b:
                return GATT_STATUS.GATT_INVALID_CFG;
            case 0x008c:
                return GATT_STATUS.GATT_SERVICE_STARTED;
            case 0x008d:
                return GATT_STATUS.GATT_ENCRYPED_NO_MITM;
            case 0x008e:
                return GATT_STATUS.GATT_NOT_ENCRYPTED;
        }
        return GATT_STATUS.GATT_ERROR;
    }
}
