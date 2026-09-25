"""Creates the Android Enterprise provisioning QR code for the CT70 probe.

Usage:
    python tools/make_qr.py --url http://<pc-ip>:8765/ct70-probe.apk [--apk dist/ct70-probe.apk]

Writes qr-provisioning.json and, if the "segno" package is installed, qr-provisioning.png.
The checksum is the SHA-256 of the signing certificate (via apksigner from the Android SDK);
without apksigner the SHA-256 of the APK file is used instead.
"""
import argparse
import base64
import glob
import hashlib
import json
import os
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
COMPONENT = "de.ixtech.ct70probe/de.ixtech.ct70probe.ProbeAdminReceiver"


def find_apksigner():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        return None
    candidates = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "apksigner*")))
    return next((c for c in reversed(candidates) if c.endswith((".bat", "apksigner"))), None)


def b64url(raw):
    return base64.urlsafe_b64encode(raw).decode()


def checksum_extra(apk):
    apksigner = find_apksigner()
    if apksigner:
        out = subprocess.run([apksigner, "verify", "--print-certs", str(apk)],
                             capture_output=True, text=True, check=True).stdout
        digest = re.search(r"certificate SHA-256 digest: ([0-9a-f]{64})", out).group(1)
        return "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", b64url(bytes.fromhex(digest))
    return "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM", b64url(hashlib.sha256(apk.read_bytes()).digest())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True, help="HTTP(S) URL the device downloads the APK from")
    parser.add_argument("--apk", default=str(ROOT / "dist" / "ct70-probe.apk"))
    args = parser.parse_args()

    key, value = checksum_extra(pathlib.Path(args.apk))
    payload = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": COMPONENT,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.url,
        key: value,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
    }
    text = json.dumps(payload, separators=(",", ":"))
    (ROOT / "qr-provisioning.json").write_text(text, encoding="utf-8")
    print(text)
    try:
        import segno
        segno.make(text, error="m").save(ROOT / "qr-provisioning.png", scale=8, border=4)
        print(f"QR code: {ROOT / 'qr-provisioning.png'}")
    except ImportError:
        print("segno not installed (pip install segno); encode the JSON above with any QR generator.")


if __name__ == "__main__":
    main()
