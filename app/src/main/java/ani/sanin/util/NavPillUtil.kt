package ani.sanin.util

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.ui.components.SnakeNavRailView

fun applyNavPillCustomizations(
    railContainer: FrameLayout,
    railBg: SnakeNavRailView,
    pills: List<ImageButton>,
) {
    val ctx = railContainer.context
    val density = ctx.resources.displayMetrics.density
    val pillWidth = (PrefManager.getVal<Int>(PrefName.NavPillWidth) * density).toInt()
    val pillHeight = (PrefManager.getVal<Int>(PrefName.NavPillHeight) * density).toInt()
    val spacing = (PrefManager.getVal<Int>(PrefName.NavPillSpacing) * density).toInt()
    val cornerPercent = PrefManager.getVal<Int>(PrefName.NavPillCornerRadius)
    val railWidth = (pillWidth + 16 * density).toInt().coerceAtLeast(pillWidth + 8)

    val containerLp = railContainer.layoutParams
    containerLp.width = railWidth
    railContainer.layoutParams = containerLp

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val cornerRadius = railWidth * cornerPercent / 200f
        railContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        railContainer.clipToOutline = true
    }

    pills.forEach { pill ->
        val lp = pill.layoutParams
        lp.width = pillWidth
        lp.height = pillHeight
        pill.layoutParams = lp
        val mlp = pill.layoutParams as? ViewGroup.MarginLayoutParams
        mlp?.topMargin = spacing / 2
        mlp?.bottomMargin = spacing / 2
    }

    railBg.invalidate()
}
