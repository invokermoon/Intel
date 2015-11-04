package com.atalspeak.used.sdk.view;


/**
 * This activity allows to test the connection
 * with the device
 * The sequence to do is :
 *      - Connect
 *      - Get Service
 *      - Connect to ISPP
 *      - choice a test ( Ref or Border)
 *      - Set the number of test
 *      - set the number of data if need
 *
 * Check = OK  means that data send are the same than data received
 * Check = KO  means that data send are different than data received
 *
 * Test Ref : send exactly mtu number of byte
 * 
 * Border test : allows to send a number of bytes defined by user
 */


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.atalspeak.used.sdk.R;
import com.atalspeak.used.sdk.models.ModelBleDevice;
import com.atalspeak.used.sdk.tools.Params;
import com.atalspeak.used.sdk.tools.Utils;
import com.intel.ispplib.connection.device.IBleConnection;
import com.intel.ispplib.connection.device.service.BleIasService;
import com.intel.ispplib.ias.ispp.IsppUtils;
import com.intel.ispplib.utils.LogUtils;
import com.intel.ispplib.utils.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.atalspeak.used.sdk.tools.LogUtils.makeLogTag;

public class DeviceActivity extends AppCompatActivity {
    private static final String TAG = makeLogTag(DeviceActivity.class);

    private ModelBleDevice mBleDevice;
    private String mName = "";
    private String mAddress = "";
    private boolean mIsConnected = false;
//    private boolean mIsPaired = false;
    private boolean isServiceBound = false;
    private boolean isConnectISPP = false;

    private BleIasService mBleIasService;
    private byte[] mByteArrayRef;


    private IntentFilter mIasIntentFilter;

    @Bind(R.id.tvDeviceNameValue) TextView tvName;
    @Bind(R.id.tvDeviceAddressValue) TextView tvAddress;
    @Bind(R.id.tvDeviceConnectedValue) TextView tvConnected;
    @Bind(R.id.btDeviceConnect) Button btConnect;
    @Bind(R.id.tvDeviceErrorLabel) TextView tvErrorLabel;
    @Bind(R.id.tvDeviceErrorValue) TextView tvError;
    @Bind(R.id.tvDeviceStatusValue) TextView tvStatus;
    @Bind(R.id.btDeviceConnectISPP) Button btConnectISPP;
    @Bind(R.id.btDeviceGetServices) Button btGetServices;
    @Bind(R.id.tvDeviceSendToDeviceValue) TextView tvSendDataToDevice;
    @Bind(R.id.tvDeviceReceivedFromDeviceValue) TextView tvReceivedDataFromDevice;
    @Bind(R.id.tvDeviceCheckLabelValue) TextView tvCheckData;
    @Bind(R.id.btDeviceRefISPP) Button btRefISPP;
//    @Bind(R.id.btDeviceSeeDataISPP) Button btRefSeeISPP;
    @Bind(R.id.etRefStress) EditText etRefStress;
    @Bind(R.id.btDeviceBorderISPP) Button btBorder;
    @Bind(R.id.etBorderStress) EditText etBorderStress;
    @Bind(R.id.etBorderStressNbrData) EditText etBorderData;
    @Bind(R.id.tvDeviceSendBorderToDeviceValue) TextView tvSendBorderDataToDevice;
    @Bind(R.id.tvDeviceReceivedBorderFromDeviceValue) TextView tvReceivedBorderDataFromDevice;
    @Bind(R.id.tvDeviceCheckBorderValue) TextView tvCheckBorderData;

    /**
     * params used for stress tests
     */
    private int nbrSendData = 0;
    private int nbrReceivedData = 0;
    private int nbrOK = 0;
    private int nbrError = 0;
    private enum Sender {
        Ref,
        Border
    }
    private Sender mSender = Sender.Ref;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_activity);
        ButterKnife.bind(this);

        Intent intent = getIntent();
        mName = intent.getStringExtra(Params.EXTRA_DEVICE_NAME);
        mAddress = intent.getStringExtra(Params.EXTRA_DEVICE_ADDRESS);
        mBleDevice = new ModelBleDevice(mName, mAddress, mIsConnected, false);

        tvName.setText(mName);
        tvAddress.setText(mAddress);
        tvConnected.setText(getString(R.string.not_connected));
        btConnect.setText(getString(R.string.connect));

        /**
         * set filters used by the Broadcast Receiver
         */
        mIasIntentFilter = new IntentFilter();
        mIasIntentFilter.addAction(BleIasService.ACTION_STATUS_CHANGED);
        mIasIntentFilter.addAction(BleIasService.ACTION_ON_CHARACTERISTIC_CHANGED);
        mIasIntentFilter.addAction(BleIasService.ACTION_ON_CHARACTERISTIC_READ);
        mIasIntentFilter.addAction(BleIasService.ACTION_ON_CHARACTERISTIC_SUBSCRIBE);
        mIasIntentFilter.addAction(BleIasService.ACTION_ON_CHARACTERISTIC_WRITE);
        mIasIntentFilter.addAction(BleIasService.ACTION_ISPP_CONNECTION_ESTABLISHED);
        mIasIntentFilter.addAction(BleIasService.ACTION_ISPP_CONNECTION_LOST);
        mIasIntentFilter.addAction(BleIasService.ACTION_ISPP_PACKET_RECEIVED);
        mIasIntentFilter.addAction(BleIasService.ACTION_ON_BOND_STATE_CHANGED);
        mIasIntentFilter.addAction(BleIasService.ACTION_ON_SERVICES_DISCOVERED);

        /**
         * init info
         */
        tvStatus.setText("");
        removeError();

        /**
         * init button
         */
        btGetServices.setEnabled(false);
        btConnectISPP.setEnabled(false);
        btRefISPP.setEnabled(false);
        btBorder.setEnabled(false);

    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * Start BleIasService to chat with the device
         */
        Intent bleIasServiceIntent = new Intent(this, BleIasService.class);
        LogUtils.LOGI(TAG, "Starting service");
        startService(bleIasServiceIntent);
        LogUtils.LOGI(TAG, "Binding to service");
        isServiceBound = bindService(bleIasServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        if (isServiceBound){
            tvStatus.setText(getString(R.string.bleiasService_connected));
        }else{

        }
        registerReceiver(mbleIasReceiver, mIasIntentFilter);
    }

    @OnClick(R.id.btDeviceConnect)
    void btConnectDevice(){
        if (!mBleDevice.isConnected()){
            mBleIasService.connect(mBleDevice.getAddress(), false);
            tvStatus.setText(getString(R.string.bleiasService_device_connecting));

        }else{
            mBleIasService.disconnect();
            tvStatus.setText(getString(R.string.bleiasService_device_disconnecting));
        }
    }

    @OnClick(R.id.btDeviceGetServices)
    void btGetService(){
        LogUtils.LOGI(TAG, "btGetService");
        if (mBleDevice.isConnected() && mBleIasService != null){
            btGetServices.setEnabled(false);
            mBleIasService.discoverServices();
            tvStatus.setText(getString(R.string.bleiasService_device_getting_service));
        }else{
            setError(getString(R.string.bleiasService_error_get_service));
        }
    }

    @OnClick(R.id.btDeviceConnectISPP)
    void btConnectISPP(){
        if (!mBleDevice.isConnected()){
            Toast.makeText(getApplicationContext(), getString(R.string.bleiasService_device_ispp_error_connect), Toast.LENGTH_LONG).show();
            tvStatus.setText(getString(R.string.bleiasService_device_disconnected));
            setError(getString(R.string.bleiasService_device_ispp_error_connect));
            return;
        }
        if (mBleIasService != null){
            LogUtils.LOGI(TAG, "ISPP - Connect to ISPP");
            mBleIasService.startIsppConnection();
        }else{
            LogUtils.LOGI(TAG, "ISPP - mBleIasService null");
        }

    }

    @OnClick(R.id.btDeviceRefISPP)
    void btRefSendData(){
        nbrSendData = 0;
        nbrReceivedData = 0;
        nbrOK = 0;
        nbrError = 0;
        mSender = Sender.Ref;
        tvSendDataToDevice.setText("");
        tvReceivedDataFromDevice.setText("");
        tvCheckData.setText("");
        int nbrStress = 1;
        if (Utils.checkEditTextStress(etRefStress)){
            nbrStress = Integer.parseInt(etRefStress.getText().toString());
        }
        mByteArrayRef = Utils.generateISPPMessage(mBleDevice.getIsppMtu());
        if (nbrStress >= 1){
            for (int Cpt = 0; Cpt < nbrStress; Cpt++){
                mBleIasService.isppWrite(mByteArrayRef);
                nbrSendData = nbrSendData + mByteArrayRef.length;
            }
        }
        tvSendDataToDevice.setText(String.valueOf(mByteArrayRef.length) + "b");
        tvStatus.setText(getString(R.string.bleiasService_device_ispp_stress_nbr) + String.valueOf(nbrStress));
    }

    @OnClick(R.id.btDeviceBorderISPP)
    void btBorderSendData(){
        nbrSendData = 0;
        nbrReceivedData = 0;
        nbrOK = 0;
        nbrError = 0;
        tvSendBorderDataToDevice.setText("");
        tvReceivedBorderDataFromDevice.setText("");
        tvCheckBorderData.setText("");
        mSender = Sender.Border;
        int nbrStress = 1;
        if (Utils.checkEditTextStress(etBorderStress)){
            nbrStress = Integer.parseInt(etBorderStress.getText().toString());
        }

        int nbrData = Utils.checkEditTextData(etBorderData);
        mByteArrayRef = Utils.generateISPPMessage(nbrData);
        for (int Cpt = 0; Cpt < nbrStress; Cpt++){
            mBleIasService.isppWrite(mByteArrayRef);
            nbrSendData = nbrSendData + mByteArrayRef.length;
        }
        tvSendBorderDataToDevice.setText(String.valueOf(nbrSendData) + "b");
        tvStatus.setText(getString(R.string.bleiasService_device_ispp_stress_nbr) + String.valueOf(nbrStress));

    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBleIasService = ((BleIasService.LocalBinder) service).getService();
            if (!mBleIasService.initialize()) {
                Toast.makeText(getApplicationContext(), getString(R.string.bleiasService_error_connect), Toast.LENGTH_LONG).show();
                LogUtils.LOGE(TAG, "Unable to initialize BleIasService");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBleIasService = null;
            setError(getString(R.string.bleiasService_error_main_service_disconnect));
        }
    };



    private final BroadcastReceiver mbleIasReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            LogUtils.LOGD(TAG, "mbleIasReceiver onReceive");
            if (intent != null) {
                if (intent.getAction().equals(BleIasService.ACTION_STATUS_CHANGED)) {
                    LogUtils.LOGD(TAG, "mbleIasReceiver ACTION_STATUS_CHANGED");
                    IBleConnection.CONNECTION_STATUS status = (IBleConnection.CONNECTION_STATUS) intent.getSerializableExtra(BleIasService.EXTRA_CONNECTION_STATUS);
                    IBleConnection.GATT_STATUS gattStatus = (IBleConnection.GATT_STATUS) intent.getSerializableExtra(BleIasService.EXTRA_STATUS);
                    switch (status){
                        case CONNECTING:
                            LogUtils.LOGD(TAG, "Connecting");
                            tvStatus.setText(getString(R.string.bleiasService_device_connecting));
                            break;

                        case CONNECTED:
                            LogUtils.LOGD(TAG, "Connected");
                            tvStatus.setText(getString(R.string.bleiasService_device_connected));
                            tvConnected.setText(getString(R.string.bleiasService_device_connected));
                            btConnect.setText(getString(R.string.un_connect));
                            mBleDevice.isConnected(true);
                            btGetServices.setEnabled(true);
                            break;

                        case DISCONNECTING:
                            LogUtils.LOGD(TAG, "disconnecting");
                            break;

                        case DISCONNECTED:
                            LogUtils.LOGD(TAG, "disconnected");
                            tvStatus.setText(getString(R.string.bleiasService_device_disconnected));
                            tvConnected.setText(getString(R.string.bleiasService_device_disconnected));
                            btConnect.setText(getString(R.string.connect));
                            mBleDevice.isConnected(false);
                            btGetServices.setEnabled(false);
                            btConnectISPP.setEnabled(false);
                            btBorder.setEnabled(false);
                            btRefISPP.setEnabled(false);
                            break;
                    }

                }

                if (intent.getAction().equals(BleIasService.ACTION_ON_SERVICES_DISCOVERED)) {
                    LogUtils.LOGI(TAG, "ACTION_ON_SERVICES_DISCOVERED");
                    IBleConnection.GATT_STATUS gattStatus = (IBleConnection.GATT_STATUS) intent.getSerializableExtra(BleIasService.EXTRA_STATUS);
                    if (gattStatus == IBleConnection.GATT_STATUS.GATT_SUCCESS) {
                        List<UUID> services = (List<UUID>) intent.getSerializableExtra(BleIasService.EXTRA_SERVICES);
                        for (UUID service : services) {
                             if (service.equals(IsppUtils.ISSP_SERVICE_UUID)) {
                                LogUtils.LOGI(TAG, "Ispp service discovered");
                                tvStatus.setText(getString(R.string.bleiasService_device_ispp_service_found));
                                btConnectISPP.setEnabled(true);
                            }
                        }
                    } else {
                        LogUtils.LOGI(TAG, "The status is : " + gattStatus);
                        setError(getString(R.string.bleiasService_error_service_discovered));
                    }

                }
                if (intent.getAction().equals(BleIasService.ACTION_ISPP_CONNECTION_ESTABLISHED)) {
                    LogUtils.LOGI(TAG, "Ispp connection established");
                    int mtu = intent.getIntExtra(BleIasService.EXTRA_MTU, -1);
                    if(mtu > 0) {
                        LogUtils.LOGI(TAG, "Ispp connection established with mtu: " + mtu);
                        btConnectISPP.setEnabled(false);
                        btRefISPP.setEnabled(true);
                        btBorder.setEnabled(true);
                        btRefISPP.setText(getString(R.string.bleiasService_device_test_ispp) + " (mtu = " + String.valueOf(mtu) + ")");
                        tvStatus.setText(getString(R.string.bleiasService_device_ispp_connected) + String.valueOf(mtu));
                        mBleDevice.setIsppMtu(mtu);
                    }else{
                        LogUtils.LOGI(TAG, "mtu = 0");
                        setError(getString(R.string.bleiasService_error_mtu_null));
                    }
                }

                if (intent.getAction().equals(BleIasService.ACTION_ISPP_PACKET_RECEIVED)) {
                    byte[] loopBack = intent.getByteArrayExtra(BleIasService.EXTRA_PACKET);
                    LogUtils.LOGI(TAG, "Ispp packet received, comparing with what was sent");
                    LogUtils.LOGI(TAG, "received packet: " + StringUtils.toHexString(loopBack));
                    nbrReceivedData = nbrReceivedData + mByteArrayRef.length;
                    LogUtils.LOGI(TAG, "Length of the loopback array: " + mByteArrayRef.length );
                    LogUtils.LOGI(TAG, "Expected  packet: " + StringUtils.toHexString(mByteArrayRef));

                    if (Arrays.equals(loopBack, mByteArrayRef)){
                        LogUtils.LOGD(TAG, "ACTION_ISPP_PACKET_RECEIVED : bleiasService_device_ispp_check_same");
                        nbrOK = nbrOK + 1;
                    }else{
                        LogUtils.LOGD(TAG, "ACTION_ISPP_PACKET_RECEIVED : bleiasService_device_ispp_check_different");
                        nbrError = nbrError + 1;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("OK = ");
                    sb.append(String.valueOf(nbrOK));
                    sb.append(" - KO = ");
                    sb.append(String.valueOf(nbrError));

                    if (mSender == Sender.Ref){
                        tvReceivedDataFromDevice.setText(String.valueOf(nbrReceivedData) + " b");
                        tvCheckData.setText(sb.toString());
                    }
                    if (mSender == Sender.Border){
                        tvReceivedBorderDataFromDevice.setText(String.valueOf(nbrReceivedData) + " b");
                        tvCheckBorderData.setText(sb.toString());
                    }

                }

            }
        }

    };





    private void setError(String msg){
        tvErrorLabel.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private void removeError(){
        tvErrorLabel.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        tvError.setText("");
    }
}
