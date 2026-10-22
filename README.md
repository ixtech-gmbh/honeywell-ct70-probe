# CT70 probe: `DevicePolicyManager.clearResetPasswordToken` fails during setup

On a Honeywell CT70 with Android 15, the first call of `DevicePolicyManager.clearResetPasswordToken` after a factory reset fails with a `SecurityException` for `WRITE_SECURE_SETTINGS`, whichever app makes it. Every later call behaves as documented: with no token set it returns `false`. A device owner that makes this call during setup is usually the first caller and fails. A workaround is described below.

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

In these runs the call after setup was always the second call since the factory reset. It returns `false` because of that, not because setup has finished (see "First call after a factory reset").

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

Probe 1.1 differs from 1.0 only in the result text (1.0 printed the step name twice on exceptions). Probe 1.2 additionally requests `WRITE_SECURE_SETTINGS` in its manifest (not granted unless `pm grant` is run) and prints whether it is granted.

## Result with `WRITE_SECURE_SETTINGS` granted

Probe 1.2 preinstalled during setup and granted the permission before provisioning (Reproduce, step 10). Build 615.00.05.0089-G, log: [`evidence/ct70_615.00.05.0089-G_2026-10-08_probe-1.2_pm-grant_logcat.txt`](evidence/ct70_615.00.05.0089-G_2026-10-08_probe-1.2_pm-grant_logcat.txt). The permission stays granted through provisioning (`WRITE_SECURE_SETTINGS granted = true` in both phases).

| Step | Call | During setup | After setup |
|---|---|---|---|
| 1 | `clearResetPasswordToken` (no token set) | **`SecurityException: Package android does not belong to 10223`** | `false` |
| 2 to 4 | as above | as above | as above |

Remote stack trace of step 1 during setup:

```
at android.app.AppOpsManager.checkPackage(AppOpsManager.java:9366)
at android.content.ContentProvider.getCallingAttributionSource(ContentProvider.java:1199)
at android.content.ContentProvider.getCallingPackage(ContentProvider.java:1179)
at com.android.providers.settings.SettingsProvider.mutateGlobalSetting(SettingsProvider.java:1529)
at com.android.providers.settings.SettingsProvider.insertGlobalSetting(SettingsProvider.java:1472)
```

The permission check in `mutateGlobalSetting` now passes; the next check fails. The global setting is written in the name of package `android` (`system_server`), while the calling UID is still that of the device owner app (10223 = UID of the probe in this run). Granting the permission does not avoid the failure.

## First call after a factory reset

Build 615.00.05.0089-G, 2026-10-22. Log of rows 2 and 3: [`evidence/ct70_615.00.05.0089-G_2026-10-22_first-call-other-app_logcat.txt`](evidence/ct70_615.00.05.0089-G_2026-10-22_first-call-other-app_logcat.txt) (the device clock and time zone are set during setup, hence the jump in the timestamps).

| # | Situation | Result of `clearResetPasswordToken` (no token set) |
|---|---|---|
| 1 | Probe as device owner after setup, first call after a reboot | `false` |
| 2 | Fresh device, first call, from a copy of the probe under another package name that is not a device admin | `SecurityException: Permission denial, must have one of: [android.permission.WRITE_SECURE_SETTINGS]` |
| 3 | Then the probe as device owner during setup (QR provisioning) | `false` (steps 2 to 4 `true`) |
| 4 | Fresh device, first call, from the shell: `adb shell service call device_policy 296 i32 0 s16 com.android.shell` | `SecurityException: Package android does not belong to 2000` (the shell holds `WRITE_SECURE_SETTINGS`, so the next check fails, as in the section above) |
| 5 | Second shell call | `NullPointerException: ComponentName is null` (regular behaviour) |
| 6 | Then a call from an app that is not a device admin | `SecurityException: Admin ... does not exist or is not owned by uid ...` (regular behaviour) |

The failure happens before any admin check: the caller in row 2 is not even an active admin. It is not a setting: `settings list global`, `secure` and `system` are identical before and after the first call, and so are the system properties. On this build `clearResetPasswordToken` takes the code path without the device policy engine (`ComponentName is null` is the `Objects.requireNonNull` message of that path). 296 is `IDevicePolicyManager.Stub.TRANSACTION_clearResetPasswordToken` in the `framework.jar` of 615.00.05.0089-G; check it on other builds before using `service call`.

**A reboot re-arms the failure while setup is not complete.** With `user_setup_complete = 0`, the first call after every boot fails again, so a reboot undoes a previous warmup call. Once setup is complete (`user_setup_complete = 1`), a reboot no longer re-arms it: there the consumed state survives reboots and only a factory reset brings it back (row 1 is `false` after a reboot). This is why the workaround only helps if no reboot happens between the warmup call and the MDM activation.

## Impact

The BlackBerry UEM Client (12.54.2) calls `clearResetPasswordToken` in the admin policy compliance step of a "Work space only" (fully managed) activation. The exception aborts the activation, the client shows error 3006 and factory-resets the device. Fully managed enrollment with BlackBerry UEM is therefore not possible on these CT70 builds without the workaround below.

## Workaround

Make the first call after the factory reset, after the last reboot and before the MDM QR code is scanned. `warmup/` is a minimal app for this (`dist/ct70-warmup.apk`, package `de.ixtech.ct70warmup`, no permissions): its launcher activity calls `clearResetPasswordToken` once, logs the outcome with tag `CT70Warmup` and finishes.

Verified on 615.00.05.0089-G, 2026-10-22: with the warmup call made and no reboot before provisioning, the device owner's call during setup returned `false` and no exception followed (log [`evidence/ct70_615.00.05.0089-G_2026-10-22_warmup-then-provisioning_logcat.txt`](evidence/ct70_615.00.05.0089-G_2026-10-22_warmup-then-provisioning_logcat.txt), warmup line first, then provisioning). **There must be no reboot between the warmup call and the MDM activation**, because a reboot re-arms the failure during setup (see above). The warmup app cannot cover a later reboot itself: during setup the system keeps a freshly installed app in the stopped state across a reboot, so a `BOOT_COMPLETED` receiver in it never runs.

Without ADB the Honeywell Enterprise Provisioner can do it from the scan screen of the setup wizard. [`ep/ct70-token-warmup.xml`](ep/ct70-token-warmup.xml) is an Enterprise Provisioner project (device settings package HON660 [89-90].00.03) that waits for network, installs the warmup app from this repository and launches it, waits 5 seconds (the delay is given in milliseconds) and uninstalls it:

1. In the Enterprise Provisioner, import `ep/ct70-token-warmup.xml` under Provisioning and leave "Download files from Enterprise Provisioner" unchecked.
2. Import [`ep/ct70-wifi.xml`](ep/ct70-wifi.xml) under Settings and replace `DEMO-SSID` and `DEMO-PASSWORD` with the Wi-Fi of the site (Wireless and networks > WiFi > Supplicant > WiFiProfile1; the template is WPA/WPA2 PSK with AES-CCMP and DHCP). The device needs internet access to download the warmup app from GitHub.
3. Generate the barcode with the setup wizard kept running, so that the MDM QR code can be scanned afterwards.
4. After the factory reset, press Start on the welcome screen, scan the barcode in the "HoneywellSetup" scan screen, wait until the warmup app has been installed and removed, then scan the MDM QR code without rebooting in between.

The shell call in row 4 would work without an app, but the Provisioner's command filter does not run `service` (it allows `am`, `pm`, `dpm`, `content`, `input`, `screenrecord`, `settings` and `appops`).

## Notes

**Early return in both token methods.** In AOSP, `setResetPasswordToken` and `clearResetPasswordToken` start with the same check:

```java
if (!mHasFeature || !mLockPatternUtils.hasSecureLockScreen()) {
    return false;
}
```

`hasSecureLockScreen()` reports the device feature `android.software.secure_lock_screen` (`LockSettingsService`: `mHasSecureLockScreen = pm.hasSystemFeature(FEATURE_SECURE_LOCK_SCREEN)`), not whether a screen lock is set. On the CT70, step 2 (`setResetPasswordToken`) returns `true` during setup, so the check is passed. `adb shell pm has-feature android.software.secure_lock_screen` returns `true` on the CT70 (615.00.05.0089-G).

**Origin of the exception.** The exception is raised in `system_server` while it processes the call (remote stack traces above): the global setting is inserted by code running as package `android` under the UID of the calling app, so `SettingsProvider` checks the calling app's permissions and package. The key of the global setting is not logged.

**API contract.** The probe is device owner when it runs the test (`device owner = true` in the result). According to the `DevicePolicyManager` documentation, `clearResetPasswordToken` throws a `SecurityException` only if the caller is not a device or profile owner and does not hold `MANAGE_DEVICE_POLICY_RESET_PASSWORD`. `ACTION_ADMIN_POLICY_COMPLIANCE` is the step in which the admin can apply its policies before the end of the setup wizard.

**Enrollment.** QR code provisioning only, no `afw#` identifier, no Enterprise Provisioner apart from the optional ADB barcode in step 9. Both scan paths in step 4 end in `PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE` with the Google setup wizard and fail identically. In the UEM environment where the CT70 fails, devices from other manufacturers are activated with the same procedure without errors.

## Open points

- Which global setting is written during the first call, and by which `system_server` component.
- What the device records after the first call, so that later calls no longer write it.

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
9. Logs: `adb logcat -s CT70Probe:*`. Exceptions are logged with the full stack trace. To record logcat during setup, enable ADB in the setup wizard first with an Enterprise Provisioner barcode (`adb_enabled` = 1, "Keep Setup Wizard running" = 1). If the setup wizard then stays on "Please wait...", run `adb reboot`; it comes back to the "Set up your CT70" scan screen.
10. Variant with the permission granted: after enabling ADB as in step 9 and before step 4, run `adb install dist/ct70-probe.apk` and `adb shell pm grant de.ixtech.ct70probe android.permission.WRITE_SECURE_SETTINGS`, then continue with step 4.

## Contents

| Path | Content |
|---|---|
| `app/` | Probe source (device admin receiver, provisioning mode and policy compliance activities, test) |
| `warmup/` | Warmup app source (see "Workaround") |
| `dist/ct70-probe.apk` | Prebuilt probe APK, debug-signed |
| `dist/ct70-warmup.apk` | Prebuilt warmup APK, debug-signed |
| `ep/ct70-token-warmup.xml` | Enterprise Provisioner tasks for the workaround |
| `ep/ct70-wifi.xml` | Enterprise Provisioner Wi-Fi template for the workaround |
| `tools/make_qr.py` | Creates the provisioning QR code for a given download URL |
| `evidence/` | Logcat of the runs on the tested device |
