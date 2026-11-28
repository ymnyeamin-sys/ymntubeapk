package us.shandian.giga.ui.adapter;

import java.util.Locale;

/**
 * Android-independent formatting helpers used by the download mission list.
 */
final class MissionFormatUtils {
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private MissionFormatUtils() {
    }

    static String formatDuration(final long milliseconds) {
        if (milliseconds <= 0L) return "";
        final long totalSeconds = milliseconds / 1000L;
        final long hours = totalSeconds / 3600L;
        final long minutes = (totalSeconds % 3600L) / 60L;
        final long seconds = totalSeconds % 60L;
        return hours > 0L
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    static String formatDownloadAge(final long timestamp, final long now) {
        final long ageMillis = Math.max(0L, now - timestamp);
        final long ageDays = ageMillis / MILLIS_PER_DAY;
        if (ageDays == 0L) return "Today";
        if (ageDays == 1L) return "Yesterday";
        if (ageDays < 7L) return ageDays + " days ago";
        if (ageDays < 30L) return (ageDays / 7L) + "w ago";
        if (ageDays < 365L) return (ageDays / 30L) + "mo ago";
        return (ageDays / 365L) + "y ago";
    }
}
