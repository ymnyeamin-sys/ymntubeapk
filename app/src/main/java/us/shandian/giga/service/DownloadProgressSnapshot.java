// SPDX-License-Identifier: GPL-3.0-or-later
package us.shandian.giga.service;

import androidx.annotation.NonNull;

public final class DownloadProgressSnapshot {
    public static final int UNKNOWN_PERCENT = -1;

    @NonNull
    public final String fileName;
    public final int position;
    public final int total;
    public final int percent;

    public DownloadProgressSnapshot(@NonNull final String fileName,
                                    final int position,
                                    final int total,
                                    final int percent) {
        this.fileName = fileName;
        this.position = position;
        this.total = total;
        this.percent = percent;
    }

    public static int percent(final long done, final long length, final boolean unknownLength) {
        if (unknownLength || length <= 0) {
            return UNKNOWN_PERCENT;
        }
        return (int) Math.round(Math.max(0, Math.min(1, (double) done / length)) * 100);
    }
}
