package com.vpcodebuilder.objectmeasurement.detect;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Low-level image helpers shared by the detector and the tuning preview. */
public final class ImageOps {

    private ImageOps() {
    }

    // ------------------------------------------------------------------ colour

    /**
     * Percentile-clipping white balance ("simplest colour balance").
     *
     * <p>The original implementation sorted every pixel of every channel on each
     * call — O(n log n) per frame, which is why the live preview crawled. It also
     * read percentiles from a matrix it had already reallocated, so the clip
     * points were frequently wrong. This version builds a 256-bin histogram and a
     * lookup table instead: same result, single pass, no allocation per pixel.</p>
     *
     * @param rgb     8-bit 3-channel image, modified in place
     * @param percent total share of pixels to clip (split between both tails)
     */
    public static void colorBalance(Mat rgb, double percent) {
        if (rgb == null || rgb.empty() || rgb.channels() != 3 || percent <= 0) return;

        double halfShare = percent / 200.0;
        double target = (double) rgb.rows() * rgb.cols() * halfShare;

        byte[] lut = new byte[256 * 3];
        List<Mat> images = Collections.singletonList(rgb);
        Mat hist = new Mat();
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);
        Mat noMask = new Mat();
        float[] counts = new float[256];

        try {
            for (int channel = 0; channel < 3; channel++) {
                MatOfInt channels = new MatOfInt(channel);
                try {
                    Imgproc.calcHist(images, channels, noMask, hist, histSize, ranges);
                } finally {
                    channels.release();
                }
                hist.get(0, 0, counts);

                int lo = 0;
                double acc = 0;
                for (int i = 0; i < 256; i++) {
                    acc += counts[i];
                    if (acc > target) { lo = i; break; }
                }

                int hi = 255;
                acc = 0;
                for (int i = 255; i >= 0; i--) {
                    acc += counts[i];
                    if (acc > target) { hi = i; break; }
                }

                if (hi <= lo) { lo = 0; hi = 255; }
                double span = hi - lo;

                for (int v = 0; v < 256; v++) {
                    int clamped = v < lo ? lo : (v > hi ? hi : v);
                    int mapped = (int) Math.round((clamped - lo) * 255.0 / span);
                    lut[v * 3 + channel] = (byte) (mapped & 0xFF);
                }
            }
        } finally {
            hist.release();
            histSize.release();
            ranges.release();
            noMask.release();
        }

        Mat lutMat = new Mat(1, 256, CvType.CV_8UC3);
        try {
            lutMat.put(0, 0, lut);
            Core.LUT(rgb, lutMat, rgb);
        } finally {
            lutMat.release();
        }
    }

    // -------------------------------------------------------------------- mask

    /**
     * Binary mask of the pixels inside the configured HSV box.
     *
     * <p>Unlike the original single {@code inRange} call, a hue range whose lower
     * bound is above its upper bound is treated as wrapping across the 179 -> 0
     * seam. Without that, red markers — the most natural choice — were impossible
     * to select, because red straddles both ends of the hue axis.</p>
     */
    public static Mat hsvMask(Mat rgb, DetectorConfig cfg) {
        Mat hsv = new Mat();
        Mat mask = new Mat();
        try {
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV);

            if (!cfg.hueWraps()) {
                Core.inRange(hsv,
                        new Scalar(cfg.hueLower, cfg.satLower, cfg.valLower),
                        new Scalar(cfg.hueUpper, cfg.satUpper, cfg.valUpper),
                        mask);
            } else {
                Mat low = new Mat();
                Mat high = new Mat();
                try {
                    Core.inRange(hsv,
                            new Scalar(cfg.hueLower, cfg.satLower, cfg.valLower),
                            new Scalar(179, cfg.satUpper, cfg.valUpper), low);
                    Core.inRange(hsv,
                            new Scalar(0, cfg.satLower, cfg.valLower),
                            new Scalar(cfg.hueUpper, cfg.satUpper, cfg.valUpper), high);
                    Core.bitwise_or(low, high, mask);
                } finally {
                    low.release();
                    high.release();
                }
            }
        } finally {
            hsv.release();
        }
        return mask;
    }

    /**
     * Open-then-close with a real structuring element.
     *
     * <p>The original called {@code erode}/{@code dilate} with
     * {@code new Mat(3, 3, CV_8UC1)} — an <em>uninitialised</em> matrix, so the
     * morphology kernel was whatever happened to be in memory and the result was
     * not reproducible between runs.</p>
     */
    public static void clean(Mat mask, int kernelSize) {
        if (kernelSize < 3) return;
        Mat kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(kernelSize, kernelSize));
        try {
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        } finally {
            kernel.release();
        }
    }

    /**
     * Blank the outermost pixels so a blob touching the frame edge cannot produce
     * a contour that simply traces the image border. Replaces the old
     * {@code removeMorphologyBug}, which copied the whole pixel buffer to Java and
     * back to do the same thing.
     */
    public static void clearBorder(Mat mask, int thickness) {
        if (thickness <= 0) return;
        Imgproc.rectangle(mask,
                new Point(0, 0),
                new Point(mask.cols() - 1, mask.rows() - 1),
                new Scalar(0), thickness);
    }

    /**
     * Edge mask used when colour segmentation finds nothing — this is what lets a
     * plain white sheet of A4 or a bank card work as a marker with no HSV tuning
     * at all.
     */
    public static Mat edgeMask(Mat gray) {
        Mat blurred = new Mat();
        Mat edges = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        try {
            Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
            double median = median(blurred);
            double lower = Math.max(0, 0.66 * median);
            double upper = Math.min(255, 1.33 * median);
            if (upper - lower < 20) {
                lower = Math.max(0, median - 20);
                upper = Math.min(255, median + 20);
            }
            Imgproc.Canny(blurred, edges, lower, upper);
            // Close 1px gaps so a slightly broken rectangle still yields one contour.
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel);
        } finally {
            blurred.release();
            kernel.release();
        }
        return edges;
    }

    /** Median intensity of a single-channel 8-bit image, via its histogram. */
    public static double median(Mat gray8u) {
        Mat hist = new Mat();
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat ranges = new MatOfFloat(0f, 256f);
        MatOfInt channels = new MatOfInt(0);
        Mat noMask = new Mat();
        try {
            Imgproc.calcHist(Collections.singletonList(gray8u), channels, noMask,
                    hist, histSize, ranges);
            float[] counts = new float[256];
            hist.get(0, 0, counts);
            double half = (double) gray8u.rows() * gray8u.cols() / 2.0;
            double acc = 0;
            for (int i = 0; i < 256; i++) {
                acc += counts[i];
                if (acc >= half) return i;
            }
            return 128;
        } finally {
            hist.release();
            histSize.release();
            ranges.release();
            channels.release();
            noMask.release();
        }
    }

    // ---------------------------------------------------------------- geometry

    /**
     * Order four corners as top-left, top-right, bottom-right, bottom-left.
     *
     * <p>The original sorted purely by y and then split the pairs by x, which
     * produces the wrong assignment as soon as the marker is rotated more than a
     * few degrees — the two "top" points are then not the top edge at all. Sorting
     * by angle around the centroid is rotation-invariant; the start of the cycle
     * is then pinned to the corner nearest the image origin.</p>
     */
    public static Point[] orderCorners(Point[] points) {
        if (points == null || points.length != 4) return points;

        double cx = 0;
        double cy = 0;
        for (Point p : points) {
            cx += p.x;
            cy += p.y;
        }
        cx /= 4.0;
        cy /= 4.0;

        final double centreX = cx;
        final double centreY = cy;
        Point[] cycle = new Point[]{
                new Point(points[0].x, points[0].y),
                new Point(points[1].x, points[1].y),
                new Point(points[2].x, points[2].y),
                new Point(points[3].x, points[3].y)
        };
        // In image coordinates (y grows downwards) ascending atan2 walks clockwise.
        Arrays.sort(cycle, (a, b) -> Double.compare(
                Math.atan2(a.y - centreY, a.x - centreX),
                Math.atan2(b.y - centreY, b.x - centreX)));

        int start = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double sum = cycle[i].x + cycle[i].y;
            if (sum < best) {
                best = sum;
                start = i;
            }
        }

        Point[] ordered = new Point[4];
        for (int i = 0; i < 4; i++) {
            ordered[i] = cycle[(start + i) % 4];
        }
        return ordered;
    }

    /** Rotate an ordered corner cycle one step, swapping the width and height edges. */
    public static Point[] rotateCorners(Point[] ordered) {
        return new Point[]{ordered[1], ordered[2], ordered[3], ordered[0]};
    }

    /**
     * Interior angle at {@code vertex}, in degrees.
     *
     * <p>The original helper returned degrees but its result was accumulated into
     * a variable called {@code maxCosine} and compared against the literal range
     * {@code (80, 100)} — the naming was wrong and the test silently rejected any
     * quadrilateral whose <em>largest</em> angle fell below 80°, which excludes
     * plenty of valid perspective views.</p>
     *
     * <p>Computed as {@code atan2(|cross|, dot)} rather than {@code acos} of a
     * normalised dot product. {@code acos} loses most of its precision where its
     * argument approaches ±1 — exactly the near-0° and near-180° cases the
     * degeneracy checks care about — and needs its input clamped to survive
     * rounding past 1. The {@code atan2} form is well conditioned across the
     * whole range and returns an exact 0 for coincident or zero-length rays.</p>
     */
    public static double angleAt(Point previous, Point vertex, Point next) {
        double ax = previous.x - vertex.x;
        double ay = previous.y - vertex.y;
        double bx = next.x - vertex.x;
        double by = next.y - vertex.y;
        double cross = Math.abs(ax * by - ay * bx);
        double dot = ax * bx + ay * by;
        if (cross == 0 && dot == 0) return 0;
        return Math.toDegrees(Math.atan2(cross, dot));
    }

    public static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /** Area of the convex hull of a contour. */
    public static double hullArea(Point[] points) {
        MatOfPoint contour = new MatOfPoint(points);
        MatOfInt hullIndices = new MatOfInt();
        try {
            Imgproc.convexHull(contour, hullIndices);
            int[] indices = hullIndices.toArray();
            if (indices.length < 3) return 0;
            Point[] hull = new Point[indices.length];
            for (int i = 0; i < indices.length; i++) {
                hull[i] = points[indices[i]];
            }
            MatOfPoint hullContour = new MatOfPoint(hull);
            try {
                return Math.abs(Imgproc.contourArea(hullContour));
            } finally {
                hullContour.release();
            }
        } finally {
            contour.release();
            hullIndices.release();
        }
    }

    public static double polygonArea(Point[] points) {
        MatOfPoint contour = new MatOfPoint(points);
        try {
            return Math.abs(Imgproc.contourArea(contour));
        } finally {
            contour.release();
        }
    }

    public static boolean isConvex(Point[] points) {
        MatOfPoint contour = new MatOfPoint(points);
        try {
            return Imgproc.isContourConvex(contour);
        } finally {
            contour.release();
        }
    }

    /**
     * Snap corners onto the true intensity corner with sub-pixel accuracy.
     * Skipped when a corner sits too close to the border for the search window.
     */
    public static Point[] refineCorners(Mat gray, Point[] corners) {
        int window = 5;
        for (Point p : corners) {
            if (p.x < window + 1 || p.y < window + 1
                    || p.x > gray.cols() - window - 2
                    || p.y > gray.rows() - window - 2) {
                return corners;
            }
        }

        MatOfPoint2f refined = new MatOfPoint2f(corners);
        try {
            Imgproc.cornerSubPix(gray, refined,
                    new Size(window, window), new Size(-1, -1),
                    new org.opencv.core.TermCriteria(
                            org.opencv.core.TermCriteria.EPS
                                    + org.opencv.core.TermCriteria.MAX_ITER, 30, 0.01));
            Point[] result = refined.toArray();
            // A refinement that runs away is worse than none at all.
            for (int i = 0; i < result.length; i++) {
                if (distance(result[i], corners[i]) > window * 2.0) return corners;
            }
            return result;
        } catch (Exception ex) {
            return corners;
        } finally {
            refined.release();
        }
    }

    /** Release every Mat in a list and clear it. */
    public static void releaseAll(List<MatOfPoint> mats) {
        if (mats == null) return;
        for (MatOfPoint m : mats) {
            if (m != null) m.release();
        }
        mats.clear();
    }

    public static List<MatOfPoint> newContourList() {
        return new ArrayList<>();
    }
}
