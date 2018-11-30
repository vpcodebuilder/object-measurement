package com.vpcodebuilder.objectmeasurement;

import java.io.File;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Rect;
import org.opencv.core.Size;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;

public class MeasurementActivity extends Activity {
	private SharedPreferences settings;
	private MenuItem mnuProcess;
	private MenuItem mnuSave;
	private MeasurementGraphicView graphicMeasurement;
	private MeasurementDetector processor;
	private Uri imageUri;
	private SignMeasurement signMeasurement;
	private int itemId;
	private int seqNo;
	private boolean isUpdateMode;
	private ProgressDialog progressDialog;
	private boolean isProcessor = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_measurement);
		settings = getSharedPreferences(SettingActivity.SETTING_PREFERENCE, 0);
		graphicMeasurement = (MeasurementGraphicView)findViewById(R.id.graphicMeasurement);

		Bundle bundle = this.getIntent().getExtras();
		imageUri = Uri.parse(bundle.getString(MainActivity.EXTRA_IMAGE_PATH));
		signMeasurement = new SignMeasurement(new Rect(
				bundle.getInt(CaptureDetectActivity.EXTRA_SIGN_POS_X),
				bundle.getInt(CaptureDetectActivity.EXTRA_SIGN_POS_Y),
				bundle.getInt(CaptureDetectActivity.EXTRA_SIGN_WIDTH),
				bundle.getInt(CaptureDetectActivity.EXTRA_SIGN_HEIGHT)),
				bundle.getDouble(CaptureDetectActivity.EXTRA_RULER_WIDTH),
				bundle.getDouble(CaptureDetectActivity.EXTRA_RULER_HEIGHT)
		);
		itemId = bundle.getInt(MainActivity.EXTRA_ITEM_ID);
		seqNo = bundle.getInt(MainActivity.EXTRA_SEQ_NO);
		isUpdateMode = bundle.getBoolean(MainActivity.EXTRA_IS_UPDATE_MODE);

		if (savedInstanceState == null) {
			try {
				Bitmap bmp = BitmapHelper.getBitmapFromPath(imageUri.getPath(), MainActivity.FRAME_REQUEST_WIDTH);

				if (bmp.getHeight() > bmp.getWidth()) {
					//bmp = BitmapHelper.rotateBitmap(bmp, 90);
				}

				graphicMeasurement.setBackgroundImage(bmp, signMeasurement);
			} catch (Exception ex) {
				this.showErrorDialog(ex);
			}
		} else {
			isProcessor = savedInstanceState.getBoolean("isProcessor");
			graphicMeasurement.getInstanceState(savedInstanceState);

			if (isProcessor) this.process();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.measurement_actions, menu);
		mnuProcess = (MenuItem)menu.findItem(R.id.action_process);
		mnuSave = (MenuItem)menu.findItem(R.id.action_save);

		if (!graphicMeasurement.hasLockMeasurement()) {
			mnuProcess.setVisible(true);
			mnuSave.setVisible(false);
		} else {
			mnuProcess.setVisible(false);
			mnuSave.setVisible(true);
		}

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_process) {
			this.process();
			return true;
		}

		if (item.getItemId() == R.id.action_save) {
			this.save();
			return true;
		}

		if (item.getItemId() == R.id.action_cancel) {
			this.cancel();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void process() {
		isProcessor = true;

		if (progressDialog != null) {
			progressDialog.dismiss();
			progressDialog = null;
		}

		progressDialog = ProgressDialog.show(MeasurementActivity.this, "Please wait", "Processing...", true);
		final Handler uiHandler = new Handler();

		new Thread(new Runnable() {
			@Override
			public void run() {
			processor = new MeasurementDetector(graphicMeasurement.getCropImage(),
						new Size(settings.getFloat(SettingActivity.SETTING_REAL_SIGN_WIDTH, 0),
								settings.getFloat(SettingActivity.SETTING_REAL_SIGN_HEIGHT, 0)));

			try {
				processor.process(graphicMeasurement.getSignMeasurement());
			} catch (final Exception ex) {
				uiHandler.post(new Runnable() {
					@Override
					public void run() {
						progressDialog.dismiss();
						showErrorDialog(ex);
					}
				});

				return;
			}

			uiHandler.post(new Runnable() {
				@Override
				public void run() {
				try {
					progressDialog.dismiss();

					Size objectMilimeterSize = processor.getObjectMilimeterSize();
					android.graphics.Rect boundary = SignMeasurement.ConvertToGraphicRectType(
							processor.getSignMeasurement().getBoundary());

					graphicMeasurement.setObjectMeasure(
							objectMilimeterSize.width,
							objectMilimeterSize.height,
							boundary);
					mnuProcess.setVisible(false);
					mnuSave.setVisible(true);
					isProcessor = false;
				} catch (final Exception ex) {	}
				}
			});
			}
		}).start();
	}

	private void save() {
		DBObjectMeasurement oDBObjectMeasurement = new DBObjectMeasurement();

		try {
			oDBObjectMeasurement.setItemId(this.itemId);
			oDBObjectMeasurement.setSeqNo(this.seqNo);
			oDBObjectMeasurement.setObjectMeterWidth(graphicMeasurement.getObjectMeterWidth());
			oDBObjectMeasurement.setObjectMeterHeight(graphicMeasurement.getObjectMeterHeight());
			oDBObjectMeasurement.setImageData(graphicMeasurement.getImageResult());

			DBManager dbManager = new DBManager(MeasurementActivity.this);

			if (!isUpdateMode) {
				DBObjectMeasurement.insert(dbManager, oDBObjectMeasurement);
			} else {
				DBObjectMeasurement.update(dbManager, oDBObjectMeasurement);
			}
		} catch (Exception ex) {
			this.showErrorDialog(ex);
			return;
		}

		this.deleteCameraBufferFile();

		Intent returnIntent = new Intent(this, MainActivity.class);
		setResult(RESULT_OK, returnIntent);
		finish();
	}

	private void cancel() {
		// ถามการยืนยันการยกเลิก
		AlertDialog.Builder adlg = new AlertDialog.Builder(this);
		String message = "Are you sure you want to cancel?";

		if (graphicMeasurement.hasLockMeasurement())
			message = "Are you sure you want back to original?";

		adlg.setTitle("Confirm cancel");
		adlg.setMessage(message);
		adlg.setNegativeButton("No", null);
		adlg.setPositiveButton("Yes", new AlertDialog.OnClickListener() {
			public void onClick(DialogInterface dialog, int arg1) {
				if (graphicMeasurement.hasLockMeasurement()) {
					graphicMeasurement.undoProcess();
					mnuProcess.setVisible(true);
					mnuSave.setVisible(false);
				} else {
					deleteCameraBufferFile();
					finish();
				}
			}
		});

		adlg.show();
	}

	private void deleteCameraBufferFile() {
		File cameraBufferFile = new File(imageUri.getPath());
		cameraBufferFile.delete();
	}

	private void showErrorDialog(Exception ex) {
		AlertDialog.Builder adlg = new AlertDialog.Builder(this);
		adlg.setTitle("Error");
		adlg.setMessage("Operation error\n" + ex.toString());
		adlg.show();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		graphicMeasurement.saveInstanceState(savedInstanceState);
		savedInstanceState.putBoolean("isProcessor", isProcessor);
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