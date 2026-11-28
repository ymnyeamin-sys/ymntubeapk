// SPDX-License-Identifier: GPL-3.0-or-later
package us.shandian.giga.service;

public final class DownloadProgressCalculator {
    public static final int NO_ACTIVE_DOWNLOADS = -1;

    private DownloadProgressCalculator() {
    }

    /**
     * Returns the average progress of the active missions, or {@link #NO_ACTIVE_DOWNLOADS}.
     * Missions with unknown lengths contribute zero until their size becomes available.
     */
    public static int calculatePercent(final long[] done, final long[] lengths) {
        if (done == null || lengths == null || done.length != lengths.length) {
            throw new IllegalArgumentException("Progress and length arrays must have equal sizes");
        }
        if (done.length == 0) {
            return NO_ACTIVE_DOWNLOADS;
        }

        double progress = 0;
        for (int i = 0; i < done.length; i++) {
            if (lengths[i] > 0) {
                progress += Math.max(0, Math.min(1, (double) done[i] / lengths[i]));
            }
        }
        return (int) Math.round(progress * 100 / done.length);
    }
}
