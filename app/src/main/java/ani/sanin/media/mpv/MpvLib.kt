package ani.sanin.media.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import `is`.xyz.mpv.MPV

class MpvLib(context: Context) {
    val isInitialized: Boolean get() = true
    private val mpv: MPV = MPV()

    fun attachSurface(surface: Surface) = mpv.attachSurface(surface)
    fun detachSurface() = mpv.detachSurface()

    fun command(vararg cmd: String) = mpv.command(*cmd)
    fun commandNode(vararg cmd: String): MpvNode? = mpv.commandNode(*cmd) as? MpvNode

    fun setOptionString(name: String, value: String) = mpv.setOptionString(name, value)

    fun getPropertyInt(property: String) = mpv.getPropertyInt(property)
    fun setPropertyInt(property: String, value: Int) = mpv.setPropertyInt(property, value)
    fun getPropertyDouble(property: String) = mpv.getPropertyDouble(property)
    fun setPropertyDouble(property: String, value: Double) = mpv.setPropertyDouble(property, value)
    fun getPropertyBoolean(property: String) = mpv.getPropertyBoolean(property)
    fun setPropertyBoolean(property: String, value: Boolean) = mpv.setPropertyBoolean(property, value)
    fun getPropertyString(property: String) = mpv.getPropertyString(property)
    fun setPropertyString(property: String, value: String) = mpv.setPropertyString(property, value)

    fun getPropertyFloat(property: String) = getPropertyDouble(property)?.toFloat()
    fun setPropertyFloat(property: String, value: Float) =
        setPropertyDouble(property, value.toDouble())

    fun getPropertyLong(property: String) = getPropertyInt(property)?.toLong()
    fun setPropertyLong(property: String, value: Long) = setPropertyInt(property, value.toInt())

    fun close() {
        mpv.close()
    }

    companion object {
        var nativeLoaded = false
    }

    init {
        if (!nativeLoaded) {
            throw IllegalStateException("mpv native library not loaded")
        }
        mpv.init(context)
        mpv.setOptionString("idle", "once")
        mpv.setPropertyBoolean("pause", true)
    }
}
