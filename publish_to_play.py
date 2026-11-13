#!/usr/bin/env python3
"""
Headless CLI Publisher for Google Play Console (Phone `internal` + Wear OS `wear:internal` tracks)
Uses Google Play Android Publisher REST API v3 with zero external pip dependencies.
"""
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

PACKAGE_NAME = "com.droid.nanitroutines"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DOCS_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "../.."))
RELEASE_OUTPUT_DIR = os.path.join(ROOT_DOCS_DIR, "Releases/NanitRoutines")
DEFAULT_KEY_PATH = os.path.join(ROOT_DOCS_DIR, ".secrets/play-store-keys.json")
PHONE_AAB_PATH = os.path.join(RELEASE_OUTPUT_DIR, "NanitRoutines.aab")
WEAR_AAB_PATH = os.path.join(RELEASE_OUTPUT_DIR, "NanitRoutines-wear.aab")
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def get_access_token(key_file_path: str) -> str:
    with open(key_file_path, "r", encoding="utf-8") as f:
        sa = json.load(f)

    client_email = sa["client_email"]
    private_key_pem = sa["private_key"]
    token_uri = sa.get("token_uri", "https://oauth2.googleapis.com/token")

    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT"}
    payload = {
        "iss": client_email,
        "scope": SCOPE,
        "aud": token_uri,
        "iat": now,
        "exp": now + 3600,
    }

    unsigned_jwt = f"{b64url(json.dumps(header).encode())}.{b64url(json.dumps(payload).encode())}"

    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as kf:
        kf.write(private_key_pem)
        key_temp_path = kf.name

    try:
        proc = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key_temp_path],
            input=unsigned_jwt.encode("ascii"),
            capture_output=True,
            check=True,
        )
        signature = b64url(proc.stdout)
    finally:
        os.remove(key_temp_path)

    signed_jwt = f"{unsigned_jwt}.{signature}"
    form_body = urllib.parse.urlencode(
        {
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": signed_jwt,
        }
    ).encode("utf-8")

    req = urllib.request.Request(
        token_uri,
        data=form_body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        token_data = json.loads(resp.read().decode("utf-8"))
        return token_data["access_token"]


def api_request(token: str, method: str, url: str, body: dict | bytes | None = None, content_type: str = "application/json") -> dict:
    headers = {"Authorization": f"Bearer {token}"}
    data = None
    if body is not None:
        if isinstance(body, dict):
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        else:
            data = body
            headers["Content-Type"] = content_type

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw.strip() else {}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code} on {method} {url}:\n{err_body}") from e


def upload_bundle(token: str, edit_id: str, aab_path: str) -> int:
    print(f"Uploading bundle: {aab_path} ({os.path.getsize(aab_path) / (1024*1024):.1f} MB)...")
    url = (
        f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/"
        f"applications/{PACKAGE_NAME}/edits/{edit_id}/bundles?uploadType=media"
    )
    with open(aab_path, "rb") as f:
        aab_bytes = f.read()
    res = api_request(token, "POST", url, body=aab_bytes, content_type="application/octet-stream")
    version_code = int(res["versionCode"])
    print(f"  -> Uploaded successfully! versionCode = {version_code}, sha256 = {res.get('sha256', '')[:16]}...")
    return version_code


def update_track(token: str, edit_id: str, track_name: str, version_code: int, release_name: str, notes: str):
    print(f"Assigning versionCode {version_code} to track '{track_name}'...")
    encoded_track = urllib.parse.quote(track_name, safe="")
    url = (
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/"
        f"applications/{PACKAGE_NAME}/edits/{edit_id}/tracks/{encoded_track}"
    )
    body = {
        "track": track_name,
        "releases": [
            {
                "name": release_name,
                "versionCodes": [str(version_code)],
                "status": "completed",
                "releaseNotes": [
                    {
                        "language": "en-US",
                        "text": notes,
                    }
                ],
            }
        ],
    }
    res = api_request(token, "PUT", url, body=body)
    print(f"  -> Track '{track_name}' updated to versionCode {version_code} (status: completed)!")
    return res


def main():
    key_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_KEY_PATH
    if not os.path.exists(key_path):
        print(f"ERROR: Service account key not found at {key_path}")
        sys.exit(1)

    print("1. Authenticating with Google Play Android Developer API...")
    token = get_access_token(key_path)
    print("   ✓ Authenticated successfully!")

    print(f"2. Creating Play Console edit session for '{PACKAGE_NAME}'...")
    edit = api_request(
        token,
        "POST",
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}/edits",
        body={},
    )
    edit_id = edit["id"]
    print(f"   ✓ Created editId = {edit_id}")

    # Inspect current tracks & bundles already in the library
    tracks_res = api_request(
        token,
        "GET",
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}/edits/{edit_id}/tracks",
    )
    print("3. Existing tracks in Play Console:")
    for t in tracks_res.get("tracks", []):
        t_name = t.get("track")
        t_releases = t.get("releases", [])
        print(f"   - Track '{t_name}': {json.dumps(t_releases)}")

    bundles_res = api_request(
        token,
        "GET",
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}/edits/{edit_id}/bundles",
    )
    existing_codes = {int(b["versionCode"]) for b in bundles_res.get("bundles", [])}
    print(f"   Existing uploaded bundle versionCodes in library: {sorted(existing_codes)}")

    # Upload Phone AAB (or reuse if already uploaded)
    if os.path.exists(PHONE_AAB_PATH):
        try:
            phone_vc = upload_bundle(token, edit_id, PHONE_AAB_PATH)
        except RuntimeError as e:
            if "already been used" in str(e) or "version code" in str(e).lower():
                phone_vc = max([c for c in existing_codes if c < 1000], default=8)
                print(f"   -> Phone versionCode {phone_vc} already in library; activating it on 'internal' track!")
            else:
                raise
        update_track(
            token,
            edit_id,
            track_name="internal",
            version_code=phone_vc,
            release_name=f"1.3.2 ({phone_vc})",
            notes="Nanit token auto-refresh, 2-tab UI, tap feedback, and Pixel Watch companion sync.",
        )

    # Upload Wear OS AAB (or reuse if already uploaded)
    if os.path.exists(WEAR_AAB_PATH):
        try:
            wear_vc = upload_bundle(token, edit_id, WEAR_AAB_PATH)
        except RuntimeError as e:
            if "already been used" in str(e) or "version code" in str(e).lower():
                wear_vc = max([c for c in existing_codes if c >= 1000], default=1002)
                print(f"   -> Wear OS versionCode {wear_vc} already in library; activating it on 'wear:internal' track!")
            else:
                raise
        update_track(
            token,
            edit_id,
            track_name="wear:internal",
            version_code=wear_vc,
            release_name=f"1.3.2-wear ({wear_vc})",
            notes="Standalone + companion Pixel Watch Tile for 1-tap Naptime and Wakeup routines.",
        )

    print("4. Committing edit to Google Play Console...")
    commit_res = api_request(
        token,
        "POST",
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE_NAME}/edits/{edit_id}:commit",
    )
    print("==========================================================")
    print(f"✅ PUBLISHED SUCCESSFULLY TO GOOGLE PLAY! (Edit {commit_res.get('id')})")
    print("==========================================================")


if __name__ == "__main__":
    main()
