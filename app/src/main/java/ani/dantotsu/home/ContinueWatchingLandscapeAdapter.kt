package ani.dantotsu.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.LogoApi
import ani.dantotsu.connections.anizip.AniZip
import ani.dantotsu.loadImage
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.BitmapUtil
import ani.dantotsu.util.FocusEffectUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContinueWatchingLandscapeAdapter(
    private val items: MutableList<Media>,
    private val onItemClick: (Media) -> Unit
) : RecyclerView.Adapter<ContinueWatchingLandscapeAdapter.ViewHolder>() {
    private val gradientJobs = mutableMapOf<Int, Job>()
    private val coverGradientCache = mutableMapOf<Int, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching_landscape, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = items[position]
        val ctx = holder.itemView.context

        holder.card.radius =
            PrefManager.getVal<Int>(PrefName.ContinueWatchingCardRoundness).toFloat()

        CoroutineScope(Dispatchers.IO).launch {
            val anizipUrl = AniZip.getBackdropUrl(media.id) ?: media.cover
            withContext(Dispatchers.Main) {
                if (!anizipUrl.isNullOrBlank()) {
                    holder.image.loadImage(anizipUrl)
                }
            }
        }

        loadGradientOverlay(holder.gradientOverlay, media, position)

        holder.clearlogo.visibility = View.GONE
        holder.overlayTitle.visibility = View.GONE
        CoroutineScope(Dispatchers.Main).launch {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                holder.clearlogo.visibility = View.VISIBLE
                holder.clearlogo.loadImage(logoUrl)
                holder.overlayTitle.visibility = View.GONE
            } else {
                holder.clearlogo.visibility = View.GONE
                holder.overlayTitle.visibility = View.VISIBLE
                holder.overlayTitle.text = media.userPreferredName ?: media.name
            }
        }

        val episodes = media.anime?.totalEpisodes
        val progress = media.userProgress ?: 0
        val isReleasing = media.status == ctx.getString(R.string.status_releasing)

        val subtitle = buildString {
            if (episodes != null) {
                append("S1 ")
            }
            if (progress > 0) {
                append("E$progress")
            }
            val nextAiring = media.anime?.nextAiringEpisodeTime
            if (nextAiring != null && isReleasing) {
                val timeUntilAiring = nextAiring - System.currentTimeMillis() / 1000
                if (timeUntilAiring > 0 && timeUntilAiring < 86400) {
                    val minutes = (timeUntilAiring / 60).toInt()
                    append(" · ${minutes}min left")
                }
            }
        }
        holder.subtitle.text = subtitle

        if (episodes != null && episodes > 0) {
            holder.progress.max = episodes
            holder.progress.progress = progress
        } else {
            holder.progress.max = 100
            holder.progress.progress = 0
        }

        holder.ongoing.isVisible = isReleasing

        holder.itemView.setOnClickListener { onItemClick(media) }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false
        FocusEffectUtil.applyFocusListener(holder.itemView)
        holder.itemView.alpha = 0.85f
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().alpha(1f).setDuration(200).start()
            } else {
                v.animate().alpha(0.85f).setDuration(200).start()
            }
        }
    }

    override fun getItemCount() = items.size

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        gradientJobs.values.forEach { it.cancel() }
        gradientJobs.clear()
    }

    private fun loadGradientOverlay(view: View, media: Media, position: Int) {
        gradientJobs[position]?.cancel()
        val cached = coverGradientCache[media.id]
        if (cached != null) {
            setGradient(view, cached)
            return
        }
        gradientJobs[position] = CoroutineScope(Dispatchers.IO).launch {
            val bitmap = BitmapUtil.downloadImageAsBitmap(media.cover ?: return@launch) ?: return@launch
            val color = averageColor(bitmap)
            coverGradientCache[media.id] = color
            withContext(Dispatchers.Main) {
                setGradient(view, color)
            }
        }
    }

    private fun setGradient(view: View, color: Int) {
        val intensity = PrefManager.getVal<Float>(PrefName.CardGradientIntensity)
        if (intensity <= 0f) {
            view.background = null
            return
        }
        val baseAlpha = 180
        val endAlpha = 200
        val startColor = Color.argb((baseAlpha * intensity).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
        val endColor = Color.argb((endAlpha * intensity).toInt().coerceIn(0, 255), 0, 0, 0)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(endColor, startColor)
        )
        view.background = gradient
    }

    private fun averageColor(bitmap: android.graphics.Bitmap): Int {
        val sample = android.graphics.Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        var r = 0L; var g = 0L; var b = 0L
        val pixels = IntArray(1024)
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        for (pixel in pixels) {
            r += Color.red(pixel)
            g += Color.green(pixel)
            b += Color.blue(pixel)
        }
        sample.recycle()
        val count = pixels.size
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cwCard)
        val image: ShapeableImageView = view.findViewById(R.id.cwImage)
        val gradientOverlay: View = view.findViewById(R.id.cwGradientOverlay)
        val clearlogo: ImageView = view.findViewById(R.id.cwClearlogo)
        val overlayTitle: TextView = view.findViewById(R.id.cwOverlayTitle)
        val title: TextView = view.findViewById(R.id.cwTitle)
        val subtitle: TextView = view.findViewById(R.id.cwSubtitle)
        val progress: ProgressBar = view.findViewById(R.id.cwProgress)
        val ongoing: View = view.findViewById(R.id.cwOngoing)
    }
}