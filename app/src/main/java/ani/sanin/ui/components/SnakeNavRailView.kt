package ani.sanin.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectDrawable
import ani.sanin.util.GlassEffectManager

class SnakeNavRailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedWidth = -1f
    private var cachedHeight = -1f

    private var glassBlur: Bitmap? = null
    private val clipPath = Path()
    private val clipRect = RectF()

    init {
        setWillNotDraw(false)
        tintPaint.color = GlassEffectManager.getTintColor()
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
        _glassEnabled = enabled && GlassEffectManager.isComponentEnabled(GlassComponent.SideRail)
        if (!_glassEnabled) {
            glassBlur?.recycle()
            glassBlur = null
        }
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
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        if (_glassEnabled) {
            drawGlass(canvas, w, h)
        } else {
            drawGradient(canvas, w, h)
        }
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

    private fun drawGlass(canvas: Canvas, w: Float, h: Float) {
        val iw = w.toInt()
        val ih = h.toInt()
        if (iw <= 0 || ih <= 0) return

        if (glassBlur == null || cachedWidth != w || cachedHeight != h) {
            cachedWidth = w
            cachedHeight = h

            val ds = 0.25f
            val sw = (iw * ds).coerceAtLeast(1)
            val sh = (ih * ds).coerceAtLeast(1)

            val root = rootView
            val bitmap = try {
                Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                return
            }
            val c = Canvas(bitmap)
            c.scale(ds, ds)
            val pos = IntArray(2)
            getLocationInWindow(pos)
            c.translate(-pos[0].toFloat(), -pos[1].toFloat())
            root.draw(c)

            glassBlur?.recycle()
            glassBlur = GlassEffectDrawable.fastBlur(bitmap, 25)
        }

        val blur = glassBlur ?: return

        val cornerPx = 16f * resources.displayMetrics.density
        clipPath.rewind()
        clipRect.set(0f, 0f, w, h)
        clipPath.addRoundRect(clipRect, cornerPx, cornerPx, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(blur, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRect(0f, 0f, w, h, tintPaint)
        canvas.restore()
    }

    fun invalidateGlass() {
        glassBlur?.recycle()
        glassBlur = null
        invalidate()
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t).toInt()
        return Color.rgb(r, g, b)
    }
}
