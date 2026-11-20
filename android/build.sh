#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_DIR="$ROOT_DIR/android/app/src/main"
BUILD_DIR="$ROOT_DIR/android/build"
GEN_DIR="$BUILD_DIR/generated"
CLASSES_DIR="$BUILD_DIR/classes"
DEX_FILE="$BUILD_DIR/classes.dex"
UNSIGNED_APK="$BUILD_DIR/unsigned.apk"
ALIGNED_APK="$BUILD_DIR/aligned.apk"
OUTPUT_APK="$ROOT_DIR/api-chat-app.apk"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
SIGN_DIR="${APICHAT_SIGN_DIR:-/data/user/.apichat}"
KEYSTORE="$SIGN_DIR/apichat-release.jks"
PASS_FILE="$SIGN_DIR/keystore.pass"
KEY_ALIAS="apichat"

for tool in aapt apksigner zipalign dalvik-exchange javac keytool zip; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf 'Missing required build tool: %s\n' "$tool" >&2
    exit 1
  }
done
[[ -f "$ANDROID_JAR" ]] || {
  printf 'Android platform jar not found: %s\n' "$ANDROID_JAR" >&2
  exit 1
}

if [[ ! -f "$KEYSTORE" ]]; then
  mkdir -p "$SIGN_DIR"
  umask 077
  python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$PASS_FILE"
  KEYSTORE_PASSWORD="$(<"$PASS_FILE")"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storetype JKS \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=API Chat Local, OU=Local Build, O=API Chat, L=Local, ST=Local, C=CN"
else
  KEYSTORE_PASSWORD="$(<"$PASS_FILE")"
fi

rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$CLASSES_DIR"
cp "$ROOT_DIR/api-chat-app-single.html" "$MAIN_DIR/assets/www/index.html"

aapt package -f -m \
  -J "$GEN_DIR" \
  -M "$MAIN_DIR/AndroidManifest.xml" \
  -S "$MAIN_DIR/res" \
  -I "$ANDROID_JAR"

javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$CLASSES_DIR" \
  "$GEN_DIR/com/local/apichat/R.java" \
  "$MAIN_DIR/java/com/local/apichat/MainActivity.java"

dalvik-exchange --dex --output="$DEX_FILE" "$CLASSES_DIR"

aapt package -f \
  -M "$MAIN_DIR/AndroidManifest.xml" \
  -S "$MAIN_DIR/res" \
  -A "$MAIN_DIR/assets" \
  -I "$ANDROID_JAR" \
  -F "$UNSIGNED_APK"

zip -q -j "$UNSIGNED_APK" "$DEX_FILE"
zipalign -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" \
  --key-pass "pass:$KEYSTORE_PASSWORD" \
  --out "$OUTPUT_APK" \
  "$ALIGNED_APK"

apksigner verify "$OUTPUT_APK"
printf 'DONE: %s\n' "$OUTPUT_APK"
