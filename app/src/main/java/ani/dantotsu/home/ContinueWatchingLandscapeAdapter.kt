package ani.dantotsu.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.loadImage
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView

class ContinueWatchingLandscapeAdapter(
    private val items: MutableList<Media>,
    private val onItemClick: (Media) -> Unit
) : RecyclerView.Adapter<ContinueWatchingLandscapeAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching_landscape, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = items[position]
        val ctx = holder.itemView.context

        val imageUrl = media.cover
        if (!imageUrl.isNullOrBlank()) {
            holder.image.loadImage(imageUrl)
        }

        holder.title.text = media.userPreferredName ?: media.name

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
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cwCard)
        val image: ShapeableImageView = view.findViewById(R.id.cwImage)
        val title: TextView = view.findViewById(R.id.cwTitle)
        val subtitle: TextView = view.findViewById(R.id.cwSubtitle)
        val progress: ProgressBar = view.findViewById(R.id.cwProgress)
        val ongoing: View = view.findViewById(R.id.cwOngoing)
    }
}
