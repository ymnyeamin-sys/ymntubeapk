/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Map;

public class RecommendationHelperTest {
    @Test
    public void returnsMostWatchedCategory() {
        assertEquals("Gaming", RecommendationHelper.mostWatchedCategory(
                Map.of("Gaming", 12L, "Music", 4L)));
    }

    @Test
    public void returnsNullWhenNoUsableCategoriesExist() {
        assertNull(RecommendationHelper.mostWatchedCategory(Map.of("", 3L, " ", 2L)));
        assertNull(RecommendationHelper.mostWatchedCategory(Map.of()));
    }

    @Test
    public void breaksTiesDeterministically() {
        assertEquals("Music", RecommendationHelper.mostWatchedCategory(
                Map.of("Gaming", 5L, "Music", 5L)));
    }
}
