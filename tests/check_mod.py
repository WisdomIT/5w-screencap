#!/usr/bin/env python3
"""Checks mod/5w-screencap the way HMG-API's engine reads it (hmgapi/engine/ModRuntime.java),
so a mistake shows up here and not as a red mod in the car."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "mod" / "5w-screencap"
fails = []


def check(what, ok):
    if not ok:
        fails.append(what)


mod = json.loads((MOD / "mod.json").read_text(encoding="utf-8"))
settings = json.loads((MOD / "settings.json").read_text(encoding="utf-8"))

# identity and version (HMG-API: id [a-z0-9._-]{1,64}, semver)
check("id", re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,63}", mod["id"]) is not None and mod["id"] == MOD.name)
check("version is MAJOR.MINOR.PATCH", re.fullmatch(r"\d+\.\d+\.\d+", mod["version"]) is not None)
check("hmgapi range", mod.get("hmgapi", "").startswith(">="))

# the built files mod.json points at
apps = mod["apps"]
pkgs = {a["package"] for a in apps}
for a in apps:
    check(f"app {a['apk']} built", (MOD / a["apk"]).is_file())
for s in mod["services"]:
    check(f"service {s['name']} jar built", (MOD / s["jar"]).is_file())
    check(f"service {s['name']} user", s.get("user") in ("root", "system"))
    check(f"service {s['name']} start", s.get("start", "android") in ("android", "boot", "manual"))

# buttons, as ModRuntime validates them
KEYS = {"power", "home", "map", "navi", "radio", "media", "custom", "setup", "phone", "seek_up", "seek_down",
        "volume_up", "volume_down", "volume_knob_up", "volume_knob_down", "tune_up", "tune_down", "tune_enter",
        "apps", "mymenu", "info", "dest", "fmam", "fm", "am", "xm", "dmb", "ev", "nmode", "mood_lamp",
        "performance_gauge", "channel_up", "channel_down", "mode", "mute", "ptt", "ptt_rear", "wheel_custom",
        "call", "connect", "poi", "sos"}
seen = set()
choice_values = {}
for s in settings["settings"]:
    if s["type"] == "choice":
        choice_values[s["key"]] = {o["value"] for o in s["options"]}
        check(f"setting {s['key']} default is an option", s["default"] in choice_values[s["key"]])
for b in mod["input"]:
    what = f"input {b.get('key')} {b.get('press')} {b.get('when')}"
    press = b.get("press", "short")
    stock = b.get("stock", "replace" if press == "hold" else "keep")
    check(what + ": known key", b.get("key") in KEYS)
    check(what + ": press", press in ("short", "hold", "down"))
    check(what + ": hold can't keep", not (press == "hold" and "stock" in b and b["stock"] == "keep"))
    check(what + ": stock", stock in ("keep", "replace"))
    check(what + ": sos/power stay the car's", not (stock == "replace" and b.get("key") in ("sos", "power")))
    check(what + ": broadcast name", re.fullmatch(r"[A-Za-z0-9_.]{1,100}", b.get("broadcast", "")) is not None)
    check(what + ": broadcast is ours", b.get("broadcast", "").startswith(tuple(p + "." for p in pkgs)))
    m = re.fullmatch(r"!?settings\.([A-Za-z0-9_]+)(=([A-Za-z0-9_.-]+))?", b.get("when", ""))
    check(what + ": when", m is not None)
    if m:
        check(what + ": when names a choice value", m.group(3) in choice_values.get(m.group(1), set()))
        seen.add((m.group(1), m.group(3)))
for key, values in choice_values.items():
    if key not in ("capture", "record"):
        continue
    for v in values - {"off", "star"}:
        check(f"{key}={v} has a binding", (key, v) in seen)

# star-key entries and the launcher tile
for e in mod["provides"]["star_key"]:
    check(f"star_key {e['id']}: broadcast or activity", "broadcast" in e or "activity" in e)
    check(f"star_key {e['id']}: broadcast is ours", e.get("broadcast", "").startswith(tuple(p + "." for p in pkgs)))
for e in mod["provides"]["launcher"]:
    check("launcher package is a mod app", e["package"] in pkgs)

manifest = (ROOT / "app" / "AndroidManifest.xml").read_text(encoding="utf-8")
for action in {b["broadcast"] for b in mod["input"]} | {e["broadcast"] for e in mod["provides"]["star_key"]}:
    check(f"the app receives {action}", f'android:name="{action}"' in manifest)
check("car events receiver is guarded", 'android.permission.DEVICE_POWER' in manifest)

if fails:
    for f in fails:
        print("FAIL " + f)
    sys.exit(1)
print(f"mod: {len(mod['input'])} bindings, {len(choice_values)} choices ok")
