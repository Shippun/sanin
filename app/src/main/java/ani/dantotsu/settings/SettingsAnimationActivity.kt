package ani.dantotsu.settings

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.dantotsu.databinding.ActivitySettingsAnimationBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.restartApp
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil

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
        binding.animationBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        FocusEffectUtil.applyFocusListener(
            binding.animationBack,
            binding.animationEnabled,
            binding.animationBanner,
            binding.animationLayout,
            binding.animationTrending,
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
            restartApp()
        }
    }
}
