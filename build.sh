#!/usr/bin/env bash
# Gradle'sız, çevrimdışı APK derleme: aapt2 + javac + d8 + apksigner
# Kullanım: ANDROID_HOME=~/Android/Sdk KEYSTORE=perde.jks KS_PASS=... ./build.sh
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.0.0}"
PLATFORM="$SDK/platforms/${PLATFORM:-android-35}/android.jar"
D8_JAR="${D8_JAR:-$BT/lib/d8.jar}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$BT/lib/apksigner.jar}"
SRC=app/src/main
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT"/{res,classes,dex}
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BT/aapt2" link -I "$PLATFORM" --manifest "$SRC/AndroidManifest.xml" -o "$OUT/base.apk" \
  --java "$OUT/gen" -A "$SRC/assets" --min-sdk-version 26 --target-sdk-version 35 "$OUT/res.zip"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 --release 11 -nowarn -classpath "$PLATFORM" -d "$OUT/classes" @"$OUT/sources.txt"
java -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 26 --lib "$PLATFORM" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes.dex)
LD_LIBRARY_PATH="$BT/lib64" "$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEYSTORE="${KEYSTORE:-debug.jks}"
KS_PASS="${KS_PASS:-android}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias perde \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Perde" >/dev/null
fi
java -jar "$APKSIGNER_JAR" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias perde --out "$OUT/Perde.apk" "$OUT/aligned.apk"
echo "APK hazır: $OUT/Perde.apk"
