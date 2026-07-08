package ani.dantotsu.themes

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import ani.dantotsu.R
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions


class ThemeManager(private val context: Activity) {
    fun applyTheme(fromImage: Bitmap? = null) {
        applyUIScale()
        val darkTheme = isDarkThemeActive(context)
        val oledMode: Int = if (darkTheme) PrefManager.getVal(PrefName.OledMode) else 0
        val useOLED = oledMode >= 1
        val useCustomTheme: Boolean = PrefManager.getVal(PrefName.UseCustomTheme)
        val customTheme: Int = PrefManager.getVal(PrefName.CustomThemeInt)
        val useSource: Boolean = PrefManager.getVal(PrefName.UseSourceTheme)
        val useMaterial: Boolean = PrefManager.getVal(PrefName.UseMaterialYou)
        val accentColorIndex: Int = PrefManager.getVal(PrefName.AccentColor)

        val effectiveCustom = if (accentColorIndex > 0) {
            accentColorToInt(accentColorIndex)
        } else if (useCustomTheme) {
            customTheme
        } else {
            null
        }

        if (useSource) {
            val returnedEarly = applyDynamicColors(
                useMaterial,
                context,
                useOLED,
                fromImage,
                useCustom = effectiveCustom
            )
            if (!returnedEarly && effectiveCustom == null) return
        } else if (accentColorIndex > 0 && !useCustomTheme) {
            // Accent color only — skip DynamicColors (no-op or interfering on TV),
            // the mapped hardcoded theme is applied below via setTheme()
        } else if (useCustomTheme) {
            val returnedEarly =
                applyDynamicColors(useMaterial, context, useOLED, useCustom = effectiveCustom)
            if (!returnedEarly && effectiveCustom == null) return
        } else {
            val returnedEarly = applyDynamicColors(useMaterial, context, useOLED, useCustom = null)
            if (!returnedEarly) return
        }
        val theme: String = if (accentColorIndex > 0 && !useSource && !useCustomTheme) {
            accentColorToTheme(accentColorIndex)
        } else {
            PrefManager.getVal(PrefName.Theme)
        }

        val themeToApply = when (theme) {
            "BLUE" -> if (useOLED) R.style.Theme_Dantotsu_BlueOLED else R.style.Theme_Dantotsu_Blue
            "GREEN" -> if (useOLED) R.style.Theme_Dantotsu_GreenOLED else R.style.Theme_Dantotsu_Green
            "PINK" -> if (useOLED) R.style.Theme_Dantotsu_PinkOLED else R.style.Theme_Dantotsu_Pink
            "ORIAX" -> if (useOLED) R.style.Theme_Dantotsu_OriaxOLED else R.style.Theme_Dantotsu_Oriax
            "SAIKOU" -> if (useOLED) R.style.Theme_Dantotsu_SaikouOLED else R.style.Theme_Dantotsu_Saikou
            "RED" -> if (useOLED) R.style.Theme_Dantotsu_RedOLED else R.style.Theme_Dantotsu_Red
            "LAVENDER" -> if (useOLED) R.style.Theme_Dantotsu_LavenderOLED else R.style.Theme_Dantotsu_Lavender
            "OCEAN" -> if (useOLED) R.style.Theme_Dantotsu_OceanOLED else R.style.Theme_Dantotsu_Ocean
            "MONOCHROME (BETA)" -> if (useOLED) R.style.Theme_Dantotsu_MonochromeOLED else R.style.Theme_Dantotsu_Monochrome
            else -> if (useOLED) R.style.Theme_Dantotsu_BlueOLED else R.style.Theme_Dantotsu_Blue
        }

        val window = context.window
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = 0x00000000
        context.setTheme(themeToApply)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR

        if (oledMode == 2 || oledMode == 3 || oledMode == 4) {
            val tv = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
            val gradientDir: Int = PrefManager.getVal(PrefName.GradientDirection)
            OledBackgroundManager.apply(context, oledMode, tv.data, gradientDir)
        } else {
            OledBackgroundManager.remove(context)
        }
    }

    private fun applyUIScale() {
        val scale = PrefManager.getVal<Float>(PrefName.UIScale)
        if (scale == 1.0f) return
        val metrics: DisplayMetrics = context.resources.displayMetrics
        metrics.scaledDensity = metrics.density * scale
    }

    fun setWindowFlag(activity: Activity, bits: Int, on: Boolean) {
        val win: Window = activity.window
        val winParams: WindowManager.LayoutParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }

    private fun applyDynamicColors(
        useMaterialYou: Boolean,
        context: Context,
        useOLED: Boolean,
        bitmap: Bitmap? = null,
        useCustom: Int? = null
    ): Boolean {
        val builder = DynamicColorsOptions.Builder()
        var needMaterial = true

        if (bitmap != null) {
            builder.setContentBasedSource(bitmap)
            needMaterial = false
        } else if (useCustom != null) {
            builder.setContentBasedSource(useCustom)
            needMaterial = false
        }

        if (useOLED) {
            builder.setThemeOverlay(R.style.AppTheme_Amoled)
        }
        if (needMaterial && !useMaterialYou) return true

        val options = builder.build()

        val activity = context as Activity
        DynamicColors.applyToActivityIfAvailable(activity, options)

        if (useOLED) {
            val options2 = DynamicColorsOptions.Builder()
                .setThemeOverlay(R.style.AppTheme_Amoled)
                .build()
            DynamicColors.applyToActivityIfAvailable(activity, options2)
        }

        return false
    }

    private fun isDarkThemeActive(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> false
            else -> false
        }
    }

    companion object {
        fun accentColorToInt(index: Int): Int = when (index) {
            1 -> Color.parseColor("#F44336")
            2 -> Color.parseColor("#E91E63")
            3 -> Color.parseColor("#9C27B0")
            4 -> Color.parseColor("#673AB7")
            5 -> Color.parseColor("#3F51B5")
            6 -> Color.parseColor("#2196F3")
            7 -> Color.parseColor("#03A9F4")
            8 -> Color.parseColor("#00BCD4")
            9 -> Color.parseColor("#009688")
            10 -> Color.parseColor("#4CAF50")
            11 -> Color.parseColor("#8BC34A")
            12 -> Color.parseColor("#CDDC39")
            13 -> Color.parseColor("#FFEB3B")
            14 -> Color.parseColor("#FFC107")
            15 -> Color.parseColor("#FF9800")
            16 -> Color.parseColor("#FF5722")
            else -> Color.parseColor("#03A9F4")
        }

        fun accentColorToTheme(index: Int): String = when (index) {
            1, 16 -> "RED"
            2 -> "PINK"
            3, 4 -> "LAVENDER"
            5, 6, 7 -> "BLUE"
            8, 9 -> "OCEAN"
            10, 11, 12 -> "GREEN"
            13, 14, 15 -> "ORIAX"
            else -> "BLUE"
        }

        fun applyUIScale(activity: Activity) {
            val scale = PrefManager.getVal<Float>(PrefName.UIScale)
            if (scale == 1.0f) return
            val metrics: DisplayMetrics = activity.resources.displayMetrics
            metrics.scaledDensity = metrics.density * scale
        }

        enum class Theme(val theme: String) {
            BLUE("BLUE"),
            GREEN("GREEN"),
            PINK("PINK"),
            ORIAX("ORIAX"),
            SAIKOU("SAIKOU"),
            RED("RED"),
            LAVENDER("LAVENDER"),
            OCEAN("OCEAN"),
            MONOCHROME("MONOCHROME (BETA)");

            companion object {
                fun fromString(value: String): Theme {
                    return entries.find { it.theme == value } ?: BLUE
                }
            }
        }
    }
}
