package com.example.serialgampad;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RCActivity extends Activity {
    boolean enableButtonOn;

    private enum UsbPermission {Unknown, Requested, Granted, Denied}

    private UsbPermission usbPermission = UsbPermission.Unknown;
    private static final String INTENT_ACTION_GRANT_USB = "com.example.serialgampad" + ".GRANT_USB";
    private Handler mainLooper;

    UsbSerialPort usbSerialPort;
    int[] channels;
    Button buttonBFPass;
    JoystickView joystickRight, joystickLeft;
    TextView mTextViewAngleRight;
    TextView mTextViewStrengthRight;
    TextView mTextViewCoordinateRight;
    TextView mTextViewAngleLeft;
    TextView mTextViewStrengthLeft;
    TextView mTextViewCoordinateLeft;
    TextView mTextLog;
    String log;
    boolean serialConnected;
    int baudRate = 115200, portNumber = 0;
    byte[] inputBuffer;
    int RcPacketHz = 50;
    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainLooper = new Handler(Looper.getMainLooper());

        serialConnected = false;
        usbSerialPort = null;
        inputBuffer = new byte[64];

        channels = new int[16];
        Arrays.fill(channels, 0);

        log = "";
        mTextLog = findViewById(R.id.log);
        mTextLog.setMovementMethod(new ScrollingMovementMethod());
        appendToLog("sdf");
        initJoysticks();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    openSerialPort();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB), RECEIVER_NOT_EXPORTED);
        } else {
            this.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        }

        buttonBFPass = findViewById(R.id.buttonBFPass);
        buttonBFPass.setOnClickListener(this::initBFPassthrough);

        mainLoop();//starts loop
    }

    void mainLoop() {
        String status = "";
        if (usbSerialPort != null && serialConnected) {
            status = "connected";
            if (enableButtonOn) {
                sendRcChannelsPacket();
                status+=" and sending";
                try {
                    int len = usbSerialPort.read(inputBuffer, 64, 100);
                    appendToLog(Arrays.toString(Arrays.copyOf(inputBuffer, len)));
                    status += " read " + len + " bytes " + Arrays.toString(Arrays.copyOf(inputBuffer, len));
                } catch (IOException e) {
                    disconnect();
                    appendToLog(e.getMessage());
                }
            }
        } else {
            setJoysticksVisibility(View.INVISIBLE);
            status = "disconnred";
        }
        log = status;
        mTextLog.setText(status);
        mainLooper.postDelayed(this::mainLoop, 1000 / RcPacketHz);
    }

    void appendToLog(String str) {
        log += str;
        if (mTextLog != null) {
            mTextLog.setText(log);
        }
    }

    @Override
    public void onStop() {
        this.unregisterReceiver(broadcastReceiver);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!serialConnected && (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted))
            mainLooper.post(this::openSerialPort);
    }

    @Override
    public void onPause() {
        if (serialConnected) {
            disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            appendToLog("USB device attached\r\n");
        }
        super.onNewIntent(intent);
    }

    void initJoysticks() {

        mTextViewAngleRight = findViewById(R.id.textView_angle_right);
        mTextViewStrengthRight = findViewById(R.id.textView_strength_right);
        mTextViewCoordinateRight = findViewById(R.id.textView_coordinate_right);
        mTextViewAngleLeft = findViewById(R.id.textView_angle_left);
        mTextViewStrengthLeft = findViewById(R.id.textView_strength_left);
        mTextViewCoordinateLeft = findViewById(R.id.textView_coordinate_left);
        joystickRight = findViewById(R.id.joystickView_right);
        joystickRight.setOnMoveListener(RcPacketHz, (angle, strength) -> {
            mTextViewAngleRight.setText(angle + "°");
            mTextViewStrengthRight.setText(strength + "%");
            int x = joystickRight.getNormalizedX();
            int y = joystickRight.getNormalizedY();
            mTextViewCoordinateRight.setText(x + "," + y);
            channels[1] = (int) (x * (1984.0 / 100.0));
            channels[2] = (int) (y * (1984.0 / 100.0));
        });

        joystickLeft = findViewById(R.id.joystickView_left);
        joystickLeft.setOnMoveListener(RcPacketHz, (angle, strength) -> {
            mTextViewAngleLeft.setText(angle + "°");
            mTextViewStrengthLeft.setText(strength + "%");
            int x = joystickLeft.getNormalizedX();
            int y = joystickLeft.getNormalizedY();
            mTextViewCoordinateLeft.setText(x + "," + y);
            channels[3] = (int) (x * (1984.0 / 100.0));
            channels[4] = (int) (y * (1984.0 / 100.0));
        });
        setJoysticksVisibility(View.INVISIBLE);
    }

    void setJoysticksVisibility(int visibility) {
        mTextViewAngleRight.setVisibility(visibility);
        mTextViewStrengthRight.setVisibility(visibility);
        mTextViewCoordinateRight.setVisibility(visibility);
        mTextViewAngleLeft.setVisibility(visibility);
        mTextViewStrengthLeft.setVisibility(visibility);
        mTextViewCoordinateLeft.setVisibility(visibility);
        joystickLeft.setVisibility(visibility);
        joystickRight.setVisibility(visibility);
    }

    boolean openSerialPort() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
//        for (UsbDevice d : usbManager.getDeviceList().values())
//        {
//            if(d!=null){
//                device=d;
//            }
//        }
        List<UsbSerialDriver> avaibleDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (avaibleDrivers.size() == 0) {
            return false;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).get(0);//UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            appendToLog("connection failed, no driver\r\n");
            return false;
        }
        if (driver.getPorts().size() < 1) {
            appendToLog("connection failed, no ports on device");
            return false;
        }
        usbSerialPort = driver.getPorts().get(portNumber);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
            intent.setPackage(this.getPackageName());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return false;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                appendToLog("connection failed: permission denied");
            else
                appendToLog("connection failed: open failed");
            return false;
        }
        try {
            usbSerialPort.open(usbConnection);
            try {
                usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            } catch (UnsupportedOperationException e) {
                appendToLog("unsupport setparameters");
            }
            appendToLog("connected");
            serialConnected = true;
        } catch (Exception e) {
            appendToLog("connection failed: " + e.getMessage());
            disconnect();
        }
        return true;
    }

    void initBFPassthrough(View view) {
        if (view.getId() == R.id.buttonBFPass) {
            appendToLog("sf");
            if (serialConnected && usbSerialPort != null) {
                try {
                    usbSerialPort.write("#".getBytes(StandardCharsets.US_ASCII), 50);
                } catch (IOException e) {
                    disconnect();
                    throw new RuntimeException(e);
                }
                mainLooper.postDelayed(() -> {
                    try {
                        usbSerialPort.write(("serialpassthrough 1 " + baudRate).getBytes(StandardCharsets.US_ASCII), 100);
                        setJoysticksVisibility(View.VISIBLE);
                        enableButtonOn = true;
                    } catch (IOException e) {
                        disconnect();
                        throw new RuntimeException(e);
                    }
                }, 1000);
            }
        }
    }

    void sendRcChannelsPacket() {
        byte[] packet = CRSFKt.channelsCRSFToChannelsPacket(channels);
        try {
            usbSerialPort.write(packet, 20);
        } catch (IOException e) {
            serialConnected = false;
            Log.e(this.getClass().getName(), Objects.requireNonNull(e.getMessage()));
            appendToLog(e.getMessage());
        }
    }

    private void disconnect() {
        serialConnected = false;
        appendToLog("disconnected");
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {
        }
        usbSerialPort = null;
    }


}
