package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.vpcodebuilder.objectmeasurement.detect.LengthUnit;
import com.vpcodebuilder.objectmeasurement.detect.MeasuredSize;
import com.vpcodebuilder.objectmeasurement.detect.SignMeasurement;

import org.opencv.core.Point;

/**
 * The selection surface: a photo, the detected marker, and a draggable box the
 * user places around the object.
 *
 * <p>Two structural changes over the original:</p>
 * <ul>
 *   <li>The selection is stored in <em>image</em> pixels, not view pixels. The old
 *       version kept view coordinates and re-derived them against a remembered
 *       layout rectangle on every rotation and state restore, which drifted a
 *       little each time. Image coordinates are what the measurement needs
 *       anyway, so the conversion now happens once, at draw and touch time.</li>
 *   <li>{@code onDraw} no longer allocates. The previous implementation called
 *       {@code Bitmap.createBitmap} twice per frame to punch out the bright
 *       region, which made dragging stutter and churned the heap; a clip
 *       rectangle does the same job for free.</li>
 * </ul>
 */
public class MeasurementGraphicView extends View {

    /** Notified while the box moves, so the host can show a live readout. */
    public interface OnMeasureListener {
        void onMeasureChanged(MeasuredSize size);
    }

    private enum Mode {NONE, RESIZE, MOVE}

    private static final int HANDLE_COUNT = 8;

    private final Paint dimPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint brightPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint cropPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap image;
    /** Where the bitmap is drawn, in view pixels. */
    private final Rect imageRect = new Rect();
    /** The selection, in image pixels. */
    private final RectF crop = new RectF();

    private SignMeasurement marker;
    private MeasuredSize measured = MeasuredSize.EMPTY;
    private LengthUnit unit = LengthUnit.MM;
    private boolean locked;

    private Mode mode = Mode.NONE;
    private int activeHandle = -1;
    private float lastX;
    private float lastY;

    private float handleRadius;
    private float minCropPx;
    private OnMeasureListener measureListener;

    public MeasurementGraphicView(Context context) {
        super(context);
        initialise();
    }

    public MeasurementGraphicView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialise();
    }

    private void initialise() {
        float density = getResources().getDisplayMetrics().density;
        handleRadius = dp(14);
        minCropPx = dp(24);

        dimPaint.setAlpha(70);

        cropPaint.setStyle(Paint.Style.STROKE);
        cropPaint.setColor(Color.WHITE);
        cropPaint.setStrokeWidth(2f * density);

        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setColor(0xFF00E07A);
        markerPaint.setStrokeWidth(2f * density);

        handleOuterPaint.setColor(0xB4186584);
        handleInnerPaint.setColor(0xE631B6E7);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        labelPaint.setTextSize(dp(14));

        labelBackPaint.setColor(0xCC000000);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------ input

    public void setImage(Bitmap bitmap, @Nullable SignMeasurement marker) {
        this.image = bitmap;
        this.marker = marker;
        this.locked = false;
        this.measured = MeasuredSize.EMPTY;
        crop.setEmpty();
        cropPaint.setColor(Color.WHITE);
        computeLayout();
        invalidate();
    }

    public void setUnit(LengthUnit unit) {
        this.unit = unit == null ? LengthUnit.MM : unit;
        invalidate();
    }

    public void setOnMeasureListener(OnMeasureListener listener) {
        this.measureListener = listener;
    }

    public boolean hasImage() {
        return image != null;
    }

    public boolean isLocked() {
        return locked;
    }

    @Nullable
    public SignMeasurement getMarker() {
        return marker;
    }

    public MeasuredSize getMeasured() {
        return measured;
    }

    /** The current selection in image pixels — this is what gets measured. */
    public Rect getCropInImage() {
        ensureCrop();
        return new Rect(
                Math.round(crop.left), Math.round(crop.top),
                Math.round(crop.right), Math.round(crop.bottom));
    }

    public void setCropInImage(Rect rect) {
        if (rect == null || image == null) return;
        crop.set(
                clamp(rect.left, 0, image.getWidth()),
                clamp(rect.top, 0, image.getHeight()),
                clamp(rect.right, 0, image.getWidth()),
                clamp(rect.bottom, 0, image.getHeight()));
        recomputeMeasurement();
        invalidate();
    }

    /** Freeze the selection and show the result. */
    public void lockMeasurement(MeasuredSize size) {
        this.measured = size == null ? MeasuredSize.EMPTY : size;
        this.locked = true;
        cropPaint.setColor(0xFFFF8000);
        invalidate();
    }

    public void unlockMeasurement() {
        this.locked = false;
        this.measured = MeasuredSize.EMPTY;
        cropPaint.setColor(Color.WHITE);
        invalidate();
        notifyMeasure();
    }

    // ----------------------------------------------------------------- layout

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeLayout();
    }

    private void computeLayout() {
        if (image == null || getWidth() == 0 || getHeight() == 0) {
            imageRect.setEmpty();
            return;
        }
        float viewAspect = getWidth() / (float) getHeight();
        float imageAspect = image.getWidth() / (float) image.getHeight();

        int width;
        int height;
        if (imageAspect > viewAspect) {
            width = getWidth();
            height = Math.max(1, Math.round(width / imageAspect));
        } else {
            height = getHeight();
            width = Math.max(1, Math.round(height * imageAspect));
        }
        int left = (getWidth() - width) / 2;
        int top = (getHeight() - height) / 2;
        imageRect.set(left, top, left + width, top + height);
        if (ensureCrop()) recomputeMeasurement();
    }

    /** @return true when a default selection had to be created */
    private boolean ensureCrop() {
        if (image == null || !crop.isEmpty()) return false;
        // Start with a box covering the middle half of the frame.
        float w = image.getWidth() * 0.45f;
        float h = image.getHeight() * 0.35f;
        float cx = image.getWidth() / 2f;
        float cy = image.getHeight() / 2f;
        crop.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        return true;
    }

    // ------------------------------------------------------ coordinate mapping

    private float imageToViewX(float x) {
        if (image == null || imageRect.isEmpty()) return x;
        return imageRect.left + x * imageRect.width() / (float) image.getWidth();
    }

    private float imageToViewY(float y) {
        if (image == null || imageRect.isEmpty()) return y;
        return imageRect.top + y * imageRect.height() / (float) image.getHeight();
    }

    private float viewToImageX(float x) {
        if (image == null || imageRect.isEmpty()) return x;
        return (x - imageRect.left) * image.getWidth() / (float) imageRect.width();
    }

    private float viewToImageY(float y) {
        if (image == null || imageRect.isEmpty()) return y;
        return (y - imageRect.top) * image.getHeight() / (float) imageRect.height();
    }

    private RectF cropInView() {
        return new RectF(
                imageToViewX(crop.left), imageToViewY(crop.top),
                imageToViewX(crop.right), imageToViewY(crop.bottom));
    }

    // ------------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (image == null || imageRect.isEmpty()) return;

        ensureCrop();
        RectF view = cropInView();

        // Dimmed everywhere, full brightness inside the selection.
        canvas.drawBitmap(image, null, imageRect, dimPaint);
        canvas.save();
        canvas.clipRect(view);
        canvas.drawBitmap(image, null, imageRect, brightPaint);
        canvas.restore();

        drawMarker(canvas);
        canvas.drawRect(view, cropPaint);

        if (locked) {
            drawResultLabels(canvas, view);
        } else {
            for (int i = 0; i < HANDLE_COUNT; i++) {
                drawHandle(canvas, handleX(i, view), handleY(i, view));
            }
            if (!measured.isEmpty()) {
                drawResultLabels(canvas, view);
            }
        }
    }

    private void drawMarker(Canvas canvas) {
        if (marker == null) return;
        Point[] corners = marker.cornerPoints();
        for (int i = 0; i < 4; i++) {
            Point a = corners[i];
            Point b = corners[(i + 1) % 4];
            canvas.drawLine(
                    imageToViewX((float) a.x), imageToViewY((float) a.y),
                    imageToViewX((float) b.x), imageToViewY((float) b.y),
                    markerPaint);
        }
    }

    private void drawHandle(Canvas canvas, float cx, float cy) {
        canvas.drawCircle(cx, cy, handleRadius, handleOuterPaint);
        canvas.drawCircle(cx, cy, handleRadius * 0.4f, handleInnerPaint);
    }

    private void drawResultLabels(Canvas canvas, RectF view) {
        if (measured.isEmpty()) return;

        String width = unit.format(measured.widthMm);
        String height = unit.format(measured.heightMm);

        float padding = dp(6);
        float textHeight = labelPaint.getTextSize();

        // Width label, above the box (below it when there is no room).
        float widthText = labelPaint.measureText(width);
        float wx = view.centerX() - widthText / 2f;
        float wy = view.top - padding * 1.5f;
        if (wy - textHeight < 0) wy = view.top + textHeight + padding * 1.5f;
        drawLabel(canvas, width, wx, wy, padding);

        // Height label, rotated along the left edge.
        float heightText = labelPaint.measureText(height);
        float hx = view.left - padding * 1.5f;
        if (hx - textHeight < 0) hx = view.left + textHeight + padding * 1.5f;
        float hy = view.centerY() + heightText / 2f;

        canvas.save();
        canvas.rotate(-90, hx, hy);
        drawLabel(canvas, height, hx, hy, padding);
        canvas.restore();
    }

    private void drawLabel(Canvas canvas, String text, float x, float baselineY, float padding) {
        float width = labelPaint.measureText(text);
        float ascent = -labelPaint.ascent();
        float descent = labelPaint.descent();
        canvas.drawRoundRect(
                x - padding, baselineY - ascent - padding / 2f,
                x + width + padding, baselineY + descent + padding / 2f,
                padding, padding, labelBackPaint);
        canvas.drawText(text, x, baselineY, labelPaint);
    }

    // ------------------------------------------------------------------ touch

    private float handleX(int index, RectF r) {
        switch (index) {
            case 0: case 6: case 7: return r.left;
            case 2: case 3: case 4: return r.right;
            default: return r.centerX();
        }
    }

    private float handleY(int index, RectF r) {
        switch (index) {
            case 0: case 1: case 2: return r.top;
            case 4: case 5: case 6: return r.bottom;
            default: return r.centerY();
        }
    }

    private int hitTest(float x, float y) {
        RectF view = cropInView();
        float slack = handleRadius * 1.3f;
        for (int i = 0; i < HANDLE_COUNT; i++) {
            if (Math.hypot(x - handleX(i, view), y - handleY(i, view)) <= slack) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (locked || image == null || imageRect.isEmpty()) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = hitTest(x, y);
                if (activeHandle >= 0) {
                    mode = Mode.RESIZE;
                } else if (cropInView().contains(x, y)) {
                    mode = Mode.MOVE;
                } else {
                    mode = Mode.NONE;
                    return false;
                }
                lastX = x;
                lastY = y;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (mode == Mode.RESIZE) {
                    resize(activeHandle, viewToImageX(x), viewToImageY(y));
                } else if (mode == Mode.MOVE) {
                    translate(viewToImageX(x) - viewToImageX(lastX),
                            viewToImageY(y) - viewToImageY(lastY));
                } else {
                    return false;
                }
                lastX = x;
                lastY = y;
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                normalise();
                mode = Mode.NONE;
                activeHandle = -1;
                performClick();
                return true;

            default:
                return false;
        }
    }

    private void resize(int handle, float ix, float iy) {
        switch (handle) {
            case 0: crop.left = ix; crop.top = iy; break;
            case 1: crop.top = iy; break;
            case 2: crop.right = ix; crop.top = iy; break;
            case 3: crop.right = ix; break;
            case 4: crop.right = ix; crop.bottom = iy; break;
            case 5: crop.bottom = iy; break;
            case 6: crop.left = ix; crop.bottom = iy; break;
            case 7: crop.left = ix; break;
            default: return;
        }
        clampToImage();
        recomputeMeasurement();
        invalidate();
    }

    private void translate(float dx, float dy) {
        float width = crop.width();
        float height = crop.height();

        crop.offset(dx, dy);
        if (crop.left < 0) crop.offsetTo(0, crop.top);
        if (crop.top < 0) crop.offsetTo(crop.left, 0);
        if (crop.right > image.getWidth()) crop.offsetTo(image.getWidth() - width, crop.top);
        if (crop.bottom > image.getHeight()) crop.offsetTo(crop.left, image.getHeight() - height);

        recomputeMeasurement();
        invalidate();
    }

    private void clampToImage() {
        crop.left = clamp(crop.left, 0, image.getWidth());
        crop.right = clamp(crop.right, 0, image.getWidth());
        crop.top = clamp(crop.top, 0, image.getHeight());
        crop.bottom = clamp(crop.bottom, 0, image.getHeight());
    }

    private void normalise() {
        if (crop.left > crop.right) {
            float t = crop.left;
            crop.left = crop.right;
            crop.right = t;
        }
        if (crop.top > crop.bottom) {
            float t = crop.top;
            crop.top = crop.bottom;
            crop.bottom = t;
        }
        // Keep the box big enough to still be grabbable.
        float minImagePx = minCropPx * image.getWidth() / Math.max(1, imageRect.width());
        if (crop.width() < minImagePx) crop.right = crop.left + minImagePx;
        if (crop.height() < minImagePx) crop.bottom = crop.top + minImagePx;
        clampToImage();
        recomputeMeasurement();
        invalidate();
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    // ----------------------------------------------------------- measurement

    private void recomputeMeasurement() {
        if (marker == null || !marker.hasMarkerSize() || crop.isEmpty()) {
            measured = MeasuredSize.EMPTY;
        } else {
            measured = marker.measure(getCropInImage());
        }
        notifyMeasure();
    }

    /**
     * Delivered on the next frame rather than inline: recomputation can be
     * triggered from {@code onSizeChanged} and {@code onDraw}, and hosts respond
     * by setting text on another view — a layout request from inside a layout or
     * draw pass.
     */
    private void notifyMeasure() {
        if (measureListener == null) return;
        final MeasuredSize snapshot = measured;
        post(() -> {
            if (measureListener != null) measureListener.onMeasureChanged(snapshot);
        });
    }

    // ---------------------------------------------------------------- results

    /**
     * The annotated image, at full resolution, for saving and sharing.
     * Rendered against a fresh canvas rather than the on-screen one so the export
     * is not tied to the current view size.
     */
    public Bitmap renderResult() {
        if (image == null) return null;

        Bitmap out = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Rect full = new Rect(0, 0, image.getWidth(), image.getHeight());
        Rect box = getCropInImage();

        canvas.drawBitmap(image, null, full, dimPaint);
        canvas.save();
        canvas.clipRect(box);
        canvas.drawBitmap(image, null, full, brightPaint);
        canvas.restore();

        // Scale the annotation strokes to the exported resolution.
        float scale = Math.max(1f, image.getWidth() / 720f);
        Paint exportCrop = new Paint(cropPaint);
        exportCrop.setStrokeWidth(cropPaint.getStrokeWidth() * scale);
        Paint exportMarker = new Paint(markerPaint);
        exportMarker.setStrokeWidth(markerPaint.getStrokeWidth() * scale);
        Paint exportLabel = new Paint(labelPaint);
        exportLabel.setTextSize(labelPaint.getTextSize() * scale);

        if (marker != null) {
            Point[] corners = marker.cornerPoints();
            for (int i = 0; i < 4; i++) {
                Point a = corners[i];
                Point b = corners[(i + 1) % 4];
                canvas.drawLine((float) a.x, (float) a.y, (float) b.x, (float) b.y, exportMarker);
            }
        }
        canvas.drawRect(box, exportCrop);

        if (!measured.isEmpty()) {
            float padding = 6 * scale;
            String width = unit.format(measured.widthMm);
            String height = unit.format(measured.heightMm);

            float wx = box.centerX() - exportLabel.measureText(width) / 2f;
            float wy = box.top - padding * 1.5f;
            if (wy - exportLabel.getTextSize() < 0) {
                wy = box.top + exportLabel.getTextSize() + padding * 1.5f;
            }
            drawExportLabel(canvas, exportLabel, width, wx, wy, padding);

            float hx = box.left - padding * 1.5f;
            if (hx - exportLabel.getTextSize() < 0) {
                hx = box.left + exportLabel.getTextSize() + padding * 1.5f;
            }
            float hy = box.centerY() + exportLabel.measureText(height) / 2f;
            canvas.save();
            canvas.rotate(-90, hx, hy);
            drawExportLabel(canvas, exportLabel, height, hx, hy, padding);
            canvas.restore();
        }

        return out;
    }

    private void drawExportLabel(Canvas canvas, Paint paint, String text,
                                 float x, float baselineY, float padding) {
        float width = paint.measureText(text);
        canvas.drawRoundRect(
                x - padding, baselineY + paint.ascent() - padding / 2f,
                x + width + padding, baselineY + paint.descent() + padding / 2f,
                padding, padding, labelBackPaint);
        canvas.drawText(text, x, baselineY, paint);
    }

    // ------------------------------------------------------------------ state

    /**
     * Saves geometry only. The old version put the whole background bitmap into
     * the state Bundle, which risks {@code TransactionTooLargeException} on
     * rotation; the host re-decodes the photo from disk instead.
     */
    public void saveState(Bundle out) {
        out.putFloatArray("crop", new float[]{crop.left, crop.top, crop.right, crop.bottom});
        out.putBoolean("locked", locked);
        out.putDouble("measuredWidthMm", measured.widthMm);
        out.putDouble("measuredHeightMm", measured.heightMm);
        out.putBoolean("measuredPerspective", measured.perspectiveCorrected);
    }

    public void restoreState(Bundle in) {
        float[] values = in.getFloatArray("crop");
        if (values != null && values.length == 4) {
            crop.set(values[0], values[1], values[2], values[3]);
        }
        double width = in.getDouble("measuredWidthMm", 0);
        double height = in.getDouble("measuredHeightMm", 0);
        if (width > 0 && height > 0) {
            measured = new MeasuredSize(width, height,
                    in.getBoolean("measuredPerspective", false));
        }
        if (in.getBoolean("locked", false)) {
            lockMeasurement(measured);
        }
        invalidate();
    }
}
