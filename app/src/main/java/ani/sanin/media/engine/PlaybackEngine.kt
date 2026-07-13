package ani.sanin.media.engine

import android.view.View

data class TrackInfo(
    val index: Int,
    val id: String,
    val name: String,
    val language: String?,
    val codec: String?,
    val isSelected: Boolean,
    val isForced: Boolean = false,
)

data class TrackSnapshot(
    val audioTracks: List<TrackInfo>,
    val subtitleTracks: List<TrackInfo>,
)

interface PlaybackEngine {
    var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)?
    var onError: ((error: String) -> Unit)?

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun currentPositionMs(): Long
    fun durationMs(): Long
    fun isPlaying(): Boolean
    fun setPlaybackSpeed(speed: Float)
    fun selectAudioTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String)
    fun disableSubtitles()
    fun setSubtitlesEnabled(enabled: Boolean)
    fun addExternalSubtitle(url: String, title: String?, language: String?): Boolean
    fun setSubtitleDelayMs(delayMs: Int)
    fun getTracks(): TrackSnapshot
    fun getSurfaceView(): View
    fun release()
}
