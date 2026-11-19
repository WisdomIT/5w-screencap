package dev.wisdomit.screencap.daemon;

import java.io.File;
import java.util.Calendar;

/**
 * File names. The head unit reads 2015 until it gets the time from GPS or the network;
 * names from that clock would sort wrong, so before then a boot number and a counter
 * are used instead (boot0042_003).
 */
final class Names {
    private Names() {}

    /** the clock is set when it reads a year after this */
    static final int CLOCK_SANE_AFTER = 2020;

    static String stamp(long millis, int boot, int counter) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        if (c.get(Calendar.YEAR) <= CLOCK_SANE_AFTER) return String.format("boot%04d_%03d", boot, counter);
        return String.format("%04d%02d%02d_%02d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
    }

    /** dir/base.ext, or dir/base-2.ext, -3 ... if taken (two captures in one second) */
    static File unique(File dir, String base, String ext) {
        File f = new File(dir, base + ext);
        for (int i = 2; f.exists(); i++) f = new File(dir, base + "-" + i + ext);
        return f;
    }

    static String segment(String base, int n) {
        return String.format("%s_%03d", base, n);
    }
}
