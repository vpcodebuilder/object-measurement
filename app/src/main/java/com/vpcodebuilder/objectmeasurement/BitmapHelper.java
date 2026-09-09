package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bitmap loading that downsamples for memory and honours EXIF orientation. */
public final class BitmapHelper {

    private BitmapHelper() {
    }

    /**
     * Decode from any URI — {@code content://} included.
     *
     * <p>The old gallery path resolved {@code MediaStore.Images.Media.DATA} to a
     * filesystem path and opened it directly. That column has been unreadable
     * since Android 10, so importing a photo threw or produced a null bitmap on
     * every modern device. Reading through the content resolver works for
     * documents, cloud providers and the photo picker alike.</p>
     */
    public static Bitmap decode(Context context, Uri uri, int requestedWidth) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0) {
            throw new IOException("Not a readable image");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sampleSize(bounds.outWidth, requestedWidth);

        Bitmap bitmap;
        try (InputStream in = open(context, uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Could not decode the image");
        }

        int rotation;
        try (InputStream in = open(context, uri)) {
            rotation = exifRotation(in);
        } catch (IOException ignored) {
            rotation = 0;
        }
        return rotate(bitmap, rotation);
    }

    public static Bitmap decode(File file, int requestedWidth) throws IOException {
        return decode(null, Uri.fromFile(file), requestedWidth);
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        if (context != null && !"file".equals(uri.getScheme())) {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Could not open " + uri);
            return in;
        }
        String path = uri.getPath();
        if (path == null) throw new IOException("Malformed URI " + uri);
        return new FileInputStream(path);
    }

    /**
     * Largest power-of-two subsample that still leaves at least
     * {@code requestedWidth} pixels across.
     *
     * <p>The previous loop halved the width and compared it against the request
     * inside the same iteration, so it undershot by a factor of two for some
     * inputs and returned images below the requested size.</p>
     */
    static int sampleSize(int sourceWidth, int requestedWidth) {
        int sample = 1;
        if (requestedWidth > 0 && sourceWidth > requestedWidth) {
            int half = sourceWidth / 2;
            while ((half / sample) >= requestedWidth) {
                sample *= 2;
            }
        }
        return sample;
    }

    private static int exifRotation(InputStream in) throws IOException {
        ExifInterface exif = new ExifInterface(in);
        switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    public static Bitmap rotate(Bitmap source, int degrees) {
        if (source == null || degrees % 360 == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0,
                source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }

    /** Scale so the width becomes {@code requestedWidth}, preserving aspect. */
    public static Bitmap scaleToWidth(Bitmap source, int requestedWidth) {
        if (source == null || requestedWidth <= 0 || source.getWidth() == requestedWidth) {
            return source;
        }
        double ratio = requestedWidth / (double) source.getWidth();
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        return Bitmap.createScaledBitmap(source, requestedWidth, height, true);
    }
}
