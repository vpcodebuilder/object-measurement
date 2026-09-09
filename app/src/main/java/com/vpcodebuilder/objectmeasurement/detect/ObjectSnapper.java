package com.vpcodebuilder.objectmeasurement.detect;

import android.graphics.Bitmap;
import android.graphics.Rect;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/**
 * Shrinks a hand-drawn selection box onto the object actually inside it.
 *
 * <p>Dragging eight handles to the exact edge of an object on a phone screen is
 * fiddly and is the main source of operator error in this app — a box 5% too
 * wide is a measurement 5% too large. "Auto-fit" gets the user most of the way
 * there in one tap; they can still nudge the handles afterwards.</p>
 *
 * <p>Works from the edge energy inside the box: rows and columns whose edge
 * response is negligible are trimmed away from each side.</p>
 */
public final class ObjectSnapper {

    private ObjectSnapper() {
    }

    /**
     * @param image the full image the box is drawn on
     * @param box   selection in image pixels
     * @return a tightened box, or {@code box} itself when nothing convincing was found
     */
    public static Rect snap(Bitmap image, Rect box) {
        if (image == null || box == null) return box;

        int left = Math.max(0, box.left);
        int top = Math.max(0, box.top);
        int right = Math.min(image.getWidth(), box.right);
        int bottom = Math.min(image.getHeight(), box.bottom);
        if (right - left < 16 || bottom - top < 16) return box;

        Mat rgba = new Mat();
        Mat roi = null;
        Mat gray = new Mat();
        Mat edges = null;
        Mat colProfile = new Mat();
        Mat rowProfile = new Mat();

        try {
            Utils.bitmapToMat(image, rgba);
            roi = rgba.submat(top, bottom, left, right);
            Imgproc.cvtColor(roi, gray, Imgproc.COLOR_RGBA2GRAY);

            edges = ImageOps.edgeMask(gray);
            // A little dilation lets a dashed or textured outline register as one edge.
            Mat kernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_RECT, new org.opencv.core.Size(3, 3));
            try {
                Imgproc.dilate(edges, edges, kernel);
            } finally {
                kernel.release();
            }

            Core.reduce(edges, colProfile, 0, Core.REDUCE_SUM, CvType.CV_32S);
            Core.reduce(edges, rowProfile, 1, Core.REDUCE_SUM, CvType.CV_32S);

            int[] cols = new int[colProfile.cols()];
            int[] rows = new int[rowProfile.rows()];
            colProfile.get(0, 0, cols);
            rowProfile.get(0, 0, rows);

            int[] xSpan = activeSpan(cols);
            int[] ySpan = activeSpan(rows);
            if (xSpan == null || ySpan == null) return box;

            // Give the object a couple of pixels of breathing room.
            int pad = 2;
            int newLeft = left + Math.max(0, xSpan[0] - pad);
            int newRight = left + Math.min(cols.length - 1, xSpan[1] + pad) + 1;
            int newTop = top + Math.max(0, ySpan[0] - pad);
            int newBottom = top + Math.min(rows.length - 1, ySpan[1] + pad) + 1;

            if (newRight - newLeft < 8 || newBottom - newTop < 8) return box;
            return new Rect(newLeft, newTop, newRight, newBottom);
        } catch (Exception ex) {
            return box;
        } finally {
            rgba.release();
            if (roi != null) roi.release();
            gray.release();
            if (edges != null) edges.release();
            colProfile.release();
            rowProfile.release();
        }
    }

    /**
     * First and last index whose value clears a fraction of the profile's peak.
     * Returns {@code null} when the profile is flat (nothing to snap onto).
     */
    private static int[] activeSpan(int[] profile) {
        int peak = 0;
        for (int v : profile) peak = Math.max(peak, v);
        if (peak <= 0) return null;

        int threshold = Math.max(255, (int) (peak * 0.08));
        int first = -1;
        int last = -1;
        for (int i = 0; i < profile.length; i++) {
            if (profile[i] >= threshold) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0 || last <= first) return null;
        return new int[]{first, last};
    }
}
