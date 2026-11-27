// SPDX-License-Identifier: GPL-3.0-or-later
package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.schabi.newpipe.R;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;
import us.shandian.giga.service.DownloadProgressCalculator;

public final class DownloadProgressIndicator implements DefaultLifecycleObserver {
    private static final long INITIAL_BIND_DELAY_MS = 750;
    private static final long POLL_INTERVAL_MS = 1000;

    private final Activity activity;
    private final BottomNavigationView navigationView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;
    private boolean bound;
    private DownloadManagerBinder binder;

    private final Runnable bindService = this::bindDownloadManager;

    private void bindDownloadManager() {
        if (started && !bound) {
            bound = activity.bindService(
                    new Intent(activity, DownloadManagerService.class),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
            );
            if (!bound) {
                clearProgress();
            }
        }
    }

    private final Runnable updateProgress = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            if (binder != null) {
                final int progress = binder.getDownloadManager().getActiveDownloadProgressPercent();
                if (progress == DownloadProgressCalculator.NO_ACTIVE_DOWNLOADS) {
                    clearProgress();
                } else {
                    showProgress(progress);
                }
            }
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            binder = (DownloadManagerBinder) service;
            handler.removeCallbacks(updateProgress);
            updateProgress.run();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            binder = null;
            clearProgress();
        }
    };

    public DownloadProgressIndicator(
            final Activity activity,
            final BottomNavigationView navigationView) {
        this.activity = activity;
        this.navigationView = navigationView;
    }

    static void showProgress(final Activity activity,
                             final BottomNavigationView navigationView,
                             final int progress) {
        final MenuItem downloadsItem =
                navigationView.getMenu().findItem(R.id.main_navigation_downloads);
        downloadsItem.setContentDescription(activity.getString(
                R.string.download_progress_accessibility, progress));

        final View itemView = navigationView.findViewById(R.id.main_navigation_downloads);
        if (!(itemView instanceof FrameLayout)) {
            downloadsItem.setTitle(activity.getString(
                    R.string.downloads_progress_label, progress));
            return;
        }

        final FrameLayout item = (FrameLayout) itemView;
        TextView badge = item.findViewWithTag("download-progress-badge");
        if (badge == null) {
            badge = new TextView(activity);
            badge.setTag("download-progress-badge");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(9);
            badge.setGravity(Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            badge.setPadding(dp(activity, 4), dp(activity, 2),
                    dp(activity, 4), dp(activity, 2));
            badge.setBackground(createBadgeBackground(activity));
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            params.setMargins(0, dp(activity, 3), dp(activity, 10), 0);
            item.addView(badge, params);
        }
        badge.setText(activity.getString(R.string.download_progress_badge, progress));
    }

    static void clearProgress(final Activity activity,
                              final BottomNavigationView navigationView) {
        final MenuItem downloadsItem =
                navigationView.getMenu().findItem(R.id.main_navigation_downloads);
        downloadsItem.setTitle(R.string.downloads);
        downloadsItem.setContentDescription(activity.getString(R.string.downloads));
        final View itemView = navigationView.findViewById(R.id.main_navigation_downloads);
        if (itemView instanceof ViewGroup) {
            final View badge = ((ViewGroup) itemView).findViewWithTag("download-progress-badge");
            if (badge != null) {
                ((ViewGroup) itemView).removeView(badge);
            }
        }
    }

    @Override
    public void onStart(final LifecycleOwner owner) {
        started = true;
        handler.postDelayed(bindService, INITIAL_BIND_DELAY_MS);
    }

    @Override
    public void onStop(final LifecycleOwner owner) {
        started = false;
        handler.removeCallbacks(bindService);
        handler.removeCallbacks(updateProgress);
        binder = null;
        if (bound) {
            activity.unbindService(serviceConnection);
            bound = false;
        }
        clearProgress();
    }

    private void showProgress(final int progress) {
        showProgress(activity, navigationView, progress);
    }

    private void clearProgress() {
        clearProgress(activity, navigationView);
    }

    private static GradientDrawable createBadgeBackground(final Activity activity) {
        final GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(activity, 10));
        background.setColor(ThemeHelper.resolveColorFromAttr(activity, R.attr.colorPrimary));
        return background;
    }

    private static int dp(final Activity activity, final int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
