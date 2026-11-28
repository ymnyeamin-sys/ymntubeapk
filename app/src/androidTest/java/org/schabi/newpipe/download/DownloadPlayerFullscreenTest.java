package org.schabi.newpipe.download;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DownloadPlayerFullscreenTest {
    @Test
    @SuppressWarnings("deprecation")
    public void downloadedPlayerOpensInLandscape() throws PackageManager.NameNotFoundException {
        final Context context = ApplicationProvider.getApplicationContext();
        final ActivityInfo activityInfo = context.getPackageManager().getActivityInfo(
                new ComponentName(context, DownloadPlayerActivity.class), 0);

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                activityInfo.screenOrientation);
    }
}
