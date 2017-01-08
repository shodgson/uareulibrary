package asia.kanopi.uareu4500library;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import java.nio.ByteBuffer;

public class ScanFinger implements Runnable {

    private UruConnection reader;
    private UsbDevice usbDevice;
    private static final String LOG_TAG = "ScanFinger";

    // IRQ values
    private static final short IRQDATA_SCANPWR_ON = 0x56aa;
    private static final short IRQDATA_FINGER_ON = 0x0101;
    private static final short IRQDATA_FINGER_OFF = 0x0200;
    private static final short IRQDATA_DEATH = 0x0800;

    private static final int irqLength = 64;
    private static final int imgLength = 16384;

    private boolean listen;
    private Status status;
    private Handler imageHandler;
    private Handler statusHandler;
    private Context context;

    public ScanFinger(UruConnection reader, UsbDevice usbDevice,
                      Status status, Handler imageHandler, Handler statusHandler, Context context) {
        this.reader = reader;
        this.usbDevice = usbDevice;
        this.status = status;
        this.imageHandler = imageHandler;
        this.statusHandler = statusHandler;
        this.context = context;
        listen = true;
    }

    private void sendImage(UruImage img) {
        byte[] image = img.getImageBitmap();
        byte[] pgm = img.getPgm();
        updateStatus(Status.SUCCESS);
        Message msg = imageHandler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putInt("status", status.getStatus());
        bundle.putByteArray("img", image);
        bundle.putByteArray("pgm", pgm);
        msg.setData(bundle);
        imageHandler.sendMessage(msg);
    }

    private void updateStatus(int newStatus) {
        status.setStatus(newStatus);
        updateStatus();
    }

    private void updateStatus(int newStatus, String newErrorMessage) {
        status.setStatus(newStatus, newErrorMessage);
        updateStatus();
    }

    private void updateStatus() {
        if (statusHandler != null) {
            Message msg = statusHandler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putInt("status", status.getStatus());
            if (status.getStatus() == Status.ERROR) {
                bundle.putString("errorMessage", status.getErrorMessage());
            }
            msg.setData(bundle);
            statusHandler.sendMessage(msg);
        }
    }

    @Override
    public void run() {
        ByteBuffer irqBuffer = ByteBuffer.allocate(irqLength);
        ByteBuffer imgBuffer = ByteBuffer.allocate(imgLength);
        UsbRequest irq = new UsbRequest();
        UsbRequest img_data = new UsbRequest();
        UsbRequest response;
        UsbInterface uru_interface = usbDevice.getInterface(0);
        UsbEndpoint m_irq_endpoint = uru_interface.getEndpoint(0);
        UsbEndpoint data_endpoint = uru_interface.getEndpoint(1);

        irq.initialize(reader.m_connection, m_irq_endpoint);
        img_data.initialize(reader.m_connection, data_endpoint);

        // Listen to the USB connection
        while (listen) {
            // Set up buffers
            if (irq.queue(irqBuffer, irqLength) && img_data.queue(imgBuffer, imgLength)) {
                // Wait for response
                response = reader.m_connection.requestWait();
                if (response != null) {
                    if (response.equals(irq)) {
                        short irq_type = irqBuffer.getShort(0);
                        switch (irq_type) {
                            case IRQDATA_SCANPWR_ON:
                                updateStatus(Status.SCANNER_POWERED_ON);
                                reader.awaitFinger();
                                updateStatus(Status.READY_TO_SCAN);
                                break;
                            case IRQDATA_FINGER_ON:
                                updateStatus(Status.FINGER_DETECTED);
                                UruImage img = reader.captureFinger(data_endpoint);
                                sendImage(img);
                                break;
                            case IRQDATA_FINGER_OFF:
                                updateStatus(Status.FINGER_LIFTED);
                                reader.turnScannerOff();
                                updateStatus(Status.SCANNER_POWERED_OFF);
                                listen = false;
                                break;
                            default:
                                break;
                        }
                    }
                } else {
                    updateStatus(Status.ERROR, "Null response from the reader");
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        irq.close();
        img_data.close();

    }
}
