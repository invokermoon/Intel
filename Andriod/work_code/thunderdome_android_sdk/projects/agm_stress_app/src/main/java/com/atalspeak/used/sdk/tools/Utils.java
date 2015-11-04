package com.atalspeak.used.sdk.tools;

import android.widget.EditText;

import java.nio.ByteBuffer;


public class Utils {

    public static byte[] generateISPPMessage(int nbrData) {
        /**
         * this condition id done to avoid crash
         * when we create loopBackByteArray;
         *
         * TO BE CHECK if necessary
         */
        if (nbrData < 3){
            nbrData = 3;
        }
        byte [] loopBackByteArray = new byte[nbrData - 3];
        for (int i = 0; i < loopBackByteArray.length; i++) {
            loopBackByteArray[i] = (byte) i;
        }
        ByteBuffer buffer = ByteBuffer.allocate(2);
        short length = (short)loopBackByteArray.length;
        buffer.putShort(length);
        byte l0 = buffer.array()[1]; //little endian!
        byte l1 = buffer.array()[0];
        buffer = ByteBuffer.allocate(loopBackByteArray.length + 3);
        buffer.put((byte)0x1f); buffer.put(l0); buffer.put(l1);
        buffer.put(loopBackByteArray);
        return buffer.array();
    }


    public static boolean checkEditTextStress(EditText et){
        String str = et.getText().toString();
        int value = 0;
        if (str == null || str.length() == 0){
            return false;
        }
        try {
            value = Integer.parseInt(str);
        }
        catch(NumberFormatException nfe) {
            return false;
        }
        if (value == 0){
            return false;
        }

        return true;
    }

    public static int checkEditTextData(EditText et){
        String str = et.getText().toString();
        int value = 0;
        if (str == null || str.length() == 0){
            return 0;
        }
        try {
            value = Integer.parseInt(str);
        }
        catch(NumberFormatException nfe) {
            return 0;
        }
        return value;
    }



}
