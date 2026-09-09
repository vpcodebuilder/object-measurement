package com.vpcodebuilder.objectmeasurement;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.vpcodebuilder.objectmeasurement.detect.DetectorConfig;
import com.vpcodebuilder.objectmeasurement.detect.LengthUnit;
import com.vpcodebuilder.objectmeasurement.detect.MarkerDetector;
import com.vpcodebuilder.objectmeasurement.detect.SignMeasurement;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: two measurement slots ("sides"), and the entry points for
 * capturing or importing a photo.
 *
 * <p>The original used {@code ActionBar.NAVIGATION_MODE_TABS} — removed in API 21
 * — driving a support-library {@code ViewPager} whose fragments it reached by
 * reconstructing their internal tags ({@code "android:switcher:" + id + ":" + i}).
 * Both are gone. Two slots do not need a pager at all, so the tab strip now just
 * selects which record this screen renders.</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final int ITEM_ID = 1;
    private static final int SLOT_COUNT = 2;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TabLayout tabs;
    private ScrollView resultScroll;
    private View emptyState;
    private View progress;
    private TextView widthValue;
    private TextView heightValue;
    private TextView note;
    private ImageView resultImage;

    private final DBObjectMeasurement[] slots = new DBObjectMeasurement[SLOT_COUNT];
    private int selectedSlot;
    private LengthUnit unit = LengthUnit.MM;

    private ActivityResultLauncher<Intent> measureLauncher;
    private ActivityResultLauncher<Intent> captureLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tabs = findViewById(R.id.tabs);
        resultScroll = findViewById(R.id.resultScroll);
        emptyState = findViewById(R.id.emptyState);
        progress = findViewById(R.id.progress);
        widthValue = findViewById(R.id.lblWidthValue);
        heightValue = findViewById(R.id.lblHeightValue);
        note = findViewById(R.id.lblNote);
        resultImage = findViewById(R.id.imgResult);

        tabs.addTab(tabs.newTab().setText(R.string.tab_side_a));
        tabs.addTab(tabs.newTab().setText(R.string.tab_side_b));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedSlot = tab.getPosition();
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        ExtendedFloatingActionButton fab = findViewById(R.id.fabCapture);
        fab.setOnClickListener(v -> openCamera());

        registerLaunchers();

        if (savedInstanceState != null) {
            selectedSlot = savedInstanceState.getInt("selectedSlot", 0);
            TabLayout.Tab tab = tabs.getTabAt(selectedSlot);
            if (tab != null) tab.select();
        }

        if (!App.isOpenCvReady()) {
            DialogHelper.alert(this, R.string.dialog_error, getString(R.string.error_no_opencv));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selectedSlot", selectedSlot);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(DetectorConfig.PREFS, MODE_PRIVATE);
        unit = LengthUnit.byName(prefs.getString(DetectorConfig.KEY_UNIT, null), LengthUnit.MM);
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    // ------------------------------------------------------------------ results

    private void registerLaunchers() {
        captureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Intent data = result.getData();
                    openMeasurement(
                            data.getStringExtra(CaptureDetectActivity.EXTRA_IMAGE_PATH),
                            data.getIntExtra(CaptureDetectActivity.EXTRA_SOURCE_WIDTH, 0),
                            data.getParcelableExtra(CaptureDetectActivity.EXTRA_MARKER));
                });

        measureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) reload();
                });

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) importFromGallery(uri);
                });
    }

    private void reload() {
        worker.execute(() -> {
            final DBObjectMeasurement[] loaded = new DBObjectMeasurement[SLOT_COUNT];
            try {
                List<DBObjectMeasurement> rows =
                        DBObjectMeasurement.select(DBManager.get(this), ITEM_ID);
                for (DBObjectMeasurement row : rows) {
                    int index = row.getSeqNo() - 1;
                    if (index >= 0 && index < SLOT_COUNT) loaded[index] = row;
                }
            } catch (Exception ignored) {
                // An unreadable database should show the empty state, not crash.
            }
            main.post(() -> {
                System.arraycopy(loaded, 0, slots, 0, SLOT_COUNT);
                render();
            });
        });
    }

    private void render() {
        DBObjectMeasurement row = slots[selectedSlot];
        boolean hasData = row != null;

        resultScroll.setVisibility(hasData ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasData ? View.GONE : View.VISIBLE);
        if (!hasData) return;

        widthValue.setText(unit.format(row.getObjectWidthMm()));
        heightValue.setText(unit.format(row.getObjectHeightMm()));

        StringBuilder text = new StringBuilder(
                getString(R.string.confidence_format, Math.round(row.getConfidence() * 100)));
        text.append(" · ").append(getString(row.isPerspectiveCorrected()
                ? R.string.perspective_corrected
                : R.string.perspective_uncorrected));
        note.setText(text.toString());

        resultImage.setImageBitmap(row.getImageData());
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.capture_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_camera) {
            openCamera();
            return true;
        }
        if (id == R.id.action_gallery) {
            openGallery();
            return true;
        }
        if (id == R.id.action_setting) {
            startActivity(new Intent(this, SettingActivity.class));
            return true;
        }
        if (id == R.id.action_share) {
            shareResult();
            return true;
        }
        if (id == R.id.action_export) {
            exportResult();
            return true;
        }
        if (id == R.id.action_clear) {
            DialogHelper.confirm(this, R.string.confirm_clear_title,
                    R.string.confirm_clear_body, R.string.confirm_clear_yes,
                    () -> worker.execute(() -> {
                        DBObjectMeasurement.deleteAll(DBManager.get(this));
                        main.post(this::reload);
                    }));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ sources

    private void openCamera() {
        captureLauncher.launch(new Intent(this, CaptureDetectActivity.class));
    }

    private void openGallery() {
        galleryLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /**
     * Copies the chosen photo into the app's own storage and runs detection there.
     *
     * <p>The previous implementation queried {@code MediaStore.Images.Media.DATA}
     * for a filesystem path and copied the file with {@code java.io} — a path that
     * has returned null since scoped storage landed, and never worked at all for
     * cloud-backed or downloaded images. The photo picker hands back a URI that
     * the content resolver can always open.</p>
     */
    private void importFromGallery(Uri uri) {
        setBusy(true);
        SharedPreferences prefs = getSharedPreferences(DetectorConfig.PREFS, MODE_PRIVATE);
        DetectorConfig config = DetectorConfig.from(prefs);

        worker.execute(() -> {
            Bitmap bitmap = null;
            SignMeasurement marker = null;
            File destination = null;
            Exception failure = null;

            try {
                bitmap = BitmapHelper.decode(this, uri, App.WORK_IMAGE_WIDTH);
                marker = MarkerDetector.detect(bitmap, config);
                destination = Storage.captureBuffer(this);
                Storage.deleteQuietly(destination);
                Storage.writeJpeg(destination, bitmap, 92);
            } catch (Exception ex) {
                failure = ex;
            }

            final int width = bitmap != null ? bitmap.getWidth() : 0;
            final SignMeasurement finalMarker = marker;
            final File finalDestination = destination;
            final Exception finalFailure = failure;

            main.post(() -> {
                setBusy(false);
                if (finalFailure != null || finalDestination == null) {
                    DialogHelper.error(this, R.string.error_load_image, finalFailure);
                    return;
                }
                if (finalMarker == null) {
                    // Still let the user through: they can re-tune detection and
                    // the measurement screen explains what is missing.
                    Toast.makeText(this, R.string.error_detect_failed, Toast.LENGTH_LONG).show();
                }
                openMeasurement(finalDestination.getAbsolutePath(), width, finalMarker);
            });
        });
    }

    private void openMeasurement(String imagePath, int sourceWidth, SignMeasurement marker) {
        if (imagePath == null) return;
        Intent intent = new Intent(this, MeasurementActivity.class);
        intent.putExtra(MeasurementActivity.EXTRA_IMAGE_PATH, imagePath);
        intent.putExtra(MeasurementActivity.EXTRA_SOURCE_WIDTH, sourceWidth);
        intent.putExtra(MeasurementActivity.EXTRA_MARKER, marker);
        intent.putExtra(MeasurementActivity.EXTRA_ITEM_ID, ITEM_ID);
        intent.putExtra(MeasurementActivity.EXTRA_SEQ_NO, selectedSlot + 1);
        measureLauncher.launch(intent);
    }

    // ------------------------------------------------------------------ sharing

    private void shareResult() {
        DBObjectMeasurement row = slots[selectedSlot];
        if (row == null || row.getImageData() == null) {
            Toast.makeText(this, R.string.nothing_to_share, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File file = Storage.newCaptureFile(this, "measurement");
            Storage.writeJpeg(file, row.getImageData(), 92);
            Uri uri = Storage.shareUri(this, file);

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/jpeg");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_TEXT, unit.format(row.getObjectWidthMm())
                    + " × " + unit.format(row.getObjectHeightMm()));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.action_share)));
        } catch (Exception ex) {
            DialogHelper.error(this, ex.toString());
        }
    }

    private void exportResult() {
        DBObjectMeasurement row = slots[selectedSlot];
        if (row == null || row.getImageData() == null) {
            Toast.makeText(this, R.string.nothing_to_share, Toast.LENGTH_SHORT).show();
            return;
        }
        worker.execute(() -> {
            Exception failure = null;
            try {
                Storage.exportToGallery(this, row.getImageData(),
                        "measurement_" + System.currentTimeMillis() + ".jpg");
            } catch (Exception ex) {
                failure = ex;
            }
            final Exception finalFailure = failure;
            main.post(() -> {
                if (finalFailure == null) {
                    Toast.makeText(this, R.string.saved_to_gallery, Toast.LENGTH_SHORT).show();
                } else {
                    DialogHelper.error(this, finalFailure.toString());
                }
            });
        });
    }
}
