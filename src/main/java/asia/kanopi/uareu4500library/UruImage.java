package asia.kanopi.uareu4500library;

import android.graphics.Bitmap;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UruImage  {


    private static final int IMAGE_HEIGHT = 290;
    private static final int IMAGE_WIDTH = 384;
    private static final int QUALITY = 100;
    private static final String LOG_TAG = "U.are.U-Image";

    // Raw data
    private short unknown_00;
    private byte[] unknown_07 = new byte[9];
    private byte[] unknown_2E = new byte[18];

    public short num_lines;
    public short width;
    public byte key_number;
    public byte[] flags_num_lines = new byte[30];
    public byte[] data;

    // setter & getter

    public void createFromData(ByteBuffer sData) throws IOException {

        sData.order(ByteOrder.LITTLE_ENDIAN);

        unknown_00 = sData.getShort();
        Log.d(LOG_TAG, "unknown_00: " + String.valueOf(unknown_00));

        width = sData.getShort();
        Log.d(LOG_TAG, "width: " + String.valueOf(width));

        num_lines = sData.getShort();
        Log.d(LOG_TAG, "num_lines: " + String.valueOf(num_lines));

        key_number = sData.get();
        Log.d(LOG_TAG, "key_number: " + String.valueOf(key_number));

        sData.get(unknown_07, 0, 9);
        Log.d(LOG_TAG, "unknown_07: " + bytesToHex(unknown_07));

        sData.get(flags_num_lines, 0, 30);
        Log.d(LOG_TAG, "flags_num_lines: " + bytesToHex(flags_num_lines));

        sData.get(unknown_2E, 0, 18);
        Log.d(LOG_TAG, "unknown_2E: " + bytesToHex(unknown_2E));

        data = new byte[(num_lines + 1) * width]; // extra line for decoding
        sData.get(data, 0, num_lines * width);

    }

    public byte[] getImageBitmap(){

        int dataOffset = 0;

        int[] pixels = new int[IMAGE_WIDTH * IMAGE_HEIGHT];
        int i = 0;
        for (int y = 0; y < IMAGE_HEIGHT; y++) {
            for (int x = 0; x < IMAGE_WIDTH; x++) {
                int gray = data[dataOffset + i] & 0xff;
                pixels[i] = 0xff000000 | gray << 16 | gray << 8 | gray;
                i++;
            }
        }

        Bitmap bm = Bitmap.createBitmap(pixels, IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, QUALITY, bs);
        return bs.toByteArray();
    }

    public byte[] getPgm(){
        return data;
    }

    public void invert() {
        int length = width * num_lines;
        int i;
        byte max_value = 0xFFFFFFFF;
        for (i = 0; i < length; i++) {
            data[i] = (byte) (max_value - data[i]);
        }
    }


    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}


