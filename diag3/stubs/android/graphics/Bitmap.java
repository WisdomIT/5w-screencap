package android.graphics;
public final class Bitmap {
    public enum CompressFormat { JPEG, PNG, WEBP }
    public int getWidth() { throw new RuntimeException("stub"); }
    public int getHeight() { throw new RuntimeException("stub"); }
    public void getPixels(int[] p, int off, int stride, int x, int y, int w, int h) { throw new RuntimeException("stub"); }
    public boolean compress(CompressFormat f, int q, java.io.OutputStream s) { throw new RuntimeException("stub"); }
    public void copyPixelsToBuffer(java.nio.Buffer dst) { throw new RuntimeException("stub"); }
    public int getPixel(int x, int y) { throw new RuntimeException("stub"); }
    public void recycle() { throw new RuntimeException("stub"); }
}
