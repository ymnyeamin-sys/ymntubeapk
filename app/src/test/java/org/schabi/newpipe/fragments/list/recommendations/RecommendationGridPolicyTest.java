package org.schabi.newpipe.fragments.list.recommendations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RecommendationGridPolicyTest {
    @Test
    public void phoneWidthUsesOneFullWidthColumn() {
        assertEquals(1, RecommendationGridPolicy.getColumnCount(352));
        assertEquals(1, RecommendationGridPolicy.getColumnCount(599));
    }

    @Test
    public void wideScreensUseAdaptiveColumns() {
        assertEquals(2, RecommendationGridPolicy.getColumnCount(600));
        assertEquals(3, RecommendationGridPolicy.getColumnCount(900));
    }

    @Test
    public void narrowMeasuredRecyclerViewUsesOneFullWidthColumn() {
        assertEquals(1, RecommendationGridPolicy.getColumnCountForWidth(352, 1));
        assertEquals(1, RecommendationGridPolicy.getColumnCountForWidth(704, 2));
    }
}
