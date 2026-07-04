package ani.dantotsu.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.LogoApi
import ani.dantotsu.loadImage
import ani.dantotsu.media.Media
import ani.dantotsu.util.FocusEffectUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.KeyEvent

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

        holder.title.text = media.userPreferredName ?: media.name
        val episodes = media.anime?.totalEpisodes
        val score = media.meanScore
        val subtitle = buildString {
            append("TV")
            if (episodes != null) {
                append(" | S1: $episodes Episodes")
            }
            if (score != null) {
                append(" | ${score / 10.0}")
            }
        }
        holder.subtitle.text = subtitle

        val bannerUrl = media.banner
        if (!bannerUrl.isNullOrBlank()) {
            holder.bannerImage.loadImage(bannerUrl)
        } else if (!media.cover.isNullOrBlank()) {
            holder.bannerImage.loadImage(media.cover)
        }

        val ta = ctx.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        val bgColor = ta.getColor(0, 0xFF000000.toInt())
        ta.recycle()
        val gradient = GradientDrawable()
        gradient.gradientType = GradientDrawable.RADIAL_GRADIENT
        gradient.setCenterX(0.59f)
        gradient.setCenterY(0.35f)
        gradient.colors = intArrayOf(android.graphics.Color.TRANSPARENT, bgColor)
        holder.gradientOverlay.post {
            val size = Math.max(holder.gradientOverlay.width, holder.gradientOverlay.height)
            gradient.gradientRadius = size * 0.7f
            holder.gradientOverlay.background = gradient
        }

        scope.launch(Dispatchers.Main) {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                holder.clearlogo.visibility = View.VISIBLE
                holder.title.visibility = View.GONE
                holder.clearlogo.loadImage(logoUrl)
            } else {
                holder.clearlogo.visibility = View.GONE
                holder.title.visibility = View.VISIBLE
            }
        }

        holder.itemView.setOnClickListener { onItemClick(media) }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false
        FocusEffectUtil.applyFocusListener(holder.itemView)
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val focusDown = holder.itemView.focusSearch(View.FOCUS_DOWN)
                if (focusDown == null || focusDown == holder.itemView) {
                    holder.itemView.requestFocus(View.FOCUS_DOWN)
                    true
                } else false
            } else false
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bannerImage: ImageView = view.findViewById(R.id.bannerImage)
        val gradientOverlay: View = view.findViewById(R.id.bannerGradientOverlay)
        val clearlogo: ImageView = view.findViewById(R.id.bannerClearlogo)
        val title: TextView = view.findViewById(R.id.bannerTitle)
        val subtitle: TextView = view.findViewById(R.id.bannerSubtitle)
    }
}
