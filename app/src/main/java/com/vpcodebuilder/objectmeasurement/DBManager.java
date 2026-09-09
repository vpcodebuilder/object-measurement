package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite helper for stored measurements.
 *
 * <p>Version 2 stores millimetres rather than metres. Keeping one canonical unit
 * in the database and converting only for display removes a class of rounding
 * and unit-confusion bugs, and it is what lets the app offer mm/cm/m/inch
 * without touching stored data.</p>
 */
public class DBManager extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "om.db";
    public static final String TBL_OM = "tbl_om";
    public static final int DATABASE_VERSION = 2;

    public static final String COL_ITEM_ID = "itemId";
    public static final String COL_SEQ_NO = "seqNo";
    public static final String COL_WIDTH_MM = "objectWidthMm";
    public static final String COL_HEIGHT_MM = "objectHeightMm";
    public static final String COL_CONFIDENCE = "confidence";
    public static final String COL_PERSPECTIVE = "perspectiveCorrected";
    public static final String COL_CREATED_AT = "createdAt";
    public static final String COL_IMAGE = "imageData";

    private static DBManager instance;

    /**
     * One helper per process. The old code created a new helper for every query
     * and called {@code db.close()} inside insert/update, tearing down the cached
     * connection other callers were still using.
     */
    public static synchronized DBManager get(Context context) {
        if (instance == null) {
            instance = new DBManager(context.getApplicationContext());
        }
        return instance;
    }

    private DBManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TBL_OM + " ("
                + COL_ITEM_ID + " INTEGER NOT NULL, "
                + COL_SEQ_NO + " INTEGER NOT NULL, "
                + COL_WIDTH_MM + " REAL NOT NULL DEFAULT 0, "
                + COL_HEIGHT_MM + " REAL NOT NULL DEFAULT 0, "
                + COL_CONFIDENCE + " REAL NOT NULL DEFAULT 0, "
                + COL_PERSPECTIVE + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_IMAGE + " BLOB, "
                + "PRIMARY KEY (" + COL_ITEM_ID + ", " + COL_SEQ_NO + ")"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Measurements are per-session working data, not an archive, so a clean
        // rebuild is the honest migration here.
        db.execSQL("DROP TABLE IF EXISTS " + TBL_OM);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
