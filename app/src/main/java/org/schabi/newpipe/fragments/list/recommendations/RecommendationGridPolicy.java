package org.schabi.newpipe.fragments.list.recommendations;

public final class RecommendationGridPolicy {
    private static final int MIN_COLUMN_WIDTH_DP = 300;

    private RecommendationGridPolicy() {
    }

    public static int getColumnCount(final int screenWidthDp) {
        return Math.max(1, screenWidthDp / MIN_COLUMN_WIDTH_DP);
    }

    public static int getColumnCountForWidth(final int widthPx, final float density) {
        if (widthPx <= 0 || density <= 0) {
            return 1;
        }
        return getColumnCount(Math.round(widthPx / density));
    }
}
