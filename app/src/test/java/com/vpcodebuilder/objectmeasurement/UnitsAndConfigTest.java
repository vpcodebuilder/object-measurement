package com.vpcodebuilder.objectmeasurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vpcodebuilder.objectmeasurement.detect.DetectorConfig;
import com.vpcodebuilder.objectmeasurement.detect.LengthUnit;
import com.vpcodebuilder.objectmeasurement.detect.MarkerPreset;

import org.junit.Test;

public class UnitsAndConfigTest {

    @Test
    public void lengthUnit_convertsFromMillimetres() {
        assertEquals(1.0, LengthUnit.CM.fromMm(10), 1e-9);
        assertEquals(1.0, LengthUnit.M.fromMm(1000), 1e-9);
        assertEquals(1.0, LengthUnit.INCH.fromMm(25.4), 1e-9);
        assertEquals(1.0, LengthUnit.MM.fromMm(1), 1e-9);
    }

    @Test
    public void lengthUnit_formatsWithSensibleResolution() {
        // Metres need decimals that millimetres do not; a shared format string
        // would either lose precision or print noise.
        assertEquals("1.234 m", LengthUnit.M.format(1234));
        assertEquals("123 mm", LengthUnit.MM.format(123.4));
        assertEquals("12.3 cm", LengthUnit.CM.format(123.4));
    }

    @Test
    public void lengthUnit_byNameFallsBack() {
        assertEquals(LengthUnit.CM, LengthUnit.byName("CM", LengthUnit.MM));
        assertEquals(LengthUnit.MM, LengthUnit.byName("furlong", LengthUnit.MM));
        assertEquals(LengthUnit.MM, LengthUnit.byName(null, LengthUnit.MM));
    }

    /**
     * A hue window whose lower bound sits above its upper bound means "wrap past
     * the 179 -> 0 seam". Red is the only sensible marker colour that needs it,
     * and the old single-{@code inRange} pipeline could not express it at all.
     */
    @Test
    public void detectorConfig_detectsWrappingHueRange() {
        DetectorConfig config = new DetectorConfig();

        config.hueLower = 170;
        config.hueUpper = 10;
        assertTrue(config.hueWraps());

        config.hueLower = 30;
        config.hueUpper = 90;
        assertFalse(config.hueWraps());
    }

    @Test
    public void detectorConfig_requiresBothMarkerDimensions() {
        DetectorConfig config = new DetectorConfig();
        assertFalse(config.hasMarkerSize());

        config.markerWidthMm = 210;
        assertFalse(config.hasMarkerSize());

        config.markerHeightMm = 297;
        assertTrue(config.hasMarkerSize());
    }

    @Test
    public void detectorConfig_copyIsIndependent() {
        DetectorConfig original = new DetectorConfig();
        original.hueLower = 42;
        original.markerWidthMm = 85.6;

        DetectorConfig copy = original.copy();
        copy.hueLower = 7;

        assertEquals(42, original.hueLower);
        assertEquals(7, copy.hueLower);
        assertEquals(85.6, copy.markerWidthMm, 1e-9);
    }

    @Test
    public void markerPreset_matchesKnownSizes() {
        assertEquals(MarkerPreset.A4, MarkerPreset.match(210, 297));
        assertEquals(MarkerPreset.CREDIT_CARD, MarkerPreset.match(85.6, 53.98));
        assertEquals(MarkerPreset.CUSTOM, MarkerPreset.match(123, 456));
        assertEquals(MarkerPreset.CUSTOM, MarkerPreset.match(0, 0));
    }

    @Test
    public void bitmapHelper_sampleSizeKeepsRequestedWidth() {
        assertEquals(1, BitmapHelper.sampleSize(800, 1600));
        assertEquals(1, BitmapHelper.sampleSize(1600, 1600));
        assertEquals(2, BitmapHelper.sampleSize(3200, 1600));
        assertEquals(4, BitmapHelper.sampleSize(6400, 1600));
        assertEquals(1, BitmapHelper.sampleSize(4000, 0));
    }
}
