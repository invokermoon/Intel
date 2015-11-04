package com.intel.ispplib.connection.device;

import com.intel.ispplib.ias.ispp.IBleCallback;

import java.util.UUID;

/**
 * Interface to implement for a Ble connection
 */
public interface IBleConnection {

    public enum CONNECTION_STATUS {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        PAIRING,
        PAIRED,
        UNPAIRING,
        UNPAIRED
    }

    public enum GATT_STATUS {
        GATT_SUCCESS,
        GATT_NOT_ENCRYPTED,
        GATT_ENCRYPED_NO_MITM,
        GATT_SERVICE_STARTED,
        GATT_INVALID_CFG,
        GATT_MORE,
        GATT_AUTH_FAIL,
        GATT_PENDING,
        GATT_CMD_STARTED,
        GATT_ERROR,
        GATT_BUSY, GATT_DB_FULL,
        GATT_WRONG_STATE,
        GATT_INTERNAL_ERROR,
        GATT_NO_RESOURCES,
        GATT_ILLEGAL_PARAMETER,
        GATT_INSUF_RESOURCE,
        GATT_UNSUPPORT_GRP_TYPE,
        GATT_INSUF_ENCRYPTION,
        GATT_ERR_UNLIKELY,
        GATT_INVALID_ATTR_LEN,
        GATT_INSUF_KEY_SIZE,
        GATT_NOT_LONG,
        GATT_NOT_FOUND,
        GATT_PREPARE_Q_FULL,
        GATT_INSUF_AUTHORIZATION,
        GATT_INVALID_HANDLE,
        GATT_READ_NOT_PERMIT,
        GATT_WRITE_NOT_PERMIT,
        GATT_INVALID_PDU,
        GATT_INSUF_AUTHENTICATION,
        GATT_REQ_NOT_SUPPORTED,
        GATT_INVALID_OFFSET,
    }

    /**
     * Establish gatt connection  with the specified device
     * @param address The BD address of the device, discovered previously during scan.
     * @param autoConnect The autoconnect parameter, see {@link android.bluetooth.BluetoothDevice}
     */
    public boolean connect(String address, boolean autoConnect);

    /**
     * Disconnect from the currently connected gatt connection, if there is one
     */
    public boolean disconnect();

    /**
     * Establish bonding with specified device
     * @param address The device address
     */
    public boolean pair(String address);

    /**
     * Remove bonding from the currently bonded device
     * @param address The device address
     */
    public boolean unpair(String address);

    /**
     * Write the specified value to the specified gatt characteristic
     * @param service Service unique identifier
     * @param characteristic Characteristic unique identifier
     * @param value The value to be written
     */
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value);

    /**
     * Write the specified value to the specified gatt characteristic
     * @param service Service unique identifier
     * @param characteristic Characteristic unique identifier
     * @param value The value to be written
     * @param  noResponse Tell whether the writetype of the characteristic should be no response
     */
    public boolean writeCharacteristic(UUID service, UUID characteristic, byte[] value,
                                       boolean noResponse);

    /**
     * Read the specified characteristic
     * @param service Service unique identifier
     * @param characteristic Characteristic unique identifier
     */
    public boolean readCharacteristic(UUID service, UUID characteristic);

    /**
     * Discover Ble services
     */
    public boolean discoverServices();

    /**
     * Subscribe to characteristic notifications.
     * @param service Service unique identifier
     * @param characteristic Characteristic unique identifier
     * @param enabled boolean enabling or disabling notifications
     */
    public boolean subscribeToCharacteristic(UUID service, UUID characteristic, boolean enabled);

    /**
     * Set the callback for getting ble gatt callbacks (used by Ispp)
     * @param bleCallback the callback to be called when events specific to ispp occur
     */
    public void setBleCallbackForIspp(IBleCallback bleCallback);
}
