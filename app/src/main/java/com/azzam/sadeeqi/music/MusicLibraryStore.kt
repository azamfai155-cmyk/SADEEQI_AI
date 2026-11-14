package com.azzam.sadeeqi.music

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.musicDataStore by preferencesDataStore(name = "sadeeqi_music_library")

class MusicLibraryStore(context: Context) {
    private val store = context.applicationContext.musicDataStore
    private val queueKey = stringPreferencesKey("local_queue_v1")
    private val historyKey = stringPreferencesKey("listening_history_v1")
    private val favoritesKey = stringSetPreferencesKey("favorites_v1")
    private val dataSaverKey = booleanPreferencesKey("data_saver_enabled")

    suspend fun isDataSaverEnabled(): Boolean = store.data.first()[dataSaverKey] ?: true

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        store.edit { it[dataSaverKey] = enabled }
    }

    suspend fun saveQueue(tracks: List<Pair<Uri, String>>) {
        store.edit { prefs ->
            prefs[queueKey] = tracks.joinToString("\n") { encode(it.first, it.second) }
        }
    }

    suspend fun loadQueue(): List<Pair<Uri, String>> =
        decodeList(store.data.first()[queueKey].orEmpty())

    suspend fun recordPlayed(uri: Uri, title: String) {
        store.edit { prefs ->
            val current = decodeList(prefs[historyKey].orEmpty())
            val updated = (listOf(uri to title) + current.filterNot { it.first == uri }).take(50)
            prefs[historyKey] = updated.joinToString("\n") { encode(it.first, it.second) }
        }
    }

    suspend fun loadHistory(): List<Pair<Uri, String>> =
        decodeList(store.data.first()[historyKey].orEmpty())

    suspend fun toggleFavorite(uri: Uri, title: String): Boolean {
        var added = false
        store.edit { prefs ->
            val current = prefs[favoritesKey].orEmpty().toMutableSet()
            val encoded = encode(uri, title)
            if (current.remove(encoded)) {
                added = false
            } else {
                current.add(encoded)
                added = true
            }
            prefs[favoritesKey] = current
        }
        return added
    }

    suspend fun loadFavorites(): List<Pair<Uri, String>> =
        store.data.first()[favoritesKey].orEmpty().mapNotNull { decode(it) }

    private fun encode(uri: Uri, title: String): String =
        Uri.encode(uri.toString()) + "|" + Uri.encode(title)

    private fun decode(value: String): Pair<Uri, String>? = runCatching {
        val split = value.split("|", limit = 2)
        require(split.size == 2)
        Uri.parse(Uri.decode(split[0])) to Uri.decode(split[1])
    }.getOrNull()

    private fun decodeList(value: String): List<Pair<Uri, String>> =
        value.lineSequence().filter { it.isNotBlank() }.mapNotNull(::decode).toList()
}
