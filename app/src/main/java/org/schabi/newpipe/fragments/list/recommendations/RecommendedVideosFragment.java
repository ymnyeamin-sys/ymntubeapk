/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.recommendations;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.RecommendationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class RecommendedVideosFragment extends BaseListInfoFragment<InfoItem, SearchInfo> {
    private static final String TAG = RecommendedVideosFragment.class.getSimpleName();
    private static final int MAX_HISTORY_ITEMS_TO_ANALYZE = 10;

    @Nullable
    private View.OnLayoutChangeListener recommendationLayoutChangeListener;

    @State
    String searchQuery;

    public RecommendedVideosFragment() {
        super(UserAction.SEARCHED);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        infoListAdapter.setFullWidthGridThumbnails(true);
        serviceId = ServiceHelper.getSelectedServiceId(requireContext());
        setInitialData(serviceId, "", getString(R.string.tab_for_you));
    }

    @Override
    public void onResume() {
        super.onResume();
        final int selectedServiceId = ServiceHelper.getSelectedServiceId(requireContext());
        if (serviceId != selectedServiceId) {
            serviceId = selectedServiceId;
            currentInfo = null;
            currentNextPage = null;
            searchQuery = null;
            reloadContent();
        }
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        recommendationLayoutChangeListener =
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateRecommendationColumnCount();
        itemsList.addOnLayoutChangeListener(recommendationLayoutChangeListener);
        itemsList.post(this::updateRecommendationColumnCount);
    }

    @Override
    public void onDestroyView() {
        if (recommendationLayoutChangeListener != null && itemsList != null) {
            itemsList.removeOnLayoutChangeListener(recommendationLayoutChangeListener);
            recommendationLayoutChangeListener = null;
        }
        super.onDestroyView();
    }

    @Override
    protected Single<SearchInfo> loadResult(final boolean forceLoad) {
        return getRecommendationQuery()
                .flatMap(query -> {
                    searchQuery = query;
                    setInitialData(serviceId, query, getString(R.string.tab_for_you));
                    return ExtractorHelper.searchFor(serviceId, query, List.of(), "");
                });
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreSearchItems(
                serviceId, searchQuery, List.of(), "", currentNextPage);
    }

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        super.handleResult(result);
        name = getString(R.string.tab_for_you);
        setTitle(name);
    }

    @Override
    protected ItemViewMode getItemViewMode() {
        return ItemViewMode.GRID;
    }

    @Override
    protected RecyclerView.LayoutManager getGridLayoutManager() {
        final int spanCount = RecommendationGridPolicy.getColumnCount(
                getResources().getConfiguration().screenWidthDp);
        final GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), spanCount);
        layoutManager.setSpanSizeLookup(infoListAdapter.getSpanSizeLookup(spanCount));
        return layoutManager;
    }

    private void updateRecommendationColumnCount() {
        if (itemsList == null || itemsList.getWidth() <= 0
                || !(itemsList.getLayoutManager() instanceof GridLayoutManager)) {
            return;
        }

        final int availableWidth = itemsList.getWidth()
                - itemsList.getPaddingLeft() - itemsList.getPaddingRight();
        final int spanCount = RecommendationGridPolicy.getColumnCountForWidth(
                availableWidth, getResources().getDisplayMetrics().density);
        final GridLayoutManager layoutManager =
                (GridLayoutManager) itemsList.getLayoutManager();
        if (layoutManager.getSpanCount() != spanCount) {
            layoutManager.setSpanCount(spanCount);
            layoutManager.setSpanSizeLookup(infoListAdapter.getSpanSizeLookup(spanCount));
        }
    }

    private Single<String> getRecommendationQuery() {
        final String fallbackQuery = getString(R.string.trending);
        final HistoryRecordManager historyRecordManager =
                new HistoryRecordManager(requireContext());

        return historyRecordManager.getStreamHistorySortedById()
                .firstOrError()
                .onErrorReturn(error -> {
                    Log.w(TAG, "Could not read watch history; loading trending instead", error);
                    return List.of();
                })
                .flatMap(history -> {
                    final List<StreamHistoryEntry> candidates = history.stream()
                            .filter(entry -> entry.getStreamEntity().getServiceId() == serviceId)
                            .sorted(Comparator
                                    .comparingLong(StreamHistoryEntry::getRepeatCount).reversed()
                                    .thenComparing(StreamHistoryEntry::getAccessDate,
                                            Comparator.reverseOrder()))
                            .limit(MAX_HISTORY_ITEMS_TO_ANALYZE)
                            .toList();

                    if (candidates.isEmpty()) {
                        return Single.just(fallbackQuery);
                    }

                    return Flowable.fromIterable(candidates)
                            .flatMapSingle(entry -> ExtractorHelper.getStreamInfo(
                                            entry.getStreamEntity().getServiceId(),
                                            entry.getStreamEntity().getUrl(),
                                            false)
                                    .map(info -> new CategoryWatch(
                                            info.getCategory(), entry.getRepeatCount()))
                                    .onErrorReturn(error -> {
                                        Log.w(TAG, "Could not load a watched video's category",
                                                error);
                                        return new CategoryWatch(null, 0);
                                    }))
                            .toList()
                            .map(this::selectRecommendationCategory)
                            .map(category -> category == null ? fallbackQuery : category);
                });
    }

    @Nullable
    private String selectRecommendationCategory(final List<CategoryWatch> watchedCategories) {
        final Map<String, Long> categoryWeights = new HashMap<>();
        final Map<String, String> categoryNames = new HashMap<>();

        for (final CategoryWatch watchedCategory : watchedCategories) {
            if (TextUtils.isEmpty(watchedCategory.category)) {
                continue;
            }

            final String category = watchedCategory.category.trim();
            if (category.isEmpty()) {
                continue;
            }

            final String normalizedCategory = category.toLowerCase(Locale.ROOT);
            categoryNames.putIfAbsent(normalizedCategory, category);
            categoryWeights.merge(normalizedCategory,
                    Math.max(watchedCategory.watchCount, 1), Long::sum);
        }

        final String category = RecommendationHelper.mostWatchedCategory(categoryWeights);
        return category == null ? null : Objects.requireNonNull(categoryNames.get(category));
    }

    private static final class CategoryWatch {
        @Nullable
        private final String category;
        private final long watchCount;

        private CategoryWatch(@Nullable final String category, final long watchCount) {
            this.category = category;
            this.watchCount = watchCount;
        }
    }
}
