package com.vpcodebuilder.objectmeasurement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

public class MainActivity extends FragmentActivity implements ActionBar.TabListener {
    final static int CAMERA_REQUEST = 1;
    final static int PICK_IMAGE_REQUEST = 2;
    final static int PROCESSING_REQUEST = 3;
    final static String LOAD_DATABASE_FIRST = "LOAD_DATABASE_FIRST";
    final static String EXTRA_IMAGE_PATH = "imagePath";
    final static String EXTRA_ITEM_ID = "itemId";
    final static String EXTRA_SEQ_NO = "seqNo";
    final static String EXTRA_IS_UPDATE_MODE = "isUpdateMode";
    final static Integer FRAME_REQUEST_WIDTH = 1024;

    private ViewPager viewPager;
    private ActionBar actionBar;
    private ArrayList<String> tabs = new ArrayList<>();
    private DBObjectMeasurement[] oDBObjectMeasurements;
    private Uri imageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tabs.add("Photo side A");
        tabs.add("Photo side B");

        viewPager = findViewById(R.id.pager);
        TabsPagerAdapter tabPagerAdapter = new TabsPagerAdapter(this.getSupportFragmentManager() , tabs);
        viewPager.setAdapter(tabPagerAdapter);

        actionBar = this.getActionBar();
        actionBar.setHomeButtonEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        for (String tabName : tabs) {
            actionBar.addTab(actionBar.newTab().setText(tabName).setTabListener(this));
        }

        getActionBar().setCustomView(R.layout.tabbar_capture);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) { }

            @Override
            public void onPageScrollStateChanged(int arg0) { }
        });

        Boolean isClearDatabase = true;

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(LOAD_DATABASE_FIRST)) {
                isClearDatabase = false;
            }

            this.refreshData();
        }

        if (isClearDatabase) {
            DBManager dbManager = new DBManager(MainActivity.this);

            try {
                DBObjectMeasurement.delete(dbManager);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(LOAD_DATABASE_FIRST, true);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) { }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction ft) {
        viewPager.setCurrentItem(tab.getPosition());
        this.loadData();
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) { }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.capture_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_camera) {
            //this.openCamera();
            this.openCaptureDetect();
            return true;
        }

        if (item.getItemId() == R.id.action_gallery) {
            this.openGallery();
            return true;
        }

        if (item.getItemId() == R.id.action_setting) {
            Intent intent = new Intent(MainActivity.this, SettingActivity.class);
            startActivity(intent);
            return true;
        }

        if (item.getItemId() == R.id.action_back) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAMERA_REQUEST:
                    Bundle bundle = data.getExtras();
                    imageUri = (Uri)bundle.get(CaptureDetectActivity.EXTRA_PICTURE_URI);
                    this.measurementImage();
                    break;
                case PICK_IMAGE_REQUEST:
                    if (data != null) {
                        String errorMessage = "Cannot import photo file it's copy corrupt.";
                        Uri dataUri = PathHelper.getFullPathFromUri(data.getData(), this.getContentResolver());
                        String cameraPath = PathHelper.getResourcePath();
                        String fileName = cameraPath + File.separator + "om_camera_buffer.jpg";
                        File dst = new File(fileName);

                        if (dst.exists()) dst.delete();

                        if (dataUri.getPath() != null) {
                            File galleryFile = new File(dataUri.getPath());

                            try {
                                PathHelper.copy(galleryFile, dst);
                            } catch (IOException ex) {
                                DialogHelper.showDialog(this, "Error", errorMessage);
                                break;
                            }

                            imageUri = Uri.fromFile(dst);
                            this.measurementImage();
                        } else {
                            DialogHelper.showDialog(this, "Error", errorMessage);
                            break;
                        }
                    } else {
                        DialogHelper.showDialog(this, "Error", "Cannot import photo file it's has no file.");
                    }

                    break;
                case PROCESSING_REQUEST:
                    this.refreshData();
                    this.loadData();
                    break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            exitProgram();
        }

        return super.onKeyDown(keyCode, event);
    }

    private TabCaptureFragment getCurrentTabCapture() {
        int index = viewPager.getCurrentItem();
        String tag = "android:switcher:" + viewPager.getId() + ":" + index;
        TabCaptureFragment currentTab = ((TabCaptureFragment)this.getSupportFragmentManager().findFragmentByTag(tag));
        return currentTab;
    }

    public DBObjectMeasurement getData(int position) {
        if (oDBObjectMeasurements != null) {
            return oDBObjectMeasurements[position];
        } else {
            return null;
        }
    }

    private void refreshData() {
        DBManager dbManager = new DBManager(this);
        List<DBObjectMeasurement> oDBObjectMeasurementList;

        try {
            oDBObjectMeasurementList = DBObjectMeasurement.select(dbManager, 1);
        } catch (Exception ex) {
            this.showErrorDialog(ex);
            return;
        }

        oDBObjectMeasurements = new DBObjectMeasurement[2];

        if (oDBObjectMeasurementList.size() == 1) {
            if (oDBObjectMeasurementList.get(0).getSeqNo() == 1) {
                oDBObjectMeasurements[0] = oDBObjectMeasurementList.get(0);
            } else {
                oDBObjectMeasurements[1] = oDBObjectMeasurementList.get(0);
            }
        } else if (oDBObjectMeasurementList.size() == 2) {
            oDBObjectMeasurements[0] = oDBObjectMeasurementList.get(0);
            oDBObjectMeasurements[1] = oDBObjectMeasurementList.get(1);
        }
    }

    private void loadData() {
        if (oDBObjectMeasurements == null) return;

        DBObjectMeasurement oDBObjectMeasurement = oDBObjectMeasurements[viewPager.getCurrentItem()];

        if (oDBObjectMeasurement != null) {
            TabCaptureFragment currentTabCapture = this.getCurrentTabCapture();

            if (currentTabCapture != null) {
                currentTabCapture.lblObjectMeterWidthValue.setText(String.format(Locale.ENGLISH, "%.2f m.", oDBObjectMeasurement.getObjectMeterWidth()));
                currentTabCapture.lblObjectMeterHeightValue.setText(String.format(Locale.ENGLISH, "%.2f m.", oDBObjectMeasurement.getObjectMeterHeight()));
                currentTabCapture.imgResult.setImageBitmap(oDBObjectMeasurement.getImageData());
            }
        }
    }

    private void openCaptureDetect() {
        Intent intent = new Intent(this, CaptureDetectActivity.class);
        startActivityForResult(intent, CAMERA_REQUEST);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(Intent.createChooser(intent, ""), PICK_IMAGE_REQUEST);
    }

    private void measurementImage() {
        SharedPreferences settings = getSharedPreferences(SettingActivity.SETTING_PREFERENCE, 0);
        int hueLower = settings.getInt(SettingActivity.HUE_LOWER, 0);
        int hueUpper = settings.getInt(SettingActivity.HUE_UPPER, 180);
        int saturationLower = settings.getInt(SettingActivity.SATURATION_LOWER, 0);
        int saturationUpper = settings.getInt(SettingActivity.SATURATION_UPPER, 255);
        int valueLower = settings.getInt(SettingActivity.VALUE_LOWER, 0);
        int valueUpper = settings.getInt(SettingActivity.VALUE_UPPER, 255);

        Bitmap bmp;
        File pictureFile = new File(imageUri.getPath());
        PathHelper.updateMediaScanner(this, pictureFile);

        try {
            bmp = BitmapHelper.getBitmapFromPath(imageUri.getPath(), FRAME_REQUEST_WIDTH);
        } catch (Exception ex) {
            showErrorDialog(ex);
            return;
        }

        if (bmp != null) {
            if (bmp.getHeight() > bmp.getWidth()) {
                //bmp = BitmapHelper.rotateBitmap(bmp, 90);
            }

            SignMeasurement oSignMeasurement = MeasurementDetector.getSignMeasurement(bmp,
                    hueLower, saturationLower, valueLower,
                    hueUpper, saturationUpper, valueUpper);

            if (oSignMeasurement != null) {
                this.measurementProcess(
                        oSignMeasurement.getBoundary().x,
                        oSignMeasurement.getBoundary().y,
                        oSignMeasurement.getBoundary().width,
                        oSignMeasurement.getBoundary().height,
                        oSignMeasurement.getRulerWidth(),
                        oSignMeasurement.getRulerHeight());
            } else {
                DialogHelper.showDialog(this, "Not found", "The sign cannot be found. Try again.");
            }
        } else {
            DialogHelper.showDialog(this, "Error", "Cannot import photo file it's file has corrupted.");
        }
    }

    private void measurementProcess(int x, int y, int width, int height, double rulerWidth, double rulerHeight) {
        try {
            int position = viewPager.getCurrentItem();

            if (oDBObjectMeasurements == null) {
                this.refreshData();
            }

            DBObjectMeasurement oDBObjectMeasurement = oDBObjectMeasurements[position];
            boolean isUpdateMode = (oDBObjectMeasurement != null);
            Intent intent = new Intent(this, MeasurementActivity.class);

            intent.putExtra(EXTRA_IMAGE_PATH, imageUri.getPath());
            intent.putExtra(CaptureDetectActivity.EXTRA_SIGN_POS_X, x);
            intent.putExtra(CaptureDetectActivity.EXTRA_SIGN_POS_Y, y);
            intent.putExtra(CaptureDetectActivity.EXTRA_SIGN_WIDTH, width);
            intent.putExtra(CaptureDetectActivity.EXTRA_SIGN_HEIGHT, height);
            intent.putExtra(CaptureDetectActivity.EXTRA_RULER_WIDTH, rulerWidth);
            intent.putExtra(CaptureDetectActivity.EXTRA_RULER_HEIGHT, rulerHeight);

            intent.putExtra(EXTRA_ITEM_ID, 1);
            intent.putExtra(EXTRA_SEQ_NO, position + 1);
            intent.putExtra(EXTRA_IS_UPDATE_MODE, isUpdateMode);

            startActivityForResult(intent, PROCESSING_REQUEST);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exitProgram() {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        int pid = android.os.Process.myPid();
                        android.os.Process.killProcess(pid);
                        System.exit(0);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Exit program");
        alert.setMessage("Are you sure you want to exit?");
        alert.setNegativeButton("No", dialogClickListener);
        alert.setPositiveButton("Yes", dialogClickListener);
        alert.show();
    }

    private void showErrorDialog(Exception ex) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Error");
        alert.setMessage("Error: \n" + ex.toString());
        alert.show();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };
}