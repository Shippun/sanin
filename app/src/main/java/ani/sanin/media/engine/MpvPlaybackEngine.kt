package ani.sanin.media.engine

import android.view.View
import ani.sanin.media.mpv.MpvLib
import ani.sanin.media.mpv.SaninMpvView

class MpvPlaybackEngine(
    private val mpvView: SaninMpvView,
) : PlaybackEngine {

    override var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null
    override var onError: ((error: String) -> Unit)? = null

    override fun play() {
        mpvView.setPaused(false)
    }

    override fun pause() {
        mpvView.setPaused(true)
    }

    override fun seekTo(positionMs: Long) {
        mpvView.seekToMs(positionMs)
    }

    override fun currentPositionMs(): Long = mpvView.currentPositionMs()

    override fun durationMs(): Long = mpvView.durationMs()

    override fun isPlaying(): Boolean = mpvView.isPlayingNow()

    override fun setPlaybackSpeed(speed: Float) {
        mpvView.setPlaybackSpeed(speed)
    }

    override fun selectAudioTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        mpvView.selectAudioTrackById(id)
    }

    override fun selectSubtitleTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        mpvView.selectSubtitleTrackById(id)
    }

    override fun disableSubtitles() {
        mpvView.disableSubtitles()
    }

    override fun setSubtitlesEnabled(enabled: Boolean) {
        if (!enabled) mpvView.disableSubtitles()
    }

    override fun addExternalSubtitle(url: String, title: String?, language: String?): Boolean = false

    override fun setSubtitleDelayMs(delayMs: Int) {
        mpvView.setSubtitleDelayMs(delayMs)
    }

    override fun getTracks(): TrackSnapshot {
        return TrackSnapshot(emptyList(), emptyList())
    }

    override fun getSurfaceView(): View = mpvView

    override fun release() {
        mpvView.releasePlayer()
    }
}
