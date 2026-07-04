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
    private val activeAnimators = mutableMapOf<View, List<ObjectAnimator>>()

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
        cancelAnimators(v)
        v.elevation = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationX = 0f
        v.alpha = 1f
    }

    private fun applyFocusGain(v: View) {
        cancelAnimators(v)
        val effect = PrefManager.getVal<Int>(PrefName.FocusEffect)
        when (effect) {
            0 -> { // Glow
                v.elevation = 12f
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
            }
            1 -> { // Breathing
                val animX = ObjectAnimator.ofFloat(v, "scaleX", 1.06f, 1.0f).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
                val animY = ObjectAnimator.ofFloat(v, "scaleY", 1.06f, 1.0f).apply {
                    duration = 800
                    interpolator = AccelerateDecelerateInterpolator()
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
                activeAnimators[v] = listOf(animX, animY)
            }
            2 -> { // Pulse
                val animX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.15f).apply {
                    duration = 400
                    interpolator = BounceInterpolator()
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
                val animY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.15f).apply {
                    duration = 400
                    interpolator = BounceInterpolator()
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
                activeAnimators[v] = listOf(animX, animY)
            }
            3 -> { // Shaking
                val shake = ObjectAnimator.ofFloat(v, "translationX", 0f, 8f, -8f, 4f, -4f, 0f).apply {
                    duration = 400
                    interpolator = AccelerateDecelerateInterpolator()
                    repeatCount = ObjectAnimator.INFINITE
                    start()
                }
                activeAnimators[v] = listOf(shake)
                v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(200).start()
            }
        }
    }

    private fun cancelAnimators(v: View) {
        activeAnimators.remove(v)?.forEach { it.cancel() }
    }

    private fun applyFocusLoss(v: View) {
        cancelAnimators(v)
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationX = 0f
        v.elevation = 0f
    }
}
