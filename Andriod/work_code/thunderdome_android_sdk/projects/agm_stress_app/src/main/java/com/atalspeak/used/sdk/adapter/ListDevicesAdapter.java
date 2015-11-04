package com.atalspeak.used.sdk.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atalspeak.used.sdk.R;
import com.atalspeak.used.sdk.models.ModelBleDevice;
import com.atalspeak.used.sdk.scanner.BleScanner;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;


public class ListDevicesAdapter extends BaseAdapter{
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private BleScanner mBleScanner;

    private ArrayList<ModelBleDevice> mListBleDevices = new ArrayList<>();

    public ListDevicesAdapter(Context context){
        this.mContext = context;
        mLayoutInflater = LayoutInflater.from(this.mContext);
    }


    @Override
    public int getCount() {
        return mListBleDevices.size();
    }

    @Override
    public ModelBleDevice getItem(int position) {
        return mListBleDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ViewHolder viewHolder;
        if (view == null) {
            view = mLayoutInflater.inflate(R.layout.line_list_device, parent, false);
            viewHolder = new ViewHolder(view);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }


        ModelBleDevice bleDevice = mListBleDevices.get(position);

        /**
         * Set device name
         */
        final String deviceName = bleDevice.getName();
        if (deviceName != null && deviceName.length() > 0) {
            viewHolder.deviceName.setText(deviceName);
        } else {
            viewHolder.deviceName.setText(R.string.unknown_device);
        }


        /**
         * Set device address
         */
        final String deviceAddress = bleDevice.getAddress();
        if (deviceAddress != null && deviceAddress.length() > 0) {
            viewHolder.deviceAddress.setText(deviceAddress);
        } else {
            viewHolder.deviceAddress.setText(R.string.unknown_device_address);
        }

        return view;
    }


    static class ViewHolder {
        @Bind(R.id.device_name) TextView deviceName;
        @Bind(R.id.mac_address) TextView deviceAddress;

        public ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }
    }


    public void addBleDevice(final ModelBleDevice device) {
        for (int Cpt = 0; Cpt < mListBleDevices.size(); Cpt++){
            if (device.getAddress().equalsIgnoreCase(mListBleDevices.get(Cpt).getAddress())){
                mListBleDevices.get(Cpt).isConnected(device.isConnected());
                mListBleDevices.get(Cpt).isPaired(device.isPaired());
                notifyDataSetChanged();
                return;
            }
        }
        mListBleDevices.add(device);
        notifyDataSetChanged();
    }





}
