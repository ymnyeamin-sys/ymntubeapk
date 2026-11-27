package us.shandian.giga.ui.adapter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MissionAdapterTest {
    @Test
    public void formatsVideoDurationForListBadges() {
        assertEquals("1:05", MissionFormatUtils.formatDuration(65_000L));
        assertEquals("1:02:03", MissionAdapter.formatDuration(3_723_000L));
    }

    @Test
    public void formatsDownloadAgeWithoutInventingMetadata() {
        final long day = 24L * 60L * 60L * 1000L;
        assertEquals("Today", MissionFormatUtils.formatDownloadAge(100L, 100L));
        assertEquals("Yesterday", MissionAdapter.formatDownloadAge(day, 2L * day));
        assertEquals("3 days ago", MissionAdapter.formatDownloadAge(2L * day, 5L * day));
    }
}
