package com.marktreble.f3ftimer.racemanager;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.marktreble.f3ftimer.F3FtimerApplication;
import com.marktreble.f3ftimer.R;
import com.marktreble.f3ftimer.dialog.RaceTimerActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

/**
 * Created by marktreble on 04/08/15.
 */
public class RaceResultsDisplayService extends Service{
    private BluetoothAdapter mBluetoothAdapter;
    public static final String BT_DEVICE = "btdevice";
    public static final int STATE_NONE = 0; // we're doing nothing
    public static final int STATE_LISTEN = 1; // now listening for incoming
    // connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing
    // connection
    public static final int STATE_CONNECTED = 3; // now connected to a remote
    // device
    private ConnectThread mConnectThread;
    private static ConnectedThread mConnectedThread;

    public static Handler mHandler = null;
    public static Handler mHandler2 = null;
    public static int mState = STATE_NONE;
    public static String deviceName;
    public Vector<Byte> packdata = new Vector<>(2048);
    public static BluetoothDevice device = null;

    private String mMacAddress;

    private Context mContext;

    public static void startRDService(Context context, String prefExternalDisplay){
        if (prefExternalDisplay == null || prefExternalDisplay.equals("")) return;

        Intent serviceIntent = new Intent(context, RaceResultsDisplayService.class);
        serviceIntent.putExtra(BT_DEVICE, prefExternalDisplay);
        context.startService(serviceIntent);
    }

    @Override
    public void onCreate() {
        Log.i("RaceResultsDisplay", "Service started");
        this.registerReceiver(onBroadcast, new IntentFilter("com.marktreble.f3ftimer.onExternalUpdate"));

        mContext = this;
        mHandler2 = new Handler();

        super.onCreate();
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.d("RaceResultsDisplay", "ONBIND");
        mHandler = ((F3FtimerApplication) getApplication()).getHandler();
        return mBinder;
    }

    public class LocalBinder extends Binder {
        RaceResultsDisplayService getService() {
            return RaceResultsDisplayService.this;
        }
    }



    private final IBinder mBinder = new LocalBinder();

    public static boolean stop(RaceActivity context){
        if (context.isServiceRunning("com.marktreble.f3ftimer.racemanager.RaceResultsDisplayService")) {
            Intent serviceIntent = new Intent(context, RaceResultsDisplayService.class);
            context.stopService(serviceIntent);
            return true;
        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("RaceResultsDisplay", "Onstart Command");
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        String chosenDevice = intent.getStringExtra(BT_DEVICE);
        if (mBluetoothAdapter != null && !chosenDevice.equals("")) {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice d : pairedDevices) {
                    if (d.getAddress().equals(chosenDevice))
                        device = d;
                }
            }
            if (device == null){
                Log.d("RaceResultsDisplay", "No device... stopping");
                return 0;
            }
            deviceName = device.getName();
            mMacAddress = device.getAddress();
            if (mMacAddress != null && mMacAddress.length() > 0) {
                Log.d("RaceResultsDisplay", "Connecting to: "+deviceName);
                connectToDevice(mMacAddress);
            } else {
                Log.d("RaceResultsDisplay", "No macAddress... stopping");
                stopSelf();
                return 0;
            }
        }

        return START_STICKY;
    }

    private synchronized void connectToDevice(String macAddress) {
        Log.d("RaceResultsDisplay", "Connecting... ");
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    private void setState(int state) {
        RaceResultsDisplayService.mState = state;
    }

    public synchronized void stop() {
        setState(STATE_NONE);
        mHandler2.removeCallbacks(reconnect);

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }
        stopSelf();
    }

    @Override
    public boolean stopService(Intent name) {

        setState(STATE_NONE);
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mBluetoothAdapter.cancelDiscovery();
        return super.stopService(name);
    }

    private void connectionFailed() {
        Log.d("RaceResultsDisplay", "Connection Failed");
        //RaceResultsDisplayService.this.stop();
        // Post to UI that connection is off
        if (mState == STATE_NONE) return;
        reconnect();
    }

    public void connectionLost() {
        Log.d("RaceResultsDisplay", "Connection Lost");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        //RaceResultsDisplayService.this.stop();
        // Post to UI that connection is off
        reconnect();
    }

    public Runnable reconnect = new Runnable() {
        @Override
        public void run() {
            connectToDevice(mMacAddress);
        }
    };

    public void reconnect(){
        Log.d("RaceResultsDisplay", "Reconnecting in 3 seconds...");
        mHandler2.postDelayed(reconnect, 3000);
    }

    private final static Object obj = new Object();

    public static void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (obj) {
            if (mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    private synchronized void connected(BluetoothSocket mmSocket, BluetoothDevice mmDevice) {
        Log.d("RaceResultsDisplay", "Connected");
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);

        // Post to UI that connection is on!


    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            this.mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(getResources().getString(R.string.external_display_uuid)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            setName("ConnectThread");
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                connectionFailed();
                return;

            }
            synchronized (RaceResultsDisplayService.this) {
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("RaceResultsDisplay", "close() of connect socket failed", e);
            }
        }

        public void sendData(String data){
            mConnectedThread.write(data.getBytes());
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;
        private final InputStream mmInStream;

        byte[] buffer = new byte[256];
        int bufferLength;

        private long last_time = 0;
        private boolean ping_sent = false;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            OutputStream tmpOut = null;
            InputStream tmpIn = null;
            try {
                tmpOut = socket.getOutputStream();
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e("RaceResultsDisplay", "temp sockets not created", e);
            }
            mmOutStream = tmpOut;
            mmInStream = tmpIn;
        }

        @Override
        public void run() {

            while (mState==STATE_CONNECTED) {


                long time = System.nanoTime();
                if (time-last_time > 10000000000l){
                    if (ping_sent) {
                        Log.d("RaceResultsDisplay", "PING NOT RETURNED");
                        connectionLost();
                    } else {
                        last_time = time;
                        String ping = String.format("{\"type\":\"ping\",\"time\":%d}", time);
                        Log.d("RaceResultsDisplay", ping);
                        write(ping.getBytes());
                        ping_sent = true;
                    }
                }

                try {
                    if (mmInStream.available()>0){
                        bufferLength = mmInStream.read(buffer);

                        byte[] data = new byte[bufferLength];
                        System.arraycopy(buffer, 0, data, 0, bufferLength);
                        final String response = new String(data, "UTF-8");
                        Log.d("RaceResultsDisplay", "R:"+response);
                        if (response.equals(String.format("%d", last_time))) {
                            ping_sent = false;
                        }
                    }

                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        }


        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e("RaceResultsDisplay", "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
                setState(STATE_NONE);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onDestroy() {
        this.unregisterReceiver(onBroadcast);


        setState(STATE_NONE);

        stop();
        super.onDestroy();
    }

    // Binding for Service->UI Communication
    private BroadcastReceiver onBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.hasExtra("com.marktreble.f3ftimer.external_results_callback")){
                if (mState != STATE_CONNECTED) return;

                Bundle extras = intent.getExtras();
                String data = extras.getString("com.marktreble.f3ftimer.external_results_callback");
                if (data == null){
                    return;
                }

                if (data.equals("run_finalised")){
                    String name = extras.getString("com.marktreble.f3ftimer.pilot_name");
                    String nationality = extras.getString("com.marktreble.f3ftimer.pilot_nationality");
                    nationality = (nationality!=null) ? nationality.toLowerCase() : "";
                    String time = extras.getString("com.marktreble.f3ftimer.pilot_time");
                    JSONObject json = new JSONObject();
                    try {
                        json.put("type", "data");
                        json.put("name", name);
                        json.put("nationality", nationality);
                        json.put("time", time);
                    } catch (JSONException | NullPointerException e) {
                        e.printStackTrace();
                    }

                    mConnectedThread.write(json.toString().getBytes());

                }
            }
        }
    };

}
