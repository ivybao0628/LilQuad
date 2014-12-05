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

import java.util.Arrays;
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


        //gains (type=0x67)
        // kp(int16) kd(int16) kpyaw(int16) kdyaw(int16)
        // meat: 700 100 300 150
        // pwm ticks per radian
        // pwm ticks per radian per second

        // trpy (type=0x70)
        // thrust(int16) roll(int16) pitch(int16) yaw(int16) currentyaw(int16)
        // enablemotors(uint8) = 1 or 0, need to disable then enable if quadrotor went >60 degrees
        // usexternalyaw(unti8) = 0 (unless vicon)
        //
        // range thrust = 0-10000(max), Newtons * 10,000
        // range roll, pitch = 0-10000(roughly 60deg), radians *10,000
        // range yaw = whatever, radians*10000

        // return trpy (type = char 't')

        short thrust = 1000;
        short roll = 0;
        short pitch = 0;
        short yaw = 0;
        short currentYaw = 0;
        byte enableMotors = 1;
        byte useExternalYaw = 0;

        short[] trpy = {thrust, roll, pitch, yaw, currentYaw};
        byte[] message = new byte[18];
        message[0] = 0x55;  // starting bytes
        message[1] = 0x55;
        message[2] = 12;    // length of data
        message[3] = 0x70;  // trpy
        int message_index = 4;
        for(short var : trpy){
            message[message_index+1] = (byte)((var >> 8) & 0xff);
            message[message_index] = (byte)(var & 0xff);
            message_index+=2;
        }
        message[14] = enableMotors;
        message[15] = useExternalYaw;

        short crc = (short)crc16(Arrays.copyOfRange(message,2,16));
        message[16] = (byte)(crc & 0xff);
        message[17] = (byte)((crc >> 8) & 0xff);

        TextView text = (TextView)findViewById(R.id.text);
        text.setText("message :" +Arrays.toString(message));

        connection.bulkTransfer(end, message, message.length, 1000);
    }

    public void onStopButton(View view){

        short thrust = 0;
        short roll = 0;
        short pitch = 0;
        short yaw = 0;
        short currentYaw = 0;
        byte enableMotors = 0;
        byte useExternalYaw = 0;

        short[] trpy = {thrust, roll, pitch, yaw, currentYaw};
        byte[] message = new byte[18];
        message[0] = 0x55;  // starting bytes
        message[1] = 0x55;
        message[2] = 12;    // length of data
        message[3] = 0x70;  // trpy
        int message_index = 4;
        for(short var : trpy){
            message[message_index] = (byte)((var >> 8) & 0xff);
            message[message_index+1] = (byte)(var & 0xff);
            message_index+=2;
        }
        message[14] = enableMotors;
        message[15] = useExternalYaw;

        short crc = (short)crc16(Arrays.copyOfRange(message,2,16));
        message[16] = (byte)(crc & 0xff);
        message[17] = (byte)((crc >> 8) & 0xff);

        TextView text = (TextView)findViewById(R.id.text);
        text.setText("message :" + Arrays.toString(message));

        connection.bulkTransfer(end, message, message.length, 1000);
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
                            //byte[] buffer = hexStringToByteArray("5555020a130049F8");
                            // decimal: 85 85 02 10 19 00 73 248
                            // hex:     55 55 02 0a 13 00 49 F8




//                            byte[] buffer = {85,85,02,10,19,00,00,00};
//                            short crc = (short)crc16(Arrays.copyOfRange(buffer,2,6));
//                            buffer[6] = (byte)(crc & 0xff);
//                            buffer[7] = (byte)((crc >> 8) & 0xff);
//
//
//                            byte[] test_buff = {02,10,19,00};
//                            short test = (short)crc16(test_buff);
//                            TextView text = (TextView)findViewById(R.id.text);
//                            text.setText("CRC (hex): " + String.valueOf(buffer[6]) +"\n"+ String.valueOf(buffer[7]));




                            //connection.bulkTransfer(end, buffer, buffer.length, 1000);
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

    static public int GenerateChecksumCRC16(int bytes[]) {

        int crc = 0xFFFF;
        int temp;
        int crc_byte;

        for (int byte_index = 0; byte_index < bytes.length; byte_index++) {

            crc_byte = bytes[byte_index];

            for (int bit_index = 0; bit_index < 8; bit_index++) {

                temp = ((crc >> 15)) ^ ((crc_byte >> 7));

                crc <<= 1;
                crc &= 0xFFFF;

                if (temp > 0) {
                    crc ^= 0x1021;
                    crc &= 0xFFFF;
                }
                crc_byte <<=1;
                crc_byte &= 0xFF;
            }
        }
        return crc;
//        String hex = Integer.toHexString(crc);
//        hex = hex.substring()
//        return hex;
    }

    static int crc16(final byte[] buffer) {
        int crc = 0xFFFF;

        for (int j = 0; j < buffer.length ; j++) {
            crc = ((crc  >>> 8) | (crc  << 8) )& 0xffff;
            crc ^= (buffer[j] & 0xff);//byte to int, trunc sign
            crc ^= ((crc & 0xff) >> 4);
            crc ^= (crc << 12) & 0xffff;
            crc ^= ((crc & 0xFF) << 5) & 0xffff;
        }
        crc &= 0xffff;
        return crc;
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
