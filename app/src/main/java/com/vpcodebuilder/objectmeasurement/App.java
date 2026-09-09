package com.vpcodebuilder.objectmeasurement;

import android.app.Application;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

/**
 * Loads OpenCV once, at process start.
 *
 * <p>Every activity used to call {@code OpenCVLoader.initAsync(...)} in
 * {@code onResume}, which asked the separate "OpenCV Manager" APK to supply the
 * native libraries. That app was pulled from the Play Store years ago, and this
 * project shipped no {@code .so} files of its own — so on any current device the
 * loader callback simply never reported success and every screen silently did
 * nothing. The OpenCV artifact on Maven Central bundles its own natives, so a
 * single local init is all that is needed.</p>
 */
public class App extends Application {

    private static final String TAG = "ObjectMeasurement";

    /**
     * Width photos are downsampled to before detection and measurement.
     *
     * <p>Measurement is a ratio between the marker and the selection box, so the
     * absolute size does not change the answer — this only trades memory against
     * how precisely the user can place the box. The old value of 1024 was chosen
     * when devices had far less headroom.</p>
     */
    public static final int WORK_IMAGE_WIDTH = 1600;

    private static boolean openCvReady;

    @Override
    public void onCreate() {
        super.onCreate();
        openCvReady = OpenCVLoader.initLocal();
        if (openCvReady) {
            Log.i(TAG, "OpenCV native libraries loaded");
        } else {
            Log.e(TAG, "OpenCV failed to load — check the ABI filters in build.gradle");
        }
    }

    /** False when the native library is missing for this device's ABI. */
    public static boolean isOpenCvReady() {
        return openCvReady;
    }
}
