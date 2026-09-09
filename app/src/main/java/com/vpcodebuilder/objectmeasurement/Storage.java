package com.vpcodebuilder.objectmeasurement;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * File locations for the app.
 *
 * <p>The old {@code PathHelper} hard-coded
 * {@code Environment.getExternalStorageDirectory() + "/Android/data/<pkg>/Files"}
 * and needed {@code WRITE_EXTERNAL_STORAGE}. Building that path by hand stopped
 * working under scoped storage on Android 10, so every capture failed with a
 * silent {@code null} path. Everything now goes through the app's own scoped
 * directory, which needs no permission on any supported release.</p>
 */
public final class Storage {

    private static final String AUTHORITY_SUFFIX = ".fileprovider";

    private Storage() {
    }

    /** Private, permission-free directory for captures and working files. */
    public static File captureDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "captures");
        if (!dir.exists() && !dir.mkdirs()) {
            // Fall back to internal storage if the external volume is unavailable.
            File internal = new File(context.getFilesDir(), "captures");
            //noinspection ResultOfMethodCallIgnored
            internal.mkdirs();
            return internal;
        }
        return dir;
    }

    /** Scratch file the capture screen hands to the measurement screen. */
    public static File captureBuffer(Context context) {
        return new File(captureDir(context), "capture_buffer.jpg");
    }

    public static File newCaptureFile(Context context, String prefix) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(captureDir(context), prefix + "_" + stamp + ".jpg");
    }

    /** A URI another app may read, for share sheets. */
    public static Uri shareUri(Context context, File file) {
        return FileProvider.getUriForFile(
                context, context.getPackageName() + AUTHORITY_SUFFIX, file);
    }

    public static void writeJpeg(File file, Bitmap bitmap, int quality) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                throw new IOException("Could not encode " + file.getName());
            }
            out.flush();
        }
    }

    /**
     * Publish a result image to the shared Pictures collection so it shows up in
     * the gallery. Uses MediaStore on API 29+ and a plain file below that.
     */
    public static Uri exportToGallery(Context context, Bitmap bitmap, String displayName)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/ObjectMeasurement");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore rejected the insert");

            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Could not open the destination");
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }

            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
            return uri;
        }

        File pictures = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "ObjectMeasurement");
        //noinspection ResultOfMethodCallIgnored
        pictures.mkdirs();
        File out = new File(pictures, displayName);
        writeJpeg(out, bitmap, 92);
        return Uri.fromFile(out);
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
