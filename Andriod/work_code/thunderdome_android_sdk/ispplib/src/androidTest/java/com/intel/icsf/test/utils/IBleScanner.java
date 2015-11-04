package com.intel.icsf.test.utils;

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
