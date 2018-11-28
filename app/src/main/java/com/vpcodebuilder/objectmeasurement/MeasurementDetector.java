package com.vpcodebuilder.objectmeasurement;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.graphics.Bitmap;

public class MeasurementDetector {
	private Bitmap frame = null;
	private SignMeasurement knowSignMeasurement;
	private Size realMarkerMilimeterSize;
	private Size realObjectMilimeterSize;

	private Mat settingHSVFrame = null;

	public MeasurementDetector(Bitmap frame, Size realMarkerMilimeterSize) {
		this.frame = frame;
		this.realMarkerMilimeterSize = realMarkerMilimeterSize;
	}

	public Size getObjectMilimeterSize() {
		return realObjectMilimeterSize;
	}

	public SignMeasurement getSignMeasurement() {
		return knowSignMeasurement;
	}

	public Bitmap preProcess(int hueLower, int saturationLower,  int valueLower, int hueUpper, int saturationUpper, int valueUpper) throws Exception {
		if (this.settingHSVFrame == null) {
			Mat currentFrame = new Mat();
			Utils.bitmapToMat(this.frame, currentFrame);
			Imgproc.pyrDown(currentFrame, currentFrame);
			Mat simplestCBFrame = this.simplestColorBalance(currentFrame, 1);
			Imgproc.medianBlur(simplestCBFrame, simplestCBFrame, 5);
			settingHSVFrame = new Mat();
			Imgproc.cvtColor(simplestCBFrame, settingHSVFrame, Imgproc.COLOR_RGB2HSV, 3);
		}

		Mat thresholdFrame = new Mat(this.settingHSVFrame.rows(), this.settingHSVFrame.cols(), CvType.CV_8UC1);
		Core.inRange(settingHSVFrame,
				new Scalar(hueLower, saturationLower, valueLower),
				new Scalar(hueUpper, saturationUpper, valueUpper), thresholdFrame);

		Imgproc.erode(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		Imgproc.erode(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		Imgproc.dilate(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		thresholdFrame = this.removeMorphologyBug(thresholdFrame);

		Imgproc.pyrUp(thresholdFrame, thresholdFrame);

		Mat colorFrame = new Mat(thresholdFrame.rows(), thresholdFrame.cols(), CvType.CV_8UC3);
		Imgproc.cvtColor(thresholdFrame, colorFrame, Imgproc.COLOR_GRAY2BGR, 3);

		Bitmap bmp = Bitmap.createBitmap(colorFrame.cols(), colorFrame.rows(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(colorFrame, bmp);

		return bmp;
	}

	public static SignMeasurement getSignMeasurement(Bitmap bmp, int hueLower, int saturationLower, int valueLower, int hueUpper, int saturationUpper, int valueUpper) {
		Mat rgbaFrame = new Mat();
		Utils.bitmapToMat(bmp, rgbaFrame);
		return getSignMeasurement(rgbaFrame, hueLower, saturationLower, valueLower, hueUpper, saturationUpper, valueUpper);
	}

	public static SignMeasurement getSignMeasurement(Mat rgbaFrame, int hueLower, int saturationLower, int valueLower, int hueUpper, int saturationUpper, int valueUpper) {
		SignMeasurement signMeasurement = null;
		Rect originalRect = new Rect(0, 0, rgbaFrame.width(), rgbaFrame.height());

		Imgproc.pyrDown(rgbaFrame, rgbaFrame);

		Rect resizeRect = new Rect(0, 0, rgbaFrame.width(), rgbaFrame.height());

		Mat rgbFrame = new Mat();
		Imgproc.cvtColor(rgbaFrame, rgbFrame, Imgproc.COLOR_RGBA2RGB, 3);
		Imgproc.medianBlur(rgbFrame, rgbFrame, 5);

		Mat hsvFrame = new Mat();
		Imgproc.cvtColor(rgbFrame, hsvFrame, Imgproc.COLOR_RGB2HSV, 3);

		Mat thresholdFrame = new Mat(rgbaFrame.rows(), rgbaFrame.cols(), CvType.CV_8UC1);
		Core.inRange(hsvFrame,
				new Scalar(hueLower, saturationLower, valueLower),
				new Scalar(hueUpper, saturationUpper, valueUpper), thresholdFrame);

		Imgproc.erode(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		Imgproc.erode(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		Imgproc.dilate(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));

		Mat cannyFrame = new Mat();
		Imgproc.Canny(thresholdFrame, cannyFrame, 0, 255);
		Imgproc.dilate(cannyFrame, cannyFrame, new Mat(3, 3, CvType.CV_8UC1));

		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(cannyFrame, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

		double maxArea = -1;
		MatOfPoint approxCurveSelected = null;

		for (int i = 0; i < contours.size(); i++) {
			MatOfPoint2f curve = new MatOfPoint2f(contours.get(i).toArray());
			MatOfPoint2f approxCurve2f = new MatOfPoint2f();
			Imgproc.approxPolyDP(curve, approxCurve2f, Imgproc.arcLength(curve, true) * 0.05, true);

			MatOfPoint approxCurve = new MatOfPoint(approxCurve2f.toArray());
			double contourArea = Imgproc.contourArea(approxCurve);

			if (approxCurve.total() != 4) continue;
			if (contourArea < maxArea) continue;

			double maxCosine = 0;
			Point[] contourPoints = approxCurve.toArray();

			for (int j = 2; j < 5; j++)
			{
				double cosine = Math.abs(angle(contourPoints[j % 4], contourPoints[j - 1], contourPoints[j - 2]));
				maxCosine = Math.max(maxCosine, cosine);
			}

			if (maxCosine > 80 && maxCosine < 100) {
				maxArea = contourArea;
				approxCurveSelected = new MatOfPoint(approxCurve.toArray());
			}
		}

		if (approxCurveSelected != null) {
			Point[] signPoints = approxCurveSelected.toArray();
			MatOfPoint2f signContour = new MatOfPoint2f(signPoints);
			Rect signBoundingRect = Imgproc.minAreaRect(signContour).boundingRect();

			signMeasurement = createSignMeasurement(originalRect, resizeRect, signBoundingRect, signPoints);

			approxCurveSelected.release();
			signContour.release();
		}

		rgbFrame.release();
		hsvFrame.release();
		thresholdFrame.release();
		cannyFrame.release();

		return signMeasurement;
	}

	public void process(SignMeasurement knowSignMeasurement) {
		this.knowSignMeasurement = knowSignMeasurement;
		Rect originalRect = new Rect(0, 0, this.frame.getWidth(), this.frame.getHeight());
		this.realObjectMilimeterSize = this.knowSignMeasurement.ComputeObjectSize(originalRect, this.realMarkerMilimeterSize);
	}

	public void process(int hueLower, int saturationLower,  int valueLower, int hueUpper, int saturationUpper, int valueUpper) throws Exception {
		boolean debugMode = false;

		Mat currentFrame = new Mat();
		Utils.bitmapToMat(this.frame, currentFrame);
		Rect originalRect = new Rect(0, 0, currentFrame.width(), currentFrame.height());
		Imgproc.pyrDown(currentFrame, currentFrame);

		if (debugMode) this.saveMat(currentFrame, "1_currentFrame.jpg", Imgproc.COLOR_RGB2BGR);

		Rect resizeRect = new Rect(0, 0, currentFrame.width(), currentFrame.height());

		Mat simplestCBFrame = this.simplestColorBalance(currentFrame, 1);

		if (debugMode) this.saveMat(simplestCBFrame, "2_colorBalanceFrame.jpg", Imgproc.COLOR_RGB2BGR);

		Imgproc.medianBlur(simplestCBFrame, simplestCBFrame, 5);

		if (debugMode) this.saveMat(simplestCBFrame, "3_blurFrame.jpg", Imgproc.COLOR_RGB2BGR);

		Mat hsvFrame = new Mat();
		Imgproc.cvtColor(simplestCBFrame, hsvFrame, Imgproc.COLOR_RGB2HSV, 3);

		if (debugMode) this.saveMat(hsvFrame, "4_hsvFrame.jpg");

		Mat thresholdFrame = new Mat(currentFrame.rows(), currentFrame.cols(), CvType.CV_8UC1);
		Core.inRange(hsvFrame, new Scalar(hueLower, saturationLower, valueLower), new Scalar(hueUpper, saturationUpper, valueUpper), thresholdFrame);

		if (debugMode) this.saveMat(thresholdFrame, "5_thresholdFrame.jpg");

		Imgproc.erode(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		Imgproc.erode(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));
		Imgproc.dilate(thresholdFrame, thresholdFrame, new Mat(3, 3, CvType.CV_8UC1));

		if (debugMode) this.saveMat(thresholdFrame, "6_morphology.jpg");

		Mat cannyFrame = new Mat();
		Imgproc.Canny(thresholdFrame, cannyFrame, 0, 255);
		Imgproc.dilate(cannyFrame, cannyFrame, new Mat(3, 3, CvType.CV_8UC1));
		cannyFrame = this.removeMorphologyBug(cannyFrame);

		if (debugMode) this.saveMat(cannyFrame, "7_cannyFrame.jpg");

		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(cannyFrame, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

		double maxArea = -1;
		MatOfPoint approxCurveSelected = null;

		for (int i = 0; i < contours.size(); i++) {
			MatOfPoint2f curve = new MatOfPoint2f(contours.get(i).toArray());
			MatOfPoint2f approxCurve2f = new MatOfPoint2f();
			Imgproc.approxPolyDP(curve, approxCurve2f, Imgproc.arcLength(curve, true) * 0.05, true);
			MatOfPoint approxCurve = new MatOfPoint(approxCurve2f.toArray());

			double contourArea = Imgproc.contourArea(approxCurve);

			if (approxCurve.total() != 4) continue;
			if (contourArea < maxArea) continue;

			double maxCosine = 0;
			Point[] contourPoints = approxCurve.toArray();

			for (int j = 2; j < 5; j++) {
				double cosine = Math.abs(angle(contourPoints[j % 4], contourPoints[j - 1], contourPoints[j - 2]));
				maxCosine = Math.max(maxCosine, cosine);
			}

			if (maxCosine > 80 && maxCosine < 100) {
				maxArea = contourArea;
				approxCurveSelected = new MatOfPoint(approxCurve.toArray());
			}
		}

		if (approxCurveSelected != null) {
			Point[] signPoints = approxCurveSelected.toArray();
			MatOfPoint2f signContour = new MatOfPoint2f(signPoints);
			Rect signBoundingRect = Imgproc.minAreaRect(signContour).boundingRect();

			if (debugMode) {
				Core.rectangle(currentFrame,
						new Point(signBoundingRect.x, signBoundingRect.y),
						new Point(signBoundingRect.x + signBoundingRect.width, signBoundingRect.y + signBoundingRect.height),
						new Scalar(255, 0, 0), 1);
				this.saveMat(currentFrame, "8_detectFrame.jpg", Imgproc.COLOR_RGB2BGR);
			}

			this.knowSignMeasurement = createSignMeasurement(originalRect, resizeRect, signBoundingRect, signPoints);
			this.realObjectMilimeterSize = this.knowSignMeasurement.ComputeObjectSize(originalRect, this.realMarkerMilimeterSize);

			approxCurveSelected.release();
			signContour.release();
		}

		currentFrame.release();
		simplestCBFrame.release();
		hsvFrame.release();
		thresholdFrame.release();
		cannyFrame.release();
	}

	private Mat simplestColorBalance(Mat src, float percent) {
		float halfPercent = percent / 200.0f;
		List<Mat> channels = new ArrayList<Mat>();
		Mat flat = new Mat(1, src.rows() * src.cols(), CvType.CV_8UC1);
		float[] pixels = new float[(int)flat.total()];

		Core.split(src, channels);

		for (int i = 0; i < 3; i++) {
			Mat ch = channels.get(i).clone();
			ch.reshape(1, 1).copyTo(flat);
			Core.sort(flat, flat, Core.SORT_EVERY_ROW + Core.SORT_ASCENDING);

			float lowVal = (float)flat.get(0, (int)Math.floor((float)flat.cols() * halfPercent))[0];
			float highVal = (float)flat.get(0, (int)Math.ceil((float)flat.cols() * (1.0 - halfPercent)))[0];

			ch.convertTo(ch, CvType.CV_32FC1);
			ch.get(0, 0, pixels);

			for (int j = 0; j < pixels.length; j++) {
				if (pixels[j] < lowVal) pixels[j] = lowVal;
				if (pixels[j] > highVal) pixels[j] = highVal;
			}

			channels.get(i).convertTo(channels.get(i), CvType.CV_32FC1);
			channels.get(i).put(0, 0, pixels);
			channels.get(i).convertTo(channels.get(i), CvType.CV_8UC1);
			Core.normalize(channels.get(i), channels.get(i), 0, 255, Core.NORM_MINMAX);
		}

		Mat dst = new Mat(src.rows(), src.cols(), src.type());
		Core.merge(channels, dst);

		return dst;
	}

	private Mat equalizeIntensity(Mat src) {
		List<Mat> channels = new ArrayList<Mat>();
		Mat dst = new Mat(src.rows(), src.cols(), src.type());

		Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGB2YCrCb, 3);
		Core.split(dst, channels);
		Imgproc.equalizeHist(channels.get(0), channels.get(0));
		Core.merge(channels, dst);
		Imgproc.cvtColor(dst, dst, Imgproc.COLOR_YCrCb2RGB, 3);

		return dst;
	}

	private static double angle(Point p0, Point p1, Point p2)
	{
		double a = Math.pow(p1.x - p0.x,2) + Math.pow(p1.y - p0.y,2);
		double b = Math.pow(p1.x - p2.x,2) + Math.pow(p1.y - p2.y,2);
		double c = Math.pow(p2.x - p0.x,2) + Math.pow(p2.y - p0.y,2);
		return Math.toDegrees(Math.acos((a + b - c) / Math.sqrt(4 * a * b)));
	}

	private static Point[] nomalizePoints(Point[] points) {
		// P1 -- P2
		//  |       |
		// P3 -- P4
		if (points == null) return null;
		if (points.length != 4) return null;

		List<Point> pointList = new ArrayList<Point>();

		Comparator<Point> pointYOrder = new Comparator<Point>() {
			public int compare(Point p1, Point p2) {
				if (p1.y < p2.y) return -1;
				if (p1.y > p2.y) return +1;
				return 0;
			}
		};

		for (int i = 0; i < points.length; i++) {
			pointList.add(points[i]);
		}

		Collections.sort(pointList, pointYOrder);

		return new Point[] {
			pointList.get(0).x < pointList.get(1).x ? pointList.get(0) : pointList.get(1),
			pointList.get(0).x < pointList.get(1).x ? pointList.get(1) : pointList.get(0),
			pointList.get(2).x < pointList.get(3).x ? pointList.get(2) : pointList.get(3),
			pointList.get(2).x < pointList.get(3).x ? pointList.get(3) : pointList.get(2)
		};
	}

	private static double distancePoint(Point p1, Point p2) {
		double dx = p1.x - p2.x;
		double dy = p1.y - p2.y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	private static SignMeasurement createSignMeasurement(Rect originalRect, Rect resizeRect, Rect signBoundingRect, Point[] signPoints) {
		double ratioWidth = originalRect.width /  (double)resizeRect.width;
		double ratioHeight = originalRect.height / (double)resizeRect.height;

		double left = signBoundingRect.x * ratioWidth;
		double top = signBoundingRect.y * ratioHeight;
		double right = signBoundingRect.width * ratioWidth;
		double bottom = signBoundingRect.height * ratioHeight;

		Rect signRectangle = new Rect(new Point(0, 0), new Point(right, bottom));

		signRectangle.x += left;
		signRectangle.y += top;

		for (int i = 0; i < signPoints.length; i++) {
			signPoints[i].x *= ratioWidth;
			signPoints[i].y *= ratioHeight;
		}

		signPoints = nomalizePoints(signPoints);

		// P1 --- P2
		//  |         |
		// P3 --- P4
		// w = (|P1 - P2| + |P3 - P4|) / 2
		// h = (|P1 - P3| + |P2 - P4|) / 2
		double rulerWidth = Math.min(distancePoint(signPoints[0], signPoints[1]), distancePoint(signPoints[2], signPoints[3]));
		double rulerHeight = Math.min(distancePoint(signPoints[0], signPoints[2]), distancePoint(signPoints[1], signPoints[3]));

		return new SignMeasurement(signRectangle, rulerWidth, rulerHeight);
	}

	private Mat removeMorphologyBug(Mat src) {
		Mat dst = src.clone();
		int dstRows = dst.rows();
		int dstCols = dst.cols();
		byte[] pixels = new byte[(int)dst.total()];
		int rowSize = 3;
		int colSize = 3;

		dst.get(0, 0, pixels);

		for (int r = 0; r < rowSize; r++) {
			for (int c = 0; c < dstCols; c++) {
				pixels[(r * dstCols) + c] = 0;
			}
		}

		for (int r = dstRows - 1; r >= dstRows - rowSize; r--) {
			for (int c = 0; c < dstCols; c++) {
				pixels[(r * dstCols) + c] = 0;
			}
		}

		for (int c = 0; c < colSize; c++) {
			for (int r = 0; r < dstRows; r++) {
				pixels[(r * dstCols) + c] = 0;
			}
		}

		for (int c = dstCols - 1; c >= dstCols - colSize; c--) {
			for (int r = 0; r < dstRows; r++) {
				pixels[(r * dstCols) + c] = 0;
			}
		}

		dst.put(0, 0, pixels);

		return dst;
	}

	private void saveMat(Mat mat, String filename) {
		saveMat(mat, filename, null);
	}

	private void saveMat(Mat mat, String filename, Integer convertCode) {
		String path = PathHelper.getResourcePath();

		if (path != null) {
			Mat colorFrame = new Mat(mat.rows(), mat.cols(), CvType.CV_8UC3);

			if (mat.channels() == 1) {
				Imgproc.cvtColor(mat, colorFrame, Imgproc.COLOR_GRAY2BGR, 3);
			} else {
				if (convertCode != null) {
					Imgproc.cvtColor(mat, colorFrame, convertCode, 3);
				} else {
					colorFrame = mat;
				}
			}

			Highgui.imwrite(path + File.separator + filename, colorFrame);
		}
	}
}