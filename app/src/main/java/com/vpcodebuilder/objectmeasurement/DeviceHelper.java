package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class DeviceHelper {
	public static Point getScreenResolution(Context context) {
		try {
			Point size = new Point();
			WindowManager windowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
			
			if (Build.VERSION.SDK_INT >= 11) {
		        try {
		        	windowManager.getDefaultDisplay().getRealSize(size);
		        } catch (NoSuchMethodError e) {
		        	size.x = windowManager.getDefaultDisplay().getWidth();
		        	size.y = windowManager.getDefaultDisplay().getHeight();
		        }
		    } else {
		        DisplayMetrics metrics = new DisplayMetrics();
		        
		        windowManager.getDefaultDisplay().getMetrics(metrics);
		        size.x = metrics.widthPixels;
		        size.y = metrics.heightPixels;
		    }
			
			return size;
		} catch (Exception ex) {
			return null;
		}
	}
}
