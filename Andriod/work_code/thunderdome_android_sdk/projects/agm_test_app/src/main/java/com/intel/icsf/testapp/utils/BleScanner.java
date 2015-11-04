package com.intel.icsf.testapp.utils;

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

public class BleScanner implements IBleScanner {

    private static BleScanner sInstance = null;
    private BluetoothAdapter bluetoothAdapter;
    private IBleScannerCallback scannerCallback;
    private BleScanCallback bleScanCallback;
    private  BluetoothManager bluetoothManager;
    private static final String TAG = "BleScanner";
    public BleScanner() {

    }
    private class BleScanCallback implements BluetoothAdapter.LeScanCallback {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (scannerCallback != null) {
                scannerCallback.onDeviceDiscovered(device);
            }
        }
    }

    @NonNull
    private static final Object sLock = new Object();
    private Context mContext;
    private BleScanner(Context context) {
        this.mContext = context;
       bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanCallback = new BleScanCallback();
    }

    public static BleScanner getInstance(Context context, IBleScannerCallback callback) {
        BleScanner bleScanner = sInstance;
        if (bleScanner == null) {
            synchronized (sLock) {
                if (bleScanner == null) {
                    sInstance = bleScanner = new BleScanner(context);
                }
            }
        }
        if(callback != null)
            bleScanner.setBleScannerCallback(callback);
        return bleScanner;
    }

    private void setBleScannerCallback(IBleScannerCallback callback) {
        scannerCallback = callback;
    }
    @Override
    public void startLeScan() {
            bluetoothAdapter.startLeScan(bleScanCallback);
    }

    @Override
    public void stopLeScan() {
            bluetoothAdapter.stopLeScan(bleScanCallback);
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
