#!/usr/bin/env bash
# Derle + JVM testleri çalıştır
set -e
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
./build.sh >/dev/null
mkdir -p build/test
javac -encoding UTF-8 -nowarn -cp build/classes:"$SDK/platforms/android-35/android.jar" -d build/test tools/Harness.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp build/test:build/classes:"$SDK/platforms/android-35/android.jar" Harness app/src/main/assets
