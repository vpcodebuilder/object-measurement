package com.vpcodebuilder.objectmeasurement.detect;

/**
 * Common reference objects, so the user does not have to type millimetres by hand.
 * Sizes are the real-world dimensions of the printed/physical marker.
 */
public enum MarkerPreset {

    CUSTOM("Custom size", 0, 0),
    A4("A4 sheet — 210 × 297 mm", 210.0, 297.0),
    A5("A5 sheet — 148 × 210 mm", 148.0, 210.0),
    LETTER("US Letter — 216 × 279 mm", 215.9, 279.4),
    CREDIT_CARD("Bank card — 85.6 × 54 mm", 85.6, 53.98),
    CD("CD / DVD case — 120 × 120 mm", 120.0, 120.0),
    POSTIT("Sticky note — 76 × 76 mm", 76.0, 76.0);

    public final String label;
    public final double widthMm;
    public final double heightMm;

    MarkerPreset(String label, double widthMm, double heightMm) {
        this.label = label;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
    }

    @Override
    public String toString() {
        return label;
    }

    /** The preset matching these dimensions, or {@link #CUSTOM} when nothing fits. */
    public static MarkerPreset match(double widthMm, double heightMm) {
        for (MarkerPreset p : values()) {
            if (p == CUSTOM) continue;
            if (Math.abs(p.widthMm - widthMm) < 0.5 && Math.abs(p.heightMm - heightMm) < 0.5) {
                return p;
            }
        }
        return CUSTOM;
    }
}
