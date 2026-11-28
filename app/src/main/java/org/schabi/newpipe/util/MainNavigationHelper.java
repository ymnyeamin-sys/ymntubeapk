/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.settings.SettingsActivity;

public final class MainNavigationHelper {
    private MainNavigationHelper() { }

    public static void setup(final BottomNavigationView navigationView,
                             final Activity activity,
                             @IdRes final int selectedItemId) {
        attachDownloadProgress(activity, navigationView);
        navigationView.getMenu().findItem(R.id.main_navigation_history)
                .setVisible(PreferenceManager.getDefaultSharedPreferences(activity)
                        .getBoolean(activity.getString(R.string.enable_watch_history_key), true));
        navigationView.setSelectedItemId(selectedItemId);
        navigationView.setOnItemSelectedListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == navigationView.getSelectedItemId()) {
                return true;
            }

            if (itemId == R.id.main_navigation_home
                    || itemId == R.id.main_navigation_history) {
                openMainDestination(activity, itemId);
                return true;
            } else if (itemId == R.id.main_navigation_shorts) {
                openMainDestination(activity, itemId);
                return true;
            } else if (itemId == R.id.main_navigation_downloads) {
                return activity instanceof DownloadActivity
                        || NavigationHelper.openDownloads(activity);
            } else if (itemId == R.id.main_navigation_settings) {
                if (!(activity instanceof SettingsActivity)) {
                    NavigationHelper.openSettings(activity);
                }
                return true;
            }
            return false;
        });
    }

    public static void attachDownloadProgress(final Activity activity,
                                              final BottomNavigationView navigationView) {
        if (activity instanceof LifecycleOwner) {
            ((LifecycleOwner) activity).getLifecycle().addObserver(
                    new DownloadProgressIndicator(activity, navigationView));
        }
    }

    private static void openMainDestination(final Activity activity, @IdRes final int destination) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).navigateToMainDestination(destination);
            return;
        }

        final Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_MAIN_NAVIGATION_DESTINATION, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }
}
