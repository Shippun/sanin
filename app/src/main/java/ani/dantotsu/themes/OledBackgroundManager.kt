package ani.dantotsu.themes

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable

object OledBackgroundManager {

    fun apply(activity: Activity, oledMode: Int, primaryColor: Int, gradientDir: Int = 0) {
        activity.window.decorView.post {
            when (oledMode) {
                1 -> activity.window.setBackgroundDrawable(DarkBgDrawable())
                2 -> activity.window.setBackgroundDrawable(GlowSpotsDrawable(primaryColor))
                3 -> activity.window.setBackgroundDrawable(GradientBgDrawable(primaryColor, gradientDir))
                4 -> activity.window.setBackgroundDrawable(VignetteBgDrawable(primaryColor))
            }
        }
    }

    private class DarkBgDrawable : Drawable() {
        override fun draw(canvas: Canvas) { canvas.drawColor(Color.BLACK) }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        override fun getOpacity() = PixelFormat.OPAQUE
    }

    private class GlowSpotsDrawable(private val primaryColor: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val spots = listOf(
            floatArrayOf(0.50f, 0.10f, 0.45f),
            floatArrayOf(0.90f, 0.18f, 0.36f),
            floatArrayOf(0.10f, 0.82f, 0.38f),
        )

        override fun draw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val r = Color.red(primaryColor)
            val g = Color.green(primaryColor)
            val b = Color.blue(primaryColor)
            for (spot in spots) {
                val cx = spot[0] * w
                val cy = spot[1] * h
                val radius = spot[2] * minOf(w, h)
                paint.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(
                        Color.argb(90, r, g, b),
                        Color.argb(40, r, g, b),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.40f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private class GradientBgDrawable(
        private val primaryColor: Int,
        private val direction: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val r = Color.red(primaryColor)
            val g = Color.green(primaryColor)
            val b = Color.blue(primaryColor)

            val x0: Float; val y0: Float; val x1: Float; val y1: Float
            when (direction) {
                1 -> { x0 = w / 2f; y0 = h; x1 = w / 2f; y1 = h * 0.30f }
                2 -> { x0 = 0f; y0 = h / 2f; x1 = w * 0.70f; y1 = h / 2f }
                3 -> { x0 = w; y0 = h / 2f; x1 = w * 0.30f; y1 = h / 2f }
                else -> { x0 = w / 2f; y0 = 0f; x1 = w / 2f; y1 = h * 0.70f }
            }

            paint.shader = LinearGradient(
                x0, y0, x1, y1,
                intArrayOf(
                    Color.argb(80, r, g, b),
                    Color.argb(35, r, g, b),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.40f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private class VignetteBgDrawable(private val primaryColor: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val r = Color.red(primaryColor)
            val g = Color.green(primaryColor)
            val b = Color.blue(primaryColor)
            val cx = w / 2f
            val cy = h / 2f
            val radius = maxOf(w, h) * 0.80f
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(45, r, g, b),
                    Color.argb(110, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
