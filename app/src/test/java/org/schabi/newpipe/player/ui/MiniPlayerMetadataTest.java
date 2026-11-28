package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiniPlayerMetadataTest {
    @Test
    public void displayTitleTakesPrecedenceOverMediaTitle() {
        assertEquals("Display title",
                MiniPlayerMetadata.titleOrDefault("Display title", "Media title", "Now playing"));
    }

    @Test
    public void missingTitleUsesFallbackInsteadOfLeavingPlayerBlank() {
        assertEquals("Now playing", MiniPlayerMetadata.titleOrDefault(null, null, "Now playing"));
    }

    @Test
    public void blankMetadataUsesFallbackInsteadOfRenderingBlankText() {
        assertEquals("Now playing",
                MiniPlayerMetadata.titleOrDefault("  ", "", "Now playing"));
    }

    @Test
    public void displaySubtitleTakesPrecedenceOverArtist() {
        assertEquals("Display creator",
                MiniPlayerMetadata.creator("Display creator", "Artist", "Album artist"));
    }

    @Test
    public void creatorFallsBackToArtistWhenDisplaySubtitleIsMissing() {
        assertEquals("Artist", MiniPlayerMetadata.creator(null, "Artist", "Album artist"));
    }
}
