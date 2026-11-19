#!/usr/bin/env python3
"""Writes the button bindings of mod/5w-screencap/mod.json and the button choices of settings.json.

HMG-API's `when` compares one setting to one value, so the owner's pick for an action
("which button, which press") is one setting value, <key>.<mode>, and every candidate
gets its own binding that counts only while that value is set. The Launch screen
shows the same pick as two lists (button, press).

  tools/gen_input.py          rewrite the two files
  tools/gen_input.py --check  exit 1 if they are not what this script would write
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "mod" / "5w-screencap"
PKG = "dev.wisdomit.screencap"

# HMG-API key name -> label on the car (Korean). The star keys' short press belongs to the
# car's star-key menu, where this mod offers its own entries (provides.star_key).
KEYS = [
    ("wheel_custom", "운전대 ☆"),
    ("custom", "헤드유닛 ☆"),
    ("mode", "운전대 MODE"),
    ("mute", "운전대 음소거"),
    ("call", "운전대 통화"),
    ("radio", "RADIO"),
    ("media", "MEDIA"),
    ("fmam", "FM/AM"),
    ("setup", "SETUP"),
    ("phone", "PHONE"),
    ("map", "MAP"),
    ("tune_enter", "TUNE 누름"),
]
STAR_KEYS = {"wheel_custom", "custom"}

# mode -> (press, stock, label). "double" is two short presses within 0.4 s, counted by the app;
# HMG-API only delivers single presses, so the car never gets the first of the two.
MODES = [
    ("short", ("short", "keep", "1회 누름 (원래 기능도 실행)")),
    ("short_r", ("short", "replace", "1회 누름 (원래 기능 막기)")),
    ("hold", ("hold", None, "길게 누름")),
    ("double", ("short", "replace", "연속 2회 (원래 기능 막기)")),
]
ACTIONS = [
    ("capture", "CAPTURE", "캡처 버튼", "star"),
    ("record", "RECORD", "녹화 시작/정지 버튼", "wheel_custom.hold"),
]


def modes_for(key):
    return [m for m in MODES if m[0] == "hold"] if key in STAR_KEYS else MODES


def bindings():
    out = []
    for action, suffix, _, _ in ACTIONS:
        for key, _ in KEYS:
            for mode, (press, stock, _) in modes_for(key):
                b = {"key": key, "press": press}
                if stock == "replace":
                    b["stock"] = "replace"
                b["when"] = f"settings.{action}={key}.{mode}"
                b["broadcast"] = f"{PKG}.{suffix}"
                out.append(b)
    return out


def choices():
    out = []
    for action, _, title, default in ACTIONS:
        opts = [{"value": "off", "title": "사용 안 함"},
                {"value": "star", "title": "차량 ☆ 버튼 설정에서 고름 (설정 > 버튼 > ☆)"}]
        for key, label in KEYS:
            for mode, (_, _, mlabel) in modes_for(key):
                opts.append({"value": f"{key}.{mode}", "title": f"{label} · {mlabel}"})
        out.append({"key": action, "type": "choice", "default": default, "title": title, "options": opts})
    return out


def render():
    mod = json.loads((MOD / "mod.json").read_text(encoding="utf-8"))
    mod["input"] = bindings()
    settings = json.loads((MOD / "settings.json").read_text(encoding="utf-8"))
    rest = [s for s in settings["settings"] if s["key"] not in {a[0] for a in ACTIONS}]
    settings["settings"] = choices() + rest
    dump = lambda o: json.dumps(o, ensure_ascii=False, indent=2) + "\n"
    return {MOD / "mod.json": dump(mod), MOD / "settings.json": dump(settings)}


def main():
    files = render()
    if "--check" in sys.argv:
        stale = [p.name for p, text in files.items() if p.read_text(encoding="utf-8") != text]
        if stale:
            print("out of date (run tools/gen_input.py): " + ", ".join(stale))
            sys.exit(1)
        return
    for p, text in files.items():
        p.write_text(text, encoding="utf-8")
    print(f"{len(bindings())} bindings, {sum(len(c['options']) for c in choices())} choices")


if __name__ == "__main__":
    main()
