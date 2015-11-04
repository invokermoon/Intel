package com.intel.ispplib.connection.device.utils;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.support.annotation.NonNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DeviceUtils {

    private static DeviceUtils sInstance = null;
    @NonNull
    private static final Object sLock = new Object();
    private Context mContext;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private static final String TAG = "DeviceUtils";

    private DeviceUtils(Context context) {
        mContext = context;
        bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public static DeviceUtils getInstance(Context context) {
        DeviceUtils deviceUtils = sInstance;
        if (deviceUtils == null) {
            synchronized (sLock) {
                if (deviceUtils == null) {
                    sInstance = deviceUtils = new DeviceUtils(context);
                }
            }
        }
        return deviceUtils;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null &&
                bluetoothAdapter.isEnabled();
    }

    public List<BluetoothDevice> getConnectedDevices() {
        if(isBluetoothEnabled()) {
            Set<BluetoothDevice> bondedDedvices = bluetoothAdapter.getBondedDevices();
            return bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        }
        return null;
    }

    public boolean isDeviceConnected(String address) {
        for(BluetoothDevice device: getConnectedDevices()) {
            if(device.getAddress().equals(address)) return true;
        }
        return false;
    }

    public boolean isDeviceBonded(String address) {
        for(BluetoothDevice device: getBondedDevices()) {
            if(device.getAddress().equals(address)) return true;
        }
        return false;
    }

    public synchronized Set<BluetoothDevice> getBondedDevices() {
        if(isBluetoothEnabled()) {
            Set<BluetoothDevice> bondedDedvices = new HashSet<BluetoothDevice>();
            bondedDedvices.addAll(
                    bluetoothAdapter.getBondedDevices());

            Iterator<BluetoothDevice> it = bondedDedvices.iterator();
            while (it.hasNext())
            {
                BluetoothDevice device = it.next();
                if (device.getType() != BluetoothDevice.DEVICE_TYPE_LE) {
                    it.remove();
                }
            }
            return bondedDedvices;
        }
        return null;
    }
}
