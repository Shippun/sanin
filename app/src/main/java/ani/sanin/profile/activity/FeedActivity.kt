package ani.sanin.profile.activity

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
import ani.sanin.R
import ani.sanin.databinding.ActivityNotificationBinding
import ani.sanin.initActivity
import ani.sanin.profile.activity.ActivityFragment.Companion.ActivityType
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil

class FeedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationBinding
    private var selected: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.notificationTitle.text = getString(R.string.activities)
        binding.notificationToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.notificationNavRailBg.live = PrefManager.getVal(PrefName.LiveSideRail)
        FocusEffectUtil.applyFocusListener(binding.notificationBack)

        val navButtons = listOf(
            binding.notificationNavUser,
            binding.notificationNavMedia,
        )
        binding.notificationNavSubs.visibility = View.GONE
        binding.notificationNavComment.visibility = View.GONE
        binding.notificationNavMedia.setImageResource(R.drawable.ic_globe_24)

        navButtons.forEach { btn ->
            btn.setOnClickListener {
                val idx = navButtons.indexOf(btn)
                selected = idx
                binding.notificationViewPager.setCurrentItem(selected, false)
                updateNavTints(navButtons, selected)
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
            ViewPagerAdapter(supportFragmentManager, lifecycle, getOne)
        binding.notificationViewPager.setOffscreenPageLimit(4)
        binding.notificationViewPager.setCurrentItem(selected, false)
        updateNavTints(navButtons, selected)
        binding.notificationNavRail.visibility = View.GONE
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
                    if (id == R.id.notificationNavUser || id == R.id.notificationNavMedia) {
                        hideNotificationNavRail()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val id = currentFocus?.id
                    if (id == R.id.notificationNavUser || id == R.id.notificationNavMedia) {
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
        val id = if (selected == 0) R.id.notificationNavUser else R.id.notificationNavMedia
        binding.root.findViewById<View>(id)?.requestFocus()
    }

    private fun hideNotificationNavRail() {
        binding.notificationNavRail.visibility = View.GONE
        binding.notificationBack.requestFocus()
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

    override fun onResume() {
        super.onResume()
        val navButtons = listOf(binding.notificationNavUser, binding.notificationNavMedia)
        updateNavTints(navButtons, selected)
    }

    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val activityId: Int
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = if (activityId != -1) 1 else 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ActivityFragment.newInstance(
                    if (activityId != -1) ActivityType.ONE else ActivityType.USER,
                    activityId = activityId
                )

                else -> ActivityFragment.newInstance(ActivityType.GLOBAL)
            }
        }
    }
}
