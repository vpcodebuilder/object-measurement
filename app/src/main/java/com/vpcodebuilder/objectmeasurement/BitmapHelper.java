package com.vpcodebuilder.objectmeasurement;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

public class BitmapHelper {
	public static Bitmap getBitmapFromPath(String path) throws Exception {
		File file = new File(path);
		Bitmap bufferedImage;

		try {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			FileInputStream fileInputStream;
			fileInputStream = new FileInputStream(file);
			bufferedImage = BitmapFactory.decodeStream(fileInputStream, null, options);
			fileInputStream.close();
		} catch (Exception ex) {
			throw ex;
		}

		return bufferedImage;
	}

	public static Bitmap getBitmapFromPath(String path, int requestWidth) throws Exception  {
		/* The input stream could be reset instead of closed and reopened if it were possible
           to reliably wrap the input stream on a buffered stream, but it's not possible because
           decodeStream() places an upper read limit of 1024 bytes for a reset to be made (it calls
           mark(1024) on the stream). */

		File file = new File(path);
		FileInputStream fileInputStream;
		BitmapFactory.Options sizeOptions = new BitmapFactory.Options();
		sizeOptions.inJustDecodeBounds = true;

		try {
			fileInputStream = new FileInputStream(file);
			BitmapFactory.decodeStream(fileInputStream, null, sizeOptions);
			fileInputStream.close();
		} catch (Exception ex) {
			throw ex;
		}

		int width = sizeOptions.outWidth;
		int sampleSize = 1;

		while (width > requestWidth) {
			width /= 2.0;

			if (width < requestWidth) break;

			sampleSize *= 2;
		}

		BitmapFactory.Options finalBitmapOptions = new BitmapFactory.Options();
		Bitmap bufferedImage;

		finalBitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
		finalBitmapOptions.inSampleSize = sampleSize;

		try {
			fileInputStream = new FileInputStream(file);
			bufferedImage = BitmapFactory.decodeStream(fileInputStream, null, finalBitmapOptions);
			fileInputStream.close();
		} catch (Exception ex) {
			throw ex;
		}

		return bufferedImage;
	}

	public static Bitmap getScaledBitmap(Bitmap bmp, int reqWidth) {
		double ratio = reqWidth / (double)bmp.getWidth();
		int nWidth = (int)(bmp.getWidth() * ratio);
		int nHeight = (int)(bmp.getHeight() * ratio);

		return Bitmap.createScaledBitmap(bmp, nWidth, nHeight, true);
	}

	public static Bitmap rotateBitmap(Bitmap source, float angle)
	{
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
	}
}