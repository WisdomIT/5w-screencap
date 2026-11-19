package dev.wisdomit.screencap;

/**
 * A button choice: the owner picks a button (<action>_key) and a press (<action>_press), in the
 * Launch screen's two dropdowns or HMG-API's two lists. HMG-API's `when` can only compare one
 * setting, so the binding is picked by a private third value, <action> = combined(key, press),
 * that Settings.sync keeps in step. Plain Java, tested off the car; KEYS and PRESS_VALUES must
 * match tools/gen_input.py (tests compare them with settings.json).
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
    static final String OFF = "off", OFF_LABEL = "사용 안 함";

    static final String[] PRESS_VALUES = { "short", "short_r", "hold", "double" };
    static final String[] PRESS_TITLES = { "1회 누름", "1회 누름 · 원래 기능 막기", "길게 누름", "연속 2회" };
    /** a star key's one press is the car's ★-menu entry; a double press can't be offered there */
    static final String[] STAR_PRESS_VALUES = { "short", "hold" };
    static final String[] STAR_PRESS_TITLES = { "1회 누름 (☆ 메뉴)", "길게 누름" };

    private Choices() {}

    /** index in KEYS, or -1 for off or a key we don't offer */
    static int keyIndex(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i][0].equals(key)) return i;
        return -1;
    }

    static boolean isStar(String key) {
        return "wheel_custom".equals(key) || "custom".equals(key);
    }

    static String[] pressValues(String key) {
        return isStar(key) ? STAR_PRESS_VALUES : PRESS_VALUES;
    }

    static String[] pressTitles(String key) {
        return isStar(key) ? STAR_PRESS_TITLES : PRESS_TITLES;
    }

    /** the private value HMG-API's `when` compares (same rule as tools/gen_input.py combined()) */
    static String combined(String key, String press) {
        if (keyIndex(key) < 0) return OFF;
        if (isStar(key)) return "hold".equals(press) ? key + ".hold" : "star";
        for (String p : PRESS_VALUES) if (p.equals(press)) return key + "." + p;
        return key + ".short_r";
    }

    /** {key, press} back from a combined value (for settings saved before the split) */
    static String[] split(String combined) {
        if (combined == null || combined.isEmpty() || OFF.equals(combined)) return new String[] { OFF, "short" };
        if ("star".equals(combined)) return new String[] { "wheel_custom", "short" };
        int dot = combined.indexOf('.');
        String key = dot < 0 ? combined : combined.substring(0, dot), press = dot < 0 ? "short" : combined.substring(dot + 1);
        if (keyIndex(key) < 0) return new String[] { OFF, "short" };
        return new String[] { key, press };
    }

    /** why the two combined choices can't both be saved, or null */
    static String clash(String capture, String record) {
        if (OFF.equals(capture) || OFF.equals(record)) return null;
        if ("star".equals(capture) && "star".equals(record)) return null;   // the car's menu gives each ★ key one entry
        String[] c = split(capture), r = split(record);
        if (!c[0].equals(r[0]) || OFF.equals(c[0])) return null;
        boolean cHold = "hold".equals(c[1]), rHold = "hold".equals(r[1]);
        if (cHold && rHold) return "캡처와 녹화에 같은 버튼의 길게 누름을 함께 쓸 수 없습니다";
        if (!cHold && !rHold) return "캡처와 녹화에 같은 버튼의 누름을 함께 쓸 수 없습니다";
        return null;
    }
}
