package com.whatever.cis542.dng.lilquad;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Handler;


public class FlyingActivity extends Activity implements SensorEventListener {


    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private static final int INTERFACE = 1;
    private static final int ENDPOINT = 0;
    private static final boolean USE_FORCE = true;
    private UsbManager manager;
    private UsbInterface inter;
    private UsbEndpoint end;
    private UsbDeviceConnection connection;

    private SensorManager mSensorManager;
    private float[] accel_vals = new float[3];
    private float[] magnet_vals = new float[3];
    private float[] mRotationM = new float[9];
    private float[] rpy = new float[3];

    private boolean gotPermission = false;
    private short current_thrust = 0;
    private short current_yaw = 0;
    boolean isPressedYawPlus = false;
    boolean isPressedYawMinus = false;
    private short max_thrust = 10000;
    private short one_rad_roll = 4000;
    private short one_rad_pitch = 4000;
    private short yaw_incr = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_flying);

        TextView text = (TextView)findViewById(R.id.text);
        String s = "USB devices:\n";

        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        final Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(isPressedYawPlus){
                    current_yaw += yaw_incr;
                }
                if(isPressedYawMinus){
                    current_yaw -= yaw_incr;
                }
            }
        }, 0, 30);
        ImageButton buttonYawUp = (ImageButton) findViewById(R.id.yaw_plus);
        buttonYawUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(isPressedYawPlus == false){
                            isPressedYawPlus = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        isPressedYawPlus = false;
                        break;
                }
                return true;
            }
        });

        ImageButton buttonYawDown = (ImageButton) findViewById(R.id.yaw_minus);
        buttonYawDown.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(isPressedYawMinus == false){
                            isPressedYawMinus = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        isPressedYawMinus = false;
                        break;
                }
                return true;
            }
        });

        VerticalSeekBar vSeekBar = (VerticalSeekBar)findViewById(R.id.SeekBar02);
        vSeekBar.setMax(max_thrust);
        vSeekBar.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(VerticalSeekBar seekBar) {
                current_thrust = 0;
                seekBar.setProgress(0);
            }

            @Override
            public void onStartTrackingTouch(VerticalSeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(VerticalSeekBar seekBar, int progress,
                                          boolean fromUser) {
                current_thrust = (short)(progress);
            }
        });

        // assuming that only one usb device is plugged in to the phone
        if (deviceIterator.hasNext()){
            UsbDevice device  = deviceIterator.next();
            manager.requestPermission(device, mPermissionIntent);
            s += device.toString() + "\n";
            s += "num usbInterfaces: " + String.valueOf(device.getInterfaceCount()) + "\n";
            text.setText(s);
        }
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    public void sendMessage(short thrust, short roll, short pitch, short yaw, byte enableMotors){
        short currentYaw = 0;
        byte useExternalYaw = 0;
        final byte[] message = new byte[18];

        short[] trpy = {thrust, roll, pitch, yaw, currentYaw};

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

//        TextView text = (TextView)findViewById(R.id.text);
//        text.setText("message :" +Arrays.toString(message));

        new Thread(new Runnable() {
            public void run() {
                connection.bulkTransfer(end, message, message.length, 1000);
            }
        }).start();

    }

    public void onStopButton(View view){
        mSensorManager.unregisterListener(this);
        sendMessage((short)0, (short)0, (short)0, (short)0, (byte)0);
        Intent intent = new Intent(this, OpeningScreen.class);
        startActivity(intent);
    }

//    public void onThrustInc(View v){
//        current_thrust +=50;
//        ((TextView)findViewById(R.id.text)).setText("Thrust: " + Short.toString(current_thrust));
//    }
//
//    public void onThrustDec(View v){
//        current_thrust -=50;
//        ((TextView)findViewById(R.id.text)).setText("Thrust: " + Short.toString(current_thrust));
//    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()){
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accel_vals, 0, 3);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnet_vals, 0, 3);
                break;
            default:
                return;
        }
        if (SensorManager.getRotationMatrix(mRotationM, null, accel_vals,
                magnet_vals)){
            SensorManager.getOrientation(mRotationM, rpy);
        }

//        TextView t = (TextView)findViewById(R.id.text);
//        t.setText("Yaw: " + String.valueOf(rpy[0]) + "\nPitch: " +
//                String.valueOf(rpy[1]) + "\nRoll: " + String.valueOf(rpy[2]));

        if (isPressedYawMinus || isPressedYawPlus) {
            ((TextView)findViewById(R.id.text)).setText("Yaw: " + Short.toString(current_yaw));
        }

        short current_pitch = (short)(rpy[2]*one_rad_pitch);
        short current_roll = (short)(-rpy[1]*one_rad_roll);

        TextView t = (TextView)findViewById(R.id.text);
        t.setText("Yaw: " + Short.toString(current_yaw) + "\nPitch: " +
                Short.toString(current_pitch) + "\nRoll: " + Short.toString(current_roll)
                + "\nThrust: " + Short.toString(current_thrust));

        if (gotPermission) {
            sendMessage(current_thrust, current_roll, current_pitch, (short)-current_yaw, (byte)1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if(gotPermission) {
            sendMessage((short)0, (short)0, (short)0, (short)0, (byte)0);
        }
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
                        }
                        gotPermission = true;
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
