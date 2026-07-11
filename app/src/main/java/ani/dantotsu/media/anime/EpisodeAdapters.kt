package ani.dantotsu.media.anime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.lifecycle.coroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.updateProgress
import ani.dantotsu.databinding.ItemEpisodeCompactBinding
import ani.dantotsu.databinding.ItemEpisodeGridBinding
import ani.dantotsu.databinding.ItemEpisodeListBinding
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaNameAdapter
import ani.dantotsu.setAnimation
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.util.customAlertDialog
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.util.SizeFormatter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.NumberPicker
import ani.dantotsu.currContext


fun handleProgress(cont: LinearLayout, bar: View, empty: View, mediaId: Int, ep: String) {
    val curr = PrefManager.getNullableCustomVal("${mediaId}_${ep}", null, Long::class.java)
    val max = PrefManager.getNullableCustomVal("${mediaId}_${ep}_max", null, Long::class.java)
    if (curr != null && max != null) {
        cont.visibility = View.VISIBLE
        val div = curr.toFloat() / max.toFloat()
        val barParams = bar.layoutParams as LinearLayout.LayoutParams
        barParams.weight = div
        bar.layoutParams = barParams
        val params = empty.layoutParams as LinearLayout.LayoutParams
        params.weight = 1 - div
        empty.layoutParams = params
    } else {
        cont.visibility = View.GONE
    }
}

@OptIn(UnstableApi::class)
class EpisodeAdapter(
    private var type: Int,
    private val media: Media,
    private val fragment: AnimeWatchFragment,
    var arr: List<Episode> = arrayListOf(),
    var offlineMode: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val context = fragment.requireContext()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return (when (viewType) {
            0 -> EpisodeListViewHolder(
                ItemEpisodeListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            1 -> EpisodeGridViewHolder(
                ItemEpisodeGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            2 -> EpisodeCompactViewHolder(
                ItemEpisodeCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException()
        })
    }

    override fun getItemViewType(position: Int): Int {
        return type
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(holder, position, false)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            bindViewHolder(holder, position, false)
        } else {
            bindViewHolder(holder, position, payloads.contains("metadata"))
        }
    }

    private fun bindViewHolder(holder: RecyclerView.ViewHolder, position: Int, metadataOnly: Boolean) {
        val ep = arr[position]
        val title = if (!ep.title.isNullOrEmpty() && ep.title != "null") {
            ep.title?.let { MediaNameAdapter.removeEpisodeNumber(it) }
        } else {
            ep.number
        } ?: ""

        if (position == 0) {
            holder.itemView.nextFocusUpId = R.id.mediaSourceNameContainer
        }
        if (position == arr.size - 1) {
            holder.itemView.nextFocusDownId = R.id.ScrollTop
        }

        when (holder) {
            is EpisodeListViewHolder -> {
                val binding = holder.binding
                if (!metadataOnly) {
                    setAnimation(fragment.requireContext(), holder.binding.root)

                    val thumb = ep.thumb?.let {
                        if (it.url.isNotEmpty()) {
                            if (it.url.startsWith("content://") || it.url.startsWith("file://")) {
                                it.url
                            } else {
                                GlideUrl(it.url) { it.headers }
                            }
                        } else null
                    }
                    Glide.with(binding.itemMediaImage).load(thumb ?: media.cover).override(400, 0)
                        .diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.itemMediaImage)
                }
                binding.itemEpisodeDesc.isVisible = !ep.desc.isNullOrBlank()
                binding.itemEpisodeDesc.text = ep.desc ?: ""
                holder.bind(ep.number, ep.downloadProgress, ep.desc)
                binding.itemEpisodeNumber.text = ep.number
                binding.itemEpisodeTitle.text = if (ep.number == title) "Episode $title" else title

                if (ep.filler) {
                    binding.itemEpisodeFiller.visibility = View.VISIBLE
                    binding.itemEpisodeFillerView.visibility = View.VISIBLE
                } else {
                    binding.itemEpisodeFiller.visibility = View.GONE
                    binding.itemEpisodeFillerView.visibility = View.GONE
                }

                val ratingStr = ep.rating
                if (ratingStr != null) {
                    binding.itemEpisodeRating.visibility = View.VISIBLE
                    binding.itemEpisodeRating.text = "★ $ratingStr"
                    val ratingFloat = ratingStr.toFloatOrNull()
                    if (ratingFloat != null && ratingFloat > 7.9f) {
                        binding.itemEpisodeSparkle1.visibility = View.VISIBLE
                        binding.itemEpisodeSparkle2.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeSparkle1.visibility = View.GONE
                        binding.itemEpisodeSparkle2.visibility = View.GONE
                    }
                } else {
                    binding.itemEpisodeRating.visibility = View.GONE
                    binding.itemEpisodeSparkle1.visibility = View.GONE
                    binding.itemEpisodeSparkle2.visibility = View.GONE
                }

                if (ep.date != null) {
                    binding.itemEpisodeDate.visibility = View.VISIBLE
                    binding.itemEpisodeDate.text = ep.date
                } else {
                    binding.itemEpisodeDate.visibility = View.GONE
                }

                if (media.userProgress != null) {
                    if ((ep.number.toFloatOrNull() ?: 9999f) <= media.userProgress!!.toFloat()) {
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                        binding.itemEpisodeViewed.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeViewed.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            updateProgress(media, ep.number)
                            true
                        }
                    }
                } else {
                    binding.itemEpisodeViewedCover.visibility = View.GONE
                    binding.itemEpisodeViewed.visibility = View.GONE
                }

                handleProgress(
                    binding.itemMediaProgressCont,
                    binding.itemMediaProgress,
                    binding.itemMediaProgressEmpty,
                    media.id,
                    ep.number
                )
            }

            is EpisodeGridViewHolder -> {
                val binding = holder.binding
                if (!metadataOnly) {
                    setAnimation(fragment.requireContext(), holder.binding.root)

                    val thumb = ep.thumb?.let {
                        if (it.url.isNotEmpty()) {
                            if (it.url.startsWith("content://") || it.url.startsWith("file://")) {
                                it.url
                            } else {
                                GlideUrl(it.url) { it.headers }
                            }
                        } else null
                    }
                    Glide.with(binding.itemMediaImage).load(thumb ?: media.cover).override(400, 0)
                        .diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.itemMediaImage)
                }

                binding.itemEpisodeNumber.text = ep.number
                binding.itemEpisodeTitle.text = title

                val ratingStr = ep.rating
                if (ratingStr != null) {
                    binding.itemEpisodeRating.visibility = View.VISIBLE
                    binding.itemEpisodeRating.text = "★ $ratingStr"
                    val ratingFloat = ratingStr.toFloatOrNull()
                    if (ratingFloat != null && ratingFloat > 7.9f) {
                        binding.itemEpisodeSparkle1.visibility = View.VISIBLE
                        binding.itemEpisodeSparkle2.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeSparkle1.visibility = View.GONE
                        binding.itemEpisodeSparkle2.visibility = View.GONE
                    }
                } else {
                    binding.itemEpisodeRating.visibility = View.GONE
                    binding.itemEpisodeSparkle1.visibility = View.GONE
                    binding.itemEpisodeSparkle2.visibility = View.GONE
                }

                if (ep.date != null) {
                    binding.itemEpisodeDate.visibility = View.VISIBLE
                    binding.itemEpisodeDate.text = ep.date
                } else {
                    binding.itemEpisodeDate.visibility = View.GONE
                }

                if (ep.filler) {
                    binding.itemEpisodeFiller.visibility = View.VISIBLE
                    binding.itemEpisodeFillerView.visibility = View.VISIBLE
                } else {
                    binding.itemEpisodeFiller.visibility = View.GONE
                    binding.itemEpisodeFillerView.visibility = View.GONE
                }
                if (media.userProgress != null) {
                    if ((ep.number.toFloatOrNull() ?: 9999f) <= media.userProgress!!.toFloat()) {
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                        binding.itemEpisodeViewed.visibility = View.VISIBLE
                    } else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeViewed.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            updateProgress(media, ep.number)
                            true
                        }
                    }
                } else {
                    binding.itemEpisodeViewedCover.visibility = View.GONE
                    binding.itemEpisodeViewed.visibility = View.GONE
                }
                handleProgress(
                    binding.itemMediaProgressCont,
                    binding.itemMediaProgress,
                    binding.itemMediaProgressEmpty,
                    media.id,
                    ep.number
                )
            }

            is EpisodeCompactViewHolder -> {
                val binding = holder.binding
                if (!metadataOnly) {
                    setAnimation(fragment.requireContext(), holder.binding.root)
                }
                binding.itemEpisodeNumber.text = ep.number
                binding.itemEpisodeFillerView.isVisible = ep.filler
                if (media.userProgress != null) {
                    if ((ep.number.toFloatOrNull() ?: 9999f) <= media.userProgress!!.toFloat())
                        binding.itemEpisodeViewedCover.visibility = View.VISIBLE
                    else {
                        binding.itemEpisodeViewedCover.visibility = View.GONE
                        binding.itemEpisodeCont.setOnLongClickListener {
                            updateProgress(media, ep.number)
                            true
                        }
                    }
                }
                handleProgress(
                    binding.itemMediaProgressCont,
                    binding.itemMediaProgress,
                    binding.itemMediaProgressEmpty,
                    media.id,
                    ep.number
                )
            }
        }
    }

    override fun getItemCount(): Int = arr.size
    private val downloadedEpisodes = mutableSetOf<String>()

    fun startDownload(episodeNumber: String) {
        if (downloadedEpisodes.contains(episodeNumber))
                return
        val position = arr.indexOfFirst { it.number == episodeNumber }
        if (position != -1) {
            arr[position].downloadProgress = ""
            notifyItemChanged(position)
        }
    }

    fun addToDownloadedEpisodes(episodeNumber: String, size: Double) {
        downloadedEpisodes.add(episodeNumber)
        // Find the position of the chapter and notify only that item
        val position = arr.indexOfFirst { it.number == episodeNumber }
        if (position != -1) {
            arr[position].downloadProgress = "Downloaded" + ": (${"%.1f".format(size)} MB)"
            notifyItemChanged(position)
        }
    }

    fun deleteDownload(episodeNumber: String) {
        downloadedEpisodes.remove(episodeNumber)
        // Find the position of the chapter and notify only that item
        val position = arr.indexOfFirst { it.number == episodeNumber }
        if (position != -1) {
            arr[position].downloadProgress = null
            notifyItemChanged(position)
        }
    }

    fun purgeDownload(episodeNumber: String) {
        downloadedEpisodes.remove(episodeNumber)
        // Find the position of the chapter and notify only that item
        val position = arr.indexOfFirst { it.number == episodeNumber }
        if (position != -1) {
            arr[position].downloadProgress = "Failed"
            notifyItemChanged(position)
        }
    }

    fun updateDownloadProgress(episodeNumber: String, progress: Int) {
        updateDownloadProgress(episodeNumber, progress, -1L, -1L)
    }

    fun updateDownloadProgress(
        episodeNumber: String,
        progress: Int,
        downloadedBytes: Long,
        estimatedTotalBytes: Long
    ) {
        // Find the position of the chapter and notify only that item
        val position = arr.indexOfFirst { it.number == episodeNumber }
        if (position != -1) {
            arr[position].downloadProgress = buildDownloadProgressText(
                progress,
                downloadedBytes,
                estimatedTotalBytes
            )

            notifyItemChanged(position)
        }
    }

    private fun buildDownloadProgressText(
        progress: Int,
        downloadedBytes: Long,
        estimatedTotalBytes: Long
    ): String {
        val hasDownloaded = downloadedBytes > 0L
        val hasEstimatedTotal = estimatedTotalBytes > 0L
        return if (hasDownloaded && hasEstimatedTotal) {
            "Downloading: $progress% (${SizeFormatter.formatBytes(downloadedBytes)} / ${SizeFormatter.formatBytes(estimatedTotalBytes)} est.)"
        } else if (hasEstimatedTotal) {
            "Downloading: $progress% (~${SizeFormatter.formatBytes(estimatedTotalBytes)} est.)"
        } else {
            "Downloading: $progress%"
        }
    }


    inner class EpisodeCompactViewHolder(val binding: ItemEpisodeCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.isFocusable = true
            FocusEffectUtil.applyFocusListener(itemView)
            itemView.setOnClickListener {
                if (bindingAdapterPosition < arr.size && bindingAdapterPosition >= 0)
                    fragment.onEpisodeClick(arr[bindingAdapterPosition].number)
            }
        }
    }

    inner class EpisodeGridViewHolder(val binding: ItemEpisodeGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.isFocusable = true
            FocusEffectUtil.applyFocusListener(itemView)
            itemView.setOnClickListener {
                if (bindingAdapterPosition < arr.size && bindingAdapterPosition >= 0)
                    fragment.onEpisodeClick(arr[bindingAdapterPosition].number)
            }
        }
    }

    inner class EpisodeListViewHolder(val binding: ItemEpisodeListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.isFocusable = true
            FocusEffectUtil.applyFocusListener(itemView)
            itemView.setOnClickListener {
                if (bindingAdapterPosition < arr.size && bindingAdapterPosition >= 0)
                    fragment.onEpisodeClick(arr[bindingAdapterPosition].number)
            }
            binding.itemDownload.visibility = View.GONE
            binding.itemDownload.setOnClickListener {}
            binding.itemDownload.setOnLongClickListener { true }
            binding.itemEpisodeDesc.setOnClickListener {
                if (binding.itemEpisodeDesc.maxLines == 3)
                    binding.itemEpisodeDesc.maxLines = 100
                else
                    binding.itemEpisodeDesc.maxLines = 3
            }
        }

        fun bind(episodeNumber: String, progress: String?, desc: String?) {
            binding.itemDownload.visibility = View.GONE
            binding.itemDownloadStatus.visibility = View.GONE
            binding.itemEpisodeDesc.visibility =
                if (desc != null && desc.trim(' ') != "") View.VISIBLE else View.GONE
        }
    }

    fun updateType(t: Int) {
        type = t
    }
}
