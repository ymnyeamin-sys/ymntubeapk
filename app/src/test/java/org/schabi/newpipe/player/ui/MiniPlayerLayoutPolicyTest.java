package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MiniPlayerLayoutPolicyTest {
    @Test
    public void miniPlayerBottomMatchesNavigationTop() {
        assertEquals(80, MiniPlayerLayoutPolicy.getBottomMargin(800, 720));
    }

    @Test
    public void unmeasuredParentDoesNotCreateNegativeMargin() {
        assertEquals(0, MiniPlayerLayoutPolicy.getBottomMargin(0, 40));
    }
}
