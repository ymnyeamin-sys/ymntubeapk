package org.schabi.newpipe.player.ui;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Nullable;

final class MiniPlayerMetadata {
    private MiniPlayerMetadata() {
    }

    static CharSequence titleOrDefault(@Nullable final CharSequence displayTitle,
                                       @Nullable final CharSequence title,
                                       final CharSequence defaultTitle) {
        final CharSequence nonEmptyDisplayTitle = getNonEmptyText(displayTitle);
        if (nonEmptyDisplayTitle != null) {
            return nonEmptyDisplayTitle;
        }

        final CharSequence nonEmptyTitle = getNonEmptyText(title);
        return nonEmptyTitle == null ? defaultTitle : nonEmptyTitle;
    }

    @Nullable
    static CharSequence creator(@Nullable final CharSequence displaySubtitle,
                                @Nullable final CharSequence artist,
                                @Nullable final CharSequence albumArtist) {
        final CharSequence nonEmptyDisplaySubtitle = getNonEmptyText(displaySubtitle);
        if (nonEmptyDisplaySubtitle != null) {
            return nonEmptyDisplaySubtitle;
        }

        final CharSequence nonEmptyArtist = getNonEmptyText(artist);
        return nonEmptyArtist != null ? nonEmptyArtist : getNonEmptyText(albumArtist);
    }

    @Nullable
    static Bitmap artwork(@Nullable final MediaMetadataCompat metadata) {
        if (metadata == null) {
            return null;
        }
        final Bitmap albumArt = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        return albumArt != null ? albumArt
                : metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON);
    }

    @Nullable
    private static CharSequence getNonEmptyText(@Nullable final CharSequence value) {
        return value == null || value.toString().trim().isEmpty() ? null : value;
    }
}
