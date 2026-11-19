package dev.wisdomit.screencap;

public final class RunTests {
    public static void main(String[] a) throws Exception {
        dev.wisdomit.screencap.daemon.DaemonTests.run();
        AppTests.run(a[0]);
        System.out.println("java: " + T.passed + " passed, " + T.failed + " failed");
        System.exit(T.failed == 0 ? 0 : 1);
    }
}
