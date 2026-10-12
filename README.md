# CT70 probe: `DevicePolicyManager.clearResetPasswordToken` fails during setup

On a Honeywell CT70 with Android 15, a device owner that calls `DevicePolicyManager.clearResetPasswordToken` while no reset password token exists gets a `SecurityException` for `WRITE_SECURE_SETTINGS`, but only while the setup wizard is running. After setup the same call returns `false` without error.

## Tested device

CT70 (product CT70-L0-G), Wi-Fi only, Android 15, GMS. Same result on both builds:

| Build | Security patch | Probe | Log |
|---|---|---|---|
| 615.00.01.0040-G | 2025-08-05 | 1.0 | [`evidence/ct70_615.00.01.0040-G_2026-09-25_probe-1.0_logcat.txt`](evidence/ct70_615.00.01.0040-G_2026-09-25_probe-1.0_logcat.txt) |
| 615.00.05.0089-G | 2026-07-05 | 1.1 | [`evidence/ct70_615.00.05.0089-G_2026-09-25_probe-1.1_logcat.txt`](evidence/ct70_615.00.05.0089-G_2026-09-25_probe-1.1_logcat.txt) |

## Result

| Step | Call | During setup (admin policy compliance step) | After setup |
|---|---|---|---|
| 1 | `clearResetPasswordToken` (no token set) | **`SecurityException: Permission denial, must have one of: [android.permission.WRITE_SECURE_SETTINGS]`** | `false` |
| 2 | `setResetPasswordToken` | `true` | `true` |
| 3 | `isResetPasswordTokenActive` | `true` | `true` |
| 4 | `clearResetPasswordToken` (token set) | `true` | `true` |

Stack trace of step 1 during setup (build 615.00.01.0040-G; on 615.00.05.0089-G only the `Parcel` and `ContentProvider` line numbers differ):

```
java.lang.SecurityException: Permission denial, must have one of: [android.permission.WRITE_SECURE_SETTINGS]
    at android.os.Parcel.createExceptionOrNull(Parcel.java:3242)
    at android.os.Parcel.createException(Parcel.java:3226)
    at android.os.Parcel.readException(Parcel.java:3209)
    at android.os.Parcel.readException(Parcel.java:3151)
    at android.app.admin.IDevicePolicyManager$Stub$Proxy.clearResetPasswordToken(IDevicePolicyManager.java:11834)
    at android.app.admin.DevicePolicyManager.clearResetPasswordToken(DevicePolicyManager.java:6326)
    ...
Caused by: android.os.RemoteException: Remote stack trace:
    at com.android.providers.settings.SettingsProvider.enforceHasAtLeastOnePermission(SettingsProvider.java:2383)
    at com.android.providers.settings.SettingsProvider.mutateGlobalSetting(SettingsProvider.java:1518)
    at com.android.providers.settings.SettingsProvider.insertGlobalSetting(SettingsProvider.java:1472)
    at com.android.providers.settings.SettingsProvider.call(SettingsProvider.java:463)
    at android.content.ContentProvider.call(ContentProvider.java:2704)
```

A global setting is inserted with the calling app's identity while the call is processed. In AOSP ([android15-release, `DevicePolicyManagerService.clearResetPasswordToken`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java)) this method writes no global setting; with no token set it returns `false`.

Probe 1.1 differs from 1.0 only in the result text (1.0 printed the step name twice on exceptions). Probe 1.2 additionally requests `WRITE_SECURE_SETTINGS` in its manifest (not granted unless `pm grant` is run, see Open points) and prints whether it is granted.

## Impact

The BlackBerry UEM Client (12.54.2) calls `clearResetPasswordToken` in the admin policy compliance step of a "Work space only" (fully managed) activation. The exception aborts the activation, the client shows error 3006 and factory-resets the device. Fully managed enrollment with BlackBerry UEM is therefore not possible on these CT70 builds.

## Notes

**Early return in both token methods.** In AOSP, `setResetPasswordToken` and `clearResetPasswordToken` start with the same check:

```java
if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
    return false;
}
```

`hasSecureLockScreen()` reports the device feature `android.software.secure_lock_screen` (`LockSettingsService`: `mHasSecureLockScreen = pm.hasSystemFeature(FEATURE_SECURE_LOCK_SCREEN)`), not whether a screen lock is set. On the CT70, step 2 (`setResetPasswordToken`) returns `true` during setup, so the check is passed. Check on a device: `adb shell pm has-feature android.software.secure_lock_screen`.

**Origin of the exception.** The exception is raised in `system_server` while it processes the call (remote stack trace above): `SettingsProvider` checks `WRITE_SECURE_SETTINGS` against the identity of the calling app. The key of the global setting is not logged.

**API contract.** The probe is device owner when it runs the test (`device owner = true` in the result). According to the `DevicePolicyManager` documentation, `clearResetPasswordToken` throws a `SecurityException` only if the caller is not a device or profile owner and does not hold `MANAGE_DEVICE_POLICY_RESET_PASSWORD`. `ACTION_ADMIN_POLICY_COMPLIANCE` is the step in which the admin can apply its policies before the end of the setup wizard.

**Enrollment.** QR code provisioning only, no `afw#` identifier, no Enterprise Provisioner apart from the optional ADB barcode in step 9. Both scan paths in step 4 end in `PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE` with the Google setup wizard and fail identically. In the UEM environment where the CT70 fails, devices from other manufacturers are activated with the same procedure without errors.

## Open points

- Which global setting is written during the call, and by which component.
- Workaround, not yet tested: grant `WRITE_SECURE_SETTINGS` to the device owner app before provisioning (preinstall it with the Enterprise Provisioner, run `pm grant <package> android.permission.WRITE_SECURE_SETTINGS`, then scan the QR code). `pm grant` only works for apps that request the permission in their manifest; probe 1.2 does. Whether the BlackBerry UEM Client requests it is not known.

## Requirements

| Item | Needed for | Notes |
|---|---|---|
| CT70 after a **Factory Reset** (not Enterprise Reset) | test | Wi-Fi with access to the PC below |
| PC in the same network | serving the APK | port 8765 reachable from the CT70 |
| Python 3.8 or newer | `tools/make_qr.py`, `python -m http.server` | standard library only |
| `segno` (`pip install segno`) | QR image | optional; without it, encode `qr-provisioning.json` with any QR generator |
| Android SDK build-tools (`apksigner`, via `ANDROID_HOME`) | signature checksum in the QR code | optional; without it the SHA-256 of the APK file is used, which works as well |
| `adb` (Android SDK platform-tools) | step 8 and logs | optional |
| JDK 17 and Android SDK 35 | building the APK | only if you do not use `dist/ct70-probe.apk` |

## Reproduce

1. Use the prebuilt `dist/ct70-probe.apk` (SHA-256 in `dist/SHA256SUMS`), or build it: `gradlew assembleDebug` (JDK 17, Android SDK 35), output `app/build/outputs/apk/debug/app-debug.apk`.
2. Serve the APK: `cd dist` and `python -m http.server 8765`. Provisioning on the CT70 accepts an HTTP download location.
3. Create the QR code: `python tools/make_qr.py --url http://<pc-ip>:8765/ct70-probe.apk`. Output: `qr-provisioning.png` (and `qr-provisioning.json`).
4. On the CT70 welcome screen, press **Start** and scan the QR code in the "HoneywellSetup" scan screen (used for the result above). Alternatively tap the empty area of the welcome screen 7 times to open the Android QR reader; the BlackBerry UEM Client fails identically on both paths.
5. If the Wi-Fi screen appears, connect and confirm with **Next**. Do not press Back.
6. The setup wizard downloads the probe and sets it as device owner. In the admin policy compliance step the probe runs the test and shows the block "DURING SETUP" (result table above, column "During setup").
7. Tap **Continue setup** and finish the setup wizard.
8. Run the test again after setup: `adb shell am start -n de.ixtech.ct70probe/.TokenTestActivity`. The block "AFTER SETUP" is appended. The app "CT70 Probe" shows all results.
9. Logs: `adb logcat -s CT70Probe:*`. Exceptions are logged with the full stack trace. To record logcat during setup, enable ADB in the setup wizard first with an Enterprise Provisioner barcode (`adb_enabled` = 1, "Keep Setup Wizard running" = 1).

## Contents

| Path | Content |
|---|---|
| `app/` | Probe source (device admin receiver, provisioning mode and policy compliance activities, test) |
| `dist/ct70-probe.apk` | Prebuilt APK, debug-signed |
| `tools/make_qr.py` | Creates the provisioning QR code for a given download URL |
| `evidence/` | Logcat of the run on the tested device |
