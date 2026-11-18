package com.azzam.sadeeqi.music

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Queries providers concurrently and returns available results without waiting indefinitely for a slow source. */
class MusicCatalogRepository(
    private val audius: AudiusMusicRepository = AudiusMusicRepository(),
    private val openverse: OpenverseMusicRepository = OpenverseMusicRepository(),
    private val commons: WikimediaCommonsAudioRepository = WikimediaCommonsAudioRepository(),
    private val archive: InternetArchiveAudioRepository = InternetArchiveAudioRepository()
) {
    fun search(query: String, limit: Int = 20): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب اسم أغنية أو فنان للبحث." }
        val perProvider = limit.coerceIn(1, 30)
        val pool = Executors.newFixedThreadPool(4)
        val completion = ExecutorCompletionService<List<RemoteMusicTrack>>(pool)
        return try {
            val jobs = listOf(
                Callable { runCatching { audius.search(query, perProvider) }.getOrDefault(emptyList()) },
                Callable { runCatching { openverse.search(query, perProvider) }.getOrDefault(emptyList()) },
                Callable { runCatching { commons.search(query, perProvider) }.getOrDefault(emptyList()) },
                Callable { runCatching { archive.search(query, minOf(perProvider, 8)) }.getOrDefault(emptyList()) }
            )
            jobs.forEach(completion::submit)

            val collected = ArrayList<RemoteMusicTrack>()
            var completed = 0
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(24)
            while (completed < jobs.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) break
                val finished = completion.poll(remaining, TimeUnit.NANOSECONDS) ?: break
                completed++
                runCatching { finished.get() }.getOrDefault(emptyList()).let(collected::addAll)
            }
            collected.distinctBy {
                "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}".replace(Regex("\\s+"), " ")
            }.take(limit.coerceIn(1, 60))
        } finally {
            pool.shutdownNow()
        }
    }
}
