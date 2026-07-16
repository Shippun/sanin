package ani.sanin.util

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

object NavPillCustomizer {

    fun getHeightDp(): Int = PrefManager.getVal<Int>(PrefName.NavPillHeight).coerceIn(32, 72)
    fun getWidthDp(): Int = PrefManager.getVal<Int>(PrefName.NavPillWidth).coerceIn(32, 72)
    fun getSpacingDp(): Int = PrefManager.getVal<Int>(PrefName.NavPillSpacing).coerceIn(0, 24)
    fun getIconPaddingDp(): Int = PrefManager.getVal<Int>(PrefName.NavPillIconPadding).coerceIn(4, 28)
    fun getIconColor(): Int = PrefManager.getVal<Int>(PrefName.NavPillIconColor)

    fun applyToPillList(pillList: LinearLayout) {
        val density = pillList.resources.displayMetrics.density
        val pillWidthPx = (getWidthDp() * density).toInt()
        val pillHeightPx = (getHeightDp() * density).toInt()
        val iconPaddingPx = (getIconPaddingDp() * density).toInt()
        val spacingPx = (getSpacingDp() * density).toInt()
        val iconColor = getIconColor()

        pillList.setPadding(pillList.paddingLeft, spacingPx, pillList.paddingRight, spacingPx)

        for (i in 0 until pillList.childCount) {
            val child = pillList.getChildAt(i)
            if (child is ImageButton) {
                val lp = child.layoutParams
                lp.width = pillWidthPx
                lp.height = pillHeightPx
                child.layoutParams = lp
                child.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
                val d = child.drawable
                if (d != null) {
                    d.setTint(iconColor)
                }
            }
        }
    }

    fun applyToPillPreview(preview: View) {
        val group = preview as? ViewGroup ?: return
        val density = group.resources.displayMetrics.density
        val width = getWidthDp()
        val height = getHeightDp()
        val spacing = getSpacingDp()
        val iconPadding = getIconPaddingDp()
        val iconColor = getIconColor()

        val previewH = (Math.max(height, 32) + spacing * 2) * density.toInt()
        val lp = group.layoutParams
        if (lp.height != previewH) {
            lp.height = previewH
            group.layoutParams = lp
        }

        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ImageButton) {
                val clp = child.layoutParams
                clp.width = (width * density).toInt()
                clp.height = (height * density).toInt()
                child.layoutParams = clp
                child.setPadding(
                    (iconPadding * density).toInt(),
                    (iconPadding * density).toInt(),
                    (iconPadding * density).toInt(),
                    (iconPadding * density).toInt()
                )
                val d = child.drawable
                if (d != null) {
                    d.setTint(iconColor)
                }
            }
        }
    }
}
