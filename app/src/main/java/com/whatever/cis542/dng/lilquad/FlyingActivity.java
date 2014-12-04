package com.whatever.cis542.dng.lilquad;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;


public class FlyingActivity extends Activity {


    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private static final int INTERFACE = 1;
    private static final int ENDPOINT = 0;
    private static final boolean USE_FORCE = true;

    private UsbManager manager;
    private UsbInterface inter;
    private UsbEndpoint end;
    private UsbDeviceConnection connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flying);

        TextView text = (TextView)findViewById(R.id.text);
        String s = "USB devices:\n";

        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();


        // assuming that only one usb device is plugged in to the phone
        if (deviceIterator.hasNext()){
            UsbDevice device  = deviceIterator.next();

            manager.requestPermission(device, mPermissionIntent);

            s += device.toString() + "\n";
            s += "num usbInterfaces: " + String.valueOf(device.getInterfaceCount()) + "\n";

            text.setText(s);
        }
    }

    public void onStartButton(View view){
        byte[] buffer = hexStringToByteArray("5555020a130049F8");
        // 85 85 02 10 19 00 73 248
        // 55 55 02 0a 13 00 49 F8
        connection.bulkTransfer(end, buffer, buffer.length, 1000);

        
    }



    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            inter = device.getInterface(INTERFACE);
                            end = inter.getEndpoint(ENDPOINT);
                            connection = manager.openDevice(device);
                            connection.claimInterface(inter, USE_FORCE);
                            byte[] buffer = hexStringToByteArray("5555020a130049F8");
                            // 85 85 02 10 19 00 73 248
                            // 55 55 02 0a 13 00 49 F8
                            connection.bulkTransfer(end, buffer, buffer.length, 1000);
                        }
                    }
                    else {
                        Log.d("PERMISSIONS", "permission denied for device " + device);
                        ((TextView)findViewById(R.id.text)).setText("permission denied");
                    }
                }
            }
        }
    };

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_flying, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
