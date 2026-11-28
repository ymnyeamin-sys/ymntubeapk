package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.session.PlaybackStateCompat;

import org.junit.Test;

public class MiniPlayerStateTest {
    @Test
    public void activeTrackIsVisibleAndReflectsPlayPauseState() {
        final MiniPlayerState playing = MiniPlayerState.from(
                true, PlaybackStateCompat.STATE_PLAYING);
        final MiniPlayerState paused = MiniPlayerState.from(
                true, PlaybackStateCompat.STATE_PAUSED);

        assertTrue(playing.isVisible());
        assertTrue(playing.isPlaying());
        assertTrue(paused.isVisible());
        assertFalse(paused.isPlaying());
    }

    @Test
    public void collapsedPlayerSheetKeepsMiniPlayerVisible() {
        final MiniPlayerState playing = MiniPlayerState.from(
                true, PlaybackStateCompat.STATE_PLAYING);

        assertTrue(playing.isVisibleWhenPlayerExpanded(false));
        assertFalse(playing.isVisibleWhenPlayerExpanded(true));
    }

    @Test
    public void stoppedOrDisconnectedPlaybackIsHidden() {
        assertFalse(MiniPlayerState.from(
                true, PlaybackStateCompat.STATE_STOPPED).isVisible());
        assertFalse(MiniPlayerState.from(
                false, PlaybackStateCompat.STATE_PLAYING).isVisible());
    }

    @Test
    public void activePlaybackWithoutMetadataStillShowsControls() {
        assertTrue(MiniPlayerState.from(
                true, PlaybackStateCompat.STATE_PLAYING).isVisible());
    }
}
