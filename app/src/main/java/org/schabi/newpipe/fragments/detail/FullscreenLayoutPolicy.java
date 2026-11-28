package org.schabi.newpipe.fragments.detail;

import android.content.pm.ActivityInfo;
import android.view.View;

final class FullscreenLayoutPolicy {
    private FullscreenLayoutPolicy() {
    }

    static int chromeVisibility(final boolean fullscreen, final int originalVisibility) {
        return fullscreen ? View.GONE : originalVisibility;
    }

    static int playerBottomMargin(final boolean fullscreen, final int originalBottomMargin) {
        return fullscreen ? 0 : originalBottomMargin;
    }

    static int requestedOrientation(final boolean fullscreen,
                                   final int originalOrientation) {
        return fullscreen ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : originalOrientation;
    }
}
