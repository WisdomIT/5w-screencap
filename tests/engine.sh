#!/bin/sh
# The built mod under HMG-API's own engine (mod SDK, host mode, a fake head unit):
# it loads without problems, and the button bindings follow the settings the way the
# Launch screen saves them (one value per action picks one binding through "when").
#   HMGAPI_SDK=<unpacked sdk> tests/engine.sh      (tests/run.sh sets it)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MOD="$ROOT/mod/5w-screencap"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
R=$T/root
. "${HMGAPI_SDK:?set HMGAPI_SDK to the unpacked mod SDK}/test/lib.sh"
mkdir -p $R/system $R/data/hmgapi/settings
printf 'ro.board.platform=tcc897x\nro.build.product=DEPEHEV.KOR.0000.V039.001.260902\n' > $R/props.txt
install_mod "$MOD"

# the "input" bindings the engine publishes for system_server, as "key press stock broadcast" lines
bindings() {
    python3 -c '
import json, sys
s = json.load(open(sys.argv[1]))
for b in s.get("input", []):
    if b.get("mod") == "5w-screencap":
        print(b["key"], b["press"], b.get("stock", "-"), b.get("broadcast", "-").rsplit(".", 1)[-1])
' "$R/data/hmgapi/public/state.json" | sort
}

eng plan; check "plan" 0 $rc
case "$out" in *"5w-screencap:"*) echo "FAIL  the plan has problems with the mod:"; echo "$out" | grep "5w-screencap:"; fail=1 ;; *) echo "ok    no problems in the plan" ;; esac
eng apply; check "apply" 0 $rc
has "published as on" "1 of 1 mods on" "$out"

# defaults: capture through the car's star menu (no binding), record = steering wheel star held
check "defaults: one binding" "wheel_custom hold replace RECORD" "$(bindings)"

# the Launch screen saves both choices; the engine re-settles at once
printf '{"capture": "radio.short_r", "record": "mode.hold", "quality": "standard", "auto_record": false, "floating": true}\n' \
    > $R/data/hmgapi/settings/5w-screencap.json
eng publish-state; check "publish-state" 0 $rc
check "radio press replaced, MODE held" "mode hold replace RECORD
radio short replace CAPTURE" "$(bindings)"

printf '{"capture": "mute.short", "record": "off"}\n' > $R/data/hmgapi/settings/5w-screencap.json
eng publish-state
check "keep: the car still mutes" "mute short keep CAPTURE" "$(bindings)"

printf '{"capture": "call.double", "record": "star"}\n' > $R/data/hmgapi/settings/5w-screencap.json
eng publish-state
check "double: single presses taken from the car" "call short replace CAPTURE" "$(bindings)"

eng remove 5w-screencap; check "remove" 0 $rc
eng apply; check "apply after remove" 0 $rc
check "gone from the mods folder" "" "$(ls $R/data/hmgapi/mods)"
exit $fail
