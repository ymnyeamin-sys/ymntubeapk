package org.schabi.newpipe.player.ui;

public final class MiniPlayerLayoutPolicy {
    private MiniPlayerLayoutPolicy() {
    }

    public static int getBottomMargin(final int parentHeight, final int navigationTop) {
        return Math.max(0, parentHeight - navigationTop);
    }
}
