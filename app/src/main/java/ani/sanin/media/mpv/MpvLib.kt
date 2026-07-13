package ani.sanin.media.mpv

import android.content.Context
import android.view.Surface

class MpvLib(context: Context) {
    private var nativeHandle: Long = 0
    val isInitialized: Boolean get() = nativeHandle != 0L

    private external fun nativeCreate(appctx: Context)
    private external fun nativeInit()
    external fun nativeDestroy()

    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(vararg cmd: String)
    external fun commandNode(vararg cmd: String): MpvNode?

    external fun setOptionString(name: String, value: String): Int

    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int)
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double)
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean)
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String)

    fun getPropertyFloat(property: String) = getPropertyDouble(property)?.toFloat()
    fun setPropertyFloat(property: String, value: Float) =
        setPropertyDouble(property, value.toDouble())

    fun getPropertyLong(property: String) = getPropertyInt(property)?.toLong()
    fun setPropertyLong(property: String, value: Long) = setPropertyInt(property, value.toInt())

    fun close() {
        nativeDestroy()
    }

    companion object {
        var nativeLoaded = false
        private const val LIB_NAME = "mpv"

        fun loadNative(libDir: String): Boolean {
            if (nativeLoaded) return true
            return try {
                System.load("$libDir/lib$LIB_NAME.so")
                nativeLoaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
    }

    init {
        if (!nativeLoaded) {
            throw IllegalStateException("mpv native library not loaded")
        }
        nativeCreate(context)
        nativeInit()
        setOptionString("idle", "once")
        setPropertyBoolean("pause", true)
    }
}
