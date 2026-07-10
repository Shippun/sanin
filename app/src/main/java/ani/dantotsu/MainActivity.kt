package ani.dantotsu

import android.animation.ObjectAnimator
import android.view.animation.DecelerateInterpolator
import android.annotation.SuppressLint
import kotlin.math.cos
import kotlin.math.exp
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.addons.torrent.TorrentAddonManager
import ani.dantotsu.addons.torrent.TorrentServerService
import ani.dantotsu.blurImage
import ani.dantotsu.connections.LogoApi
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.connections.anilist.AnilistHomeViewModel
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.databinding.ActivityMainBinding
import ani.dantotsu.databinding.DialogUserAgentBinding
import ani.dantotsu.databinding.SplashScreenBinding
import ani.dantotsu.home.AnimeFragment
import ani.dantotsu.home.DiscoveryFragment
import ani.dantotsu.home.HomeFragment
import ani.dantotsu.home.LibraryFragment
import ani.dantotsu.home.LoginFragment
import ani.dantotsu.home.NoInternet
import ani.dantotsu.media.MediaDetailsActivity
import ani.dantotsu.notifications.TaskScheduler
import ani.dantotsu.others.CustomBottomDialog
import ani.dantotsu.others.calc.CalcActivity
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.profile.activity.FeedActivity
import ani.dantotsu.profile.notification.NotificationActivity
import ani.dantotsu.settings.AddRepositoryBottomSheet
import ani.dantotsu.settings.ExtensionsActivity
import ani.dantotsu.settings.SettingsNotificationActivity
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefManager.asLiveBool
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.settings.saving.SharedPreferenceBooleanLiveData
import ani.dantotsu.settings.saving.internal.PreferenceKeystore
import ani.dantotsu.settings.saving.internal.PreferencePackager
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.ui.components.NavigationPillsViewModel
import ani.dantotsu.util.AudioHelper
import ani.dantotsu.util.Logger
import ani.dantotsu.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.kanade.domain.source.service.SourcePreferences
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var incognitoLiveData: SharedPreferenceBooleanLiveData
    private val scope = lifecycleScope
    private var load = false
    lateinit var navPillsViewModel: NavigationPillsViewModel
    private var currentFragmentTag: String? = null

    private val tabFragments = mapOf(
        0 to "home",
        1 to "anime",
        2 to "discovery",
        3 to "library"
    )

    private fun getFragmentForTab(index: Int): Fragment = when (index) {
        0 -> HomeFragment()
        1 -> AnimeFragment()
        2 -> DiscoveryFragment()
        3 -> LibraryFragment()
        else -> HomeFragment()
    }

    private fun switchTab(index: Int) {
        val tag = tabFragments[index] ?: "home"
        if (tag == currentFragmentTag) return
        currentFragmentTag = tag
        val fragment = getFragmentForTab(index)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }


    @kotlin.OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager(this).applyTheme()
        LogoApi.init(this)

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!CalcActivity.hasPermission) {
            val pin: String = PrefManager.getVal(PrefName.AppPassword)
            if (pin.isNotEmpty()) {
                ContextCompat.startActivity(
                    this@MainActivity,
                    Intent(this@MainActivity, CalcActivity::class.java)
                        .putExtra("code", pin)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                    null
                )
                finish()
                return
            }
        }
        TaskScheduler.scheduleSingleWork(this)

        if (Intent.ACTION_VIEW == intent.action) {
            handleViewIntent(intent)
        }

        val offset = try {
            val statusBarHeightId = resources.getIdentifier("status_bar_height", "dimen", "android")
            resources.getDimensionPixelSize(statusBarHeightId)
        } catch (e: Exception) {
            statusBarHeight
        }
        val layoutParams = binding.incognito.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = 11 * offset / 12
        binding.incognito.layoutParams = layoutParams

        val rescueLayoutParams = binding.rescueModeIcon.layoutParams as ViewGroup.MarginLayoutParams
        rescueLayoutParams.topMargin = 11 * offset / 12
        binding.rescueModeIcon.layoutParams = rescueLayoutParams

      
        fun syncRescueIconMargin(incognitoOn: Boolean) {
            val p = binding.rescueModeIcon.layoutParams as ViewGroup.MarginLayoutParams
            p.marginStart = if (incognitoOn) {
                (54f * resources.displayMetrics.density).toInt()
            } else {
                (16f * resources.displayMetrics.density).toInt()
            }
            binding.rescueModeIcon.layoutParams = p
        }
        syncRescueIconMargin(PrefManager.getVal(PrefName.Incognito))

        incognitoLiveData = PrefManager.getLiveVal(
            PrefName.Incognito,
            false
        ).asLiveBool()
        incognitoLiveData.observe(this) {
            if (it) {
                val slideDownAnim = ObjectAnimator.ofFloat(
                    binding.incognito,
                    View.TRANSLATION_Y,
                    -(200f + statusBarHeight),
                    0f
                )
                slideDownAnim.duration = 200
                slideDownAnim.start()
                binding.incognito.visibility = View.VISIBLE
                if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) syncRescueIconMargin(true)
            } else {
                val slideUpAnim = ObjectAnimator.ofFloat(
                    binding.incognito,
                    View.TRANSLATION_Y,
                    0f,
                    -(200f + statusBarHeight)
                )
                slideUpAnim.duration = 200
                slideUpAnim.start()
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.incognito.visibility = View.GONE
                    if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) syncRescueIconMargin(false)
                }, 200)
            }
        }

        val rescueModeLiveData = PrefManager.getLiveVal(PrefName.RescueMode, false).asLiveBool()
        rescueModeLiveData.observe(this) {
            if (it) {
                syncRescueIconMargin(PrefManager.getVal(PrefName.Incognito))
                val slideDownAnim = ObjectAnimator.ofFloat(
                    binding.rescueModeIcon,
                    View.TRANSLATION_Y,
                    -(200f + statusBarHeight),
                    0f
                )
                slideDownAnim.duration = 200
                slideDownAnim.start()
                binding.rescueModeIcon.visibility = View.VISIBLE
            } else {
                val slideUpAnim = ObjectAnimator.ofFloat(
                    binding.rescueModeIcon,
                    View.TRANSLATION_Y,
                    0f,
                    -(200f + statusBarHeight)
                )
                slideUpAnim.duration = 200
                slideUpAnim.start()
                //wait for animation to finish
                Handler(Looper.getMainLooper()).postDelayed(
                    { binding.rescueModeIcon.visibility = View.GONE },
                    200
                )
            }
        }
        binding.rescueModeIcon.setOnClickListener {
            PrefManager.setVal(PrefName.RescueMode, false)
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
            overridePendingTransition(0, 0)
        }
        incognitoNotification(this)

        var doubleBackToExitPressedOnce = false
        onBackPressedDispatcher.addCallback(this) {
            if (binding.mainDrawer.isDrawerOpen(Gravity.END)) {
                binding.mainDrawer.closeDrawer(Gravity.END)
            } else if (doubleBackToExitPressedOnce) {
                finish()
            } else {
                doubleBackToExitPressedOnce = true
                snackString(this@MainActivity.getString(R.string.back_to_exit)).apply {
                    this?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            doubleBackToExitPressedOnce = false
                        }
                    })
                }
            }
        }

        binding.root.isMotionEventSplittingEnabled = false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val splash = SplashScreenBinding.inflate(layoutInflater)
            binding.root.addView(splash.root)
            splash.root.postDelayed({
                ObjectAnimator.ofFloat(
                    splash.root,
                    View.ALPHA,
                    1f,
                    0f
                ).apply {
                    duration = 400L
                    doOnEnd { binding.root.removeView(splash.root) }
                    start()
                }
            }, 1200L)
        }


        binding.root.doOnAttach {
            initActivity(this)
            val preferences: SourcePreferences = Injekt.get()
                    if (preferences.animeExtensionUpdatesCount()
                            .get() > 0) {
                snackString(R.string.extension_updates_available)
                    ?.setDuration(Snackbar.LENGTH_SHORT)
                    ?.setAction(R.string.review) {
                        startActivity(Intent(this, ExtensionsActivity::class.java))
                    }
            }
            window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            binding.mainProgressBar.visibility = View.GONE

            // Setup home side rail (replaces compose navPills)
            navPillsViewModel = ViewModelProvider(this)[NavigationPillsViewModel::class.java]
            setupHomeNavRail()

            // Setup avatar and right rail drawer
            binding.mainAvatarContainer.visibility = View.VISIBLE
            loadAvatar()
            binding.mainUserAvatarContainer.setOnClickListener {
                if (!binding.mainDrawer.isDrawerOpen(Gravity.END)) {
                    populateRightRail()
                    binding.mainDrawer.openDrawer(Gravity.END)
                } else {
                    binding.mainDrawer.closeDrawer(Gravity.END)
                }
            }
            binding.mainCalendarContainer.setOnClickListener {
                ContextCompat.startActivity(
                    this,
                    Intent(this, ani.dantotsu.media.CalendarActivity::class.java),
                    null
                )
            }
            // Focus: each icon gets its own border
            FocusEffectUtil.applyFocusListener(binding.mainCalendarContainer)
            FocusEffectUtil.applyFocusListener(binding.mainUserAvatarContainer)
            // Focus chain: calendar ↔ avatar
            binding.mainCalendarContainer.nextFocusLeftId = R.id.mainUserAvatarContainer
            binding.mainCalendarContainer.nextFocusRightId = R.id.mainUserAvatarContainer
            binding.mainUserAvatarContainer.nextFocusLeftId = R.id.mainCalendarContainer
            binding.mainUserAvatarContainer.nextFocusRightId = R.id.mainCalendarContainer

            // Observe tab changes
            lifecycleScope.launch {
                navPillsViewModel.currentTab.collect { tabIndex ->
                    switchTab(tabIndex)
                }
            }

            // Load initial tab
            var startTab = PrefManager.getVal<Int>(PrefName.DefaultStartUpTab)
            if (startTab > 1) {
                startTab = 0
                PrefManager.setVal(PrefName.DefaultStartUpTab, 0)
            }
            navPillsViewModel.setTab(startTab)
            switchTab(startTab)

            // Setup avatar and right rail drawer
            loadAvatar()
            setupRightRail()
        }

        var launched = false
        intent.extras?.let { extras ->
            val fragmentToLoad = extras.getString("FRAGMENT_TO_LOAD")
            val mediaId = extras.getInt("mediaId", -1)
            val commentId = extras.getInt("commentId", -1)
            val activityId = extras.getInt("activityId", -1)

            if (fragmentToLoad != null && mediaId != -1 && commentId != -1) {
                val detailIntent = Intent(this, MediaDetailsActivity::class.java).apply {
                    putExtra("FRAGMENT_TO_LOAD", fragmentToLoad)
                    putExtra("mediaId", mediaId)
                    putExtra("commentId", commentId)
                }
                launched = true
                startActivity(detailIntent)
            } else if (fragmentToLoad == "FEED" && activityId != -1) {
                if (!PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                    val feedIntent = Intent(this, FeedActivity::class.java).apply {
                        putExtra("FRAGMENT_TO_LOAD", "NOTIFICATIONS")
                        putExtra("activityId", activityId)
                    }
                    launched = true
                    startActivity(feedIntent)
                }
            } else if (fragmentToLoad == "NOTIFICATIONS" && activityId != -1) {
                Logger.log("MainActivity, onCreate: $activityId")
                if (!PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                    val notificationIntent = Intent(this, NotificationActivity::class.java).apply {
                        putExtra("activityId", activityId)
                    }
                    launched = true
                    startActivity(notificationIntent)
                }
            }
        }
        val offlineMode: Boolean = PrefManager.getVal(PrefName.OfflineMode)
        if (!isOnline(this)) {
            snackString(this@MainActivity.getString(R.string.no_internet_connection))
            startActivity(Intent(this, NoInternet::class.java))
        } else {
            if (offlineMode) {
                snackString(this@MainActivity.getString(R.string.no_internet_connection))
                startActivity(Intent(this, NoInternet::class.java))
            } else {
                val model: AnilistHomeViewModel by viewModels()

                //Load Data
                if (!load && !launched) {
                    scope.launch(Dispatchers.IO) {
                        model.loadMain(this@MainActivity)
                        val id = intent.extras?.getInt("mediaId", 0)
                        val isMAL = intent.extras?.getBoolean("mal") ?: false
                        val cont = intent.extras?.getBoolean("continue") ?: false
                        val mediaType = intent.extras?.getString("mediaType")
                        if (id != null && id != 0) {
                            val media = withContext(Dispatchers.IO) {
                                Anilist.query.getMedia(id, isMAL, mediaType)
                            }
                            if (media != null) {
                                media.cameFromContinue = cont
                                startActivity(
                                    Intent(this@MainActivity, MediaDetailsActivity::class.java)
                                        .putExtra("media", media as Serializable)
                                )
                            } else {
                                snackString(this@MainActivity.getString(R.string.anilist_not_found))
                            }
                        }
                        val username = intent.extras?.getString("username")
                        if (username != null) {
                            val nameInt = username.toIntOrNull()
                            if (nameInt != null) {
                                startActivity(
                                    Intent(this@MainActivity, ProfileActivity::class.java)
                                        .putExtra("userId", nameInt)
                                )
                            } else {
                                startActivity(
                                    Intent(this@MainActivity, ProfileActivity::class.java)
                                        .putExtra("username", username)
                                )
                            }
                        }
                    }
                    load = true
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!(PrefManager.getVal(PrefName.AllowOpeningLinks) as Boolean)) {
                        CustomBottomDialog.newInstance().apply {
                            title = "Allow Dantotsu to automatically open Anilist & MAL Links?"
                            val md = "Open settings & click +Add Links & select Anilist & Mal urls"
                            addView(TextView(this@MainActivity).apply {
                                val markWon =
                                    Markwon.builder(this@MainActivity)
                                        .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                                markWon.setMarkdown(this, md)
                            })

                            setNegativeButton(this@MainActivity.getString(R.string.no)) {
                                PrefManager.setVal(PrefName.AllowOpeningLinks, true)
                                dismiss()
                            }

                            setPositiveButton(this@MainActivity.getString(R.string.yes)) {
                                PrefManager.setVal(PrefName.AllowOpeningLinks, true)
                                tryWith(true) {
                                    startActivity(
                                        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS)
                                            .setData(Uri.parse("package:$packageName"))
                                    )
                                }
                                dismiss()
                            }
                        }.show(supportFragmentManager, "dialog")
                    }
                }
            }
        }
        if (PrefManager.getVal(PrefName.OC)) {
            AudioHelper.run(this, R.raw.audio)
            PrefManager.setVal(PrefName.OC, false)
        }
        val torrentManager = Injekt.get<TorrentAddonManager>()
        fun startTorrent() {
            if (torrentManager.isAvailable() && PrefManager.getVal(PrefName.TorrentEnabled)) {
                launchIO {
                    if (!TorrentServerService.isRunning()) {
                        TorrentServerService.start()
                    }
                }
            }
        }
        if (torrentManager.isInitialized.value == false) {
            torrentManager.isInitialized.observe(this) {
                if (it) {
                    startTorrent()
                }
            }
        } else {
            startTorrent()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (binding.homeNavRail.visibility == View.VISIBLE) {
                        hideHomeNavRail()
                        if (binding.homeNavRail.visibility == View.VISIBLE) return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val id = currentFocus?.id
                    if (id == R.id.homeNavHome || id == R.id.homeNavAnime || id == R.id.homeNavDiscovery || id == R.id.homeNavLibrary) {
                        hideHomeNavRail()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val id = currentFocus?.id
                    if (id == R.id.homeNavHome || id == R.id.homeNavAnime || id == R.id.homeNavDiscovery || id == R.id.homeNavLibrary) {
                        return true
                    }
                    if (binding.homeNavRail.visibility != View.VISIBLE) {
                        val focus = currentFocus
                        if (focus != null) {
                            var p = focus.parent
                            var inHorizontalRv = false
                            while (p != null) {
                                if (p is RecyclerView) {
                                    val lm = p.layoutManager
                                    if (lm != null && lm.canScrollHorizontally()) {
                                        val holder = p.findContainingViewHolder(focus)
                                        if (holder != null && holder.bindingAdapterPosition > 0) {
                                            inHorizontalRv = true
                                        } else if (p.canScrollHorizontally(-1)) {
                                            inHorizontalRv = true
                                        }
                                    }
                                    break
                                }
                                p = (p as? View)?.parent
                            }
                            if (!inHorizontalRv) {
                                val railWidth = (60f * resources.displayMetrics.density).toInt()
                                if (focus.left <= railWidth || focus.focusSearch(View.FOCUS_LEFT) == null) {
                                    showHomeNavRail()
                                    return true
                                }
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.homeNavRail.visibility != View.VISIBLE) {
                        showHomeNavRail()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onRestart() {
        super.onRestart()
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
    }

    override fun onResume() {
        super.onResume()
        loadAvatar()
        binding.homeNavRailBg.live = PrefManager.getVal(PrefName.LiveSideRail)
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist) && ::navPillsViewModel.isInitialized) {
            showHomeNavRail()
        }
    }

    private fun handleViewIntent(intent: Intent) {
        val uri: Uri? = intent.data
        try {
            if (uri == null) {
                throw Exception("Uri is null")
            }
            if (uri.scheme == "aniyomi" && uri.host == "add-repo") {
                val url = uri.getQueryParameter("url") ?: throw Exception("No url for repo import")
                val (prefName, name) = when (uri.scheme) {
                    "aniyomi" -> PrefName.AnimeExtensionRepos to "Anime"
                    else -> throw Exception("Invalid scheme")
                }
                val savedRepos: Set<String> = PrefManager.getVal(prefName)
                val newRepos = savedRepos.toMutableSet()
                AddRepositoryBottomSheet.addRepoWarning(this) {
                    newRepos.add(url)
                    PrefManager.setVal(prefName, newRepos)
                    toast("$name Extension Repo added")
                }
                return
            }
            if (intent.type == null) return
            val jsonString =
                contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Error reading file")
            val name =
                DocumentFile.fromSingleUri(this, uri)?.name ?: "settings"
            //.sani is encrypted, .ani is not
            if (name.endsWith(".sani")) {
                passwordAlertDialog { password ->
                    if (password != null) {
                        val salt = jsonString.copyOfRange(0, 16)
                        val encrypted = jsonString.copyOfRange(16, jsonString.size)
                        val decryptedJson = try {
                            PreferenceKeystore.decryptWithPassword(
                                password,
                                encrypted,
                                salt
                            )
                        } catch (e: Exception) {
                            toast("Incorrect password")
                            return@passwordAlertDialog
                        }
                        if (PreferencePackager.unpack(decryptedJson)) {
                            val newIntent = Intent(this, this.javaClass)
                            this.finish()
                            startActivity(newIntent)
                        }
                    } else {
                        toast("Password cannot be empty")
                    }
                }
            } else if (name.endsWith(".ani")) {
                val decryptedJson = jsonString.toString(Charsets.UTF_8)
                if (PreferencePackager.unpack(decryptedJson)) {
                    val newIntent = Intent(this, this.javaClass)
                    this.finish()
                    startActivity(newIntent)
                }
            } else {
                toast("Invalid file type")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Error importing settings")
        }
    }

    private fun passwordAlertDialog(callback: (CharArray?) -> Unit) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater).apply {
            userAgentTextBox.hint = "Password"
            subtitle.visibility = View.VISIBLE
            subtitle.text = getString(R.string.enter_password_to_decrypt_file)
        }
        customAlertDialog().apply {
            setTitle("Enter Password")
            setCustomView(dialogView.root)
            setPosButton(R.string.yes) {
                val editText = dialogView.userAgentTextBox
                if (editText.text?.isNotBlank() == true) {
                    editText.text?.toString()?.trim()?.toCharArray(password)
                    callback(password)
                } else {
                    toast("Password cannot be empty")
                }
            }
            setNegButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
            show()
        }
    }

    private fun loadAvatar() {
        binding.mainUserAvatar.loadImage(Anilist.avatar)
        val showRedDot = PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot)
        if (showRedDot == true) {
            binding.mainNotificationCount.isVisible = Anilist.unreadNotificationCount > 0
            binding.mainNotificationCount.text = Anilist.unreadNotificationCount.toString()
        } else {
            binding.mainNotificationCount.isVisible = false
        }
    }

    private fun setupHomeNavRail() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cornerPx = 16f * resources.displayMetrics.density
            binding.homeNavRail.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
            binding.homeNavRail.elevation = 10f
            binding.homeNavRail.clipToOutline = true
        }

        binding.homeNavRailBg.live = PrefManager.getVal(PrefName.LiveSideRail)

        binding.homeNavRailBg.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateHomeNavIconTints()
        }

        val pills = listOf(binding.homeNavHome, binding.homeNavAnime, binding.homeNavDiscovery, binding.homeNavLibrary)
        pills.forEachIndexed { index, pill ->
            pill.setOnClickListener {
                navPillsViewModel.setTab(index)
                hideHomeNavRail()
            }
            FocusEffectUtil.applyFocusListener(pill)
        }
    }

    private fun updateHomeNavIconTints() {
        val bg = binding.homeNavRailBg
        if (bg.height <= 0) return
        val pills = listOf(binding.homeNavHome, binding.homeNavAnime, binding.homeNavDiscovery, binding.homeNavLibrary)
        pills.forEach { pill ->
            val yCenter = pill.top + pill.height / 2f
            val fraction = yCenter / bg.height
            val color = bg.getColorAtFraction(fraction)
            val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
            val tintColor = if (luminance > 0.5) Color.parseColor("#2A2A2A") else Color.WHITE
            pill.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
        }
    }

    private fun showHomeNavRail() {
        binding.homeNavRail.apply {
            visibility = View.VISIBLE
            pivotY = 0f
            translationX = -60f * resources.displayMetrics.density
            scaleY = 0.3f
            alpha = 0f
        }
        binding.homeNavRail.post {
            ObjectAnimator.ofFloat(binding.homeNavRail, View.SCALE_Y, 1f).apply {
                interpolator = SpringInterpolator()
                duration = 700
            }.start()
            binding.homeNavRail.animate()
                .translationX(0f)
                .alpha(1f)
                .setInterpolator(DecelerateInterpolator())
                .setDuration(500)
                .start()
            updateHomeNavIconTints()
        }
        val tab = navPillsViewModel.currentTab.value
        val id = when (tab) {
            0 -> R.id.homeNavHome
            1 -> R.id.homeNavAnime
            2 -> R.id.homeNavDiscovery
            3 -> R.id.homeNavLibrary
            else -> R.id.homeNavHome
        }
        binding.root.findViewById<View>(id)?.requestFocus()
    }

    private fun hideHomeNavRail() {
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) return
        binding.homeNavRail.visibility = View.GONE
        val tag = currentFragmentTag
        if (tag != null) {
            supportFragmentManager.findFragmentByTag(tag)?.view?.let {
                it.requestFocus()
            }
        }
    }

    private fun setupRightRail() {
        val drawerItems = mapOf(
            R.id.rightRailNotifications to {
                startActivity(Intent(this, SettingsNotificationActivity::class.java))
            },
            R.id.rightRailExtensions to {
                startActivity(Intent(this, ExtensionsActivity::class.java))
            },
            R.id.rightRailProfile to {
                ContextCompat.startActivity(this, Intent(this, ProfileActivity::class.java)
                    .putExtra("userId", Anilist.userid), null)
            },
            R.id.rightRailAnimeList to {
                ContextCompat.startActivity(this, Intent(this, ani.dantotsu.media.user.ListActivity::class.java)
                    .putExtra("anime", true)
                    .putExtra("userId", Anilist.userid ?: 0)
                    .putExtra("username", Anilist.username), null)
            },
            R.id.rightRailSettings to {
                startActivity(Intent(this, ani.dantotsu.settings.SettingsActivity::class.java))
            },
            R.id.rightRailAccount to {
                startActivity(Intent(this, ani.dantotsu.settings.SettingsAccountActivity::class.java))
            },
            R.id.rightRailSync to {
                lifecycleScope.launch(Dispatchers.IO) {
                    ani.dantotsu.connections.syncPendingProgressUpdates()
                    ani.dantotsu.connections.syncPendingDeletions()
                }
                snackString("Sync triggered")
            },
            R.id.rightRailClearCache to {
                try {
                    cacheDir.deleteRecursively()
                    Glide.get(this).clearMemory()
                    snackString("Cache cleared")
                } catch (e: Exception) {
                    snackString("Failed to clear cache: ${e.message}")
                }
            },
            R.id.rightRailLogout to {
                customAlertDialog().apply {
                    setTitle("Log Out")
                    setMessage("Are you sure you want to log out?")
                    setPosButton("Yes") {
                        Anilist.removeSavedToken()
                        MAL.removeSavedToken()
                        startActivity(Intent(this@MainActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                    setNegButton("No", null)
                    show()
                }
            }
        )

        for ((id, action) in drawerItems) {
            val view = findViewById<View>(id)
            view.setOnClickListener {
                binding.mainDrawer.closeDrawer(Gravity.END)
                action()
            }
            view.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    binding.mainDrawer.closeDrawer(Gravity.END)
                    action()
                    true
                } else false
            }
            FocusEffectUtil.applyFocusListener(view)
        }

        binding.mainDrawer.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerOpened(drawerView: View) {
                findViewById<View>(R.id.rightRailNotifications).requestFocus()
            }
            override fun onDrawerClosed(drawerView: View) {}
        })
    }

    private fun populateRightRail() {
        findViewById<ImageView>(R.id.rightRailAvatar).loadImage(Anilist.avatar)
        findViewById<TextView>(R.id.rightRailUserName).text = Anilist.username ?: MAL.username ?: "User"
        findViewById<TextView>(R.id.rightRailUserEmail).text = "AniList ID: ${Anilist.userid ?: "—"}"
        findViewById<TextView>(R.id.rightRailEpisodesWatched).text = (Anilist.episodesWatched ?: 0).toString()
    }

}

private class SpringInterpolator(
    private val damping: Float = 6f,
    private val stiffness: Float = 10f
) : android.animation.TimeInterpolator {
    override fun getInterpolation(t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val decay = exp(-t * damping)
        val oscillation = cos(t * stiffness)
        return 1f - decay * oscillation
    }
}