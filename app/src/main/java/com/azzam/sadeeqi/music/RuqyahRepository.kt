package com.azzam.sadeeqi.music

/** Quran-only listening playlist; not a diagnosis or substitute for qualified care. */
class RuqyahRepository(private val quran: QuranAudioRepository = QuranAudioRepository()) {
    fun loadPlaylist(reciterEdition: String = QuranAudioRepository.DEFAULT_RECITER): List<RemoteMusicTrack> {
        val passages = listOf(
            1 to (1..7).toSet(),
            2 to ((1..5).toSet() + setOf(102, 163, 164, 255, 285, 286)),
            7 to (117..122).toSet(),
            10 to setOf(81, 82),
            20 to setOf(69),
            112 to (1..4).toSet(),
            113 to (1..5).toSet(),
            114 to (1..6).toSet()
        )
        return passages.flatMap { (surah, selectedAyahs) ->
            quran.loadSurah(surah, reciterEdition).filter { track ->
                track.id.substringAfterLast('-').toIntOrNull() in selectedAyahs
            }
        }
    }
}