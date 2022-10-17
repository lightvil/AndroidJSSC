package com.cydinfo.jscc;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.cydinfo.jssc.SerialPort;
import com.cydinfo.jssc.SerialPortEvent;
import com.cydinfo.jssc.SerialPortEventListener;
import com.cydinfo.jssc.SerialPortException;


/**
 * Main activity.
 */
public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getCanonicalName();

    static final String INTENT_ACTION_GRANT_USB  = "com.cydinfo.jssc.GRANT_USB";
    static final String INTENT_ACTION_DISCONNECT = "com.cydinfo.jscc.Disconnect";



    /**
     * The port.
     */
    private UsbDevice serialDevice = null;
    private SerialPort serialPort = null;
    //private static final String PORT_NAME = "/dev/ttyS2";
    private static final int BAUD_RATE = 115200;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openSerialPort();

        write();

    }

    @Override
    protected void onDestroy() {
        closeSerialPort();
        super.onDestroy();
    }

    /**
     * Write a sample string to serial port.
     */
    private void write() {
        if (null != serialPort) {
            try {
                serialPort.writeBytes("Hello".getBytes());
            } catch (SerialPortException e) {
                Log.e(LOG_TAG, "Exception while writing", e);
            }
        }
    }

    /**
     * Open serial port.
     */
    private void openSerialPort() {
        // UsbDevice의 name으로 Serial Port를 연다.
        serialPort = new com.cydinfo.jssc.SerialPort(serialDevice.getDeviceName());

        try {
            serialPort.openPort();
            serialPort.setParams(BAUD_RATE, 8, 1, 0);
            serialPort.setFlowControlMode(com.cydinfo.jssc.SerialPort.FLOWCONTROL_XONXOFF_OUT);
            serialPort.addEventListener(mSerialPortEventListener);

        } catch (SerialPortException e) {
            Log.e(LOG_TAG, "Unable to open port", e);
        }
    }

    private void closeSerialPort() {
        try {
            if (serialPort != null) {
                synchronized (this) {
                    serialPort.removeEventListener();
                    serialPort.closePort();
                    serialPort = null;
                    this.notify();
                }
                Log.i(LOG_TAG, "Serial port '" + serialDevice.getDeviceName() + "' closed.");
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Error closing serial port: '" + serialDevice.getDeviceName() + "'", e);
        }

    }
    /**
     * Sample serial port listener.
     */
    private SerialPortEventListener mSerialPortEventListener = new SerialPortEventListener() {
        @Override
        public void serialEvent(final SerialPortEvent serialPortEvent) {
            int eventType = serialPortEvent.getEventType();
            int eventValue = serialPortEvent.getEventValue();
            Log.d(LOG_TAG, String.format("SerialPortEvent EventType: %d, EventValue: %d", eventType, eventValue));
        }
    };

    private BroadcastReceiver usbDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(LOG_TAG, "usbDeviceReceiver: " + action);
            UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(LOG_TAG, "ACTION_USB_DEVICE device: " + device.getProductId());
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                if (device.getProductId() == Constants.USB_UART_PRODUCT_ID ) {
                    // 먼저 장치 권한 처리
                    checkDevices();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device.getProductId() == Constants.USB_UART_PRODUCT_ID) {
                    onDeviceDetached();
                }
            } else if (INTENT_ACTION_GRANT_USB.equals(action)) {
                // USB 권한을 부여 받았다.
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    onDeviceAttached();
                } else {
                    Log.i(LOG_TAG, "usbDeviceReceiver::USB NOT GRANTED");
                }
            }
        }
    };

    private void onDeviceAttached() {
        Log.i(LOG_TAG, "onDeviceAttached()");
        openSerialPort();
    }

    private void onDeviceDetached() {
        closeSerialPort();
    }

    private void checkDevices() {        Log.i(LOG_TAG, "checkDevices()");
        // CP210x 장치가 연결되었는지, 그리고 장치에 대한 권한이 있는지 확인한다.
        UsbDevice uartDevice = null;
        // DeviceManager에서 우리가 사용할 UART 장치를 구한다.
        // 여기서 찾지 못하면 장치가 연결되지 않지 않은 것이다.
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getProductId() == Constants.USB_UART_PRODUCT_ID) {
                Log.i(LOG_TAG, "WE GOT UART DEVICE: " + device.getDeviceName() + "-VID: " + device.getVendorId() + "/PID:" + device.getProductId());
                uartDevice = device;
                break;
            }
        }

        if (uartDevice == null) {
            // TODO: USB 장치가 연결되지 않았을 때: 일단 메시지만 표시한다.
            //   아마도 종료하면 되지 않을까?
            Toast.makeText(MainActivity.this, "사용 가능한 디바이스가 없습니다!", Toast.LENGTH_SHORT).show();
            return;
        }
        // 장치 권한을 확인한다.
        if (!usbManager.hasPermission(uartDevice)) {
            // UART 장치에 권한이 없다면 요청
            Log.i(LOG_TAG, "Requesting permission of UART Device" + uartDevice.getDeviceName() + "-VID: " + uartDevice.getVendorId() + "/PID:" + uartDevice.getProductId());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(uartDevice, usbPermissionIntent);
        } else {
            onDeviceAttached();
        }
    }
}
