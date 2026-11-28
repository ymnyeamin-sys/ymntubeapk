package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.Test;
import org.schabi.newpipe.player.PlayerType;

public class VideoDetailFragmentTest {
    @Test
    public void backgroundPlaybackCollapsesExpandedPlayer() {
        assertTrue(VideoDetailFragment.shouldCollapseMiniPlayer(PlayerType.AUDIO,
                BottomSheetBehavior.STATE_EXPANDED, false));
    }

    @Test
    public void mainPlayerPreservesExpandedState() {
        assertFalse(VideoDetailFragment.shouldCollapseMiniPlayer(PlayerType.MAIN,
                BottomSheetBehavior.STATE_EXPANDED, false));
    }

    @Test
    public void hiddenPlayerIsRestoredForAnyPlayerType() {
        assertTrue(VideoDetailFragment.shouldCollapseMiniPlayer(PlayerType.MAIN,
                BottomSheetBehavior.STATE_HIDDEN, false));
    }

    @Test
    public void backgroundPlaybackDoesNotHideNewlySelectedVideo() {
        assertFalse(VideoDetailFragment.shouldCollapseMiniPlayer(PlayerType.AUDIO,
                BottomSheetBehavior.STATE_EXPANDED, true));
    }
}
