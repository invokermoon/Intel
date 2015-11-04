package com.intel.icsf.testapp;

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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.agm_test_app.R;
import com.intel.ispplib.connection.device.service.BleIasService;
import com.intel.icsf.testapp.utils.BleScanner;
import com.intel.icsf.testapp.utils.IBleScannerCallback;
import com.intel.icsf.testapp.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;

public class ScanActivity  extends Activity {
    private static final String TAG = "ScanActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;
    private BleScanner scanner;
    private Menu mOptionsMenu;

    @Bind(R.id.device_list)
    ListView mDeviceList;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private BleIasService mBleIasService;
    private boolean isServieBound;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleIasService = ((BleIasService.LocalBinder) service).getService();

            scanner = BleScanner.getInstance(mBleIasService, new IBleScannerCallback() {
                @Override
                public void onDeviceDiscovered(BluetoothDevice device) {
                    addDevice(device);
                }
            });
            if(!scanner.isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            // Initializes list view adapter.
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            mDeviceList.setAdapter(mLeDeviceListAdapter);
            addConnectedDevices();
            addPairedDevices();
            if(!mScanning) {
                scanLeDevice(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.LOGI(TAG, "In oncreate");
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_device_scan);
        ButterKnife.bind(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        mHandler = new Handler();
        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if(device != null) {
                    scanLeDevice(false);
                    Intent intent = new Intent(ScanActivity.this, DeviceDetailsActivity.class);
                    intent.putExtra(DeviceDetailsActivity.DEVICE_EXTRA, device);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            }
        });
    }

    private void addDevice(final BluetoothDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLeDeviceListAdapter.addDevice(device);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        });
    }

    private class LeDeviceListAdapter extends BaseAdapter {
        private  ArrayList<BluetoothDevice> mLeDevices;
        private  LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = ScanActivity.this.getLayoutInflater();
        }

        public void addAllDevices(List<BluetoothDevice> devices) {
            mLeDevices.addAll(devices);

        }
        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int position, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = mInflator.inflate(R.layout.list_item_device, viewGroup, false);
                viewHolder = new ViewHolder(view);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(position);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            viewHolder.deviceAddress.setText(device.getAddress());
            if(scanner.getConnectedDevices().contains(device)) {
                viewHolder.connected.setText(getString(R.string.connected));
            } else {
                viewHolder.connected.setText(getString(R.string.not_connected));
            }
            if(device.getBondState() == BluetoothDevice.BOND_BONDED) {
                viewHolder.paired.setText(getString(R.string.paired));
            } else {
                viewHolder.paired.setText(getString(R.string.not_paired));
            }
            return view;
        }
    }
    static class ViewHolder {

        @Bind(R.id.device_name)
        TextView deviceName;

        @Bind(R.id.mac_address)
        TextView deviceAddress;

        @Bind(R.id.connected)
        TextView connected;

        @Bind(R.id.paired)
        TextView paired;

        public ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private Runnable mScanRunnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.LOGI(TAG, "Stopping scan after timeout");
            setProgressBarIndeterminateVisibility(false);
           // mSwipeContainer.setRefreshing(false);
            scanner.stopLeScan();
            if(mOptionsMenu != null)
                mOptionsMenu.getItem(0).setTitle(getString(R.string.start_scan));
            mScanning = false;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.LOGI(TAG, "In onresume");
        if(mBleIasService == null) {
            Intent bleIasServiceIntent = new Intent(this, BleIasService.class);
            isServieBound = bindService(bleIasServiceIntent,
                    mServiceConnection, BIND_AUTO_CREATE);
        } else {
            // Initializes list view adapter.
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            mDeviceList.setAdapter(mLeDeviceListAdapter);
            addConnectedDevices();
            addPairedDevices();
            if(!mScanning) {
                scanLeDevice(true);
            }
        }
    }

    private void addConnectedDevices() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                List<BluetoothDevice> connectedDevices = scanner.getConnectedDevices();
                if (connectedDevices != null) {
                    if (connectedDevices.size() != 0) {
                        for (int i = 0; i < connectedDevices.size(); i++) {
                            mLeDeviceListAdapter.addDevice(connectedDevices.get(i));
                        }
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                }
            }
        });
    }

    private void addPairedDevices() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Set<BluetoothDevice> bondedDevices = scanner.getBondedDevices();
                if (bondedDevices != null) {
                    for (BluetoothDevice device : bondedDevices) {
                        mLeDeviceListAdapter.addDevice(device);
                    }
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scan, menu);
        mOptionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mScanning) {
            menu.getItem(0).setTitle(getString(R.string.stop_scan));
        } else {
            menu.getItem(0).setTitle(getString(R.string.start_scan));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        LogUtils.LOGI(TAG, "PAUL options items selected");
        switch (item.getItemId()) {
            case R.id.action_refresh:
                LogUtils.LOGI(TAG, "PAUL Button is pressed");
                if (item.getTitle().equals(getString(R.string.stop_scan))) {
                    scanLeDevice(false);
                } else if (item.getTitle().equals(getString(R.string.start_scan))) {
                    clearDeviceList();
                    addConnectedDevices();
                    addPairedDevices();
                    scanLeDevice(true);
                }
                break;
            default:
                LogUtils.LOGW(TAG, "Menu ID is unknown: " + item.getItemId());
        }
        return true;
    }

    private void clearDeviceList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLeDeviceListAdapter.clear();
                mLeDeviceListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            scanner.startLeScan();
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(mScanRunnable, SCAN_PERIOD);
            mScanning = true;
            if(mOptionsMenu != null)
                mOptionsMenu.getItem(0).
                        setTitle(getString(R.string.stop_scan));
            setProgressBarIndeterminateVisibility(true);
        } else {
            mScanRunnable.run();
            mHandler.removeCallbacks(mScanRunnable);
        }
    }

    private void notifyDataChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(isServieBound) {
            unbindService(mServiceConnection);
            isServieBound = false;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if(isServieBound) {
            unbindService(mServiceConnection);
            isServieBound = false;
        }
    }
}

