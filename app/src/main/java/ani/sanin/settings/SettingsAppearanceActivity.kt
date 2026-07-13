package ani.sanin.settings

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import ani.sanin.R
import ani.sanin.databinding.ActivitySettingsAppearanceBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.customAlertDialog

class SettingsAppearanceActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsAppearanceBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivitySettingsAppearanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)
        binding.appearanceContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }
        binding.appearanceBack.isFocusable = true
        binding.appearanceBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        FocusEffectUtil.applyFocusListener(
            binding.appearanceBack,
            binding.appearanceCardSize,
            binding.appearanceCardStyle,
            binding.appearanceCardOrientation,
            binding.appearanceCardTitlePosition,
            binding.appearanceHideRedDot,
            binding.appearanceBlurBanners,
            binding.appearancePersistSideRail,
            binding.appearanceBannerBrightness,
            binding.appearanceCardGradientIntensity,
            binding.appearanceStandardCardRoundness,
            binding.appearanceContinueWatchingCardRoundness,
            binding.appearanceBlurRadius,
            binding.appearanceBlurSampling,
            binding.appearanceUiScale,
        )

        binding.appearanceCardSize.isFocusable = true
        binding.appearanceCardSize.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Size")
                val labels = arrayOf("Small (0.5x)", "Medium (0.75x)", "Normal (1x)", "Large (1.25x)", "X-Large (1.5x)", "XX-Large (1.75x)", "XXX-Large (2.0x)")
                val values = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                val current = PrefManager.getVal<Float>(PrefName.CardSize)
                val currentIdx = values.indexOfFirst { it == current }.coerceAtLeast(0)
                singleChoiceItems(labels, currentIdx) { index ->
                    PrefManager.setVal(PrefName.CardSize, values[index])
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardStyle.isFocusable = true
        binding.appearanceCardStyle.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Style")
                val labels = arrayOf("Rounded", "Compact")
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.CardStyle).coerceAtMost(1)) { index ->
                    PrefManager.setVal(PrefName.CardStyle, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardOrientation.isFocusable = true
        binding.appearanceCardOrientation.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Orientation")
                val labels = arrayOf("Landscape", "Portrait")
                val current = PrefManager.getVal<Int>(PrefName.CardOrientation)
                singleChoiceItems(labels, current) { index ->
                    PrefManager.setVal(PrefName.CardOrientation, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardTitlePosition.isFocusable = true
        binding.appearanceCardTitlePosition.setOnClickListener {
            customAlertDialog().apply {
                setTitle("Card Title Position")
                val labels = arrayOf("Bottom Overlay", "Below Card", "Hidden")
                val current = PrefManager.getVal<Int>(PrefName.CardTitlePosition)
                singleChoiceItems(labels, current) { index ->
                    PrefManager.setVal(PrefName.CardTitlePosition, index)
                    restartApp()
                }
                show()
            }
        }

        binding.appearanceCardGradientIntensity.isFocusable = true
        binding.appearanceCardGradientIntensity.value = PrefManager.getVal(PrefName.CardGradientIntensity)
        binding.appearanceCardGradientIntensity.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.CardGradientIntensity, value)
        }

        binding.appearanceStandardCardRoundness.isFocusable = true
        binding.appearanceStandardCardRoundness.value =
            PrefManager.getVal<Int>(PrefName.StandardCardRoundness).toFloat()
        binding.appearanceStandardCardRoundness.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.StandardCardRoundness, value.toInt())
        }

        binding.appearanceContinueWatchingCardRoundness.isFocusable = true
        binding.appearanceContinueWatchingCardRoundness.value =
            PrefManager.getVal<Int>(PrefName.ContinueWatchingCardRoundness).toFloat()
        binding.appearanceContinueWatchingCardRoundness.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.ContinueWatchingCardRoundness, value.toInt())
        }

        binding.appearanceHideRedDot.isChecked =
            !PrefManager.getVal<Boolean>(PrefName.ShowNotificationRedDot)
        binding.appearanceHideRedDot.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowNotificationRedDot, !isChecked)
        }

        binding.appearanceBlurBanners.isChecked = PrefManager.getVal(PrefName.BlurBanners)
        binding.appearanceBlurBanners.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.BlurBanners, isChecked)
        }
        binding.appearanceBlurRadius.isFocusable = true
        binding.appearanceBlurRadius.value = PrefManager.getVal(PrefName.BlurRadius) as Float
        binding.appearanceBlurRadius.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.BlurRadius, value)
        }
        binding.appearanceBlurSampling.isFocusable = true
        binding.appearanceBlurSampling.value = PrefManager.getVal(PrefName.BlurSampling) as Float
        binding.appearanceBlurSampling.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.BlurSampling, value)
        }

        binding.appearanceUiScale.isFocusable = true
        binding.appearanceUiScale.value = PrefManager.getVal(PrefName.UIScale)
        binding.appearanceUiScale.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.UIScale, value)
        }

        binding.appearanceBannerBrightness.isFocusable = true
        binding.appearanceBannerBrightness.value = PrefManager.getVal(PrefName.BannerBrightness)
        binding.appearanceBannerBrightness.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.BannerBrightness, value)
        }

        binding.appearancePersistSideRail.isChecked = PrefManager.getVal(PrefName.SideRailPersist)
        binding.appearancePersistSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.SideRailPersist, isChecked)
        }
    }
}
