package com.vpcodebuilder.objectmeasurement;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.vpcodebuilder.objectmeasurement.detect.DetectorConfig;
import com.vpcodebuilder.objectmeasurement.detect.LengthUnit;
import com.vpcodebuilder.objectmeasurement.detect.MarkerDetector;
import com.vpcodebuilder.objectmeasurement.detect.MarkerPreset;
import com.vpcodebuilder.objectmeasurement.detect.SignMeasurement;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Marker dimensions, display unit, and detector tuning.
 *
 * <p>The headline change is that tuning is no longer a manual search through six
 * sliders: load a photo of your marker, tap it, and the HSV window is derived
 * from the pixels under your finger. The sliders remain for fine adjustment, and
 * the right-hand preview shows exactly what the detector sees, with the marker it
 * would pick outlined in green.</p>
 *
 * <p>Preview refreshes are debounced and run off the main thread. Previously each
 * slider tick synchronously re-ran the whole pipeline — including a full pixel
 * sort — on the UI thread.</p>
 */
public class SettingActivity extends AppCompatActivity {

    private static final long PREVIEW_DEBOUNCE_MS = 180;
    private static final int SAMPLE_RADIUS_PX = 12;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable previewTask = this::refreshPreview;

    private Spinner presetSpinner;
    private Spinner unitSpinner;
    private Spinner modeSpinner;
    private TextInputEditText widthField;
    private TextInputEditText heightField;
    private Slider hueLower;
    private Slider hueUpper;
    private RangeSlider saturation;
    private RangeSlider value;
    private TextView hueLabel;
    private TextView saturationLabel;
    private TextView valueLabel;
    private TextView detectStatus;
    private GradientColorView colorRange;
    private ImageView beforeImage;
    private ImageView afterImage;

    private DetectorConfig config;
    private Bitmap sample;
    private boolean suppressCallbacks;

    private ActivityResultLauncher<PickVisualMediaRequest> photoLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        presetSpinner = findViewById(R.id.spPreset);
        unitSpinner = findViewById(R.id.spUnit);
        modeSpinner = findViewById(R.id.spMode);
        widthField = findViewById(R.id.txtSignWidth);
        heightField = findViewById(R.id.txtSignHeight);
        hueLower = findViewById(R.id.sbHueLower);
        hueUpper = findViewById(R.id.sbHueUpper);
        saturation = findViewById(R.id.sbSaturation);
        value = findViewById(R.id.sbValue);
        hueLabel = findViewById(R.id.lblHueRangeValue);
        saturationLabel = findViewById(R.id.lblSaturationRangeValue);
        valueLabel = findViewById(R.id.lblValueRangeValue);
        detectStatus = findViewById(R.id.lblDetectStatus);
        colorRange = findViewById(R.id.colorRangeView);
        beforeImage = findViewById(R.id.imageBefore);
        afterImage = findViewById(R.id.imageAfter);

        config = DetectorConfig.from(getSettings());

        setupSpinners();
        bindConfigToViews();
        setupListeners();
        registerLaunchers();

        findViewById(R.id.btCapture).setOnClickListener(v -> pickSamplePhoto());
        findViewById(R.id.btResetColor).setOnClickListener(v -> resetColorRange());
        ((MaterialButton) findViewById(R.id.btSave)).setOnClickListener(v -> saveAndClose());
        ((MaterialButton) findViewById(R.id.btClose)).setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(previewTask);
        worker.shutdownNow();
    }

    private SharedPreferences getSettings() {
        return getSharedPreferences(DetectorConfig.PREFS, MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ set-up

    private void setupSpinners() {
        presetSpinner.setAdapter(adapterFor(MarkerPreset.values()));
        unitSpinner.setAdapter(adapterFor(LengthUnit.values()));

        List<String> modes = new ArrayList<>();
        modes.add(getString(R.string.settings_mode_auto));
        modes.add(getString(R.string.settings_mode_color));
        modes.add(getString(R.string.settings_mode_edge));
        modeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, modes));
    }

    private <T> ArrayAdapter<T> adapterFor(T[] values) {
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    }

    private void bindConfigToViews() {
        suppressCallbacks = true;
        try {
            widthField.setText(config.markerWidthMm > 0
                    ? trim(config.markerWidthMm) : "");
            heightField.setText(config.markerHeightMm > 0
                    ? trim(config.markerHeightMm) : "");

            presetSpinner.setSelection(MarkerPreset
                    .match(config.markerWidthMm, config.markerHeightMm).ordinal());

            LengthUnit unit = LengthUnit.byName(
                    getSettings().getString(DetectorConfig.KEY_UNIT, null), LengthUnit.MM);
            unitSpinner.setSelection(unit.ordinal());

            modeSpinner.setSelection(modeToSpinner(config.mode));

            hueLower.setValue(clamp(config.hueLower, 0, 179));
            hueUpper.setValue(clamp(config.hueUpper, 0, 179));
            setRange(saturation, config.satLower, config.satUpper);
            setRange(value, config.valLower, config.valUpper);
        } finally {
            suppressCallbacks = false;
        }
        updateRangeLabels();
    }

    /** RangeSlider requires ascending thumbs; a stale profile may not have them. */
    private void setRange(RangeSlider slider, int low, int high) {
        float lo = clamp(Math.min(low, high), 0, 255);
        float hi = clamp(Math.max(low, high), 0, 255);
        if (hi <= lo) hi = Math.min(255f, lo + 1f);
        slider.setValues(lo, hi);
    }

    private static float clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String trim(double v) {
        return v == Math.rint(v)
                ? String.valueOf((long) v)
                : String.format(Locale.US, "%.2f", v);
    }

    private static int modeToSpinner(int mode) {
        if (mode == DetectorConfig.MODE_COLOR) return 1;
        if (mode == DetectorConfig.MODE_EDGE) return 2;
        return 0;
    }

    private static int spinnerToMode(int position) {
        if (position == 1) return DetectorConfig.MODE_COLOR;
        if (position == 2) return DetectorConfig.MODE_EDGE;
        return DetectorConfig.MODE_AUTO;
    }

    private void setupListeners() {
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (suppressCallbacks) return;
                MarkerPreset preset = MarkerPreset.values()[position];
                if (preset == MarkerPreset.CUSTOM) return;
                suppressCallbacks = true;
                widthField.setText(trim(preset.widthMm));
                heightField.setText(trim(preset.heightMm));
                suppressCallbacks = false;
                config.markerWidthMm = preset.widthMm;
                config.markerHeightMm = preset.heightMm;
                schedulePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (suppressCallbacks) return;
                config.mode = spinnerToMode(position);
                schedulePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        TextWatcher sizeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressCallbacks) return;
                config.markerWidthMm = parse(widthField);
                config.markerHeightMm = parse(heightField);
                suppressCallbacks = true;
                presetSpinner.setSelection(MarkerPreset
                        .match(config.markerWidthMm, config.markerHeightMm).ordinal());
                suppressCallbacks = false;
                schedulePreview();
            }
        };
        widthField.addTextChangedListener(sizeWatcher);
        heightField.addTextChangedListener(sizeWatcher);

        hueLower.addOnChangeListener((slider, v, fromUser) -> {
            if (suppressCallbacks) return;
            config.hueLower = Math.round(v);
            updateRangeLabels();
            schedulePreview();
        });
        hueUpper.addOnChangeListener((slider, v, fromUser) -> {
            if (suppressCallbacks) return;
            config.hueUpper = Math.round(v);
            updateRangeLabels();
            schedulePreview();
        });
        saturation.addOnChangeListener((slider, v, fromUser) -> {
            if (suppressCallbacks) return;
            List<Float> values = saturation.getValues();
            config.satLower = Math.round(values.get(0));
            config.satUpper = Math.round(values.get(1));
            updateRangeLabels();
            schedulePreview();
        });
        value.addOnChangeListener((slider, v, fromUser) -> {
            if (suppressCallbacks) return;
            List<Float> values = value.getValues();
            config.valLower = Math.round(values.get(0));
            config.valUpper = Math.round(values.get(1));
            updateRangeLabels();
            schedulePreview();
        });

        beforeImage.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                v.performClick();
                sampleColourAt(event.getX(), event.getY());
            }
            return true;
        });
    }

    private double parse(TextInputEditText field) {
        try {
            CharSequence text = field.getText();
            if (text == null || text.length() == 0) return 0;
            return Double.parseDouble(text.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void registerLaunchers() {
        photoLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) loadSample(uri);
                });

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                    if (uri == null) return;
                    applyToPreferences();
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new java.io.IOException("No output stream");
                        SettingsIO.export(getSettings(), out);
                        Toast.makeText(this, R.string.settings_export_ok,
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        DialogHelper.error(this, ex.toString());
                    }
                });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri == null) return;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new java.io.IOException("No input stream");
                        int applied = SettingsIO.importInto(getSettings(), in);
                        config = DetectorConfig.from(getSettings());
                        bindConfigToViews();
                        schedulePreview();
                        Toast.makeText(this,
                                getString(R.string.settings_import_ok, applied),
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception ex) {
                        DialogHelper.error(this, ex.toString());
                    }
                });
    }

    // ---------------------------------------------------------------- sampling

    private void pickSamplePhoto() {
        photoLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void loadSample(Uri uri) {
        worker.execute(() -> {
            Bitmap decoded = null;
            Exception failure = null;
            try {
                decoded = BitmapHelper.decode(this, uri, 1024);
            } catch (Exception ex) {
                failure = ex;
            }
            final Bitmap finalBitmap = decoded;
            final Exception finalFailure = failure;
            main.post(() -> {
                if (finalBitmap == null) {
                    DialogHelper.error(this, R.string.error_load_image, finalFailure);
                    return;
                }
                sample = finalBitmap;
                beforeImage.setImageBitmap(sample);
                refreshPreview();
            });
        });
    }

    /**
     * Maps a touch on the preview back into bitmap pixels through the inverse of
     * the ImageView's own draw matrix, so it stays correct for any scale type or
     * letterboxing, then derives an HSV window from the patch under the finger.
     */
    private void sampleColourAt(float viewX, float viewY) {
        if (sample == null) {
            pickSamplePhoto();
            return;
        }

        Matrix inverse = new Matrix();
        if (!beforeImage.getImageMatrix().invert(inverse)) return;

        float[] point = {viewX, viewY};
        inverse.mapPoints(point);
        int x = Math.round(point[0]);
        int y = Math.round(point[1]);
        if (x < 0 || y < 0 || x >= sample.getWidth() || y >= sample.getHeight()) return;

        DetectorConfig sampled = MarkerDetector.sampleColorAt(sample, x, y, SAMPLE_RADIUS_PX);
        if (sampled == null) return;

        config.hueLower = sampled.hueLower;
        config.hueUpper = sampled.hueUpper;
        config.satLower = sampled.satLower;
        config.satUpper = sampled.satUpper;
        config.valLower = sampled.valLower;
        config.valUpper = sampled.valUpper;
        // Sampling a colour is an explicit statement that colour is the cue.
        if (config.mode == DetectorConfig.MODE_EDGE) config.mode = DetectorConfig.MODE_AUTO;

        suppressCallbacks = true;
        try {
            hueLower.setValue(clamp(config.hueLower, 0, 179));
            hueUpper.setValue(clamp(config.hueUpper, 0, 179));
            setRange(saturation, config.satLower, config.satUpper);
            setRange(value, config.valLower, config.valUpper);
            modeSpinner.setSelection(modeToSpinner(config.mode));
        } finally {
            suppressCallbacks = false;
        }
        updateRangeLabels();
        refreshPreview();
    }

    private void resetColorRange() {
        config.hueLower = 0;
        config.hueUpper = 179;
        config.satLower = 60;
        config.satUpper = 255;
        config.valLower = 60;
        config.valUpper = 255;
        bindConfigToViews();
        schedulePreview();
    }

    // ----------------------------------------------------------------- preview

    private void updateRangeLabels() {
        hueLabel.setText(getString(
                config.hueLower > config.hueUpper
                        ? R.string.settings_range_wrapping
                        : R.string.settings_range_format,
                config.hueLower, config.hueUpper));
        saturationLabel.setText(getString(R.string.settings_range_format,
                config.satLower, config.satUpper));
        valueLabel.setText(getString(R.string.settings_range_format,
                config.valLower, config.valUpper));
        colorRange.setRange(config.hueLower, config.hueUpper);
    }

    /** Collapse a burst of slider ticks into one preview run. */
    private void schedulePreview() {
        main.removeCallbacks(previewTask);
        main.postDelayed(previewTask, PREVIEW_DEBOUNCE_MS);
    }

    private void refreshPreview() {
        if (sample == null) return;
        final DetectorConfig snapshot = config.copy();

        worker.execute(() -> {
            final Bitmap mask = MarkerDetector.maskPreview(sample, snapshot);
            final SignMeasurement found = MarkerDetector.detect(sample, snapshot);
            main.post(() -> {
                if (mask != null) afterImage.setImageBitmap(mask);
                if (found != null) {
                    detectStatus.setText(getString(R.string.settings_detected,
                            Math.round(found.confidence() * 100)));
                } else {
                    detectStatus.setText(R.string.settings_not_detected);
                }
            });
        });
    }

    // -------------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_setting_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_activity_setting_export) {
            exportLauncher.launch("object-measurement-profile.json");
            return true;
        }
        if (id == R.id.action_activity_setting_import) {
            importLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------- saving

    private void applyToPreferences() {
        config.markerWidthMm = parse(widthField);
        config.markerHeightMm = parse(heightField);
        config.save(getSettings());
        getSettings().edit()
                .putString(DetectorConfig.KEY_UNIT,
                        LengthUnit.values()[unitSpinner.getSelectedItemPosition()].name())
                .apply();
    }

    private void saveAndClose() {
        double width = parse(widthField);
        double height = parse(heightField);
        if (width <= 0 || height <= 0) {
            Toast.makeText(this, R.string.settings_invalid_size, Toast.LENGTH_LONG).show();
            return;
        }
        applyToPreferences();
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
