package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.settings.tabs.Tab;

import java.util.List;

public class MainFragmentTest {
    @Test
    public void homeContainsOnlyTheRecommendationsTab() {
        final List<Tab> homeTabs = MainFragment.getHomeTabs();

        assertEquals(1, homeTabs.size());
        assertEquals(Tab.Type.DEFAULT_KIOSK.getTab(), homeTabs.get(0));
    }
}
