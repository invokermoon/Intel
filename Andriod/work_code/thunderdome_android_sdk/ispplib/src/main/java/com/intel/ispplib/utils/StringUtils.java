package com.intel.ispplib.utils;

public class StringUtils {
    public static String toHexString(byte[] value) {
        StringBuffer buf = new StringBuffer();
        buf.append("");
        for (byte b: value) {
            buf.append(String.format("%02X ", b));
        }
        return buf.toString();
    }
}
