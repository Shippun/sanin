package ani.sanin.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

class GlassEffectDrawable(
    targetView: View,
    cornerRadiusPx: Float = 0f,
    blurRadius: Float = 25f,
    @ColorInt tintColor: Int = 0x66000000,
    refreshOnLayout: Boolean = true
) : Drawable() {

    private val targetRef = WeakReference(targetView)
    private var backdropCache: Bitmap? = null
    private var blurCache: Bitmap? = null

    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = tintColor }
    private val clipPath = Path()
    private val clipRect = RectF()
    private var cornerRad = cornerRadiusPx
    private var downscaleFactor = 0.25f

    private var lastWidth = -1
    private var lastHeight = -1
    private var lastBlurRadius = blurRadius.roundToInt().coerceIn(1, 100)

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        invalidateCache()
        targetRef.get()?.invalidate()
    }

    init {
        if (cornerRad > 0f && refreshOnLayout) {
            targetView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
    }

    fun setTintColor(@ColorInt color: Int) {
        tintPaint.color = color
        invalidateSelf()
    }

    fun setCornerRadius(radiusPx: Float) {
        cornerRad = radiusPx
        invalidateSelf()
    }

    fun invalidateCache() {
        backdropCache?.recycle()
        backdropCache = null
        blurCache?.recycle()
        blurCache = null
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0) return

        val target = targetRef.get() ?: return

        if (blurCache == null || lastWidth != w || lastHeight != h) {
            lastWidth = w
            lastHeight = h
            captureAndBlur(target, w, h)
        }

        val blur = blurCache ?: return
        canvas.save()
        if (cornerRad > 0f) {
            clipPath.rewind()
            clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
            clipPath.addRoundRect(clipRect, cornerRad, cornerRad, Path.Direction.CW)
            canvas.clipPath(clipPath)
        }
        canvas.drawBitmap(blur, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), blurPaint)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), tintPaint)
        canvas.restore()
    }

    private fun captureAndBlur(target: View, w: Int, h: Int) {
        val ds = downscaleFactor
        val sw = (w * ds).roundToInt().coerceAtLeast(1)
        val sh = (h * ds).roundToInt().coerceAtLeast(1)

        val root = target.rootView
        val bitmap = try {
            Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return
        }
        val c = Canvas(bitmap)
        c.scale(ds, ds)
        val pos = IntArray(2)
        target.getLocationInWindow(pos)
        c.translate(-pos[0].toFloat(), -pos[1].toFloat())
        root.draw(c)

        backdropCache = bitmap
        blurCache = fastBlur(bitmap, lastBlurRadius)
    }

    override fun setAlpha(alpha: Int) {
        blurPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        blurPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = lastWidth.coerceAtLeast(0)
    override fun getIntrinsicHeight(): Int = lastHeight.coerceAtLeast(0)

    fun destroy() {
        val target = targetRef.get()
        if (target != null) {
            try {
                target.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            } catch (_: Exception) {}
        }
        invalidateCache()
    }

    companion object {
        fun fastBlur(bitmap: Bitmap, radius: Int): Bitmap {
            val r = radius.coerceIn(1, 100)
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return bitmap

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val result = stackBlur(pixels, w, h, r)
            val output = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                return bitmap
            }
            output.setPixels(result, 0, w, 0, 0, w, h)
            return output
        }

        private fun stackBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
            val r = radius.coerceIn(1, 100)
            val div = r + r + 1
            val wm = w - 1
            val hm = h - 1
            val ds = div.coerceAtMost(w * h)
            val pix = pixels.copyOf()

            val vmin = IntArray(w.coerceAtLeast(h))
            val divsum = div
            val dv = IntArray(256 * divsum)
            for (i in dv.indices) dv[i] = i / divsum

            val s = IntArray(ds)
            var yi = 0

            for (y in 0 until h) {
                var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
                for (i in -r..r) {
                    val col = pix[yi + (wm.coerceAtMost(i.coerceAtLeast(0)))]
                    val si = i + r
                    s[si] = col
                    sumR += (col shr 16) and 0xFF
                    sumG += (col shr 8) and 0xFF
                    sumB += col and 0xFF
                    sumA += (col shr 24) and 0xFF
                }
                for (x in 0 until w) {
                    pix[yi] = (dv[sumA.coerceIn(0, dv.size - 1)] shl 24) or
                            (dv[sumR.coerceIn(0, dv.size - 1)] shl 16) or
                            (dv[sumG.coerceIn(0, dv.size - 1)] shl 8) or
                            dv[sumB.coerceIn(0, dv.size - 1)]
                    val col1 = s[0]
                    val col2 = if (x + r < wm) s[r + r] else s[ds - 1]
                    sumR += ((col2 shr 16) and 0xFF) - ((col1 shr 16) and 0xFF)
                    sumG += ((col2 shr 8) and 0xFF) - ((col1 shr 8) and 0xFF)
                    sumB += (col2 and 0xFF) - (col1 and 0xFF)
                    sumA += ((col2 shr 24) and 0xFF) - ((col1 shr 24) and 0xFF)
                    s.copyInto(s, 0, 1, ds)
                    val nextCol = if (x + r + 1 < w) pix[yi + r + 1] else pix[yi + wm]
                    s[ds - 1] = nextCol
                    yi++
                }
            }

            yi = 0
            for (x in 0 until w) {
                var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
                for (i in -r..r) {
                    val col = pix[yi + (hm.coerceAtMost(i.coerceAtLeast(0))) * w]
                    val si = i + r
                    s[si] = col
                    sumR += (col shr 16) and 0xFF
                    sumG += (col shr 8) and 0xFF
                    sumB += col and 0xFF
                    sumA += (col shr 24) and 0xFF
                }
                for (y in 0 until h) {
                    pix[yi] = (dv[sumA.coerceIn(0, dv.size - 1)] shl 24) or
                            (dv[sumR.coerceIn(0, dv.size - 1)] shl 16) or
                            (dv[sumG.coerceIn(0, dv.size - 1)] shl 8) or
                            dv[sumB.coerceIn(0, dv.size - 1)]
                    val col1 = s[0]
                    val col2 = if (y + r < hm) s[r + r] else s[ds - 1]
                    sumR += ((col2 shr 16) and 0xFF) - ((col1 shr 16) and 0xFF)
                    sumG += ((col2 shr 8) and 0xFF) - ((col1 shr 8) and 0xFF)
                    sumB += (col2 and 0xFF) - (col1 and 0xFF)
                    sumA += ((col2 shr 24) and 0xFF) - ((col1 shr 24) and 0xFF)
                    s.copyInto(s, 0, 1, ds)
                    val nextCol = if (y + r + 1 < h) pix[yi + (r + 1) * w] else pix[yi + hm * w]
                    s[ds - 1] = nextCol
                    yi += w
                }
            }

            return pix
        }

        fun applyToView(
            view: View,
            cornerRadiusDp: Float = 16f,
            blurRadius: Float = 25f,
            tintColor: Int = 0x66000000
        ): GlassEffectDrawable {
            val density = view.resources.displayMetrics.density
            val radiusPx = cornerRadiusDp * density
            val drawable = GlassEffectDrawable(
                targetView = view,
                cornerRadiusPx = radiusPx,
                blurRadius = blurRadius,
                tintColor = tintColor,
                refreshOnLayout = true
            )
            view.background = drawable
            return drawable
        }
    }
}
