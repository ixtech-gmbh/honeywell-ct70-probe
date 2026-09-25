package de.ixtech.ct70probe;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.security.SecureRandom;

/** Calls the DevicePolicyManager reset-password-token APIs as device owner and records each outcome. */
final class TokenTest {
    static final String TAG = "CT70Probe";
    private static final String PREFS = "probe";
    private static final String KEY_RESULTS = "results";

    private interface Call {
        Object run() throws Exception;
    }

    private TokenTest() {
    }

    static void run(Context context, String phase) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        ComponentName admin = new ComponentName(context, ProbeAdminReceiver.class);
        StringBuilder sb = new StringBuilder("== " + phase + "\n");
        sb.append("device: ").append(Build.MODEL).append(", build ").append(Build.DISPLAY)
                .append(", Android ").append(Build.VERSION.RELEASE)
                .append(", patch ").append(Build.VERSION.SECURITY_PATCH).append('\n');
        sb.append("user_setup_complete = ")
                .append(Settings.Secure.getInt(context.getContentResolver(), "user_setup_complete", -1)).append('\n');
        sb.append("device owner = ").append(dpm.isDeviceOwnerApp(context.getPackageName())).append('\n');
        step(sb, "1 clearResetPasswordToken (no token set)", () -> dpm.clearResetPasswordToken(admin));
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        step(sb, "2 setResetPasswordToken", () -> dpm.setResetPasswordToken(admin, token));
        step(sb, "3 isResetPasswordTokenActive", () -> dpm.isResetPasswordTokenActive(admin));
        step(sb, "4 clearResetPasswordToken (token set)", () -> dpm.clearResetPasswordToken(admin));

        String result = sb.toString();
        for (String line : result.split("\n")) {
            Log.i(TAG, line);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_RESULTS, prefs.getString(KEY_RESULTS, "") + result + "\n").commit();
    }

    static String results(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RESULTS, "(no results yet)");
    }

    private static void step(StringBuilder sb, String name, Call call) {
        try {
            sb.append(name).append(" -> ").append(call.run()).append('\n');
        } catch (Throwable t) {
            sb.append(name).append(" -> ").append(t).append('\n');
            Log.e(TAG, name, t);
        }
    }
}
