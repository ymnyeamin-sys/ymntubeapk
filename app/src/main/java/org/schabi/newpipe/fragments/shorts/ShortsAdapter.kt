package org.schabi.newpipe.fragments.shorts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ui.PlayerView
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ItemShortBinding
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.CoilHelper

/**
 * One full-screen page per Short. The video surface ([PlayerView]) of the page that is currently
 * shown is driven by [ShortsFragment], which owns the single shared player.
 */
class ShortsAdapter(
    private val isMuted: () -> Boolean,
    private val onTogglePlayback: () -> Unit,
    private val onToggleMute: () -> Unit,
    private val onOpenInPlayer: (StreamInfoItem) -> Unit,
    private val onOpenChannel: (StreamInfoItem) -> Unit,
    private val onHolderAttached: (ViewHolder) -> Unit,
    private val onHolderDetached: (ViewHolder) -> Unit
) : RecyclerView.Adapter<ShortsAdapter.ViewHolder>() {

    private val items = ArrayList<StreamInfoItem>()

    fun itemAt(position: Int): StreamInfoItem? = items.getOrNull(position)

    fun addAll(newItems: List<StreamInfoItem>) {
        if (newItems.isEmpty()) return
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.binding.shortTitle.text = item.name
        holder.binding.shortChannel.text = item.uploaderName.orEmpty()
        holder.binding.shortViews.text =
            if (item.viewCount >= 0) Localization.shortViewCount(context, item.viewCount) else ""
        CoilHelper.loadThumbnail(holder.binding.shortThumbnail, item.thumbnails)

        holder.showThumbnail()
        holder.setPaused(false)
        holder.setProgress(0f)
        holder.setMuted(isMuted())

        holder.binding.root.setOnClickListener { onTogglePlayback() }
        holder.binding.shortMuteButton.setOnClickListener { onToggleMute() }
        holder.binding.shortOpenButton.setOnClickListener { onOpenInPlayer(item) }
        holder.binding.shortChannel.setOnClickListener { onOpenChannel(item) }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        onHolderAttached(holder)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        onHolderDetached(holder)
    }

    class ViewHolder(val binding: ItemShortBinding) : RecyclerView.ViewHolder(binding.root) {
        val playerView: PlayerView get() = binding.shortPlayerView

        fun showThumbnail() {
            binding.shortThumbnail.visibility = View.VISIBLE
        }

        fun hideThumbnail() {
            binding.shortThumbnail.visibility = View.GONE
        }

        fun setBuffering(buffering: Boolean) {
            binding.shortBuffering.visibility =
                if (buffering) View.VISIBLE else View.GONE
        }

        fun setPaused(paused: Boolean) {
            binding.shortPauseIcon.visibility =
                if (paused) View.VISIBLE else View.GONE
        }

        fun setMuted(muted: Boolean) {
            binding.shortMuteButton.setImageResource(
                if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
            )
        }

        /** @param fraction playback position from 0 to 1 */
        fun setProgress(fraction: Float) {
            binding.shortProgress.progress = (fraction.coerceIn(0f, 1f) * PROGRESS_MAX).toInt()
        }

        private companion object {
            const val PROGRESS_MAX = 1000
        }
    }
}
