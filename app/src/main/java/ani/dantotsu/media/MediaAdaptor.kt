package ani.dantotsu.media

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ani.dantotsu.R
import ani.dantotsu.blurImage
import ani.dantotsu.connections.LogoApi
import ani.dantotsu.connections.anizip.AniZip
import ani.dantotsu.currActivity
import ani.dantotsu.databinding.ItemMediaCompactBinding
import ani.dantotsu.databinding.ItemMediaCompactLandBinding
import ani.dantotsu.databinding.ItemMediaLargeBinding
import ani.dantotsu.databinding.ItemMediaPageBinding
import ani.dantotsu.databinding.ItemMediaPageSmallBinding
import ani.dantotsu.loadImage
import ani.dantotsu.setAnimation
import ani.dantotsu.setSafeOnClickListener
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.BitmapUtil
import ani.dantotsu.util.FocusEffectUtil
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import java.io.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MediaAdaptor(
    var type: Int,
    private val mediaList: MutableList<Media>?,
    private val activity: FragmentActivity,
    private val matchParent: Boolean = false,
    private val viewPager: ViewPager2? = null,
    private val fav: Boolean = false,
    private val isOtherUser: Boolean = false,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rawCardStyle = 0
    private var isLandscape = false

    init {
        if (type == 0) {
            rawCardStyle = PrefManager.getVal<Int>(PrefName.CardStyle)
            type = when (rawCardStyle) {
                0, 4, 6 -> 0
                1 -> 1
                2, 5 -> 2
                3 -> 3
                else -> 0
            }
        }
        isLandscape = if (type == 0) PrefManager.getVal<Int>(PrefName.CardOrientation) == 0 else false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (type == 0 && isLandscape) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_compact_land, parent, false)
            return MediaLandscapeViewHolder(
                ani.dantotsu.databinding.ItemMediaCompactLandBinding.bind(view)
            )
        }
        return when (type) {
            0 -> MediaViewHolder(
                ItemMediaCompactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            1 -> MediaLargeViewHolder(
                ItemMediaLargeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            2 -> MediaPageViewHolder(
                ItemMediaPageBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            3 -> MediaPageSmallViewHolder(
                ItemMediaPageSmallBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> throw IllegalArgumentException()
        }

    }

    private var logoJobs = mutableMapOf<Int, Job>()
    private var gradientJobs = mutableMapOf<Int, Job>()
    private val coverGradientCache = mutableMapOf<Int, Int>()

    private fun bindLogo(
        clearlogo: ImageView,
        overlayTitle: TextView,
        media: Media,
        position: Int
    ) {
        logoJobs[position]?.cancel()
        logoJobs[position] = CoroutineScope(Dispatchers.Main).launch {
            val logoUrl = LogoApi.getLogoUrl(media.id)
            if (!logoUrl.isNullOrBlank()) {
                clearlogo.visibility = View.VISIBLE
                clearlogo.loadImage(logoUrl)
                overlayTitle.visibility = View.GONE
            } else {
                clearlogo.visibility = View.GONE
                overlayTitle.visibility = View.VISIBLE
                overlayTitle.text = media.userPreferredName
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val cardRoundness = PrefManager.getVal<Int>(PrefName.StandardCardRoundness).toFloat()

        if (holder is MediaLandscapeViewHolder) {
            bindLandscape(holder, position, cardRoundness)
            return
        }
        when (type) {
            0 -> {
                val b = (holder as MediaViewHolder).binding
                setAnimation(activity, b.root)
                val media = mediaList?.getOrNull(position)
                if (media != null) {
                    val imageUrl = media.cover
                    b.itemCompactImage.loadImage(imageUrl)
                    loadGradientOverlay(b.itemCompactImageOverlay, media, position)
                    val cardSize = PrefManager.getVal<Float>(PrefName.CardSize)
                    val finalW = (102 * cardSize).toInt()
                    val finalH = (154 * cardSize).toInt()
                    b.itemCompactImage.updateLayoutParams {
                        width = finalW
                        height = finalH
                    }
                    val styleRadius = when (rawCardStyle) {
                        4 -> 24f
                        6 -> 4f
                        else -> cardRoundness
                    }
                    b.itemCompactCard.radius = styleRadius
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    val titlePos = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
                    when (titlePos) {
                        0 -> {
                            b.itemCompactImageOverlay.visibility = View.VISIBLE
                            b.itemCompactTitleBelow.visibility = View.GONE
                            bindLogo(b.itemCompactClearlogo, b.itemCompactOverlayTitle, media, position)
                        }
                        1 -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitleBelow.visibility = View.VISIBLE
                            b.itemCompactTitleBelow.text = media.userPreferredName
                        }
                        else -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitleBelow.visibility = View.GONE
                        }
                    }
                }
            }

            1 -> {
                val b = (holder as MediaLargeViewHolder).binding
                setAnimation(activity, b.root)
                val media = mediaList?.get(position)
                if (media != null) {
                    b.itemCompactImage.loadImage(media.cover)
                    blurImage(b.itemCompactBanner, media.banner ?: media.cover)
                    loadGradientOverlay(b.imageView, media, position)
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    val titlePos = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
                    when (titlePos) {
                        0 -> {
                            b.itemCompactImageOverlay.visibility = View.VISIBLE
                            b.itemCompactTitle.visibility = View.GONE
                            bindLogo(b.itemCompactClearlogo, b.itemCompactOverlayTitle, media, position)
                            loadGradientOverlay(b.itemCompactImageOverlay, media, position)
                        }
                        1 -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitle.visibility = View.VISIBLE
                        }
                        else -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitle.visibility = View.GONE
                        }
                    }
                    if (media.anime != null) {
                        val itemTotal = " " + if ((media.anime.totalEpisodes
                                ?: 0) != 1
                        ) currActivity()!!.getString(R.string.episode_plural) else currActivity()!!.getString(
                            R.string.episode_singular
                        )
                        b.itemTotal.text = itemTotal
                        b.itemCompactTotal.text =
                            if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " / " + (media.anime.totalEpisodes
                                ?: "??").toString()) else (media.anime.totalEpisodes
                                ?: "??").toString()
                    }
                    if (position == mediaList.size - 2 && viewPager != null) viewPager.post {
                        val start = mediaList.size
                        mediaList.addAll(mediaList)
                        val end = mediaList.size - start
                        notifyItemRangeInserted(start, end)
                    }
                }
            }

            2 -> {
                val b = (holder as MediaPageViewHolder).binding
                val media = mediaList?.get(position)
                if (media != null) {

                    val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
                    b.itemCompactImage.loadImage(media.cover)
                    loadGradientOverlay(b.itemCompactGradientOverlay, media, position)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * ((PrefManager.getVal(PrefName.AnimationSpeed)) as Float)).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    blurImage(
                        if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen,
                        media.banner ?: media.cover
                    )
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    val titlePos = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
                    when (titlePos) {
                        0 -> {
                            b.itemCompactImageOverlay.visibility = View.VISIBLE
                            b.itemCompactTitleContainer.visibility = View.GONE
                            bindLogo(b.itemCompactClearlogo, b.itemCompactOverlayTitle, media, position)
                        }
                        1 -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitleContainer.visibility = View.VISIBLE
                        }
                        else -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitleContainer.visibility = View.GONE
                        }
                    }
                    if (media.anime != null) {
                        b.itemTotal.text = " " + if ((media.anime.totalEpisodes
                                ?: 0) != 1
                        ) currActivity()!!.getString(R.string.episode_plural)
                        else currActivity()!!.getString(R.string.episode_singular)
                        b.itemCompactTotal.text =
                            if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " / " + (media.anime.totalEpisodes
                                ?: "??").toString()) else (media.anime.totalEpisodes
                                ?: "??").toString()
                    }
                    @SuppressLint("NotifyDataSetChanged")
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }
            }

            3 -> {
                val b = (holder as MediaPageSmallViewHolder).binding
                val media = mediaList?.get(position)
                if (media != null) {
                    val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
                    b.itemCompactImage.loadImage(media.cover)
                    loadGradientOverlay(b.itemCompactGradientOverlay, media, position)
                    if (bannerAnimations)
                        b.itemCompactBanner.setTransitionGenerator(
                            RandomTransitionGenerator(
                                (10000 + 15000 * ((PrefManager.getVal(PrefName.AnimationSpeed) as Float))).toLong(),
                                AccelerateDecelerateInterpolator()
                            )
                        )
                    blurImage(
                        if (bannerAnimations) b.itemCompactBanner else b.itemCompactBannerNoKen,
                        media.banner ?: media.cover
                    )
                    b.itemCompactOngoing.isVisible =
                        media.status == currActivity()!!.getString(R.string.status_releasing)
                    b.itemCompactTitle.text = media.userPreferredName
                    b.itemCompactScore.text =
                        ((if (media.userScore == 0) (media.meanScore
                            ?: 0) else media.userScore) / 10.0).toString()
                    b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                        b.root.context,
                        (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
                    )
                    val titlePos = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
                    when (titlePos) {
                        0 -> {
                            b.itemCompactImageOverlay.visibility = View.VISIBLE
                            b.itemCompactTitleContainer.visibility = View.GONE
                            bindLogo(b.itemCompactClearlogo, b.itemCompactOverlayTitle, media, position)
                        }
                        1 -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitleContainer.visibility = View.VISIBLE
                        }
                        else -> {
                            b.itemCompactImageOverlay.visibility = View.GONE
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.GONE
                            b.itemCompactTitleContainer.visibility = View.GONE
                        }
                    }
                    media.genres.apply {
                        if (isNotEmpty()) {
                            var genres = ""
                            forEach { genres += "$it • " }
                            genres = genres.removeSuffix(" • ")
                            b.itemCompactGenres.text = genres
                        }
                    }
                    b.itemCompactStatus.text = media.status ?: ""
                    if (media.anime != null) {
                        b.itemTotal.text = " " + if ((media.anime.totalEpisodes
                                ?: 0) != 1
                        ) currActivity()!!.getString(R.string.episode_plural)
                        else currActivity()!!.getString(R.string.episode_singular)
                        b.itemCompactTotal.text =
                            if (media.anime.nextAiringEpisode != null) (media.anime.nextAiringEpisode.toString() + " / " + (media.anime.totalEpisodes
                                ?: "??").toString()) else (media.anime.totalEpisodes
                                ?: "??").toString()
                    }
                    @SuppressLint("NotifyDataSetChanged")
                    if (position == mediaList!!.size - 2 && viewPager != null) viewPager.post {
                        val size = mediaList.size
                        mediaList.addAll(mediaList)
                        notifyItemRangeInserted(size - 1, mediaList.size)
                    }
                }
            }
        }
    }

    override fun getItemCount() = mediaList!!.size

    override fun getItemViewType(position: Int): Int {
        return type
    }

    fun randomOptionClick() {
        val media = if (!mediaList.isNullOrEmpty()) {
            mediaList.random()
        } else {
            null
        }
        media?.let {
            val index = mediaList?.indexOf(it) ?: -1
            clicked(index, null)
        }
    }

    inner class MediaViewHolder(val binding: ItemMediaCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            if (matchParent) itemView.updateLayoutParams { width = -1 }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            FocusEffectUtil.applyFocusListener(itemView, binding.itemCompactCard)
        }
    }

    inner class MediaLargeViewHolder(val binding: ItemMediaLargeBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            FocusEffectUtil.applyFocusListener(itemView, binding.itemCompactCard)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageViewHolder(val binding: ItemMediaPageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactImage.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            FocusEffectUtil.applyFocusListener(itemView, binding.itemCompactCard)
            itemView.setOnTouchListener { _, _ -> true }
            binding.itemCompactImage.setOnLongClickListener { longClicked(bindingAdapterPosition) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class MediaPageSmallViewHolder(val binding: ItemMediaPageSmallBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.itemCompactImage.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            binding.itemCompactTitleContainer.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            FocusEffectUtil.applyFocusListener(itemView, binding.itemCompactCard)
            itemView.setOnTouchListener { _, _ -> true }
            binding.itemCompactImage.setOnLongClickListener { longClicked(bindingAdapterPosition) }
        }
    }

    inner class MediaLandscapeViewHolder(val binding: ItemMediaCompactLandBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            if (matchParent) itemView.updateLayoutParams { width = -1 }
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.setSafeOnClickListener {
                clicked(
                    bindingAdapterPosition,
                    binding.itemCompactImage,
                    resizeBitmap(getBitmapFromImageView(binding.itemCompactImage), 100)
                )
            }
            itemView.setOnLongClickListener { longClicked(bindingAdapterPosition) }
            FocusEffectUtil.applyFocusListener(itemView, binding.itemCompactCard)
        }
    }

    private fun bindLandscape(
        holder: MediaLandscapeViewHolder,
        position: Int,
        cardRoundness: Float
    ) {
        val b = holder.binding
        setAnimation(activity, b.root)
        val media = mediaList?.getOrNull(position)
        if (media != null) {
            b.itemCompactCard.radius = cardRoundness
            b.itemCompactOngoing.isVisible =
                media.status == currActivity()!!.getString(R.string.status_releasing)
            b.itemCompactScore.text =
                ((if (media.userScore == 0) (media.meanScore ?: 0) else media.userScore) / 10.0).toString()
            b.itemCompactScoreBG.background = ContextCompat.getDrawable(
                b.root.context,
                (if (media.userScore != 0) R.drawable.item_user_score else R.drawable.item_score)
            )

            b.itemCompactImage.scaleType = ImageView.ScaleType.CENTER_CROP
            b.itemCompactImage.loadImage(media.cover)
            logoJobs[position]?.cancel()
            logoJobs[position] = CoroutineScope(Dispatchers.IO).launch {
                val posterUrl = AniZip.getPosterUrl(media.id)
                if (posterUrl != null) {
                    withContext(Dispatchers.Main) {
                        b.itemCompactImage.loadImage(posterUrl)
                    }
                }
            }
            val titlePos = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
            when (titlePos) {
                0 -> {
                    b.itemCompactOverlay.visibility = View.VISIBLE
                    loadGradientOverlay(b.itemCompactOverlay, media, position, GradientDrawable.Orientation.TOP_BOTTOM)
                    b.itemCompactTitleBelow.visibility = View.GONE
                    logoJobs[position]?.cancel()
                    logoJobs[position] = CoroutineScope(Dispatchers.Main).launch {
                        val logoUrl = LogoApi.getLogoUrl(media.id)
                        if (!logoUrl.isNullOrBlank()) {
                            b.itemCompactClearlogo.visibility = View.VISIBLE
                            b.itemCompactClearlogo.loadImage(logoUrl)
                            b.itemCompactOverlayTitle.visibility = View.GONE
                        } else {
                            b.itemCompactClearlogo.visibility = View.GONE
                            b.itemCompactOverlayTitle.visibility = View.VISIBLE
                            b.itemCompactOverlayTitle.text = media.userPreferredName
                        }
                    }
                }
                1 -> {
                    b.itemCompactOverlay.visibility = View.GONE
                    b.itemCompactClearlogo.visibility = View.GONE
                    b.itemCompactOverlayTitle.visibility = View.GONE
                    b.itemCompactTitleBelow.visibility = View.VISIBLE
                    b.itemCompactTitleBelow.text = media.userPreferredName
                }
                else -> {
                    b.itemCompactOverlay.visibility = View.GONE
                    b.itemCompactClearlogo.visibility = View.GONE
                    b.itemCompactOverlayTitle.visibility = View.GONE
                    b.itemCompactTitleBelow.visibility = View.GONE
                }
            }
            b.itemCompactScoreBG.visibility = View.VISIBLE
        }
    }

    private fun loadGradientOverlay(view: View, media: Media, position: Int, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM) {
        gradientJobs[position]?.cancel()
        val cached = coverGradientCache[media.id]
        if (cached != null) {
            setGradient(view, cached, orientation)
            return
        }
        gradientJobs[position] = CoroutineScope(Dispatchers.IO).launch {
            val bitmap = BitmapUtil.downloadImageAsBitmap(media.cover ?: return@launch) ?: return@launch
            val color = averageColor(bitmap)
            coverGradientCache[media.id] = color
            withContext(Dispatchers.Main) {
                setGradient(view, color, orientation)
            }
        }
    }

    private fun setGradient(view: View, color: Int, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM) {
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
            orientation,
            intArrayOf(startColor, endColor)
        )
        view.background = gradient
    }

    private fun averageColor(bitmap: Bitmap): Int {
        val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
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

    fun clicked(position: Int, itemCompactImage: ImageView?, bitmap: Bitmap? = null) {
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position)
            if (bitmap != null) MediaSingleton.bitmap = bitmap
            ContextCompat.startActivity(
                activity,
                Intent(activity, MediaDetailsActivity::class.java).putExtra(
                    "media",
                    media as Serializable
                ),
                if (itemCompactImage != null) {
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity,
                        itemCompactImage,
                        ViewCompat.getTransitionName(itemCompactImage)!!
                    ).toBundle()
                } else {
                    null
                }
            )
        }
    }


    fun longClicked(position: Int): Boolean {
        if (isOtherUser) return false
        if ((mediaList?.size ?: 0) > position && position != -1) {
            val media = mediaList?.get(position) ?: return false
            if (activity.supportFragmentManager.findFragmentByTag("list") == null) {
                MediaListDialogSmallFragment.newInstance(media)
                    .show(activity.supportFragmentManager, "list")
                return true
            }
        }
        return false
    }

    fun getBitmapFromImageView(imageView: ImageView): Bitmap? {
        val drawable = imageView.drawable ?: return null

        // If the drawable is a BitmapDrawable, then just get the bitmap
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        // Create a bitmap with the same dimensions as the drawable
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )

        // Draw the drawable onto the bitmap
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    fun resizeBitmap(source: Bitmap?, maxDimension: Int): Bitmap? {
        if (source == null) return null
        val width = source.width
        val height = source.height
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (height * (maxDimension.toFloat() / width)).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (width * (maxDimension.toFloat() / height)).toInt()
        }

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

}