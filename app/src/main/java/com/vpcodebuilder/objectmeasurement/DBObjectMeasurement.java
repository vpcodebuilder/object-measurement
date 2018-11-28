package com.vpcodebuilder.objectmeasurement;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;

public class DBObjectMeasurement {
	private int itemId;
	private int seqNo;
	private double objectMeterWidth;
	private double objectMeterHeight;
	private Bitmap imageData;
	
	public int getItemId() {
		return this.itemId;
	}
	
	public void setItemId(int itemId) {
		this.itemId = itemId;
	}
	
	public int getSeqNo() {
		return this.seqNo;
	}
	
	public void setSeqNo(int seqNo) {
		this.seqNo = seqNo;
	}
	
	public double getObjectMeterWidth() {
		return this.objectMeterWidth;
	}
	
	public void setObjectMeterWidth(double objectMeterWidth) {
		this.objectMeterWidth = objectMeterWidth;
	}
	
	public double getObjectMeterHeight() {
		return this.objectMeterHeight;
	}
	
	public void setObjectMeterHeight(double objectMeterHeight) {
		this.objectMeterHeight = objectMeterHeight;
	}
	
	public Bitmap getImageData() {
		return this.imageData;
	}
	
	public void setImageData(Bitmap imageData) {
		this.imageData = imageData;
	}
	
	private static List<DBObjectMeasurement> select(DBManager dbManager, String sqlSelectCommand) throws Exception {
		List<DBObjectMeasurement> oDBObjectMeasurementList = new ArrayList<DBObjectMeasurement>();
		
		try {
			SQLiteDatabase db = dbManager.getReadableDatabase();
			Cursor cursor = db.rawQuery(sqlSelectCommand, null);
			
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					do {
						DBObjectMeasurement item = new DBObjectMeasurement();
						
						item.itemId = cursor.getInt(0);
						item.seqNo = cursor.getInt(1);
						item.objectMeterWidth = cursor.getDouble(2);
						item.objectMeterHeight = cursor.getDouble(3);
						item.imageData = getImage(cursor.getBlob(4));
						
						oDBObjectMeasurementList.add(item);
					} while (cursor.moveToNext());
				}
				
				cursor.close();
			}
		} catch (Exception ex) {
			throw ex;
		}
		
		return oDBObjectMeasurementList;
	}
	
	public static List<DBObjectMeasurement> select(DBManager dbManager) throws Exception {
		String sqlSelectCommand = "SELECT * FROM " + DBManager.TBL_OM + " ORDER BY itemId, SeqNo";
		return select(dbManager, sqlSelectCommand);
	}
	
	public static List<DBObjectMeasurement> select(DBManager dbManager, int itemId) throws Exception {
		String sqlSelectCommand = "SELECT * FROM " + DBManager.TBL_OM;
		
		sqlSelectCommand += " WHERE itemId = " + itemId + " ORDER BY SeqNo";
		
		return select(dbManager, sqlSelectCommand);
	}
	
	public static List<DBObjectMeasurement> select(DBManager dbManager, int itemId, int seqNo) throws Exception {
		String sqlSelectCommand = "SELECT * FROM " + DBManager.TBL_OM;
		
		sqlSelectCommand += " WHERE itemId = " + itemId + " AND seqNo = " + seqNo;
		
		return select(dbManager, sqlSelectCommand);
	}
	
	public static void insert(DBManager dbManager, DBObjectMeasurement oDBObjectMeasurement) throws Exception {
		 try {
			SQLiteDatabase db = dbManager.getWritableDatabase();
    	    ContentValues values = new ContentValues();
    	    
    	    values.put("itemId", oDBObjectMeasurement.itemId);
    	    values.put("seqNo", oDBObjectMeasurement.seqNo);
    	    values.put("objectMeterWidth", oDBObjectMeasurement.objectMeterWidth);
    	    values.put("objectMeterHeight", oDBObjectMeasurement.objectMeterHeight);
    	    values.put("imageData", getBytes(oDBObjectMeasurement.imageData));
	   		
			db.insert(DBManager.TBL_OM, null, values);
			db.close();
		 } catch (Exception ex) {
			 throw ex;
		 }
	}
	
	public static void update(DBManager dbManager, DBObjectMeasurement oDBObjectMeasurement) throws Exception {
		try {
			SQLiteDatabase db = dbManager.getWritableDatabase();
			ContentValues values = new ContentValues();
			
			values.put("objectMeterWidth", oDBObjectMeasurement.objectMeterWidth);
    	    values.put("objectMeterHeight", oDBObjectMeasurement.objectMeterHeight);
    	    values.put("imageData", getBytes(oDBObjectMeasurement.imageData));
			
			db.update(DBManager.TBL_OM, values, "itemId = ? AND seqNo = ?",
					new String[] { String.valueOf(oDBObjectMeasurement.itemId), String.valueOf(oDBObjectMeasurement.seqNo) });
			db.close();
		} catch (Exception ex) {
			throw ex;
		}
	}
	
	public static void delete(DBManager dbManager) throws Exception {
		try {
			SQLiteDatabase db = dbManager.getWritableDatabase();
			String sql = "DELETE FROM " + DBManager.TBL_OM;
			db.execSQL(sql);
		} catch (Exception ex) {
			throw ex;
		}
	}
	
	public static void delete(DBManager dbManager, String[] withoutItemIdList) throws Exception {
		try {
			SQLiteDatabase db = dbManager.getWritableDatabase();
			String sql = "DELETE FROM " + DBManager.TBL_OM;
			String idList = "";
			
			if (withoutItemIdList != null && withoutItemIdList.length > 0) {
				sql += " WHERE itemId NOT IN (";
				
				for (int i = 0; i < withoutItemIdList.length; i++) {
					idList += withoutItemIdList[i] + ", ";
				}
				
				idList = idList.substring(0, idList.length() - 2);
				sql += idList + ")";
			}
			
			db.execSQL(sql);
		} catch (Exception ex) {
			throw ex;
		}
	}
	
    public static byte[] getBytes(Bitmap bmp) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

    public static Bitmap getImage(byte[] data) {
    	if (data == null) return null;
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }
}
