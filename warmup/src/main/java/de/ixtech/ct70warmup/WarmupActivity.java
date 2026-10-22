package de.ixtech.ct70warmup;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;

/**
 * Makes the first DevicePolicyManager.clearResetPasswordToken call after a factory reset, so that the device owner's
 * later call during setup no longer runs into the CT70 failure. The caller needs no rights: the failure happens before
 * any admin check, and the expected outcome here is a SecurityException.
 */
public class WarmupActivity extends Activity {
    private static final String TAG = "CT70Warmup";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            boolean result = getSystemService(DevicePolicyManager.class)
                    .clearResetPasswordToken(new ComponentName(this, WarmupActivity.class));
            Log.i(TAG, "clearResetPasswordToken -> " + result);
        } catch (RuntimeException e) {
            Log.i(TAG, "clearResetPasswordToken -> " + e);
        }
        finish();
    }
}
