package com.vpcodebuilder.objectmeasurement;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.vpcodebuilder.objectmeasurement.detect.DetectorConfig;
import com.vpcodebuilder.objectmeasurement.detect.LengthUnit;
import com.vpcodebuilder.objectmeasurement.detect.MarkerDetector;
import com.vpcodebuilder.objectmeasurement.detect.MeasuredSize;
import com.vpcodebuilder.objectmeasurement.detect.ObjectSnapper;
import com.vpcodebuilder.objectmeasurement.detect.SignMeasurement;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Place a box around the object and read off its size.
 *
 * <p>The old screen had an explicit "Process" action that spun up a thread, threw
 * a {@code ProgressDialog} over the UI and multiplied the box's pixel size by a
 * single mm/px scalar. With the measurement reduced to a four-point perspective
 * transform, that whole round trip costs microseconds — so the size is now shown
 * live as the box is dragged, and there is nothing to wait for. The heavy work
 * that remains (decoding the photo, running detection when the caller did not
 * supply a marker, auto-fit) runs off the main thread with a small inline
 * spinner instead of a modal dialog.</p>
 */
public class MeasurementActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "imagePath";
    public static final String EXTRA_SOURCE_WIDTH = "sourceWidth";
    public static final String EXTRA_MARKER = "marker";
    public static final String EXTRA_ITEM_ID = "itemId";
    public static final String EXTRA_SEQ_NO = "seqNo";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MeasurementGraphicView graphic;
    private TextView sizeLabel;
    private TextView noteLabel;
    private CircularProgressIndicator progress;

    private DetectorConfig config;
    private LengthUnit unit = LengthUnit.MM;

    private String imagePath;
    private int sourceWidth;
    private SignMeasurement marker;
    private Bitmap image;
    private int itemId;
    private int seqNo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> confirmDiscard());

        graphic = findViewById(R.id.graphicMeasurement);
        sizeLabel = findViewById(R.id.lblSize);
        noteLabel = findViewById(R.id.lblNote);
        progress = findViewById(R.id.progress);

        SharedPreferences prefs = getSharedPreferences(DetectorConfig.PREFS, MODE_PRIVATE);
        config = DetectorConfig.from(prefs);
        unit = LengthUnit.byName(prefs.getString(DetectorConfig.KEY_UNIT, null), LengthUnit.MM);
        graphic.setUnit(unit);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            finish();
            return;
        }
        imagePath = extras.getString(EXTRA_IMAGE_PATH);
        if (imagePath == null) {
            DialogHelper.error(this, getString(R.string.error_load_image));
            finish();
            return;
        }
        sourceWidth = extras.getInt(EXTRA_SOURCE_WIDTH, 0);
        marker = extras.getParcelable(EXTRA_MARKER);
        itemId = extras.getInt(EXTRA_ITEM_ID, 1);
        seqNo = extras.getInt(EXTRA_SEQ_NO, 1);

        graphic.setOnMeasureListener(this::showSize);

        final Bundle restore = savedInstanceState;
        loadImage(() -> {
            if (restore != null) graphic.restoreState(restore);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(@Nullable Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outState != null) graphic.saveState(outState);
    }

    // ------------------------------------------------------------------ loading

    private void loadImage(Runnable afterLoad) {
        setBusy(true);
        worker.execute(() -> {
            Bitmap decoded = null;
            SignMeasurement resolved = marker;
            Exception failure = null;

            try {
                decoded = BitmapHelper.decode(this,
                        android.net.Uri.fromFile(new File(imagePath)), App.WORK_IMAGE_WIDTH);

                if (resolved != null && sourceWidth > 0 && decoded.getWidth() != sourceWidth) {
                    // Corners were found in the capture frame; move them into the
                    // coordinate space of the bitmap we actually decoded.
                    resolved = resolved.scaled(decoded.getWidth() / (double) sourceWidth);
                }
                if (resolved == null) {
                    resolved = MarkerDetector.detect(decoded, config);
                } else if (!resolved.hasMarkerSize() && config.hasMarkerSize()) {
                    resolved = resolved.withMarkerSize(config.markerWidthMm, config.markerHeightMm);
                }
            } catch (Exception ex) {
                failure = ex;
            }

            final Bitmap finalBitmap = decoded;
            final SignMeasurement finalMarker = resolved;
            final Exception finalFailure = failure;

            main.post(() -> {
                setBusy(false);
                if (finalFailure != null || finalBitmap == null) {
                    DialogHelper.error(this, R.string.error_load_image, finalFailure);
                    return;
                }
                image = finalBitmap;
                marker = finalMarker;
                graphic.setImage(image, marker);
                updateNote();
                invalidateOptionsMenu();
                if (afterLoad != null) afterLoad.run();
            });
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // -------------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.measurement_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean measurable = marker != null && marker.hasMarkerSize();
        MenuItem save = menu.findItem(R.id.action_save);
        if (save != null) save.setEnabled(measurable);
        MenuItem autofit = menu.findItem(R.id.action_autofit);
        if (autofit != null) autofit.setEnabled(image != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_autofit) {
            autoFit();
            return true;
        }
        if (id == R.id.action_save) {
            save();
            return true;
        }
        if (id == R.id.action_reset) {
            resetBox();
            return true;
        }
        if (id == R.id.action_cancel) {
            confirmDiscard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ actions

    /** Shrink the selection onto the object inside it. */
    private void autoFit() {
        if (image == null) return;
        final Rect current = graphic.getCropInImage();
        setBusy(true);
        worker.execute(() -> {
            final Rect snapped = ObjectSnapper.snap(image, current);
            main.post(() -> {
                setBusy(false);
                graphic.setCropInImage(snapped);
            });
        });
    }

    private void resetBox() {
        if (image == null) return;
        int w = image.getWidth();
        int h = image.getHeight();
        graphic.setCropInImage(new Rect(
                (int) (w * 0.275), (int) (h * 0.325),
                (int) (w * 0.725), (int) (h * 0.675)));
    }

    private void save() {
        MeasuredSize size = graphic.getMeasured();
        if (size.isEmpty()) {
            DialogHelper.alert(this, R.string.dialog_error,
                    getString(marker == null
                            ? R.string.measure_no_marker
                            : R.string.measure_no_marker_size));
            return;
        }

        final DBObjectMeasurement row = new DBObjectMeasurement();
        row.setItemId(itemId);
        row.setSeqNo(seqNo);
        row.setObjectWidthMm(size.widthMm);
        row.setObjectHeightMm(size.heightMm);
        row.setConfidence(marker != null ? marker.confidence() : 0f);
        row.setPerspectiveCorrected(size.perspectiveCorrected);
        // Rendering the annotated export and JPEG-encoding it for the blob is
        // tens of milliseconds of bitmap work; it does not belong on the main
        // thread, so the row is completed and written on the worker.
        row.setImageData(graphic.renderResult());

        setBusy(true);
        worker.execute(() -> {
            Exception failure = null;
            try {
                DBObjectMeasurement.save(DBManager.get(this), row);
            } catch (Exception ex) {
                failure = ex;
            }
            final Exception finalFailure = failure;
            main.post(() -> {
                setBusy(false);
                if (finalFailure != null) {
                    DialogHelper.error(this, finalFailure.toString());
                    return;
                }
                Storage.deleteQuietly(new File(imagePath));
                setResult(RESULT_OK, new Intent());
                finish();
            });
        });
    }

    private void confirmDiscard() {
        DialogHelper.confirm(this,
                R.string.confirm_discard_title,
                R.string.confirm_discard_body,
                R.string.confirm_discard_yes,
                () -> {
                    Storage.deleteQuietly(new File(imagePath));
                    finish();
                });
    }

    // ------------------------------------------------------------------ display

    private void showSize(MeasuredSize size) {
        if (size == null || size.isEmpty()) {
            sizeLabel.setText(R.string.measure_prompt);
        } else {
            sizeLabel.setText(size.format(unit));
        }
    }

    private void updateNote() {
        if (marker == null) {
            noteLabel.setText(R.string.measure_no_marker);
            return;
        }
        if (!marker.hasMarkerSize()) {
            noteLabel.setText(R.string.measure_no_marker_size);
            return;
        }

        StringBuilder note = new StringBuilder();
        note.append(getString(R.string.confidence_format,
                Math.round(marker.confidence() * 100)));
        // A strongly trapezoidal marker means a steep viewing angle: the
        // homography still corrects it, but small corner errors are amplified.
        if (marker.perspectiveSkew() > 1.25) {
            note.append(" · ").append(getString(R.string.measure_skew_warning));
        }
        noteLabel.setText(note.toString());
    }
}
