package com.vpcodebuilder.objectmeasurement.detect;

import java.util.Locale;

/** Display unit for measured lengths. Everything is stored internally in millimetres. */
public enum LengthUnit {

    MM("mm", 1.0, 0),
    CM("cm", 10.0, 1),
    M("m", 1000.0, 3),
    INCH("in", 25.4, 2);

    public final String suffix;
    /** Millimetres in one of this unit. */
    public final double millimetres;
    private final int decimals;

    LengthUnit(String suffix, double millimetres, int decimals) {
        this.suffix = suffix;
        this.millimetres = millimetres;
        this.decimals = decimals;
    }

    public double fromMm(double mm) {
        return mm / millimetres;
    }

    public String format(double mm) {
        return String.format(Locale.US, "%." + decimals + "f %s", fromMm(mm), suffix);
    }

    /** Just the number, for places that render the unit separately. */
    public String formatValue(double mm) {
        return String.format(Locale.US, "%." + decimals + "f", fromMm(mm));
    }

    public static LengthUnit byName(String name, LengthUnit fallback) {
        if (name == null) return fallback;
        for (LengthUnit u : values()) {
            if (u.name().equals(name)) return u;
        }
        return fallback;
    }

    @Override
    public String toString() {
        switch (this) {
            case MM: return "Millimetres (mm)";
            case CM: return "Centimetres (cm)";
            case M:  return "Metres (m)";
            default: return "Inches (in)";
        }
    }
}
