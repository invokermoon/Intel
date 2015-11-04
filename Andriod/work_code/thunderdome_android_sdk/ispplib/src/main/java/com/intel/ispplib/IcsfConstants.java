package com.intel.ispplib;

import java.util.UUID;

public class IcsfConstants {

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.
            fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final UUID BATTERY_SERVICE_UUID =
            UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    public static final UUID DEVICE_INFO_SERVICE_UUID =
            UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME_UUID =
            UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_STRING_UUID =
            UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
    public static final UUID SERIAL_NUMBER_UUID =
            UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REV_UUID =
            UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");
    public static final UUID SOFTWARE_REV_UUID =
            UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
    public static final UUID HARDWARE_REV_UUID =
            UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");

    public enum STATUS {
        OK,
        FAIL,
        TIMEOUT,
        NOTFOUND,
        INVALID_PARAMETER,
        ALREADY_IN_USE,
        NOT_READY,
        UNEXPECTED_CMD,
        TOO_LONG,
        BUSY,
        BATTERY_LOW,
        NO_SPACE,
        UNSUPPORTED,
        BLE_ERROR,
        BT_ERROR,
        ANT_ERROR,
        IN_PROGRESS,
        ACTION_UNAVAILBLE,
        TCP_IP_ERROR,
        PROTOBUF_ERROR,
        CONNECTION_REFUSED,
        SECURITY_ERROR,
        CREDENTIAL_ERROR
    }
}

