package android.media;
public final class MediaCodecInfo {
    public String getName() { throw new RuntimeException("stub"); }
    public boolean isEncoder() { throw new RuntimeException("stub"); }
    public String[] getSupportedTypes() { throw new RuntimeException("stub"); }
    public CodecCapabilities getCapabilitiesForType(String t) { throw new RuntimeException("stub"); }
    public static final class CodecCapabilities {
        public static final int COLOR_FormatYUV420Planar = 19, COLOR_FormatYUV420SemiPlanar = 21;
        public int[] colorFormats;
    }
}
