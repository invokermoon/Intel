package com.atalspeak.used.sdk.view;

/**
 * this activity starts the scan to detect other devices by BLE
 */


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.atalspeak.used.sdk.R;
import com.atalspeak.used.sdk.adapter.ListDevicesAdapter;
import com.atalspeak.used.sdk.models.ModelBleDevice;
import com.atalspeak.used.sdk.scanner.BleScanner;
import com.atalspeak.used.sdk.scanner.IBleScannerCallback;
import com.atalspeak.used.sdk.tools.Params;
import com.intel.ispplib.connection.device.service.BleIasService;

import java.util.List;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;


import static com.atalspeak.used.sdk.tools.LogUtils.makeLogTag;


public class HomeActivity extends AppCompatActivity {
    private static final String TAG = makeLogTag(HomeActivity.class);

    @Bind(R.id.lvDevices) ListView mDeviceList;
    @Bind(R.id.tvScanValue) TextView tvScanValue;
    @Bind(R.id.pbScan) ProgressBar pbScan;

    private Menu mMenu;

    private boolean mScanning = false;
    private boolean isServiceBound = false;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 20000;

    private BleScanner mBleScanner;
    private BleIasService mBleIasService;
    private Handler mHandler;

    private ListDevicesAdapter mListDevicesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity);
        ButterKnife.bind(this);

        /**
         * Check if this device is compatible with BLE
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mHandler = new Handler();

        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ModelBleDevice device = mListDevicesAdapter.getItem(position);
//                Toast.makeText(getApplicationContext(), device.getAddress(), Toast.LENGTH_LONG).show();
                if (device != null) {
                    Intent intent = new Intent(HomeActivity.this, DeviceActivity.class);

                    /**
                     * TODO
                     * used GSON
                     */
                    intent.putExtra(Params.EXTRA_DEVICE_NAME, device.getName());
                    intent.putExtra(Params.EXTRA_DEVICE_ADDRESS, device.getAddress());
                    startActivity(intent);
                }
            }
        });

        mListDevicesAdapter = new ListDevicesAdapter(getApplicationContext());
        mDeviceList.setAdapter(mListDevicesAdapter);
        pbScan.setVisibility(View.GONE);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_scan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan_refresh) {
            if (!mScanning){
                mListDevicesAdapter = new ListDevicesAdapter(getApplicationContext());
                mDeviceList.setAdapter(mListDevicesAdapter);
                scanLeDevice(true);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBleIasService == null) {
            Intent bleIasServiceIntent = new Intent(this, BleIasService.class);
            isServiceBound = bindService(bleIasServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        } else {
            initScannBle();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeScannerActivity();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeScannerActivity();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        removeScannerActivity();
    }


    private void removeScannerActivity(){
        if (isServiceBound) {
            unbindService(mServiceConnection);
            isServiceBound = false;
        }
        if (mScanning) {
            scanLeDevice(false);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /**
         * There is a problem with BLE
         */
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void addDevice(final BluetoothDevice device) {
        ModelBleDevice bleDevice = new ModelBleDevice(device.getName(), device.getAddress(), false, false);
        mListDevicesAdapter.addBleDevice(bleDevice);
    }

    private void addConnectedDevices() {
        List<BluetoothDevice> connectedDevices = mBleScanner.getConnectedDevices();
        if (connectedDevices != null && connectedDevices.size() > 0) {
            for (int i = 0; i < connectedDevices.size(); i++) {
                ModelBleDevice bleDevice = new ModelBleDevice(connectedDevices.get(i).getName(), connectedDevices.get(i).getAddress(), true, true);
                mListDevicesAdapter.addBleDevice(bleDevice);
            }
        }
    }

    private void addPairedDevices() {
        Set<BluetoothDevice> bondedDevices = mBleScanner.getBondedDevices();
        if (bondedDevices != null && bondedDevices.size() > 0) {
            for (BluetoothDevice device : bondedDevices) {
                ModelBleDevice bleDevice = new ModelBleDevice(device.getName(), device.getAddress(), false, true);
                mListDevicesAdapter.addBleDevice(bleDevice);
            }
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mBleScanner.startLeScan();
            mHandler.postDelayed(mScanRunnable, SCAN_PERIOD);

            mScanning = true;
            tvScanValue.setText(getString(R.string.bleiasService_scanning));
//            setProgressBarIndeterminateVisibility(true);
            pbScan.setVisibility(View.VISIBLE);
        } else {
            mScanRunnable.run();
            mHandler.removeCallbacks(mScanRunnable);
            pbScan.setVisibility(View.GONE);
        }
    }

    private Runnable mScanRunnable = new Runnable() {
        @Override
        public void run() {
            mBleScanner.stopLeScan();
            mScanning = false;
            tvScanValue.setText(getString(R.string.bleiasService_scan_finish));
            mMenu.getItem(0).setVisible(true);
            pbScan.setVisibility(View.GONE);
        }
    };



    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleIasService = ((BleIasService.LocalBinder) service).getService();

            mBleScanner = BleScanner.getInstance(mBleIasService, new IBleScannerCallback() {
                @Override
                public void onDeviceDiscovered(BluetoothDevice device) {
                    addDevice(device);
                }
            });

            /**
             * if there is no Bluetooth
             * we inform main activity
             */
            if (!mBleScanner.isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

            initScannBle();

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleIasService = null;
        }
    };

    private void initScannBle(){
        addConnectedDevices();
        addPairedDevices();

        if (!mScanning) {
            scanLeDevice(true);
        }

    }


}
