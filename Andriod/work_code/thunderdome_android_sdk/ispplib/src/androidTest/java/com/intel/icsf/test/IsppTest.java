package com.intel.icsf.test;

import android.app.Application;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.test.ApplicationTestCase;
import android.util.Log;

import com.intel.ispplib.connection.device.IBleConnection;
import com.intel.ispplib.ias.ispp.IBleCallback;
import com.intel.ispplib.ias.ispp.Ispp;
import com.intel.ispplib.ias.ispp.IsppRxCallBack;
import com.intel.ispplib.ias.ispp.IsppUtils;

import junit.framework.Assert;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.Vector;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class IsppTest extends ApplicationTestCase<Application> {
    private static final String TAG = "IaspTest";
    private TestDeviceBleTopicConnection testBleCommunication;
    private static final byte LINK_SETUP_RESPONSE = (byte)0x80;
    private static final byte LINK_SETUP_REQUEST = 0x00;
    private BluetoothGattCharacteristic controlChar;
    private BluetoothGattCharacteristic dataChar;
    private static final byte ACK = 0x01;
    private static final byte NACK = 0x02;
    private byte[] loopBackByteArray;/*MTU size is 133*/;
    private int counterSend;
    private int counterReceive;
    private  Ispp ispp;
    private   MyIsppRxCallback isppRxCallback;
    private final byte [] protocolInitPacket = {LINK_SETUP_REQUEST,0x01,0x00,
            (byte) 0xbe, 0x00, 0x04, 0x04/*number of nacks before disconnecting*/};
    private final byte[] linkSetupResponse = {LINK_SETUP_RESPONSE,0x01,0x00,
            (byte) 0x85, 0x00, 0x04, 0x04/*number of nacks before disconnecting*/};
    public IsppTest() {
        super(Application.class);
    }

    private static byte[] generateAckpacket(byte packetNumber) {
        byte [] ackPacket = {ACK,0x00,0x00};
        ackPacket[1] = packetNumber;
        return ackPacket;
    }

    private static byte[] generateNackPacket(byte packetNumber) {
        byte [] nackPacket = {NACK,0x00,0x00};
        nackPacket[1] = (byte) (packetNumber & 0x7f);
        return nackPacket;
    }

    private class MyIsppRxCallback implements IsppRxCallBack {

        @Override
        public void onIsppConnectionEstablished(int mtu) {

        }

        @Override
        public void onIsppConnectionLost() {

        }

        @Override
        public void onIsppPacketReceived(byte[] packet) {
            Assert.assertTrue(Arrays.equals(packet, loopBackByteArray));
        }
    }

    private class TestDeviceBleTopicConnection implements IBleConnection {
        private byte[] dataPacket;
        private byte[] controlPacket;
        private BluetoothGattDescriptor mlastDescriptor;
        private BluetoothGattCharacteristic mLastChar;
        IBleCallback mbleCallBack;
        private boolean dataChanged = false;
        private boolean controlChanged = false;
        private final Object lockData = new Object();
        private final Object lockControl = new Object();

        public TestDeviceBleTopicConnection(Context  context) {
        }

        public byte[] getLastControlPacket() {
            byte [] toReturn;
            synchronized (lockControl) {
                while(!controlChanged) {
                    try {
                        lockControl.wait();
                    } catch (InterruptedException e) {

                    }
                }
                toReturn = controlPacket;
                controlChanged = false;
            }
            //StringBuffer buf = new StringBuffer();
            //for(byte b: toReturn) buf.append(String.format("0x%02x ", b));
           // Log.i(TAG, "Control Packet: "+buf.toString());
            return toReturn;
        }
        public byte[] getLastDataPacket()  {
            byte [] toReturn;
            synchronized (lockData) {
                while(!dataChanged) {
                    try {
                        lockData.wait();
                    } catch (InterruptedException e) {

                    }
                }
                toReturn = dataPacket;
                dataChanged = false;
            }
           // StringBuffer buf = new StringBuffer();
            //for(byte b: toReturn) buf.append(String.format("0x%02x ", b));
           // Log.i(TAG, "data Packet: "+buf.toString());
            return toReturn;
        }

        public BluetoothGattDescriptor getLastDescriptor() {
            return mlastDescriptor;
        }

        @Override
        public void setBleCallbackForIspp(IBleCallback bleCallback) {
            mbleCallBack = bleCallback;
        }

        public IBleCallback getBleCallback() {
            return mbleCallBack;
        }

        @Override
        public boolean connect(String address, boolean autoConnect) {
            return true;
        }

        @Override
        public boolean disconnect() {
            return true;
        }

        @Override
        public boolean pair(String address) {
            return true;
        }

        @Override
        public boolean unpair(String address) {
            return true;
        }

        @Override
        public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value) {
            if(characteristic.equals(IsppUtils.ISSP_CONTROL_CHAR)) {
                Log.i(TAG, "Write control called ");
                synchronized (lockControl) {
                    controlPacket = value;
                    controlChanged = true;
                    lockControl.notify();
                }
            } else if(characteristic.equals(IsppUtils.ISSP_DATA_CHAR)) {
                synchronized (lockData) {
                    dataPacket = value;
                    dataChanged = true;
                    lockData.notify();
                }
            }
            return true;
        }

        @Override
        public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value,
                                           boolean noResponse) {
            return true;
        }

        @Override
        public boolean readCharacteristic(UUID service, UUID characteristic) {
            return true;
        }

        @Override
        public boolean discoverServices() {
            return true;
        }

        @Override
        public boolean subscribeToCharacteristic(UUID service, UUID characteristic, boolean enabled) {
            Log.i(TAG, "notify char called: " + characteristic);
            BluetoothGattCharacteristic c = new BluetoothGattCharacteristic(characteristic,0, 0);
            BluetoothGattDescriptor d = new BluetoothGattDescriptor(
                    IsppUtils.CLIENT_CHARACTERISTIC_CONFIG_UUID, 0);
            c.addDescriptor(d);
            mLastChar = c;
            mlastDescriptor = d;
            return true;
        }
    }

    @Override
    public void setUp() throws Exception {
        Log.i(TAG, "Calling Setup!!");
        isppRxCallback = new MyIsppRxCallback();
        ispp = new Ispp();
        testBleCommunication = new TestDeviceBleTopicConnection(null);
        dataChar = new BluetoothGattCharacteristic(IsppUtils.ISSP_DATA_CHAR,0, 0);
        controlChar = new BluetoothGattCharacteristic(IsppUtils.ISSP_CONTROL_CHAR,0, 0);
    }

    private void sleep() {
        try{
            Thread.sleep(300);
        } catch(InterruptedException e) {

        }
    }
    private void sleepForLong() {
        try{
            Thread.sleep(3000);
        } catch(InterruptedException e) {

        }
    }
    private void linkSetup() {
        Vector<String> vst = new Vector<String>();
        Log.i(TAG, "testIspp!");
        byte [] controlPacket;
        ispp.startConnection(new MyIsppRxCallback(), testBleCommunication);
        sleep();
        Assert.assertTrue(testBleCommunication.getLastDescriptor().getCharacteristic().getUuid().
                equals(IsppUtils.ISSP_CONTROL_CHAR));
        testBleCommunication.getBleCallback().onDescriptorWrite(null, testBleCommunication.
                getLastDescriptor(), 0);
        sleep();
        Assert.assertTrue(testBleCommunication.getLastDescriptor().getCharacteristic().getUuid().
                equals(IsppUtils.ISSP_DATA_CHAR));
        testBleCommunication.getBleCallback().onDescriptorWrite(null, testBleCommunication.
                getLastDescriptor(), 0);
        sleep();
        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, protocolInitPacket));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        sleep();
        controlChar.setValue(linkSetupResponse);
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
        sleep();
        counterSend = 0;
        counterReceive = 0;
    }

    private void loopBackGeneric(int loopBacksize) {
        loopBackByteArray = generateLoopBackMessage(loopBacksize + 3);
        byte [] dataPacket;
        byte [] controlPacket;
        Vector<byte[]> v = new Vector<byte []>();
        ispp.writeData(loopBackByteArray);
        dataPacket = testBleCommunication.getLastDataPacket();
        while((dataPacket[0]&0x80)!= 0x80) {
            Log.i(TAG, "Considering packet : "+packetValue(dataPacket));
            v.add(dataPacket);
            dataChar.setValue(dataPacket);//0
            testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
            counterReceive++;
            Log.i(TAG, "Counter receive is: "+ counterReceive);
            if((counterReceive % 4) == 0) {
                Log.i(TAG, "Sending ack packet for packet:  "+ String.format("0x%02x",
                        dataPacket[0]));
                controlChar.setValue(generateAckpacket((byte)dataPacket[0]));
                testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
            }
            dataPacket = testBleCommunication.getLastDataPacket();
        }
        v.add(dataPacket);
        counterReceive++;
        Log.i(TAG, "Sending ack packet for packet, end of packet:  " + String.format("0x%02x",
                dataPacket[0]));
        controlChar.setValue(generateAckpacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        //Sending the loopback back!
        for(byte[] value: v) {
            dataChar.setValue(value);
            testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);
            counterSend++;
            if((counterSend % 4) == 0 || ((value[0] & 0x80) == 0x80)) {
                controlPacket = testBleCommunication.getLastControlPacket();
                Assert.assertTrue(Arrays.equals(controlPacket, generateAckpacket((byte) value[0])));
                controlChar.setValue(controlPacket);
                testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
            }
        }
    }

    public void testManyLoopBacks() {
        linkSetup();
        for(int i=1; i < 131; i++) {
            loopBackGeneric(i);
            sleep();
        }
    }

    public void testNackSentByLib() {
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] controlPacket;
        Vector<byte[]> v = new Vector<byte[]>();
        v.add(new byte[] {0x0, 0x1f, (byte)0x82, 0x0, 0x0 ,0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                0x9, 0xa ,0xb ,0xc, 0xd, 0xe, 0xf });
        v.add(new byte[] { 0x01, 0x10, 0x11, 0x12, 0x13, 0x14 ,0x15, 0x16, 0x17, 0x18, 0x19, 0x1a,
                0x1b ,0x1c ,0x1d ,0x1e, 0x1f ,0x20, 0x21, 0x22});
        v.add(new byte[] { 0x02, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a ,0x2b ,0x2c, 0x2d,
                0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35});
        v.add(new byte[] {0x3, 0x36 ,0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40,
                0x41, 0x42 ,0x43, 0x44, 0x45, 0x46, 0x47, 0x48});
        v.add(new byte[] {0x4, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x53,
                0x54 ,0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5b});
        v.add(new byte[]{0x5, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66,
                0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e});
        v.add(new byte[]{(byte) 0x86, 0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
                0x79, 0x7a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f, (byte) 0x80, (byte) 0x81});
        v.add(new byte[]{0x7, 0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
                0x7a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f, (byte) 0x80, (byte) 0x81});

        linkSetup();
        dataChar.setValue(v.get(0));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);

        dataChar.setValue(v.get(2));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);
        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, generateNackPacket((byte) v.get(1)[0])));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        dataChar.setValue(v.get(1));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);


        dataChar.setValue(v.get(3));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);
        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, generateNackPacket((byte) v.get(2)[0])));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        dataChar.setValue(v.get(2));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);


        dataChar.setValue(v.get(4));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);
        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, generateNackPacket((byte) v.get(3)[0])));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        dataChar.setValue(v.get(3));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);

        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, generateAckpacket((byte) v.get(3)[0])));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        dataChar.setValue(v.get(4));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);
        dataChar.setValue(v.get(5));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);

        dataChar.setValue(v.get(7));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);
        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, generateNackPacket((byte) v.get(6)[0])));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        dataChar.setValue(v.get(6));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, dataChar);

        controlPacket = testBleCommunication.getLastControlPacket();
        Assert.assertTrue(Arrays.equals(controlPacket, generateAckpacket((byte) v.get(6)[0])));
        controlChar.setValue(controlPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, controlChar, 0);
        sleep();
        }

    private String packetValue(byte[] packet) {
        StringBuffer buf = new StringBuffer();
        for(byte b: packet) buf.append(String.format("0x%02x ", b));
         return buf.toString();
    }

    public void testTooMuchNacksSentToLib() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] bufferDataPacket;
        byte [] controlPacket;

        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: "+ packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: "+ packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: "+ packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: "+ packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
       //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
    }

    public void testNackonPackets123SentToLib() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] bufferDataPacket;
        byte [] controlPacket;
        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
    }

    public void testTimeOutAndFail() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] dataPacket1;
        byte [] dataPacket3;
        byte [] dataPacket4;
        byte [] dataPacket6;
        byte [] bufferDataPacket;
        byte [] controlPacket;

        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket1 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket3 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//should be 0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket1));
        controlChar.setValue(generateNackPacket((byte) 0x3));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//should be 3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket3));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        controlChar.setValue(generateAckpacket(dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket4 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//5
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//6
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket6 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//should be 4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket4));
        controlChar.setValue(generateNackPacket((byte) 0x6));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//should be 6
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket6));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        sleepForLong();
        //Should timeout a second time
    }

    public void testTimeOutandSuccess() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] dataPacket1;
        byte [] dataPacket3;
        byte [] dataPacket4;
        byte [] dataPacket6;
        byte [] bufferDataPacket;
        byte [] controlPacket;
        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket1 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket3 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//should be 0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket1));
        controlChar.setValue(generateNackPacket((byte) 0x3));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//should be 3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket3));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        controlChar.setValue(generateAckpacket(dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket4 = dataPacket;


        dataPacket = testBleCommunication.getLastDataPacket();//5
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//6
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket6 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//should be 4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket4));
        controlChar.setValue(generateNackPacket((byte) 0x6));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//should be 6
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket6));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        controlChar.setValue(generateAckpacket(dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
    }

    public void testMissedAck() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] dataPacket1;
        byte [] dataPacket3;
        byte [] dataPacket4;
        byte [] dataPacket6;
        byte [] bufferDataPacket;
        byte [] controlPacket;
        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket1 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        dataPacket3 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//should be 0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket1));
        controlChar.setValue(generateNackPacket((byte) 0x4));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        Assert.assertTrue((dataPacket[0] & 0x7f) == 0x4);
        dataPacket4 = dataPacket;

        dataPacket = testBleCommunication.getLastDataPacket();//5
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//6
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//should be 4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(dataPacket, dataPacket4));
        controlChar.setValue(generateNackPacket((byte) 0x7));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);
    }

    public void testNackonLastPacketSentToLib() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] bufferDataPacket;
        byte [] controlPacket;
        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack 1 for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        bufferDataPacket = dataPacket;
        //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack 2 for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        bufferDataPacket = dataPacket;
        //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack  3 for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);



        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
    }

    public void testNackonPacket1and3SentToLib() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] bufferDataPacket;
        byte [] controlPacket;
        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        bufferDataPacket = dataPacket;
        testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack 1 for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);


        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): " + packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
        bufferDataPacket = dataPacket;
        //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack 2 for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

    }

    public void testZNackonLastIsppPacketSentToLib() {
        linkSetup();
        loopBackByteArray = generateLoopBackMessage(133);
        byte [] dataPacket;
        byte [] bufferDataPacket;
        byte [] controlPacket;
        ispp.writeData(loopBackByteArray);

        dataPacket = testBleCommunication.getLastDataPacket();//0
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//1
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//2
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        dataPacket = testBleCommunication.getLastDataPacket();//3
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);


        controlChar.setValue(generateAckpacket((byte) dataPacket[0]));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//4
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//5
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        dataPacket = testBleCommunication.getLastDataPacket();//6, the last one
        Log.i(TAG, "Received packet: " + packetValue(dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);

        bufferDataPacket = dataPacket;
        //blocks otherwise: testBleCommunication.getLastDataPacket();
        Log.i(TAG, "generating nack 2 for packet: " + dataPacket[0]);
        controlChar.setValue(generateNackPacket((byte) (dataPacket[0]&0x7f)));
        testBleCommunication.getBleCallback().onCharacteristicChanged(null, controlChar);

        dataPacket = testBleCommunication.getLastDataPacket();//Retransmitted
        Log.i(TAG, "Received packet (R): "+ packetValue(dataPacket));
        Assert.assertTrue(Arrays.equals(bufferDataPacket, dataPacket));
        dataChar.setValue(dataPacket);
        testBleCommunication.getBleCallback().onCharacteristicWrite(null, dataChar, 0);
    }

    private byte[] generateLoopBackMessage(int isppMtu) {
        byte [] loopBackByteArray = new byte[isppMtu - 3];
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

}