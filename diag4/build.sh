#!/bin/bash
# Builds diag4/usb/5w_diag4.jar (classes.dex) for app_process on the head unit (API 19),
# against the android-all 4.4 jar tools/build.sh fetches into build/toolchain.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
TC=$HERE/../build/toolchain
[ -f "$TC/android-all.jar" ] && [ -f "$TC/r8.jar" ] || { echo "run tools/build.sh once first (it fetches build/toolchain)"; exit 1; }
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -cp "$TC/android-all.jar" -d "$work/classes" $(find "$HERE/src" -name '*.java')
java -cp "$TC/r8.jar" com.android.tools.r8.D8 --release --min-api 19 --lib "$TC/android-all.jar" \
    --lib "$(/usr/libexec/java_home)" --output "$work/out.zip" $(find "$work/classes" -name '*.class')
cp "$work/out.zip" "$HERE/usb/5w_diag4.jar"
unzip -l "$HERE/usb/5w_diag4.jar" | tail -1
