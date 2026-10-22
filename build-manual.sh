#!/usr/bin/env bash
# ============================================================================
# Orange TV — pipeline build manual (tanpa Gradle).
#
# Mengikuti jalur yang sama dengan build APK di repo ini:
#   aapt2 compile -> aapt2 link -> javac -> d8 -> zipalign -> apksigner
#
# Toolchain dipinjam dari ~/workspace/glass-tv-build (JDK 17 Temurin +
# Android SDK platform 34 / build-tools 34.0.0). Sesuaikan dua variabel di
# bawah bila toolchain berpindah tempat.
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN="$HOME/workspace/glass-tv-build"
JDK="$TOOLCHAIN/jdk"
SDK="$TOOLCHAIN/sdk"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
KEYSTORE="$TOOLCHAIN/build/debug.keystore"   # kunci debug yang sama dengan build lain
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

# 1) Kompilasi sumber daya (res/)
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/resources.zip"

# 2) Tautkan sumber daya + manifest -> APK belum bertanda tangan + R.java
"$BT/aapt2" link \
    -o "$BUILD/unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
    -R "$BUILD/resources.zip" \
    --auto-add-overlay \
    --java "$BUILD/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    --version-code 5 \
    --version-name "2.2.1"

# 3) Kompilasi Java (sumber aplikasi + R.java hasil aapt2)
find "$ROOT/app/src/main/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -cp "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"
"$JDK/bin/jar" cf "$BUILD/classes.jar" -C "$BUILD/classes" .

# 4) Dexing dengan d8 (tanpa shrinking; APK tetap mungil karena framework-only)
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --release --min-api 26 \
    --output "$BUILD/dex" "$BUILD/classes.jar"

# 5) Masukkan classes.dex ke APK, rapikan (zipalign), lalu tanda tangani
cp "$BUILD/unsigned.apk" "$BUILD/app.apk"
(cd "$BUILD/dex" && zip -q "$BUILD/app.apk" classes.dex)
"$BT/zipalign" -f 4 "$BUILD/app.apk" "$BUILD/aligned.apk"
"$BT/apksigner" sign \
    --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$ROOT/orange-tv.apk" "$BUILD/aligned.apk"

# 6) Salinan pengiriman berversi + verifikasi
cp "$ROOT/orange-tv.apk" "$ROOT/orange-tv-v22.apk"
"$BT/apksigner" verify --verbose "$ROOT/orange-tv.apk"
"$BT/aapt2" dump badging "$ROOT/orange-tv.apk" | head -5
ls -l "$ROOT/orange-tv.apk" "$ROOT/orange-tv-v22.apk"
