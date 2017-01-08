package asia.kanopi.uareu4500library;

public class Status {


    private static String errorMessage;
    private static int code;

    // Status codes
    public static final int ERROR = -1;
    public static final int SUCCESS = 0;
    public static final int INITIALISED = 1;
    public static final int READY_TO_SCAN = 2;
    public static final int FINGER_DETECTED = 3;
    public static final int RECEIVING_IMAGE = 4;
    public static final int SCANNER_POWERED_ON = 5;
    public static final int FINGER_LIFTED = 6;
    public static final int SCANNER_POWERED_OFF = 7;


    public Status() {
        setStatus(INITIALISED);
    }

    public String getErrorMessage() {
        return Status.errorMessage;
    }

    public int getStatus() {
        return Status.code;
    }

    public void setStatus(int status) {
        Status.code = status;
        Status.errorMessage = "";
    }

    public void setStatus(int status, String errorMessage) {
        Status.code = status;
        Status.errorMessage = errorMessage;
    }
}
