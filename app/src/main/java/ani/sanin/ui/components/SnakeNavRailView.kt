package ani.sanin.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectDrawable
import ani.sanin.util.GlassEffectManager
import ani.sanin.util.NavPillCustomizer

class SnakeNavRailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedWidth = -1f
    private var cachedHeight = -1f

    private var glassDrawable: GlassEffectDrawable? = null

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> suggestedMinimumHeight
            else -> suggestedMinimumHeight
        }
        setMeasuredDimension(width, height)
    }

    var live = true
        set(value) {
            field = value
            invalidate()
        }

    var horizontal: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var _glassEnabled: Boolean = false
    fun setGlassEnabled(enabled: Boolean) {
        _glassEnabled = enabled && GlassEffectManager.isComponentEnabled(GlassComponent.NavPills)
        if (_glassEnabled) {
            if (glassDrawable == null) {
                val cornerPx = NavPillCustomizer.getCornerRadiusDp() * resources.displayMetrics.density
                glassDrawable = GlassEffectDrawable.applyToView(
                    view = this,
                    cornerRadiusDp = NavPillCustomizer.getCornerRadiusDp(),
                    blurRadius = GlassEffectManager.getBlurRadius(),
                    tintColor = GlassEffectManager.getTintColor()
                ).also { d ->
                    GlassEffectManager.applyParams(d)
                }
            }
            setWillNotDraw(false)
        } else {
            glassDrawable?.destroy()
            glassDrawable = null
            background = null
        }
        invalidate()
    }

    fun updateGlassParams() {
        if (!_glassEnabled) return
        glassDrawable?.let { d ->
            d.setTintColor(GlassEffectManager.getTintColor())
            d.setCornerRadius(NavPillCustomizer.getCornerRadiusDp() * resources.displayMetrics.density)
            GlassEffectManager.applyParams(d)
            d.invalidateCache()
            d.invalidateSelf()
        }
    }

    fun getColorAtFraction(fraction: Float): Int {
        val split = 0.33f
        if (fraction >= split) return Color.WHITE
        val t = fraction / split
        return lerpColor(Color.BLACK, Color.WHITE, t)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (_glassEnabled) return
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        drawGradient(canvas, w, h)
    }

    private fun drawGradient(canvas: Canvas, w: Float, h: Float) {
        if (w != cachedWidth || h != cachedHeight) {
            cachedWidth = w
            cachedHeight = h
            if (horizontal) {
                val split = w * 0.33f
                bgPaint.shader = LinearGradient(
                    0f, 0f, split, 0f,
                    Color.BLACK, Color.WHITE,
                    Shader.TileMode.CLAMP
                )
            } else {
                val split = h * 0.33f
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, split,
                    Color.BLACK, Color.WHITE,
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)
    }

    fun invalidateGlass() {
        glassDrawable?.invalidateCache()
        invalidate()
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
