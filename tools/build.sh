#!/bin/sh
# Builds the mod's two binaries into mod/5w-screencap/:
#   svc/screencapd.jar   the root service (classes.dex, run by HMG-API under app_process)
#   app/Screencap.apk    the car app (buttons, feedback, floating button, Launch screen)
#
# No Android SDK: the tools come from public Maven repositories, pinned by version and sha256
# (same set as pretendard-systemfont):
#   aapt2  (com.android.tools.build:aapt2, Apache-2.0)  manifest -> base.apk
#   r8/d8  (com.android.tools:r8, BSD-3)                 .class -> classes.dex, API 19 check
#   android-all 4.4 (org.robolectric, Apache-2.0)        the API 19 framework to compile against
# Signing: JDK keytool/jarsigner (v1 signature, what KitKat reads).
#
# App key: $SC_APP_KEY (default ~/.5w-screencap/app-release.p12, password in the .pass next to it).
# Made on first build if missing. Updates must be signed with the same key: back it up.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/build/toolchain"
W="$ROOT/build/work"
MOD="$ROOT/mod/5w-screencap"
KEY="${SC_APP_KEY:-$HOME/.5w-screencap/app-release.p12}"
PASSF="${KEY%.p12}.pass"

case "$(uname -s)" in
    Darwin) AAPT2_OS=osx ;;
    Linux)  AAPT2_OS=linux ;;
    *) echo "unsupported OS"; exit 1 ;;
esac

fetch() {   # file url sha256
    if [ ! -f "$TC/$1" ] && [ -n "${SC_TOOLCHAIN_CACHE:-}" ] && [ -f "$SC_TOOLCHAIN_CACHE/$1" ]; then
        cp "$SC_TOOLCHAIN_CACHE/$1" "$TC/$1"
    fi
    if [ ! -f "$TC/$1" ]; then
        echo "download $2"
        curl -fsSL -o "$TC/$1.part" "$2"
        mv "$TC/$1.part" "$TC/$1"
    fi
    got="$(shasum -a 256 "$TC/$1" | cut -d' ' -f1)"
    if [ "$got" != "$3" ]; then echo "sha256 mismatch: $1 ($got)" >&2; rm -f "$TC/$1"; exit 1; fi
}

mkdir -p "$TC"
G=https://dl.google.com/android/maven2
if [ "$AAPT2_OS" = osx ]; then
    fetch aapt2-osx.jar "$G/com/android/tools/build/aapt2/8.7.3-12006047/aapt2-8.7.3-12006047-osx.jar" \
        8cfca8d3fcb8ad45cf46e3780cdd4a64cb38c70ca45b442655de9f3e7d19efc1
else
    fetch aapt2-linux.jar "$G/com/android/tools/build/aapt2/8.7.3-12006047/aapt2-8.7.3-12006047-linux.jar" \
        "${SC_AAPT2_LINUX_SHA256:?set SC_AAPT2_LINUX_SHA256 on Linux}"
fi
fetch r8.jar "$G/com/android/tools/r8/8.7.18/r8-8.7.18.jar" \
    58366f77067207c39a17d469de7b05701d2877212a9c55201bcb0af43e59e903
fetch android-all.jar "https://repo1.maven.org/maven2/org/robolectric/android-all/4.4_r1-robolectric-r2/android-all-4.4_r1-robolectric-r2.jar" \
    5c68ab0a11d6f46e303505e75da62215f45b6a46ff7877441a31d527899359f9
if [ ! -x "$TC/aapt2" ]; then
    (cd "$TC" && unzip -o -q "aapt2-$AAPT2_OS.jar" aapt2 && chmod +x aapt2)
    xattr -d com.apple.quarantine "$TC/aapt2" 2>/dev/null || true
fi
ANDROID_JAR="$TC/android-all.jar"
JHOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p')"

python3 "$ROOT/tools/gen_input.py" --check

# version: mod.json "version" -> versionName, versionCode (0.1.0 -> 100, 1.2.3 -> 10203)
VNAME="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["version"])' "$MOD/mod.json")"
VCODE="$(echo "$VNAME" | awk -F. '{printf "%d", ($1*10000)+($2*100)+$3}')"
[ "$VCODE" -gt 0 ] || VCODE=1

dex() {   # out-dir class-dir
    java -cp "$TC/r8.jar" com.android.tools.r8.D8 --release --min-api 19 --lib "$ANDROID_JAR" --lib "$JHOME" \
        --output "$1" $(find "$2" -name '*.class')
}

rm -rf "$W" && mkdir -p "$W/daemon/classes" "$W/app/classes" "$MOD/svc" "$MOD/app"

# ---- service ----
javac --release 8 -encoding UTF-8 -Xlint:-options -nowarn -cp "$ANDROID_JAR" -d "$W/daemon/classes" \
    $(find "$ROOT/daemon/src" -name '*.java')
dex "$W/daemon" "$W/daemon/classes"
(cd "$W/daemon" && zip -q -X screencapd.jar classes.dex)
cp "$W/daemon/screencapd.jar" "$MOD/svc/screencapd.jar"

# ---- app ----
"$TC/aapt2" link -o "$W/app/base.apk" -I "$ANDROID_JAR" --manifest "$ROOT/app/AndroidManifest.xml" \
    --min-sdk-version 19 --target-sdk-version 19 --version-code "$VCODE" --version-name "$VNAME"
javac --release 8 -encoding UTF-8 -Xlint:-options -nowarn -cp "$ANDROID_JAR" -d "$W/app/classes" \
    $(find "$ROOT/app/src" "$ROOT/app/third_party" -name '*.java')
dex "$W/app" "$W/app/classes"
cp "$W/app/base.apk" "$W/app/unsigned.apk"
(cd "$W/app" && zip -q -X unsigned.apk classes.dex)

if [ ! -f "$KEY" ]; then
    echo "no signing key; making one: $KEY  (back it up)"
    mkdir -p "$(dirname "$KEY")" && chmod 700 "$(dirname "$KEY")"
    head -c 24 /dev/urandom | base64 | tr -d '/+=' > "$PASSF" && chmod 600 "$PASSF"
    keytool -genkeypair -keystore "$KEY" -storetype PKCS12 -storepass "$(cat "$PASSF")" \
        -alias app -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=WisdomIT 5w-screencap" >/dev/null 2>&1
    chmod 600 "$KEY"
fi
jarsigner -keystore "$KEY" -storepass "$(cat "$PASSF")" -sigalg SHA256withRSA -digestalg SHA-256 \
    -signedjar "$W/app/signed.apk" "$W/app/unsigned.apk" app >/dev/null
jarsigner -verify "$W/app/signed.apk" >/dev/null
cp "$W/app/signed.apk" "$MOD/app/Screencap.apk"

echo "built $VNAME ($VCODE): svc/screencapd.jar $(($(wc -c < "$MOD/svc/screencapd.jar") / 1024)) KB, app/Screencap.apk $(($(wc -c < "$MOD/app/Screencap.apk") / 1024)) KB"
