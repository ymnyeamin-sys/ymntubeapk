package org.schabi.newpipe.player.ui;

import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;

public final class MiniPlayerState {
    private final boolean visible;
    private final boolean playing;

    private MiniPlayerState(final boolean visible, final boolean playing) {
        this.visible = visible;
        this.playing = playing;
    }

    @NonNull
    public static MiniPlayerState from(final boolean connected,
                                       final int playbackState) {
        final boolean active = playbackState != PlaybackStateCompat.STATE_NONE
                && playbackState != PlaybackStateCompat.STATE_STOPPED
                && playbackState != PlaybackStateCompat.STATE_ERROR;
        final boolean isPlaying = playbackState == PlaybackStateCompat.STATE_PLAYING
                || playbackState == PlaybackStateCompat.STATE_BUFFERING
                || playbackState == PlaybackStateCompat.STATE_CONNECTING
                || playbackState == PlaybackStateCompat.STATE_FAST_FORWARDING
                || playbackState == PlaybackStateCompat.STATE_REWINDING;
        return new MiniPlayerState(connected && active, isPlaying);
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isVisibleWhenPlayerExpanded(final boolean playerExpanded) {
        return visible && !playerExpanded;
    }

    public boolean isPlaying() {
        return playing;
    }
}
