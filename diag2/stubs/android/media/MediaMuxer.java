package android.media;
import java.nio.ByteBuffer;
public final class MediaMuxer {
    public static final class OutputFormat { public static final int MUXER_OUTPUT_MPEG_4 = 0; }
    public MediaMuxer(String path, int fmt) throws java.io.IOException { throw new RuntimeException("stub"); }
    public int addTrack(MediaFormat f) { throw new RuntimeException("stub"); }
    public void start() { throw new RuntimeException("stub"); }
    public void writeSampleData(int t, ByteBuffer b, MediaCodec.BufferInfo i) { throw new RuntimeException("stub"); }
    public void stop() { throw new RuntimeException("stub"); }
    public void release() { throw new RuntimeException("stub"); }
}
