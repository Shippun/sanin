package ani.sanin.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
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
import ani.sanin.util.applyNavPillCustomizations
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
            binding.appearanceNavPillWidth,
            binding.appearanceNavPillHeight,
            binding.appearanceNavPillSpacing,
            binding.appearanceNavPillIconSize,
            binding.appearanceNavPillCorner,
            binding.appearanceNavPillBgStyle,
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

        updateNavPillPreview()
        setupNavPillSetting(
            binding.appearanceNavPillWidth, "Pill Width", PrefName.NavPillWidth, 24, 80
        ) { updateNavPillPreview() }
        setupNavPillSetting(
            binding.appearanceNavPillHeight, "Pill Height", PrefName.NavPillHeight, 24, 80
        ) { updateNavPillPreview() }
        setupNavPillSetting(
            binding.appearanceNavPillSpacing, "Pill Spacing", PrefName.NavPillSpacing, 0, 32
        ) { updateNavPillPreview() }
        setupNavPillSetting(
            binding.appearanceNavPillIconSize, "Icon Size", PrefName.NavPillIconSize, 4, 24
        ) { updateNavPillPreview() }
        setupNavPillSetting(
            binding.appearanceNavPillCorner, "Rail Corner", PrefName.NavPillCornerRadius, 0, 100
        ) { updateNavPillPreview() }

        binding.appearanceNavPillBgStyle.setOnClickListener {
            val labels = arrayOf("Top Dark → Bottom Light", "Top Light → Bottom Dark")
            customAlertDialog().apply {
                setTitle("Background Style")
                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.NavPillBgStyle)) { index ->
                    PrefManager.setVal(PrefName.NavPillBgStyle, index)
                    updateNavPillPreview()
                }
                show()
            }
        }
    }

    private fun setupNavPillSetting(
        button: View, title: String, pref: PrefName, min: Int, max: Int,
        onChanged: () -> Unit
    ) {
        button.setOnClickListener {
            val current = PrefManager.getVal<Int>(pref)
            val layout = LayoutInflater.from(this).inflate(R.layout.dialog_seekbar, null)
            val valueText = layout.findViewById<TextView>(R.id.dialogSeekbarValue)
            val seekBar = layout.findViewById<SeekBar>(R.id.dialogSeekbar)
            seekBar.max = max - min
            seekBar.progress = current - min
            valueText.text = "${current}dp"
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    valueText.text = "${v}dp"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
            customAlertDialog().apply {
                setTitle(title)
                setCustomView(layout)
                setPosButton(R.string.ok) {
                    val v = seekBar.progress + min
                    PrefManager.setVal(pref, v)
                    onChanged()
                }
                setNegButton(R.string.cancel, null)
                show()
            }
        }
    }

    private fun updateNavPillPreview() {
        val previewPills = listOf(
            binding.navPillPreview1,
            binding.navPillPreview2,
            binding.navPillPreview3,
        )
        applyNavPillCustomizations(
            railContainer = binding.navPillPreviewContainer,
            railBg = binding.navPillPreviewBg,
            pills = previewPills,
        )
        val bg = binding.navPillPreviewBg
        previewPills.forEach { pill ->
            val yCenter = pill.top + pill.height / 2f
            val fraction = if (bg.height > 0) yCenter / bg.height else 0f
            val color = bg.getColorAtFraction(fraction)
            val luminance = (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(color)) / 255.0
            val tint = if (luminance > 0.5) android.graphics.Color.parseColor("#2A2A2A") else android.graphics.Color.WHITE
            pill.imageTintList = android.content.res.ColorStateList.valueOf(tint)
        }
    }
}
