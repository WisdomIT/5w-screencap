package dev.wisdomit.screencap;

/** Two presses within windowMs count once (plain Java, tested off the car). */
final class DoublePress {
    private final long windowMs;
    private long first = -1;

    DoublePress(long windowMs) { this.windowMs = windowMs; }

    /** a press at nowMs; true when it completes a double press */
    synchronized boolean press(long nowMs) {
        if (first >= 0 && nowMs - first <= windowMs) {
            first = -1;
            return true;
        }
        first = nowMs;
        return false;
    }
}
