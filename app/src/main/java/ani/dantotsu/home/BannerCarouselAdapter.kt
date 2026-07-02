package ani.dantotsu.home

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
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
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

        scope.launch(Dispatchers.Main) {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                holder.clearlogo.visibility = View.VISIBLE
                holder.clearlogo.loadImage(logoUrl)
                holder.title.visibility = View.GONE
            } else {
                holder.clearlogo.visibility = View.GONE
                holder.title.visibility = View.VISIBLE
            }
        }

        holder.itemView.setOnClickListener { onItemClick(media) }
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false
        FocusEffectUtil.applyFocusListener(holder.itemView)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bannerImage: ImageView = view.findViewById(R.id.bannerImage)
        val clearlogo: ImageView = view.findViewById(R.id.bannerClearlogo)
        val title: TextView = view.findViewById(R.id.bannerTitle)
        val subtitle: TextView = view.findViewById(R.id.bannerSubtitle)
    }
}
