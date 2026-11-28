package org.schabi.newpipe.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;


@RunWith(AndroidJUnit4.class)
public class DownloadProgressIndicatorTest {
    @Test
    public void progressUsesAppCompatOverlayAndKeepsAccessibleDownloadsLabel() {
        try (ActivityScenario<DownloadActivity> scenario =
                     ActivityScenario.launch(DownloadActivity.class)) {
            scenario.onActivity(activity -> {
                final BottomNavigationView navigation = activity.findViewById(
                        R.id.main_bottom_navigation);
                DownloadProgressIndicator.showProgress(activity, navigation, 42);

                final CharSequence description = navigation.getMenu()
                        .findItem(R.id.main_navigation_downloads).getContentDescription();
                assertTrue(description.toString().contains("42"));
                final View itemView = navigation.findViewById(
                        R.id.main_navigation_downloads);
                final View badge = itemView.findViewWithTag("download-progress-badge");
                final CharSequence title = navigation.getMenu()
                        .findItem(R.id.main_navigation_downloads).getTitle();
                assertTrue(badge != null || title.toString().contains("42"));

                DownloadProgressIndicator.clearProgress(activity, navigation);
                assertTrue(activity.getString(R.string.downloads).contentEquals(
                        navigation.getMenu().findItem(R.id.main_navigation_downloads)
                                .getContentDescription()));
            });
        }
    }

    @Test
    public void downloadPromptShowsThumbnailAndScrollableStreamControls() {
        try (ActivityScenario<DownloadActivity> scenario =
                     ActivityScenario.launch(DownloadActivity.class)) {
            scenario.onActivity(activity -> {
                final View prompt = activity.getLayoutInflater().inflate(
                        R.layout.download_dialog, null, false);

                assertTrue(prompt instanceof ScrollView);
                assertNotNull(prompt.findViewById(R.id.download_thumbnail));
                assertNotNull(prompt.findViewById(R.id.download_video_title));
                assertNotNull(prompt.findViewById(R.id.download_video_uploader));
                assertNotNull(prompt.findViewById(R.id.video_audio_group));
                assertNotNull(prompt.findViewById(R.id.quality_spinner));
                assertTrue(((ScrollView) prompt).isFillViewport());
            });
        }
    }
}
