package dev.wisdomit.screencap;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** Talks to screencapd (the mod's root service): one line out, one JSON line back. Blocks: not on the UI thread. */
final class Daemon {
    static final String SOCKET = "dev.wisdomit.screencap";

    private Daemon() {}

    static JSONObject call(String line, int timeoutMs) {
        LocalSocket s = new LocalSocket();
        try {
            s.connect(new LocalSocketAddress(SOCKET, LocalSocketAddress.Namespace.ABSTRACT));
            s.setSoTimeout(timeoutMs);
            OutputStream o = s.getOutputStream();
            o.write((line + "\n").getBytes("UTF-8"));
            o.flush();
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8")).readLine();
            return reply == null ? fail("no_reply") : new JSONObject(reply);
        } catch (Exception e) {
            return fail("no_service");
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    static JSONObject fail(String error) {
        try { return new JSONObject().put("ok", false).put("error", error); } catch (Exception e) { return new JSONObject(); }
    }
}
