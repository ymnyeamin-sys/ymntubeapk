package org.schabi.newpipe.local.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedInitialLoadGateTest {
    @Test
    fun emptyCachedFeedStartsOneLoad() {
        val gate = FeedInitialLoadGate()

        assertTrue(gate.shouldLoadEmptyFeed(emptyLoadedState()))
        assertFalse(gate.shouldLoadEmptyFeed(emptyLoadedState()))
    }

    @Test
    fun existingLoadDoesNotStartAnotherRequest() {
        val gate = FeedInitialLoadGate()

        assertFalse(gate.shouldLoadEmptyFeed(FeedState.ProgressState()))
        assertFalse(gate.shouldLoadEmptyFeed(emptyLoadedState()))
    }

    private fun emptyLoadedState() =
        FeedState.LoadedState(emptyList(), null, 0L, emptyList())
}
