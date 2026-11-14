package com.azzam.sadeeqi.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

data class MusicPlaybackState(
    val title: String = "لم يتم اختيار مقطع",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val volume: Float = 1f
)

class MusicPlayerManager(
    context: Context,
    private val onState: (MusicPlaybackState) -> Unit
) {
    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingRemoteQueue: Pair<List<RemoteMusicTrack>, Int>? = null
    private var dataSaverEnabled: Boolean = true
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = publish()
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                controller = future.get().also { it.addListener(listener) }
                applyDataSaver()
                publish()
                pendingRemoteQueue?.let { (tracks, index) ->
                    pendingRemoteQueue = null
                    playRemoteQueue(tracks, index)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    fun playQueue(tracks: List<Pair<Uri, String>>, startIndex: Int = 0) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        val items = tracks.map { (uri, title) ->
            MediaItem.Builder().setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        }
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
        publish()
    }

    fun playRemoteQueue(tracks: List<RemoteMusicTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val player = controller
        if (player == null) {
            pendingRemoteQueue = tracks to startIndex
            return
        }
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.artworkUrl?.let(Uri::parse))
                        .build()
                )
                .build()
        }
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
        publish()
    }

    fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
        publish()
    }
    fun pause() { controller?.pause(); publish() }
    fun resume() { controller?.play(); publish() }
    fun refresh() = publish()

    fun next() { controller?.seekToNextMediaItem(); publish() }
    fun previous() { controller?.seekToPreviousMediaItem(); publish() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs); publish() }
    fun setDataSaver(enabled: Boolean) {
        dataSaverEnabled = enabled
        applyDataSaver()
    }

    private fun applyDataSaver() {
        val player = controller ?: return
        val maxBitrate = if (dataSaverEnabled) MusicPlaybackService.DATA_SAVER_MAX_AUDIO_BITRATE else Int.MAX_VALUE
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setMaxAudioBitrate(maxBitrate)
            .build()
    }

    fun setVolume(volume: Float) { controller?.volume = volume.coerceIn(0f, 1f); publish() }
    fun adjustVolume(delta: Float) {
        controller?.let { it.volume = (it.volume + delta).coerceIn(0f, 1f) }
        publish()
    }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; publish() }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        publish()
    }

    private fun publish() {
        val p = controller ?: return
        onState(
            MusicPlaybackState(
                title = p.currentMediaItem?.mediaMetadata?.title?.toString() ?: "ملف صوتي",
                isPlaying = p.isPlaying,
                durationMs = p.duration.coerceAtLeast(0L),
                positionMs = p.currentPosition.coerceAtLeast(0L),
                repeatMode = p.repeatMode,
                shuffleEnabled = p.shuffleModeEnabled,
                volume = p.volume
            )
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingRemoteQueue = null
    }
}
