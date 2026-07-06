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
        style = Paint.Style.FILL
    }
    private val keelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val scaleEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    private var animator: ValueAnimator? = null

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

        val scroll = gradientOffset * h
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

        drawScales(canvas, w, h, scroll)
    }

    private val scalePath = buildScalePath()

    private fun drawScales(canvas: Canvas, w: Float, h: Float, scroll: Float) {
        val stepY = 30f
        val stepX = 34f

        var row = 0
        var y = -stepY + (scroll % stepY)
        while (y < h + stepY) {
            val offsetX = if (row % 2 == 0) 0f else stepX * 0.5f
            var x = offsetX - stepX
            while (x < w + stepX) {
                val cx = x + stepX * 0.5f
                val cy = y + stepY * 0.5f
                drawOneScale(canvas, cx, cy, h)
                x += stepX
            }
            y += stepY
            row++
        }
    }

    private fun drawOneScale(canvas: Canvas, cx: Float, cy: Float, h: Float) {
        canvas.save()
        canvas.translate(cx, cy)

        val baseColor = sampleGradientColor(cy, h)
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        shadowPaint.color = Color.argb(70, 0, 0, 0)
        canvas.save()
        canvas.translate(0f, 2.5f)
        canvas.drawPath(scalePath, shadowPaint)
        canvas.restore()

        scalePaint.color = Color.argb(180, r, g, b)
        canvas.drawPath(scalePath, scalePaint)

        canvas.drawPath(scalePath, scaleEdgePaint)

        val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        keelPaint.color = if (luminance > 0.4f)
            Color.argb(100, 0, 0, 0)
        else
            Color.argb(100, 255, 255, 255)
        canvas.drawLine(0f, -11f, 0f, 11f, keelPaint)

        canvas.restore()
    }

    private fun buildScalePath(): Path {
        val path = Path()
        path.moveTo(0f, -16f)
        path.cubicTo(10f, -15f, 12f, -3f, 7f, 0f)
        path.cubicTo(4f, 3f, 2f, 10f, 0f, 16f)
        path.cubicTo(-2f, 10f, -4f, 3f, -7f, 0f)
        path.cubicTo(-12f, -3f, -10f, -15f, 0f, -16f)
        path.close()
        return path
    }

    private fun sampleGradientColor(cy: Float, h: Float): Int {
        val norm = ((cy / h) + gradientOffset) % 1f
        val adj = if (norm < 0f) norm + 1f else norm
        val seg = adj * (colors.size - 1)
        val idx = seg.toInt().coerceIn(0, colors.size - 2)
        val t = (seg - idx).coerceIn(0f, 1f)
        return lerpColor(colors[idx], colors[idx + 1], t)
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
