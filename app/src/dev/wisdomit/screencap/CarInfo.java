package dev.wisdomit.screencap;

import java.lang.reflect.Method;
import java.net.URLEncoder;

/** This car's versions for the issue QR (.github/ISSUE_TEMPLATE/bug_report.yml field ids). Nothing personal. */
final class CarInfo {
    static final String REPO = "https://github.com/WisdomIT/5w-screencap";

    final String firmware, hmgapi, nemesis, version;

    CarInfo(String firmware, String hmgapi, String nemesis, String version) {
        this.firmware = firmware;
        this.hmgapi = hmgapi;
        this.nemesis = nemesis;
        this.version = version;
    }

    /** reads the props and asks hmgapid/nemesis for their versions (blocks) */
    static CarInfo read(String appVersion) {
        String fw = prop("ro.build.product2");
        if (fw.isEmpty()) fw = prop("ro.build.product");
        return new CarInfo(fw, first(Settings.run(Settings.HMGAPID, "version")),
                first(Settings.run("/system/xbin/nemesis", "version")), appVersion);
    }

    String issueUrl() {
        StringBuilder u = new StringBuilder(REPO).append("/issues/new?template=bug_report.yml");
        add(u, "firmware", firmware);
        add(u, "hmgapi", "HMG-API " + hmgapi + ", NEMESIS " + nemesis);
        add(u, "version", version);
        return u.toString();
    }

    private static void add(StringBuilder u, String k, String v) {
        try { u.append('&').append(k).append('=').append(URLEncoder.encode(v, "UTF-8")); } catch (Exception ignored) { }
    }

    static String prop(String name) {
        try {
            Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            Object v = get.invoke(null, name);
            return v == null ? "" : v.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String first(String s) {
        if (s == null) return "?";
        String t = s.trim();
        int nl = t.indexOf('\n');
        t = nl < 0 ? t : t.substring(0, nl);
        return t.isEmpty() ? "?" : t;
    }
}
