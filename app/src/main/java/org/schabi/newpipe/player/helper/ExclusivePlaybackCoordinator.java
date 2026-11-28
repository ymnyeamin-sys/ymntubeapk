package org.schabi.newpipe.player.helper;

import androidx.annotation.Nullable;

public final class ExclusivePlaybackCoordinator {
    private ExclusivePlaybackCoordinator() {
    }

    @Nullable
    private static Object activeOwner;

    @Nullable
    private static Runnable activeStopper;

    public static synchronized void claim(final Object owner, final Runnable stopActivePlayback) {
        stopActive();
        activeOwner = owner;
        activeStopper = stopActivePlayback;
    }

    public static synchronized void stopActive() {
        final Runnable stopper = activeStopper;
        activeOwner = null;
        activeStopper = null;
        if (stopper != null) {
            stopper.run();
        }
    }

    public static synchronized void release(final Object owner) {
        if (activeOwner == owner) {
            activeOwner = null;
            activeStopper = null;
        }
    }
}
