/*
 * Copyright 2015 Intel Corporation
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

package com.atalspeak.used.sdk.scanner;

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



import static com.atalspeak.used.sdk.tools.LogUtils.makeLogTag;

public class BleScanner implements IBleScanner {

    private static final String TAG = makeLogTag(BleScanner.class);

    private static BleScanner sInstance = null;
    private BleScanCallback mBleScanCallback;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private IBleScannerCallback mScannerCallback;

    private class BleScanCallback implements BluetoothAdapter.LeScanCallback {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (mScannerCallback != null) {
                mScannerCallback.onDeviceDiscovered(device);
            }
        }
    }

    @NonNull
    private static final Object sLock = new Object();

    private Context mContext;

    private BleScanner(Context context) {
        this.mContext = context;
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBleScanCallback = new BleScanCallback();
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

        if (callback != null) {
            bleScanner.setBleScannerCallback(callback);
        }

        return bleScanner;
    }

    private void setBleScannerCallback(IBleScannerCallback callback) {
        mScannerCallback = callback;
    }

    @Override
    public void startLeScan() {
        mBluetoothAdapter.startLeScan(mBleScanCallback);
    }

    @Override
    public void stopLeScan() {
            mBluetoothAdapter.stopLeScan(mBleScanCallback);
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public List<BluetoothDevice> getConnectedDevices() {
        if (isBluetoothEnabled()) {
            return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        }
        return null;
    }

    public synchronized Set<BluetoothDevice> getBondedDevices() {
        if (isBluetoothEnabled()) {
            Set<BluetoothDevice> bondedDedvices = new HashSet<>();
            bondedDedvices.addAll(mBluetoothAdapter.getBondedDevices());

            Iterator<BluetoothDevice> it = bondedDedvices.iterator();
            while (it.hasNext()) {
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
