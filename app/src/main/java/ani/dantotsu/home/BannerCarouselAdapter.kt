package ani.dantotsu.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.LogoApi
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.loadImage
import ani.dantotsu.media.Media
import ani.dantotsu.util.FocusEffectUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class BannerCarouselAdapter(
    private val items: List<Media>,
    private val scope: CoroutineScope,
    private val onItemClick: (Media) -> Unit
) : RecyclerView.Adapter<BannerCarouselAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_banner_carousel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = items[position]
        val ctx = holder.itemView.context

        // --- Banner images (two-layer to avoid black bars) ---
        val bannerUrl = media.banner
        if (!bannerUrl.isNullOrBlank()) {
            holder.bannerBg.loadImage(bannerUrl)
            holder.bannerImage.loadImage(bannerUrl)
        } else if (!media.cover.isNullOrBlank()) {
            holder.bannerBg.loadImage(media.cover)
            holder.bannerImage.loadImage(media.cover)
        }

        // --- Clearlogo (from LogoApi) / Title fallback ---
        holder.title.text = media.userPreferredName ?: media.name
        holder.title.isVisible = true
        holder.clearlogo.isVisible = false
        scope.launch(Dispatchers.Main) {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                holder.clearlogo.isVisible = true
                holder.title.isVisible = false
                holder.clearlogo.loadImage(logoUrl)
            }
        }

        // --- Format tag ---
        val formatText = media.format?.replace("_", " ")?.let { fmt ->
            when {
                fmt.equals("TV", true) -> "TV Series"
                fmt.equals("TV_SHORT", true) -> "TV Short"
                else -> fmt
            }
        }
        if (!formatText.isNullOrBlank()) {
            holder.formatTag.text = formatText
            holder.formatTag.isVisible = true
        } else {
            holder.formatTag.isVisible = false
        }

        // --- Status tag ---
        val statusText = media.status?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() }
        if (!statusText.isNullOrBlank()) {
            holder.statusTag.text = statusText
            holder.statusTag.isVisible = true
        } else {
            holder.statusTag.isVisible = false
        }

        // --- Season tag ---
        val season = media.anime?.season?.lowercase()
        val year = media.anime?.seasonYear
        val seasonText = if (season != null && year != null) "$season $year" else null
        if (seasonText != null) {
            holder.seasonTag.text = seasonText
            holder.seasonTag.isVisible = true
        } else {
            holder.seasonTag.isVisible = false
        }

        // --- Score tag ---
        val score = media.meanScore
        if (score != null) {
            holder.scoreTag.text = "$score%"
            holder.scoreTag.isVisible = true
        } else {
            holder.scoreTag.isVisible = false
        }

        // --- Description ---
        val desc = media.description
            ?.replace(Regex("<.*?>"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
        if (!desc.isNullOrBlank()) {
            holder.description.text = desc
            holder.description.isVisible = true
        } else {
            holder.description.isVisible = false
        }

        // --- Genre chips ---
        holder.genresRow.removeAllViews()
        if (media.genres.isNotEmpty()) {
            val density = ctx.resources.displayMetrics.density
            for (genre in media.genres.take(4)) {
                val chip = TextView(ctx).apply {
                    text = genre
                    setTextColor(ContextCompat.getColor(ctx, R.color.bg_white))
                    textSize = 11f
                    setBackgroundResource(R.drawable.tag_chip_bg)
                    setPadding(
                        (10 * density).toInt(),
                        (3 * density).toInt(),
                        (10 * density).toInt(),
                        (3 * density).toInt()
                    )
                    maxLines = 1
                    isFocusable = false
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = (6 * density).toInt()
                holder.genresRow.addView(chip, lp)
            }
            holder.genresRow.isVisible = true
        } else {
            holder.genresRow.isVisible = false
        }

        // --- Play button ---
        holder.playBtn.setOnClickListener { onItemClick(media) }
        holder.playBtn.isFocusable = true
        holder.playBtn.isFocusableInTouchMode = false
        holder.playBtn.visibility = View.VISIBLE

        // --- Favorite button ---
        val isFav = media.isFav
        holder.favBtn.setImageDrawable(
            ContextCompat.getDrawable(
                ctx,
                if (isFav) R.drawable.ic_round_favorite_24
                else R.drawable.ic_round_favorite_border_24
            )
        )
        holder.favBtn.setOnClickListener {
            val newState = !media.isFav
            media.isFav = newState
            holder.favBtn.setImageDrawable(
                ContextCompat.getDrawable(
                    ctx,
                    if (newState) R.drawable.ic_round_favorite_24
                    else R.drawable.ic_round_favorite_border_24
                )
            )
            scope.launch(Dispatchers.IO) {
                Anilist.mutation.toggleFav(media.anime != null, media.id)
            }
        }
        holder.favBtn.visibility = View.VISIBLE

        // --- Focus effects ---
        FocusEffectUtil.applyFocusListener(holder.itemView, holder.playBtn, holder.favBtn)

        // --- Item click ---
        holder.itemView.setOnClickListener { onItemClick(media) }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false

        // --- D-pad focus chain ---
        holder.itemView.nextFocusUpId = View.NO_ID
        holder.itemView.nextFocusLeftId = View.NO_ID
        holder.itemView.nextFocusRightId = View.NO_ID
        holder.playBtn.nextFocusUpId = View.NO_ID
        holder.playBtn.nextFocusDownId = R.id.homeContinueWatch
        holder.favBtn.nextFocusUpId = View.NO_ID
        holder.favBtn.nextFocusDownId = R.id.homeContinueWatch
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bannerBg: ImageView = view.findViewById(R.id.bannerBg)
        val bannerImage: ImageView = view.findViewById(R.id.bannerImage)
        val clearlogo: ImageView = view.findViewById(R.id.bannerClearlogo)
        val title: TextView = view.findViewById(R.id.bannerTitle)

        val formatTag: TextView = view.findViewById(R.id.bannerFormatTag)
        val statusTag: TextView = view.findViewById(R.id.bannerStatusTag)
        val seasonTag: TextView = view.findViewById(R.id.bannerSeasonTag)
        val scoreTag: TextView = view.findViewById(R.id.bannerScoreTag)

        val description: TextView = view.findViewById(R.id.bannerDescription)
        val genresRow: LinearLayout = view.findViewById(R.id.bannerGenresRow)

        val playBtn: android.widget.Button = view.findViewById(R.id.bannerPlayBtn)
        val favBtn: ImageView = view.findViewById(R.id.bannerFavBtn)
    }
}
