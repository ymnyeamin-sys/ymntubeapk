package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo;
import android.view.View;

import org.junit.Test;

public class FullscreenLayoutPolicyTest {
    @Test
    public void fullscreenHidesChromeUsesLandscapeAndFillsParent() {
        assertEquals(View.GONE, FullscreenLayoutPolicy.chromeVisibility(true, View.VISIBLE));
        assertEquals(0, FullscreenLayoutPolicy.playerBottomMargin(true, 56));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                FullscreenLayoutPolicy.requestedOrientation(
                        true, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
    }

    @Test
    public void exitingFullscreenRestoresOriginalLayoutAndOrientation() {
        assertEquals(View.INVISIBLE,
                FullscreenLayoutPolicy.chromeVisibility(false, View.INVISIBLE));
        assertEquals(56, FullscreenLayoutPolicy.playerBottomMargin(false, 56));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                FullscreenLayoutPolicy.requestedOrientation(
                        false, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    }
}
