package dev.wisdomit.screencap;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * The mod's settings live in HMG-API (settings.json defaults, the owner's values over them),
 * reached with the `hmgapid` command: hmgapid knows this app by its uid. No HMG-API code is
 * bundled (it's AGPL). A copy is kept in SharedPreferences so a button press can read it
 * at once; it's refreshed whenever HMG-API says settings changed (STATE_CHANGED).
 *
 * Saving a setting re-settles the button bindings right away: HMG-API publishes its state
 * and the `when` conditions in mod.json pick the binding for the new value.
 */
final class Settings {
    static final String HMGAPID = "/system/bin/hmgapid";
    static final String CAPTURE = "capture", RECORD = "record", QUALITY = "quality",
            AUTO_RECORD = "auto_record", FLOATING = "floating";
    static final String[] ACTIONS = { CAPTURE, RECORD };

    private Settings() {}

    /** the same defaults as mod/5w-screencap/settings.json (tests check they agree) */
    static JSONObject defaults() {
        try {
            return new JSONObject()
                    .put(CAPTURE + "_key", "wheel_custom").put(CAPTURE + "_press", "short")
                    .put(RECORD + "_key", "wheel_custom").put(RECORD + "_press", "hold")
                    .put(QUALITY, "standard").put(AUTO_RECORD, false).put(FLOATING, true)
                    .put(CAPTURE, "star").put(RECORD, "wheel_custom.hold");
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** reads HMG-API's values (blocks) and keeps a copy; on failure the last copy */
    static JSONObject load(Context c) {
        String out = run(HMGAPID, "setting-get");
        JSONObject s = cached(c);
        if (out != null && out.trim().startsWith("{")) {
            try {
                // HMG-API's answer is the whole truth (its defaults with the owner's values over them):
                // start from our defaults, not the last copy, so a value it no longer has isn't kept
                s = defaults();
                JSONObject got = new JSONObject(out.trim());
                for (java.util.Iterator<String> it = got.keys(); it.hasNext(); ) {
                    String k = it.next();
                    s.put(k, got.get(k));
                }
                prefs(c).edit().putString("settings", s.toString()).apply();
            } catch (Exception ignored) { }
        }
        return s;
    }

    /** the last copy over the defaults, without asking HMG-API */
    static JSONObject cached(Context c) {
        JSONObject s = defaults();
        try {
            JSONObject got = new JSONObject(prefs(c).getString("settings", "{}"));
            for (java.util.Iterator<String> it = got.keys(); it.hasNext(); ) {
                String k = it.next();
                s.put(k, got.get(k));
            }
        } catch (Exception ignored) { }
        return s;
    }

    /**
     * Brings each action's private value (what the bindings compare) in step with its button and
     * press, and saves if that changed something. Needed after the owner edits the two lists in the
     * HMG-API app: its page doesn't show private values and saves without them. Settings from
     * before the split (only the combined value) get their button and press from it.
     * Blocks; returns the settings as they are now.
     */
    static JSONObject sync(Context c, JSONObject s) {
        if (syncValues(s)) save(c, s);
        return s;
    }

    /** the part of sync without saving (plain JSON, tested off the car); true if s changed */
    static boolean syncValues(JSONObject s) {
        boolean changed = false;
        try {
            for (String a : ACTIONS) {
                String key = s.optString(a + "_key", ""), press = s.optString(a + "_press", "");
                if (key.isEmpty()) {
                    String[] kp = Choices.split(s.optString(a));
                    s.put(a + "_key", kp[0]).put(a + "_press", kp[1]);
                    key = kp[0];
                    press = kp[1];
                    changed = true;
                }
                String want = Choices.combined(key, press);
                if (!want.equals(s.optString(a))) {
                    s.put(a, want);
                    changed = true;
                }
            }
        } catch (Exception ignored) { }
        return changed;
    }

    /** saves all values in HMG-API (blocks); false if hmgapid didn't take them */
    static boolean save(Context c, JSONObject values) {
        String out = run(HMGAPID, "setting-set", values.toString());
        boolean ok = out != null && out.contains("saved");
        if (ok) prefs(c).edit().putString("settings", values.toString()).apply();
        return ok;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("screencap", Context.MODE_PRIVATE);
    }

    /** stdout+stderr of a command, or null */
    static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
            p.waitFor();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
