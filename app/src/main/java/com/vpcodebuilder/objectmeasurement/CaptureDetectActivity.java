package com.vpcodebuilder.objectmeasurement;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vpcodebuilder.objectmeasurement.detect.DetectorConfig;
import com.vpcodebuilder.objectmeasurement.detect.MarkerDetector;
import com.vpcodebuilder.objectmeasurement.detect.SignMeasurement;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import java.io.File;

/**
 * Live camera view that highlights the reference marker while you frame the shot.
 *
 * <p>Two things changed fundamentally from the original screen.</p>
 *
 * <p>First, capture. The old {@code takePicture} asked the Camera1 API to take a
 * full-size JPEG and then called {@code Thread.sleep(2000)} <em>on the UI
 * thread</em> hoping the callback had finished writing the file — it froze the
 * app for two seconds and returned a truncated file whenever the device was
 * slower than that guess. The frame the user is looking at is already in memory
 * and is the exact frame the detector matched, so that is what gets saved, with
 * the marker geometry alongside it. No sleep, no second capture path, no
 * mismatch between what was detected and what was stored.</p>
 *
 * <p>Second, permissions. {@code minSdk} was 17 and nothing ever requested
 * {@code CAMERA} at runtime, so on Android 6 and later the preview was simply
 * black.</p>
 */
public class CaptureDetectActivity extends AppCompatActivity implements CvCameraViewListener2 {

    public static final String EXTRA_IMAGE_PATH = "imagePath";
    public static final String EXTRA_MARKER = "marker";
    public static final String EXTRA_SOURCE_WIDTH = "sourceWidth";

    /** Don't re-run detection more often than this; the preview stays at full rate. */
    private static final long DETECT_INTERVAL_NS = 90_000_000L;
    /** Frames of a steady, confident lock before auto-capture fires. */
    private static final int AUTO_CAPTURE_FRAMES = 8;
    private static final float AUTO_CAPTURE_CONFIDENCE = 0.72f;

    private static final Scalar COLOR_STRONG = new Scalar(0, 224, 122, 255);
    private static final Scalar COLOR_WEAK = new Scalar(255, 179, 0, 255);

    private CameraBridgeViewBase cameraView;
    private TextView status;
    private MaterialButton torchButton;
    private MaterialButton autoButton;

    /** Fast settings for the live viewfinder. */
    private DetectorConfig config;
    /** Full-quality settings, used once on the frame that is actually kept. */
    private DetectorConfig preciseConfig;

    private volatile SignMeasurement lastMarker;
    private volatile boolean captureRequested;
    private volatile boolean finishing;

    private long lastDetectAt;
    private int stableFrames;
    private Point lastCentre;

    /** Toggled on the UI thread, read on the camera thread. */
    private volatile boolean autoCapture;
    private boolean torchOn;
    private String torchCameraId;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capture_detect);

        if (!App.isOpenCvReady()) {
            DialogHelper.alert(this, R.string.dialog_error, getString(R.string.error_no_opencv));
            return;
        }

        SharedPreferences prefs = getSharedPreferences(DetectorConfig.PREFS, MODE_PRIVATE);
        // Two passes with different budgets. The viewfinder runs on the camera
        // callback thread, where every millisecond is a dropped preview frame,
        // so it works at half resolution and skips sub-pixel refinement — plenty
        // to draw an outline and judge stability. The corners that actually get
        // measured are re-derived at full quality from the captured frame, once.
        preciseConfig = DetectorConfig.from(prefs);
        config = preciseConfig.copy();
        config.workingWidth = 540;
        config.refineCorners = false;

        status = findViewById(R.id.txtStatus);
        torchButton = findViewById(R.id.btTorch);
        autoButton = findViewById(R.id.btAuto);
        FloatingActionButton shutter = findViewById(R.id.btTakePicture);

        cameraView = findViewById(R.id.capture_view);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);
        cameraView.setMaxFrameSize(1920, 1080);

        shutter.setOnClickListener(v -> captureRequested = true);
        torchButton.setOnClickListener(v -> toggleTorch());
        autoButton.setOnClickListener(v -> toggleAutoCapture());
        updateAutoLabel();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, R.string.capture_permission_required,
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });

        if (!config.hasMarkerSize()) {
            status.setText(R.string.capture_hint_no_marker_size);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraView == null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        cameraView.setCameraPermissionGranted();
        cameraView.enableView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        setTorch(false);
        if (cameraView != null) cameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) cameraView.disableView();
    }

    // ------------------------------------------------------------ camera frames

    @Override
    public void onCameraViewStarted(int width, int height) {
        lastMarker = null;
        stableFrames = 0;
        lastCentre = null;
    }

    @Override
    public void onCameraViewStopped() {
        lastMarker = null;
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat rgba = inputFrame.rgba();
        if (finishing) return rgba;

        long now = System.nanoTime();
        if (now - lastDetectAt >= DETECT_INTERVAL_NS) {
            lastDetectAt = now;
            // The detector copies what it needs, so the live frame is untouched.
            lastMarker = MarkerDetector.detect(rgba, config);
            updateStability(lastMarker);
            postStatus(lastMarker);
        }

        SignMeasurement marker = lastMarker;

        // Save before the overlay is drawn: the frame is the deliverable, the
        // green outline is only a viewfinder aid and must not be baked in.
        if (captureRequested
                || (autoCapture && marker != null && stableFrames >= AUTO_CAPTURE_FRAMES)) {
            captureRequested = false;
            finishing = true;
            capture(rgba, marker);
            return rgba;
        }

        if (marker != null) {
            Scalar colour = marker.confidence() >= AUTO_CAPTURE_CONFIDENCE
                    ? COLOR_STRONG : COLOR_WEAK;
            MarkerDetector.drawQuad(rgba, marker.cornerPoints(), colour, 3);
        }

        return rgba;
    }

    /**
     * Counts consecutive frames where the marker is both confident and holding
     * still, which is what makes hands-free capture usable rather than trigger-happy.
     */
    private void updateStability(SignMeasurement marker) {
        if (marker == null || marker.confidence() < AUTO_CAPTURE_CONFIDENCE) {
            stableFrames = 0;
            lastCentre = null;
            return;
        }

        android.graphics.Rect box = marker.boundary();
        Point centre = new Point(box.centerX(), box.centerY());
        if (lastCentre != null) {
            double drift = Math.hypot(centre.x - lastCentre.x, centre.y - lastCentre.y);
            stableFrames = drift < box.width() * 0.04 ? stableFrames + 1 : 0;
        }
        lastCentre = centre;
    }

    private void postStatus(SignMeasurement marker) {
        final String text;
        if (!config.hasMarkerSize()) {
            text = getString(R.string.capture_hint_no_marker_size);
        } else if (marker == null) {
            text = getString(R.string.capture_hint_searching);
        } else if (marker.confidence() >= AUTO_CAPTURE_CONFIDENCE) {
            text = getString(R.string.capture_hint_found,
                    Math.round(marker.confidence() * 100));
        } else {
            text = getString(R.string.capture_hint_weak);
        }
        runOnUiThread(() -> {
            if (status != null) status.setText(text);
        });
    }

    /** Saves the exact frame the detector matched, then returns it to the caller. */
    private void capture(Mat cleanFrame, SignMeasurement previewMarker) {
        Bitmap bitmap = null;
        try {
            // One accurate pass on the frame we are keeping. The viewfinder's
            // corners came from a half-resolution image with no sub-pixel
            // refinement; scaling those back up would multiply their error into
            // every measurement taken from this photo.
            SignMeasurement marker = MarkerDetector.detect(cleanFrame, preciseConfig);
            if (marker == null) marker = previewMarker;

            bitmap = Bitmap.createBitmap(cleanFrame.cols(), cleanFrame.rows(),
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cleanFrame, bitmap);

            File file = Storage.captureBuffer(this);
            Storage.deleteQuietly(file);
            Storage.writeJpeg(file, bitmap, 92);

            Intent result = new Intent();
            result.putExtra(EXTRA_IMAGE_PATH, file.getAbsolutePath());
            // The marker corners are in this frame's pixel space; the receiving
            // screen downsamples the JPEG, so it needs the original width to
            // rescale them.
            result.putExtra(EXTRA_SOURCE_WIDTH, cleanFrame.cols());
            if (marker != null) result.putExtra(EXTRA_MARKER, marker);
            setResult(RESULT_OK, result);
            runOnUiThread(this::finish);
        } catch (Exception ex) {
            finishing = false;
            runOnUiThread(() ->
                    Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_LONG).show());
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    // -------------------------------------------------------------- UI controls

    private void toggleAutoCapture() {
        autoCapture = !autoCapture;
        stableFrames = 0;
        updateAutoLabel();
        Toast.makeText(this,
                autoCapture ? R.string.capture_auto_on : R.string.capture_auto_off,
                Toast.LENGTH_SHORT).show();
    }

    private void updateAutoLabel() {
        autoButton.setAlpha(autoCapture ? 1f : 0.55f);
    }

    private void toggleTorch() {
        setTorch(!torchOn);
    }

    /**
     * Driven through {@link CameraManager#setTorchMode} rather than by reaching
     * into the OpenCV camera view's private capture request, so it keeps working
     * across OpenCV releases. Not every device allows it while the camera is open,
     * hence the explicit failure message.
     */
    private void setTorch(boolean on) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;
        try {
            if (torchCameraId == null) torchCameraId = findTorchCamera(manager);
            if (torchCameraId == null) {
                if (on) Toast.makeText(this, R.string.capture_torch_unavailable,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            manager.setTorchMode(torchCameraId, on);
            torchOn = on;
            torchButton.setAlpha(on ? 1f : 0.55f);
        } catch (CameraAccessException | IllegalArgumentException ex) {
            if (on) {
                Toast.makeText(this, R.string.capture_torch_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String findTorchCamera(@NonNull CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(hasFlash)
                    && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return null;
    }
}
