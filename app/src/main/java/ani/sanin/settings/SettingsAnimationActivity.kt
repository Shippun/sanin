package ani.sanin.settings

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
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
        FocusEffectUtil.applyFocusListener(
            binding.animationBack,
            binding.animationEnabled,
            binding.animationBanner,
            binding.animationLayout,
            binding.animationTrending,
            binding.animationLiveSideRail,
            binding.animationNavPillTop,
        )

        binding.animationEnabled.isChecked = PrefManager.getVal(PrefName.AnimationsEnabled)
        binding.animationEnabled.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.AnimationsEnabled, isChecked)
            restartApp()
        }

        binding.animationBanner.isChecked = PrefManager.getVal(PrefName.BannerAnimations)
        binding.animationBanner.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.BannerAnimations, isChecked)
            restartApp()
        }

        binding.animationLayout.isChecked = PrefManager.getVal(PrefName.LayoutAnimations)
        binding.animationLayout.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.LayoutAnimations, isChecked)
            restartApp()
        }

        binding.animationTrending.isChecked = PrefManager.getVal(PrefName.TrendingScroller)
        binding.animationTrending.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.TrendingScroller, isChecked)
        }

        binding.animationLiveSideRail.isChecked = PrefManager.getVal(PrefName.LiveSideRail)
        binding.animationLiveSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.LiveSideRail, isChecked)
        }

        binding.animationNavPillTop.isChecked = PrefManager.getVal<Int>(PrefName.NavPillPosition) == 1
        binding.animationNavPillTop.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.NavPillPosition, if (isChecked) 1 else 0)
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
    }
}
