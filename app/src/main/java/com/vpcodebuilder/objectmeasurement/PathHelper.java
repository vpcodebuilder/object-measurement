package com.vpcodebuilder.objectmeasurement;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PathHelper {
    public static void updateMediaScanner(Activity activity, File file) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        activity.getApplicationContext().sendBroadcast(intent);
    }

    public static String getResourcePath() {
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/"
                + "com.vpcodebuilder.objectmeasurement"
                + "/Files");

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }

        return mediaStorageDir.getPath();
    }

    public static Uri getFullPathFromUri(Uri dataUri, ContentResolver resolver) {
        String[] arrFilePath = { MediaStore.Images.Media.DATA };
        Cursor cursor = resolver.query(dataUri, arrFilePath, null, null, null);
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

        cursor.moveToFirst();

        String filePath = cursor.getString(columnIndex);
        cursor.close();

        return Uri.fromFile(new File(filePath));
    }

    public static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;

        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }

        in.close();
        out.close();
    }
}
