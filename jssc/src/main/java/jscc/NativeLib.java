package jscc;

public class NativeLib {

    // Used to load the 'jssc' library on application startup.
    static {
        System.loadLibrary("jssc");
    }

    /**
     * A native method that is implemented by the 'jssc' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}