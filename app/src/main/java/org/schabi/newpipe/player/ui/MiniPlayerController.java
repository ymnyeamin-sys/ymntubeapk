package org.schabi.newpipe.player.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.ViewMiniPlayerBinding;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.notification.NotificationConstants;

public final class MiniPlayerController implements DefaultLifecycleObserver {
    private static final String TAG = "MiniPlayerController";

    private final Activity activity;
    private final View navigation;
    private final View playerSheet;
    private final Runnable openPlayerAction;
    private final ViewMiniPlayerBinding binding;
    private final Runnable dismissalStateChangeListener = this::updateVisibility;
    private final BottomSheetBehavior.BottomSheetCallback bottomSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
                    // Hiding the sheet triggers VideoDetailFragment cleanup and stops playback.
                    updateVisibility();
                }

                @Override
                public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                    // The mini-player remains available while the detail sheet is being dragged.
                }
            };

    private MediaBrowserCompat browser;
    private MediaControllerCompat controller;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private MiniPlayerState state = MiniPlayerState.from(
            false, PlaybackStateCompat.STATE_NONE);
    private boolean bottomSheetCallbackRegistered;
    private boolean updateBottomMarginOnLayout;
    private final View.OnLayoutChangeListener navigationLayoutChangeListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    updateBottomMargin();

    private final MediaBrowserCompat.ConnectionCallback connectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    if (browser == null || !browser.isConnected()) {
                        return;
                    }

                    controller = new MediaControllerCompat(activity, browser.getSessionToken());
                    controller.registerCallback(mediaControllerCallback);
                    renderPlayer();
                }

                @Override
                public void onConnectionFailed() {
                    Log.w(TAG, "Could not connect to the playback service");
                    updateState(null, null);
                }

                @Override
                public void onConnectionSuspended() {
                    disconnectController();
                    updateState(null, null);
                }
            };

    private final MediaControllerCompat.Callback mediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(final PlaybackStateCompat playbackState) {
                    renderPlayer();
                }

                @Override
                public void onMetadataChanged(final MediaMetadataCompat metadata) {
                    renderPlayer();
                }

                @Override
                public void onSessionDestroyed() {
                    disconnectController();
                    updateState(null, null);
                }
            };

    public MiniPlayerController(@NonNull final Activity activity,
                                @NonNull final View navigation,
                                @NonNull final View playerSheet,
                                @NonNull final Runnable openPlayerAction) {
        this.activity = activity;
        this.navigation = navigation;
        this.playerSheet = playerSheet;
        this.openPlayerAction = openPlayerAction;
        binding = ViewMiniPlayerBinding.inflate(LayoutInflater.from(activity));
        attachToNavigation();

        binding.getRoot().setOnClickListener(view -> this.openPlayerAction.run());
        binding.miniPlayerPlayPause.setOnClickListener(view -> togglePlayback());
        binding.miniPlayerClose.setOnClickListener(view -> closePlayback());
        MiniPlayerDismissal.addListener(dismissalStateChangeListener);
    }

    @Override
    public void onStart(@NonNull final LifecycleOwner owner) {
        attachBottomSheetCallback();
        browser = new MediaBrowserCompat(
                activity,
                new ComponentName(activity, PlayerService.class),
                connectionCallback,
                null);
        browser.connect();
    }

    @Override
    public void onStop(@NonNull final LifecycleOwner owner) {
        disconnectController();
        if (browser != null) {
            browser.disconnect();
            browser = null;
        }
        updateState(null, null);
    }

    @Override
    public void onDestroy(@NonNull final LifecycleOwner owner) {
        onStop(owner);
        MiniPlayerDismissal.removeListener(dismissalStateChangeListener);
        if (bottomSheetBehavior != null && bottomSheetCallbackRegistered) {
            bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback);
            bottomSheetCallbackRegistered = false;
        }
        if (updateBottomMarginOnLayout) {
            navigation.removeOnLayoutChangeListener(navigationLayoutChangeListener);
            final ViewGroup parent = (ViewGroup) navigation.getParent();
            if (parent != null) {
                parent.removeOnLayoutChangeListener(navigationLayoutChangeListener);
            }
        }
        final ViewGroup parent = (ViewGroup) binding.getRoot().getParent();
        if (parent != null) {
            parent.removeView(binding.getRoot());
        }
    }

    private void attachToNavigation() {
        final ViewGroup parent = (ViewGroup) navigation.getParent();
        if (parent == null) {
            throw new IllegalStateException("Bottom navigation is not attached");
        }

        final int index = parent.indexOfChild(navigation);
        final ViewGroup.LayoutParams params;
        if (parent instanceof LinearLayout
                && ((LinearLayout) parent).getOrientation() == LinearLayout.VERTICAL) {
            params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else if (parent instanceof ConstraintLayout) {
            final ConstraintLayout.LayoutParams constraintParams =
                    new ConstraintLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            constraintParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            constraintParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            constraintParams.bottomToTop = navigation.getId();
            params = constraintParams;
        } else if (parent instanceof CoordinatorLayout) {
            final CoordinatorLayout.LayoutParams coordinatorParams =
                    new CoordinatorLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            coordinatorParams.gravity = Gravity.BOTTOM | Gravity.START;
            coordinatorParams.bottomMargin = getNavigationTopMargin(parent);
            params = coordinatorParams;
            updateBottomMarginOnLayout = true;
        } else if (parent instanceof RelativeLayout) {
            final RelativeLayout.LayoutParams relativeParams =
                    new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            relativeParams.addRule(RelativeLayout.ABOVE, navigation.getId());
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            relativeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            params = relativeParams;
        } else if (parent instanceof FrameLayout) {
            final FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            frameParams.bottomMargin = getNavigationTopMargin(parent);
            params = frameParams;
            updateBottomMarginOnLayout = true;
        } else if (parent instanceof DrawerLayout) {
            final DrawerLayout.LayoutParams drawerParams = new DrawerLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            drawerParams.bottomMargin = getNavigationTopMargin(parent);
            params = drawerParams;
            updateBottomMarginOnLayout = true;
        } else {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            Log.w(TAG, "Using fallback mini-player placement for "
                    + parent.getClass().getSimpleName());
        }
        final int insertionIndex = parent instanceof LinearLayout
                && ((LinearLayout) parent).getOrientation() == LinearLayout.VERTICAL
                ? index : index + 1;
        parent.addView(binding.getRoot(), insertionIndex, params);
        ViewCompat.setElevation(binding.getRoot(),
                ViewCompat.getElevation(navigation)
                        + activity.getResources().getDisplayMetrics().density);
        if (updateBottomMarginOnLayout) {
            navigation.addOnLayoutChangeListener(navigationLayoutChangeListener);
            parent.addOnLayoutChangeListener(navigationLayoutChangeListener);
        }
    }

    private int getNavigationTopMargin(@NonNull final ViewGroup parent) {
        return MiniPlayerLayoutPolicy.getBottomMargin(parent.getHeight(), navigation.getTop());
    }

    private void updateBottomMargin() {
        final ViewGroup parent = (ViewGroup) binding.getRoot().getParent();
        if (parent == null) {
            return;
        }
        final ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams marginParams =
                    (ViewGroup.MarginLayoutParams) params;
            final int navigationTopMargin = getNavigationTopMargin(parent);
            if (marginParams.bottomMargin != navigationTopMargin) {
                marginParams.bottomMargin = navigationTopMargin;
                binding.getRoot().setLayoutParams(marginParams);
            }
        }
    }

    private void attachBottomSheetCallback() {
        if (bottomSheetCallbackRegistered) {
            return;
        }
        if (!(playerSheet.getLayoutParams() instanceof CoordinatorLayout.LayoutParams)) {
            return;
        }

        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) playerSheet.getLayoutParams();
        if (params.getBehavior() instanceof BottomSheetBehavior) {
            // The behavior is created by the activity layout and shared with VideoDetailFragment.
            bottomSheetBehavior = BottomSheetBehavior.from(playerSheet);
            bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);
            bottomSheetCallbackRegistered = true;
        }
    }

    private void renderPlayer() {
        final MediaMetadataCompat metadata = controller == null ? null : controller.getMetadata();
        final PlaybackStateCompat playbackState =
                controller == null ? null : controller.getPlaybackState();
        updateState(metadata, playbackState);
    }

    private void updateState(final MediaMetadataCompat metadata,
                             final PlaybackStateCompat playbackState) {
        state = MiniPlayerState.from(
                controller != null,
                playbackState == null ? PlaybackStateCompat.STATE_NONE : playbackState.getState());

        if (state.isVisible()) {
            final CharSequence displayTitle = metadata == null ? null
                    : metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
            final CharSequence title = metadata == null ? null
                    : metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE);
            binding.miniPlayerTitle.setText(MiniPlayerMetadata.titleOrDefault(
                    displayTitle, title, activity.getString(R.string.mini_player_now_playing)));
            final CharSequence displaySubtitle = metadata == null ? null
                    : metadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE);
            final CharSequence artist = metadata == null ? null
                    : metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST);
            final CharSequence albumArtist = metadata == null ? null
                    : metadata.getText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
            final CharSequence creator = MiniPlayerMetadata.creator(
                    displaySubtitle, artist, albumArtist);
            binding.miniPlayerCreator.setText(creator);
            binding.miniPlayerCreator.setVisibility(
                    creator == null || creator.length() == 0 ? View.GONE : View.VISIBLE);

            final Bitmap artwork = MiniPlayerMetadata.artwork(metadata);
            if (artwork == null) {
                binding.miniPlayerThumbnail.setImageResource(
                        R.drawable.placeholder_thumbnail_video);
            } else {
                binding.miniPlayerThumbnail.setImageBitmap(artwork);
            }
            binding.miniPlayerPlayPause.setImageResource(
                    state.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            binding.miniPlayerPlayPause.setContentDescription(
                    activity.getString(state.isPlaying() ? R.string.pause : R.string.play));
        }

        updateVisibility();
    }

    private void updateVisibility() {
        final boolean playerExpanded = bottomSheetBehavior != null
                && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
        binding.getRoot().setVisibility(
                MiniPlayerDismissal.isVisible(state.isVisible(), playerExpanded)
                        ? View.VISIBLE : View.GONE);
    }

    private void togglePlayback() {
        if (controller == null) {
            return;
        }

        if (state.isPlaying()) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    private void closePlayback() {
        MiniPlayerDismissal.dismiss();
        updateVisibility();
        activity.sendBroadcast(new Intent(NotificationConstants.ACTION_CLOSE)
                .setPackage(activity.getPackageName()));
    }

    private void disconnectController() {
        if (controller != null) {
            controller.unregisterCallback(mediaControllerCallback);
            controller = null;
        }
    }
}
