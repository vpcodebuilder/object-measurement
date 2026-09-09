package com.vpcodebuilder.objectmeasurement.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opencv.core.Point;

/**
 * Pure-Java coverage for the geometry that the detector's accuracy rests on.
 * None of these touch OpenCV's native layer, so they run on a plain JVM.
 */
public class GeometryTest {

    private static final double EPS = 1e-6;

    /** Corners of a {@code width x height} rectangle rotated about its centre. */
    private static Point[] rotatedRectangle(double width, double height,
                                            double degrees, double cx, double cy) {
        double hw = width / 2.0;
        double hh = height / 2.0;
        double[][] local = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};

        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        Point[] out = new Point[4];
        for (int i = 0; i < 4; i++) {
            double x = local[i][0];
            double y = local[i][1];
            out[i] = new Point(cx + x * cos - y * sin, cy + x * sin + y * cos);
        }
        return out;
    }

    @Test
    public void angleAt_rightAngleIsNinetyDegrees() {
        double angle = ImageOps.angleAt(
                new Point(0, -1), new Point(0, 0), new Point(1, 0));
        assertEquals(90.0, angle, 1e-9);
    }

    @Test
    public void angleAt_straightLineIsOneEighty() {
        double angle = ImageOps.angleAt(
                new Point(-1, 0), new Point(0, 0), new Point(1, 0));
        assertEquals(180.0, angle, 1e-9);
    }

    @Test
    public void angleAt_coincidentRaysReturnZeroNotNaN() {
        // acos() is fed a clamped cosine, so degenerate geometry yields 0 rather
        // than a NaN that would propagate into the candidate score.
        double angle = ImageOps.angleAt(
                new Point(1, 1), new Point(0, 0), new Point(1, 1));
        assertFalse(Double.isNaN(angle));
        assertEquals(0.0, angle, 1e-9);
    }

    @Test
    public void angleAt_zeroLengthRayIsHandled() {
        double angle = ImageOps.angleAt(
                new Point(0, 0), new Point(0, 0), new Point(1, 0));
        assertFalse(Double.isNaN(angle));
    }

    @Test
    public void orderCorners_axisAlignedRectangle() {
        Point[] shuffled = {
                new Point(140, 120),  // bottom-right
                new Point(60, 80),    // top-left
                new Point(60, 120),   // bottom-left
                new Point(140, 80)    // top-right
        };

        Point[] ordered = ImageOps.orderCorners(shuffled);

        assertEquals(60, ordered[0].x, EPS);
        assertEquals(80, ordered[0].y, EPS);
        assertEquals(140, ordered[1].x, EPS);
        assertEquals(80, ordered[1].y, EPS);
        assertEquals(140, ordered[2].x, EPS);
        assertEquals(120, ordered[2].y, EPS);
        assertEquals(60, ordered[3].x, EPS);
        assertEquals(120, ordered[3].y, EPS);
    }

    /**
     * The regression that mattered most: the previous implementation sorted the
     * four points by y and then split each pair by x. For a rectangle rotated
     * 30° that puts two <em>left-edge</em> corners into the "top" pair, so the
     * edge it called "width" ran diagonally across the marker and every
     * millimetre-per-pixel scale derived from it was wrong.
     */
    @Test
    public void orderCorners_rotatedRectangleKeepsEdgesAdjacent() {
        Point[] corners = rotatedRectangle(80, 40, 30, 100, 100);
        // Feed them in a scrambled order; ordering must not depend on input order.
        Point[] shuffled = {corners[2], corners[0], corners[3], corners[1]};

        Point[] ordered = ImageOps.orderCorners(shuffled);

        assertEquals("edge 0->1 must be the long side",
                80.0, ImageOps.distance(ordered[0], ordered[1]), 1e-6);
        assertEquals("edge 1->2 must be the short side",
                40.0, ImageOps.distance(ordered[1], ordered[2]), 1e-6);
        assertEquals(80.0, ImageOps.distance(ordered[2], ordered[3]), 1e-6);
        assertEquals(40.0, ImageOps.distance(ordered[3], ordered[0]), 1e-6);
    }

    /**
     * At every rotation the cycle must alternate long, short, long, short — that
     * is what "the corners stay adjacent" means, and it is all the detector
     * needs. Which of the two lands first is <em>not</em> fixed: the cycle
     * starts at the corner nearest the origin, so past 45° the short edge
     * leads. {@link com.vpcodebuilder.objectmeasurement.detect.MarkerDetector}
     * settles that phase itself, by rotating the cycle when the marker's
     * configured aspect ratio matches the other way round.
     */
    @Test
    public void orderCorners_survivesSteepRotation() {
        for (int degrees = 0; degrees < 90; degrees += 7) {
            Point[] ordered = ImageOps.orderCorners(
                    rotatedRectangle(80, 40, degrees, 500, 500));

            double first = ImageOps.distance(ordered[0], ordered[1]);
            double second = ImageOps.distance(ordered[1], ordered[2]);

            String where = "at " + degrees + "° the sides were " + first + "/" + second;
            assertTrue(where, Math.abs(first - 80) < 1e-6 && Math.abs(second - 40) < 1e-6
                    || Math.abs(first - 40) < 1e-6 && Math.abs(second - 80) < 1e-6);
            assertEquals(where, first, ImageOps.distance(ordered[2], ordered[3]), 1e-6);
            assertEquals(where, second, ImageOps.distance(ordered[3], ordered[0]), 1e-6);
        }
    }

    @Test
    public void orderCorners_producesRightAnglesForARectangle() {
        Point[] ordered = ImageOps.orderCorners(rotatedRectangle(90, 55, 18, 200, 150));
        for (int i = 0; i < 4; i++) {
            double angle = ImageOps.angleAt(
                    ordered[(i + 3) % 4], ordered[i], ordered[(i + 1) % 4]);
            assertEquals(90.0, angle, 1e-6);
        }
    }

    @Test
    public void rotateCorners_swapsWidthAndHeightEdges() {
        Point[] ordered = ImageOps.orderCorners(rotatedRectangle(80, 40, 0, 100, 100));
        Point[] rotated = ImageOps.rotateCorners(ordered);

        assertEquals(40.0, ImageOps.distance(rotated[0], rotated[1]), EPS);
        assertEquals(80.0, ImageOps.distance(rotated[1], rotated[2]), EPS);
    }

    @Test
    public void distance_isEuclidean() {
        assertEquals(5.0, ImageOps.distance(new Point(0, 0), new Point(3, 4)), EPS);
    }
}
