package ani.sanin.media.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

class SaninMpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMpvView(context, attrs) {

    private var initialized = false
    private var hasQueuedMedia = false
    private var pendingUrl: String? = null
    private var pendingStartOption: String? = null

    fun ensureInitialized() {
        if (initialized) return
        initialize()
        initialized = true
    }

    private fun initialize() {
        if (mpv == null) {
            mpv = MpvLib(context)
            if (holder.surface?.isValid == true) {
                mpv?.attachSurface(holder.surface)
                mpv?.setOptionString("force-window", "yes")
                mpv?.setPropertyString("vo", voInUse)
            }
        }
        mpv?.setOptionString("profile", "fast")
        setVo("gpu")
        mpv?.setOptionString("gpu-context", "android")
        mpv?.setOptionString("opengl-es", "yes")
        mpv?.setOptionString("sub-ass-override", "no")
        mpv?.setOptionString("sub-font", "Roboto")
        mpv?.setOptionString("sub-use-margins", "yes")
        mpv?.setOptionString("sub-ass-force-margins", "yes")
        mpv?.setOptionString("hwdec", "auto-safe")
        mpv?.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv?.setOptionString("ao", "audiotrack,opensles")
        mpv?.setOptionString("audio-set-media-role", "yes")
        mpv?.setOptionString("tls-verify", "yes")
        mpv?.setOptionString("input-default-bindings", "yes")
        mpv?.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        mpv?.setOptionString("demuxer-max-back-bytes", "${64 * 1024 * 1024}")
        mpv?.setOptionString("keep-open", "yes")
        mpv?.setOptionString("softvol", "yes")
        mpv?.setOptionString("volume-max", "400")
    }

    fun setMedia(url: String, headers: Map<String, String>, startPositionMs: Long = 0L) {
        ensureInitialized()
        val startOption = startPositionMs
            .takeIf { it > 0L }
            ?.let { String.format(Locale.US, "start=%.3f", it / 1000.0) }
        applyHeaders(headers)
        if (startOption != null && holder.surface?.isValid == true) {
            mpv?.command("loadfile", url, "replace", startOption)
            hasQueuedMedia = true
            pendingUrl = null
            pendingStartOption = null
        } else if (startOption != null) {
            pendingUrl = url
            pendingStartOption = startOption
            hasQueuedMedia = true
        } else {
            pendingUrl = null
            pendingStartOption = null
            mpv?.command("loadfile", url, "replace")
            hasQueuedMedia = true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        val url = pendingUrl ?: return
        val startOption = pendingStartOption
        pendingUrl = null
        pendingStartOption = null
        if (startOption != null) {
            mpv?.command("loadfile", url, "replace", startOption)
        } else {
            mpv?.command("loadfile", url, "replace")
        }
    }

    fun setPaused(paused: Boolean) {
        if (!initialized) return
        mpv?.setPropertyBoolean("pause", paused)
    }

    fun isPlayingNow(): Boolean {
        if (!initialized) return false
        return mpv?.getPropertyBoolean("pause") == false
    }

    fun seekToMs(positionMs: Long) {
        if (!initialized) return
        val seconds = (positionMs.coerceAtLeast(0L) / 1000.0)
        mpv?.setPropertyDouble("time-pos", seconds)
    }

    fun currentPositionMs(): Long {
        if (!initialized) return 0L
        val seconds = mpv?.getPropertyDouble("time-pos/full")
            ?: mpv?.getPropertyDouble("time-pos")
            ?: 0.0
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun durationMs(): Long {
        if (!initialized) return 0L
        val seconds = mpv?.getPropertyDouble("duration/full") ?: 0.0
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!initialized) return
        mpv?.setPropertyDouble("speed", speed.toDouble())
    }

    fun setVolume(vol: Float) {
        if (!initialized) return
        mpv?.setPropertyDouble("volume", (vol * 100.0).coerceIn(0.0, 400.0))
    }

    fun selectAudioTrackById(trackId: Int): Boolean {
        if (!initialized) return false
        return try {
            mpv?.setPropertyInt("aid", trackId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select audio track: ${e.message}")
            false
        }
    }

    fun selectSubtitleTrackById(trackId: Int): Boolean {
        if (!initialized) return false
        return try {
            mpv?.setPropertyBoolean("sub-visibility", true)
            mpv?.setPropertyInt("sid", trackId)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select subtitle track: ${e.message}")
            false
        }
    }

    fun disableSubtitles(): Boolean {
        if (!initialized) return false
        return try {
            mpv?.setPropertyString("sid", "no")
            mpv?.setPropertyBoolean("sub-visibility", false)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable subtitles: ${e.message}")
            false
        }
    }

    fun setSubtitleDelayMs(delayMs: Int) {
        if (!initialized) return
        try {
            mpv?.setPropertyDouble("sub-delay", delayMs / 1000.0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set subtitle delay: ${e.message}")
        }
    }

    fun releasePlayer() {
        if (!initialized) return
        try { mpv?.close() } catch (e: Exception) {
            Log.w(TAG, "Failed to destroy mpv: ${e.message}")
        }
        initialized = false
        hasQueuedMedia = false
        pendingUrl = null
        pendingStartOption = null
    }

    private fun applyHeaders(headers: Map<String, String>) {
        if (headers.isEmpty()) {
            mpv?.setPropertyString("http-header-fields", "")
            return
        }
        val raw = headers.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString(separator = ",") { (key, value) ->
                "$key: $value".replace("\\", "\\\\").replace(",", "\\,")
            }
        mpv?.setPropertyString("http-header-fields", raw)
    }

    companion object {
        private const val TAG = "SaninMpvView"
    }
}
