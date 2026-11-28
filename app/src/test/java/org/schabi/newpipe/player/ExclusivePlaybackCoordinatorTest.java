package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.player.helper.ExclusivePlaybackCoordinator;

public class ExclusivePlaybackCoordinatorTest {
    @Test
    public void claimingNewPlaybackStopsPreviousPlayback() {
        final int[] stops = {0};
        final Object first = new Object();
        final Object second = new Object();

        ExclusivePlaybackCoordinator.claim(first, () -> stops[0]++);
        ExclusivePlaybackCoordinator.claim(second, () -> stops[0]++);

        assertEquals(1, stops[0]);
    }

    @Test
    public void stopActiveStopsOnlyCurrentPlayback() {
        final int[] stops = {0};
        final Object owner = new Object();

        ExclusivePlaybackCoordinator.claim(owner, () -> stops[0]++);
        ExclusivePlaybackCoordinator.stopActive();
        ExclusivePlaybackCoordinator.stopActive();

        assertEquals(1, stops[0]);
    }

    @Test
    public void releasingWrongOwnerKeepsCurrentPlayback() {
        final int[] stops = {0};
        final Object owner = new Object();

        ExclusivePlaybackCoordinator.claim(owner, () -> stops[0]++);
        ExclusivePlaybackCoordinator.release(new Object());

        ExclusivePlaybackCoordinator.stopActive();
        assertEquals(1, stops[0]);
    }
}
