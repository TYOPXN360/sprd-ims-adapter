#!/usr/bin/env bash
# Build the VP19 Spreadtrum IMS adapter APK from source.
#
# The adapter reuses the 29 factory-generated HIDL classes
# (vendor.sprd.hardware.radio.V1_0.*) from the original Android 8 ims.apk,
# plus four generated callback classes (two Spreadtrum, two standard radio),
# merged into a single classes.dex along with the Java sources in
# SprdImsAdapterPrototype/app.
#
# Prerequisites (paths below are examples - adjust to your machine):
#   ANDROID_SDK      Android SDK root (build-tools 36.0.0)
#   ANDROID_ALL_JAR  Android 11 full framework (compileOnly; e.g. android-all-11.jar)
#   JDK              JDK 17
#   APKTOOL_JAR      apktool.jar
#   STOCK_IMS_APK    original Android 8 ims.apk (for HIDL generated classes)
#   PLATFORM_PK8 / PLATFORM_CERT  platform signing key (e.g. AOSP testkey)
#
# Output: out/SprdImsAdapterPrototype.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/tools"
OUT="$ROOT/out"
mkdir -p "$OUT"

# ---------------------------------------------------------------- config
ANDROID_SDK="${ANDROID_SDK:-/path/to/android-sdk}"
ANDROID_ALL_JAR="${ANDROID_ALL_JAR:-/path/to/android-all-11.jar}"
JDK="${JDK:-/path/to/jdk-17}"
APKTOOL_JAR="${APKTOOL_JAR:-/path/to/apktool.jar}"
STOCK_IMS_APK="${STOCK_IMS_APK:-/path/to/stock-ims.apk}"
PLATFORM_PK8="${PLATFORM_PK8:-platform.pk8}"
PLATFORM_CERT="${PLATFORM_CERT:-platform.x509.pem}"

BT="$ANDROID_SDK/build-tools/36.0.0"
D8="$JDK/bin/java -cp $BT/lib/d8.jar com.android.tools.r8.D8"
APKSIGNER="$JDK/bin/java -jar $BT/lib/apksigner.jar"
JAVAC="$JDK/bin/javac"

# ---------------------------------------------------- 1. stock HIDL classes
# Disassemble the stock ims.apk and keep the Spreadtrum radio HIDL classes.
STOCK_SMALI="$OUT/stock-smali"
rm -rf "$STOCK_SMALI"
"$JDK/bin/java" -jar "$APKTOOL_JAR" d -f -o "$STOCK_SMALI" "$STOCK_IMS_APK"
HIDL_SMALI="$OUT/hidl-smali"
rm -rf "$HIDL_SMALI"
mkdir -p "$HIDL_SMALI"
cp -r "$STOCK_SMALI/smali/vendor/sprd/hardware/radio/V1_0" "$HIDL_SMALI/"

# -------------------------------------------------- 2. generated callbacks
# Spreadtrum callbacks (smali, from the stock HIDL interface stubs).
CALLBACK_SMALI="$OUT/callback-smali"
rm -rf "$CALLBACK_SMALI"
mkdir -p "$CALLBACK_SMALI"
python3 "$TOOLS/gen_ims_callbacks.py" "$CALLBACK_SMALI" "$HIDL_SMALI"

# Standard radio callbacks (Java, signatures from android-all-11.jar).
"$JDK/bin/javac" -cp "$ANDROID_ALL_JAR" -d "$OUT" "$TOOLS/ExtractRadioApi.java"
"$JDK/bin/java" -cp "$OUT:$ANDROID_ALL_JAR" ExtractRadioApi > "$OUT/radio-api.txt"
STD_SRC="$OUT/std-callback-src"
rm -rf "$STD_SRC"
mkdir -p "$STD_SRC"
python3 "$TOOLS/gen_std_radio_callbacks.py" "$OUT/radio-api.txt" "$STD_SRC"

# ------------------------------------------------- 3. classes2.dex (HIDL)
# Rebuild the smali (stock HIDL + Spreadtrum callbacks) into a dex via apktool.
SUPPORT_APK="$OUT/hidl-adapter-support.apk"
rm -rf "$OUT/support-build"
mkdir -p "$OUT/support-build"
cp -r "$OUT/callback-smali" "$OUT/support-build/smali/com/vp19/sprdims/adapter/prototype"
mkdir -p "$OUT/support-build/smali/vendor"
cp -r "$HIDL_SMALI" "$OUT/support-build/smali/vendor/sprd"
# minimal manifest for apktool build
cat > "$OUT/support-build/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.vp19.sprdims.adapter.prototype"/>
XML
"$JDK/bin/java" -jar "$APKTOOL_JAR" b "$OUT/support-build" -o "$SUPPORT_APK"
unzip -p "$SUPPORT_APK" classes.dex > "$OUT/classes2.dex"

# ---------------------------------------------- 4. missing framework types
# GSI BOOTCLASSPATH lacks android.hidl.base.V1_0 and android.hardware.radio.V1_0;
# pull those .class files from android-all-11.jar so they ride in the APK dex.
HIDL_BASE_JAR="$OUT/hidlbase.jar"
HIDL_RADIO_JAR="$OUT/hidlradio.jar"
rm -rf "$OUT/hidlbase-classes" "$OUT/hidlradio-classes"
mkdir -p "$OUT/hidlbase-classes" "$OUT/hidlradio-classes"
for c in DebugInfo 'DebugInfo$Architecture' IBase 'IBase$Proxy' 'IBase$Stub'; do
  unzip -o -q "$ANDROID_ALL_JAR" "android/hidl/base/V1_0/$c.class" -d "$OUT/hidlbase-classes"
done
for c in RadioResponseInfo RadioError RadioResponseType UusInfo UusDcs UusType; do
  unzip -o -q "$ANDROID_ALL_JAR" "android/hardware/radio/V1_0/$c.class" -d "$OUT/hidlradio-classes"
done
(cd "$OUT/hidlbase-classes" && "$JDK/bin/jar" cf "$HIDL_BASE_JAR" .)
(cd "$OUT/hidlradio-classes" && "$JDK/bin/jar" cf "$HIDL_RADIO_JAR" .)

# ---------------------------------------------------- 5. Java main classes
MAIN_SRC="$ROOT/SprdImsAdapterPrototype/app/src/main/java"
CLASSES_OUT="$OUT/classes"
rm -rf "$CLASSES_OUT"
mkdir -p "$CLASSES_OUT"
"$JAVAC" -source 8 -target 8 -cp "$ANDROID_ALL_JAR" -d "$CLASSES_OUT" \
  "$MAIN_SRC/com/vp19/sprdims/adapter/prototype/"*.java \
  "$STD_SRC/Vp19StdRadioResponse.java" \
  "$STD_SRC/Vp19StdRadioIndication.java"
"$JDK/bin/jar" cf "$OUT/classes.jar" -C "$CLASSES_OUT" .

# ------------------------------------------------- 6. merge into single dex
DEX_OUT="$OUT/dex"
rm -rf "$DEX_OUT"
mkdir -p "$DEX_OUT"
$D8 --min-api 30 --lib "$ANDROID_ALL_JAR" --output "$DEX_OUT" \
  "$OUT/classes.jar" "$OUT/classes2.dex" "$HIDL_RADIO_JAR" "$HIDL_BASE_JAR"
test -f "$DEX_OUT/classes.dex"

# -------------------------------------------------------------- 7. package
# Reuse the app's manifest; build the APK with aapt2, then sign.
"$BT/aapt2" link --auto-add-overlay --no-resource-removal \
  -I "$ANDROID_SDK/platforms/android-30/android.jar" \
  --manifest "$MAIN_SRC/AndroidManifest.xml" \
  -o "$OUT/unsigned.apk"
( cd "$DEX_OUT" && "$JDK/bin/jar" cf "$OUT/dex.jar" . )
"$JDK/bin/zip" -j "$OUT/unsigned.apk" "$DEX_OUT/classes.dex" >/dev/null 2>&1 || \
  /usr/bin/zip -j "$OUT/unsigned.apk" "$DEX_OUT/classes.dex" >/dev/null
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --key "$PLATFORM_PK8" --cert "$PLATFORM_CERT" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/SprdImsAdapterPrototype.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify --verbose "$OUT/SprdImsAdapterPrototype.apk" | head -8

echo
echo "Built: $OUT/SprdImsAdapterPrototype.apk"
echo "Deploy: push to /system/priv-app/SprdImsAdapterPrototype/ and set"
echo "  persist.dbg.volte_avail_ovr0=1, persist.dbg.volte_avail_ovr=1"
