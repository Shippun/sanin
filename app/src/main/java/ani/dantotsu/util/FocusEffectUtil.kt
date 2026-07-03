package ani.dantotsu.util

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.CycleInterpolator
import androidx.core.view.ViewCompat
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

object FocusEffectUtil {

    private var lastFocusedView: View? = null

    fun applyFocusListener(vararg views: View) {
        for (view in views) {
            view.onFocusChangeListener = null
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    if (lastFocusedView != v) {
                        resetView(lastFocusedView)
                        lastFocusedView = v
                    }
                    applyFocusGain(v)
                } else {
                    applyFocusLoss(v)
                }
            }
        }
    }

    private fun resetView(v: View?) {
        if (v == null) return
        v.elevation = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.alpha = 1f
        v.animate().cancel()
    }

    private fun applyFocusGain(v: View) {
        val effect = PrefManager.getVal<Int>(PrefName.FocusEffect)
        when (effect) {
            0 -> { // Glow
                v.elevation = 12f
                v.scaleX = 1.05f
                v.scaleY = 1.05f
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
            }
            1 -> { // Breathing
                v.animate()
                    .scaleX(1.06f).scaleY(1.06f)
                    .setDuration(800)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        v.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(800)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                    }
                    .start()
            }
            2 -> { // Pulse
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.15f, 1f).apply {
                    duration = 400
                    interpolator = BounceInterpolator()
                    start()
                }
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.15f, 1f).apply {
                    duration = 400
                    interpolator = BounceInterpolator()
                    start()
                }
            }
            3 -> { // Shaking
                val shake = ObjectAnimator.ofFloat(v, "translationX", 0f, 8f, -8f, 4f, -4f, 0f)
                shake.duration = 400
                shake.interpolator = AccelerateDecelerateInterpolator()
                shake.start()
                v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(200).start()
            }
            else -> { // None
                // no effect
            }
        }
    }

    private fun applyFocusLoss(v: View) {
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationX = 0f
        v.elevation = 0f
    }
}
