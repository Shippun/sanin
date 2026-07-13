package ani.sanin.media.engine

import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

class ExoPlaybackEngine(
    private val playerView: PlayerView,
    private val cacheDataSourceFactory: CacheDataSource.Factory,
) : PlaybackEngine, Player.Listener {

    private var _exoPlayer: ExoPlayer? = null
    private val exoPlayer: ExoPlayer get() = _exoPlayer!!

    private var currentMediaItem: MediaItem? = null

    override var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null
    override var onError: ((error: String) -> Unit)? = null

    private val trackSelector = DefaultTrackSelector(playerView.context)

    fun initialize() {
        val ctx = playerView.context
        _exoPlayer = ExoPlayer.Builder(ctx)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(
                DefaultRenderersFactory(ctx)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(ctx)
                    .setLocalAdInsertionAllowed(true)
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBackBuffer(DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS, false)
                    .build()
            )
            .build()
            .also { player ->
                player.addListener(this)
                playerView.player = player
                player.playWhenReady = true
                player.repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    fun setMediaItem(mediaItem: MediaItem) {
        currentMediaItem = mediaItem
    }

    fun loadMedia(
        videoUrl: String,
        headers: Map<String, String>,
        subtitleMediaItem: MediaItem?,
    ) {
        val ctx = playerView.context
        val dataSourceFactory = if (currentMediaItem != null) {
            cacheDataSourceFactory
        } else {
            DefaultDataSource.Factory(ctx)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSourceFactory)

        val videoMediaSource = if (videoUrl.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(videoUrl))
        } else {
            mediaSourceFactory.createMediaSource(MediaItem.fromUri(videoUrl))
        }

        val merged = if (subtitleMediaItem != null) {
            val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                .createMediaSource(subtitleMediaItem, C.TIME_END_OF_SOURCE)
            MergingMediaSource(videoMediaSource, subtitleSource)
        } else {
            MergingMediaSource(videoMediaSource)
        }

        exoPlayer.setMediaSource(merged)
        exoPlayer.prepare()
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun currentPositionMs(): Long = exoPlayer.currentPosition

    override fun durationMs(): Long = exoPlayer.duration.coerceAtLeast(0L)

    override fun isPlaying(): Boolean = exoPlayer.isPlaying

    override fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    override fun selectAudioTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        for (i in 0 until exoPlayer.currentTracks.groups.size) {
            val group = exoPlayer.currentTracks.groups[i]
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (j in 0 until group.length) {
                val format = group.getTrackFormat(j)
                if (format.id == trackId || format.id?.toIntOrNull() == id) {
                    val params = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup, listOf(format)
                            )
                        )
                        .build()
                    exoPlayer.trackSelectionParameters = params
                    return
                }
            }
        }
    }

    override fun selectSubtitleTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        for (i in 0 until exoPlayer.currentTracks.groups.size) {
            val group = exoPlayer.currentTracks.groups[i]
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (j in 0 until group.length) {
                val format = group.getTrackFormat(j)
                if (format.id == trackId || format.id?.toIntOrNull() == id) {
                    val params = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup, listOf(format)
                            )
                        )
                        .build()
                    exoPlayer.trackSelectionParameters = params
                    return
                }
            }
        }
    }

    override fun disableSubtitles() {
        val params = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
            .build()
        exoPlayer.trackSelectionParameters = params
    }

    override fun setSubtitlesEnabled(enabled: Boolean) {
        // No-op for ExoPlayer – handled by track selection
    }

    override fun addExternalSubtitle(url: String, title: String?, language: String?): Boolean = false

    override fun setSubtitleDelayMs(delayMs: Int) {
        // Not directly supported in ExoPlayer's public API
    }

    override fun getTracks(): TrackSnapshot {
        val audioTracks = mutableListOf<TrackInfo>()
        val subTracks = mutableListOf<TrackInfo>()
        var audioIdx = 0
        var subIdx = 0

        for (group in exoPlayer.currentTracks.groups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val track = TrackInfo(
                    index = if (group.type == C.TRACK_TYPE_AUDIO) audioIdx++ else subIdx++,
                    id = format.id ?: "",
                    name = format.label ?: format.id ?: "Unknown",
                    language = format.language,
                    codec = format.codecs,
                    isSelected = group.isTrackSelected(i),
                )
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audioTracks.add(track)
                    C.TRACK_TYPE_TEXT -> subTracks.add(track)
                }
            }
        }
        return TrackSnapshot(audioTracks, subTracks)
    }

    override fun getSurfaceView(): View = playerView

    override fun release() {
        _exoPlayer?.let {
            it.removeListener(this)
            it.release()
        }
        _exoPlayer = null
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> onPlaybackStateChanged?.invoke(exoPlayer.isPlaying)
            Player.STATE_ENDED -> onPlaybackStateChanged?.invoke(false)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onPlaybackStateChanged?.invoke(isPlaying)
    }

    override fun onPlayerError(error: PlaybackException) {
        onError?.invoke(error.localizedMessage ?: "Playback error")
    }
}
