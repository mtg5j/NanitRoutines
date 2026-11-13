#!/usr/bin/env bash
set -euo pipefail

# Determine directories relative to script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DOCS_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RELEASE_OUTPUT_DIR="${ROOT_DOCS_DIR}/Releases/NanitRoutines"
SECRETS_DIR="${ROOT_DOCS_DIR}/.secrets"

cd "${SCRIPT_DIR}"
mkdir -p "${RELEASE_OUTPUT_DIR}"

# Automatically bump Phone versionCode in app/build.gradle.kts
current_code=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | awk '{print $3}')
next_code=$((current_code + 1))
sed -i "s/versionCode = ${current_code}/versionCode = ${next_code}/" app/build.gradle.kts
echo ">>> Bumped Phone versionCode: ${current_code} -> ${next_code}"

# Automatically bump Wear OS versionCode in wear/build.gradle.kts
wear_code=$(grep -oE 'versionCode = [0-9]+' wear/build.gradle.kts | awk '{print $3}')
next_wear_code=$((wear_code + 1))
sed -i "s/versionCode = ${wear_code}/versionCode = ${next_wear_code}/" wear/build.gradle.kts
echo ">>> Bumped Wear OS versionCode: ${wear_code} -> ${next_wear_code}"

./gradlew :app:bundleRelease :app:assembleRelease :wear:bundleRelease :wear:assembleRelease \
    -x lintVitalRelease -x lintVitalAnalyzeRelease \
    -Dorg.gradle.jvmargs="-Xmx1536m" \
    --project-cache-dir="$HOME/.cache/projects/NanitRoutines/gradle"

cp app/build/outputs/bundle/release/NanitRoutines-release.aab "${RELEASE_OUTPUT_DIR}/NanitRoutines.aab"
cp app/build/outputs/apk/release/NanitRoutines-release.apk "${RELEASE_OUTPUT_DIR}/NanitRoutines.apk"

cp wear/build/outputs/bundle/release/NanitRoutines-wear-release.aab "${RELEASE_OUTPUT_DIR}/NanitRoutines-wear.aab"
cp wear/build/outputs/apk/release/NanitRoutines-wear-release.apk "${RELEASE_OUTPUT_DIR}/NanitRoutines-wear.apk"

echo "=========================================================="
echo "✅ Phone + Wear OS Release Builds Successful!"
echo "Phone AAB (Play Console):   ${RELEASE_OUTPUT_DIR}/NanitRoutines.aab (versionCode ${next_code})"
echo "Phone APK:                  ${RELEASE_OUTPUT_DIR}/NanitRoutines.apk"
echo "Wear OS AAB (Play Console): ${RELEASE_OUTPUT_DIR}/NanitRoutines-wear.aab (versionCode ${next_wear_code})"
echo "Wear OS APK:                ${RELEASE_OUTPUT_DIR}/NanitRoutines-wear.apk"
echo "=========================================================="

if [ -f "${SECRETS_DIR}/play-store-keys.json" ]; then
    echo ">>> Auto-publishing to Google Play Console (internal & wear:internal)..."
    python3 "${SCRIPT_DIR}/publish_to_play.py" "${SECRETS_DIR}/play-store-keys.json"
fi
