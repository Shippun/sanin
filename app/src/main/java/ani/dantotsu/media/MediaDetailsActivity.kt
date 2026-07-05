package ani.dantotsu.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.color
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.dantotsu.GesturesListener
import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.ZoomOutPageTransformer
import ani.dantotsu.blurImage
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.copyToClipboard
import ani.dantotsu.databinding.ActivityMediaBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.media.anime.AnimeWatchFragment
import ani.dantotsu.media.comments.CommentsFragment
import ani.dantotsu.openLinkInBrowser
import ani.dantotsu.others.ImageViewDialog
import ani.dantotsu.others.getSerialized
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.util.LauncherWrapper
import com.flaviofaria.kenburnsview.RandomTransitionGenerator
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds


class MediaDetailsActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {
    lateinit var launcher: LauncherWrapper
    lateinit var binding: ActivityMediaBinding
    private val scope = lifecycleScope
    private val model: MediaDetailsViewModel by viewModels()
    var selected = 0
    var anime = true
    private var adult = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        var media: Media = intent.getSerialized("media") ?: mediaSingleton ?: emptyMedia()
        val id = intent.getIntExtra("mediaId", -1)
        if (id != -1) {
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            runBlocking {
                withContext(Dispatchers.IO) {
                    if (rescueMode) {
                        val animeNode = MAL.query.getAnimeDetails(id)
                        if (animeNode != null) {
                            media = Media(animeNode, true)
                        } else {
                            val mangaNode = MAL.query.getMangaDetails(id)
                            media = if (mangaNode != null) Media(mangaNode, false)
                            else emptyMedia()
                        }
                    } else {
                        media = Anilist.query.getMedia(id, false) ?: emptyMedia()
                    }
                }
            }
        }
        if (media.name == "No media found") {
            snackString(media.name)
            onBackPressedDispatcher.onBackPressed()
            return
        }
        val contract = ActivityResultContracts.OpenDocumentTree()
        launcher = LauncherWrapper(this, contract)

        mediaSingleton = null
        ThemeManager(this).applyTheme(MediaSingleton.bitmap)
        initActivity(this)
        MediaSingleton.bitmap = null

        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        screenWidth = resources.displayMetrics.widthPixels.toFloat()

        binding.mediaBanner.updateLayoutParams { height += statusBarHeight }
        binding.mediaBannerNoKen.updateLayoutParams { height += statusBarHeight }
        binding.mediaClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.incognito.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.mediaCollapsing.minimumHeight = statusBarHeight

        binding.mediaTitle.isSelected = true

        mMaxScrollSize = binding.mediaAppBar.totalScrollRange
        binding.mediaAppBar.addOnOffsetChangedListener(this)

        binding.mediaClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        FocusEffectUtil.applyFocusListener(binding.mediaClose)
        val isDownload = intent.getBooleanExtra("download", false)
        media.selected = model.loadSelected(media, isDownload)
        val initialSelected = media.selected!!.window
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        val hasComments = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1 && !rescueMode

        // Native nav pills (info/watch/comments)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.mediaNavPills?.outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            binding.mediaNavPills?.elevation = 10f
        }
        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val onBgColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground)
        val navInfo = binding.navPillInfo
        val navWatch = binding.navPillWatch
        val navComments = binding.navPillComments
        val allNav = listOfNotNull(navInfo, navWatch, navComments)
        allNav.forEach { FocusEffectUtil.applyFocusListener(it) }

        fun selectTab(idx: Int) {
            selected = idx
            allNav.forEachIndexed { i, btn ->
                btn.imageTintList = ColorStateList.valueOf(if (i == idx) primaryColor else onBgColor)
                btn.alpha = if (i == idx) 1f else 0.45f
            }
            binding.commentInputLayout.isVisible = selected == 2
            binding.mediaViewPager.setCurrentItem(selected, true)
            val sel = model.loadSelected(media, isDownload)
            sel.window = selected
            model.saveSelected(media.id, sel)
        }

        navInfo?.setOnClickListener { selectTab(0) }
        navWatch?.setOnClickListener { selectTab(1) }
        navComments?.visibility = if (hasComments) View.VISIBLE else View.GONE
        if (hasComments) {
            navComments?.setOnClickListener { selectTab(2) }
        }
        selectTab(initialSelected)

        val bannerAnimations: Boolean = PrefManager.getVal(PrefName.BannerAnimations)
        if (bannerAnimations) {
            val adi = AccelerateDecelerateInterpolator()
            val generator = RandomTransitionGenerator(
                (10000 + 15000 * ((PrefManager.getVal(PrefName.AnimationSpeed) as Float))).toLong(),
                adi
            )
            binding.mediaBanner.setTransitionGenerator(generator)
        }
        val banner =
            if (bannerAnimations) binding.mediaBanner else binding.mediaBannerNoKen
        val viewPager = binding.mediaViewPager
        viewPager.offscreenPageLimit = 2
        viewPager.isUserInputEnabled = false
        viewPager.setPageTransformer(ZoomOutPageTransformer())

        binding.mediaCoverImage.loadImage(media.cover)
        binding.mediaCoverImage.setOnLongClickListener {
            val coverTitle = getString(R.string.cover, media.userPreferredName)
            ImageViewDialog.newInstance(
                this,
                coverTitle,
                media.cover
            )
        }

        blurImage(banner, media.banner ?: media.cover)
        val gestureDetector = GestureDetector(this, object : GesturesListener() {
            override fun onDoubleClick(event: MotionEvent) {
                if (!(PrefManager.getVal(PrefName.BannerAnimations) as Boolean))
                    snackString(getString(R.string.enable_banner_animations))
                else {
                    binding.mediaBanner.restart()
                    binding.mediaBanner.performClick()
                }
            }

            override fun onLongClick(event: MotionEvent) {
                val bannerTitle = getString(R.string.banner, media.userPreferredName)
                ImageViewDialog.newInstance(
                    this@MediaDetailsActivity,
                    bannerTitle,
                    media.banner ?: media.cover
                )
                banner.performClick()
            }
        })
        banner.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent);true }
        if (PrefManager.getVal(PrefName.Incognito)) {
            val mediaTitle = "    ${media.userPreferredName}"
            binding.mediaTitle.text = mediaTitle
            binding.incognito.visibility = View.VISIBLE
        } else {
            binding.mediaTitle.text = media.userPreferredName
        }
        binding.mediaTitle.setOnLongClickListener {
            copyToClipboard(media.userPreferredName)
            true
        }
        binding.mediaTitleCollapse.text = media.userPreferredName
        binding.mediaTitleCollapse.setOnLongClickListener {
            copyToClipboard(media.userPreferredName)
            true
        }
        binding.mediaStatus.text = media.status ?: ""

        fun fav(media: Media):  PopImageButton? {
            //Fav Button
            return if (Anilist.userid != null && !rescueMode) {
                if (media.isFav) binding.mediaFav.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_round_favorite_24
                    )
                )

                PopImageButton(
                    scope,
                    binding.mediaFav,
                    R.drawable.ic_round_favorite_24,
                    R.drawable.ic_round_favorite_border_24,
                    R.color.bg_opp,
                    R.color.violet_400,
                    media.isFav
                ) {
                    media.isFav = it
                    Anilist.mutation.toggleFav(media.anime != null, media.id)
                    Refresh.all()
                }
            } else {
                binding.mediaFav.visibility = View.GONE
                null
            }
        }
        var isFavSyncRunning = false
        fun syncMediaFavStateIfNeeded(favButton: PopImageButton?) {
            if (rescueMode || Anilist.userid == null || favButton == null || media.isFav || isFavSyncRunning) return
            isFavSyncRunning = true
            scope.launch {
                try {
                    val favType = if (media.anime != null) {
                        ani.dantotsu.connections.anilist.AnilistMutations.FavType.ANIME
                    } else {
                        ani.dantotsu.connections.anilist.AnilistMutations.FavType.MANGA
                    }
                    val isUserFav = withContext(Dispatchers.IO) {
                        Anilist.query.isUserFav(favType, media.id)
                    }
                    if (isUserFav) {
                        media.isFav = true
                        if (!favButton.clicked) favButton.clicked()
                    }
                } finally {
                    isFavSyncRunning = false
                }
            }
        }

        @SuppressLint("ResourceType")
        fun total() {
            val text = SpannableStringBuilder().apply {

                val white =
                    this@MediaDetailsActivity.getThemeColor(com.google.android.material.R.attr.colorOnBackground)
                if (media.userStatus != null) {
                    append(if (media.anime != null) getString(R.string.watched_num) else getString(R.string.read_num))
                    val colorSecondary =
                        getThemeColor(com.google.android.material.R.attr.colorSecondary)
                    bold { color(colorSecondary) { append("${media.userProgress}") } }
                    append(
                        if (media.anime != null) getString(R.string.episodes_out_of) else getString(
                            R.string.chapters_out_of
                        )
                    )
                } else {
                    append(
                        if (media.anime != null) getString(R.string.episodes_total_of) else getString(
                            R.string.chapters_total_of
                        )
                    )
                }
                if (media.anime != null) {
                    if (media.anime!!.nextAiringEpisode != null) {
                        bold { color(white) { append("${media.anime!!.nextAiringEpisode}") } }
                        append(" / ")
                    }
                    bold { color(white) { append("${media.anime!!.totalEpisodes ?: "??"}") } }
                }
            }
            binding.mediaTotal.text = text
        }

        fun progress() {
            val statuses: Array<String> = resources.getStringArray(R.array.status)
            val statusStrings = resources.getStringArray(R.array.status_anime)
            val userStatus =
                if (media.userStatus != null) statusStrings[statuses.indexOf(media.userStatus).coerceAtLeast(0)] else statusStrings[0]

            if (media.userStatus != null) {
                binding.mediaTotal.visibility = View.VISIBLE
                binding.mediaAddToList.text = userStatus
            } else {
                binding.mediaAddToList.setText(R.string.add_list)
            }
            total()
            binding.mediaAddToList.setOnClickListener {
                if (rescueMode) {
                    if (MAL.token != null) {
                        if (supportFragmentManager.findFragmentByTag("dialog") == null)
                            MediaListDialogFragment().show(supportFragmentManager, "dialog")
                    } else snackString("Please login to MAL")
                } else if (Anilist.userid != null) {
                    if (supportFragmentManager.findFragmentByTag("dialog") == null)
                        MediaListDialogFragment().show(supportFragmentManager, "dialog")
                } else snackString(getString(R.string.please_login_anilist))
            }
            binding.mediaAddToList.setOnLongClickListener {
                PrefManager.setCustomVal(
                    "${media.id}_progressDialog",
                    true,
                )
                snackString(getString(R.string.auto_update_reset))
                true
            }
        }
        progress()

        model.getMedia().observe(this) {
            if (it != null) {
                val oldId = media.id
                media = it

                // mapping button for LOCAL media
                if (media.format?.startsWith("LOCAL") == true) {
                    binding.mediaMapping?.visibility = View.VISIBLE
                    binding.mediaMapping?.setOnClickListener {
                        val isAnime = media.anime != null
                        val isNovel = media.format == "LOCAL_NOVEL"
                        val folderName = media.folderName ?: media.name ?: media.nameRomaji
                        val dialog = LocalMappingSearchDialog.newInstance(
                            folderName = folderName,
                            isAnime = isAnime,
                            isNovel = isNovel
                        ) { _ ->
                            // Re-trigger it
                            val updatedMedia = media.copy(id = 0)
                            model.loading = false
                            model.loadMedia(updatedMedia)
                        }
                        dialog.show(supportFragmentManager, "localMapping")
                    }
                }

                scope.launch {
                    val favIcon = fav(it)
                    syncMediaFavStateIfNeeded(favIcon)
                    if (media.isFav != favIcon?.clicked) favIcon?.clicked()
                }


                binding.mediaNotify.setOnClickListener {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, media.shareLink)
                    startActivity(Intent.createChooser(i, media.userPreferredName))
                }
                binding.mediaNotify.setOnLongClickListener {
                    openLinkInBrowser(media.shareLink)
                    true
                }
                binding.mediaCover.setOnClickListener {
                    openLinkInBrowser(media.shareLink)
                }
                FocusEffectUtil.applyFocusListener(binding.mediaFav, binding.mediaNotify, binding.mediaCover)

                if (oldId == 0 && media.id != 0) {
                    if (media.format?.startsWith("LOCAL") == true) {
                        binding.mediaCoverImage.loadImage(media.cover)
                        blurImage(if (bannerAnimations) binding.mediaBanner else binding.mediaBannerNoKen, media.banner ?: media.cover)
                    }
                }
                progress()
            }
        }
        adult = media.isAdult
        if (media.anime != null) {
            viewPager.adapter =
                ViewPagerAdapter(
                    supportFragmentManager,
                    lifecycle,
                    media,
                    intent.getIntExtra("commentId", -1)
                )
        }
        selected = if (PrefManager.getVal<Int>(PrefName.CommentsEnabled) != 1 && media.selected!!.window == 2 || rescueMode && media.selected!!.window == 2) 1 else media.selected!!.window
        binding.mediaTitle.translationX = -screenWidth

        if (model.continueMedia == null && media.cameFromContinue) {
            model.continueMedia = PrefManager.getVal(PrefName.ContinueMedia)
            selected = 1
        }
        if (intent.getStringExtra("FRAGMENT_TO_LOAD") != null && hasComments) selected = 2
        if (viewPager.currentItem != selected) viewPager.post {
            viewPager.setCurrentItem(selected, false)
        }
        binding.commentInputLayout.isVisible = selected == 2

        if (selected == 2 && !hasComments) {
            selected = 1
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch(Dispatchers.IO) {
                    model.loadMedia(media)
                    live.postValue(false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return

        val extContainer = findViewById<android.widget.FrameLayout>(R.id.fragmentExtensionsContainer)
        if (extContainer != null) {
            val hasExtFragment = supportFragmentManager.findFragmentById(R.id.fragmentExtensionsContainer) != null
            if (hasExtFragment) {
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                extContainer.visibility = View.GONE
            }
        }

        binding.mediaAppBar.visibility = View.VISIBLE
        binding.mediaViewPager.visibility = View.VISIBLE
        binding.mediaCover.visibility = View.VISIBLE
        binding.mediaClose.visibility = View.VISIBLE
        binding.root.requestLayout()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val focusedId = currentFocus?.id
                    if (focusedId == R.id.navPillInfo || focusedId == R.id.navPillWatch || focusedId == R.id.navPillComments) {
                        binding.mediaViewPager.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (binding.mediaViewPager.isFocused || binding.commentMessageContainer.isFocused) {
                        val targetId = when (selected) {
                            0 -> R.id.navPillInfo
                            1 -> R.id.navPillWatch
                            2 -> R.id.navPillComments
                            else -> R.id.navPillInfo
                        }
                        binding.root.findViewById<View>(targetId)?.requestFocus()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ViewPager
    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val media: Media,
        private val commentId: Int
    ) :
        FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = if (PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1 && !PrefManager.getVal<Boolean>(PrefName.RescueMode)) 3 else 2

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> MediaInfoFragment()
            1 -> AnimeWatchFragment()

            2 -> { // Index 2
                if (PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1 && !PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                    val fragment = CommentsFragment()
                    val bundle = Bundle()
                    bundle.putInt("mediaId", media.id)
                    bundle.putString("mediaName", media.mainName())
                    bundle.putString("mediaFormat", media.format)
                    if (commentId != -1) bundle.putInt("commentId", commentId)
                    fragment.arguments = bundle
                    fragment
                } else {
                    MediaInfoFragment() // Fallback to Info tab if comments are disabled
                }
            }

            else -> MediaInfoFragment()
        }
    }

    //Collapsing UI Stuff
    private var isCollapsed = false
    private val percent = 45
    private var mMaxScrollSize = 0
    private var screenWidth: Float = 0f

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (!::binding.isInitialized) return
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize

        binding.mediaCover.visibility =
            if (binding.mediaCover.scaleX == 0f) View.GONE else View.VISIBLE
        val duration = (200 * (PrefManager.getVal(PrefName.AnimationSpeed) as Float)).toLong()
        if (percentage >= percent && !isCollapsed) {
            isCollapsed = true
            ObjectAnimator.ofFloat(binding.mediaTitle, "translationX", 0f).setDuration(duration)
                .start()
            ObjectAnimator.ofFloat(binding.mediaAccessContainer, "translationX", screenWidth)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaCover, "translationX", screenWidth)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaCollapseContainer, "translationX", screenWidth)
                .setDuration(duration).start()
            binding.mediaBanner.pause()
        }
        if (percentage <= percent && isCollapsed) {
            isCollapsed = false
            ObjectAnimator.ofFloat(binding.mediaTitle, "translationX", -screenWidth)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaAccessContainer, "translationX", 0f)
                .setDuration(duration).start()
            ObjectAnimator.ofFloat(binding.mediaCover, "translationX", 0f).setDuration(duration)
                .start()
            ObjectAnimator.ofFloat(binding.mediaCollapseContainer, "translationX", 0f)
                .setDuration(duration).start()
            if (PrefManager.getVal(PrefName.BannerAnimations)) binding.mediaBanner.resume()
        }
        // Fade/slide nav pills as toolbar scrolls so they don't overlap ViewPager when collapsed
        val progress = abs(i).toFloat() / mMaxScrollSize.coerceAtLeast(1)
        binding.mediaNavPills?.alpha = 1f - progress
        binding.mediaNavPills?.translationY = -i.toFloat()
        binding.mediaNavPills?.visibility =
            if (progress > 0.85f) View.INVISIBLE else View.VISIBLE

        if (percentage == 1 && model.scrolledToTop.value != false) model.scrolledToTop.postValue(
            false
        )
        if (percentage == 0 && model.scrolledToTop.value != true) model.scrolledToTop.postValue(true)
    }

    class PopImageButton(
        private val scope: CoroutineScope,
        private val image: ImageView,
        private val d1: Int,
        private val d2: Int,
        private val c1: Int,
        private val c2: Int,
        var clicked: Boolean,
        needsInitialClick: Boolean = false,
        callback: suspend (Boolean) -> (Unit)
    ) {
        private var disabled = false
        private val context = image.context
        private var pressable = true

        init {
            enabled(true)
            if (needsInitialClick) {
                scope.launch {
                    clicked()
                }
            }
            image.setOnClickListener {
                if (pressable && !disabled) {
                    pressable = false
                    clicked = !clicked
                    scope.launch {
                        launch(Dispatchers.IO) {
                            callback.invoke(clicked)
                        }
                        clicked()
                        pressable = true
                    }
                }
            }
        }

        suspend fun clicked() {
            ObjectAnimator.ofFloat(image, "scaleX", 1f, 0f).setDuration(69).start()
            ObjectAnimator.ofFloat(image, "scaleY", 1f, 0f).setDuration(100).start()
            delay(100.milliseconds)

            if (clicked) {
                ObjectAnimator.ofArgb(
                    image,
                    "ColorFilter",
                    ContextCompat.getColor(context, c1),
                    ContextCompat.getColor(context, c2)
                ).setDuration(120).start()
                image.setImageDrawable(AppCompatResources.getDrawable(context, d1))
            } else image.setImageDrawable(AppCompatResources.getDrawable(context, d2))
            ObjectAnimator.ofFloat(image, "scaleX", 0f, 1.5f).setDuration(120).start()
            ObjectAnimator.ofFloat(image, "scaleY", 0f, 1.5f).setDuration(100).start()
            delay(120.milliseconds)
            ObjectAnimator.ofFloat(image, "scaleX", 1.5f, 1f).setDuration(100).start()
            ObjectAnimator.ofFloat(image, "scaleY", 1.5f, 1f).setDuration(100).start()
            delay(200.milliseconds)
            if (clicked) {
                ObjectAnimator.ofArgb(
                    image,
                    "ColorFilter",
                    ContextCompat.getColor(context, c2),
                    ContextCompat.getColor(context, c1)
                ).setDuration(200).start()
            }
        }

        fun enabled(enabled: Boolean) {
            disabled = !enabled
            image.alpha = if (disabled) 0.33f else 1f
        }
    }

    companion object {
        var mediaSingleton: Media? = null
    }
}


