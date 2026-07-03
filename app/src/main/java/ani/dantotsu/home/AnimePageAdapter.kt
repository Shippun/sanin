package ani.dantotsu.home

import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.home.BannerCarouselAdapter
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.databinding.ItemAnimePageBinding
import ani.dantotsu.databinding.LayoutTrendingBinding
import ani.dantotsu.getAppString
import ani.dantotsu.getThemeColor
import ani.dantotsu.loadImage
import ani.dantotsu.media.CalendarActivity
import ani.dantotsu.media.GenreActivity
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaAdaptor
import ani.dantotsu.media.MediaListViewActivity
import ani.dantotsu.media.SearchActivity
import ani.dantotsu.openLinkInCustomTab
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.px
import ani.dantotsu.setSafeOnClickListener
import ani.dantotsu.setSlideIn
import ani.dantotsu.setSlideUp
import ani.dantotsu.settings.SettingsDialogFragment
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnimePageAdapter : RecyclerView.Adapter<AnimePageAdapter.AnimePageViewHolder>() {
    val ready = MutableLiveData(false)
    lateinit var binding: ItemAnimePageBinding
    private lateinit var trendingBinding: LayoutTrendingBinding
    var bannerAdapter: BannerCarouselAdapter? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimePageViewHolder {
        val binding =
            ItemAnimePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnimePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimePageViewHolder, position: Int) {
        binding = holder.binding
        trendingBinding = LayoutTrendingBinding.bind(binding.root)
        trendingBinding.trendingViewPager.overScrollMode = RecyclerView.OVER_SCROLL_NEVER

        val textInputLayout = holder.itemView.findViewById<TextInputLayout>(R.id.searchBar)
        val currentColor = textInputLayout.boxBackgroundColor
        val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xA8000000.toInt()
        textInputLayout.boxBackgroundColor = semiTransparentColor
        val materialCardView =
            holder.itemView.findViewById<MaterialCardView>(R.id.userAvatarContainer)
        materialCardView.setCardBackgroundColor(semiTransparentColor)
        val color = binding.root.context.getThemeColor(android.R.attr.windowBackground)
        textInputLayout.boxBackgroundColor = (color and 0x00FFFFFF) or 0x28000000
        materialCardView.setCardBackgroundColor((color and 0x00FFFFFF) or 0x28000000)

        trendingBinding.titleContainer.updatePadding(top = statusBarHeight)

        if (PrefManager.getVal(PrefName.SmallView)) trendingBinding.trendingContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = (-108f).px
        }

        updateAvatar()

        trendingBinding.searchBar.hint = binding.root.context.getString(R.string.search)
        trendingBinding.searchBarText.setOnClickListener {
            val context = binding.root.context
            if (Anilist.token != null) {
                ContextCompat.startActivity(
                    context,
                    Intent(context, SearchActivity::class.java).putExtra("type", "ANIME"),
                    null
                )
            } else {
                SearchBottomSheet.newInstance().show(
                    (context as AppCompatActivity).supportFragmentManager,
                    "search"
                )
            }
        }

        trendingBinding.userAvatar.setSafeOnClickListener {
            val dialogFragment =
                SettingsDialogFragment.newInstance(SettingsDialogFragment.Companion.PageType.ANIME)
            dialogFragment.show((it.context as AppCompatActivity).supportFragmentManager, "dialog")
        }
        trendingBinding.userAvatar.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            if (!rescueMode) {
                ContextCompat.startActivity(
                    view.context,
                    Intent(view.context, ProfileActivity::class.java)
                        .putExtra("userId", Anilist.userid), null
                )
            } else {
                val malUsername = MAL.username
                if (!malUsername.isNullOrBlank()) {
                    openLinkInCustomTab("https://myanimelist.net/profile/$malUsername")
                } else {
                    ani.dantotsu.toast(view.context.getString(R.string.rescue_mode_active))
                }
            }
            false
        }

        trendingBinding.searchBar.setEndIconOnClickListener {
            trendingBinding.searchBar.performClick()
        }

        val isRescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        trendingBinding.notificationCount.isVisible = !isRescueMode && Anilist.unreadNotificationCount > 0
                && PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot) == true
        trendingBinding.notificationCount.text = Anilist.unreadNotificationCount.toString()

        listOf(
            binding.animePreviousSeason,
            binding.animeThisSeason,
            binding.animeNextSeason
        ).forEachIndexed { i, it ->
            it.setSafeOnClickListener { onSeasonClick.invoke(i) }
            it.setOnLongClickListener { onSeasonLongClick.invoke(i) }
        }

        binding.animeGenreImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/anime/banner/16498-8jpFCOcDmneX.jpg")
        binding.animeCalendarImage.loadImage("https://s4.anilist.co/file/anilistcdn/media/anime/banner/125367-hGPJLSNfprO3.jpg")

        binding.animeGenre.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, GenreActivity::class.java).putExtra("type", "ANIME"),
                null
            )
        }
        binding.animeCalendar.setOnClickListener {
            ContextCompat.startActivity(
                it.context,
                Intent(it.context, CalendarActivity::class.java),
                null
            )
        }

        val rescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
        binding.animeIncludeList.isVisible = if (rescueMode) MAL.token != null else Anilist.token != null

        binding.animeIncludeList.isChecked = PrefManager.getVal(PrefName.PopularAnimeList)

        binding.animeIncludeList.setOnCheckedChangeListener { _, isChecked ->
            onIncludeListClick.invoke(isChecked)

            PrefManager.setVal(PrefName.PopularAnimeList, isChecked)
        }
        if (ready.value == false)
            ready.postValue(true)
    }

    lateinit var onSeasonClick: ((Int) -> Unit)
    lateinit var onSeasonLongClick: ((Int) -> Boolean)
    lateinit var onIncludeListClick: ((Boolean) -> Unit)

    override fun getItemCount(): Int = 1

    fun updateHeight() {
        trendingBinding.trendingViewPager.updateLayoutParams { height += statusBarHeight }
    }

    fun updateTrending(media: List<Media>) {
        trendingBinding.trendingProgressBar.visibility = View.GONE
        val rv = trendingBinding.trendingViewPager
        rv.layoutManager = LinearLayoutManager(rv.context, LinearLayoutManager.HORIZONTAL, false)
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(rv)
        rv.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        bannerAdapter = BannerCarouselAdapter(media, CoroutineScope(Dispatchers.Main)) { item ->
            val context = binding.root.context
            ContextCompat.startActivity(
                context,
                Intent(context, ani.dantotsu.media.MediaDetailsActivity::class.java)
                    .putExtra("media", item)
                    .putExtra("anime", true),
                null
            )
        }
        rv.adapter = bannerAdapter
        rv.layoutAnimation = LayoutAnimationController(setSlideIn(), 0.25f)
        trendingBinding.titleContainer.startAnimation(setSlideUp())
        binding.animeListContainer.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
        binding.animeSeasonsCont.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    fun updateRecent(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeUpdatedRecyclerView,
                animeUpdatedProgressBar,
                animeRecently,
                animeRecentlyMore,
                getAppString(R.string.updated),
                media
            )
            animePopular.visibility = View.VISIBLE
            animePopular.startAnimation(setSlideUp())
            if (adaptor.itemCount == 0) {
                animeRecentlyContainer.visibility = View.GONE
            }
        }

    }

    fun updateMovies(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeMoviesRecyclerView,
                animeMoviesProgressBar,
                animeMovies,
                animeMoviesMore,
                getAppString(R.string.trending_movies),
                media
            )
        }
    }

    fun updateTopRated(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeTopRatedRecyclerView,
                animeTopRatedProgressBar,
                animeTopRated,
                animeTopRatedMore,
                getAppString(R.string.top_rated),
                media
            )
        }
    }

    fun updateMostFav(adaptor: MediaAdaptor, media: MutableList<Media>) {
        binding.apply {
            init(
                adaptor,
                animeMostFavRecyclerView,
                animeMostFavProgressBar,
                animeMostFav,
                animeMostFavMore,
                getAppString(R.string.most_favourite),
                media
            )
        }
    }

    fun init(
        adaptor: MediaAdaptor,
        recyclerView: RecyclerView,
        progress: View,
        title: View,
        more: View,
        string: String,
        media: MutableList<Media>
    ) {
        progress.visibility = View.GONE
        recyclerView.adapter = adaptor
        recyclerView.layoutManager =
            LinearLayoutManager(
                recyclerView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )

        more.setOnClickListener {
            MediaListViewActivity.passedMedia = media.toCollection(ArrayList())
            ContextCompat.startActivity(
                it.context, Intent(it.context, MediaListViewActivity::class.java)
                    .putExtra("title", string),
                null
            )
        }
        recyclerView.visibility = View.VISIBLE
        title.visibility = View.VISIBLE
        more.visibility = View.VISIBLE
        title.startAnimation(setSlideUp())
        more.startAnimation(setSlideUp())
        recyclerView.layoutAnimation =
            LayoutAnimationController(setSlideIn(), 0.25f)
    }

    fun updateAvatar() {
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        val avatarUrl = if (rescueMode) MAL.avatar else Anilist.avatar
        if (avatarUrl != null && ready.value == true) {
            trendingBinding.userAvatar.loadImage(avatarUrl)
            trendingBinding.userAvatar.imageTintList = null
        }
    }

    fun updateNotificationCount() {
        if (this::binding.isInitialized) {
            val isRescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            trendingBinding.notificationCount.isVisible = !isRescueMode && Anilist.unreadNotificationCount > 0
                    && PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot) == true
            trendingBinding.notificationCount.text = Anilist.unreadNotificationCount.toString()
        }
    }

    inner class AnimePageViewHolder(val binding: ItemAnimePageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
