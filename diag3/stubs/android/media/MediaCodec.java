package android.media;
import java.nio.ByteBuffer;
public final class MediaCodec {
    public static final int CONFIGURE_FLAG_ENCODE = 1, BUFFER_FLAG_CODEC_CONFIG = 2, BUFFER_FLAG_END_OF_STREAM = 4,
        INFO_TRY_AGAIN_LATER = -1, INFO_OUTPUT_FORMAT_CHANGED = -2, INFO_OUTPUT_BUFFERS_CHANGED = -3;
    public static final class BufferInfo { public int offset, size, flags; public long presentationTimeUs; }
    public static MediaCodec createByCodecName(String n) { throw new RuntimeException("stub"); }
    public void configure(MediaFormat f, android.view.Surface surface, MediaCrypto crypto, int flags) { throw new RuntimeException("stub"); }
    public void start() { throw new RuntimeException("stub"); }
    public void stop() { throw new RuntimeException("stub"); }
    public void release() { throw new RuntimeException("stub"); }
    public ByteBuffer[] getInputBuffers() { throw new RuntimeException("stub"); }
    public ByteBuffer[] getOutputBuffers() { throw new RuntimeException("stub"); }
    public int dequeueInputBuffer(long us) { throw new RuntimeException("stub"); }
    public void queueInputBuffer(int i, int off, int size, long pts, int flags) { throw new RuntimeException("stub"); }
    public int dequeueOutputBuffer(BufferInfo info, long us) { throw new RuntimeException("stub"); }
    public void releaseOutputBuffer(int i, boolean render) { throw new RuntimeException("stub"); }
    public MediaFormat getOutputFormat() { throw new RuntimeException("stub"); }
}
