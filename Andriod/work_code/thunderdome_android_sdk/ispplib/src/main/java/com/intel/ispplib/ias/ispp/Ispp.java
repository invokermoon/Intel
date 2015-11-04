/*
 * Copyright 2015 Intel Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.ispplib.ias.ispp;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import android.util.Log;

import com.intel.ispplib.connection.device.IBleConnection;
import com.intel.ispplib.utils.LogUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.nio.ByteBuffer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

public class Ispp {
    private static final String TAG = "Ispp";

    // Link setup request, ispp version 1, default protocol options, max mtu: 190
    // bytes little endian, 2 bytes, ack window: 4,
    private final byte[] protocolInitPacket = {LINK_SETUP_REQUEST, 0x01, 0x00, (byte) 0xbe,
            0x00, 0x04, 0x04/*number of nacks before disconnecting*/};

    private static final byte LINK_SETUP_REQUEST = 0x00;
    private static final byte LINK_SETUP_RESPONSE = (byte) 0x80;
    private static final byte ACK = 0x01;
    private static final byte NACK = 0x02;
    private static final int BLE_PACKET_DATA_SIZE = 19;
    private static final int MAX_PACKET_COUNTER = 128;
    private static final int MAX_ACK_TIMEOUTS = 2;
    private Handler mHandler;
    private int ackWindow;
    private volatile int ackWindowIndex;
    private int nacksBeforeConnectionEnd;
    private int maxMtuSize;
    private volatile int absolutePacketCounter; //% 128 packet number
    private volatile int nacksNumberCounter;
    private IsppUtils.ISPP_STATE CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
    private Timer ackTimer;
    private TimerTask ackTask;
    private final long ACK_WAITING_TIME = 2000;
    private ByteArrayOutputStream dataReadOutput;
    private int nextExpectedPacket;
    private int wrongPacketsCounter;
    private int mAckTimeouts;
    private IsppRxCallBack mCallBack;
    private IBleConnection mbleCommunication;
    private boolean mIsConnectionEnabled;

    LinkedBlockingQueue<byte[]> sendBuffer = new LinkedBlockingQueue<byte[]>();
    Vector<byte[]> ackWindowList;

    public int getMtu() {
        return maxMtuSize;
    }

    private static byte[] generateAckpacket(byte packetNumber, boolean ack) {
        byte[] ackPacket;
        if (ack)
            ackPacket = new byte[]{ACK, 0x00, 0x00};
        else ackPacket = new byte[]{NACK, 0x00, 0x00};
        ackPacket[1] = packetNumber;
        return ackPacket;
    }

    public Ispp() {
        initHandler(); //The handler to post messages
        mIsConnectionEnabled = false;
        CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
        maxMtuSize = -1;
    }

    public void startConnection(IsppRxCallBack callBack, IBleConnection bleCommunication) {
        Log.i(TAG, "Starting connection");
        mbleCommunication = bleCommunication;
        mCallBack = callBack;
        mbleCommunication.setBleCallbackForIspp(new BleCallback());
        if (!mHandler.sendMessage(
                mHandler.obtainMessage(IsspHandlerCallback.MESSAGE_INIT_ISPP))) {
            Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET connection message!!");
        }
    }

    private final class IsspHandlerCallback implements Handler.Callback {
        public static final int MESSAGE_INIT_CONNECTION = 1;
        public static final int ISPP_CONTROL_MESSAGE_FROM_DEVICE = 2;
        public static final int ISPP_DATA_MESSAGE_FROM_DEVICE = 3;
        public static final int MESSAGE_SEND_NEXT_DATA_PACKET = 4;
        public static final int MESSAGE_INIT_ISPP = 5;
        public static final int WAIT_FOR_ACK = 6;
        public static final int MESSAGE_RESEND = 7;

        @Override
        public boolean handleMessage(Message message) {
            byte[] value = (byte[]) message.obj;
            switch (message.what) {
                case MESSAGE_INIT_CONNECTION:
                    sendinitPacket();
                    break;
                case ISPP_CONTROL_MESSAGE_FROM_DEVICE:
                    StringBuffer packetStringBuffer = new StringBuffer();
                    for (byte v : value) packetStringBuffer.append(String.format("0x%02x ", v));
                    Log.i(TAG, "Control: " + packetStringBuffer.toString());
                    analyzeControlMessage(value);
                    break;
                case ISPP_DATA_MESSAGE_FROM_DEVICE:
                    analyzeDataMessage(value);
                    break;
                case MESSAGE_INIT_ISPP:
                    initIsppConnection();
                    break;
                case MESSAGE_SEND_NEXT_DATA_PACKET:
                    sendNextDataPacket();
                    break;
                case WAIT_FOR_ACK:
                    waitforLastAckWindowPacket();
                    break;
                case MESSAGE_RESEND:
                    resendPacket();
                    break;
            }
            return false;
        }

        private void resendPacket() {
            byte[] thePacket = ackWindowList.get(ackWindowIndex - 1);
            if (ackWindowIndex < ackWindowList.size()) {
                ackWindowIndex++;
            } else {
                CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_PACKET;
            }

              mbleCommunication.writeCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                      IsppUtils.ISSP_DATA_CHAR, thePacket);
        }

        private void waitforLastAckWindowPacket() {
            Log.i(TAG, "Starting timer, waiting for ack packet");
            CURRENT_STATE = IsppUtils.ISPP_STATE.WAITING_FOR_ACK;
            ackTimer = new Timer();
            ackTask = new TimerTask() {
                @Override
                public void run() { //Timer has expired
                    mAckTimeouts++;
                    if (mAckTimeouts == MAX_ACK_TIMEOUTS) {
                        Log.i(TAG, "Timer has expired a second time," +
                                " the link is considered to be down");
                        CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
                        mCallBack.onIsppConnectionLost();
                        mIsConnectionEnabled = false;
                    } else { // Start to retransmit window from beginning
                        Log.i(TAG, "Timer has expired first time, " +
                                "trying to retransmit from the beginning of the window");
                        ackWindowIndex = 1;
                        Log.i(TAG, "Trying to retransmit from index: " + ackWindowIndex);
                        if (!mHandler.sendMessage(
                                mHandler.obtainMessage(IsspHandlerCallback.MESSAGE_RESEND))) {
                            Log.e(TAG, "Did not manage to send MESSAGE_RESEND message!!");
                        }
                    }
                }
            };
            ackTimer.schedule(ackTask, ACK_WAITING_TIME);
        }

        private void sendNextDataPacket() {
            if (CURRENT_STATE != IsppUtils.ISPP_STATE.WAITING_FOR_ACK) {
                CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_PACKET;
                byte[] packetToSend = sendBuffer.poll();
                if (packetToSend == null) { // No more packets to send
                    CURRENT_STATE = IsppUtils.ISPP_STATE.LINK_OK;
                    return;
                }
                ackWindowList.add(packetToSend);
                ackWindowIndex++;
                String packet = "";
                for (byte v : packetToSend) packet = packet + String.format("0x%x ", v);
                Log.i(TAG, "Sending packet: " + packet);
                mbleCommunication.writeCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                        IsppUtils.ISSP_DATA_CHAR, packetToSend);
            } else {
                Log.i(TAG, "Current state is waiting for ack, a timer should be running," +
                        " we cannot send next data packet.");
            }
        }

        private void sendinitPacket() {
            CURRENT_STATE = IsppUtils.ISPP_STATE.WAITING_FOR_LINK_SETUP;
            mbleCommunication.writeCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                    IsppUtils.ISSP_CONTROL_CHAR, protocolInitPacket);
        }

        private void processLinkSetupResponse(byte[] packet) {
            if (CURRENT_STATE == IsppUtils.ISPP_STATE.WAITING_FOR_LINK_SETUP) {
                ByteBuffer number = ByteBuffer.allocate(2);
                number.put(packet[4]);
                number.put(packet[3]);
                number.flip();
                maxMtuSize = number.getShort();
                Log.i(TAG, "Max MTU size: " + maxMtuSize);

                ackWindow = packet[5] & 0xff;
                ackWindowList = new Vector<byte[]>(ackWindow);
                ackWindowIndex = 0;
                Log.i(TAG, "Ack window: " + ackWindow);
                nacksBeforeConnectionEnd = packet[6] & 0xff;
                Log.i(TAG, "Nacks before connection ends: " + nacksBeforeConnectionEnd);

                resetCounters();
                dataReadOutput = new ByteArrayOutputStream();
                CURRENT_STATE = IsppUtils.ISPP_STATE.LINK_OK;

                mCallBack.onIsppConnectionEstablished(maxMtuSize);
                mIsConnectionEnabled = true;
            }
        }

        private void resetCounters() {
            nextExpectedPacket = 0;
            nacksNumberCounter = 0;
            wrongPacketsCounter = 0;
            absolutePacketCounter = 0;
            mAckTimeouts = 0;
        }

        private void processLinkSetupRequest(byte[] packet) {
            Log.e(TAG, "LInk setup requests are not answered");
        }

        private void processAck(byte[] packet) {
            if ((ackWindowIndex - 1) < 0 || ackWindowIndex > ackWindow) {
                Log.i(TAG, "ACK window index is not in the right position, " +
                        "ack packet will not be processed: " + ackWindowIndex);
                return;
            }
            byte[] sentpacket = ackWindowList.get(ackWindowIndex - 1);
            if ((sentpacket[0] & 0x7f) == (packet[1] & 0x7f)) { // The ack we received is correct,
                Log.i(TAG, "Cancelling timer");
                if (ackTimer != null) ackTimer.cancel();  // Cancel the timer,
                // we received what we wanted
                ackWindowList.clear();
                ackWindowIndex = 0; // Reset the ackwindow index
                nacksNumberCounter = 0; // This resets the nacks number counter
                mAckTimeouts = 0;

                CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_PACKET;
                //Processing: sending next packet
                if (!mHandler.sendMessage(
                        mHandler.obtainMessage(IsspHandlerCallback.
                                MESSAGE_SEND_NEXT_DATA_PACKET))) {
                    Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET message!!");
                }
            } else {
                Log.i(TAG, "Received ack is not the right one, what we got in the buffer is: " +
                        String.format("0x%x", sentpacket[0]));
            }
        }

        private int getPacketIndexInAckWindow(byte[] packet) {
            byte[] sent;
            for (int i = 0; i < ackWindowList.size(); i++) {
                sent = ackWindowList.get(i);
                if ((sent[0] & 0x7f) == (packet[1] & 0x7f)) {
                    return i;
                }
            }
            return -1;
        }

        private void processNack(byte[] packet) {
            int index = getPacketIndexInAckWindow(packet);
            if (index != -1) { // Asking to resent a packet in the ack window, OK
                if (ackTimer != null) ackTimer.cancel(); // A correct nack has been received
                CURRENT_STATE = IsppUtils.ISPP_STATE.RETRANSMITTING_PACKET;
                nacksNumberCounter++;
                ackWindowIndex = index + 1;

                if (nacksNumberCounter < nacksBeforeConnectionEnd) {
                    if (!mHandler.sendMessage(
                            mHandler.obtainMessage(IsspHandlerCallback.MESSAGE_RESEND))) {
                        Log.e(TAG, "Did not manage to send MESSAGE_RESEND message!!");
                    }
                } else { //Reached max number of nacks, sending connection lost event
                    Log.e(TAG, "Reached max number of nacks");
                    CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
                    mCallBack.onIsppConnectionLost();
                    mIsConnectionEnabled = false;
                }
            } else { // Packet is outside the ackwindow, maybe a missed ACK ?
                byte[] last = ackWindowList.lastElement();
                if ((packet[1] & 0x7f) == (((last[0] & 0x7f) + 1) % 128)) {
                    Log.i(TAG, "Lost an ACK packet: continue the transfer");
                    ackWindowList.clear();
                    ackWindowIndex = 0; //Also reset the ackwindow index
                    nacksNumberCounter = 0; //This resets the nacks number counter
                    mAckTimeouts = 0;

                    CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_PACKET;
                    //Processing: sending next packet
                    if (!mHandler.sendMessage(
                            mHandler.obtainMessage(IsspHandlerCallback.
                                    MESSAGE_SEND_NEXT_DATA_PACKET))) {
                        Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET " +
                                "message!!");
                    }
                } else { //Connection considered to be down
                    Log.e(TAG, "Inconsistent nack received, no backup plan," +
                            " link considered to be down");
                    CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
                    mCallBack.onIsppConnectionLost();
                    mIsConnectionEnabled = false;
                }
            }

        }

        private void analyzeDataMessage(byte[] data) {
            Log.i(TAG, "Received data packet");
            StringBuffer strbuf = new StringBuffer();
            for (byte v : data) strbuf.append(String.format("0x%02x ", v));
            Log.i(TAG, "Value: " + strbuf.toString());

            if ((((byte) nextExpectedPacket) & 0x7f) == (data[0] & 0x7f)) {
                dataReadOutput.write(data, 1, data.length - 1);
                if (((data[0] & 0x80) == 0x80)/*Last packet*/ ||
                        (nextExpectedPacket + 1) % ackWindow == 0) {
                    Log.i(TAG, "Generating ACK for packet: " + String.format("0x%x", data[0]));
                    CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_CONTROL;
                    mbleCommunication.writeCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                            IsppUtils.ISSP_CONTROL_CHAR, generateAckpacket(data[0], true));
                    wrongPacketsCounter = 0;
                }

                if ((data[0] & 0x80) == 0x80) { //This is the last packet,
                    // send the full packet to the upper layer
                    mCallBack.onIsppPacketReceived(dataReadOutput.toByteArray());
                    dataReadOutput = new ByteArrayOutputStream();
                }
                nextExpectedPacket = (nextExpectedPacket + 1) % 128;
            } else { //Not the expected packet, generate nack
                Log.i(TAG, "Generating NACK for packet: " + String.format("0x%x",
                        (byte) ((byte) nextExpectedPacket & 0x7f)));

                CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_CONTROL;
                mbleCommunication.writeCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                        IsppUtils.ISSP_CONTROL_CHAR, generateAckpacket((byte) (
                                (byte)nextExpectedPacket & 0x7f), false));
                wrongPacketsCounter++;

                if (wrongPacketsCounter == nacksBeforeConnectionEnd) {
                    Log.i(TAG, "Maximum number of wrong packets reached, " +
                            "considering the link to be down");
                    CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
                    mCallBack.onIsppConnectionLost();
                    mIsConnectionEnabled = false;
                }
            }

        }

        private void analyzeControlMessage(byte[] packet) {
            switch (packet[0]) {
                case LINK_SETUP_RESPONSE:
                    processLinkSetupResponse(packet);
                    break;
                case LINK_SETUP_REQUEST:
                    processLinkSetupRequest(packet);
                    break;
                case ACK:
                    processAck(packet);
                    break;
                case NACK:
                    processNack(packet);
                    break;
            }
        }
    }

    private void initHandler() {
        final IsspHandlerCallback isspHandlerCallback = new IsspHandlerCallback();
        final HandlerThread thread = new HandlerThread(Ispp.class.getName(),
                android.os.Process.THREAD_PRIORITY_AUDIO);
        thread.start();
        mHandler = new Handler(thread.getLooper(), isspHandlerCallback);
    }

    public boolean writeData(byte[] dataPacket) {
        if (dataPacket.length < 1 || dataPacket.length > maxMtuSize) return false;
        //Fragment the packet in several ones, smaller, add them to the send buffer
        byte[] subarray = new byte[BLE_PACKET_DATA_SIZE];
        byte[] toWrite;
        ByteArrayInputStream baistr = new ByteArrayInputStream(dataPacket);
        while (baistr.available() > 0) {
            int read = baistr.read(subarray, 0, BLE_PACKET_DATA_SIZE);
            toWrite = new byte[read + 1];
            System.arraycopy(subarray, 0, toWrite, 1, read);
            if (read < BLE_PACKET_DATA_SIZE || baistr.available() == 0) {//Last packet, tag it as
                // last
                toWrite[0] = (byte) ((byte) absolutePacketCounter | 0x80);//Mark it as the last
                // subpacket
            } else {
                toWrite[0] = (byte) absolutePacketCounter; //Tag the packet with the right number
            }
            absolutePacketCounter = (absolutePacketCounter + 1) % MAX_PACKET_COUNTER;
            sendBuffer.add(toWrite);
        }
        //Post a message to the handler, to take in the FIFO.
        if (CURRENT_STATE == IsppUtils.ISPP_STATE.LINK_OK) { //post to handler to process next message
            CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_PACKET;
            if (!mHandler.sendMessage(
                    mHandler.obtainMessage(IsspHandlerCallback.MESSAGE_SEND_NEXT_DATA_PACKET))) {
                Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET message!!");
            }
        }
        return true;
    }

    private void initIsppConnection() {
        Log.i(TAG, "Iniating ispp ...");
        mbleCommunication.subscribeToCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                IsppUtils.ISSP_CONTROL_CHAR, true);
    }

    public boolean isConnectionEstablished() {
        return mIsConnectionEnabled;
    }

    private class BleCallback implements IBleCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Ble connection lost");
                CURRENT_STATE = IsppUtils.ISPP_STATE.INITIAL_STATE;
                mIsConnectionEnabled = false;
                mCallBack.onIsppConnectionLost();
                maxMtuSize = -1;
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(IsppUtils.ISSP_CONTROL_CHAR)) {
                if (!mHandler.sendMessage(
                        mHandler.obtainMessage(IsspHandlerCallback.ISPP_CONTROL_MESSAGE_FROM_DEVICE,
                                characteristic.getValue()))) {
                    Log.e(TAG, "Did not manage to send ISPP_CONTROL_MESSAGE_FROM_DEVICE message!!");
                }
            }
            if (characteristic.getUuid().equals(IsppUtils.ISSP_DATA_CHAR)) {
                if (!mHandler.sendMessage(
                        mHandler.obtainMessage(IsspHandlerCallback.ISPP_DATA_MESSAGE_FROM_DEVICE,
                                characteristic.getValue()))) {
                    Log.e(TAG, "Did not manage to send ISPP_DATA_MESSAGE_FROM_DEVICE message!!");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic.getUuid().equals(IsppUtils.ISSP_CONTROL_CHAR)) {
                if (CURRENT_STATE != IsppUtils.ISPP_STATE.WAITING_FOR_LINK_SETUP) {
                    if (!mHandler.sendMessage(
                            mHandler.obtainMessage(IsspHandlerCallback.
                                    MESSAGE_SEND_NEXT_DATA_PACKET))) {
                        Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET " +
                                "connection message!!");
                    }
                }
            } else if (characteristic.getUuid().equals(IsppUtils.ISSP_DATA_CHAR)) {
                //Data char has been written, try to process next data packet
                Log.i(TAG, "Current state is: " + CURRENT_STATE);
                byte[] packet = ackWindowList.get(ackWindowIndex - 1);
                if (((((packet[0] & 0x7f) + 1) % ackWindow == 0) &&
                        (CURRENT_STATE == IsppUtils.ISPP_STATE.TRANSMITTING_PACKET)) ||
                        ((packet[0] & 0x80) == 0x80)) { //Launch timer waiting for
                    // last ack window packet ack or last numbered packet
                    if (!mHandler.sendMessage(
                            mHandler.obtainMessage(IsspHandlerCallback.WAIT_FOR_ACK))) {
                        Log.e(TAG, "Did not manage to send WAIT_FOR_ACK connection message!!");
                    }
                    // We did not reach the ack window end, sending next packet
                } else if (CURRENT_STATE == IsppUtils.ISPP_STATE.RETRANSMITTING_PACKET) {
                    if (!mHandler.sendMessage(
                            mHandler.obtainMessage(IsspHandlerCallback.MESSAGE_RESEND))) {
                        Log.e(TAG, "Did not manage to send MESSAGE_RESEND message!!");
                    }
                } else {
                    CURRENT_STATE = IsppUtils.ISPP_STATE.TRANSMITTING_PACKET;
                    if (!mHandler.sendMessage(
                            mHandler.obtainMessage(IsspHandlerCallback.
                                    MESSAGE_SEND_NEXT_DATA_PACKET))) {
                        Log.e(TAG, "Did not manage to send MESSAGE_SEND_NEXT_DATA_PACKET " +
                                "connection message!!");
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            LogUtils.LOGI(TAG, "on descriptor write received: " +
                    descriptor.getCharacteristic().getUuid() + " status: " + status);
            if (descriptor.getCharacteristic().getUuid().equals(IsppUtils.ISSP_CONTROL_CHAR)) {
                LogUtils.LOGI(TAG, "subscribing to data characteristic");
                mbleCommunication.subscribeToCharacteristic(IsppUtils.ISSP_SERVICE_UUID,
                        IsppUtils.ISSP_DATA_CHAR, true);
            } else if (descriptor.getCharacteristic().getUuid()
                    .equals(IsppUtils.ISSP_DATA_CHAR)) {
                LogUtils.LOGI(TAG, "subscribed to data, sending message init");
                //post to handler to establish issp connection
                if (!mHandler.sendMessage(
                        mHandler.obtainMessage(IsspHandlerCallback.MESSAGE_INIT_CONNECTION))) {
                    Log.e(TAG, "Did not manage to send Init connection message!!");
                }
            }
        }
    }
}
