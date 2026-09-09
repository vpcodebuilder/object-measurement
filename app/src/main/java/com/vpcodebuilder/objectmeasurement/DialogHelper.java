package com.vpcodebuilder.objectmeasurement;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Small dialog wrappers.
 *
 * <p>The previous {@code showConfirmDialog} returned a static field immediately
 * after calling {@code show()} — before the user had touched anything — so every
 * caller acted on a stale answer. Confirmation is now delivered through a
 * callback, which is the only way it can work.</p>
 */
public final class DialogHelper {

    private DialogHelper() {
    }

    public static void alert(Context context, @StringRes int title, CharSequence message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void error(Context context, CharSequence message) {
        alert(context, R.string.dialog_error, message);
    }

    public static void error(Context context, @StringRes int message, Throwable cause) {
        CharSequence text = context.getString(message);
        if (cause != null && cause.getMessage() != null) {
            text = text + "\n\n" + cause.getMessage();
        }
        alert(context, R.string.dialog_error, text);
    }

    public static void confirm(Context context,
                               @StringRes int title,
                               @StringRes int message,
                               @StringRes int positiveLabel,
                               Runnable onConfirmed) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(positiveLabel, (DialogInterface d, int which) -> {
                    if (onConfirmed != null) onConfirmed.run();
                })
                .show();
    }
}
