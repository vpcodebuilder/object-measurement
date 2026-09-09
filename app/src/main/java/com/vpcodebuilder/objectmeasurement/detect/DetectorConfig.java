package com.vpcodebuilder.objectmeasurement.detect;

import android.content.SharedPreferences;

/**
 * Everything the marker detector needs to know, in one place.
 *
 * <p>The legacy code read six loose {@code SharedPreferences} ints in four different
 * activities and passed them around as six positional {@code int} arguments; a single
 * transposed pair silently produced an empty mask. This type is the single source of
 * truth instead.</p>
 */
public final class DetectorConfig {

    /** Colour-only detection: the marker is found by its HSV range. */
    public static final int MODE_COLOR = 0;
    /** Edge-only detection: any high-contrast rectangle (a sheet of paper, a card). */
    public static final int MODE_EDGE = 1;
    /** Try colour first, fall back to edges. Best default. */
    public static final int MODE_AUTO = 2;

    // --- HSV gate ------------------------------------------------------------
    // OpenCV hue is 0..179. When hueLower > hueUpper the range is treated as
    // wrapping across the 179 -> 0 seam, which is the only way to select red.
    public int hueLower = 0;
    public int hueUpper = 179;
    public int satLower = 60;
    public int satUpper = 255;
    public int valLower = 60;
    public int valUpper = 255;

    // --- Marker geometry -----------------------------------------------------
    public double markerWidthMm = 0;
    public double markerHeightMm = 0;

    // --- Pipeline ------------------------------------------------------------
    public int mode = MODE_AUTO;
    /**
     * Width the detector downsamples to before working.
     *
     * <p>Cost scales with the square of this, but so does corner precision — and
     * corner precision is measurement precision. This default suits one-shot
     * detection on a still photo; the live viewfinder overrides it downwards.</p>
     */
    public int workingWidth = 900;
    /** Percentile clip for the white-balance pass; 0 disables it. */
    public double colorBalancePercent = 1.0;
    public boolean refineCorners = true;

    // --- Candidate acceptance ------------------------------------------------
    public double minAreaRatio = 0.0006;
    public double maxAreaRatio = 0.85;
    /** How far a corner may sit from a right angle before the quad is rejected. */
    public double maxCornerDeviationDeg = 25.0;
    public double minSolidity = 0.85;
    public double minQuadFit = 0.70;
    /** Lowest observed/expected aspect agreement that is still accepted. */
    public double minAspectAgreement = 0.55;

    public DetectorConfig() {
    }

    public boolean hasMarkerSize() {
        return markerWidthMm > 0 && markerHeightMm > 0;
    }

    /** True when the hue range wraps across the 179 -> 0 seam. */
    public boolean hueWraps() {
        return hueLower > hueUpper;
    }

    public DetectorConfig copy() {
        DetectorConfig c = new DetectorConfig();
        c.hueLower = hueLower;
        c.hueUpper = hueUpper;
        c.satLower = satLower;
        c.satUpper = satUpper;
        c.valLower = valLower;
        c.valUpper = valUpper;
        c.markerWidthMm = markerWidthMm;
        c.markerHeightMm = markerHeightMm;
        c.mode = mode;
        c.workingWidth = workingWidth;
        c.colorBalancePercent = colorBalancePercent;
        c.refineCorners = refineCorners;
        c.minAreaRatio = minAreaRatio;
        c.maxAreaRatio = maxAreaRatio;
        c.maxCornerDeviationDeg = maxCornerDeviationDeg;
        c.minSolidity = minSolidity;
        c.minQuadFit = minQuadFit;
        c.minAspectAgreement = minAspectAgreement;
        return c;
    }

    // --- Persistence ---------------------------------------------------------

    public static final String PREFS = "App_Config";

    public static final String KEY_MARKER_WIDTH = "realSignWidth";
    public static final String KEY_MARKER_HEIGHT = "realSignHeight";
    public static final String KEY_HUE_LOWER = "hueLower";
    public static final String KEY_HUE_UPPER = "hueUpper";
    public static final String KEY_SAT_LOWER = "saturationLower";
    public static final String KEY_SAT_UPPER = "saturationUpper";
    public static final String KEY_VAL_LOWER = "valueLower";
    public static final String KEY_VAL_UPPER = "valueUpper";
    public static final String KEY_MODE = "detectMode";
    public static final String KEY_UNIT = "lengthUnit";

    public static DetectorConfig from(SharedPreferences prefs) {
        DetectorConfig c = new DetectorConfig();
        if (prefs == null) return c;
        c.hueLower = prefs.getInt(KEY_HUE_LOWER, c.hueLower);
        c.hueUpper = prefs.getInt(KEY_HUE_UPPER, c.hueUpper);
        c.satLower = prefs.getInt(KEY_SAT_LOWER, c.satLower);
        c.satUpper = prefs.getInt(KEY_SAT_UPPER, c.satUpper);
        c.valLower = prefs.getInt(KEY_VAL_LOWER, c.valLower);
        c.valUpper = prefs.getInt(KEY_VAL_UPPER, c.valUpper);
        c.markerWidthMm = prefs.getFloat(KEY_MARKER_WIDTH, 0f);
        c.markerHeightMm = prefs.getFloat(KEY_MARKER_HEIGHT, 0f);
        c.mode = prefs.getInt(KEY_MODE, MODE_AUTO);
        return c;
    }

    public void save(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
                .putInt(KEY_HUE_LOWER, hueLower)
                .putInt(KEY_HUE_UPPER, hueUpper)
                .putInt(KEY_SAT_LOWER, satLower)
                .putInt(KEY_SAT_UPPER, satUpper)
                .putInt(KEY_VAL_LOWER, valLower)
                .putInt(KEY_VAL_UPPER, valUpper)
                .putFloat(KEY_MARKER_WIDTH, (float) markerWidthMm)
                .putFloat(KEY_MARKER_HEIGHT, (float) markerHeightMm)
                .putInt(KEY_MODE, mode)
                .apply();
    }
}
