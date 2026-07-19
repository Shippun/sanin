package ani.sanin.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsAnimationBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil

class SettingsAnimationActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsAnimationBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivitySettingsAnimationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        binding.animationContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }
        binding.animationBack.isFocusable = true
        binding.animationBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.animationEnabled.isChecked = PrefManager.getVal<Boolean>(PrefName.AnimationsEnabled)
        binding.animationEnabled.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.AnimationsEnabled, isChecked)
            restartApp()
        }

        binding.animationBanner.isChecked = PrefManager.getVal<Boolean>(PrefName.BannerAnimations)
        binding.animationBanner.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.BannerAnimations, isChecked)
            restartApp()
        }

        binding.animationLayout.isChecked = PrefManager.getVal<Boolean>(PrefName.LayoutAnimations)
        binding.animationLayout.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.LayoutAnimations, isChecked)
            restartApp()
        }

        binding.animationTrending.isChecked = PrefManager.getVal<Boolean>(PrefName.TrendingScroller)
        binding.animationTrending.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.TrendingScroller, isChecked)
        }

        binding.animationLiveSideRail.isChecked = PrefManager.getVal<Boolean>(PrefName.LiveSideRail)
        binding.animationLiveSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.LiveSideRail, isChecked)
        }

        binding.animationPlayerGesture.isChecked = PrefManager.getVal<Boolean>(PrefName.PlayerGestureAnimations)
        binding.animationPlayerGesture.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.PlayerGestureAnimations, isChecked)
        }

        binding.animationPlayerController.isChecked = PrefManager.getVal<Boolean>(PrefName.PlayerControllerAnimations)
        binding.animationPlayerController.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.PlayerControllerAnimations, isChecked)
        }

        binding.animationProfile.isChecked = PrefManager.getVal<Boolean>(PrefName.ProfileAnimations)
        binding.animationProfile.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ProfileAnimations, isChecked)
            restartApp()
        }

        binding.animationHome.isChecked = PrefManager.getVal<Boolean>(PrefName.HomeAnimations)
        binding.animationHome.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.HomeAnimations, isChecked)
            restartApp()
        }

        val map = mapOf(
            2f to 0.5f,
            1.75f to 0.625f,
            1.5f to 0.75f,
            1.25f to 0.875f,
            1f to 1f,
            0.75f to 1.25f,
            0.5f to 1.5f,
            0.25f to 1.75f,
            0f to 0f
        )
        val mapReverse = map.map { it.value to it.key }.toMap()
        binding.animationSpeed.value =
            mapReverse[PrefManager.getVal(PrefName.AnimationSpeed)] ?: 1f
        binding.animationSpeed.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.AnimationSpeed, map[value] ?: 1f)
        }

        setupCollapsibleSection(
            binding.animationSectionDisplay,
            binding.animationSectionDisplayContent
        )
        setupCollapsibleSection(
            binding.animationSectionNavigation,
            binding.animationSectionNavigationContent
        )
        setupCollapsibleSection(
            binding.animationSectionPlayer,
            binding.animationSectionPlayerContent
        )
        setupCollapsibleSection(
            binding.animationSectionFocus,
            binding.animationSectionFocusContent
        )
        setupCollapsibleSection(
            binding.animationSectionDialogs,
            binding.animationSectionDialogsContent
        )
        setupCollapsibleSection(
            binding.animationSectionOther,
            binding.animationSectionOtherContent
        )

        bindSwitch(binding.animationNavRail, PrefName.NavRailAnimations)
        bindSwitch(binding.animationPlayerOverlay, PrefName.PlayerOverlayAnimations)
        bindSwitch(binding.animationDoubleTap, PrefName.DoubleTapAnimations)
        bindSwitch(binding.animationSeekBar, PrefName.SeekBarAnimations)
        bindSwitch(binding.animationProgressShake, PrefName.ProgressShakeAnimations)
        bindSwitch(binding.animationFocusEffects, PrefName.FocusAnimations)
        bindSwitch(binding.animationKeyboardKey, PrefName.KeyboardKeyAnimations)
        bindSwitch(binding.animationTransitions, PrefName.TransitionAnimations)
        bindSwitch(binding.animationNotificationPopup, PrefName.NotificationPopupAnimations)
        bindSwitch(binding.animationCommentInput, PrefName.CommentInputAnimations)
        bindSwitch(binding.animationImageDialog, PrefName.ImageDialogAnimations)
        bindSwitch(binding.animationSplash, PrefName.SplashAnimations)
        bindSwitch(binding.animationLikeButton, PrefName.LikeButtonAnimations)
        bindSwitch(binding.animationIncognitoBanner, PrefName.IncognitoBannerAnimations)
        bindSwitch(binding.animationSearchHeader, PrefName.SearchHeaderAnimations)
        bindSwitch(binding.animationScrollToTop, PrefName.ScrollToTopAnimations)
        bindSwitch(binding.animationInstallSpinner, PrefName.InstallSpinnerAnimations)
        bindSwitch(binding.animationFilterReset, PrefName.FilterResetAnimations)
        bindSwitch(binding.animationDescriptionExpand, PrefName.DescriptionExpandAnimations)
        bindSwitch(binding.animationInfoPage, PrefName.InfoPageAnimations)
        bindSwitch(binding.animationNoInternet, PrefName.NoInternetAnimations)
        bindSwitch(binding.animationXpandable, PrefName.XpandableAnimations)
        bindSwitch(binding.animationAnimatedVectors, PrefName.AnimatedVectorDrawables)
        bindSwitch(binding.animationMisc, PrefName.MiscUiAnimations)

        FocusEffectUtil.applyFocusListener(
            binding.animationBack,
            binding.animationEnabled,
            binding.animationBanner,
            binding.animationLayout,
            binding.animationTrending,
            binding.animationLiveSideRail,
            binding.animationPlayerGesture,
            binding.animationPlayerController,
            binding.animationProfile,
            binding.animationHome,
            binding.animationNavRail,
            binding.animationPlayerOverlay,
            binding.animationDoubleTap,
            binding.animationSeekBar,
            binding.animationProgressShake,
            binding.animationFocusEffects,
            binding.animationKeyboardKey,
            binding.animationTransitions,
            binding.animationNotificationPopup,
            binding.animationCommentInput,
            binding.animationImageDialog,
            binding.animationSplash,
            binding.animationLikeButton,
            binding.animationIncognitoBanner,
            binding.animationSearchHeader,
            binding.animationScrollToTop,
            binding.animationInstallSpinner,
            binding.animationFilterReset,
            binding.animationDescriptionExpand,
            binding.animationInfoPage,
            binding.animationNoInternet,
            binding.animationXpandable,
            binding.animationAnimatedVectors,
            binding.animationMisc,
            binding.animationSectionDisplay,
            binding.animationSectionNavigation,
            binding.animationSectionPlayer,
            binding.animationSectionFocus,
            binding.animationSectionDialogs,
            binding.animationSectionOther,
        )
    }

    private fun bindSwitch(switch: com.google.android.material.materialswitch.MaterialSwitch, pref: PrefName) {
        switch.isChecked = PrefManager.getVal<Boolean>(pref)
        switch.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(pref, isChecked)
        }
    }

    private fun setupCollapsibleSection(header: TextView, content: View) {
        header.isFocusable = true
        header.setOnClickListener {
            val expanded = content.visibility == View.VISIBLE
            content.visibility = if (expanded) View.GONE else View.VISIBLE
            header.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (expanded) R.drawable.ic_round_keyboard_arrow_up_24
                else R.drawable.ic_round_keyboard_arrow_down_24,
                0, 0, 0
            )
        }
    }
}
