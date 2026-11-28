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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

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
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class RecommendedVideosFragment extends BaseListInfoFragment<InfoItem, SearchInfo> {
    private static final String TAG = RecommendedVideosFragment.class.getSimpleName();
    private static final int MAX_HISTORY_ITEMS_TO_ANALYZE = 10;
    private static final String[] HOME_CATEGORIES = {
            "For you", "All", "Music", "Gaming", "News", "Education",
            "Sports", "Technology", "Comedy"
    };

    @Nullable
    private View.OnLayoutChangeListener recommendationLayoutChangeListener;

    @State
    String searchQuery;

    @State
    String selectedCategory = "For you";

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
    protected Supplier<View> getListHeaderSupplier() {
        return () -> {
            final float density = getResources().getDisplayMetrics().density;
            final HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setPadding(0, Math.round(8 * density), 0, Math.round(8 * density));

            final LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(Math.round(12 * density), 0, Math.round(12 * density), 0);
            final int accent = resolveThemeColor(androidx.appcompat.R.attr.colorAccent,
                    Color.rgb(45, 125, 210));
            final int textColor = resolveThemeColor(android.R.attr.textColorPrimary, Color.BLACK);

            for (final String category : HOME_CATEGORIES) {
                final TextView chip = new TextView(requireContext());
                chip.setText(category);
                chip.setTextSize(14);
                chip.setSingleLine(true);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setPadding(Math.round(16 * density), Math.round(9 * density),
                        Math.round(16 * density), Math.round(9 * density));
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(Math.round(8 * density));
                row.addView(chip, params);
                styleCategoryChip(chip, category.equals(selectedCategory), accent, textColor);
                chip.setOnClickListener(view -> {
                    if (!category.equals(selectedCategory)) {
                        selectedCategory = category;
                        for (int i = 0; i < row.getChildCount(); i++) {
                            final TextView item = (TextView) row.getChildAt(i);
                            styleCategoryChip(item, HOME_CATEGORIES[i].equals(selectedCategory),
                                    accent, textColor);
                        }
                        reloadContent();
                    }
                });
            }
            scroll.addView(row, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return scroll;
        };
    }

    private int resolveThemeColor(final int attribute, final int fallback) {
        final android.util.TypedValue value = new android.util.TypedValue();
        return requireContext().getTheme().resolveAttribute(attribute, value, true)
                ? value.data : fallback;
    }

    private void styleCategoryChip(final TextView chip, final boolean selected,
                                   final int accent, final int textColor) {
        final GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(24 * getResources().getDisplayMetrics().density);
        background.setColor(selected ? accent
                : resolveThemeColor(androidx.appcompat.R.attr.colorBackgroundFloating,
                        0xFFE8E8E8));
        chip.setBackground(background);
        chip.setTextColor(selected ? Color.WHITE : textColor);
        chip.setElevation(0);
    }

    @Override
    protected Single<SearchInfo> loadResult(final boolean forceLoad) {
        final Single<String> query = "For you".equals(selectedCategory)
                ? getRecommendationQuery()
                : Single.just("All".equals(selectedCategory)
                        ? getString(R.string.trending) : selectedCategory);
        return query.flatMap(selectedQuery -> {
            searchQuery = selectedQuery;
            setInitialData(serviceId, selectedQuery, getString(R.string.tab_for_you));
            return ExtractorHelper.searchFor(serviceId, selectedQuery, List.of(), "");
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
