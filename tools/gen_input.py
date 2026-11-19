#!/usr/bin/env python3
"""Writes the button bindings of mod/5w-screencap/mod.json and the button choices of settings.json.

The owner picks a button and a press for each action: two short lists, <action>_key and
<action>_press (HMG-API's settings page shows a choice as a radio list; it has no dropdown).
HMG-API's `when` compares one setting to one value, so the binding is chosen by a third,
private setting, <action> = <key>.<mode>, that the app keeps in step with the two lists
(Settings.sync: when the Launch screen saves, and whenever HMG-API says settings changed).
Every candidate gets its own binding that counts only while that value is set.

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
# action, broadcast suffix, title, default button, default press
ACTIONS = [
    ("capture", "CAPTURE", "캡처", "wheel_custom", "short"),
    ("record", "RECORD", "녹화 시작/정지", "wheel_custom", "hold"),
]
# the press list as the owner sees it (for the star keys "short" is the car's star-key menu entry)
PRESSES = [
    ("short", "1회 누름 (☆ 키: 차량 설정 > 버튼 > ☆ 에서 고름)"),
    ("short_r", "1회 누름 · 원래 기능 막기"),
    ("hold", "길게 누름 (0.8초)"),
    ("double", "연속 2회 · 원래 기능 막기"),
]


def modes_for(key):
    return [m for m in MODES if m[0] == "hold"] if key in STAR_KEYS else MODES


def bindings():
    out = []
    for action, suffix, _, _, _ in ACTIONS:
        for key, _ in KEYS:
            for mode, (press, stock, _) in modes_for(key):
                b = {"key": key, "press": press}
                if stock == "replace":
                    b["stock"] = "replace"
                b["when"] = f"settings.{action}={key}.{mode}"
                b["broadcast"] = f"{PKG}.{suffix}"
                out.append(b)
    return out


def combined(key, press):
    """the private value for a button and a press (same rule as Choices.combined in the app)"""
    if key == "off" or key not in dict(KEYS):
        return "off"
    if key in STAR_KEYS:
        return f"{key}.hold" if press == "hold" else "star"
    return f"{key}.{press if press in dict(PRESSES) else 'short_r'}"


def choices():
    visible, private = [], []
    for action, _, title, dkey, dpress in ACTIONS:
        visible.append({"key": f"{action}_key", "type": "choice", "default": dkey, "title": f"{title} 버튼",
                        "options": [{"value": "off", "title": "사용 안 함"}] + [{"value": k, "title": l} for k, l in KEYS]})
        visible.append({"key": f"{action}_press", "type": "choice", "default": dpress, "title": f"{title} 버튼 누르는 방식",
                        "options": [{"value": v, "title": l} for v, l in PRESSES]})
        opts = [{"value": "off", "title": "사용 안 함"}, {"value": "star", "title": "차량 ☆ 메뉴"}]
        for key, label in KEYS:
            for mode, (_, _, mlabel) in modes_for(key):
                opts.append({"value": f"{key}.{mode}", "title": f"{label} · {mlabel}"})
        private.append({"key": action, "type": "choice", "private": True, "default": combined(dkey, dpress),
                        "title": f"{title} (앱이 위의 두 값으로 맞추는 값)", "options": opts})
    return visible, private


def render():
    mod = json.loads((MOD / "mod.json").read_text(encoding="utf-8"))
    mod["input"] = bindings()
    settings = json.loads((MOD / "settings.json").read_text(encoding="utf-8"))
    ours = {a[0] for a in ACTIONS} | {a[0] + "_key" for a in ACTIONS} | {a[0] + "_press" for a in ACTIONS}
    rest = [s for s in settings["settings"] if s["key"] not in ours]
    visible, private = choices()
    settings["settings"] = visible + rest + private
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
    print(f"{len(bindings())} bindings, {sum(len(c['options']) for c in choices()[1])} private choices")


if __name__ == "__main__":
    main()
