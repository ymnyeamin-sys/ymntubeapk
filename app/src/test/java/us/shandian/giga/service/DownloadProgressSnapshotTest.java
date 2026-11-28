package us.shandian.giga.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadProgressSnapshotTest {
    @Test
    public void calculatesKnownLengthProgressAndClampsInvalidValues() {
        assertEquals(40, DownloadProgressSnapshot.percent(40, 100, false));
        assertEquals(100, DownloadProgressSnapshot.percent(120, 100, false));
        assertEquals(0, DownloadProgressSnapshot.percent(-1, 100, false));
    }

    @Test
    public void reportsUnknownProgressWhenLengthIsUnavailable() {
        assertEquals(DownloadProgressSnapshot.UNKNOWN_PERCENT,
                DownloadProgressSnapshot.percent(0, 0, false));
        assertEquals(DownloadProgressSnapshot.UNKNOWN_PERCENT,
                DownloadProgressSnapshot.percent(200, 100, true));
    }
}
