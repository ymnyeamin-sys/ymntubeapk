/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import java.util.Comparator;
import java.util.Map;

public final class RecommendationHelper {
    private RecommendationHelper() {
    }

    public static String mostWatchedCategory(final Map<String, Long> categoryWatchCounts) {
        return categoryWatchCounts.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().trim().isEmpty()
                        && entry.getValue() != null)
                .max(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
