package org.schabi.newpipe.fragments.shorts

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentShortsBinding
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper

/**
 * Instagram-Reels-like vertical feed: swipe up/down to move between short videos, each one
 * auto-plays in a loop. Tap to pause/resume, use the side buttons to mute or open the video in
 * the regular player.
 */
class ShortsFragment : Fragment() {

    private var binding: FragmentShortsBinding? = null
    private val disposables = CompositeDisposable()
    private var resolveDisposable: Disposable? = null

    private var player: ExoPlayer? = null
    private var dataSource: PlayerDataSource? = null
    private var activeHolder: ShortsAdapter.ViewHolder? = null

    private var serviceId = ServiceList.YouTube.serviceId
    private var nextPage: Page? = null
    private var isLoadingMore = false
    private var endReached = false
    private val seenUrls = HashSet<String>()

    private var currentPosition = NO_POSITION
    private var loadedPosition = NO_POSITION
    private var userPaused = false
    private var muted = false

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    // The adapter outlives the view so that the feed survives navigating to a channel and back.
    private val adapter = ShortsAdapter(
        isMuted = { muted },
        onTogglePlayback = ::togglePlayback,
        onToggleMute = ::toggleMute,
        onOpenInPlayer = ::openInPlayer,
        onOpenChannel = ::openChannel,
        onHolderAttached = ::onHolderAttached,
        onHolderDetached = ::onHolderDetached
    )

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            onPageChanged(position)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            activeHolder?.setBuffering(playbackState == Player.STATE_BUFFERING)
        }

        override fun onRenderedFirstFrame() {
            activeHolder?.hideThumbnail()
        }

        override fun onPlayerError(error: PlaybackException) {
            showCannotPlayAndSkip()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = FragmentShortsBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        serviceId = ServiceHelper.getSelectedServiceId(requireContext())

        createPlayer()

        b.shortsPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        b.shortsPager.adapter = adapter
        b.shortsPager.registerOnPageChangeCallback(pageCallback)
        b.shortsRetryButton.setOnClickListener { loadFirstPage() }

        if (adapter.itemCount > 0) {
            // Coming back to an already loaded feed
            showContent()
            val position = currentPosition.coerceAtLeast(0)
            currentPosition = NO_POSITION
            b.shortsPager.setCurrentItem(position, false)
            b.shortsPager.post { onPageChanged(position) }
        } else {
            loadFirstPage()
        }
    }

    private fun createPlayer() {
        val context = requireContext()
        dataSource = PlayerDataSource(context, DefaultBandwidthMeter.Builder(context).build())
        player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            addListener(playerListener)
        }
    }

    // region Feed loading

    private fun loadFirstPage() {
        showLoading()
        endReached = false
        nextPage = null
        disposables.add(
            ExtractorHelper.searchFor(serviceId, QUERY, FILTERS, "")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { info ->
                        nextPage = info.nextPage
                        endReached = !Page.isValid(nextPage)
                        addItems(info.relatedItems)
                        if (adapter.itemCount == 0) {
                            if (endReached) showMessage(getString(R.string.shorts_empty)) else loadMore()
                        }
                    },
                    { showMessage(getString(R.string.shorts_load_failed)) }
                )
        )
    }

    private fun loadMore() {
        val page = nextPage
        if (isLoadingMore || endReached || !Page.isValid(page)) return
        isLoadingMore = true
        disposables.add(
            ExtractorHelper.getMoreSearchItems(serviceId, QUERY, FILTERS, "", page)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        isLoadingMore = false
                        nextPage = result.nextPage
                        endReached = !Page.isValid(nextPage)
                        addItems(result.items)
                        if (adapter.itemCount == 0) {
                            if (endReached) showMessage(getString(R.string.shorts_empty)) else loadMore()
                        }
                    },
                    {
                        isLoadingMore = false
                        if (adapter.itemCount == 0) {
                            showMessage(getString(R.string.shorts_load_failed))
                        }
                    }
                )
        )
    }

    private fun addItems(items: List<InfoItem>) {
        val shorts = items.filterIsInstance<StreamInfoItem>().filter {
            it.streamType == StreamType.VIDEO_STREAM &&
                it.duration in 1..MAX_SHORT_DURATION_SECONDS &&
                seenUrls.add(it.url)
        }
        adapter.addAll(shorts)
        if (adapter.itemCount > 0) {
            showContent()
            if (currentPosition == NO_POSITION) {
                binding?.shortsPager?.post { onPageChanged(0) }
            }
        }
    }

    // endregion

    // region Paging and playback

    private fun onPageChanged(position: Int) {
        if (position == currentPosition || adapter.itemAt(position) == null) return
        currentPosition = position
        loadedPosition = NO_POSITION
        userPaused = false
        resolveDisposable?.dispose()
        player?.apply {
            stop()
            clearMediaItems()
        }
        findHolder(position)?.let { bindPlayerTo(it) }

        // Warm up the stream info of the next Short and fetch more once we get close to the end
        adapter.itemAt(position + 1)?.let { next ->
            disposables.add(
                ExtractorHelper.getStreamInfo(serviceId, next.url, false)
                    .subscribeOn(Schedulers.io())
                    .subscribe({}, {})
            )
        }
        if (position >= adapter.itemCount - PREFETCH_DISTANCE) {
            loadMore()
        }
    }

    private fun findHolder(position: Int): ShortsAdapter.ViewHolder? {
        val recycler = binding?.shortsPager?.getChildAt(0) as? RecyclerView
        return recycler?.findViewHolderForAdapterPosition(position) as? ShortsAdapter.ViewHolder
    }

    private fun onHolderAttached(holder: ShortsAdapter.ViewHolder) {
        if (holder.bindingAdapterPosition == currentPosition) {
            bindPlayerTo(holder)
        }
    }

    private fun onHolderDetached(holder: ShortsAdapter.ViewHolder) {
        if (holder === activeHolder) {
            holder.playerView.player = null
            activeHolder = null
        }
    }

    private fun bindPlayerTo(holder: ShortsAdapter.ViewHolder) {
        val exo = player ?: return
        if (activeHolder !== holder) {
            activeHolder?.let {
                it.playerView.player = null
                it.showThumbnail()
                it.setBuffering(false)
                it.setPaused(false)
            }
            activeHolder = holder
            holder.playerView.player = exo
        }
        holder.setMuted(muted)
        holder.setPaused(userPaused)
        if (loadedPosition != currentPosition) {
            holder.showThumbnail()
            startStream(currentPosition)
        } else {
            holder.setBuffering(exo.playbackState == Player.STATE_BUFFERING)
            if (exo.playbackState == Player.STATE_READY) holder.hideThumbnail()
        }
    }

    private fun startStream(position: Int) {
        val item = adapter.itemAt(position) ?: return
        loadedPosition = position
        activeHolder?.setBuffering(true)
        resolveDisposable?.dispose()
        resolveDisposable = ExtractorHelper.getStreamInfo(serviceId, item.url, false)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { info ->
                    if (position != currentPosition) return@subscribe
                    val source = buildMediaSource(info)
                    val exo = player
                    if (source == null || exo == null) {
                        showCannotPlayAndSkip()
                    } else {
                        exo.setMediaSource(source)
                        exo.prepare()
                        exo.playWhenReady = !userPaused
                    }
                },
                { showCannotPlayAndSkip() }
            )
    }

    /**
     * Prefers a progressive stream that already contains audio and video. Falls back to merging
     * the best video-only and audio streams.
     */
    private fun buildMediaSource(info: StreamInfo): MediaSource? {
        val source = dataSource ?: return null
        val factory = if (info.serviceId == ServiceList.YouTube.serviceId) {
            source.youtubeProgressiveMediaSourceFactory
        } else {
            source.progressiveMediaSourceFactory
        }

        val muxed = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.height }
        if (muxed != null) {
            return factory.createMediaSource(MediaItem.fromUri(muxed.content))
        }

        val video = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .filter { it.height <= MAX_MERGED_HEIGHT }
            .maxByOrNull { it.height }
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.averageBitrate }
        if (video != null && audio != null) {
            return MergingMediaSource(
                factory.createMediaSource(MediaItem.fromUri(video.content)),
                factory.createMediaSource(MediaItem.fromUri(audio.content))
            )
        }
        return null
    }

    private fun showCannotPlayAndSkip() {
        context?.let { Toast.makeText(it, R.string.shorts_cannot_play, Toast.LENGTH_SHORT).show() }
        val pager = binding?.shortsPager ?: return
        if (currentPosition + 1 < adapter.itemCount) {
            pager.setCurrentItem(currentPosition + 1, true)
        }
    }

    private fun togglePlayback() {
        val exo = player ?: return
        userPaused = !userPaused
        exo.playWhenReady = !userPaused
        activeHolder?.setPaused(userPaused)
    }

    private fun toggleMute() {
        muted = !muted
        player?.volume = if (muted) 0f else 1f
        activeHolder?.setMuted(muted)
    }

    private fun updateProgress() {
        val exo = player ?: return
        val duration = exo.duration
        if (duration > 0) {
            activeHolder?.setProgress(exo.currentPosition.toFloat() / duration)
        }
    }

    // endregion

    // region Navigation

    private fun openInPlayer(item: StreamInfoItem) {
        player?.pause()
        userPaused = true
        activeHolder?.setPaused(true)
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            parentFragmentManager,
            item.serviceId,
            item.url,
            item.name,
            null,
            false
        )
    }

    private fun openChannel(item: StreamInfoItem) {
        val url = item.uploaderUrl ?: return
        NavigationHelper.openChannelFragment(
            parentFragmentManager,
            item.serviceId,
            url,
            item.uploaderName.orEmpty()
        )
    }

    // endregion

    // region View state

    private fun showLoading() {
        binding?.apply {
            shortsLoading.visibility = View.VISIBLE
            shortsMessageView.visibility = View.GONE
        }
    }

    private fun showContent() {
        binding?.apply {
            shortsLoading.visibility = View.GONE
            shortsMessageView.visibility = View.GONE
        }
    }

    private fun showMessage(message: String) {
        binding?.apply {
            shortsLoading.visibility = View.GONE
            shortsMessageText.text = message
            shortsMessageView.visibility = View.VISIBLE
        }
    }

    // endregion

    // region Lifecycle

    override fun onResume() {
        super.onResume()
        if (!userPaused) player?.play()
        handler.post(progressRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(progressRunnable)
        player?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(progressRunnable)
        resolveDisposable?.dispose()
        disposables.clear()
        isLoadingMore = false
        binding?.shortsPager?.unregisterOnPageChangeCallback(pageCallback)
        binding?.shortsPager?.adapter = null
        activeHolder?.playerView?.player = null
        activeHolder = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
        dataSource = null
        loadedPosition = NO_POSITION
        binding = null
        super.onDestroyView()
    }

    // endregion

    private companion object {
        const val QUERY = "#shorts"
        val FILTERS = listOf("videos")
        const val MAX_SHORT_DURATION_SECONDS = 180L
        const val MAX_MERGED_HEIGHT = 1080
        const val PREFETCH_DISTANCE = 3
        const val PROGRESS_INTERVAL_MS = 200L
        const val NO_POSITION = -1
    }
}
