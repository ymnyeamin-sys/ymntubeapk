package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class MiniPlayerDismissalTest {
    @After
    public void resetDismissal() {
        MiniPlayerDismissal.restoreForNewPlayback();
    }

    @Test
    public void explicitCloseRemainsDismissedForNavigation() {
        MiniPlayerDismissal.dismiss();

        assertTrue(MiniPlayerDismissal.isDismissed());
    }

    @Test
    public void dismissedPlaybackDoesNotShowMiniPlayerAgain() {
        MiniPlayerDismissal.dismiss();

        assertFalse(MiniPlayerDismissal.isVisible(true, false));
    }

    @Test
    public void newPlaybackRestoresMiniPlayerRemounting() {
        MiniPlayerDismissal.dismiss();
        MiniPlayerDismissal.restoreForNewPlayback();

        assertFalse(MiniPlayerDismissal.isDismissed());
        assertTrue(MiniPlayerDismissal.isVisible(true, false));
    }
}
