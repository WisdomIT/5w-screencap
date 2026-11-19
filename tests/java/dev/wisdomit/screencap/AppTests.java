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

        // Choices: every list pick maps to a value and back
        JSONObject settings = new JSONObject(T.read(root + "/mod/5w-screencap/settings.json"));
        JSONArray all = settings.getJSONArray("settings");
        Set<String> values = new HashSet<String>();
        JSONObject defaults = new JSONObject();
        for (int i = 0; i < all.length(); i++) {
            JSONObject s = all.getJSONObject(i);
            defaults.put(s.getString("key"), s.get("default"));
            if (!s.getString("key").equals("capture")) continue;
            JSONArray o = s.getJSONArray("options");
            for (int k = 0; k < o.length(); k++) values.add(o.getJSONObject(k).getString("value"));
        }
        Set<String> made = new HashSet<String>();
        made.add(Choices.value(new Choices.Pick(-1, 0, true)));
        for (int key = 0; key < Choices.KEYS.length; key++) {
            for (int press = 0; press < Choices.pressesFor(key).length; press++) {
                for (boolean rep : new boolean[] { true, false }) {
                    String v = Choices.value(new Choices.Pick(key, press, rep));
                    made.add(v);
                    check("settings.json offers " + v, values.contains(v));
                    eq("round trip " + v, v, Choices.value(Choices.pick(v)));
                }
            }
        }
        eq("the Launch screen can reach every settings.json choice", values, made);
        eq("star pick", 0, Choices.pick("star").key);
        eq("off pick", -1, Choices.pick("off").key);
        eq("unknown key is off", -1, Choices.pick("nope.short").key);
        check("same key, both short: clash", Choices.clash("radio.short_r", "radio.double") != null);
        check("same key, both hold: clash", Choices.clash("mode.hold", "mode.hold") != null);
        check("same key, short and hold: fine", Choices.clash("radio.short_r", "radio.hold") == null);
        check("both on the ★ menu: fine", Choices.clash("star", "star") == null);

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
