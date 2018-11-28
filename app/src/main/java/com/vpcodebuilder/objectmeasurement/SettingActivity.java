package com.vpcodebuilder.objectmeasurement;

import java.io.File;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Size;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import eu.geekplace.iesp.ImportExportSharedPreferences;

public class SettingActivity extends Activity {
	public static final String SETTING_PREFERENCE = "App_Config";
	private static final int REQUEST_CAMERA = 1;
	private static final int REQUEST_CHOOSE_DIRECTORY = 2;
	private static final int REQUEST_CHOOSE_FILE = 3;

	public static final String SETTING_REAL_SIGN_WIDTH = "realSignWidth";
	public static final String SETTING_REAL_SIGN_HEIGHT = "realSignHeight";
	public static final String HUE_LOWER = "hueLower";
	public static final String HUE_UPPER = "hueUpper";
	public static final String SATURATION_LOWER = "saturationLower";
	public static final String SATURATION_UPPER = "saturationUpper";
	public static final String VALUE_LOWER = "valueLower";
	public static final String VALUE_UPPER = "valueUpper";

	private SeekBar sbHueLower;
	private SeekBar sbHueUpper;
	private SeekBar sbSaturationLower;
	private SeekBar sbSaturationUpper;
	private SeekBar sbValueLower;
	private SeekBar sbValueUpper;
	private TextView txtSignWidth;
	private TextView txtSignHeight;
	private TextView lblHueRangeValue;
	private TextView lblSaturationRangeValue;
	private TextView lblValueRangeValue;
	private GradientColorView colorRangeView;
	private ImageView imageBefore;
	private ImageView imageAfter;
	private Uri imageCaptureUri = null;
	private Bitmap frame = null;
	private Bitmap preprocessImage = null;
	private MeasurementDetector processor;
	private Point screenSize;

	private SharedPreferences getSettingPreferences() {
		return getSharedPreferences(SETTING_PREFERENCE, 0);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_setting);

		Button btCapture = (Button)findViewById(R.id.btCapture);
		Button btSave = (Button)findViewById(R.id.btSave);
		Button btClose = (Button)findViewById(R.id.btClose);

		sbHueLower = (SeekBar)findViewById(R.id.sbHueLower);
		sbHueUpper = (SeekBar)findViewById(R.id.sbHueUppper);
		sbSaturationLower = (SeekBar)findViewById(R.id.sbSaturationLower);
		sbSaturationUpper = (SeekBar)findViewById(R.id.sbSaturationUppper);
		sbValueLower = (SeekBar)findViewById(R.id.sbValueLower);
		sbValueUpper = (SeekBar)findViewById(R.id.sbValueUppper);

		txtSignWidth = (TextView)findViewById(R.id.txtSignWidth);
		txtSignHeight = (TextView)findViewById(R.id.txtSignHeight);
		lblHueRangeValue = (TextView)findViewById(R.id.lblHueRangeValue);
		lblSaturationRangeValue = (TextView)findViewById(R.id.lblSaturationRangeValue);
		lblValueRangeValue = (TextView)findViewById(R.id.lblValueRangeValue);
		colorRangeView = (GradientColorView)findViewById(R.id.colorRangeView);

		imageBefore = (ImageView)findViewById(R.id.imageBefore);
		imageAfter = (ImageView)findViewById(R.id.imageAfter);

		btCapture.setOnClickListener(btCaptureClick);
		btSave.setOnClickListener(btSaveClick);
		btClose.setOnClickListener(btCloseClick);
		sbHueUpper.setOnSeekBarChangeListener(sbHueUpperChange);
		sbHueLower.setOnSeekBarChangeListener(sbHueLowerChange);
		sbSaturationUpper.setOnSeekBarChangeListener(sbSaturationUpperChange);
		sbSaturationLower.setOnSeekBarChangeListener(sbSaturationLowerChange);
		sbValueUpper.setOnSeekBarChangeListener(sbValueUpperChange);
		sbValueLower.setOnSeekBarChangeListener(sbValueLowerChange);

		screenSize = DeviceHelper.getScreenResolution(this);

		if (savedInstanceState != null) {
			imageCaptureUri = savedInstanceState.getParcelable("imageCaptureUri");

			if (imageCaptureUri != null) this.loadProcessImage();

			return;
		}

		SharedPreferences settingPreferences = getSettingPreferences();
		this.loadSettingPreferences(settingPreferences);
	}

	private void loadSettingPreferences(SharedPreferences settingPreferences) {
		if (settingPreferences == null) return;

		txtSignWidth.setText(String.valueOf(settingPreferences.getFloat(SETTING_REAL_SIGN_WIDTH, 0)));
		txtSignHeight.setText(String.valueOf(settingPreferences.getFloat(SETTING_REAL_SIGN_HEIGHT, 0)));
		sbHueLower.setProgress(settingPreferences.getInt(HUE_LOWER, 0));
		sbHueUpper.setProgress(settingPreferences.getInt(HUE_UPPER, 180));
		sbSaturationLower.setProgress(settingPreferences.getInt(SATURATION_LOWER, 0));
		sbSaturationUpper.setProgress(settingPreferences.getInt(SATURATION_UPPER, 255));
		sbValueLower.setProgress(settingPreferences.getInt(VALUE_LOWER, 0));
		sbValueUpper.setProgress(settingPreferences.getInt(VALUE_UPPER, 255));
		lblHueRangeValue.setText(String.format("[%d, %d]", sbHueLower.getProgress(), sbHueUpper.getProgress()));
		lblSaturationRangeValue.setText(String.format("[%d, %d]", sbSaturationLower.getProgress(), sbSaturationUpper.getProgress()));
		lblValueRangeValue.setText(String.format("[%d, %d]", sbValueLower.getProgress(), sbValueUpper.getProgress()));
		colorRangeView.setRange(sbHueLower.getProgress(), sbHueUpper.getProgress());
	}

	private void saveSettingPreferences(SharedPreferences settingPreferences) {
		if (settingPreferences == null) return;

		SharedPreferences.Editor editor = settingPreferences.edit();

		editor.putFloat(SETTING_REAL_SIGN_WIDTH, Float.parseFloat(txtSignWidth.getText().toString()));
		editor.putFloat(SETTING_REAL_SIGN_HEIGHT, Float.parseFloat(txtSignHeight.getText().toString()));
		editor.putInt(HUE_LOWER, sbHueLower.getProgress());
		editor.putInt(HUE_UPPER, sbHueUpper.getProgress());
		editor.putInt(SATURATION_LOWER, sbSaturationLower.getProgress());
		editor.putInt(SATURATION_UPPER, sbSaturationUpper.getProgress());
		editor.putInt(VALUE_LOWER, sbValueLower.getProgress());
		editor.putInt(VALUE_UPPER, sbValueUpper.getProgress());
		editor.commit();
	}

	private OnClickListener btCaptureClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			openCamera();
		}
	};

	private OnClickListener btSaveClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			SharedPreferences settingPreferences = getSettingPreferences();
			saveSettingPreferences(settingPreferences);

			deleteFileBuffer();
			finish();
		}
	};

	private OnClickListener btCloseClick = new OnClickListener() {
		@Override
		public void onClick(View v) {
			deleteFileBuffer();
			finish();
		}
	};

	private OnSeekBarChangeListener sbHueUpperChange = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar sender, int value, boolean changed) {
			if (value < sbHueLower.getProgress()) sbHueLower.setProgress(value);
			lblHueRangeValue.setText(String.format("[%d, %d]", sbHueLower.getProgress(), sbHueUpper.getProgress()));
			colorRangeView.setRange(sbHueLower.getProgress(), sbHueUpper.getProgress());
			adjustPreprocess();
		}

		@Override
		public void onStartTrackingTouch(SeekBar sender) { }

		@Override
		public void onStopTrackingTouch(SeekBar sender) { }
	};

	private OnSeekBarChangeListener sbHueLowerChange = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar sender, int value, boolean changed) {
			if (value > sbHueUpper.getProgress()) sbHueUpper.setProgress(value);
			lblHueRangeValue.setText(String.format("[%d, %d]", sbHueLower.getProgress(), sbHueUpper.getProgress()));
			colorRangeView.setRange(sbHueLower.getProgress(), sbHueUpper.getProgress());
			adjustPreprocess();
		}

		@Override
		public void onStartTrackingTouch(SeekBar sender) { }

		@Override
		public void onStopTrackingTouch(SeekBar sender) { }
	};

	private OnSeekBarChangeListener sbSaturationUpperChange = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar sender, int value, boolean changed) {
			if (value < sbSaturationLower.getProgress()) sbSaturationLower.setProgress(value);
			lblSaturationRangeValue.setText(String.format("[%d, %d]", sbSaturationLower.getProgress(), sbSaturationUpper.getProgress()));
			adjustPreprocess();
		}

		@Override
		public void onStartTrackingTouch(SeekBar sender) { }

		@Override
		public void onStopTrackingTouch(SeekBar sender) { }
	};

	private OnSeekBarChangeListener sbSaturationLowerChange = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar sender, int value, boolean changed) {
			if (value > sbSaturationUpper.getProgress()) sbSaturationUpper.setProgress(value);
			lblSaturationRangeValue.setText(String.format("[%d, %d]", sbSaturationLower.getProgress(), sbSaturationUpper.getProgress()));
			adjustPreprocess();
		}

		@Override
		public void onStartTrackingTouch(SeekBar sender) { }

		@Override
		public void onStopTrackingTouch(SeekBar sender) { }
	};

	private OnSeekBarChangeListener sbValueUpperChange = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar sender, int value, boolean changed) {
			if (value < sbValueLower.getProgress()) sbValueLower.setProgress(value);
			lblValueRangeValue.setText(String.format("[%d, %d]", sbValueLower.getProgress(), sbValueUpper.getProgress()));
			adjustPreprocess();
		}

		@Override
		public void onStartTrackingTouch(SeekBar sender) { }

		@Override
		public void onStopTrackingTouch(SeekBar sender) { }
	};

	private OnSeekBarChangeListener sbValueLowerChange = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar sender, int value, boolean changed) {
			if (value > sbValueUpper.getProgress()) sbValueUpper.setProgress(value);
			lblValueRangeValue.setText(String.format("[%d, %d]", sbValueLower.getProgress(), sbValueUpper.getProgress()));
			adjustPreprocess();
		}

		@Override
		public void onStartTrackingTouch(SeekBar sender) { }

		@Override
		public void onStopTrackingTouch(SeekBar sender) { }
	};

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putParcelable("imageCaptureUri", imageCaptureUri);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_activity_setting_actions, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent = new Intent(this, DirectoryPickerActivity.class);

		switch (item.getItemId()) {
			case R.id.action_activity_setting_import:
				intent.putExtra(DirectoryPickerActivity.ONLY_DIRS, false);
				intent.putExtra(DirectoryPickerActivity.FILTER_EXTENSION, ".om");
				startActivityForResult(intent, REQUEST_CHOOSE_FILE);
				break;
			case R.id.action_activity_setting_export:
				intent.putExtra(DirectoryPickerActivity.ONLY_DIRS, true);
				startActivityForResult(intent, REQUEST_CHOOSE_DIRECTORY);
				break;
			default:
				break;
		}

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case REQUEST_CAMERA:
					getContentResolver().notifyChange(imageCaptureUri, null);
					this.loadProcessImage();
					break;
				case REQUEST_CHOOSE_DIRECTORY:
				case REQUEST_CHOOSE_FILE:
					Bundle extras = data.getExtras();
					String path = (String)extras.get(DirectoryPickerActivity.CHOSEN_DIRECTORY);
					SharedPreferences settingPreferences = getSettingPreferences();

					if (requestCode == REQUEST_CHOOSE_DIRECTORY) {
						try
						{
							File exportSetting = new File(path + "/configuration.om");
							this.saveSettingPreferences(settingPreferences);
							ImportExportSharedPreferences.exportToFile(settingPreferences, exportSetting, null);
							Toast.makeText(this, "ส่งออกไฟล์เรียบร้อยแล้ว", Toast.LENGTH_LONG).show();
						} catch (Exception ex) {
							Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
						}
					} else {
						try
						{
							File importSetting = new File(path);
							ImportExportSharedPreferences.importFromFile(settingPreferences, importSetting);
							this.loadSettingPreferences(settingPreferences);
							this.saveSettingPreferences(settingPreferences);
							Toast.makeText(this, "นำเข้าไฟล์เรียบร้อยแล้ว", Toast.LENGTH_LONG).show();
						} catch (Exception ex) {
							Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
						}
					}

					break;
			}
		}
	}

	private void openCamera() {
		try {
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			String imageFileName = "setting_preprocess.jpg";
			File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera/" + imageFileName);

			imageCaptureUri = Uri.fromFile(file);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, imageCaptureUri);
			startActivityForResult(Intent.createChooser(intent, "เลือกโปรแกรมถ่ายภาพ"), REQUEST_CAMERA);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(SettingActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void loadProcessImage() {
		try {
			frame = BitmapHelper.getBitmapFromPath(imageCaptureUri.getPath(), 1024);
			processor = new MeasurementDetector(frame, new Size(0, 0));
			preprocessImage = processor.preProcess(
				sbHueLower.getProgress(), sbSaturationLower.getProgress(), sbValueLower.getProgress(),
				sbHueUpper.getProgress(), sbSaturationUpper.getProgress(), sbValueUpper.getProgress());
		} catch (Exception ex) {
			showErrorDialog(ex);
			return;
		}

		int reqWidth = (int)(screenSize.x / 2.0);

		imageBefore.setImageBitmap(BitmapHelper.getScaledBitmap(frame, reqWidth));
		imageAfter.setImageBitmap(BitmapHelper.getScaledBitmap(preprocessImage, reqWidth));
	}

	private void adjustPreprocess() {
		if (imageCaptureUri == null) return;

		try {
			preprocessImage = processor.preProcess(
					sbHueLower.getProgress(), sbSaturationLower.getProgress(), sbValueLower.getProgress(),
					sbHueUpper.getProgress(), sbSaturationUpper.getProgress(), sbValueUpper.getProgress());
		} catch (Exception ex) {
			showErrorDialog(ex);
			return;
		}

		int reqWidth = (int)(screenSize.x / 2.0);

		imageAfter.setImageBitmap(BitmapHelper.getScaledBitmap(preprocessImage, reqWidth));
	}

	private void showErrorDialog(Exception ex) {
		AlertDialog.Builder adlg = new AlertDialog.Builder(this);
		adlg.setTitle("เกิดข้อผิดพลาด");
		adlg.setMessage("พบข้อผิดพลาดในการทำงาน\n" + ex.toString());
		adlg.show();
	}

	private void deleteFileBuffer() {
		if (imageCaptureUri != null) {
			File fDelete = new File(imageCaptureUri.getPath());
			if (fDelete.exists()) fDelete.delete();
		}
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