package dev.wisdomit.screencap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import static dev.wisdomit.screencap.T.check;
import static dev.wisdomit.screencap.T.eq;

public final class AppTests {
    public static void run(String root) throws Exception {
        // DoublePress: two presses within 0.4 s count once; a lone press never does
        DoublePress d = new DoublePress(400);
        check("first press waits", !d.press(1000));
        check("second within window fires", d.press(1300));
        check("third starts over", !d.press(1350));
        check("too late", !d.press(1800));
        check("then a quick one fires", d.press(1900));

        // Choices: the two lists and the private value, as settings.json has them
        JSONObject settings = new JSONObject(T.read(root + "/mod/5w-screencap/settings.json"));
        JSONArray all = settings.getJSONArray("settings");
        JSONObject defaults = new JSONObject();
        Set<String> keyOpts = new HashSet<String>(), pressOpts = new HashSet<String>(), combinedOpts = new HashSet<String>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject s = all.getJSONObject(i);
            defaults.put(s.getString("key"), s.get("default"));
            Set<String> into = s.getString("key").equals("capture_key") ? keyOpts
                    : s.getString("key").equals("capture_press") ? pressOpts
                    : s.getString("key").equals("capture") ? combinedOpts : null;
            if (into == null) continue;
            JSONArray o = s.getJSONArray("options");
            for (int k = 0; k < o.length(); k++) into.add(o.getJSONObject(k).getString("value"));
            if (into == combinedOpts) check("the combined value is private", s.optBoolean("private"));
            else check(s.getString("key") + " is shown", !s.optBoolean("private"));
        }
        Set<String> keys = new HashSet<String>();
        keys.add(Choices.OFF);
        for (String[] k : Choices.KEYS) keys.add(k[0]);
        eq("button list", keyOpts, keys);
        eq("press list", pressOpts, new HashSet<String>(java.util.Arrays.asList(Choices.PRESS_VALUES)));
        Set<String> made = new HashSet<String>();
        for (String k : keys) {
            for (String p : Choices.PRESS_VALUES) {
                String v = Choices.combined(k, p);
                made.add(v);
                check("settings.json has a value for " + k + " " + p + " (" + v + ")", combinedOpts.contains(v));
            }
        }
        eq("every private value is reachable from the two lists", combinedOpts, made);
        for (String[] k : Choices.KEYS) {
            for (String p : Choices.pressValues(k[0])) {
                String v = Choices.combined(k[0], p);
                if (!v.equals("star")) eq("split(" + v + ")", k[0] + " " + p, Choices.split(v)[0] + " " + Choices.split(v)[1]);
            }
        }
        eq("star key, one press -> the car's menu", "star", Choices.combined("wheel_custom", "short"));
        eq("star key, double -> the car's menu", "star", Choices.combined("custom", "double"));
        eq("star key, hold", "custom.hold", Choices.combined("custom", "hold"));
        eq("off", "off", Choices.combined("off", "hold"));
        eq("unknown key is off", "off", Choices.combined("nope", "short"));
        eq("unknown press blocks", "radio.short_r", Choices.combined("radio", "x"));
        eq("split star", "wheel_custom", Choices.split("star")[0]);
        eq("split off", "off", Choices.split("off")[0]);
        check("same key, both short: clash", Choices.clash("radio.short_r", "radio.double") != null);
        check("same key, both hold: clash", Choices.clash("mode.hold", "mode.hold") != null);
        check("same key, short and hold: fine", Choices.clash("radio.short_r", "radio.hold") == null);
        check("both on the ★ menu: fine", Choices.clash("star", "star") == null);
        eq("defaults agree: capture", defaults.getString("capture"),
                Choices.combined(defaults.getString("capture_key"), defaults.getString("capture_press")));
        eq("defaults agree: record", defaults.getString("record"),
                Choices.combined(defaults.getString("record_key"), defaults.getString("record_press")));

        // Settings.sync: after the HMG-API page saves without the private values, they follow the lists again
        JSONObject page = new JSONObject("{\"capture_key\":\"radio\",\"capture_press\":\"short_r\",\"record_key\":\"mode\",\"record_press\":\"hold\",\"capture\":\"star\",\"record\":\"wheel_custom.hold\"}");
        check("sync changes stale private values", Settings.syncValues(page));
        eq("sync capture", "radio.short_r", page.getString("capture"));
        eq("sync record", "mode.hold", page.getString("record"));
        check("then nothing to do", !Settings.syncValues(page));
        JSONObject old = new JSONObject("{\"capture\":\"mute.double\",\"record\":\"star\"}");
        check("settings from before the split", Settings.syncValues(old));
        eq("old capture key", "mute", old.getString("capture_key"));
        eq("old capture press", "double", old.getString("capture_press"));
        eq("old record key", "wheel_custom", old.getString("record_key"));
        eq("old record kept", "star", old.getString("record"));
        check("the defaults are already in step", !Settings.syncValues(Settings.defaults()));

        // Settings: the app's defaults are settings.json's
        JSONObject appDefaults = Settings.defaults();
        for (java.util.Iterator<String> it = defaults.keys(); it.hasNext(); ) {
            String k = it.next();
            eq("default " + k, String.valueOf(defaults.get(k)), String.valueOf(appDefaults.opt(k)));
        }
        eq("same default keys", defaults.length(), appDefaults.length());

        // Issue QR: the URL's fields are the bug form's ids
        String form = T.read(root + "/.github/ISSUE_TEMPLATE/bug_report.yml");
        String url = new CarInfo("DEPEHEV.KOR.S5W_M.V039.001.260902", "1.1.0", "1.16.7", "0.1.0").issueUrl();
        check("url uses the bug form", url.contains("template=bug_report.yml"));
        for (String id : new String[] { "firmware", "hmgapi", "version" }) {
            check("form has id " + id, form.contains("id: " + id));
            check("url fills " + id, url.contains("&" + id + "="));
        }
        check("url is short enough for a QR", url.length() < 400);

        eq("clock", "02:15", FabView.clock(135000));
        eq("clock negative", "00:00", FabView.clock(-5));
    }
}
