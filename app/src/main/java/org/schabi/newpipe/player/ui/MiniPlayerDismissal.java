package org.schabi.newpipe.player.ui;

import java.util.ArrayList;
import java.util.List;

public final class MiniPlayerDismissal {
    private MiniPlayerDismissal() {
    }

    private static boolean dismissed;
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    public static void dismiss() {
        updateDismissed(true);
    }

    public static void restoreForNewPlayback() {
        updateDismissed(false);
    }

    public static void addListener(final Runnable listener) {
        LISTENERS.add(listener);
        listener.run();
    }

    public static void removeListener(final Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static boolean isDismissed() {
        return dismissed;
    }

    public static boolean isVisible(final boolean playbackVisible,
                                    final boolean playerExpanded) {
        return !dismissed && playbackVisible && !playerExpanded;
    }

    private static void updateDismissed(final boolean newValue) {
        if (dismissed == newValue) {
            return;
        }

        dismissed = newValue;
        for (final Runnable listener : new ArrayList<>(LISTENERS)) {
            listener.run();
        }
    }
}
