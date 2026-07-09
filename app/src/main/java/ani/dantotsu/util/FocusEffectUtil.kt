package ani.dantotsu.util

import android.animation.ObjectAnimator
import android.content.res.TypedArray
import android.graphics.Color
import android.widget.ImageButton
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

object FocusEffectUtil {

    private var lastFocusedView: View? = null
    private val activeAnimators = mutableMapOf<View, List<ObjectAnimator>>()
    private val savedForegrounds = mutableMapOf<View, Drawable?>()
    private val savedBackgrounds = mutableMapOf<View, Drawable?>()

    fun applyFocusListener(vararg views: View) {
        for (view in views) {
            view.onFocusChangeListener = null
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    if (lastFocusedView != v) {
                        resetView(lastFocusedView)
                        lastFocusedView = v
                    }
                    applyBorder(v, v is ImageButton)
                    applyFocusGain(v)
                } else {
                    removeBorder(v)
                    applyFocusLoss(v)
                }
            }
        }
    }

    fun applyFocusListener(focusView: View, borderTarget: View, isCircular: Boolean = false) {
        focusView.onFocusChangeListener = null
        focusView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if (lastFocusedView != v) {
                    resetView(lastFocusedView)
                    lastFocusedView = v
                }
                applyBorder(borderTarget, isCircular)
                applyFocusGain(borderTarget)
            } else {
                removeBorder(borderTarget)
                applyFocusLoss(borderTarget)
            }
        }
    }

    private fun resetView(v: View?) {
        if (v == null) return
        cancelAnimators(v)
        removeBorder(v)
        v.elevation = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationX = 0f
        v.translationY = 0f
        v.rotationY = 0f
        v.alpha = 1f
    }

    private fun getPrimaryColor(v: View): Int {
        val ta: TypedArray = v.context.theme.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorPrimary))
        val color = ta.getColor(0, Color.WHITE)
        ta.recycle()
        return color
    }

    private fun applyBorder(v: View, isCircular: Boolean = false) {
        val primaryColor = getPrimaryColor(v)
        val borderWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3f, v.resources.displayMetrics
        ).toInt()
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, v.resources.displayMetrics
        ).toInt()

        val borderDrawable = GradientDrawable().apply {
            setShape(if (isCircular) GradientDrawable.OVAL else GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(borderWidthPx, primaryColor)
            if (!isCircular) setCornerRadius(cornerRadius.toFloat())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            savedForegrounds[v] = v.foreground
            v.foreground = borderDrawable
        } else {
            if (v is androidx.cardview.widget.CardView) return
            val originalBg = v.background
            savedBackgrounds[v] = originalBg
            val layers = arrayOf(
                originalBg ?: GradientDrawable().apply { setColor(Color.TRANSPARENT) },
                borderDrawable
            )
            v.setBackgroundDrawable(LayerDrawable(layers))
        }
    }

    private fun removeBorder(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = savedForegrounds.remove(v) ?: savedForegrounds.remove(v)
        } else {
            if (v is androidx.cardview.widget.CardView) return
            val original = savedBackgrounds.remove(v)
            if (original != null) {
                v.setBackgroundDrawable(original)
            } else {
                v.background = null
            }
        }
    }

    private fun applyFocusGain(v: View) {
        cancelAnimators(v)
        v.animate().rotationYBy(360f).setDuration(400).start()
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
        v.animate().rotationY(0f).scaleX(1f).scaleY(1f).translationX(0f).setDuration(200).start()
        v.elevation = 0f
    }
}
