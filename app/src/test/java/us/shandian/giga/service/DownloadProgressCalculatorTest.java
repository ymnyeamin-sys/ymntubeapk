package us.shandian.giga.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadProgressCalculatorTest {
    @Test
    public void returnsNoActiveProgressForEmptyMissionSet() {
        assertEquals(DownloadProgressCalculator.NO_ACTIVE_DOWNLOADS,
                DownloadProgressCalculator.calculatePercent(new long[0], new long[0]));
    }

    @Test
    public void averagesConcurrentMissionProgress() {
        assertEquals(50, DownloadProgressCalculator.calculatePercent(
                new long[]{25, 75}, new long[]{100, 100}));
    }

    @Test
    public void unknownLengthCountsAsAnActiveTaskWithZeroProgress() {
        assertEquals(25, DownloadProgressCalculator.calculatePercent(
                new long[]{50, 20}, new long[]{100, 0}));
    }

    @Test
    public void clampsProgressAndRoundsToAnIntegerPercentage() {
        assertEquals(100, DownloadProgressCalculator.calculatePercent(
                new long[]{120}, new long[]{100}));
        assertEquals(33, DownloadProgressCalculator.calculatePercent(
                new long[]{1}, new long[]{3}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedMissionSnapshots() {
        DownloadProgressCalculator.calculatePercent(new long[]{1}, new long[0]);
    }
}
