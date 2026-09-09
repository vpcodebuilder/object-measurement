package com.vpcodebuilder.objectmeasurement;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * Reads and writes the detector profile as JSON.
 *
 * <p>Replaces the {@code eu.geekplace.iesp} dependency together with the
 * home-grown {@code DirectoryPickerActivity} that fed it. That picker walked the
 * raw filesystem with {@code File.listFiles()}, which returns nothing outside the
 * app sandbox from Android 11 onwards, so both import and export were dead ends.
 * Callers now hand over a stream from the Storage Access Framework instead.</p>
 */
public final class SettingsIO {

    private static final String TYPE_INT = "i";
    private static final String TYPE_FLOAT = "f";
    private static final String TYPE_BOOL = "b";
    private static final String TYPE_STRING = "s";

    private SettingsIO() {
    }

    public static void export(SharedPreferences prefs, OutputStream out)
            throws IOException, JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "object-measurement/settings");
        root.put("version", 1);

        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            JSONObject typed = new JSONObject();
            if (value instanceof Integer) {
                typed.put("t", TYPE_INT).put("v", value);
            } else if (value instanceof Float) {
                typed.put("t", TYPE_FLOAT).put("v", ((Float) value).doubleValue());
            } else if (value instanceof Boolean) {
                typed.put("t", TYPE_BOOL).put("v", value);
            } else if (value instanceof String) {
                typed.put("t", TYPE_STRING).put("v", value);
            } else {
                continue; // sets and longs are not used by this app
            }
            values.put(entry.getKey(), typed);
        }
        root.put("values", values);

        out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Merges the file's entries into {@code prefs}; keys not present are left alone. */
    public static int importInto(SharedPreferences prefs, InputStream in)
            throws IOException, JSONException {
        JSONObject root = new JSONObject(readAll(in));
        JSONObject values = root.optJSONObject("values");
        if (values == null) {
            throw new JSONException("Not an Object Measurement settings file");
        }

        SharedPreferences.Editor editor = prefs.edit();
        int applied = 0;
        for (Iterator<String> keys = values.keys(); keys.hasNext(); ) {
            String key = keys.next();
            JSONObject typed = values.optJSONObject(key);
            if (typed == null) continue;
            String type = typed.optString("t");
            switch (type) {
                case TYPE_INT:
                    editor.putInt(key, typed.getInt("v"));
                    break;
                case TYPE_FLOAT:
                    editor.putFloat(key, (float) typed.getDouble("v"));
                    break;
                case TYPE_BOOL:
                    editor.putBoolean(key, typed.getBoolean("v"));
                    break;
                case TYPE_STRING:
                    editor.putString(key, typed.getString("v"));
                    break;
                default:
                    continue;
            }
            applied++;
        }
        editor.apply();
        return applied;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString("UTF-8");
    }
}
