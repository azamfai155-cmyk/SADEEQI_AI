package com.azzam.sadeeqi.music

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Each provider is isolated and queried concurrently; a provider outage won't hide other results. */
class MusicCatalogRepository(
    private val audius: AudiusMusicRepository = AudiusMusicRepository(),
    private val openverse: OpenverseMusicRepository = OpenverseMusicRepository(),
    private val commons: WikimediaCommonsAudioRepository = WikimediaCommonsAudioRepository()
) {
    fun search(query: String, limit: Int = 20): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب اسم أغنية أو فنان للبحث." }
        val perProvider = limit.coerceIn(1, 30)
        val pool = Executors.newFixedThreadPool(3)
        return try {
            val jobs = listOf(
                Callable { runCatching { audius.search(query, perProvider) }.getOrDefault(emptyList()) },
                Callable { runCatching { openverse.search(query, perProvider) }.getOrDefault(emptyList()) },
                Callable { runCatching { commons.search(query, perProvider) }.getOrDefault(emptyList()) }
            )
            pool.invokeAll(jobs).flatMap { future -> runCatching { future.get() }.getOrDefault(emptyList()) }
                .distinctBy {
                    "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}".replace(Regex("\\s+"), " ")
                }
                .take(limit.coerceIn(1, 60))
        } finally {
            pool.shutdownNow()
        }
    }
}