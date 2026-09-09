package com.vpcodebuilder.objectmeasurement.detect;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the rectangular reference marker in a frame.
 *
 * <p>Rewritten from the old {@code MeasurementDetector}, which had a number of
 * defects that all pushed measurements in the same (wrong) direction:</p>
 * <ul>
 *   <li>It ran {@code Canny} over an already-binary mask and then dilated the
 *       result, so the contour it measured was the <em>outside</em> of a thickened
 *       edge band — every marker came out a couple of pixels too large, and every
 *       object therefore too small.</li>
 *   <li>It took {@code minAreaRect(...).boundingRect()}, the axis-aligned box
 *       around a rotated rectangle. For a marker tilted 20° that box is roughly
 *       30% larger than the marker.</li>
 *   <li>Its corner-ordering sorted by y alone, which mislabels the corners of any
 *       rotated marker, so "width" and "height" could be measured along the
 *       diagonals.</li>
 *   <li>Morphology used uninitialised kernels, and the right-angle test compared
 *       degrees against a variable named for cosines.</li>
 * </ul>
 *
 * <p>The replacement scores every quadrilateral candidate on squareness,
 * convexity, solidity and how well its aspect ratio matches the configured
 * marker, refines the winner's corners to sub-pixel accuracy, and reports a
 * confidence so the UI can tell the user when a reading is shaky.</p>
 */
public final class MarkerDetector {

    /** Progressively coarser simplification until a contour collapses to 4 points. */
    private static final double[] EPSILON_STEPS = {0.02, 0.03, 0.045, 0.06};

    private MarkerDetector() {
    }

    // --------------------------------------------------------------- public API

    /** Detect in a bitmap. Returns {@code null} when no acceptable marker is found. */
    public static SignMeasurement detect(Bitmap source, DetectorConfig cfg) {
        if (source == null) return null;
        Mat rgba = new Mat();
        try {
            Utils.bitmapToMat(source, rgba);
            return detect(rgba, cfg);
        } finally {
            rgba.release();
        }
    }

    /**
     * Detect in an OpenCV frame. The input is never modified, so a live camera
     * frame can be passed straight in.
     */
    public static SignMeasurement detect(Mat source, DetectorConfig cfg) {
        if (source == null || source.empty() || cfg == null) return null;

        Mat work = new Mat();
        Mat rgb = new Mat();
        Mat gray = new Mat();
        Mat mask = null;

        try {
            double scale = 1.0;
            if (source.width() > cfg.workingWidth && cfg.workingWidth > 0) {
                scale = cfg.workingWidth / (double) source.width();
                Imgproc.resize(source, work, new Size(), scale, scale, Imgproc.INTER_AREA);
            } else {
                source.copyTo(work);
            }

            toRgb(work, rgb);
            if (cfg.colorBalancePercent > 0) {
                ImageOps.colorBalance(rgb, cfg.colorBalancePercent);
            }
            Imgproc.medianBlur(rgb, rgb, 5);
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);

            double frameArea = (double) rgb.rows() * rgb.cols();
            Candidate best = null;
            String usedSource = null;

            if (cfg.mode == DetectorConfig.MODE_COLOR || cfg.mode == DetectorConfig.MODE_AUTO) {
                mask = ImageOps.hsvMask(rgb, cfg);
                ImageOps.clean(mask, 5);
                ImageOps.clearBorder(mask, 2);
                best = bestCandidate(mask, frameArea, cfg);
                if (best != null) usedSource = SignMeasurement.SOURCE_COLOR;
                mask.release();
                mask = null;
            }

            if (best == null
                    && (cfg.mode == DetectorConfig.MODE_EDGE || cfg.mode == DetectorConfig.MODE_AUTO)) {
                mask = ImageOps.edgeMask(gray);
                ImageOps.clearBorder(mask, 2);
                best = bestCandidate(mask, frameArea, cfg);
                if (best != null) usedSource = SignMeasurement.SOURCE_EDGE;
                mask.release();
                mask = null;
            }

            if (best == null) return null;

            Point[] corners = best.corners;
            if (cfg.refineCorners) {
                corners = ImageOps.refineCorners(gray, corners);
            }

            // Back to the coordinate space of the frame the caller handed us.
            if (scale != 1.0) {
                double inverse = 1.0 / scale;
                Point[] full = new Point[4];
                for (int i = 0; i < 4; i++) {
                    full[i] = new Point(corners[i].x * inverse, corners[i].y * inverse);
                }
                corners = full;
            }

            return new SignMeasurement(corners,
                    cfg.markerWidthMm, cfg.markerHeightMm,
                    (float) best.score, usedSource);
        } catch (Exception ex) {
            return null;
        } finally {
            work.release();
            rgb.release();
            gray.release();
            if (mask != null) mask.release();
        }
    }

    /**
     * Mask preview for the tuning screen: white where the HSV gate passes, with
     * the winning quad outlined in green.
     */
    public static Bitmap maskPreview(Bitmap source, DetectorConfig cfg) {
        if (source == null) return null;

        Mat rgba = new Mat();
        Mat work = new Mat();
        Mat rgb = new Mat();
        Mat mask = new Mat();
        Mat preview = new Mat();

        try {
            Utils.bitmapToMat(source, rgba);

            double scale = 1.0;
            if (rgba.width() > cfg.workingWidth && cfg.workingWidth > 0) {
                scale = cfg.workingWidth / (double) rgba.width();
                Imgproc.resize(rgba, work, new Size(), scale, scale, Imgproc.INTER_AREA);
            } else {
                rgba.copyTo(work);
            }

            toRgb(work, rgb);
            if (cfg.colorBalancePercent > 0) {
                ImageOps.colorBalance(rgb, cfg.colorBalancePercent);
            }
            Imgproc.medianBlur(rgb, rgb, 5);

            if (cfg.mode == DetectorConfig.MODE_EDGE) {
                Mat gray = new Mat();
                try {
                    Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);
                    Mat edges = ImageOps.edgeMask(gray);
                    edges.copyTo(mask);
                    edges.release();
                } finally {
                    gray.release();
                }
            } else {
                Mat hsv = ImageOps.hsvMask(rgb, cfg);
                try {
                    ImageOps.clean(hsv, 5);
                    hsv.copyTo(mask);
                } finally {
                    hsv.release();
                }
            }
            ImageOps.clearBorder(mask, 2);

            Imgproc.cvtColor(mask, preview, Imgproc.COLOR_GRAY2RGB);

            Candidate best = bestCandidate(mask, (double) rgb.rows() * rgb.cols(), cfg);
            if (best != null) {
                drawQuad(preview, best.corners, new Scalar(0, 224, 122), 3);
            }

            Bitmap out = Bitmap.createBitmap(preview.cols(), preview.rows(),
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(preview, out);
            return out;
        } catch (Exception ex) {
            return null;
        } finally {
            rgba.release();
            work.release();
            rgb.release();
            mask.release();
            preview.release();
        }
    }

    /**
     * Sample the HSV statistics of a small patch and turn them into a detector
     * range. This is what lets the settings screen say "tap the marker" instead of
     * making the user hand-tune six sliders.
     *
     * @param bitmap  source photo
     * @param x       tap position in bitmap pixels
     * @param y       tap position in bitmap pixels
     * @param radius  patch radius in pixels
     * @return a config with only the HSV fields populated, or {@code null}
     */
    public static DetectorConfig sampleColorAt(Bitmap bitmap, int x, int y, int radius) {
        if (bitmap == null) return null;

        int left = Math.max(0, x - radius);
        int top = Math.max(0, y - radius);
        int right = Math.min(bitmap.getWidth(), x + radius);
        int bottom = Math.min(bitmap.getHeight(), y + radius);
        if (right - left < 3 || bottom - top < 3) return null;

        Mat rgba = new Mat();
        Mat hsv = new Mat();
        try {
            Utils.bitmapToMat(bitmap, rgba);
            Mat roi = rgba.submat(top, bottom, left, right);
            Mat rgb = new Mat();
            try {
                toRgb(roi, rgb);
                Imgproc.medianBlur(rgb, rgb, 3);
                Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV);
            } finally {
                roi.release();
                rgb.release();
            }

            int count = hsv.rows() * hsv.cols();
            byte[] buffer = new byte[count * 3];
            hsv.get(0, 0, buffer);

            // Hue is circular: average it as a unit vector, not as a scalar.
            double sumSin = 0;
            double sumCos = 0;
            int[] sat = new int[count];
            int[] val = new int[count];
            for (int i = 0; i < count; i++) {
                int h = buffer[i * 3] & 0xFF;
                sat[i] = buffer[i * 3 + 1] & 0xFF;
                val[i] = buffer[i * 3 + 2] & 0xFF;
                double angle = h * 2.0 * Math.PI / 180.0;
                sumSin += Math.sin(angle);
                sumCos += Math.cos(angle);
            }

            double meanAngle = Math.atan2(sumSin / count, sumCos / count);
            int meanHue = (int) Math.round(((meanAngle < 0 ? meanAngle + 2 * Math.PI : meanAngle)
                    * 180.0) / (2 * Math.PI));
            meanHue = ((meanHue % 180) + 180) % 180;

            java.util.Arrays.sort(sat);
            java.util.Arrays.sort(val);
            int satLow = sat[(int) (count * 0.10)];
            int satHigh = sat[(int) (count * 0.90)];
            int valLow = val[(int) (count * 0.10)];
            int valHigh = val[(int) (count * 0.90)];

            DetectorConfig c = new DetectorConfig();
            int hueMargin = 12;
            c.hueLower = ((meanHue - hueMargin) % 180 + 180) % 180;
            c.hueUpper = (meanHue + hueMargin) % 180;
            c.satLower = clamp(satLow - 45, 0, 255);
            c.satUpper = clamp(satHigh + 55, 0, 255);
            c.valLower = clamp(valLow - 55, 0, 255);
            c.valUpper = clamp(valHigh + 55, 0, 255);
            return c;
        } catch (Exception ex) {
            return null;
        } finally {
            rgba.release();
            hsv.release();
        }
    }

    /** Draw the marker outline and corner dots onto an RGBA frame. */
    public static void drawQuad(Mat frame, Point[] corners, Scalar color, int thickness) {
        if (frame == null || corners == null || corners.length != 4) return;
        for (int i = 0; i < 4; i++) {
            Imgproc.line(frame, corners[i], corners[(i + 1) % 4], color, thickness);
        }
        for (Point p : corners) {
            Imgproc.circle(frame, p, thickness * 2, color, -1);
        }
    }

    // ---------------------------------------------------------------- internals

    private static void toRgb(Mat src, Mat dstRgb) {
        int channels = src.channels();
        if (channels == 4) {
            Imgproc.cvtColor(src, dstRgb, Imgproc.COLOR_RGBA2RGB);
        } else if (channels == 1) {
            Imgproc.cvtColor(src, dstRgb, Imgproc.COLOR_GRAY2RGB);
        } else {
            src.copyTo(dstRgb);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Candidate {
        final Point[] corners;
        final double score;
        final double area;

        Candidate(Point[] corners, double score, double area) {
            this.corners = corners;
            this.score = score;
            this.area = area;
        }
    }

    private static Candidate bestCandidate(Mat binary, double frameArea, DetectorConfig cfg) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Candidate best = null;

        try {
            // Straight from the mask. The old pipeline inserted a Canny + dilate
            // step here, which is what inflated every detected marker.
            Imgproc.findContours(binary, contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            double minArea = frameArea * cfg.minAreaRatio;
            double maxArea = frameArea * cfg.maxAreaRatio;

            for (MatOfPoint contour : contours) {
                double area = Math.abs(Imgproc.contourArea(contour));
                if (area < minArea || area > maxArea) continue;

                Candidate candidate = evaluate(contour, cfg);
                if (candidate == null) continue;

                if (best == null
                        || candidate.score > best.score + 1e-6
                        || (Math.abs(candidate.score - best.score) <= 1e-6
                        && candidate.area > best.area)) {
                    best = candidate;
                }
            }
        } finally {
            ImageOps.releaseAll(contours);
            hierarchy.release();
        }

        return best;
    }

    private static Candidate evaluate(MatOfPoint contour, DetectorConfig cfg) {
        MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
        MatOfPoint2f approx = new MatOfPoint2f();

        try {
            double perimeter = Imgproc.arcLength(curve, true);
            if (perimeter <= 0) return null;

            Point[] quad = null;
            for (double step : EPSILON_STEPS) {
                Imgproc.approxPolyDP(curve, approx, perimeter * step, true);
                if (approx.total() == 4) {
                    quad = approx.toArray();
                    break;
                }
            }
            if (quad == null) return null;
            if (!ImageOps.isConvex(quad)) return null;

            double quadArea = ImageOps.polygonArea(quad);
            if (quadArea <= 0) return null;

            Point[] ordered = ImageOps.orderCorners(quad);

            // --- squareness -------------------------------------------------
            double maxDeviation = 0;
            for (int i = 0; i < 4; i++) {
                double angle = ImageOps.angleAt(
                        ordered[(i + 3) % 4], ordered[i], ordered[(i + 1) % 4]);
                maxDeviation = Math.max(maxDeviation, Math.abs(angle - 90.0));
            }
            if (maxDeviation > cfg.maxCornerDeviationDeg) return null;
            double angleScore = 1.0 - (maxDeviation / cfg.maxCornerDeviationDeg);

            // --- how convex the raw blob was --------------------------------
            Point[] contourPoints = contour.toArray();
            double contourArea = Math.abs(Imgproc.contourArea(contour));
            double hullArea = ImageOps.hullArea(contourPoints);
            double solidity = hullArea > 0 ? contourArea / hullArea : 0;
            if (solidity < cfg.minSolidity) return null;
            double solidityScore = normalise(solidity, cfg.minSolidity, 1.0);

            // --- how well the 4-gon explains the blob -----------------------
            double quadFit = Math.min(contourArea, quadArea) / Math.max(contourArea, quadArea);
            if (quadFit < cfg.minQuadFit) return null;
            double fitScore = normalise(quadFit, cfg.minQuadFit, 1.0);

            // --- aspect ratio, and marker orientation -----------------------
            double aspectScore = 0.5;
            if (cfg.hasMarkerSize()) {
                double observed = observedAspect(ordered);
                double expected = cfg.markerWidthMm / cfg.markerHeightMm;

                double direct = agreement(observed, expected);
                double rotated = agreement(observed, 1.0 / expected);

                // A marker held sideways is still a valid marker: rotate the
                // corner cycle so edge 0->1 always spans the marker's width.
                if (rotated > direct + 1e-6) {
                    ordered = ImageOps.rotateCorners(ordered);
                    direct = rotated;
                }
                if (direct < cfg.minAspectAgreement) return null;
                aspectScore = normalise(direct, cfg.minAspectAgreement, 1.0);
            }

            double score = 0.34 * angleScore
                    + 0.26 * aspectScore
                    + 0.22 * solidityScore
                    + 0.18 * fitScore;

            return new Candidate(ordered, score, quadArea);
        } catch (Exception ex) {
            return null;
        } finally {
            curve.release();
            approx.release();
        }
    }

    private static double observedAspect(Point[] ordered) {
        double width = (ImageOps.distance(ordered[0], ordered[1])
                + ImageOps.distance(ordered[3], ordered[2])) / 2.0;
        double height = (ImageOps.distance(ordered[0], ordered[3])
                + ImageOps.distance(ordered[1], ordered[2])) / 2.0;
        return height > 0 ? width / height : 0;
    }

    /** 1.0 when the two ratios match, falling towards 0 as they diverge. */
    private static double agreement(double a, double b) {
        if (a <= 0 || b <= 0) return 0;
        return Math.min(a, b) / Math.max(a, b);
    }

    private static double normalise(double value, double low, double high) {
        if (high <= low) return 1;
        double t = (value - low) / (high - low);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }
}
