package com.vpcodebuilder.objectmeasurement;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.io.File;

public class CaptureDetectActivity extends Activity implements CvCameraViewListener2 {
    public static final String EXTRA_PICTURE_URI = "pictureUri";
    public static final String EXTRA_SIGN_POS_X = "signPosX";
    public static final String EXTRA_SIGN_POS_Y = "signPosY";
    public static final String EXTRA_SIGN_WIDTH = "signWidth";
    public static final String EXTRA_SIGN_HEIGHT = "signHeight";
    public static final String EXTRA_RULER_WIDTH = "rulerWidth";
    public static final String EXTRA_RULER_HEIGHT = "rulerHeight";

    private CameraView cameraView;
    private SharedPreferences settings;
    private int hueLower;
    private int hueUpper;
    private int saturationLower;
    private int saturationUpper;
    private int valueLower;
    private int valueUpper;
    private Mat outputFrame;

    private SignMeasurement signMeasurement;
    private ImageButton btTakePicture;

    private ProgressDialog progressDialog;
    private boolean isProcessor = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_capture_detect);

        cameraView = (CameraView) findViewById(R.id.capture_view);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);

        settings = getSharedPreferences(SettingActivity.SETTING_PREFERENCE, 0);
        hueLower = settings.getInt(SettingActivity.HUE_LOWER, 0);
        hueUpper = settings.getInt(SettingActivity.HUE_UPPER, 180);
        saturationLower = settings.getInt(SettingActivity.SATURATION_LOWER, 0);
        saturationUpper = settings.getInt(SettingActivity.SATURATION_UPPER, 255);
        valueLower = settings.getInt(SettingActivity.VALUE_LOWER, 0);
        valueUpper = settings.getInt(SettingActivity.VALUE_UPPER, 255);

        btTakePicture = (ImageButton) findViewById(R.id.btTakePicture);
        btTakePicture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat rgbaFrame = inputFrame.rgba();

        outputFrame = rgbaFrame.clone();
        measurementProcess(rgbaFrame, outputFrame);
        rgbaFrame.release();

        return outputFrame;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        outputFrame = new Mat(height, width, CvType.CV_8UC3);
    }

    @Override
    public void onCameraViewStopped() {
        outputFrame.release();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (cameraView != null)
            cameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraView.disableView();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    cameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private void measurementProcess(Mat rgbaFrame, Mat outputFrame) {
        signMeasurement = MeasurementDetector.getSignMeasurement(rgbaFrame,
                hueLower, saturationLower, valueLower,
                hueUpper, saturationUpper, valueUpper);

        if (signMeasurement != null) {
            Core.rectangle(outputFrame,
                    signMeasurement.getBoundary().tl(), signMeasurement.getBoundary().br(),
                    new Scalar(255, 0, 0), 2);
        }
    }

    private void takePicture() {
        Intent result = new Intent();
        String cameraPath = PathHelper.getResourcePath();

        if (cameraPath != null) {
            String fileName = cameraPath + File.separator + "om_camera_buffer.jpg";
            File f = new File(fileName);

            if (f.exists()) f.delete();

            // Save file
            cameraView.takePicture(fileName);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) { }

            Uri pictureUri = Uri.fromFile(new File(fileName));
            result.putExtra(EXTRA_PICTURE_URI, pictureUri);
            setResult(RESULT_OK, result);
        } else {
            setResult(RESULT_CANCELED, result);
        }

        finish();
    }
}
