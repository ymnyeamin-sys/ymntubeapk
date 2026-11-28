package org.schabi.newpipe.download;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;

@RunWith(AndroidJUnit4.class)
public class DownloadActivityLaunchTest {
    @Test
    public void downloadsScreenInflatesLinkControls() {
        try (ActivityScenario<DownloadActivity> scenario =
                     ActivityScenario.launch(DownloadActivity.class)) {
            final Instrumentation instrumentation =
                    InstrumentationRegistry.getInstrumentation();
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.download_link_input));
                assertNotNull(activity.findViewById(R.id.paste_download_link));
                assertNotNull(activity.findViewById(R.id.start_download_from_link));
            });
        }
    }
}
