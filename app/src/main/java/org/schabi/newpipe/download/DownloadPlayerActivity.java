/*
 * SPDX-FileCopyrightText: 2026 YmnTube contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.download;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ui.PlayerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.ExclusivePlaybackCoordinator;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

public class DownloadPlayerActivity extends AppCompatActivity {
    private static final String TAG = "DownloadPlayerActivity";

    private Uri mediaUri;
    private PlayerView playerView;
    private long playbackPosition;
    @Nullable
    private ExoPlayer player;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this));
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_download_player);
        enterFullscreen();

        mediaUri = getIntent().getData();
        if (mediaUri == null) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        playerView = findViewById(R.id.download_player_view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mediaUri != null) {
            PlayerHolder.getInstance().stopService();
            initializePlayer();
        }
    }

    @Override
    protected void onStop() {
        ExclusivePlaybackCoordinator.release(this);
        releasePlayer();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        final WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void initializePlayer() {
        if (player != null) {
            return;
        }

        try {
            ExclusivePlaybackCoordinator.claim(this, this::releasePlayer);
            player = new ExoPlayer.Builder(this).build();
            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                    true);
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(final PlaybackException error) {
                    Log.e(TAG, "Unable to play downloaded media", error);
                    Toast.makeText(DownloadPlayerActivity.this, R.string.general_error,
                            Toast.LENGTH_LONG).show();
                }
            });
            final String title = getIntent().getStringExtra(Intent.EXTRA_TITLE);
            final MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMimeType(getIntent().getType())
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
                    .build();
            player.setMediaItem(mediaItem);
            if (playbackPosition > 0) {
                player.seekTo(playbackPosition);
            }
            player.prepare();
            player.play();
        } catch (final RuntimeException e) {
            Log.e(TAG, "Unable to initialize downloaded media player", e);
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_LONG).show();
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }
}
