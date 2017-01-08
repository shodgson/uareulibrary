package asia.kanopi.uareu4500library;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class UruConnection {

    public UsbDeviceConnection m_connection;

    private static final String LOG_TAG = "UareU-Device";
    private static final int LIBUSB_REQUEST_TYPE_VENDOR = (0x02 << 5);
    private static final int LIBUSB_ENDPOINT_IN = 0x80;
    private static final int LIBUSB_ENDPOINT_OUT = 0x00;

    // Request types
    private static final int CTRL_IN = (LIBUSB_REQUEST_TYPE_VENDOR | LIBUSB_ENDPOINT_IN);
    private static final int CTRL_OUT = (LIBUSB_REQUEST_TYPE_VENDOR | LIBUSB_ENDPOINT_OUT);

    // Requests
    private static final int USB_RQ = 0x04;

    // Reigster addresses
    private static final int REG_HWSTAT = 0x07;
    private static final int REG_SCRAMBLE_DATA_INDEX = 0x33;
    private static final int REG_SCRAMBLE_DATA_KEY = 0x34;
    private static final int REG_MODE = 0x4e;
    private static final int REG_DEVICE_INFO = 0xf0;
    /* firmware starts at 0x100 */
    private static final int REG_RESPONSE = 0x2000;
    private static final int REG_CHALLENGE = 0x2010;

    // Values
    private static final byte REBOOT_REQUIRED = (byte) 0x84;

    private static final int CTRL_TIMEOUT = 5000;

    // Bit values
    private static final byte B_HIGH_POWER = (byte) 0x80;

    // Modes
    private static final byte MODE_INIT = 0x00;
    private static final byte MODE_AWAIT_FINGER_ON = 0x10;
    private static final byte MODE_AWAIT_FINGER_OFF = 0x12;
    private static final byte MODE_CAPTURE = 0x20;
    private static final byte MODE_CAPTURE_AUX = 0x30;
    private static final byte MODE_OFF = 0x70;
    private static final byte MODE_READY = 0xFFFFFF80;

    // Decoding flags
    private static final byte BLOCKF_CHANGE_KEY	    = 0xFFFFFF80;
    private static final byte BLOCKF_NO_KEY_UPDATE	= 0x04;
    private static final byte BLOCKF_ENCRYPTED		= 0x02;
    private static final byte BLOCKF_NOT_PRESENT	 = 0x01;

    private static final int imgSize = 111040; // Size sent by U.are.U 4500
    private static final int usbBuffer = 16384;

    private static final int NUM_ATTEMPTS = 200;

    public byte currentMode = MODE_INIT;

    public static boolean isOn = false;


    public void init_reader(UsbDeviceConnection connection) {

        Log.d(LOG_TAG, "Initialising device");


        byte[] hwstatus;
        int status_length = 1;

        hwstatus = read_register(connection, REG_HWSTAT, status_length);

        if ((hwstatus[0] & B_HIGH_POWER) == 0) {
            hwstatus[0] = (byte) (hwstatus[0] | B_HIGH_POWER);
            set_register(connection, REG_HWSTAT, hwstatus, status_length);

            hwstatus = read_register(connection, REG_HWSTAT, status_length);

        }

        //Log.d(LOG_TAG,"Device should be powered down");
        //hwstatus = read_register(connection, REG_HWSTAT, status_length);
        //Log.d(LOG_TAG,"HW status: " + Utilities.bytesToHex(hwstatus));



        //hwstatus[0] = (byte) (hwstatus[0] & 0x7);
        //Log.d(LOG_TAG, "Power up device, setting hwstatus to: " + Utilities.bytesToHex(hwstatus));
        //set_register(connection, REG_HWSTAT, hwstatus, status_length);
        //hwstatus = read_register(connection, REG_HWSTAT, status_length);
        int attempts = NUM_ATTEMPTS;
        while ((hwstatus[0] & B_HIGH_POWER) != 0 && attempts > 0) {
            hwstatus[0] = (byte) (hwstatus[0] & 0x7);
            set_register(connection, REG_HWSTAT, hwstatus, status_length);
            hwstatus = read_register(connection, REG_HWSTAT, status_length);
            attempts--;
        }


        if ((hwstatus[0] & B_HIGH_POWER) == 0) {
            Log.d(LOG_TAG, "<---- SCANNER TURNED ON ----->");
            isOn = true;
        }
    }


    public byte[] read_register (UsbDeviceConnection connection, int register, int length) {

        byte[] data = new byte[length];
        int r;

        r = connection.controlTransfer(CTRL_IN, USB_RQ, register, 0, data, length, CTRL_TIMEOUT);
        if (r < 0) Log.e(LOG_TAG, "Read device error: " + r);
        if (r < length) Log.e(LOG_TAG, "Data length too short: " + r);
        //if (r == length) Log.d(LOG_TAG, "Data response: " + Utilities.bytesToHex(data));

        return data;
    }

    public int set_register (UsbDeviceConnection connection, int register, byte[] data, int length) {
        int r;

        r = connection.controlTransfer(CTRL_OUT, USB_RQ, register, 0, data, length, 0);

        if (r < length) Log.e(LOG_TAG, "Error sending data: " + r);

        return r;
    }

    public void set_mode(byte mode) {
        byte[] data = new byte[1];
        data[0] = mode;
        set_register(m_connection, REG_MODE, data, 1);
        currentMode = mode;
    }

    private int get_mode() {
        byte[] data;
        data = read_register(m_connection, REG_MODE, 1);
        return (int) data[0];

    }

    public void decodeImage(UruImage image) {

        Random rand = new Random();
        byte[] scrambleData;

        int img_enc_seed = rand.nextInt(1024);
        ByteBuffer scrambleIndex = ByteBuffer.allocate(5);

        scrambleIndex.order(ByteOrder.LITTLE_ENDIAN);
        scrambleIndex.put(image.key_number);
        scrambleIndex.putInt(img_enc_seed);

        set_register(m_connection, REG_SCRAMBLE_DATA_INDEX, scrambleIndex.array(), 5);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        scrambleData = read_register(m_connection, REG_SCRAMBLE_DATA_KEY, 4);


        int key = ByteBuffer.wrap(scrambleData).order(ByteOrder.LITTLE_ENDIAN).getInt();
        Log.d(LOG_TAG, "key = 0x " + Integer.toHexString(key));

        key ^= img_enc_seed;

        Log.d(LOG_TAG, "Encryption id " + String.valueOf(image.key_number) + " -> xord key: " + Integer.toHexString(key));

        // decode blocks
        int i;
        byte block_flag;
        byte block_lines;
        int decodedLines = 0;
        for (i = 0; i < 30 && decodedLines < image.num_lines; i = i + 2) {
            block_flag = image.flags_num_lines[i];
            block_lines = image.flags_num_lines[i + 1];
            Log.d(LOG_TAG, "Decoding block " + Integer.toString(i) + " of " + Integer.toString(block_lines) + " lines with flag " + Integer.toString(block_flag));

            switch (block_flag & (BLOCKF_NO_KEY_UPDATE | BLOCKF_ENCRYPTED)) {
                case BLOCKF_ENCRYPTED:
                    key = decodeBlock(key, decodedLines * image.width, block_lines * image.width, image);
                    break;
                case 0:
                    for (int j = 0; j < block_lines * image.width; j++) {
                        key = updateKey(key);
                    }
                    break;
                default:
                    break;
            }

            if ((block_flag & BLOCKF_NOT_PRESENT) == 0) {
                decodedLines += block_lines;
            }
        }

    }

    private int decodeBlock(int key, int startingByte, int numBytes, UruImage image) {
        // returns updated key if successful

        int xorbyte;
        int mask = 0x01;
        int i;
        for (i = 0; i < (numBytes - 1); i++) {
            // calculate xor cipher
            xorbyte = ((key >>> 4) & mask) << 0;
            xorbyte |= ((key >>> 8) & mask) << 1;
            xorbyte |= ((key >>> 11) & mask) << 2;
            xorbyte |= ((key >>> 14) & mask) << 3;
            xorbyte |= ((key >>> 18) & mask) << 4;
            xorbyte |= ((key >>> 21) & mask) << 5;
            xorbyte |= ((key >>> 24) & mask) << 6;
            xorbyte |= ((key >>> 29) & mask) << 7;

            key = updateKey(key);

            // decrypt data
            try {
                image.data[startingByte + i] = (byte) (image.data[startingByte + i + 1] ^ xorbyte);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
        Log.v(LOG_TAG, "startingByte + i:" + String.valueOf(startingByte + i));
        Log.v(LOG_TAG, "Length of image.data:" + String.valueOf(image.data.length));
        image.data[startingByte + i] = 0;

        return updateKey(key);
    }

    private int updateKey(int key) {
        int bit = key & 0x9248144d;
        bit ^= bit << 16;
        bit ^= bit << 8;
        bit ^= bit << 4;
        bit ^= bit << 2;
        bit ^= bit << 1;
        return (bit & 0x80000000) | (key >>> 1);
    }

    public void turnScannerOff() {
        if (currentMode != MODE_OFF && isOn) {
            set_mode(MODE_OFF);
            isOn = false;
        }
    }

    public UruImage captureFinger(UsbEndpoint data_endpoint) {
        byte[] imgBytes = new byte[usbBuffer];
        ByteBuffer scannedImg = ByteBuffer.allocate(imgSize);
        UruImage capturedImage = new UruImage();

        int r;

        if (currentMode == MODE_AWAIT_FINGER_ON) {
            set_mode(MODE_CAPTURE);

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // wait for image packet
            // keep checking until last packet (shorter length) - and then restart capture
            r = 0;
            while (r != 12736) {
                r = m_connection.bulkTransfer(data_endpoint, imgBytes, usbBuffer, 5000);
            }
            while (scannedImg.position() < imgSize) {

                r = m_connection.bulkTransfer(data_endpoint, imgBytes, usbBuffer, 5000);
                if (r > 0) {
                    if (scannedImg.position() + r > scannedImg.capacity()) {
                        scannedImg.clear();
                    } else {
                        scannedImg.put(imgBytes, 0, r);
                    }
                }
            }

            set_mode(MODE_AWAIT_FINGER_OFF);

            // Turn scannedImg in to UruImage class
            try {
                scannedImg.flip();
                capturedImage.createFromData(scannedImg);
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
            }

            // Decode image
            decodeImage(capturedImage);
            capturedImage.invert();

        }
        return capturedImage;
    }

    public void awaitFinger() {
        if (currentMode != MODE_AWAIT_FINGER_ON) {
            set_mode(MODE_AWAIT_FINGER_ON);
        }

    }
}
