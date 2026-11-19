#!/bin/sh
# Builds the mod, checks mod.json the way HMG-API reads it, and runs the JVM unit tests.
# Needs JDK 17+ and Python 3. (No Docker: the mod has no on-unit shell scripts yet.)
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TC="$ROOT/build/toolchain"
W="$ROOT/build/tests"

python3 "$ROOT/tools/gen_input.py" --check
sh "$ROOT/tools/build.sh"
python3 "$ROOT/tests/check_mod.py"

rm -rf "$W" && mkdir -p "$W"
# the pure classes and the ones whose logic is tested; android-all on the classpath to load them
javac --release 8 -encoding UTF-8 -Xlint:-options -nowarn -cp "$TC/android-all.jar" -d "$W" \
    $(find "$ROOT/daemon/src" "$ROOT/app/src" "$ROOT/app/third_party" "$ROOT/tests/java" -name '*.java')
java -cp "$W:$TC/android-all.jar" dev.wisdomit.screencap.RunTests "$ROOT"
