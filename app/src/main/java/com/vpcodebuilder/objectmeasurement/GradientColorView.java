package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Hue strip showing which slice of the colour wheel the detector will accept.
 *
 * <p>Rewritten for two reasons. It used to rebuild the bar by calling
 * {@code Bitmap.setPixels} once per row on <em>every</em> {@code onDraw}, so
 * dragging a slider repainted tens of thousands of pixels per frame from Java. A
 * gradient shader draws the same thing on the GPU. It also mapped hue with
 * {@code 360 / width} while the sliders emit OpenCV's 0..179 scale, so the
 * highlighted band sat at roughly half the hue it claimed. And a range that
 * wraps past red — the one case that matters, because red splits across both
 * ends of the hue axis — is now drawn as two bands instead of vanishing.</p>
 */
public class GradientColorView extends View {

    /** OpenCV keeps hue in 0..179 so it fits in a byte. */
    private static final int HUE_MAX = 179;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int lower;
    private int upper = HUE_MAX;
    private float corner;

    public GradientColorView(Context context) {
        super(context);
        initialise();
    }

    public GradientColorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialise();
    }

    private void initialise() {
        float density = getResources().getDisplayMetrics().density;
        corner = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                getResources().getDisplayMetrics());

        dimPaint.setColor(0xB2000000);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setColor(Color.WHITE);
        edgePaint.setStrokeWidth(2f * density);
    }

    public void setRange(int lower, int upper) {
        this.lower = clamp(lower);
        this.upper = clamp(upper);
        invalidate();
    }

    /** True when the selection crosses the 179 -> 0 seam. */
    public boolean isWrapping() {
        return lower > upper;
    }

    private static int clamp(int hue) {
        return hue < 0 ? 0 : (hue > HUE_MAX ? HUE_MAX : hue);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0) return;

        int stops = 13;
        int[] colors = new int[stops];
        for (int i = 0; i < stops; i++) {
            float hueDegrees = (i / (float) (stops - 1)) * 360f;
            colors[i] = Color.HSVToColor(new float[]{Math.min(hueDegrees, 359.9f), 1f, 1f});
        }
        barPaint.setShader(new LinearGradient(0, 0, w, 0, colors, null,
                Shader.TileMode.CLAMP));
    }

    private float hueToX(int hue) {
        return getWidth() * (hue / (float) HUE_MAX);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;

        RectF bounds = new RectF(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(bounds, corner, corner, barPaint);

        // Dim everything outside the accepted hue range.
        if (isWrapping()) {
            canvas.drawRect(hueToX(upper), 0, hueToX(lower), getHeight(), dimPaint);
        } else {
            canvas.drawRect(0, 0, hueToX(lower), getHeight(), dimPaint);
            canvas.drawRect(hueToX(upper), 0, getWidth(), getHeight(), dimPaint);
        }

        float half = edgePaint.getStrokeWidth() / 2f;
        canvas.drawLine(hueToX(lower), half, hueToX(lower), getHeight() - half, edgePaint);
        canvas.drawLine(hueToX(upper), half, hueToX(upper), getHeight() - half, edgePaint);
    }
}
