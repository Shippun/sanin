package ani.sanin.profile.notification

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.ActivityNotificationBinding
import ani.sanin.initActivity
import ani.sanin.profile.notification.NotificationFragment.Companion.NotificationType.COMMENT
import ani.sanin.profile.notification.NotificationFragment.Companion.NotificationType.MEDIA
import ani.sanin.profile.notification.NotificationFragment.Companion.NotificationType.ONE
import ani.sanin.profile.notification.NotificationFragment.Companion.NotificationType.SUBSCRIPTION
import ani.sanin.profile.notification.NotificationFragment.Companion.NotificationType.USER
import ani.sanin.profile.notification.NotificationFragment.Companion.newInstance
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil

class NotificationActivity : AppCompatActivity() {
    lateinit var binding: ActivityNotificationBinding
    private var selected: Int = 0
    private val CommentsEnabled = PrefManager.getVal<Int>(PrefName.CommentsEnabled) == 1
    private var userCount: Int = 0
    private var mediaCount: Int = 0
    private var subsCount: Int = 0
    private var commentCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.notificationTitle.text = getString(R.string.notifications)
        binding.notificationToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.notificationNavRailBg.live = PrefManager.getVal(PrefName.LiveSideRail)
        FocusEffectUtil.applyFocusListener(binding.notificationBack)
        val cornerPx = 16f * resources.displayMetrics.density
        binding.notificationNavRail.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
        binding.notificationNavRail.elevation = 10f
        binding.notificationNavRail.clipToOutline = true

        val navButtons = listOf(
            binding.notificationNavUser,
            binding.notificationNavMedia,
            binding.notificationNavSubs,
            binding.notificationNavComment,
        )

        val visibleButtons = mutableListOf(
            binding.notificationNavUser,
            binding.notificationNavMedia,
            binding.notificationNavSubs,
        )
        if (CommentsEnabled) {
            visibleButtons.add(binding.notificationNavComment)
        } else {
            binding.notificationNavComment.visibility = View.GONE
        }

        updateCounts()
        navButtons.forEach { btn ->
            btn.setOnClickListener {
                val idx = navButtons.indexOf(btn)
                if (idx >= 0 && idx < visibleButtons.size) {
                    selected = idx
                    binding.notificationViewPager.setCurrentItem(selected, false)
                    updateNavTints(navButtons, selected)
                }
            }
            FocusEffectUtil.applyFocusListener(btn)
        }

        binding.notificationBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val getOne = intent.getIntExtra("activityId", -1)
        if (getOne != -1) {
            navButtons.forEach { it.visibility = View.GONE }
        }
        binding.notificationViewPager.isUserInputEnabled = false
        binding.notificationViewPager.adapter =
            ViewPagerAdapter(supportFragmentManager, lifecycle, getOne, CommentsEnabled) { type, reset ->
                if (reset) {
                    when (type) {
                        USER -> userCount = 0
                        MEDIA -> mediaCount = 0
                        SUBSCRIPTION -> subsCount = 0
                        COMMENT -> if (CommentsEnabled) commentCount = 0
                        ONE -> {}
                    }
                    saveCounts()
                }
            }
        binding.notificationViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    selected = position
                    updateNavTints(navButtons, selected)
                    fragments[position]?.onVisible()
                }
            }
        )
        binding.notificationViewPager.setCurrentItem(selected, false)
        updateNavTints(navButtons, selected)
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
            showNotificationNavRail()
        } else {
            binding.notificationNavRail.visibility = View.GONE
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (binding.notificationNavRail.visibility == View.VISIBLE) {
                        hideNotificationNavRail()
                        if (binding.notificationNavRail.visibility == View.VISIBLE) return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val id = currentFocus?.id
                    if (id == R.id.notificationNavUser || id == R.id.notificationNavMedia ||
                        id == R.id.notificationNavSubs || id == R.id.notificationNavComment) {
                        hideNotificationNavRail()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val id = currentFocus?.id
                    if (id == R.id.notificationNavUser || id == R.id.notificationNavMedia ||
                        id == R.id.notificationNavSubs || id == R.id.notificationNavComment) {
                        return true
                    }
                    if (binding.notificationNavRail.visibility != View.VISIBLE) {
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
                                    showNotificationNavRail()
                                    return true
                                }
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.notificationNavRail.visibility != View.VISIBLE) {
                        showNotificationNavRail()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showNotificationNavRail() {
        binding.notificationNavRail.apply {
            visibility = View.VISIBLE
            pivotY = 0f
            translationX = -60f * resources.displayMetrics.density
            scaleY = 0.3f
            alpha = 0f
        }
        binding.notificationNavRail.post {
            ObjectAnimator.ofFloat(binding.notificationNavRail, View.SCALE_Y, 1f).apply {
                interpolator = OvershootInterpolator()
                duration = 500
            }.start()
            binding.notificationNavRail.animate()
                .translationX(0f)
                .alpha(1f)
                .setInterpolator(DecelerateInterpolator())
                .setDuration(500)
                .start()
        }
        val id = when (selected) {
            0 -> R.id.notificationNavUser
            1 -> R.id.notificationNavMedia
            2 -> R.id.notificationNavSubs
            3 -> R.id.notificationNavComment
            else -> R.id.notificationNavUser
        }
        binding.root.findViewById<View>(id)?.requestFocus()
    }

    private fun hideNotificationNavRail() {
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) return
        binding.notificationNavRail.visibility = View.GONE
        binding.notificationViewPager.requestFocus()
    }

    private fun updateNavTints(buttons: List<android.widget.ImageButton>, selectedIndex: Int) {
        val ta: TypedArray = theme.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorPrimary))
        val primary = ta.getColor(0, Color.WHITE)
        ta.recycle()
        buttons.forEachIndexed { i, btn ->
            btn.imageTintList = ColorStateList.valueOf(if (i == selectedIndex) primary else Color.WHITE)
            btn.alpha = if (i == selectedIndex) 1f else 0.6f
        }
    }

    private fun updateCounts() {
        userCount = PrefManager.getVal(PrefName.UnreadUserNotifications, 0)
        mediaCount = PrefManager.getVal(PrefName.UnreadMediaNotifications, 0)
        subsCount = PrefManager.getVal(PrefName.UnreadSubscriptionNotifications, 0)
        commentCount = PrefManager.getVal(PrefName.UnreadCommentNotifications, 0)
    }

    private fun saveCounts() {
        PrefManager.setVal(PrefName.UnreadUserNotifications, userCount)
        PrefManager.setVal(PrefName.UnreadMediaNotifications, mediaCount)
        PrefManager.setVal(PrefName.UnreadSubscriptionNotifications, subsCount)
        PrefManager.setVal(PrefName.UnreadCommentNotifications, commentCount)
        Anilist.unreadNotificationCount = subsCount + commentCount
    }

    override fun onResume() {
        super.onResume()
        updateCounts()
        if (PrefManager.getVal<Boolean>(PrefName.SideRailPersist)) {
            binding.notificationNavRail.visibility = View.VISIBLE
        }
    }

    val fragments = mutableMapOf<Int, NotificationFragment>()
    private inner class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        val id: Int = -1,
        val commentsEnabled: Boolean,
        private val countResetCallback: (NotificationFragment.Companion.NotificationType, Boolean) -> Unit
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = if (id != -1) 1 else if (commentsEnabled) 4 else 3

        override fun createFragment(position: Int): Fragment {
            val fragment = when (position) {
                0 -> newInstance(if (id != -1) ONE else USER, id, countResetCallback)
                1 -> newInstance(MEDIA, countResetCallback = countResetCallback)
                2 -> newInstance(SUBSCRIPTION, countResetCallback = countResetCallback)
                3 -> newInstance(COMMENT, countResetCallback = countResetCallback)
                else -> newInstance(MEDIA, countResetCallback = countResetCallback)
            }
            fragments[position] = fragment
            return fragment
        }
    }
}
