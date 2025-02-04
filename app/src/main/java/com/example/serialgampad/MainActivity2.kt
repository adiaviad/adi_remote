package com.example.serialgampad

import android.annotation.SuppressLint
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber


class MainActivity2 : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val channels:MutableList<Int> = MutableList(16) {992}

        val mTextViewAngleRight=findViewById<TextView>(R.id.textView_angle_right)
        val mTextViewStrengthRight=findViewById<TextView>(R.id.textView_strength_right)
        val mTextViewCoordinateRight=findViewById<TextView>(R.id.textView_coordinate_right)
        val mTextLog=findViewById<TextView>(R.id.log)
        val port=openSerialPort(mTextLog)
//        BFpassthrough.initBFPassthrough(port)


        val joystickRight = findViewById<JoystickView>(R.id.joystickView_right)
        joystickRight.setOnMoveListener(50, JoystickView.OnMoveListener { angle, strength ->
            mTextViewAngleRight.text = "${angle}°";
            mTextViewStrengthRight.text = "${strength}%";
            val x=joystickRight.getNormalizedX()
            val y=joystickRight.getNormalizedY()
            mTextViewCoordinateRight.text = String.format("x%03d:y%03d", x,y);
            channels[1]=(x*(1984.0/100.0)).toInt();
            channels[2]=(y*(1984.0/100.0)).toInt()
        })

        val mTextViewAngleLeft=findViewById<TextView>(R.id.textView_angle_left)
        val mTextViewStrengthLeft=findViewById<TextView>(R.id.textView_strength_left)
        val mTextViewCoordinateLeft=findViewById<TextView>(R.id.textView_coordinate_left)

        val joystickLeft = findViewById<JoystickView>(R.id.joystickView_left)
        joystickLeft.setOnMoveListener(50, JoystickView.OnMoveListener { angle, strength ->
            mTextViewAngleLeft.text = "${angle}°";
            mTextViewStrengthLeft.text = "${strength}%";
            val x=joystickLeft.getNormalizedX()
            val y=joystickLeft.getNormalizedY()
            mTextViewCoordinateLeft.text = String.format("x%03d:y%03d", x,y);
            channels[3]=(x*(1984.0/100.0)).toInt();
            channels[4]=(y*(1984.0/100.0)).toInt()

            if(port!=null) {
                sendRcChannels(port!!, channels)
            }
        })

    }


    private fun sendRcChannels(port: UsbSerialPort,channels:List<Int>) {
        val channelBytes= channelsCRSFToChannelsPacket(channels)
        port.write(channelBytes,10)
    }
    @SuppressLint("SetTextI18n")
    private fun openSerialPort(log:TextView): UsbSerialPort? {
        // Find all available drivers from attached devices.
        // Find all available drivers from attached devices.
        val manager = getSystemService(USB_SERVICE) as UsbManager

//        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
//        log.text=availableDrivers.toString()
//
//        if (availableDrivers.isEmpty()) {
//            log.text= "\n"+ log.text as String + "no drivers available"
//            return null
//        }

        // Open a connection to the first available driver.
        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
            ?: // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return null

        val port = driver.ports[0] // Most devices have just one port (port 0)

        port.open(connection)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        return port
    }
}