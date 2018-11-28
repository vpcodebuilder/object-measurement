package com.vpcodebuilder.objectmeasurement;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

import android.content.Context;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import org.opencv.core.*;

public class MeasurementGraphicView extends View {
	public enum SelectionMode {
		None,
		ReSize,
		Move
	}

	private Point screenSize;
	private Paint backgroundPaint;
	private Paint cropRectanglePaint;
	private Paint signRectanglePaint;
	private Paint internalTrackerPaint;
	private Paint externalTrackerPaint;
	private Paint textPaint;

	private double ratioTracker = 0.046875; // อัตราส่วน 640x480 ตัว Tracker เท่ากับ 30px = 30/640 = 0.046875
	private int trackerSize;
	private int handleNumber;
	private SelectionMode selectionMode = SelectionMode.None;
	private int currentLeft;
	private int currentTop;
	private int currentRight;
	private int currentBottom;
	private Point lastPoint;
	private Bitmap backgroundImage = null;
	private Bitmap backgroundImageScale = null;
	private Rect backgroundRect;
	private double objectMeterWidth;
	private double objectMeterHeight;
	private Rect signRect;
	private double rulerWidth;
	private double rulerHeight;
	private boolean isLockMeasurement = false;
	private boolean hasLoadStateComplete = false;

	public MeasurementGraphicView(Context context) {
		super(context);
		this.initialize();
	}

	public MeasurementGraphicView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.initialize();
	}

	public void saveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putInt("currentLeft", this.currentLeft);
		savedInstanceState.putInt("currentTop", this.currentTop);
		savedInstanceState.putInt("currentRight", this.currentRight);
		savedInstanceState.putInt("currentBottom", this.currentBottom);
		savedInstanceState.putParcelable("backgroundImage", this.backgroundImage);
		savedInstanceState.putParcelable("backgroundRect", this.backgroundRect);
		savedInstanceState.putDouble("objectMeterWidth", this.objectMeterWidth);
		savedInstanceState.putDouble("objectMeterHeight", this.objectMeterHeight);
		savedInstanceState.putParcelable("signRect", this.signRect);
		savedInstanceState.putDouble("rulerWidth", this.rulerWidth);
		savedInstanceState.putDouble("rulerHeight", this.rulerHeight);
		savedInstanceState.putBoolean("isLockMeasurement", this.isLockMeasurement);
	}

	public void getInstanceState(Bundle savedInstanceState) {
		this.currentLeft = savedInstanceState.getInt("currentLeft");
		this.currentTop = savedInstanceState.getInt("currentTop");
		this.currentRight = savedInstanceState.getInt("currentRight");
		this.currentBottom = savedInstanceState.getInt("currentBottom");
		this.backgroundImage = savedInstanceState.getParcelable("backgroundImage");
		this.backgroundRect = savedInstanceState.getParcelable("backgroundRect");
		this.objectMeterWidth = savedInstanceState.getDouble("objectMeterWidth");
		this.objectMeterHeight = savedInstanceState.getDouble("objectMeterHeight");
		this.signRect = savedInstanceState.getParcelable("signRect");
		this.rulerWidth = savedInstanceState.getDouble("rulerWidth");
		this.rulerHeight = savedInstanceState.getDouble("rulerHeight");
		this.isLockMeasurement = savedInstanceState.getBoolean("isLockMeasurement");

		if (this.isLockMeasurement) {
			this.cropRectanglePaint.setColor(Color.rgb(255, 128, 0));
		}

		screenSize = DeviceHelper.getScreenResolution(this.getContext());

		if (screenSize != null) {
			this.trackerSize = (int)(Math.max(screenSize.x, screenSize.y) * ratioTracker);
		}

		this.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
			if (hasLoadStateComplete) return;

			Rect backgroundRectOld = backgroundRect;

			if (backgroundRectOld == null) return;

			if (getWidth() < getHeight()) {
				int imageHeight = (int) (backgroundImage.getHeight() * (getWidth() / (double) backgroundImage.getWidth()));
				int top = (int) ((getHeight() / 2f) - (imageHeight / 2f));

				backgroundRect = new Rect(0, top, getWidth(), top + imageHeight);
			} else {
				int imageWidth = (int) (backgroundImage.getWidth() * (getHeight() / (double) backgroundImage.getHeight()));
				int left = (int) ((getWidth() / 2f) - (imageWidth / 2f));

				backgroundRect = new Rect(left, 0, left + imageWidth, getHeight());
			}

			double ratioWidth = backgroundRect.width() / (double) backgroundRectOld.width();
			double ratioHeight = backgroundRect.height() / (double) backgroundRectOld.height();

			currentLeft = (int) (backgroundRect.left + ((currentLeft - backgroundRectOld.left) * ratioWidth));
			currentTop = (int) (backgroundRect.top + ((currentTop - backgroundRectOld.top) * ratioHeight));
			currentRight = (int) (backgroundRect.left + ((currentRight - backgroundRectOld.left) * ratioWidth));
			currentBottom = (int) (backgroundRect.top + ((currentBottom - backgroundRectOld.top) * ratioHeight));

			invalidate();
			hasLoadStateComplete = true;
			}
		});
	}

	public boolean hasLockMeasurement() {
		return this.isLockMeasurement;
	}

	public double getObjectMeterWidth() {
		return this.objectMeterWidth;
	}

	public double getObjectMeterHeight() {
		return this.objectMeterHeight;
	}

	public void undoProcess() {
		if (this.isLockMeasurement) {
			this.cropRectanglePaint.setColor(Color.WHITE);
			this.objectMeterWidth = 0;
			this.objectMeterHeight = 0;
			this.isLockMeasurement = false;
			this.invalidate();
		}
	}

	private void initialize() {
		this.backgroundPaint = new Paint();
		this.backgroundPaint.setAlpha(80);

		this.internalTrackerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.internalTrackerPaint.setColor(Color.rgb(49, 182, 231));
		this.internalTrackerPaint.setAlpha(180);

		this.externalTrackerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.externalTrackerPaint.setColor(Color.rgb(24, 101, 132));
		this.externalTrackerPaint.setAlpha(180);

		this.cropRectanglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.cropRectanglePaint.setStyle(Paint.Style.STROKE);
		this.cropRectanglePaint.setColor(Color.WHITE);
		this.cropRectanglePaint.setStrokeWidth(2);

		this.signRectanglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.signRectanglePaint.setStyle(Paint.Style.STROKE);
		this.signRectanglePaint.setColor(Color.RED);
		this.signRectanglePaint.setStrokeWidth(2);

		this.textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		this.textPaint.setColor(Color.GREEN);
		this.textPaint.setTypeface(Typeface.DEFAULT_BOLD);

		screenSize = DeviceHelper.getScreenResolution(this.getContext());

		if (screenSize != null) {
			this.trackerSize = (int)(Math.max(screenSize.x, screenSize.y) * ratioTracker);
		}
	}

	private void checkCropInitialize() {
		if (currentLeft == 0 && currentTop == 0 && currentRight == 0 && currentBottom == 0) {
			int cropWidth = 256;
			int cropHeight = 128;

			currentLeft = (int)((getWidth() / 2.0) - (cropWidth / 2.0));
			currentTop = (int)((getHeight() / 2.0) - (cropHeight / 2.0));
			currentRight = currentLeft + cropWidth;
			currentBottom = currentTop + cropHeight;
		}
	}

	private Rect getCropRectangle()
	{
		int left, top, right, buttom;

		this.checkCropInitialize();

		if (currentLeft <= currentRight)
		{
			left = currentLeft;
			right = currentRight;
		}
		else
		{
			left = currentRight;
			right = currentLeft;
		}

		if (currentTop <= currentBottom)
		{
			top = currentTop;
			buttom = currentBottom;
		}
		else
		{
			top = currentBottom;
			buttom = currentTop;
		}

		return new Rect(left, top, right, buttom);
	}

	private Rect getHandleRectangle(int handleNumber)
	{
		int x = currentLeft;
		int y = currentTop;
		int xCenter = (int)((currentRight + currentLeft) / 2.0);
		int yCenter = (int)((currentBottom + currentTop) / 2.0);

		switch (handleNumber)
		{
			case 1:
				x = currentLeft;
				y = currentTop;
				break;
			case 2:
				x = xCenter;
				y = currentTop;
				break;
			case 3:
				x = currentRight;
				y = currentTop;
				break;
			case 4:
				x = currentRight;
				y = yCenter;
				break;
			case 5:
				x = currentRight;
				y = currentBottom;
				break;
			case 6:
				x = xCenter;
				y = currentBottom;
				break;
			case 7:
				x = currentLeft;
				y = currentBottom;
				break;
			case 8:
				x = currentLeft;
				y = yCenter;
				break;
		}

		Point point = new Point(x, y);
		double left = point.x - this.trackerSize / 2.0;
		double top = point.y - this.trackerSize / 2.0;

		return new Rect((int)left, (int)top, (int)(left + this.trackerSize) - 1, (int)(top + this.trackerSize) - 1);
	}

	private Rect getOriginalCropRect() {
		Rect cropRect = this.getCropRectangle();

		double ratioWidth = this.backgroundImage.getWidth() / (double)this.backgroundRect.width();
		double ratioHeight = this.backgroundImage.getHeight() / (double)this.backgroundRect.height();
		int left = (int)((cropRect.left - backgroundRect.left) * ratioWidth);
		int top = (int)((cropRect.top - backgroundRect.top) * ratioHeight);
		int right = (int)((cropRect.right - backgroundRect.left) * ratioWidth);
		int bottom = (int)((cropRect.bottom - backgroundRect.top) * ratioHeight);

		if (left < 0) left = 0;
		if (top < 0) top = 0;
		if (right >= this.backgroundImage.getWidth()) right = this.backgroundImage.getWidth() - 1;
		if (bottom >= this.backgroundImage.getHeight()) bottom = this.backgroundImage.getHeight() - 1;

		return new Rect(left, top, right, bottom);
	}

	public Bitmap getCropImage() {
		Rect originalCropRect = this.getOriginalCropRect();
		return Bitmap.createBitmap(this.backgroundImage,
				originalCropRect.left,
				originalCropRect.top,
				originalCropRect.width(),
				originalCropRect.height());
	}

	public SignMeasurement getSignMeasurement() {
		if (this.signRect == null) return null;

		return new SignMeasurement(
				new org.opencv.core.Rect(this.signRect.left, this.signRect.top, this.signRect.width(), this.signRect.height()),
				this.rulerWidth, this.rulerHeight);
	}

	public void setBackgroundImage(Bitmap bmp, SignMeasurement signMeasurement) {
		this.backgroundImage = bmp;
		this.signRect = SignMeasurement.ConvertToGraphicRectType(signMeasurement.getBoundary());
		this.rulerWidth = signMeasurement.getRulerWidth();
		this.rulerHeight = signMeasurement.getRulerHeight();
		this.invalidate();
	}

	public boolean isSetBackgroundComplete() {
		return this.backgroundImage != null;
	}

	public void setObjectMeasure(double objectMilimeterWidth, double objectMilimeterHeight, Rect signRectangle) {
		this.cropRectanglePaint.setColor(Color.rgb(255, 128, 0));
		this.objectMeterWidth = objectMilimeterWidth / 1000.0;
		this.objectMeterHeight = objectMilimeterHeight / 1000.0;
		this.isLockMeasurement = true;
		this.invalidate();
	}

	private int hitTest(Point position) {
		for (int i = 1; i <= 8; i++)
		{
			if (this.getHandleRectangle(i).contains(position.x, position.y)) {
				return i;
			}
		}

		return -1;
	}

	private void moveHandleTo(Point position, int handleNumber)
	{
		switch (handleNumber)
		{
			case 1:
				currentLeft = position.x;
				currentTop = position.y;
				break;
			case 2:
				currentTop = position.y;
				break;
			case 3:
				currentRight = position.x;
				currentTop = position.y;
				break;
			case 4:
				currentRight = position.x;
				break;
			case 5:
				currentRight = position.x;
				currentBottom = position.y;
				break;
			case 6:
				currentBottom = position.y;
				break;
			case 7:
				currentLeft = position.x;
				currentBottom = position.y;
				break;
			case 8:
				currentLeft = position.x;
				break;
		}

		if (currentLeft <= this.backgroundRect.left) currentLeft = this.backgroundRect.left;
		if (currentTop <= this.backgroundRect.top) currentTop = this.backgroundRect.top;
		if (currentRight >= this.backgroundRect.right) currentRight = this.backgroundRect.right;
		if (currentBottom >= this.backgroundRect.bottom) currentBottom = this.backgroundRect.bottom;

		this.invalidate();
	}

	private void move(int deltaX, int deltaY) {
		if (currentLeft + deltaX < this.backgroundRect.left) return;
		if (currentTop + deltaY < this.backgroundRect.top) return;
		if (currentRight + deltaX >= this.backgroundRect.right) return;
		if (currentBottom + deltaY >= this.backgroundRect.bottom) return;

		currentLeft += deltaX;
		currentTop += deltaY;
		currentRight += deltaX;
		currentBottom += deltaY;

		this.invalidate();
	}

	private void normalize()
	{
		if (currentLeft > currentRight)
		{
			int tmp = currentLeft;
			currentLeft = currentRight;
			currentRight = tmp;
		}

		if (currentTop > currentBottom)
		{
			int tmp = currentTop;
			currentTop = currentBottom;
			currentBottom = tmp;
		}
	}

	private void drawBackground(Canvas canvas, Rect cropRect) {
		if (this.backgroundImage != null) {
			int cropX;
			int cropY;

			if (canvas.getWidth() < canvas.getHeight()) {
				int imageHeight = (int)(backgroundImage.getHeight() * (this.getWidth() / (float)backgroundImage.getWidth()));
				int top = (int)((this.getHeight() / 2f) - (imageHeight / 2f));

				this.backgroundRect = new Rect(0, top, this.getWidth(), top + imageHeight);
				cropX = cropRect.left;
				cropY = cropRect.top - this.backgroundRect.top;
			} else {
				int imageWidth = (int)(backgroundImage.getWidth() * (this.getHeight() / (float)backgroundImage.getHeight()));
				int left = (int)((this.getWidth() / 2f) - (imageWidth / 2f));

				this.backgroundRect = new Rect(left, 0, left + imageWidth, this.getHeight());
				cropX = cropRect.left - this.backgroundRect.left;
				cropY = cropRect.top;
			}

			canvas.drawBitmap(this.backgroundImage, null, this.backgroundRect, backgroundPaint);

			if (this.backgroundImageScale == null) {
				this.backgroundImageScale = Bitmap.createScaledBitmap(this.backgroundImage, backgroundRect.width(), backgroundRect.height(), true);
			}

			if (cropRect.width() > 0 && cropRect.height() > 0) {
				canvas.drawBitmap(
						Bitmap.createBitmap(this.backgroundImageScale, cropX, cropY, cropRect.width(), cropRect.height()),
						cropRect.left,
						cropRect.top,
						null);
			}
		}
	}

	private void drawTracker(Canvas canvas, Rect rectangle) {
		if (this.trackerSize <= 0) return;

		float cx = rectangle.left + (rectangle.width() / 2f);
		float cy = rectangle.top + (rectangle.height() / 2f);
		float radius =  this.trackerSize / 2f;

		canvas.drawCircle(cx, cy, radius, externalTrackerPaint);
		canvas.drawCircle(cx, cy, radius * 0.35f, internalTrackerPaint);
	}

	private void drawSign(Canvas canvas, Rect rectangle) {
		if (this.signRect != null) {
			Rect signBoundingRect = new Rect(this.signRect);

			double ratioWidth = this.backgroundRect.width() / (double)this.backgroundImage.getWidth();
			double ratioHeight = this.backgroundRect.height() / (double)this.backgroundImage.getHeight();

			signBoundingRect.left = (int)(backgroundRect.left + (signBoundingRect.left * ratioWidth));
			signBoundingRect.top = (int)(backgroundRect.top + (signBoundingRect.top * ratioHeight));
			signBoundingRect.right = (int)(backgroundRect.left + (signBoundingRect.right * ratioWidth));
			signBoundingRect.bottom = (int)(backgroundRect.top + (signBoundingRect.bottom * ratioHeight));

			canvas.drawRect(signBoundingRect, this.signRectanglePaint);
		}
	}

	private void drawObjectMeasure(Canvas canvas, Rect cropRectangle, boolean isScale) {
		if (this.signRect != null) {
			Rect signBoundingRect = new Rect(this.signRect);

			if (isScale) {
				double ratioWidth = this.backgroundRect.width() / (double)this.backgroundImage.getWidth();
				double ratioHeight = this.backgroundRect.height() / (double)this.backgroundImage.getHeight();

				signBoundingRect.left = (int)(backgroundRect.left + (signBoundingRect.left * ratioWidth));
				signBoundingRect.top = (int)(backgroundRect.top + (signBoundingRect.top * ratioHeight));
				signBoundingRect.right = (int)(backgroundRect.left + (signBoundingRect.right * ratioWidth));
				signBoundingRect.bottom = (int)(backgroundRect.top + (signBoundingRect.bottom * ratioHeight));
			}

			canvas.drawRect(signBoundingRect, this.signRectanglePaint);
		}

		if (isScale) {
			this.textPaint.setTextSize(16);
		} else {
			this.textPaint.setTextSize(24);
		}

		String textObjectMeterWidth = String.format(Locale.ENGLISH, "%.2f m.", this.objectMeterWidth);
		String textObjectMeterHeight = String.format(Locale.ENGLISH, "%.2f m.", this.objectMeterHeight);
		Rect textBound = new Rect();

		this.textPaint.getTextBounds(textObjectMeterWidth, 0, textObjectMeterWidth.length(), textBound);

		int textTop = cropRectangle.top - textBound.height();

		if (isScale? (textTop < backgroundRect.top) : (textTop < 0)) textTop = (int)(cropRectangle.top + (textBound.height() * 1.2));

		canvas.drawText(textObjectMeterWidth,
				cropRectangle.left + (cropRectangle.width() / 2f) -  (textBound.width() / 2f),
				textTop,
				textPaint);

		this.textPaint.getTextBounds(textObjectMeterHeight, 0, textObjectMeterHeight.length(), textBound);

		int textLeft = cropRectangle.left - textBound.height();

		if (isScale? (textLeft < backgroundRect.left) : (textLeft < 0)) textLeft = (int)(cropRectangle.left + (textBound.height() * 1.2));

		PointF p = new PointF(textLeft, cropRectangle.bottom - (cropRectangle.height() / 2f) + (textBound.width() / 2f));

		canvas.save();
		canvas.rotate(-90, p.x, p.y);
		canvas.drawText(textObjectMeterHeight, p.x, p.y, textPaint);
		canvas.restore();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		Rect cropRect = this.getCropRectangle();

		// Clear Canvas
		canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

		// Draw Background Image
		this.drawBackground(canvas, cropRect);

		// Draw Sign Rectangle
		this.drawSign(canvas, this.signRect);

		// Draw Crop Rectangle
		canvas.drawRect(cropRect, cropRectanglePaint);

		if (!isLockMeasurement) {
			// Draw Tracker
			for (int i = 1; i <= 8; i++)
			{
				this.drawTracker(canvas, this.getHandleRectangle(i));
			}
		} else {
			this.drawObjectMeasure(canvas, cropRect, true);
		}
	}

	@Override
	public boolean performClick() {
		// Calls the super implementation, which generates an AccessibilityEvent
		// and calls the onClick() listener on the view, if any
		super.performClick();

		// Handle the action for the custom click here
		return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isLockMeasurement) return false;

		Point position = new Point((int)event.getX(), (int)event.getY());

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				selectionMode = SelectionMode.None;

				handleNumber = this.hitTest(position);

				if (handleNumber > 0) {
					selectionMode = SelectionMode.ReSize;
				} else {
					Rect cropRect = this.getCropRectangle();

					if (cropRect.contains((int)position.x, (int)position.y)) {
						selectionMode = SelectionMode.Move;
					}
				}

				lastPoint = position;
				break;
			case MotionEvent.ACTION_MOVE:
				switch (selectionMode) {
					case ReSize:
						this.moveHandleTo(position, handleNumber);
						break;
					case Move:
						this.move(position.x - lastPoint.x, position.y - lastPoint.y);
						break;
					default:
						break;
				}

				lastPoint = position;
				break;
			case MotionEvent.ACTION_UP:
				this.normalize();
				break;
			default:
				break;
		}

		return this.performClick();
	}

	private Bitmap createImageResult() {
		Rect originalCropRect = this.getOriginalCropRect();
		Bitmap bmp = Bitmap.createBitmap(this.backgroundImage.getWidth(), this.backgroundImage.getHeight(), Config.ARGB_8888);
		Canvas canvas = new Canvas(bmp);
		canvas.drawBitmap(this.backgroundImage, 0, 0, backgroundPaint);
		canvas.drawBitmap(
				Bitmap.createBitmap(this.backgroundImage, originalCropRect.left, originalCropRect.top, originalCropRect.width(), originalCropRect.height()),
				originalCropRect.left, originalCropRect.top, null);
		canvas.drawRect(originalCropRect, this.cropRectanglePaint);

		this.drawObjectMeasure(canvas, originalCropRect, false);

		return bmp;
	}

	public void saveResult(String pathFilename) throws Exception {
		Bitmap bmp = this.createImageResult();

		try {
			File file = new File(pathFilename);
			FileOutputStream stream = new FileOutputStream(file);

			stream.flush();
			stream.write(DBObjectMeasurement.getBytes(bmp));
			stream.close();
		} catch (Exception ex) {
			throw ex;
		}
	}

	public Bitmap getImageResult() {
		return this.createImageResult();
	}
}