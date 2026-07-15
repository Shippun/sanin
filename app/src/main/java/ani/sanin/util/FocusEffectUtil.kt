package ani.sanin.util

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

object FocusEffectUtil {

    private var lastFocusedView: View? = null
    private val savedForegrounds = mutableMapOf<View, Drawable?>()
    private val savedElevations = mutableMapOf<View, Float>()
    private val pulseHandlers = mutableMapOf<View, Handler>()

    private val icyBlue = Color.rgb(176, 224, 230)
    private val icyBlueGlow = Color.argb(45, 176, 224, 230)
    private val icyBlueStart = Color.argb(100, 190, 235, 245)

    private const val SCALE_FOCUSED = 1.06f
    private const val SCALE_PEAK = 1.08f
    private const val ELEVATION_BOOST = 12f

    fun mode(): Int = PrefManager.getVal<Int>(PrefName.FocusEffect)

    fun applyFocusListener(vararg views: View) {
        for (view in views) {
            view.onFocusChangeListener = null
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    if (lastFocusedView != v) {
                        resetView(lastFocusedView)
                        lastFocusedView = v
                    }
                    focusGained(v)
                } else {
                    focusLost(v)
                }
            }
            if (view.isFocused) {
                resetView(lastFocusedView)
                lastFocusedView = view
                focusGained(view)
            }
        }
    }

    fun applyFocusListener(focusView: View, borderTarget: View, isCircular: Boolean = false, fade: Boolean = false) {
        focusView.onFocusChangeListener = null
        focusView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if (lastFocusedView != borderTarget) {
                    resetView(lastFocusedView)
                    lastFocusedView = borderTarget
                }
                focusGained(borderTarget)
                if (fade) v.animate().alpha(1f).setDuration(200).start()
            } else {
                focusLost(borderTarget)
                if (fade) v.animate().alpha(0.85f).rotationY(0f).setDuration(200).start()
            }
        }
        if (focusView.isFocused) {
            resetView(lastFocusedView)
            lastFocusedView = borderTarget
            focusGained(borderTarget)
        }
    }

    private fun resetView(v: View?) {
        if (v == null) return
        stopPulse(v)
        removeGlow(v)
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationZ = 0f
        savedElevations.remove(v)?.let { v.elevation = it }
    }

    private fun focusGained(v: View) {
        val m = mode()
        if (m == 5) return

        savedElevations[v] = v.elevation
        v.animate().cancel()

        when (m) {
            0 -> icyGlow(v)
            1 -> glassLift(v)
            2 -> crystalEdge(v)
            3 -> snapBounce(v)
            4 -> serpentPulse(v)
        }
    }

    private fun focusLost(v: View) {
        if (mode() == 5) return
        stopPulse(v)
        removeGlow(v)

        v.animate().cancel()
        v.animate()
            .scaleX(1f).scaleY(1f)
            .translationZ(0f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()

        savedElevations.remove(v)?.let { v.elevation = it }
    }

    private fun applySteadyGlow(v: View) {
        val metrics = v.resources.displayMetrics
        val cornerRadius = if (v is androidx.cardview.widget.CardView && v.radius > 0) {
            v.radius
        } else {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, metrics)
        }

        val glowWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, metrics).toInt()
        val edgeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, metrics).toInt()

        val glowLayer = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(glowWidth, icyBlueGlow)
            setCornerRadius(cornerRadius)
        }

        val edgeLayer = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(edgeWidth, icyBlue)
            setCornerRadius(cornerRadius)
        }

        val layers = LayerDrawable(arrayOf(glowLayer, edgeLayer))
        savedForegrounds[v] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) v.foreground else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = layers
        }
    }

    private fun removeGlow(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val orig = savedForegrounds.remove(v)
            v.foreground = orig
        }
    }

    private fun stopPulse(v: View) {
        pulseHandlers[v]?.removeCallbacksAndMessages(null)
        pulseHandlers.remove(v)
    }

    // ── Modes ──

    private fun icyGlow(v: View) {
        v.animate()
            .scaleX(SCALE_FOCUSED).scaleY(SCALE_FOCUSED)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        v.translationZ = ELEVATION_BOOST
        applySteadyGlow(v)
    }

    private fun glassLift(v: View) {
        v.animate()
            .scaleX(SCALE_FOCUSED).scaleY(SCALE_FOCUSED)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        v.elevation = (savedElevations[v] ?: 0f) + 10f
        v.translationZ = ELEVATION_BOOST
    }

    private fun crystalEdge(v: View) {
        v.animate()
            .scaleX(SCALE_FOCUSED).scaleY(SCALE_FOCUSED)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        v.translationZ = ELEVATION_BOOST

        val metrics = v.resources.displayMetrics
        val cornerRadius = if (v is androidx.cardview.widget.CardView && v.radius > 0) {
            v.radius
        } else {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, metrics)
        }

        val glowWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, metrics).toInt()
        val edgeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, metrics).toInt()

        val glowLayer = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(glowWidth, icyBlueGlow)
            setCornerRadius(cornerRadius)
        }

        val edgeLayer = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(edgeWidth, icyBlue)
            setCornerRadius(cornerRadius)
        }

        val layers = LayerDrawable(arrayOf(glowLayer, edgeLayer))
        savedForegrounds[v] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) v.foreground else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = layers
        }

        val startWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, metrics).toInt()
        glowLayer.setStroke(startWidth, icyBlueStart)
        v.invalidate()

        val anim = ValueAnimator.ofInt(startWidth, glowWidth)
        anim.duration = 300
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener { a ->
            glowLayer.setStroke(a.animatedValue as Int, icyBlueGlow)
            v.invalidate()
        }
        anim.start()
    }

    private fun snapBounce(v: View) {
        v.animate()
            .scaleX(SCALE_PEAK).scaleY(SCALE_PEAK)
            .setDuration(100)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                v.animate()
                    .scaleX(SCALE_FOCUSED).scaleY(SCALE_FOCUSED)
                    .setDuration(80)
                    .setInterpolator(OvershootInterpolator(0.6f))
                    .start()
            }
            .start()

        v.translationZ = ELEVATION_BOOST
    }

    private fun serpentPulse(v: View) {
        v.animate()
            .scaleX(SCALE_FOCUSED).scaleY(SCALE_FOCUSED)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()

        v.translationZ = ELEVATION_BOOST

        val metrics = v.resources.displayMetrics
        val cornerRadius = if (v is androidx.cardview.widget.CardView && v.radius > 0) {
            v.radius
        } else {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, metrics)
        }

        val glowWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, metrics).toInt()
        val edgeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, metrics).toInt()

        val glowLayer = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(glowWidth, icyBlueGlow)
            setCornerRadius(cornerRadius)
        }

        val edgeLayer = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            setColor(Color.TRANSPARENT)
            setStroke(edgeWidth, icyBlue)
            setCornerRadius(cornerRadius)
        }

        val layers = LayerDrawable(arrayOf(glowLayer, edgeLayer))
        savedForegrounds[v] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) v.foreground else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            v.foreground = layers
        }

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            var pulsing = false
            override fun run() {
                if (pulsing || !v.isFocused) return
                pulsing = true
                val extra = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, v.resources.displayMetrics).toInt()
                val pw = glowWidth + extra
                val pc = Color.argb(65, 185, 230, 240)

                val expand = ValueAnimator.ofInt(glowWidth, pw)
                expand.duration = 400
                expand.interpolator = DecelerateInterpolator()
                expand.addUpdateListener { a ->
                    if (v.isFocused) {
                        glowLayer.setStroke(a.animatedValue as Int, pc)
                        v.invalidate()
                    }
                }
                expand.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!v.isFocused) { pulsing = false; return }
                        val contract = ValueAnimator.ofInt(pw, glowWidth)
                        contract.duration = 400
                        contract.interpolator = DecelerateInterpolator()
                        contract.addUpdateListener { a ->
                            if (v.isFocused) {
                                glowLayer.setStroke(a.animatedValue as Int, icyBlueGlow)
                                v.invalidate()
                            } else {
                                glowLayer.setStroke(glowWidth, icyBlueGlow)
                                v.invalidate()
                            }
                        }
                        contract.addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                pulsing = false
                            }
                        })
                        contract.start()
                    }
                })
                expand.start()

                handler.postDelayed(this, 1800L + (Math.random() * 400).toLong())
            }
        }
        pulseHandlers[v] = handler
        handler.postDelayed(runnable, 800)
    }
}
