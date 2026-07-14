package `is`.xyz.mpv

import android.content.Context
import android.view.Surface

/**
 * JNI bridge class matching the native symbols in libplayer.so.
 * The native library was built with class name is.xyz.mpv.MPV.
 */
class MPV {
    private external fun nativeCreate(appctx: Context)
    private external fun nativeInit()
    external fun nativeDestroy()

    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(vararg cmd: String)
    external fun commandNode(vararg cmd: String): Any?

    external fun setOptionString(name: String, value: String): Int

    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int)
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double)
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean)
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String)

    fun init(context: Context) {
        nativeCreate(context)
        nativeInit()
    }

    fun close() {
        nativeDestroy()
    }
}
