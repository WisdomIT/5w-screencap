package fivew.diag;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Third-stage probe, run as root under app_process:
 *   CLASSPATH=5w_diag3.jar app_process /system/bin fivew.diag.Probe3 OUT_DIR [SECONDS]
 *
 * A. Capture path: SurfaceControl.screenshot + copyPixelsToBuffer + RGBA->I420, per size.
 * B. JPEG sequence speed (MJPEG option).
 * C. Encoder matrix: real screen frames, paced to the target fps, for a few seconds each:
 *    MPEG-4 (MP4), H.264 soft at Level 2 (MP4), VP8 (IVF), hardware AVC (MP4) if it can be made.
 *    An encoder error is caught at any point and reported instead of aborting.
 */
public final class Probe3 {
    private static PrintWriter rep;
    private static File out;
    private static long deadline;
    private static Method shot;

    static final class Cfg {
        final String codec, mime, container; final int w, h, fps, bitrate, level;
        Cfg(String codec, String mime, String container, int w, int h, int fps, int bitrate, int level) {
            this.codec = codec; this.mime = mime; this.container = container;
            this.w = w; this.h = h; this.fps = fps; this.bitrate = bitrate; this.level = level;
        }
        String tag() { return codec.replace('.', '_') + "_" + w + "x" + h + "_" + fps + "fps"; }
    }

    public static void main(String[] args) {
        out = new File(args[0]);
        out.mkdirs();
        try {
            rep = new PrintWriter(new FileWriter(new File(out, "probe3.txt")));
        } catch (Exception e) {
            System.err.println("cannot write report: " + e);
            System.exit(2);
        }
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 180;
        deadline = System.currentTimeMillis() + seconds * 1000L;
        log("# probe3 start");

        try {
            shot = Class.forName("android.view.SurfaceControl").getMethod("screenshot", int.class, int.class);
        } catch (Throwable t) { err("SurfaceControl", t); finish(); }

        try { pixelOrder(); } catch (Throwable t) { err("pixelOrder", t); }
        try { capturePath(); } catch (Throwable t) { err("capturePath", t); }
        try { jpegSequence(); } catch (Throwable t) { err("jpegSequence", t); }

        final int OMX_AVC_LEVEL2 = 0x20, AVC_BASELINE = 1;
        Cfg[] matrix = {
            new Cfg("OMX.google.mpeg4.encoder", "video/mp4v-es", "mp4", 1024, 384, 15, 2000000, 0),
            new Cfg("OMX.google.mpeg4.encoder", "video/mp4v-es", "mp4", 1280, 480, 9, 2000000, 0),
            new Cfg("OMX.google.mpeg4.encoder", "video/mp4v-es", "mp4", 1920, 720, 4, 2000000, 0),
            new Cfg("OMX.google.h264.encoder", "video/avc", "mp4", 512, 192, 15, 1000000, OMX_AVC_LEVEL2),
            new Cfg("OMX.google.vp8.encoder", "video/x-vnd.on2.vp8", "ivf", 1280, 480, 15, 3000000, 0),
            new Cfg("OMX.google.vp8.encoder", "video/x-vnd.on2.vp8", "ivf", 1920, 720, 10, 4000000, 0),
            new Cfg("OMX.TCC.ENC.avc", "video/avc", "mp4", 1280, 480, 15, 4000000, 0),
        };
        for (Cfg c : matrix) {
            if (System.currentTimeMillis() > deadline - 12000) { log("skip " + c.tag() + ": out of time"); continue; }
            try { encode(c, 7000); } catch (Throwable t) { err("encode " + c.tag(), t); }
            sleepMs(500);   // let mediaserver settle between codecs
        }
        finish();
    }

    private static void finish() {
        log("done");
        rep.close();
        System.exit(0);
    }

    // ------------------------------------------------------------------ A. capture path

    private static Bitmap grab(int w, int h) throws Exception {
        return (Bitmap) shot.invoke(null, w, h);
    }

    /** Which byte order copyPixelsToBuffer gives, compared with getPixel (ARGB int). */
    private static void pixelOrder() throws Exception {
        section("A0. pixel byte order");
        Bitmap b = grab(1920, 720);
        ByteBuffer buf = ByteBuffer.allocate(1920 * 720 * 4);
        b.copyPixelsToBuffer(buf);
        int[][] pts = { { 100, 100 }, { 960, 360 }, { 1500, 600 }, { 300, 650 } };
        for (int[] p : pts) {
            int argb = b.getPixel(p[0], p[1]);
            int o = (p[1] * 1920 + p[0]) * 4;
            log(String.format("(%d,%d) getPixel=%08x  bytes=%02x %02x %02x %02x", p[0], p[1], argb,
                    buf.get(o) & 0xff, buf.get(o + 1) & 0xff, buf.get(o + 2) & 0xff, buf.get(o + 3) & 0xff));
        }
        b.recycle();
    }

    private static void capturePath() throws Exception {
        section("A. capture path (avg of 10)");
        int[][] sizes = { { 1920, 720 }, { 1280, 480 }, { 1024, 384 }, { 512, 192 } };
        for (int[] s : sizes) {
            int w = s[0], h = s[1];
            ByteBuffer buf = ByteBuffer.allocate(w * h * 4);
            byte[] yuv = new byte[w * h * 3 / 2];
            long tShot = 0, tCopy = 0, tConv = 0;
            int n = 10;
            for (int i = 0; i < n; i++) {
                long a = System.nanoTime();
                Bitmap b = grab(w, h);
                long m = System.nanoTime();
                buf.clear();
                b.copyPixelsToBuffer(buf);
                b.recycle();
                long c = System.nanoTime();
                rgbaToI420(buf.array(), yuv, w, h);
                long e = System.nanoTime();
                tShot += m - a; tCopy += c - m; tConv += e - c;
            }
            log(String.format("%dx%d shot=%.1fms copyPixelsToBuffer=%.1fms rgbaToI420=%.1fms total=%.1fms (max %.1f fps)",
                    w, h, tShot / 1e6 / n, tCopy / 1e6 / n, tConv / 1e6 / n,
                    (tShot + tCopy + tConv) / 1e6 / n, 1000.0 / ((tShot + tCopy + tConv) / 1e6 / n)));
        }
    }

    // ------------------------------------------------------------------ B. JPEG sequence

    private static void jpegSequence() throws Exception {
        section("B. JPEG sequence (avg of 8)");
        int[][] sizes = { { 960, 360 }, { 1280, 480 }, { 1920, 720 } };
        int[] qualities = { 70, 85 };
        for (int[] s : sizes) {
            for (int q : qualities) {
                long tShot = 0, tJpg = 0, bytes = 0;
                int n = 8;
                ByteArrayOutputStream bo = new ByteArrayOutputStream(512 * 1024);
                for (int i = 0; i < n; i++) {
                    long a = System.nanoTime();
                    Bitmap b = grab(s[0], s[1]);
                    long m = System.nanoTime();
                    bo.reset();
                    b.compress(Bitmap.CompressFormat.JPEG, q, bo);
                    b.recycle();
                    long e = System.nanoTime();
                    tShot += m - a; tJpg += e - m; bytes += bo.size();
                }
                log(String.format("%dx%d q%d shot=%.1fms jpeg=%.1fms size=%dKB (max %.1f fps, %.1f MB/min)",
                        s[0], s[1], q, tShot / 1e6 / n, tJpg / 1e6 / n, bytes / n / 1024,
                        1000.0 / ((tShot + tJpg) / 1e6 / n),
                        (bytes / (double) n) * (1000.0 / ((tShot + tJpg) / 1e6 / n)) * 60 / 1e6));
                if (q == 85) {
                    FileOutputStream f = new FileOutputStream(new File(out, "jpeg_" + s[0] + "x" + s[1] + ".jpg"));
                    bo.writeTo(f);
                    f.close();
                }
            }
        }
    }

    // ------------------------------------------------------------------ C. encoders

    private static void encode(Cfg c, long durationMs) throws Exception {
        section("C. " + c.tag() + " (" + c.container + ")");
        MediaCodec codec;
        try {
            codec = MediaCodec.createByCodecName(c.codec);
        } catch (Throwable t) {
            log("create FAIL: " + t);
            return;
        }
        Sink sink = null;
        int frames = 0, dropped = 0, late = 0;
        long tShot = 0, tCopy = 0, tConv = 0, tEnc = 0;
        long start = 0;
        String stage = "configure";
        try {
            MediaFormat fmt = MediaFormat.createVideoFormat(c.mime, c.w, c.h);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, c.bitrate);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, c.fps);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, 19);       // YUV420Planar (I420)
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            if (c.level != 0) {
                fmt.setInteger(MediaFormat.KEY_PROFILE, 1);         // AVC Baseline
                fmt.setInteger(MediaFormat.KEY_LEVEL, c.level);
            }
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            stage = "start";
            codec.start();
            sleepMs(300);                                           // async init errors land here
            stage = "getBuffers";
            ByteBuffer[] inBufs = codec.getInputBuffers();
            String path = new File(out, "enc_" + c.tag() + "." + c.container).getPath();
            sink = "ivf".equals(c.container) ? new IvfSink(path, c.w, c.h) : new Mp4Sink(path);
            Drain drain = new Drain(codec, sink);
            log("started; input buffer capacity=" + inBufs[0].capacity() + " need=" + (c.w * c.h * 3 / 2));

            ByteBuffer rgba = ByteBuffer.allocate(c.w * c.h * 4);
            byte[] yuv = new byte[c.w * c.h * 3 / 2];
            long frameNs = 1000000000L / c.fps;
            start = System.nanoTime();
            long next = start;
            stage = "encode";
            while (System.nanoTime() - start < durationMs * 1000000L) {
                long now = System.nanoTime();
                if (now < next) { sleepMs((next - now) / 1000000); continue; }
                if (now - next > frameNs) late++;
                next += frameNs;
                if (next < now) next = now;

                long a = System.nanoTime();
                Bitmap b = grab(c.w, c.h);
                long m = System.nanoTime();
                rgba.clear();
                b.copyPixelsToBuffer(rgba);
                b.recycle();
                long cc = System.nanoTime();
                rgbaToI420(rgba.array(), yuv, c.w, c.h);
                long d = System.nanoTime();
                int idx = codec.dequeueInputBuffer(20000);
                if (idx >= 0) {
                    ByteBuffer in = inBufs[idx];
                    in.clear();
                    in.put(yuv, 0, Math.min(yuv.length, in.capacity()));
                    codec.queueInputBuffer(idx, 0, Math.min(yuv.length, in.capacity()), (a - start) / 1000, 0);
                    frames++;
                } else {
                    dropped++;
                }
                drain.run(false);
                long e = System.nanoTime();
                tShot += m - a; tCopy += cc - m; tConv += d - cc; tEnc += e - d;
            }
            stage = "eos";
            int idx = codec.dequeueInputBuffer(500000);
            if (idx >= 0) codec.queueInputBuffer(idx, 0, 0, (System.nanoTime() - start) / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drain.run(true);
            double secs = (System.nanoTime() - start) / 1e9;
            int n = Math.max(1, frames + dropped);
            log(String.format("OK in=%d dropped=%d late=%d secs=%.1f in_fps=%.2f out_frames=%d out_kbps=%.0f",
                    frames, dropped, late, secs, frames / secs, drain.outFrames, drain.bytes * 8 / secs / 1000));
            log(String.format("avg ms: shot=%.1f copy=%.1f toI420=%.1f queue+drain=%.1f  busy=%.1f of %.1f per frame",
                    tShot / 1e6 / n, tCopy / 1e6 / n, tConv / 1e6 / n, tEnc / 1e6 / n,
                    (tShot + tCopy + tConv + tEnc) / 1e6 / n, 1000.0 / c.fps));
        } catch (Throwable t) {
            log("FAIL at " + stage + " after in=" + frames + ": " + t);
        } finally {
            try { codec.stop(); } catch (Throwable ignored) { }
            try { codec.release(); } catch (Throwable ignored) { }
            if (sink != null) {
                try { sink.close(); } catch (Throwable t) { log("sink close: " + t); }
                log("file size=" + new File(sink.path()).length());
            }
        }
    }

    /** Encoder output -> sink. */
    private static final class Drain {
        final MediaCodec codec; final Sink sink;
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] outBufs;
        int outFrames; long bytes;

        Drain(MediaCodec codec, Sink sink) { this.codec = codec; this.sink = sink; outBufs = codec.getOutputBuffers(); }

        void run(boolean eos) throws Exception {
            long until = System.currentTimeMillis() + 3000;
            while (true) {
                int idx = codec.dequeueOutputBuffer(info, eos ? 20000 : 0);
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!eos || System.currentTimeMillis() > until) return;
                } else if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outBufs = codec.getOutputBuffers();
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sink.format(codec.getOutputFormat());
                } else if (idx >= 0) {
                    ByteBuffer b = outBufs[idx];
                    boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (info.size > 0) {
                        b.position(info.offset);
                        b.limit(info.offset + info.size);
                        if (config) {
                            sink.config(b, info);
                        } else {
                            sink.sample(b, info);
                            outFrames++;
                            bytes += info.size;
                        }
                    }
                    codec.releaseOutputBuffer(idx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
                }
            }
        }
    }

    interface Sink {
        void format(MediaFormat f) throws Exception;
        void config(ByteBuffer b, MediaCodec.BufferInfo i) throws Exception;
        void sample(ByteBuffer b, MediaCodec.BufferInfo i) throws Exception;
        void close() throws Exception;
        String path();
    }

    /** MP4 through MediaMuxer (H.264 / MPEG-4 part 2). Codec config comes in via the format. */
    private static final class Mp4Sink implements Sink {
        final String path; final MediaMuxer muxer; int track = -1; boolean started;
        Mp4Sink(String path) throws Exception { this.path = path; muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); }
        public void format(MediaFormat f) { track = muxer.addTrack(f); muxer.start(); started = true; }
        public void config(ByteBuffer b, MediaCodec.BufferInfo i) { }
        public void sample(ByteBuffer b, MediaCodec.BufferInfo i) { if (started) muxer.writeSampleData(track, b, i); }
        public void close() { if (started) muxer.stop(); muxer.release(); }
        public String path() { return path; }
    }

    /** VP8 in IVF (32-byte file header, 12-byte frame headers, timebase 1/1000000). */
    static final class IvfSink implements Sink {
        final String path; final RandomAccessFile f; int count;
        IvfSink(String path, int w, int h) throws Exception {
            this.path = path;
            f = new RandomAccessFile(path, "rw");
            f.setLength(0);
            byte[] hdr = new byte[32];
            hdr[0] = 'D'; hdr[1] = 'K'; hdr[2] = 'I'; hdr[3] = 'F';
            le16(hdr, 4, 0); le16(hdr, 6, 32);
            hdr[8] = 'V'; hdr[9] = 'P'; hdr[10] = '8'; hdr[11] = '0';
            le16(hdr, 12, w); le16(hdr, 14, h);
            le32(hdr, 16, 1000000); le32(hdr, 20, 1); le32(hdr, 24, 0);
            f.write(hdr);
        }
        public void format(MediaFormat fm) { }
        public void config(ByteBuffer b, MediaCodec.BufferInfo i) { }
        public void sample(ByteBuffer b, MediaCodec.BufferInfo i) throws Exception {
            byte[] data = new byte[i.size];
            b.get(data);
            byte[] fh = new byte[12];
            le32(fh, 0, i.size);
            le32(fh, 4, (int) i.presentationTimeUs);
            le32(fh, 8, (int) (i.presentationTimeUs >>> 32));
            f.write(fh);
            f.write(data);
            count++;
        }
        public void close() throws Exception {
            byte[] n = new byte[4];
            le32(n, 0, count);
            f.seek(24);
            f.write(n);
            f.close();
        }
        public String path() { return path; }
        static void le16(byte[] a, int o, int v) { a[o] = (byte) v; a[o + 1] = (byte) (v >> 8); }
        static void le32(byte[] a, int o, int v) { a[o] = (byte) v; a[o + 1] = (byte) (v >> 8); a[o + 2] = (byte) (v >> 16); a[o + 3] = (byte) (v >> 24); }
    }

    /** RGBA bytes (Android ARGB_8888 in memory) -> BT.601 limited-range I420. */
    static void rgbaToI420(byte[] rgba, byte[] yuv, int w, int h) {
        int frame = w * h;
        int uIdx = frame, vIdx = frame + frame / 4;
        int p = 0, q = 0;
        for (int j = 0; j < h; j++) {
            boolean evenRow = (j & 1) == 0;
            for (int i = 0; i < w; i++, p++, q += 4) {
                int r = rgba[q] & 0xff, g = rgba[q + 1] & 0xff, b = rgba[q + 2] & 0xff;
                yuv[p] = (byte) (((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                if (evenRow && (i & 1) == 0) {
                    yuv[uIdx++] = (byte) (((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                    yuv[vIdx++] = (byte) (((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
                }
            }
        }
    }

    // ------------------------------------------------------------------ util

    private static void sleepMs(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
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
