package android.os;
public class Build {
    public static class VERSION {
        public static int SDK_INT; // not final, so javac does not inline it
    }
}
