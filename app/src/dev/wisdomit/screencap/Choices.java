package dev.wisdomit.screencap;

/**
 * The Launch screen shows a button choice as two lists (button, press) and one switch
 * ("원래 기능 막기"); HMG-API keeps it as one setting value, "<key>.<mode>", which picks the
 * binding in mod.json. Plain Java, tested off the car; KEYS must match tools/gen_input.py
 * (tests compare them with settings.json).
 *
 *   off                       not used
 *   star                      the car's ★-key menu entry (one press; picked in the car's settings)
 *   <key>.short / .short_r    one press, the car's own action kept / blocked
 *   <key>.hold                a long press (always takes the car's long press)
 *   <key>.double              two presses within 0.4 s (always blocks the car's action)
 */
final class Choices {
    static final String[][] KEYS = {
        { "wheel_custom", "운전대 ☆" },
        { "custom", "헤드유닛 ☆" },
        { "mode", "운전대 MODE" },
        { "mute", "운전대 음소거" },
        { "call", "운전대 통화" },
        { "radio", "RADIO" },
        { "media", "MEDIA" },
        { "fmam", "FM/AM" },
        { "setup", "SETUP" },
        { "phone", "PHONE" },
        { "map", "MAP" },
        { "tune_enter", "TUNE 누름" },
    };
    /** first entry of the button list */
    static final String OFF_LABEL = "사용 안 함";

    static final int PRESS_SHORT = 0, PRESS_HOLD = 1, PRESS_DOUBLE = 2;
    static final String[] PRESSES = { "1회 누름", "길게 누름", "연속 2회" };
    static final String[] STAR_PRESSES = { "1회 누름 (☆ 메뉴)", "길게 누름" };

    /** one choice as the Launch screen shows it: key index in KEYS (-1: off), press, block */
    static final class Pick {
        final int key, press;
        final boolean replace;

        Pick(int key, int press, boolean replace) {
            this.key = key;
            this.press = press;
            this.replace = replace;
        }
    }

    private Choices() {}

    static boolean isStar(int key) {
        return key == 0 || key == 1;
    }

    static String[] pressesFor(int key) {
        return isStar(key) ? STAR_PRESSES : PRESSES;
    }

    static String value(Pick p) {
        if (p.key < 0) return "off";
        String k = KEYS[p.key][0];
        if (isStar(p.key)) return p.press == PRESS_HOLD ? k + ".hold" : "star";
        if (p.press == PRESS_HOLD) return k + ".hold";
        if (p.press == PRESS_DOUBLE) return k + ".double";
        return k + (p.replace ? ".short_r" : ".short");
    }

    static Pick pick(String value) {
        if (value == null || value.equals("off") || value.isEmpty()) return new Pick(-1, PRESS_SHORT, true);
        if (value.equals("star")) return new Pick(0, PRESS_SHORT, true);
        int dot = value.indexOf('.');
        String k = dot < 0 ? value : value.substring(0, dot), mode = dot < 0 ? "" : value.substring(dot + 1);
        int key = -1;
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i][0].equals(k)) key = i;
        if (key < 0) return new Pick(-1, PRESS_SHORT, true);
        if (mode.equals("hold")) return new Pick(key, PRESS_HOLD, true);
        if (mode.equals("double")) return new Pick(key, PRESS_DOUBLE, true);
        return new Pick(key, PRESS_SHORT, !mode.equals("short"));
    }

    /** why two choices can't both be saved, or null */
    static String clash(String capture, String record) {
        if ("off".equals(capture) || "off".equals(record)) return null;
        if ("star".equals(capture) && "star".equals(record)) return null;   // the car's menu gives each ★ key one entry
        Pick c = pick(capture), r = pick(record);
        if (c.key < 0 || r.key < 0 || c.key != r.key) return null;
        boolean cHold = c.press == PRESS_HOLD, rHold = r.press == PRESS_HOLD;
        if (cHold && rHold) return "캡처와 녹화에 같은 버튼의 길게 누름을 함께 쓸 수 없습니다";
        if (!cHold && !rHold) return "캡처와 녹화에 같은 버튼의 누름을 함께 쓸 수 없습니다";
        return null;
    }
}
