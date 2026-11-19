package dev.wisdomit.screencap.daemon;

/**
 * Recording presets. The software MPEG-4 encoder is fixed at Core Profile Level 2:
 * at most 23760 macroblocks a second and 2 Mbit/s, so the declared frame rate times
 * the frame's macroblocks has to stay under that (checked in tests).
 * Measured on a Niro (tcc897x): standard reaches about 12 fps, sharp 8, full 3.5.
 */
final class Quality {
    static final int MAX_MBPS = 23760, MAX_BITRATE = 2000000;

    final String name;
    final int width, height, fps, bitrate;

    private Quality(String name, int width, int height, int fps, int bitrate) {
        this.name = name; this.width = width; this.height = height; this.fps = fps; this.bitrate = bitrate;
    }

    static final Quality STANDARD = new Quality("standard", 1024, 384, 15, 1500000);
    static final Quality SHARP = new Quality("sharp", 1280, 480, 9, 2000000);
    static final Quality FULL = new Quality("full", 1920, 720, 4, 2000000);
    static final Quality[] ALL = { STANDARD, SHARP, FULL };

    static Quality of(String name) {
        for (Quality q : ALL) if (q.name.equals(name)) return q;
        return STANDARD;
    }

    int macroblocksPerSecond() {
        return ((width + 15) / 16) * ((height + 15) / 16) * fps;
    }
}
