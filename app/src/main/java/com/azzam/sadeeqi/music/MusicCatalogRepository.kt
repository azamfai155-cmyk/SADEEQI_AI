package com.azzam.sadeeqi.music

/** Each provider is isolated: an outage in one catalogue won't hide another's results. */
class MusicCatalogRepository(
    private val audius: AudiusMusicRepository = AudiusMusicRepository(),
    private val openverse: OpenverseMusicRepository = OpenverseMusicRepository()
) {
    fun search(query: String, limit: Int = 20): List<RemoteMusicTrack> {
        require(query.isNotBlank()) { "اكتب اسم أغنية أو فنان للبحث." }
        val perProvider = limit.coerceIn(1, 30)
        val results = mutableListOf<RemoteMusicTrack>()
        runCatching { audius.search(query, perProvider) }.onSuccess { results.addAll(it) }
        runCatching { openverse.search(query, perProvider) }.onSuccess(results::addAll)
        return results.distinctBy {
            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}".replace(Regex("\\s+"), " ")
        }.take(limit.coerceIn(1, 60))
    }
}