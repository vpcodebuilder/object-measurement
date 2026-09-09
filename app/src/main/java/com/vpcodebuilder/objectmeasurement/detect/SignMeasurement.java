package com.vpcodebuilder.objectmeasurement.detect;

import android.os.Parcel;
import android.os.Parcelable;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.util.Locale;

/**
 * A detected reference marker: its four corners in image pixels plus the real
 * dimensions of the physical marker.
 *
 * <p>The previous version stored only an axis-aligned {@code Rect} and two scalar
 * "ruler" lengths, which threw away the information needed to correct for
 * perspective. Keeping the four corners lets us build a homography from the image
 * plane to the marker plane, so an object photographed at an angle measures
 * correctly instead of being systematically undersized.</p>
 *
 * <p>Coordinates are always in the pixel space of the image the marker was
 * detected in. Corners are ordered top-left, top-right, bottom-right, bottom-left
 * relative to the marker itself, so corner 0 -> 1 spans {@link #markerWidthMm}.</p>
 */
public final class SignMeasurement implements Parcelable {

    public static final String SOURCE_COLOR = "color";
    public static final String SOURCE_EDGE = "edge";
    public static final String SOURCE_MANUAL = "manual";

    private final double[] corners; // x0,y0, x1,y1, x2,y2, x3,y3
    private final double markerWidthMm;
    private final double markerHeightMm;
    private final float confidence;
    private final String source;

    public SignMeasurement(Point[] corners,
                           double markerWidthMm,
                           double markerHeightMm,
                           float confidence,
                           String source) {
        if (corners == null || corners.length != 4) {
            throw new IllegalArgumentException("a marker needs exactly 4 corners");
        }
        this.corners = new double[8];
        for (int i = 0; i < 4; i++) {
            this.corners[i * 2] = corners[i].x;
            this.corners[i * 2 + 1] = corners[i].y;
        }
        this.markerWidthMm = markerWidthMm;
        this.markerHeightMm = markerHeightMm;
        this.confidence = confidence;
        this.source = source == null ? SOURCE_MANUAL : source;
    }

    private SignMeasurement(double[] corners,
                            double markerWidthMm,
                            double markerHeightMm,
                            float confidence,
                            String source) {
        this.corners = corners;
        this.markerWidthMm = markerWidthMm;
        this.markerHeightMm = markerHeightMm;
        this.confidence = confidence;
        this.source = source;
    }

    // ----------------------------------------------------------------- getters

    public Point[] cornerPoints() {
        return new Point[]{
                new Point(corners[0], corners[1]),
                new Point(corners[2], corners[3]),
                new Point(corners[4], corners[5]),
                new Point(corners[6], corners[7])
        };
    }

    public double markerWidthMm() {
        return markerWidthMm;
    }

    public double markerHeightMm() {
        return markerHeightMm;
    }

    public float confidence() {
        return confidence;
    }

    public String source() {
        return source;
    }

    public boolean hasMarkerSize() {
        return markerWidthMm > 0 && markerHeightMm > 0;
    }

    /** Axis-aligned bounding box of the marker quad, in image pixels. */
    public android.graphics.Rect boundary() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minX = Math.min(minX, corners[i * 2]);
            maxX = Math.max(maxX, corners[i * 2]);
            minY = Math.min(minY, corners[i * 2 + 1]);
            maxY = Math.max(maxY, corners[i * 2 + 1]);
        }
        return new android.graphics.Rect(
                (int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    /**
     * Marker width in pixels, averaged over the top and bottom edges.
     * The old code took {@code Math.min} of the two, which biases every
     * measurement small whenever the marker is even slightly tilted.
     */
    public double rulerWidthPx() {
        Point[] p = cornerPoints();
        return (ImageOps.distance(p[0], p[1]) + ImageOps.distance(p[3], p[2])) / 2.0;
    }

    /** Marker height in pixels, averaged over the left and right edges. */
    public double rulerHeightPx() {
        Point[] p = cornerPoints();
        return (ImageOps.distance(p[0], p[3]) + ImageOps.distance(p[1], p[2])) / 2.0;
    }

    public double pxPerMmX() {
        double w = rulerWidthPx();
        return markerWidthMm > 0 && w > 0 ? w / markerWidthMm : 0;
    }

    public double pxPerMmY() {
        double h = rulerHeightPx();
        return markerHeightMm > 0 && h > 0 ? h / markerHeightMm : 0;
    }

    /**
     * How far the quad departs from a rectangle, as the ratio between the two
     * diagonals. 1.0 means the camera was square-on; large values mean a steep
     * angle and a less trustworthy measurement.
     */
    public double perspectiveSkew() {
        Point[] p = cornerPoints();
        double d1 = ImageOps.distance(p[0], p[2]);
        double d2 = ImageOps.distance(p[1], p[3]);
        if (d1 <= 0 || d2 <= 0) return 1;
        return Math.max(d1, d2) / Math.min(d1, d2);
    }

    // ------------------------------------------------------------- measurement

    /**
     * Measure a rectangle drawn in image pixels, in millimetres.
     *
     * <p>Both corners of the box are mapped through the marker homography, so the
     * result is the size of the region <em>on the marker's plane</em>. That is the
     * correction the old {@code ComputeObjectSize} was missing: it multiplied the
     * box's pixel size by a single scalar mm/px taken at the marker, which is only
     * valid when the camera is exactly perpendicular to the scene.</p>
     *
     * <p>The object still has to lie roughly in the same plane as the marker —
     * that assumption is inherent to single-image measurement.</p>
     */
    public MeasuredSize measure(android.graphics.Rect rectInImagePx) {
        if (rectInImagePx == null || rectInImagePx.width() <= 0 || rectInImagePx.height() <= 0) {
            return MeasuredSize.EMPTY;
        }
        if (!hasMarkerSize()) {
            return MeasuredSize.EMPTY;
        }

        MatOfPoint2f source = new MatOfPoint2f(cornerPoints());
        MatOfPoint2f target = new MatOfPoint2f(
                new Point(0, 0),
                new Point(markerWidthMm, 0),
                new Point(markerWidthMm, markerHeightMm),
                new Point(0, markerHeightMm));
        MatOfPoint2f box = null;
        MatOfPoint2f mapped = null;
        Mat homography = null;

        try {
            homography = Calib3d.findHomography(source, target);
            if (homography == null || homography.empty()) {
                return scalarMeasure(rectInImagePx);
            }

            box = new MatOfPoint2f(
                    new Point(rectInImagePx.left, rectInImagePx.top),
                    new Point(rectInImagePx.right, rectInImagePx.top),
                    new Point(rectInImagePx.right, rectInImagePx.bottom),
                    new Point(rectInImagePx.left, rectInImagePx.bottom));
            mapped = new MatOfPoint2f();
            Core.perspectiveTransform(box, mapped, homography);

            Point[] p = mapped.toArray();
            if (p.length != 4) return scalarMeasure(rectInImagePx);

            double width = (ImageOps.distance(p[0], p[1]) + ImageOps.distance(p[3], p[2])) / 2.0;
            double height = (ImageOps.distance(p[0], p[3]) + ImageOps.distance(p[1], p[2])) / 2.0;

            if (!isUsable(width) || !isUsable(height)) {
                return scalarMeasure(rectInImagePx);
            }
            return new MeasuredSize(width, height, true);
        } catch (Exception ex) {
            return scalarMeasure(rectInImagePx);
        } finally {
            source.release();
            target.release();
            if (box != null) box.release();
            if (mapped != null) mapped.release();
            if (homography != null) homography.release();
        }
    }

    /** Uniform-scale fallback, used when the homography is degenerate. */
    private MeasuredSize scalarMeasure(android.graphics.Rect rect) {
        double sx = pxPerMmX();
        double sy = pxPerMmY();
        if (sx <= 0 || sy <= 0) return MeasuredSize.EMPTY;
        return new MeasuredSize(rect.width() / sx, rect.height() / sy, false);
    }

    private static boolean isUsable(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v > 0;
    }

    // -------------------------------------------------------------- transforms

    /** The same marker expressed in an image scaled by {@code factor}. */
    public SignMeasurement scaled(double factor) {
        double[] scaled = new double[8];
        for (int i = 0; i < 8; i++) {
            scaled[i] = corners[i] * factor;
        }
        return new SignMeasurement(scaled, markerWidthMm, markerHeightMm, confidence, source);
    }

    /** The same corners, re-tagged with a different physical marker size. */
    public SignMeasurement withMarkerSize(double widthMm, double heightMm) {
        return new SignMeasurement(corners.clone(), widthMm, heightMm, confidence, source);
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "SignMeasurement{%.0fx%.0f px, %.1fx%.1f mm, conf=%.2f, %s}",
                rulerWidthPx(), rulerHeightPx(), markerWidthMm, markerHeightMm,
                confidence, source);
    }

    // ------------------------------------------------------------- Parcelable

    protected SignMeasurement(Parcel in) {
        corners = in.createDoubleArray();
        markerWidthMm = in.readDouble();
        markerHeightMm = in.readDouble();
        confidence = in.readFloat();
        source = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDoubleArray(corners);
        dest.writeDouble(markerWidthMm);
        dest.writeDouble(markerHeightMm);
        dest.writeFloat(confidence);
        dest.writeString(source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SignMeasurement> CREATOR = new Creator<SignMeasurement>() {
        @Override
        public SignMeasurement createFromParcel(Parcel in) {
            return new SignMeasurement(in);
        }

        @Override
        public SignMeasurement[] newArray(int size) {
            return new SignMeasurement[size];
        }
    };
}
