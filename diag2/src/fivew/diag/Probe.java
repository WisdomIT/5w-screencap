package fivew.diag;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Second-stage feasibility probe, run as root under app_process:
 *   CLASSPATH=probe.jar app_process /system/bin fivew.diag.Probe OUT_DIR [OMX_NAMES_FILE] [SECONDS]
 *
 * 1. Lists every codec MediaCodecList knows, with encoder colour formats.
 * 2. Tries to instantiate each AVC encoder candidate by name (list + names found in the libraries).
 * 3. Measures in-process screenshots (SurfaceControl.screenshot) and PNG/JPEG compression.
 * 4. For each working encoder: screenshot -> I420/NV12 -> encoder -> MP4, a few seconds per size,
 *    and reports the frame rate reached and where the time goes.
 */
public final class Probe {
    private static PrintWriter rep;
    private static File out;
    private static long deadline;
    private static Method shot;

    public static void main(String[] args) {
        out = new File(args[0]);
        out.mkdirs();
        try {
            rep = new PrintWriter(new FileWriter(new File(out, "probe.txt")));
        } catch (Exception e) {
            System.err.println("cannot write report: " + e);
            System.exit(2);
        }
        int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 150;
        deadline = System.currentTimeMillis() + seconds * 1000L;
        log("# probe start sdk=" + android.os.Build.VERSION.SDK_INT);

        List<String> encoders = new ArrayList<String>();
        try { encoders = codecList(); } catch (Throwable t) { err("codecList", t); }
        try { encoders = candidates(encoders, args.length > 1 ? args[1] : null); } catch (Throwable t) { err("candidates", t); }
        try { screenshots(); } catch (Throwable t) { err("screenshots", t); }

        int[][] sizes = { { 1280, 480 }, { 640, 240 }, { 1920, 720 } };
        if (shot != null) {
            for (String name : encoders) {
                for (int[] s : sizes) {
                    if (System.currentTimeMillis() > deadline - 10000) { log("skip " + name + " " + s[0] + "x" + s[1] + ": out of time"); continue; }
                    try { encode(name, s[0], s[1], 6000); } catch (Throwable t) { err("encode " + name + " " + s[0] + "x" + s[1], t); }
                }
            }
        }
        log("done");
        rep.close();
        System.exit(0);
    }

    // ------------------------------------------------------------------ 1. codec list

    private static List<String> codecList() {
        section("1. MediaCodecList");
        List<String> avcEncoders = new ArrayList<String>();
        int n = MediaCodecList.getCodecCount();
        for (int i = 0; i < n; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            StringBuilder sb = new StringBuilder();
            sb.append(info.isEncoder() ? "ENC " : "dec ").append(info.getName()).append(' ');
            for (String type : info.getSupportedTypes()) {
                sb.append(type);
                if (info.isEncoder()) {
                    try {
                        int[] cf = info.getCapabilitiesForType(type).colorFormats;
                        sb.append(" colors=").append(join(cf));
                    } catch (Throwable t) {
                        sb.append(" caps-error=").append(t.getClass().getSimpleName());
                    }
                    if ("video/avc".equals(type)) avcEncoders.add(info.getName());
                }
                sb.append(' ');
            }
            log(sb.toString());
        }
        return avcEncoders;
    }

    // ------------------------------------------------------------------ 2. encoder candidates

    private static List<String> candidates(List<String> fromList, String namesFile) throws Exception {
        section("2. AVC encoder candidates");
        Set<String> names = new LinkedHashSet<String>(fromList);
        if (namesFile != null && new File(namesFile).exists()) {
            BufferedReader r = new BufferedReader(new FileReader(namesFile));
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int colon = line.lastIndexOf(':');          // "lib.so:OMX.TCC.xxx"
                if (colon >= 0) line = line.substring(colon + 1);
                String low = line.toLowerCase();
                if (line.startsWith("OMX.") && low.contains("enc") && (low.contains("avc") || low.contains("264") || low.contains("video")))
                    names.add(line);
            }
            r.close();
        }
        String[] guesses = { "OMX.TCC.ENC.avc", "OMX.TCC.avcenc", "OMX.TCC.h264enc", "OMX.TCC.videoenc" };
        for (String g : guesses) names.add(g);

        List<String> ok = new ArrayList<String>();
        for (String name : names) {
            try {
                MediaCodec c = MediaCodec.createByCodecName(name);
                c.release();
                log("create OK   " + name);
                ok.add(name);
            } catch (Throwable t) {
                log("create FAIL " + name + " : " + t);
            }
        }
        // hardware first, the software encoder last
        List<String> ordered = new ArrayList<String>();
        for (String s : ok) if (!s.startsWith("OMX.google.")) ordered.add(s);
        for (String s : ok) if (s.startsWith("OMX.google.")) ordered.add(s);
        log("encode order: " + ordered);
        return ordered;
    }

    // ------------------------------------------------------------------ 3. screenshots

    private static Bitmap grab(int w, int h) throws Exception {
        return (Bitmap) shot.invoke(null, w, h);
    }

    private static void screenshots() throws Exception {
        section("3. SurfaceControl.screenshot");
        Class<?> sc = Class.forName("android.view.SurfaceControl");
        shot = sc.getMethod("screenshot", int.class, int.class);
        Bitmap b = grab(1920, 720);
        if (b == null) { log("screenshot returned null"); shot = null; return; }
        log("first shot " + b.getWidth() + "x" + b.getHeight());
        b.recycle();

        int[][] sizes = { { 1920, 720 }, { 1280, 480 }, { 640, 240 } };
        for (int[] s : sizes) {
            int[] px = new int[s[0] * s[1]];
            long tShot = 0, tPix = 0;
            int n = 10;
            for (int i = 0; i < n; i++) {
                long a = System.nanoTime();
                Bitmap x = grab(s[0], s[1]);
                long m = System.nanoTime();
                x.getPixels(px, 0, s[0], 0, 0, s[0], s[1]);
                long e = System.nanoTime();
                x.recycle();
                tShot += m - a; tPix += e - m;
            }
            log(s[0] + "x" + s[1] + " shot avg=" + (tShot / n / 1000) + "us getPixels avg=" + (tPix / n / 1000) + "us");
        }

        Bitmap full = grab(1920, 720);
        long a = System.nanoTime();
        FileOutputStream f = new FileOutputStream(new File(out, "shot_java.png"));
        full.compress(Bitmap.CompressFormat.PNG, 100, f);
        f.close();
        long m = System.nanoTime();
        f = new FileOutputStream(new File(out, "shot_java.jpg"));
        full.compress(Bitmap.CompressFormat.JPEG, 92, f);
        f.close();
        long e = System.nanoTime();
        full.recycle();
        log("png compress+write=" + ((m - a) / 1000000) + "ms  jpeg(92) compress+write=" + ((e - m) / 1000000) + "ms");
    }

    // ------------------------------------------------------------------ 4. encode

    private static void encode(String name, int w, int h, long durationMs) throws Exception {
        section("4. encode " + name + " " + w + "x" + h);
        int[] formats = { MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                          MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar };
        MediaCodec codec = null;
        int cf = 0;
        for (int f : formats) {
            MediaCodec c = null;
            try {
                c = MediaCodec.createByCodecName(name);
                MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", w, h);
                fmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(1000000, w * h * 3));
                fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
                fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, f);
                fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                c.start();
                codec = c;
                cf = f;
                break;
            } catch (Throwable t) {
                log("configure color=" + f + " FAIL: " + t);
                if (c != null) try { c.release(); } catch (Throwable ignored) { }
            }
        }
        if (codec == null) { log("no colour format worked"); return; }
        log("configured color=" + cf);

        String file = new File(out, "enc_" + name.replace('.', '_') + "_" + w + "x" + h + ".mp4").getPath();
        Enc st = new Enc(codec, new MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4));
        int[] px = new int[w * h];
        byte[] yuv = new byte[w * h * 3 / 2];
        long tShot = 0, tPix = 0, tConv = 0, tEnc = 0;
        int frames = 0, dropped = 0;
        long start = System.nanoTime();
        try {
            while ((System.nanoTime() - start) / 1000000 < durationMs) {
                long a = System.nanoTime();
                Bitmap b = grab(w, h);
                long bb = System.nanoTime();
                b.getPixels(px, 0, w, 0, 0, w, h);
                b.recycle();
                long c = System.nanoTime();
                toYuv(px, yuv, w, h, cf == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
                long d = System.nanoTime();
                int idx = codec.dequeueInputBuffer(50000);
                if (idx >= 0) {
                    ByteBuffer in = codec.getInputBuffers()[idx];
                    in.clear();
                    if (in.capacity() < yuv.length) {
                        log("input buffer too small: " + in.capacity() + " < " + yuv.length);
                        codec.queueInputBuffer(idx, 0, 0, 0, 0);
                        break;
                    }
                    in.put(yuv);
                    codec.queueInputBuffer(idx, 0, yuv.length, (a - start) / 1000, 0);
                    frames++;
                } else {
                    dropped++;
                }
                st.drain(false);
                long e = System.nanoTime();
                tShot += bb - a; tPix += c - bb; tConv += d - c; tEnc += e - d;
            }
            int idx = codec.dequeueInputBuffer(500000);
            if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, (System.nanoTime() - start) / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            st.drain(true);
        } finally {
            st.close();
        }
        double secs = (System.nanoTime() - start) / 1e9;
        int n = Math.max(1, frames + dropped);
        log(String.format("frames=%d dropped=%d secs=%.1f fps=%.2f out_frames=%d out_bytes=%d", frames, dropped, secs, frames / secs, st.outFrames, st.bytes));
        log(String.format("avg ms: shot=%.1f getPixels=%.1f toYuv=%.1f encode+mux=%.1f",
                tShot / 1e6 / n, tPix / 1e6 / n, tConv / 1e6 / n, tEnc / 1e6 / n));
        log("file " + file + " size=" + new File(file).length());
    }

    /** Encoder output -> MP4 muxer. */
    private static final class Enc {
        final MediaCodec codec;
        final MediaMuxer muxer;
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] outBufs;
        int track = -1;
        boolean started;
        int outFrames;
        long bytes;

        Enc(MediaCodec codec, MediaMuxer muxer) {
            this.codec = codec;
            this.muxer = muxer;
            outBufs = codec.getOutputBuffers();
        }

        void drain(boolean eos) {
            long until = System.currentTimeMillis() + 3000;
            while (true) {
                int idx = codec.dequeueOutputBuffer(info, eos ? 20000 : 0);
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!eos || System.currentTimeMillis() > until) return;
                } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outBufs = codec.getOutputBuffers();
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    started = true;
                } else if (idx >= 0) {
                    ByteBuffer b = outBufs[idx];
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && started) {
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        muxer.writeSampleData(track, b, info);
                        bytes += info.size;
                        outFrames++;
                    }
                    codec.releaseOutputBuffer(idx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
                }
            }
        }

        void close() {
            try { codec.stop(); } catch (Throwable t) { err("codec.stop", t); }
            try { codec.release(); } catch (Throwable t) { err("codec.release", t); }
            if (started) try { muxer.stop(); } catch (Throwable t) { err("muxer.stop", t); }
            try { muxer.release(); } catch (Throwable t) { err("muxer.release", t); }
        }
    }

    /** ARGB_8888 ints -> BT.601 limited-range I420 (planar) or NV12 (semi-planar). */
    static void toYuv(int[] argb, byte[] yuv, int w, int h, boolean semiPlanar) {
        int frame = w * h;
        int uIdx = frame, vIdx = frame + frame / 4;
        int p = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++, p++) {
                int c = argb[p];
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                yuv[p] = (byte) (((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                if ((j & 1) == 0 && (i & 1) == 0) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    if (semiPlanar) {
                        yuv[uIdx++] = (byte) u;
                        yuv[uIdx++] = (byte) v;
                    } else {
                        yuv[uIdx++] = (byte) u;
                        yuv[vIdx++] = (byte) v;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ util

    private static String join(int[] a) {
        if (a == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static void section(String s) { log(""); log("===== " + s); }

    private static void log(String s) {
        rep.println(s);
        rep.flush();
        System.out.println(s);
    }

    private static void err(String where, Throwable t) {
        log("ERROR " + where + ": " + t);
        t.printStackTrace(rep);
        rep.flush();
    }
}
