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
    private boolean homeChromeHidden;

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
    protected ItemViewMode getItemViewMode() {
        // Use YouTube-style full-width video cards on the Home feed only.
        return useAsFrontPage ? ItemViewMode.CARD : super.getItemViewMode();
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
        homeChipsScroll = view.findViewById(R.id.home_chips_scroll);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (useAsFrontPage) {
            setHomeChromeHidden(false);
        }
        if (!Localization.getPreferredContentCountry(requireContext()).equals(contentCountry)) {
            reloadContent();
        }
        if (useAsFrontPage && activity != null) {
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
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
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
        if (!useAsFrontPage || itemsList == null) {
            return;
        }
        itemsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull final RecyclerView recyclerView,
                                   final int dx, final int dy) {
                if (dy > 4) {
                    setHomeChromeHidden(true);
                } else if (dy < -4) {
                    setHomeChromeHidden(false);
                }
            }
        });
    }

    private void setHomeChromeHidden(final boolean hidden) {
        if (!useAsFrontPage) {
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
