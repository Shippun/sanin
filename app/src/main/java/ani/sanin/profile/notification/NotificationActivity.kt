package ani.sanin.profile.notification

import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
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
