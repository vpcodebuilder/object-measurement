package com.vpcodebuilder.objectmeasurement;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

public class DialogHelper {
    public enum DialogResult {
        OK,
        CANCEL,
        YES,
        NO
    }

    private static DialogResult result = DialogResult.OK;

    public static DialogResult showDialog(Activity activity, String title, String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);

        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.setPositiveButton("ตกลง", new AlertDialog.OnClickListener() {
            public void onClick(DialogInterface dialog, int arg1) { }
        });

        dialog.show();

        return result;
    }

    public static DialogResult showConfirmDialog(Activity activity, String title, String message) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        result = DialogResult.YES;
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        result = DialogResult.NO;
                        break;
                }
            }
        };

        AlertDialog.Builder dialog = new AlertDialog.Builder(activity);

        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setNegativeButton("No", dialogClickListener);
        dialog.setPositiveButton("Yes", dialogClickListener);
        dialog.show();

        return result;
    }
}
