package com.atalspeak.used.sdk.models;

import static com.atalspeak.used.sdk.tools.LogUtils.makeLogTag;



public class ModelBleDevice {
    private static final String TAG = makeLogTag(ModelBleDevice.class);

    private String mName = "";
    private String mAddress = "";
    private boolean mConnected = false;
    private boolean mPaired = false;
    private int mIsppMtu = 0;

    public ModelBleDevice(){

    }

    public ModelBleDevice(String name, String address, boolean connected, boolean paired){
        this.mName = name;
        this.mAddress = address;
        this.mConnected = connected;
        this.mPaired = paired;
    }

    public String getName(){
        return mName;
    }

    public String getAddress(){
        return mAddress;
    }

    public boolean isConnected(){
        return mConnected;
    }
    public void isConnected(boolean fg){
        this.mConnected = fg;
    }

    public boolean isPaired(){
        return mPaired;
    }
    public void isPaired(boolean fg){
        this.mPaired = fg;
    }


    public void setIsppMtu(int mtu){
        this.mIsppMtu = mtu;
    }
    public int getIsppMtu(){
        return mIsppMtu;
    }
}
