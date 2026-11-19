#!/bin/sh
# Builds and packages the mod into dist/release/ (<v> = mod.json "version"):
#   5w-screencap-<v>.zip                  the signed HMG-API mod (HMG-API app, USB HMGAPI/updates/, adb)
#   5w-screencap-<v>-usb.zip              for a USB stick: unzip onto it, NEMESIS installs it (needs HMG-API)
#   5w-screencap-<v>-nemesis-package.zip  the same as a NEMESIS package (Package Store, nemesis install)
#   SHA256SUMS.txt
#
# Mod key: $SC_MOD_KEY (default ~/.5w-screencap/mod-author.key). The mod's UUID comes from it:
# only packages signed with the same key update an installed mod. Keep it private and backed up.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
KEY="${SC_MOD_KEY:-$HOME/.5w-screencap/mod-author.key}"
[ -f "$KEY" ] || { echo "no mod key at $KEY (make one: java -cp build/sdk/sdk/lib/hmgapi-engine.jar hmgapi.engine.Signing keygen $KEY)"; exit 1; }
SDK="$(sh tools/sdk.sh)"
V="$(python3 -c 'import json; print(json.load(open("mod/5w-screencap/mod.json"))["version"])')"

sh tools/build.sh
rm -rf dist/packages dist/release
OUT="$ROOT/dist" sh "$SDK/bin/mkmodpkg.sh" mod/5w-screencap "$KEY" >/dev/null
mkdir -p dist/release
cp "dist/packages/5w-screencap-$V.zip" dist/release/
sh "$SDK/bin/mknemesismod.sh" "dist/packages/5w-screencap-$V.zip" "$ROOT/dist/release" >/dev/null
# same name as pretendard-systemfont's stick bundle (the SDK calls it -nemesis.zip)
mv "dist/release/5w-screencap-$V-nemesis.zip" "dist/release/5w-screencap-$V-usb.zip"
# the signature checks out against this key's UUID (what the head unit compares on update)
UUID="$(java -cp "$SDK/lib/hmgapi-engine.jar" hmgapi.engine.Signing uuid "$KEY.pub" 2>/dev/null || java -cp "$SDK/lib/hmgapi-engine.jar" hmgapi.engine.Signing uuid "$KEY")"
V_DIR="$(mktemp -d)"
unzip -q "dist/release/5w-screencap-$V.zip" -d "$V_DIR"
java -cp "$SDK/lib/hmgapi-engine.jar" hmgapi.engine.Signing verify "$V_DIR" "$UUID" >/dev/null || { echo "signature doesn't verify"; rm -rf "$V_DIR"; exit 1; }
rm -rf "$V_DIR"
echo "signed by $UUID"
(cd dist/release && shasum -a 256 5w-screencap-* > SHA256SUMS.txt)
ls -l dist/release | awk 'NR>1 {printf "%8d  %s\n", $5, $9}'
