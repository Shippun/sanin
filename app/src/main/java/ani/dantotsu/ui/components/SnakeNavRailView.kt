package ani.dantotsu.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import ani.dantotsu.R

class SnakeNavRailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colors = intArrayOf(
        ContextCompat.getColor(context, R.color.snake_pale_white),
        ContextCompat.getColor(context, R.color.snake_pale_green),
        ContextCompat.getColor(context, R.color.snake_pale_purple),
        ContextCompat.getColor(context, R.color.snake_pale_white),
        ContextCompat.getColor(context, R.color.snake_pale_green),
    )

    private var gradientOffset = 0f
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var animator: ValueAnimator? = null

    var live = true
        set(value) {
            field = value
            if (value) startAnimation() else stopAnimation()
        }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                gradientOffset = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
        gradientOffset = 0f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (live) startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    fun getColorAtFraction(fraction: Float): Int {
        val h = height.coerceAtLeast(1).toFloat()
        val pos = (fraction * h) / h
        val adjusted = (pos + gradientOffset) % 1f
        val segment = adjusted * (colors.size - 1)
        val idx = segment.toInt().coerceAtMost(colors.size - 2)
        val local = segment - idx
        return lerpColor(colors[idx], colors[idx + 1], local)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat().coerceAtLeast(1f)

        val positions = floatArrayOf(
            (0f + gradientOffset) % 1f,
            (0.25f + gradientOffset) % 1f,
            (0.5f + gradientOffset) % 1f,
            (0.75f + gradientOffset) % 1f,
            (1f + gradientOffset) % 1f,
        )
        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            colors, positions,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, gradientPaint)

        drawScales(canvas, w, h)
    }

    private fun drawScales(canvas: Canvas, w: Float, h: Float) {
        val scaleH = 18f
        val scaleW = 16f
        var row = 0
        var y = -scaleH
        while (y < h + scaleH) {
            val offsetX = if (row % 2 == 0) 0f else scaleW / 2f
            var x = offsetX
            while (x < w + scaleW) {
                val path = Path()
                path.moveTo(x, y)
                path.quadTo(x + scaleW / 2f, y - scaleH / 2f, x + scaleW, y)
                path.quadTo(x + scaleW / 2f, y + scaleH / 2f, x, y)
                canvas.drawPath(path, scalePaint)
                x += scaleW * 1.5f
            }
            y += scaleH * 1.2f
            row++
        }
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
