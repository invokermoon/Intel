package com.intel.icsf.testapp;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.agm_test_app.R;
import com.intel.ispplib.IcsfConstants;
import com.intel.ispplib.connection.device.DeviceBleConnection;
import com.intel.ispplib.connection.device.service.BleIasService;
import com.intel.icsf.testapp.utils.BleScanner;
import com.intel.ispplib.connection.device.IBleConnection;
import com.intel.ispplib.ias.ispp.IsppUtils;
import com.intel.icsf.testapp.utils.LogUtils;
import com.intel.ispplib.utils.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Vector;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class DeviceDetailsActivity extends Activity {
    private static final String TAG = "DeviceDetailsActivity";
    public static final String DEVICE_EXTRA = "DEVICE_EXTRA";
    private static long CONNECTION_DELAY = 12000;
    private static long PAIRING_DELAY = 12000;
    private List<UUID> mServices;
    private Handler mHandler;
    private Menu mOptionsMenu;
    private IntentFilter mIasIntentFilter;
    private boolean isServiceBound;

    @Bind(R.id.device_name_text)
    TextView deviceName;

    @Bind(R.id.device_address_text)
    TextView deviceAddress;

    @Bind(R.id.serial_number_text)
    TextView deviceSerialNumber;

    @Bind(R.id.battery_level_text)
    TextView batteryLevel;

    @Bind(R.id.manufacturer_name_text)
    TextView manufacturer;

    @Bind(R.id.model_number_text)
    TextView modelNumber;

    @Bind(R.id.firmware_revision_text)
    TextView firmwareRevision;

    @Bind(R.id.software_revision_text)
    TextView softwareRevision;

    @Bind(R.id.hardware_revision_text)
    TextView hardwareRevision;

    @Bind(R.id.connect)
    Button connectButton;

    @Bind(R.id.pair)
    Button pairButton;

    @Bind(R.id.update_battery_level)
    Button updateBatteryLevel;

    private BluetoothDevice localDevice;
    private byte[] mLoopBackByteArray;

    private BleIasService mBleIasService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleIasService = ((BleIasService.LocalBinder) service).getService();
            if (!mBleIasService.initialize()) {
                LogUtils.LOGE(TAG, "Unable to initialize BleIasService");
                finish();
            }

            if (mBleIasService.isIsppConnected()) {
                fillServicesList(mBleIasService.getServices());
            }
            if (mBleIasService.isConnectedToDevice(localDevice.getAddress())) {
                readDeviceInformation();
                reaBatteryInformation();
            }
            LogUtils.LOGI(TAG, "Bound to service");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            LogUtils.LOGI(TAG, "On service disconnected");
            mBleIasService = null;
        }
    };

    private Runnable mConnectionRunnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.LOGI(TAG, "Connection timeout reached");
            updateDisconnectedState();
            displayMessage(R.string.connection_timeout);
        }
    };

    private Runnable mPairRunnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.LOGI(TAG, "Pairing timeout reached");
            updateUnpairedState();
            displayMessage(R.string.pairing_timeout);
        }
    };


    private final BroadcastReceiver mbleIasReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (intent.getAction().equals(BleIasService
                        .ACTION_STATUS_CHANGED)) {
                    IBleConnection.CONNECTION_STATUS status =
                            (IBleConnection.CONNECTION_STATUS) intent.
                                    getSerializableExtra(BleIasService.EXTRA_CONNECTION_STATUS);
                    IBleConnection.GATT_STATUS gattStatus =
                            (IBleConnection.GATT_STATUS) intent.
                                    getSerializableExtra(BleIasService.EXTRA_STATUS);
                    switch (status) {
                        case CONNECTING:
                            LogUtils.LOGI(TAG, "Connecting");
                            break;
                        case CONNECTED:
                            LogUtils.LOGI(TAG, "Connected");
                            if (gattStatus == IBleConnection.GATT_STATUS.GATT_SUCCESS) {
                                LogUtils.LOGI(TAG, "Successfully connected, trying to discover"
                                        + "services");
                                mHandler.removeCallbacks(mConnectionRunnable);
                                mBleIasService.discoverServices();
                            } else {
                                LogUtils.LOGI(TAG, "Connected, but there is an error:  " +
                                        gattStatus);
                            }
                            break;
                        case DISCONNECTING:
                            LogUtils.LOGI(TAG, "Disconnecting");
                            break;
                        case DISCONNECTED:
                            LogUtils.LOGI(TAG, "Disconnected");
                            if (gattStatus != IBleConnection.GATT_STATUS.GATT_SUCCESS) {
                                LogUtils.LOGI(TAG, "DisConnected, but there is an error: " +
                                        gattStatus);
                            }
                            updateDisconnectedState();
                            break;
                    }
                } else if (intent.getAction().equals(BleIasService
                        .ACTION_ON_CHARACTERISTIC_READ)) {
                    IBleConnection.GATT_STATUS status =
                            (IBleConnection.GATT_STATUS) intent.
                                    getSerializableExtra(BleIasService.EXTRA_STATUS);
                    UUID characteristicUuid =
                            (UUID) intent.
                                    getSerializableExtra(BleIasService.EXTRA_CHAR_UUID);
                    byte[] value =
                            intent.getByteArrayExtra(BleIasService.EXTRA_CHAR_VALUE);

                    if (status == IBleConnection.GATT_STATUS.GATT_SUCCESS) {
                        parseCharacteristicValue(characteristicUuid, value);
                    }

                } else if (intent.getAction().equals(BleIasService
                        .ACTION_ON_CHARACTERISTIC_CHANGED)) {

                } else if (intent.getAction().equals(BleIasService
                        .ACTION_ON_CHARACTERISTIC_WRITE)) {

                } else if (intent.getAction().equals(BleIasService
                        .ACTION_ON_CHARACTERISTIC_SUBSCRIBE)) {

                } else if (intent.getAction().equals(BleIasService
                        .ACTION_ON_SERVICES_DISCOVERED)) {
                    IBleConnection.GATT_STATUS status =
                            (IBleConnection.GATT_STATUS) intent.
                                    getSerializableExtra(BleIasService.EXTRA_STATUS);
                    if (status == IBleConnection.GATT_STATUS.GATT_SUCCESS) {
                        List<UUID> services =
                                (List<UUID>) intent.
                                        getSerializableExtra(BleIasService.EXTRA_SERVICES);
                        fillServicesList(services);
                        readDeviceInformation();
                        reaBatteryInformation();
                        LogUtils.LOGI(TAG, "Starting ispp connection");
                        mBleIasService.startIsppConnection();
                    } else {
                        LogUtils.LOGI(TAG, "The status is : " + status);
                    }
                } else if (intent.getAction().equals(BleIasService
                        .ACTION_ON_BOND_STATE_CHANGED)) {
                    DeviceBleConnection.BOND_STATE bondState =
                            (DeviceBleConnection.BOND_STATE) intent.
                                    getSerializableExtra(BleIasService.EXTRA_BOND_STATUS);
                    LogUtils.LOGI(TAG, "The new bond state is: " + bondState);
                    if (bondState == DeviceBleConnection.BOND_STATE.PAIRED) {
                        updatePairedState();
                        mHandler.removeCallbacks(mPairRunnable);
                    } else if (bondState == DeviceBleConnection.BOND_STATE.UNPAIRED) {
                        updateUnpairedState();
                    }
                } else if (intent.getAction().equals(BleIasService.ACTION_ISPP_PACKET_RECEIVED)) {
                    byte[] packet = intent.getByteArrayExtra(BleIasService.EXTRA_PACKET);
                    String receivedMessage = new String(packet,StandardCharsets.US_ASCII);
                    setMenuEnabled(true);
                    displayMessage("Received message" + receivedMessage);
                } else if (intent.getAction().equals(BleIasService.ACTION_ISPP_CONNECTION_ESTABLISHED)) {
                    LogUtils.LOGI(TAG, "Ispp connection established");
                    int mtu = intent.getIntExtra(BleIasService.EXTRA_MTU, -1);
                    if(mtu > 0) {
                        LogUtils.LOGI(TAG, "Ispp connection established with mtu: " + mtu);
                        mOptionsMenu.getItem(0).setEnabled(true);
                    }
                }
            }
        }

    };

        @Override
        protected void onResume() {
            super.onResume();
            LogUtils.LOGI(TAG, "In OnResume");

            Intent bleIasServiceIntent = new Intent(this, BleIasService.class);
            LogUtils.LOGI(TAG, "Starting service");
            startService(bleIasServiceIntent);
            LogUtils.LOGI(TAG, "Binding to service");
            isServiceBound = bindService(bleIasServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            registerReceiver(mbleIasReceiver, mIasIntentFilter);

            updateBatteryLevel.setEnabled(false);
            mServices = new Vector<>();
            if (BleScanner.getInstance(mBleIasService, null).
                    getConnectedDevices().contains(localDevice)) {
                connectButton.setText(getString(R.string.disconnect));
            }
            if (localDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                pairButton.setText(getString(R.string.unpair));
            }

            if (mBleIasService != null && mBleIasService.isIsppConnected()
                    && mBleIasService.isConnectedToDevice(localDevice.getAddress())) {
                setMenuEnabled(true);
            } else {
                setMenuEnabled(false);
            }
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            LogUtils.LOGI(TAG, "Creating menu");
            getMenuInflater().inflate(R.menu.menu_sendispp_loopback, menu);
            mOptionsMenu = menu;
            if (mBleIasService != null && mBleIasService.isIsppConnected()
                    && mBleIasService.isConnectedToDevice(localDevice.getAddress())) {
                mOptionsMenu.getItem(0).setEnabled(true);
            } else {
                mOptionsMenu.getItem(0).setEnabled(false);
            }
            return super.onCreateOptionsMenu(menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            LogUtils.LOGI(TAG, "Ispp loopback clicked");
            switch (item.getItemId()) {
                case R.id.action_sendloopback:
                    if (item.isEnabled() &&
                            mBleIasService.isIsppConnected()) {
                        item.setEnabled(false);
                        mBleIasService.isppWrite(generateLoopBackPacket());
                    }
                    break;
                default:
                    LogUtils.LOGW(TAG, "Menu ID is unknown: " + item.getItemId());
            }
            return true;
        }

        @Override
        protected void onPause() {
            super.onPause();
            if (isServiceBound) {
                unbindService(mServiceConnection);
                isServiceBound = false;
            }
            unregisterReceiver(mbleIasReceiver);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            LogUtils.LOGI(TAG, "In onCreate");
            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
            setContentView(R.layout.device_details);
            ButterKnife.bind(this);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            Intent intent = getIntent();
            if (intent != null) {
                BluetoothDevice device = intent.getParcelableExtra(DEVICE_EXTRA);
                if (device != null) {
                    localDevice = device;
                    deviceName.setText(device.getName() == null ?
                            getString(R.string.unknown_device) : device.getName());
                    deviceAddress.setText(device.getAddress());
                }
            }
            mHandler = new Handler();
            mIasIntentFilter = new IntentFilter();
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_STATUS_CHANGED);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ON_CHARACTERISTIC_CHANGED);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ON_CHARACTERISTIC_READ);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ON_CHARACTERISTIC_SUBSCRIBE);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ON_CHARACTERISTIC_WRITE);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ISPP_CONNECTION_ESTABLISHED);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ISPP_CONNECTION_LOST);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ISPP_PACKET_RECEIVED);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ON_BOND_STATE_CHANGED);
            mIasIntentFilter.addAction(BleIasService
                    .ACTION_ON_SERVICES_DISCOVERED);
        }

        @OnClick(R.id.update_battery_level)
        public void onUpdateBatteryLevelPressed() {
            setProgressBarIndeterminateVisibility(true);
            readBatteryLevel();
        }

        @OnClick(R.id.connect)
        public void onConnectPressed() {
            if (mBleIasService != null) {
                if (localDevice != null) {
                    if (connectButton.getText().equals(getString(R.string.connect))) {
                        mBleIasService.connect(localDevice.getAddress(), false);
                        connectButton.setEnabled(false);
                        connectButton.setText(getString(R.string.connecting_status));
                        pairButton.setEnabled(false);
                        updateBatteryLevel.setEnabled(false);
                    } else if (connectButton.getText().equals(getString(R.string.disconnect))) {
                        LogUtils.LOGI(TAG, "Disconnecting");
                        mBleIasService.disconnect();
                        connectButton.setEnabled(false);
                        connectButton.setText(getString(R.string.disconnecting_status));
                        pairButton.setEnabled(false);
                        updateBatteryLevel.setEnabled(false);
                    }
                    setProgressBarIndeterminateVisibility(true);
                } else {
                    Toast.makeText(this, R.string.no_configured_device,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        @OnClick(R.id.pair)
        public void onPairPressed() {
            if (pairButton.getText().equals(getString(R.string.pair))) {
                mBleIasService.pair(localDevice.getAddress());
                connectButton.setEnabled(false);
                pairButton.setEnabled(false);
                updateBatteryLevel.setEnabled(false);
                pairButton.setText(getString(R.string.paring_status));
                setProgressBarIndeterminateVisibility(true);
                mHandler.postDelayed(mPairRunnable, PAIRING_DELAY);
            } else if (pairButton.getText().equals(getString(R.string.unpair))) {
                mBleIasService.unpair(localDevice.getAddress());
                connectButton.setEnabled(false);
                pairButton.setEnabled(false);
                pairButton.setText(getString(R.string.unparing_status));
                setProgressBarIndeterminateVisibility(true);
            }
        }

        private void fillServicesList(List<UUID> services) {
            for (UUID service : services) {
                if (service.equals(IcsfConstants.BATTERY_SERVICE_UUID)) {
                    LogUtils.LOGI(TAG, "Battery service discovered");
                    mServices.add(IcsfConstants.BATTERY_SERVICE_UUID);
                } else if (service.equals(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                    LogUtils.LOGI(TAG, "DIS service discovered");
                    mServices.add(IcsfConstants.DEVICE_INFO_SERVICE_UUID);
                } else if (service.equals(IsppUtils.ISSP_SERVICE_UUID)) {
                    LogUtils.LOGI(TAG, "Ispp service discovered");
                }
            }
        }

        @Override
        protected void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            LogUtils.LOGI(TAG, "on save state called");
            outState.putParcelable(DEVICE_EXTRA, localDevice);
        }

        @Override
        protected void onRestart() {
            super.onRestart();
            LogUtils.LOGI(TAG, "onRestart called ");
        }

        @Override
        protected void onRestoreInstanceState(Bundle savedInstanceState) {
            super.onRestoreInstanceState(savedInstanceState);
            LogUtils.LOGI(TAG, "on restore state called");
            if (savedInstanceState.getParcelable(DEVICE_EXTRA) != null) {
                localDevice = savedInstanceState.getParcelable(DEVICE_EXTRA);
            }
        }

        private void updateConnectedState() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectButton.setEnabled(true);
                    connectButton.setText(getString(R.string.disconnect));
                    pairButton.setEnabled(true);
                    updateBatteryLevel.setEnabled(true);
                    setProgressBarIndeterminateVisibility(false);
                }
            });
        }

        private void updateDisconnectedState() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectButton.setEnabled(true);
                    connectButton.setText(getString(R.string.connect));
                    pairButton.setEnabled(true);
                    updateBatteryLevel.setEnabled(false);
                    setProgressBarIndeterminateVisibility(false);
                    setMenuEnabled(false);
                    manufacturer.setText("");
                    modelNumber.setText("");
                    deviceSerialNumber.setText("");
                    firmwareRevision.setText("");
                    softwareRevision.setText("");
                    hardwareRevision.setText("");
                }
            });
        }

        private void updatePairedState() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectButton.setEnabled(true);
                    pairButton.setText(getString(R.string.unpair));
                    pairButton.setEnabled(true);
                    setProgressBarIndeterminateVisibility(false);
                }
            });
        }

        private void updateUnpairedState() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectButton.setEnabled(true);
                    pairButton.setText(getString(R.string.pair));
                    pairButton.setEnabled(true);
                    setProgressBarIndeterminateVisibility(false);
                }
            });
        }

        private void parseCharacteristicValue(UUID characteristicUuid,
                                              final byte[] value) {
            if (characteristicUuid.equals(IcsfConstants.MANUFACTURER_NAME_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        manufacturer.setText(" " + new String(value));
                    }
                });

            } else if (characteristicUuid.equals(IcsfConstants.MODEL_NUMBER_STRING_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        modelNumber.setText(new String(value));
                    }
                });

            } else if (characteristicUuid.equals(IcsfConstants.SERIAL_NUMBER_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceSerialNumber.setText(new String(value));
                    }
                });

            } else if (characteristicUuid.equals(IcsfConstants.FIRMWARE_REV_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        firmwareRevision.setText(new String(value));
                    }
                });

            } else if (characteristicUuid.equals(IcsfConstants.SOFTWARE_REV_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        softwareRevision.setText(new String(value));
                    }
                });

            } else if (characteristicUuid.equals(IcsfConstants.HARDWARE_REV_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hardwareRevision.setText(new String(value));
                    }
                });

            } else if (characteristicUuid.equals(IcsfConstants.BATTERY_LEVEL_UUID)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        batteryLevel.setText(Byte.toString(value[0]));
                    }
                });
                updateConnectedState();
            }

        }

        private void readDeviceInformation() {
            setProgressBarIndeterminateVisibility(true);
            LogUtils.LOGI(TAG, "READING device information");

            LogUtils.LOGI(TAG, "READING manufacturer");
            readManufacturer();
            LogUtils.LOGI(TAG, "READING model number");
            readModelNumber();
            LogUtils.LOGI(TAG, "READING serial number");
            readSerialNumber();
            LogUtils.LOGI(TAG, "READING fw version");
            readFWRevision();
            LogUtils.LOGI(TAG, "READING sw version");
            readSWRevision();
            LogUtils.LOGI(TAG, "READING hw version");
            readHWRevision();
        }

        private void readHWRevision() {
            if (mServices.contains(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.DEVICE_INFO_SERVICE_UUID,
                        IcsfConstants.HARDWARE_REV_UUID);
            }
        }

        private void readSWRevision() {
            if (mServices.contains(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.DEVICE_INFO_SERVICE_UUID,
                        IcsfConstants.SOFTWARE_REV_UUID);
            }
        }

        private void readFWRevision() {
            if (mServices.contains(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.DEVICE_INFO_SERVICE_UUID,
                        IcsfConstants.FIRMWARE_REV_UUID);
            }
        }

        private void readSerialNumber() {
            if (mServices.contains(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.DEVICE_INFO_SERVICE_UUID,
                        IcsfConstants.SERIAL_NUMBER_UUID);
            }
        }

        private void readModelNumber() {
            if (mServices.contains(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.DEVICE_INFO_SERVICE_UUID,
                        IcsfConstants.MODEL_NUMBER_STRING_UUID);
            }
        }

        private void readManufacturer() {
            if (mServices.contains(IcsfConstants.DEVICE_INFO_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.DEVICE_INFO_SERVICE_UUID,
                        IcsfConstants.MANUFACTURER_NAME_UUID);
            }
        }

        private void readBatteryLevel() {
            LogUtils.LOGI(TAG, "READING battery level");
            if (mServices.contains(IcsfConstants.BATTERY_SERVICE_UUID)) {
                mBleIasService.readCharacteristic(IcsfConstants.BATTERY_SERVICE_UUID,
                        IcsfConstants.BATTERY_LEVEL_UUID);
            }
        }

        private void displayMessage(final int stringRes) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(),
                            stringRes, Toast.LENGTH_LONG).show();
                }
            });
        }

    private void displayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        message, Toast.LENGTH_LONG).show();
            }
        });
    }

        private void setMenuEnabled(final boolean enabled) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mOptionsMenu != null) {
                        mOptionsMenu.getItem(0).setEnabled(enabled);
                    }
                }
            });
        }

        @Override
        public void onBackPressed() {
            super.onBackPressed();
            if (isServiceBound) {
                LogUtils.LOGI(TAG, "Service bound, unbinding");
                unbindService(mServiceConnection);
                isServiceBound = false;
            }
            LogUtils.LOGI(TAG, "In onback pressed");
        }

        private byte[] generateLoopBackPacket() {
            if (mBleIasService != null && mBleIasService.isIsppConnected()
                    && mBleIasService.isConnectedToDevice(localDevice.getAddress())) {
                int isppMtu = mBleIasService.getIsppMtu();
                if (isppMtu > -1) {
                   return generateLoopBackMessage("Hello world!");
                }
            }
            return null;
        }

    private byte[] generateLoopBackMessage(String message) {
        return message.getBytes(StandardCharsets.US_ASCII);
    }

        private void reaBatteryInformation() {
            readBatteryLevel();
        }

}
