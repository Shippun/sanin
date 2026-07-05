package ani.dantotsu.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.dantotsu.GesturesListener
import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.databinding.ActivityMediaBinding
import ani.dantotsu.getThemeColor
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.openLinkInBrowser
import ani.dantotsu.media.anime.AnimeWatchFragment
import ani.dantotsu.media.comments.CommentsFragment
import ani.dantotsu.others.getSerialized
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.util.LauncherWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds


class MediaDetailsActivity : AppCompatActivity() {
    lateinit var launcher: LauncherWrapper
    lateinit var binding: ActivityMediaBinding
    private val scope = lifecycleScope
    private val model: MediaDetailsViewModel by viewModels()
    var selected = 0
    var anime = true
    private var adult = false
    private var hasComments = false

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
        ThemeManager(this).applyTheme()
        initActivity(this)
        MediaSingleton.bitmap = null

        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isDownload = intent.getBooleanExtra("download", false)
        media.selected = model.loadSelected(media, isDownload)
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        hasComments = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1 && !rescueMode

        // Load full-screen banner background
        binding.mediaBg!!.loadImage(media.banner ?: media.cover)

        // Load cover image
        binding.mediaCoverImage.loadImage(media.cover)
        binding.mediaCoverImage.setOnLongClickListener {
            val coverTitle = getString(R.string.cover, media.userPreferredName)
            ani.dantotsu.others.ImageViewDialog.newInstance(
                this,
                coverTitle,
                media.cover
            )
        }

        // Close button
        binding.mediaClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        FocusEffectUtil.applyFocusListener(binding.mediaClose)

        // Incognito mode
        if (PrefManager.getVal(PrefName.Incognito)) {
            binding.incognito.visibility = View.VISIBLE
        }

        // Load MediaInfoFragment into the left panel
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mediaInfoFragmentContainer, MediaInfoFragment())
                .commit()
        }

        // Native nav pills (info/watch/comments — info now focuses the left panel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cornerPx = 16f * resources.displayMetrics.density
            binding.mediaNavPills?.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
            binding.mediaNavPills?.elevation = 10f
            binding.mediaNavPills?.clipToOutline = true
        }
        val primaryColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val onBgColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground)
        val navInfo = binding.navPillInfo
        val navWatch = binding.navPillWatch
        val navComments = binding.navPillComments
        val allNav = listOfNotNull(navInfo, navWatch, navComments)
        allNav.forEach { FocusEffectUtil.applyFocusListener(it) }

        binding.navPillBg!!.live = PrefManager.getVal(PrefName.LiveSideRail)
        binding.navPillBg!!.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateMediaNavIconTints(selected)
        }

        fun getNavContrastColor(yCenter: Float): Int {
            val bg = binding.navPillBg!!
            if (bg.height <= 0) return onBgColor
            val fraction = yCenter / bg.height
            val color = bg.getColorAtFraction(fraction)
            val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
            return if (luminance > 0.5) Color.parseColor("#2A2A2A") else Color.WHITE
        }

        fun selectTab(idx: Int) {
            selected = idx
            allNav.forEachIndexed { i, btn ->
                val yCenter = btn.top + btn.height / 2f
                val contrast = getNavContrastColor(yCenter)
                val tint = if (i == idx) contrast else Color.argb(115, Color.red(contrast), Color.green(contrast), Color.blue(contrast))
                btn.imageTintList = ColorStateList.valueOf(tint)
                btn.alpha = if (i == idx) 1f else 0.7f
            }
            binding.commentInputLayout.isVisible = selected == 2
            when (idx) {
                0 -> {
                    // Info: just ensure info fragment container is visible
                    binding.mediaInfoFragmentContainer!!.visibility = View.VISIBLE
                    binding.mediaRightPanel!!.visibility = View.VISIBLE
                }
                1 -> {
                    // Watch
                    binding.mediaInfoFragmentContainer!!.visibility = View.GONE
                    binding.mediaRightPanel!!.visibility = View.VISIBLE
                    binding.mediaViewPager!!.setCurrentItem(0, true)
                }
                2 -> {
                    // Comments
                    binding.mediaInfoFragmentContainer!!.visibility = View.GONE
                    binding.mediaRightPanel!!.visibility = View.VISIBLE
                    binding.mediaViewPager!!.setCurrentItem(1, true)
                }
            }
            val sel = model.loadSelected(media, isDownload)
            sel.window = idx
            model.saveSelected(media.id, sel)
        }

        navInfo?.setOnClickListener { selectTab(0); hideNavPills() }
        navWatch?.setOnClickListener { selectTab(1); hideNavPills() }
        navComments?.visibility = if (hasComments) View.VISIBLE else View.GONE
        if (hasComments) {
            navComments?.setOnClickListener { selectTab(2); hideNavPills() }
        }

        // ViewPager setup (2 tabs: Watch, Comments)
        val viewPager = binding.mediaViewPager!!
        viewPager.offscreenPageLimit = 2
        viewPager.isUserInputEnabled = false
        viewPager.adapter = ViewPagerAdapter(
            supportFragmentManager,
            lifecycle,
            media,
            intent.getIntExtra("commentId", -1),
            hasComments
        )

        // Restore last selected tab (0=Info, 1=Watch, 2=Comments)
        val savedWindow = media.selected!!.window
        var defaultTab = if (savedWindow == 2 && (!hasComments || rescueMode)) 1 else savedWindow
        if (model.continueMedia == null && media.cameFromContinue) {
            model.continueMedia = PrefManager.getVal(PrefName.ContinueMedia)
            defaultTab = 1
        }
        if (intent.getStringExtra("FRAGMENT_TO_LOAD") != null && hasComments) defaultTab = 2
        selectTab(defaultTab)

        // Gesture for double-tap on banner bg
        val gestureDetector = GestureDetector(this, object : GesturesListener() {
            override fun onDoubleClick(event: MotionEvent) {
                snackString(getString(R.string.enable_banner_animations))
            }
            override fun onLongClick(event: MotionEvent) {
                val bannerTitle = getString(R.string.banner, media.userPreferredName)
                ani.dantotsu.others.ImageViewDialog.newInstance(
                    this@MediaDetailsActivity,
                    bannerTitle,
                    media.banner ?: media.cover
                )
            }
        })
        binding.mediaBg!!.setOnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent); true
        }

        adult = media.isAdult

        model.getMedia().observe(this) { updatedMedia ->
            if (updatedMedia != null) {
                media = updatedMedia
                binding.mediaCoverImage.loadImage(media.cover)
                if (media.format?.startsWith("LOCAL") == true) {
                    binding.mediaCover.setOnClickListener {
                        openLinkInBrowser(media.shareLink)
                    }
                }
            }
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
        binding.root.requestLayout()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (binding.mediaNavPills?.visibility == View.VISIBLE) {
                        if (!PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
                            hideNavPills()
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val focusedId = currentFocus?.id
                    if (focusedId == R.id.navPillInfo || focusedId == R.id.navPillWatch || focusedId == R.id.navPillComments) {
                        hideNavPills()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val focusedId = currentFocus?.id
                    if (focusedId == R.id.navPillInfo || focusedId == R.id.navPillWatch || focusedId == R.id.navPillComments) {
                        return true
                    }
                    if (binding.mediaNavPills?.visibility != View.VISIBLE &&
                        currentFocus?.focusSearch(View.FOCUS_LEFT) == null) {
                        showNavPills()
                        focusNavPillForSelectedTab()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.mediaNavPills?.visibility != View.VISIBLE) {
                        showNavPills()
                        focusNavPillForSelectedTab()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showNavPills() {
        binding.mediaNavPills?.visibility = View.VISIBLE
        updateMediaNavIconTints(selected)
    }

    private fun hideNavPills() {
        binding.mediaNavPills?.visibility = View.GONE
        binding.mediaViewPager!!.requestFocus()
    }

    private fun updateMediaNavIconTints(selectedIdx: Int) {
        val bg = binding.navPillBg ?: return
        if (bg.height <= 0) return
        val pills = listOfNotNull(binding.navPillInfo, binding.navPillWatch, binding.navPillComments)
        pills.forEachIndexed { i, pill ->
            val yCenter = pill.top + pill.height / 2f
            val fraction = yCenter / bg.height
            val color = bg.getColorAtFraction(fraction)
            val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
            val contrast = if (luminance > 0.5) Color.parseColor("#2A2A2A") else Color.WHITE
            val tint = if (i == selectedIdx) contrast else Color.argb(115, Color.red(contrast), Color.green(contrast), Color.blue(contrast))
            pill.imageTintList = ColorStateList.valueOf(tint)
            pill.alpha = if (i == selectedIdx) 1f else 0.7f
        }
    }

    private fun focusNavPillForSelectedTab() {
        val targetId = when (selected) {
            0 -> R.id.navPillInfo
            1 -> R.id.navPillWatch
            2 -> R.id.navPillComments
            else -> R.id.navPillInfo
        }
        val target = binding.root.findViewById<View>(targetId)
        if (target?.visibility == View.VISIBLE) {
            target.requestFocus()
        } else {
            binding.navPillInfo?.requestFocus()
        }
    }

    // ViewPager with 2 tabs (Watch, Comments)
    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val media: Media,
        private val commentId: Int,
        private val hasComments: Boolean
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = if (hasComments) 2 else 1

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> AnimeWatchFragment()
            1 -> {
                val fragment = CommentsFragment()
                val bundle = Bundle()
                bundle.putInt("mediaId", media.id)
                bundle.putString("mediaName", media.mainName())
                bundle.putString("mediaFormat", media.format)
                if (commentId != -1) bundle.putInt("commentId", commentId)
                fragment.arguments = bundle
                fragment
            }
            else -> AnimeWatchFragment()
        }
    }

    companion object {
        var mediaSingleton: Media? = null
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
}
