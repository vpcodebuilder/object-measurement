package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.util.AttributeSet;
import android.view.View;

public class GradientColorView extends View {
	private Paint linePaint;
	private Bitmap colorBar = null;
	private int[] pixels;
	private int lower;
	private int upper;

	public GradientColorView(Context context) {
		super(context);
		this.initialize();
	}

	public GradientColorView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.initialize();
	}

	private void initialize() {
		linePaint = new Paint();
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setColor(Color.GRAY);
		linePaint.setStrokeWidth(1);
	}

	private void createColorBar() {
		colorBar = Bitmap.createBitmap(this.getWidth(), this.getHeight(), Config.ARGB_8888);
		pixels = new int[colorBar.getWidth()];
		double ratio = 360 / (double)colorBar.getWidth();

		for (int i = 0; i < colorBar.getWidth(); i++) {
			pixels[i] = Color.HSVToColor(new float[] { (float)(i * ratio), 1f, 1f });
		}
	}

	public void setRange(int lower, int upper) {
		this.lower = lower;
		this.upper = upper;
		this.invalidate();
	}

	private void drawColorBar(Canvas canvas) {
		if (colorBar == null) this.createColorBar();

		int stride = colorBar.getWidth() * 4;

		for (int y = 0; y < colorBar.getHeight(); y++) {
			colorBar.setPixels(pixels, 0, stride, 0, y, pixels.length, 1);
		}

		canvas.drawBitmap(colorBar, 0, 0, null);
	}

	private void drawValueRange(Canvas canvas) {
		double ratio = colorBar.getWidth() / 180.0;
		int lo = (int)(this.lower * ratio);
		int hi = (int)(this.upper * ratio);

		canvas.drawLine(lo, 0, lo, colorBar.getHeight(), linePaint);
		canvas.drawLine(hi, 0, hi, colorBar.getHeight(), linePaint);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		this.drawColorBar(canvas);
		this.drawValueRange(canvas);
	}
}