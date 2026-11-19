package dev.wisdomit.screencap;

/** Tiny check helper shared by the JVM tests (no JUnit: the build has no dependency manager). */
public final class T {
    public static int failed, passed;

    public static void check(String what, boolean ok) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL " + what); }
    }

    public static void eq(String what, Object want, Object got) {
        check(what + " (want " + want + ", got " + got + ")", want == null ? got == null : want.equals(got));
    }

    public static String read(String path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), "UTF-8");
    }
}
