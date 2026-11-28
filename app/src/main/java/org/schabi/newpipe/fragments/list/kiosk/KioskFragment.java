/*
 * SPDX-FileCopyrightText: 2017-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;

import io.reactivex.rxjava3.core.Single;

public class KioskFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo> {
    @State
    String kioskId = "";
    String kioskTranslatedName;
    @State
    ContentCountry contentCountry;

    private View homeChipsScroll;
    private LinearLayout homeChips;
    private boolean homeChromeHidden;
    private RecyclerView.OnScrollListener homeChromeScrollListener;
    // Persist Home identity across fragment recreation so the Home feed always uses
    // the exact same Card item layout as the Related Videos list.
    @State
    public boolean isHomeFeed;

    public static KioskFragment getInstance(final int serviceId) throws ExtractionException {
        return getInstance(serviceId, NewPipe.getService(serviceId)
                .getKioskList().getDefaultKioskId());
    }

    public static KioskFragment getInstance(final int serviceId, final String kioskId)
            throws ExtractionException {
        final KioskFragment instance = new KioskFragment();
        final StreamingService service = NewPipe.getService(serviceId);
        final ListLinkHandlerFactory kioskLinkHandlerFactory = service.getKioskList()
                .getListLinkHandlerFactoryByType(kioskId);
        instance.setInitialData(serviceId,
                kioskLinkHandlerFactory.fromId(kioskId).getUrl(), kioskId);
        instance.kioskId = kioskId;
        return instance;
    }

    public KioskFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    @Override
    public void useAsFrontPage(final boolean value) {
        super.useAsFrontPage(value);
        isHomeFeed = value;
    }

    @Override
    protected ItemViewMode getItemViewMode() {
        // Use YouTube-style full-width video cards on the Home feed only.
        return isHomeFeed ? ItemViewMode.CARD : super.getItemViewMode();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, activity);
        name = kioskTranslatedName;
        contentCountry = Localization.getPreferredContentCountry(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isHomeFeed) {
            applyRelatedVideosCardStyle();
            homeChipsScroll = view.findViewById(R.id.home_chips_scroll);
            homeChips = view.findViewById(R.id.home_chips);
            setupHomeChips();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isHomeFeed) {
            applyRelatedVideosCardStyle();
            setHomeChromeHidden(false);
        }
        if (!Localization.getPreferredContentCountry(requireContext()).equals(contentCountry)) {
            reloadContent();
        }
        if (isHomeFeed && activity != null) {
            try {
                setTitle(kioskTranslatedName);
            } catch (final Exception e) {
                showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Setting kiosk title"));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(isHomeFeed ? R.layout.fragment_home
                : R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && isHomeFeed) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    protected void useNormalItemListScrollListener() {
        super.useNormalItemListScrollListener();
        attachHomeChromeScrollListener();
    }

    @Override
    protected void useInitialItemListLoadScrollListener() {
        super.useInitialItemListLoadScrollListener();
        attachHomeChromeScrollListener();
    }

    private void attachHomeChromeScrollListener() {
        if (!isHomeFeed || itemsList == null) {
            return;
        }
        if (homeChromeScrollListener != null) {
            itemsList.removeOnScrollListener(homeChromeScrollListener);
        }
        homeChromeScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull final RecyclerView recyclerView,
                                   final int dx, final int dy) {
                if (dy > 4) {
                    setHomeChromeHidden(true);
                } else if (dy < -4 || !recyclerView.canScrollVertically(-1)) {
                    setHomeChromeHidden(false);
                }
            }
        };
        itemsList.addOnScrollListener(homeChromeScrollListener);
    }

    /**
     * Uses the exact adapter mode and vertical layout manager used by RelatedItemsFragment
     * when Card mode is selected. This avoids Home-specific grid/thumbnail holders.
     */
    private void applyRelatedVideosCardStyle() {
        if (!isHomeFeed || itemsList == null || infoListAdapter == null) {
            return;
        }
        itemsList.setLayoutManager(getListLayoutManager());
        infoListAdapter.setFullWidthGridThumbnails(false);
        infoListAdapter.setItemViewMode(ItemViewMode.CARD);
        infoListAdapter.notifyDataSetChanged();
    }

    private void setupHomeChips() {
        if (homeChips == null || !isHomeFeed) {
            return;
        }
        homeChips.removeAllViews();
        try {
            for (final String id : NewPipe.getService(serviceId).getKioskList()
                    .getAvailableKiosks()) {
                final TextView chip = new TextView(requireContext());
                chip.setText(KioskTranslator.getTranslatedKioskName(id, requireContext()));
                chip.setTextColor(requireContext().getColor(R.color.home_chip_text));
                chip.setTextSize(14);
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setBackgroundResource(R.drawable.home_chip_bg);
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
                params.setMarginEnd(dp(8));
                homeChips.addView(chip, params);
                chip.setOnClickListener(v -> selectHomeKiosk(id));
            }
        } catch (final Exception e) {
            showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Setting up Home categories"));
        }
    }

    private void selectHomeKiosk(final String id) {
        try {
            final StreamingService service = NewPipe.getService(serviceId);
            final ListLinkHandlerFactory factory = service.getKioskList()
                    .getListLinkHandlerFactoryByType(id);
            kioskId = id;
            url = factory.fromId(id).getUrl();
            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(id, requireContext());
            name = kioskTranslatedName;
            contentCountry = Localization.getPreferredContentCountry(requireContext());
            setHomeChromeHidden(false);
            reloadContent();
        } catch (final Exception e) {
            showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Changing Home category"));
        }
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setHomeChromeHidden(final boolean hidden) {
        if (!isHomeFeed) {
            return;
        }
        if (homeChromeHidden == hidden) {
            return;
        }
        homeChromeHidden = hidden;
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).setHomeChromeHidden(hidden);
        }
        if (homeChipsScroll == null) {
            return;
        }

        // Keep the category strip in sync with the activity-level Home navigation chrome.
        homeChipsScroll.animate().cancel();
        if (hidden) {
            if (homeChipsScroll.getVisibility() != View.VISIBLE) {
                return;
            }
            final int height = homeChipsScroll.getHeight();
            homeChipsScroll.animate()
                    .translationY(-height)
                    .alpha(0f)
                    .setDuration(160)
                    .withEndAction(() -> {
                        homeChipsScroll.setVisibility(View.GONE);
                        homeChipsScroll.setTranslationY(0);
                        homeChipsScroll.setAlpha(1f);
                    })
                    .start();
        } else {
            if (homeChipsScroll.getVisibility() == View.VISIBLE) {
                homeChipsScroll.animate().translationY(0).alpha(1f)
                        .setDuration(160).start();
            } else {
                homeChipsScroll.setVisibility(View.VISIBLE);
                homeChipsScroll.setAlpha(0f);
                homeChipsScroll.post(() -> {
                    homeChipsScroll.setTranslationY(-homeChipsScroll.getHeight());
                    homeChipsScroll.animate().translationY(0).alpha(1f)
                            .setDuration(160).start();
                });
            }
        }
    }

    @Override
    public Single<KioskInfo> loadResult(final boolean forceReload) {
        contentCountry = Localization.getPreferredContentCountry(requireContext());
        return ExtractorHelper.getKioskInfo(serviceId, url, forceReload);
    }

    @Override
    public Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextPage);
    }

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        name = kioskTranslatedName;
        setTitle(kioskTranslatedName);
    }

    @Override
    public void showEmptyState() {
        super.showEmptyState();
    }
}
