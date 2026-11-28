package org.schabi.newpipe.local.feed

internal class FeedInitialLoadGate {
    private var initialStateHandled = false

    fun shouldLoadEmptyFeed(state: FeedState): Boolean {
        if (initialStateHandled) {
            return false
        }

        initialStateHandled = true
        return state is FeedState.LoadedState && state.items.isEmpty()
    }
}
