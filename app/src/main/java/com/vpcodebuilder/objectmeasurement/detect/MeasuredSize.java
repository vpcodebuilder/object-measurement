package com.vpcodebuilder.objectmeasurement.detect;

import java.util.Locale;

/** A width/height pair in millimetres, plus how it was derived. */
public final class MeasuredSize {

    public static final MeasuredSize EMPTY = new MeasuredSize(0, 0, false);

    public final double widthMm;
    public final double heightMm;
    /** True when perspective was corrected via the marker homography. */
    public final boolean perspectiveCorrected;

    public MeasuredSize(double widthMm, double heightMm, boolean perspectiveCorrected) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.perspectiveCorrected = perspectiveCorrected;
    }

    public boolean isEmpty() {
        return widthMm <= 0 || heightMm <= 0;
    }

    public String format(LengthUnit unit) {
        return unit.format(widthMm) + " × " + unit.format(heightMm);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%.1f x %.1f mm", widthMm, heightMm);
    }
}
