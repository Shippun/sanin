package ani.sanin.settings

import android.graphics.Color
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
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.color.SimpleColorWheelDialog

class SettingsAppearanceActivity : AppCompatActivity(),
    SimpleDialog.OnDialogResultListener {

    interface ColorPickerCallback {
        fun onColorSelected(color: Int)
    }

    private var colorPickerCallback: ColorPickerCallback? = null
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
            binding.appearanceGlassMaster,
            binding.appearanceGlassNavPills,
            binding.appearanceGlassSideRail,
            binding.appearanceGlassServerSheet,
            binding.appearanceGlassListEditor,
            binding.appearanceGlassSourceSelector,
            binding.appearanceGlassEpisodeDrawer,
            binding.appearanceGlassBlurRadius,
            binding.appearanceGlassTintOpacity,
            binding.appearanceGlassVibrancy,
            binding.appearanceGlassChromaticAberration,
            binding.appearanceGlassRefractionHeight,
            binding.appearanceGlassRefractionAmount,
            binding.appearanceGlassDepth,
            binding.appearanceGlassSurfaceTint,
            binding.appearanceGlassTextColor,
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

        binding.appearanceGlassMaster.isChecked = PrefManager.getVal(PrefName.GlassEffectEnabled)
        binding.appearanceGlassMaster.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectEnabled, isChecked)
        }
        binding.appearanceGlassNavPills.isChecked = PrefManager.getVal(PrefName.GlassEffectNavPills)
        binding.appearanceGlassNavPills.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectNavPills, isChecked)
        }
        binding.appearanceGlassSideRail.isChecked = PrefManager.getVal(PrefName.GlassEffectSideRail)
        binding.appearanceGlassSideRail.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectSideRail, isChecked)
        }
        binding.appearanceGlassServerSheet.isChecked = PrefManager.getVal(PrefName.GlassEffectServerSheet)
        binding.appearanceGlassServerSheet.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectServerSheet, isChecked)
        }
        binding.appearanceGlassListEditor.isChecked = PrefManager.getVal(PrefName.GlassEffectListEditor)
        binding.appearanceGlassListEditor.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectListEditor, isChecked)
        }
        binding.appearanceGlassSourceSelector.isChecked = PrefManager.getVal(PrefName.GlassEffectSourceSelector)
        binding.appearanceGlassSourceSelector.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectSourceSelector, isChecked)
        }
        binding.appearanceGlassEpisodeDrawer.isChecked = PrefManager.getVal(PrefName.GlassEffectEpisodeDrawer)
        binding.appearanceGlassEpisodeDrawer.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectEpisodeDrawer, isChecked)
        }

        binding.appearanceGlassBlurRadius.isFocusable = true
        binding.appearanceGlassBlurRadius.value = PrefManager.getVal(PrefName.GlassEffectBlurRadius) as Float
        binding.appearanceGlassBlurRadius.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectBlurRadius, value)
        }

        binding.appearanceGlassTintOpacity.isFocusable = true
        binding.appearanceGlassTintOpacity.value = PrefManager.getVal(PrefName.GlassEffectTintOpacity) as Float
        binding.appearanceGlassTintOpacity.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectTintOpacity, value)
        }

        binding.appearanceGlassVibrancy.isFocusable = true
        binding.appearanceGlassVibrancy.value = PrefManager.getVal(PrefName.GlassEffectVibrancy) as Float
        binding.appearanceGlassVibrancy.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectVibrancy, value)
        }

        binding.appearanceGlassChromaticAberration.isFocusable = true
        binding.appearanceGlassChromaticAberration.value = PrefManager.getVal(PrefName.GlassEffectChromaticAberration) as Float
        binding.appearanceGlassChromaticAberration.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectChromaticAberration, value)
        }

        binding.appearanceGlassRefractionHeight.isFocusable = true
        binding.appearanceGlassRefractionHeight.value = PrefManager.getVal(PrefName.GlassEffectRefractionHeight) as Float
        binding.appearanceGlassRefractionHeight.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectRefractionHeight, value)
        }

        binding.appearanceGlassRefractionAmount.isFocusable = true
        binding.appearanceGlassRefractionAmount.value = PrefManager.getVal(PrefName.GlassEffectRefractionAmount) as Float
        binding.appearanceGlassRefractionAmount.addOnChangeListener { _, value, _ ->
            PrefManager.setVal(PrefName.GlassEffectRefractionAmount, value)
        }

        binding.appearanceGlassDepth.isChecked = PrefManager.getVal(PrefName.GlassEffectDepth)
        binding.appearanceGlassDepth.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.GlassEffectDepth, isChecked)
        }

        binding.appearanceGlassSurfaceTint.isFocusable = true
        binding.appearanceGlassSurfaceTint.setOnClickListener {
            showColorPicker(
                originalColor = PrefManager.getVal(PrefName.GlassEffectSurfaceTint),
                title = "Surface Tint Color"
            ) { color ->
                PrefManager.setVal(PrefName.GlassEffectSurfaceTint, color)
            }
        }

        binding.appearanceGlassTextColor.isFocusable = true
        binding.appearanceGlassTextColor.setOnClickListener {
            showColorPicker(
                originalColor = PrefManager.getVal(PrefName.GlassEffectTextColor),
                title = "Glass Text Color"
            ) { color ->
                PrefManager.setVal(PrefName.GlassEffectTextColor, color)
            }
        }
    }

    private fun showColorPicker(
        originalColor: Int,
        title: String,
        callback: ColorPickerCallback,
    ) {
        colorPickerCallback = callback
        SimpleColorWheelDialog()
            .title(title)
            .color(originalColor)
            .alpha(true)
            .neg()
            .theme(R.style.MyPopup)
            .show(this, "glassColorPicker")
    }

    override fun onResult(
        dialogTag: String,
        which: Int,
        extras: Bundle,
    ): Boolean {
        if (dialogTag == "glassColorPicker" && which == SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE) {
            val color = extras.getInt(SimpleColorWheelDialog.COLOR)
            colorPickerCallback?.onColorSelected(color)
            return true
        }
        return false
    }
}
