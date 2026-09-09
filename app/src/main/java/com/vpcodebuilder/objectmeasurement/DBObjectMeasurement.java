package com.vpcodebuilder.objectmeasurement;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** One stored measurement row. Lengths are millimetres. */
public class DBObjectMeasurement {

    private int itemId;
    private int seqNo;
    private double objectWidthMm;
    private double objectHeightMm;
    private float confidence;
    private boolean perspectiveCorrected;
    private long createdAt;
    private Bitmap imageData;

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
    }

    public double getObjectWidthMm() {
        return objectWidthMm;
    }

    public void setObjectWidthMm(double objectWidthMm) {
        this.objectWidthMm = objectWidthMm;
    }

    public double getObjectHeightMm() {
        return objectHeightMm;
    }

    public void setObjectHeightMm(double objectHeightMm) {
        this.objectHeightMm = objectHeightMm;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public boolean isPerspectiveCorrected() {
        return perspectiveCorrected;
    }

    public void setPerspectiveCorrected(boolean perspectiveCorrected) {
        this.perspectiveCorrected = perspectiveCorrected;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Bitmap getImageData() {
        return imageData;
    }

    public void setImageData(Bitmap imageData) {
        this.imageData = imageData;
    }

    // ---------------------------------------------------------------- queries

    /**
     * All rows for one item, ordered by sequence.
     *
     * <p>Every query used to be assembled by string concatenation
     * ({@code "... WHERE itemId = " + itemId}). Bound arguments are used
     * throughout now.</p>
     */
    public static List<DBObjectMeasurement> select(DBManager manager, int itemId) {
        SQLiteDatabase db = manager.getReadableDatabase();
        List<DBObjectMeasurement> rows = new ArrayList<>();

        try (Cursor cursor = db.query(DBManager.TBL_OM, null,
                DBManager.COL_ITEM_ID + " = ?",
                new String[]{String.valueOf(itemId)},
                null, null, DBManager.COL_SEQ_NO + " ASC")) {

            int idxItem = cursor.getColumnIndexOrThrow(DBManager.COL_ITEM_ID);
            int idxSeq = cursor.getColumnIndexOrThrow(DBManager.COL_SEQ_NO);
            int idxWidth = cursor.getColumnIndexOrThrow(DBManager.COL_WIDTH_MM);
            int idxHeight = cursor.getColumnIndexOrThrow(DBManager.COL_HEIGHT_MM);
            int idxConfidence = cursor.getColumnIndexOrThrow(DBManager.COL_CONFIDENCE);
            int idxPerspective = cursor.getColumnIndexOrThrow(DBManager.COL_PERSPECTIVE);
            int idxCreated = cursor.getColumnIndexOrThrow(DBManager.COL_CREATED_AT);
            int idxImage = cursor.getColumnIndexOrThrow(DBManager.COL_IMAGE);

            while (cursor.moveToNext()) {
                DBObjectMeasurement row = new DBObjectMeasurement();
                row.itemId = cursor.getInt(idxItem);
                row.seqNo = cursor.getInt(idxSeq);
                row.objectWidthMm = cursor.getDouble(idxWidth);
                row.objectHeightMm = cursor.getDouble(idxHeight);
                row.confidence = (float) cursor.getDouble(idxConfidence);
                row.perspectiveCorrected = cursor.getInt(idxPerspective) != 0;
                row.createdAt = cursor.getLong(idxCreated);
                row.imageData = toBitmap(cursor.getBlob(idxImage));
                rows.add(row);
            }
        }
        return rows;
    }

    /** Insert or replace — the caller no longer has to know which. */
    public static void save(DBManager manager, DBObjectMeasurement row) {
        SQLiteDatabase db = manager.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DBManager.COL_ITEM_ID, row.itemId);
        values.put(DBManager.COL_SEQ_NO, row.seqNo);
        values.put(DBManager.COL_WIDTH_MM, row.objectWidthMm);
        values.put(DBManager.COL_HEIGHT_MM, row.objectHeightMm);
        values.put(DBManager.COL_CONFIDENCE, row.confidence);
        values.put(DBManager.COL_PERSPECTIVE, row.perspectiveCorrected ? 1 : 0);
        values.put(DBManager.COL_CREATED_AT, System.currentTimeMillis());
        values.put(DBManager.COL_IMAGE, toBytes(row.imageData));
        db.insertWithOnConflict(DBManager.TBL_OM, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static void deleteAll(DBManager manager) {
        manager.getWritableDatabase().delete(DBManager.TBL_OM, null, null);
    }

    public static void delete(DBManager manager, int itemId, int seqNo) {
        manager.getWritableDatabase().delete(DBManager.TBL_OM,
                DBManager.COL_ITEM_ID + " = ? AND " + DBManager.COL_SEQ_NO + " = ?",
                new String[]{String.valueOf(itemId), String.valueOf(seqNo)});
    }

    // ----------------------------------------------------------------- codecs

    /**
     * JPEG at 85 rather than the previous 100: a full-quality re-encode of an
     * already-lossy photo roughly triples the blob size for no visible gain, and
     * these rows live in a SQLite database that is read on the main thread.
     */
    public static byte[] toBytes(Bitmap bitmap) {
        if (bitmap == null) return null;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
        return stream.toByteArray();
    }

    public static Bitmap toBitmap(byte[] data) {
        if (data == null || data.length == 0) return null;
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }
}
