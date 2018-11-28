package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBManager extends SQLiteOpenHelper {
	public static final String DATABASE_NAME = "om.db";
	public static final String TBL_OM = "tbl_om";
	public static final int DATABASE_VERSION = 1;
	
	public DBManager(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TBL_OM +
						"(" +
						"itemId INTEGER, " +
						"seqNo INTEGER, " +
						"objectMeterWidth REAL, " +
						"objectMeterHeight REAL, " +
						"imageData BLOB, "+
						"PRIMARY KEY (itemId, seqNo)" +
						")"
		);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TBL_OM);
	    onCreate(db);
	}
}
