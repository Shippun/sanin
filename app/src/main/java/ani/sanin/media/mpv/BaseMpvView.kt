package ani.sanin.media.mpv

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

open class BaseMpvView(
    context: Context, attrs: AttributeSet?
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    var mpv: MpvLib? = null
    protected var voInUse: String = "gpu"

    fun setVo(vo: String) {
        voInUse = vo
        mpv?.setOptionString("vo", vo)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mpv?.setPropertyString("android-surface-size", "${width}x${height}")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val mpv = mpv
        if (mpv?.isInitialized != true) return
        mpv.attachSurface(holder.surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setPropertyString("vo", voInUse)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val mpv = mpv
        if (mpv?.isInitialized != true) return
        mpv.setPropertyString("vo", "null")
        mpv.setPropertyString("force-window", "no")
        mpv.detachSurface()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        holder.addCallback(this)
    }

    override fun onDetachedFromWindow() {
        holder.removeCallback(this)
        super.onDetachedFromWindow()
    }
}
