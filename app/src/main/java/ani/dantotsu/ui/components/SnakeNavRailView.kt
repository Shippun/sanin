package ani.dantotsu.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class SnakeNavRailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedWidth = -1f
    private var cachedHeight = -1f

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

    fun getColorAtFraction(fraction: Float): Int {
        val split = 0.33f
        if (fraction >= split) return Color.WHITE
        val t = fraction / split
        return lerpColor(Color.BLACK, Color.WHITE, t)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat().coerceAtLeast(1f)
        val split = h * 0.33f

        if (w != cachedWidth || h != cachedHeight) {
            cachedWidth = w
            cachedHeight = h
            bgPaint.shader = LinearGradient(
                0f, 0f, 0f, split,
                Color.BLACK, Color.WHITE,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, split, bgPaint)
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
