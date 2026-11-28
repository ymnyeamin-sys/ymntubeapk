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
import android.widget.LinearLayout;
import android.widget.TextView;

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
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The home page. It looks like the YouTube home feed (category chips on top and big video cards)
 * and fills the feed the way YouTube does: videos related to what the user watched recently,
 * more videos from the topics and channels they watch most, and trending videos in between.
 * Without any watch history it shows trending videos and a few popular topics.
 */
public class RecommendedVideosFragment extends BaseListInfoFragment<InfoItem, HomeFeedInfo> {
    private static final String TAG = RecommendedVideosFragment.class.getSimpleName();
    private static final int MAX_HISTORY_ITEMS_TO_ANALYZE = 10;
    private static final int RECENT_VIDEOS_FOR_RELATED = 3;
    private static final int STREAM_INFO_CONCURRENCY = 4;
    private static final int TOP_TOPICS = 2;
    private static final int TOP_CHANNELS = 2;
    private static final int MAX_EMPTY_PAGE_RETRIES = 5;
    private static final long MAX_SHORT_DURATION_SECONDS = 60;
    private static final String HOME_PAGE_MARKER = "home-feed";
    private static final List<String> VIDEO_FILTER = List.of("videos");
    private static final String[] DEFAULT_TOPICS = {"music", "gaming", "news", "technology"};

    /** One category chip of the row on top of the home feed. */
    private static final class Chip {
        private final int labelRes;
        @Nullable
        private final String query;
        private final boolean trending;

        private Chip(final int labelRes, @Nullable final String query, final boolean trending) {
            this.labelRes = labelRes;
            this.query = query;
            this.trending = trending;
        }
    }

    private static final List<Chip> CHIPS = List.of(
            new Chip(R.string.home_chip_all, null, false),
            new Chip(R.string.trending, null, true),
            new Chip(R.string.home_chip_music, "music", false),
            new Chip(R.string.home_chip_gaming, "gaming", false),
            new Chip(R.string.home_chip_live, "live", false),
            new Chip(R.string.home_chip_news, "news", false),
            new Chip(R.string.home_chip_sports, "sports", false),
            new Chip(R.string.home_chip_podcasts, "podcast", false),
            new Chip(R.string.home_chip_comedy, "comedy", false),
            new Chip(R.string.home_chip_movies, "movies", false),
            new Chip(R.string.home_chip_cooking, "cooking", false),
            new Chip(R.string.home_chip_tech, "technology", false),
            new Chip(R.string.home_chip_learning, "learning", false));

    /** Where a part of the feed comes from. Remembers how far it has been read. */
    private static final class FeedSource {
        @Nullable
        private final List<InfoItem> fixedItems;
        @Nullable
        private final String query;
        private final boolean trending;
        @Nullable
        private String kioskUrl;
        @Nullable
        private Page nextPage;
        private boolean exhausted;

        private FeedSource(@Nullable final List<InfoItem> fixedItems,
                           @Nullable final String query,
                           final boolean trending) {
            this.fixedItems = fixedItems;
            this.query = query;
            this.trending = trending;
        }

        static FeedSource fixed(final List<InfoItem> items) {
            return new FeedSource(items, null, false);
        }

        static FeedSource search(final String query) {
            return new FeedSource(null, query, false);
        }

        static FeedSource trending() {
            return new FeedSource(null, null, true);
        }

        boolean hasMore() {
            return !exhausted && Page.isValid(nextPage);
        }
    }

    /** What the watch history tells about the user's taste. */
    private static final class Profile {
        private final List<List<InfoItem>> relatedBatches = new ArrayList<>();
        private final List<String> topTopics = new ArrayList<>();
        private final List<String> topChannels = new ArrayList<>();
    }

    private static final class WatchedVideo {
        @Nullable
        private final StreamInfo info;
        private final long watchCount;
        private final boolean recent;

        private WatchedVideo(@Nullable final StreamInfo info, final long watchCount,
                             final boolean recent) {
            this.info = info;
            this.watchCount = watchCount;
            this.recent = recent;
        }
    }

    @Nullable
    private View.OnLayoutChangeListener recommendationLayoutChangeListener;

    @State
    int selectedChipIndex = 0;

    private final List<FeedSource> feedSources = new ArrayList<>();
    private final Set<String> shownUrls = new HashSet<>();
    private final Set<String> watchedUrls = new HashSet<>();

    public RecommendedVideosFragment() {
        super(UserAction.SEARCHED);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This makes the adapter use the YouTube-like home video card
        infoListAdapter.setFullWidthGridThumbnails(true);
        serviceId = ServiceHelper.getSelectedServiceId(requireContext());
        setInitialData(serviceId, HOME_PAGE_MARKER, getString(R.string.tab_for_you));
    }

    @Override
    public void onResume() {
        super.onResume();
        final int selectedServiceId = ServiceHelper.getSelectedServiceId(requireContext());
        if (serviceId != selectedServiceId) {
            serviceId = selectedServiceId;
            currentInfo = null;
            currentNextPage = null;
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
        setupChips(rootView);
    }

    @Override
    public void onDestroyView() {
        if (recommendationLayoutChangeListener != null && itemsList != null) {
            itemsList.removeOnLayoutChangeListener(recommendationLayoutChangeListener);
            recommendationLayoutChangeListener = null;
        }
        super.onDestroyView();
    }

    // region Category chips

    private void setupChips(final View rootView) {
        final LinearLayout container = rootView.findViewById(R.id.home_chips);
        if (container == null) {
            return;
        }
        container.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < CHIPS.size(); i++) {
            final int index = i;
            final TextView chip =
                    (TextView) inflater.inflate(R.layout.item_home_chip, container, false);
            chip.setText(CHIPS.get(i).labelRes);
            chip.setSelected(i == selectedChipIndex);
            chip.setOnClickListener(view -> onChipSelected(index));
            container.addView(chip);
        }
    }

    private void onChipSelected(final int index) {
        if (index == selectedChipIndex) {
            return;
        }
        selectedChipIndex = index;

        final View root = getView();
        final LinearLayout container = root == null ? null : root.findViewById(R.id.home_chips);
        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                container.getChildAt(i).setSelected(i == index);
            }
        }
        if (itemsList != null) {
            itemsList.scrollToPosition(0);
        }
        currentInfo = null;
        currentNextPage = null;
        reloadContent();
    }

    // endregion

    // region Loading

    @Override
    protected Single<HomeFeedInfo> loadResult(final boolean forceLoad) {
        feedSources.clear();
        shownUrls.clear();
        watchedUrls.clear();

        final Chip chip = CHIPS.get(Math.max(0, Math.min(selectedChipIndex, CHIPS.size() - 1)));
        if (chip.trending) {
            feedSources.add(FeedSource.trending());
            return fetchFirstPage().map(this::toHomeInfo);
        }
        if (chip.query != null) {
            feedSources.add(FeedSource.search(chip.query));
            return fetchFirstPage().map(this::toHomeInfo);
        }

        return loadProfile().flatMap(profile -> {
            addPersonalizedSources(profile);
            return fetchFirstPage();
        }).map(this::toHomeInfo);
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        return fetchMorePage(0);
    }

    private HomeFeedInfo toHomeInfo(final List<InfoItem> items) {
        return new HomeFeedInfo(serviceId, getString(R.string.tab_for_you), items,
                anySourceHasMore() ? new Page(HOME_PAGE_MARKER) : null);
    }

    private boolean anySourceHasMore() {
        for (final FeedSource source : feedSources) {
            if (source.hasMore()) {
                return true;
            }
        }
        return false;
    }

    private Single<List<InfoItem>> fetchFirstPage() {
        final List<Single<List<InfoItem>>> loads = new ArrayList<>();
        for (final FeedSource source : feedSources) {
            loads.add(fetchFirst(source).subscribeOn(Schedulers.io()));
        }
        return Single.zip(loads, this::mergeBatches);
    }

    private Single<ListExtractor.InfoItemsPage<InfoItem>> fetchMorePage(final int attempt) {
        final List<Single<List<InfoItem>>> loads = new ArrayList<>();
        for (final FeedSource source : feedSources) {
            if (source.hasMore()) {
                loads.add(fetchMore(source).subscribeOn(Schedulers.io()));
            }
        }
        if (loads.isEmpty()) {
            return Single.just(new ListExtractor.InfoItemsPage<>(
                    Collections.<InfoItem>emptyList(), null, Collections.<Throwable>emptyList()));
        }

        return Single.zip(loads, this::mergeBatches).flatMap(items -> {
            if (items.isEmpty() && anySourceHasMore() && attempt < MAX_EMPTY_PAGE_RETRIES) {
                return fetchMorePage(attempt + 1);
            }
            return Single.just(new ListExtractor.InfoItemsPage<>(items,
                    anySourceHasMore() ? new Page(HOME_PAGE_MARKER) : null,
                    Collections.<Throwable>emptyList()));
        });
    }

    private Single<List<InfoItem>> fetchFirst(final FeedSource source) {
        final int service = serviceId;
        final Single<List<InfoItem>> load;
        if (source.fixedItems != null) {
            source.exhausted = true;
            load = Single.just(source.fixedItems);
        } else if (source.trending) {
            load = Single.fromCallable(() -> {
                        final org.schabi.newpipe.extractor.StreamingService streamingService =
                                NewPipe.getService(service);
                        final String kioskId = streamingService.getKioskList()
                                .getDefaultKioskId();
                        return streamingService.getKioskList()
                                .getListLinkHandlerFactoryByType(kioskId)
                                .fromId(kioskId).getUrl();
                    })
                    .flatMap(url -> {
                        source.kioskUrl = url;
                        return ExtractorHelper.getKioskInfo(service, url, false);
                    })
                    .map(info -> {
                        source.nextPage = info.getNextPage();
                        return new ArrayList<InfoItem>(info.getRelatedItems());
                    });
        } else {
            load = ExtractorHelper.searchFor(service, source.query, VIDEO_FILTER, "")
                    .map(info -> {
                        source.nextPage = info.getNextPage();
                        return new ArrayList<InfoItem>(info.getRelatedItems());
                    });
        }
        return load.onErrorReturn(error -> {
            Log.w(TAG, "Could not load a part of the home feed", error);
            source.exhausted = true;
            return Collections.<InfoItem>emptyList();
        });
    }

    private Single<List<InfoItem>> fetchMore(final FeedSource source) {
        final int service = serviceId;
        final Page page = source.nextPage;
        final Single<List<InfoItem>> load;
        if (source.trending) {
            load = ExtractorHelper.getMoreKioskItems(service, source.kioskUrl, page)
                    .map(result -> {
                        source.nextPage = result.getNextPage();
                        return new ArrayList<InfoItem>(result.getItems());
                    });
        } else {
            load = ExtractorHelper.getMoreSearchItems(service, source.query, VIDEO_FILTER, "", page)
                    .map(result -> {
                        source.nextPage = result.getNextPage();
                        return new ArrayList<InfoItem>(result.getItems());
                    });
        }
        return load.onErrorReturn(error -> {
            Log.w(TAG, "Could not load more of the home feed", error);
            source.exhausted = true;
            return Collections.<InfoItem>emptyList();
        });
    }

    /**
     * Interleaves the batches (first item of every batch, then the second one of every batch and
     * so on) so that the feed mixes all the sources, like YouTube does. Skips duplicates, videos
     * the user has already watched, shorts (they have their own tab) and everything that is not
     * a video.
     */
    private List<InfoItem> mergeBatches(final Object[] batches) {
        final List<InfoItem> result = new ArrayList<>();
        int index = 0;
        boolean addedInThisRound;
        do {
            addedInThisRound = false;
            for (final Object batchObject : batches) {
                @SuppressWarnings("unchecked") final List<InfoItem> batch =
                        (List<InfoItem>) batchObject;
                if (index < batch.size()) {
                    addedInThisRound = true;
                    final InfoItem item = batch.get(index);
                    if (isFeedWorthy(item) && shownUrls.add(item.getUrl())) {
                        result.add(item);
                    }
                }
            }
            index++;
        } while (addedInThisRound);
        return result;
    }

    private boolean isFeedWorthy(final InfoItem item) {
        if (!(item instanceof StreamInfoItem)) {
            return false;
        }
        final StreamInfoItem stream = (StreamInfoItem) item;
        if (stream.getDuration() > 0 && stream.getDuration() <= MAX_SHORT_DURATION_SECONDS) {
            return false;
        }
        return !watchedUrls.contains(stream.getUrl());
    }

    // endregion

    // region Personalization

    private void addPersonalizedSources(final Profile profile) {
        for (final List<InfoItem> related : profile.relatedBatches) {
            if (!related.isEmpty()) {
                feedSources.add(FeedSource.fixed(related));
            }
        }
        for (final String topic : profile.topTopics) {
            feedSources.add(FeedSource.search(topic));
        }
        for (final String channel : profile.topChannels) {
            feedSources.add(FeedSource.search(channel));
        }
        feedSources.add(FeedSource.trending());

        if (profile.topTopics.isEmpty() && profile.relatedBatches.isEmpty()) {
            // Nothing is known about the user yet
            for (final String topic : DEFAULT_TOPICS) {
                feedSources.add(FeedSource.search(topic));
            }
        }
    }

    private Single<Profile> loadProfile() {
        final HistoryRecordManager historyRecordManager =
                new HistoryRecordManager(requireContext());

        return historyRecordManager.getStreamHistorySortedById()
                .firstOrError()
                .onErrorReturn(error -> {
                    Log.w(TAG, "Could not read watch history", error);
                    return List.of();
                })
                .flatMap(history -> {
                    final List<StreamHistoryEntry> serviceHistory = new ArrayList<>();
                    for (final StreamHistoryEntry entry : history) {
                        watchedUrls.add(entry.getStreamEntity().getUrl());
                        if (entry.getStreamEntity().getServiceId() == serviceId) {
                            serviceHistory.add(entry);
                        }
                    }

                    final List<StreamHistoryEntry> mostWatched = new ArrayList<>(serviceHistory);
                    mostWatched.sort(Comparator
                            .comparingLong(StreamHistoryEntry::getRepeatCount).reversed()
                            .thenComparing(StreamHistoryEntry::getAccessDate,
                                    Comparator.reverseOrder()));
                    final List<StreamHistoryEntry> mostRecent = new ArrayList<>(serviceHistory);
                    mostRecent.sort(Comparator.comparing(StreamHistoryEntry::getAccessDate,
                            Comparator.reverseOrder()));

                    final Map<String, StreamHistoryEntry> candidates = new LinkedHashMap<>();
                    final Set<String> recentUrls = new HashSet<>();
                    for (int i = 0; i < Math.min(RECENT_VIDEOS_FOR_RELATED, mostRecent.size());
                         i++) {
                        final StreamHistoryEntry entry = mostRecent.get(i);
                        recentUrls.add(entry.getStreamEntity().getUrl());
                        candidates.put(entry.getStreamEntity().getUrl(), entry);
                    }
                    for (int i = 0; i < Math.min(MAX_HISTORY_ITEMS_TO_ANALYZE, mostWatched.size());
                         i++) {
                        final StreamHistoryEntry entry = mostWatched.get(i);
                        candidates.putIfAbsent(entry.getStreamEntity().getUrl(), entry);
                    }

                    if (candidates.isEmpty()) {
                        return Single.just(new Profile());
                    }

                    return Flowable.fromIterable(candidates.values())
                            .flatMapSingle(entry -> ExtractorHelper.getStreamInfo(
                                            entry.getStreamEntity().getServiceId(),
                                            entry.getStreamEntity().getUrl(),
                                            false)
                                    .map(info -> new WatchedVideo(info, entry.getRepeatCount(),
                                            recentUrls.contains(entry.getStreamEntity().getUrl())))
                                    .onErrorReturn(error -> {
                                        Log.w(TAG, "Could not load a watched video", error);
                                        return new WatchedVideo(null, 0, false);
                                    })
                                    .subscribeOn(Schedulers.io()),
                                    false, STREAM_INFO_CONCURRENCY)
                            .toList()
                            .map(this::buildProfile);
                });
    }

    private Profile buildProfile(final List<WatchedVideo> watchedVideos) {
        final Profile profile = new Profile();
        final Map<String, Long> topicWeights = new HashMap<>();
        final Map<String, String> topicNames = new HashMap<>();
        final Map<String, Long> channelWeights = new HashMap<>();
        final Map<String, String> channelNames = new HashMap<>();

        for (final WatchedVideo watched : watchedVideos) {
            final StreamInfo info = watched.info;
            if (info == null) {
                continue;
            }
            final long weight = Math.max(watched.watchCount, 1);
            addWeight(topicWeights, topicNames, info.getCategory(), weight);
            addWeight(channelWeights, channelNames, info.getUploaderName(), weight);

            if (watched.recent && info.getRelatedItems() != null) {
                profile.relatedBatches.add(new ArrayList<>(info.getRelatedItems()));
            }
        }

        profile.topTopics.addAll(topKeys(topicWeights, topicNames, TOP_TOPICS));
        profile.topChannels.addAll(topKeys(channelWeights, channelNames, TOP_CHANNELS));
        return profile;
    }

    private static void addWeight(final Map<String, Long> weights,
                                  final Map<String, String> names,
                                  @Nullable final String rawName,
                                  final long weight) {
        if (TextUtils.isEmpty(rawName) || rawName.trim().isEmpty()) {
            return;
        }
        final String name = rawName.trim();
        final String key = name.toLowerCase(Locale.ROOT);
        names.putIfAbsent(key, name);
        weights.merge(key, weight, Long::sum);
    }

    private static List<String> topKeys(final Map<String, Long> weights,
                                        final Map<String, String> names,
                                        final int count) {
        final List<Map.Entry<String, Long>> entries = new ArrayList<>(weights.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, entries.size()); i++) {
            result.add(names.get(entries.get(i).getKey()));
        }
        return result;
    }

    // endregion

    @Override
    public void handleResult(@NonNull final HomeFeedInfo result) {
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
}
