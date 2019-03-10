package com.coldyam.btleserver.feature;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GattActivity extends AppCompatActivity {

    /* Tag */
    private static final String TAG = GattActivity.class.getSimpleName();

    /* Service UUID */
    private static UUID BTLE_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static UUID NOTIFY_UUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
    private static UUID WRITE_UUID = UUID.fromString("00002a2c-0000-1000-8000-00805f9b34fb");
    private static UUID READ_UUID = UUID.fromString("00002a2d-0000-1000-8000-00805f9b34fb");
    private static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    /* Collection of notification subscribers */
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    /* Packet Control */
    private static int MAX_PACKET_LENGTH = 20;
    private String EOP_POSTFIX = "EOP";
    private String SOP_PREFIX = "SOP";

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    /**
     * Callback to receive information about the advertisement process.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };

//    private AdvertisingSetCallback mAdvertiseSetCallback = new AdvertisingSetCallback() {
//        @Override
//        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
//            Log.i(TAG, "onAdvertisingSetStarted(): txPower:" + txPower + " , status: "
//                    + status);
//        }
//
//        @Override
//        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
//            Log.i(TAG, "onAdvertisingDataSet() :status:" + status);
//        }
//
//        @Override
//        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
//            Log.i(TAG, "onScanResponseDataSet(): status:" + status);
//        }
//
//        @Override
//        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
//            Log.i(TAG, "onAdvertisingSetStopped():");
//        }
//    };

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();
            if (NOTIFY_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG, "onCharacteristicReadRequest(Notify)");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            } else if (READ_UUID.equals(characteristic.getUuid())) {
                Log.i(TAG, "onCharacteristicReadRequest(Read)");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            if (WRITE_UUID.equals(characteristic.getUuid())) {
                String string = new String(value, StandardCharsets.UTF_8);
                receiveString(string);
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            if (CLIENT_CONFIG.equals(descriptor.getUuid())) {
                Log.d(TAG, "Config descriptor read");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue);
            } else {
                Log.w(TAG, "Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (CLIENT_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: " + device);
                    mRegisteredDevices.add(device);
                    try {
                        JSONObject object = new JSONObject();
                        object.put("title", "连线成功并等待接收讯息");
                        object.put("id", 0);
                        object.put("name", "GATT连线");
                        Log.d(TAG, object.toString());
                        notifyJSONObject(object);
                    } catch (Throwable t) {
                        Log.e(TAG, "Could not parse malformed JSON");
                    }

                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: " + device);
                    mRegisteredDevices.remove(device);
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
        }
    };

    /**
     * Notify Characteristic Changed Processess
     * All notify change requests for characteristics are handled here.
     */

    public void receiveString(String string) {
        Log.i(TAG, "onCharacteristicWriteRequest: " + string);
    }

    public void notifyJSONObject(JSONObject object) {
        notifyString(object.toString());
    }

    public void notifyString(String string) {
        notifyCharacteristicChanged(SOP_PREFIX.getBytes());
        final byte[] buffer = string.getBytes();
        final int length = Math.min(buffer.length, MAX_PACKET_LENGTH);
        final int total = buffer.length;
        int bufferOffset = 0;
        while (bufferOffset < total) {
            int endOffset = Math.min(total, bufferOffset + length);
            byte[] value = Arrays.copyOfRange(buffer, bufferOffset, endOffset);
            Log.d("TAG", new String(value, StandardCharsets.UTF_8));
            notifyCharacteristicChanged(value);
            bufferOffset += length;
        }
        notifyCharacteristicChanged(EOP_POSTFIX.getBytes());
    }

    private void notifyCharacteristicChanged(byte[] value) {
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic notifyCharacteristic = mBluetoothGattServer
                    .getService(BTLE_SERVICE)
                    .getCharacteristic(NOTIFY_UUID);
            notifyCharacteristic.setValue(value);
            mBluetoothGattServer.notifyCharacteristicChanged(device, notifyCharacteristic, false);
        }
    }

    /* Create Service */
    public static BluetoothGattService createService() {
        BluetoothGattService service = new BluetoothGattService(BTLE_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Notify characteristic
        BluetoothGattCharacteristic notifyCharacteristic = new BluetoothGattCharacteristic(NOTIFY_UUID,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        notifyCharacteristic.addDescriptor(configDescriptor);

        // Read characteristic
        BluetoothGattCharacteristic readCharacteristic = new BluetoothGattCharacteristic(READ_UUID,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // Write characteristic
        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(WRITE_UUID,
                //Write-only characteristic
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(notifyCharacteristic);
        service.addCharacteristic(readCharacteristic);
        service.addCharacteristic(writeCharacteristic);

        return service;
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        bluetoothAdapter.setName("DUI");
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

//        AdvertisingSetParameters parameters = (new AdvertisingSetParameters.Builder())
//                .setLegacyMode(true) // True by default, but set here as a reminder.
//                .setConnectable(true)
//                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
//                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
//                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BTLE_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
//        mBluetoothLeAdvertiser
//                .startAdvertisingSet(parameters, data, null, null, null, mAdvertiseSetCallback);
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        mBluetoothGattServer.addService(createService());
    }

    /**
     * Shut down the GATT server.
     */
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mBluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            finish();
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            startAdvertising();
            startServer();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
        }

        unregisterReceiver(mBluetoothReceiver);
    }

}
