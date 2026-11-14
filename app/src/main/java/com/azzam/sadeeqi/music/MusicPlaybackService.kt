package com.azzam.sadeeqi.music

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.azzam.sadeeqi.MainActivity
import java.io.File

@UnstableApi
class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var musicCache: SimpleCache? = null

    override fun onCreate() {
        super.onCreate()

        val cache = SimpleCache(
            File(cacheDir, "sadeeqi_music_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(this)
        )
        musicCache = cache

        val upstream = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)
        )
        val cachedDataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Adaptive streams may honor this ceiling; a single-bitrate progressive
        // Audius stream cannot be transcoded by the client.
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxAudioBitrate(DATA_SAVER_MAX_AUDIO_BITRATE).build())
        }
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedDataSource))
            .build()

        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )

        val openApp = PendingIntent.getActivity(
            this,
            401,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MUSIC_CHANNEL_ID)
                .setChannelName("SADEEQI MUSIC")
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        musicCache?.release()
        musicCache = null
        super.onDestroy()
    }

    companion object {
        private const val MUSIC_CHANNEL_ID = "sadeeqi_music_playback"
        private const val MAX_CACHE_BYTES = 96L * 1024L * 1024L
        const val DATA_SAVER_MAX_AUDIO_BITRATE = 96_000
    }
}
