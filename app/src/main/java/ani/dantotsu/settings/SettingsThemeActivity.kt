package ani.dantotsu.settings

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.R
import ani.dantotsu.databinding.ActivitySettingsThemeBinding
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.restartApp
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.FocusEffectUtil
import ani.dantotsu.util.customAlertDialog

class SettingsThemeActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsThemeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsThemeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            settingsThemeLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            onBackPressedDispatcher.addCallback(context) {
                finish()
            }
            themeSettingsBack.isFocusable = true
            themeSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            FocusEffectUtil.applyFocusListener(themeSettingsBack)

            var previous: View = when (PrefManager.getVal<Int>(PrefName.DarkMode)) {
                0 -> themeAuto
                1 -> themeLight
                2 -> themeDark
                else -> themeAuto
            }
            previous.alpha = 1f
            fun uiTheme(mode: Int, current: View) {
                previous.alpha = 0.33f
                previous = current
                current.alpha = 1f
                PrefManager.setVal(PrefName.DarkMode, mode)
                restartApp()
            }
            themeLight.isFocusable = true
            FocusEffectUtil.applyFocusListener(themeLight)
            themeLight.setOnClickListener { uiTheme(1, it) }
            themeDark.isFocusable = true
            FocusEffectUtil.applyFocusListener(themeDark)
            themeDark.setOnClickListener { uiTheme(2, it) }
            themeAuto.isFocusable = true
            FocusEffectUtil.applyFocusListener(themeAuto)
            themeAuto.setOnClickListener { uiTheme(0, it) }

            val accentColors = arrayOf(
                0 to "Default", 1 to "Red", 2 to "Pink", 3 to "Purple",
                4 to "Deep Purple", 5 to "Indigo", 6 to "Blue", 7 to "Light Blue",
                8 to "Cyan", 9 to "Teal", 10 to "Green", 11 to "Light Green",
                12 to "Lime", 13 to "Yellow", 14 to "Amber", 15 to "Orange",
                16 to "Deep Orange"
            )

            settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = "Accent Color",
                        desc = "Choose your app accent color",
                        icon = R.drawable.ic_palette,
                        onClick = {
                            customAlertDialog().apply {
                                setTitle("Accent Color")
                                val labels = accentColors.map { it.second }.toTypedArray()
                                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.AccentColor)) { index ->
                                    PrefManager.setVal(PrefName.AccentColor, accentColors[index].first)
                                    restartApp()
                                }
                                show()
                            }
                        },
                    ),
                    Settings(
                        type = 1,
                        name = "OLED Mode",
                        desc = "OLED background effects",
                        icon = R.drawable.ic_round_brightness_4_24,
                        onClick = {
                            customAlertDialog().apply {
                                setTitle("OLED Background Mode")
                                val labels = arrayOf(
                                    "Off\nNormal theme background",
                                    "Pure AMOLED\nPure black background",
                                    "Glow Spots\nBlack + radial glow orbs",
                                    "Gradient\nBlack + primary color gradient",
                                    "Vignette\nColored vignette from edges"
                                )
                                singleChoiceItems(labels, PrefManager.getVal<Int>(PrefName.OledMode)) { index ->
                                    PrefManager.setVal(PrefName.OledMode, index)
                                    restartApp()
                                }
                                show()
                            }
                        },
                    ),
                    Settings(
                        type = 2,
                        name = "Swap Colors",
                        desc = "Swap primary and secondary theme colors",
                        icon = R.drawable.swap_horizontal_circle_24,
                        isChecked = PrefManager.getVal(PrefName.SwapColors),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.SwapColors, isChecked)
                            restartApp()
                        },
                    ),
                )
            )
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) &&
            binding.themeSwitcher?.hasFocus() == true
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
