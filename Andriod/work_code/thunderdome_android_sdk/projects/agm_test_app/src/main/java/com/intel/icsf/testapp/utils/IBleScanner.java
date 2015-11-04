package com.intel.icsf.testapp.utils;

public interface IBleScanner {
    /**
     * Start the ble scan
     */
    public void startLeScan();

    /**
     * Stop the ble scan
     */
    public void stopLeScan();
}
