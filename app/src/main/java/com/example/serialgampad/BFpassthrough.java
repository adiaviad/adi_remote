package com.example.serialgampad;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BFpassthrough {
    public static boolean initBFPassthrough(UsbSerialPort port) throws IOException {
        if(port==null) return false;
        port.write("#\r\n".getBytes(StandardCharsets.US_ASCII),10);
        byte[] buff= new byte[100];
        try {
            port.read(buff,100,1);
        } catch (IOException e){
            return true;
        }
        port.write("serialpassthrough 1 115200".getBytes(StandardCharsets.US_ASCII),10);
        return true;

    }
}
