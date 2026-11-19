#!/bin/bash
# Builds diag3/usb/5w_diag3.jar (classes.dex) for app_process on the head unit (API 19).
# Needs a JDK and .tools/r8-8.5.35.jar (d8). Stubs stand in for android.* at compile time only.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
R8=$HERE/../.tools/r8-8.5.35.jar
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
javac --release 8 -Xlint:-options -d "$work/stubs" $(find "$HERE/stubs" -name '*.java')
javac --release 8 -Xlint:-options -cp "$work/stubs" -d "$work/classes" $(find "$HERE/src" -name '*.java')
java -cp "$R8" com.android.tools.r8.D8 --min-api 19 --release --lib "${JAVA_HOME:-$(/usr/libexec/java_home)}" \
    --classpath "$work/stubs" --output "$work/out.zip" $(find "$work/classes" -name '*.class')
cp "$work/out.zip" "$HERE/usb/5w_diag3.jar"
unzip -l "$HERE/usb/5w_diag3.jar"
