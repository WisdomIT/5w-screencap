package dev.wisdomit.screencap.daemon;

/** Pixel conversion for the encoder (plain Java, tested off the car). */
final class Yuv {
    private Yuv() {}

    /**
     * RGBA bytes (Android ARGB_8888 as it lies in memory: R, G, B, A) to BT.601
     * limited-range I420. Chroma comes from the top-left pixel of each 2x2 block.
     */
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
}
