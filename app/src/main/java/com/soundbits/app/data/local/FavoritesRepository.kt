package com.soundbits.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soundbits.app.data.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "soundbits_prefs")

/** Favoritos persistidos en DataStore (JSON). Suficiente para cientos de canciones. */
class FavoritesRepository(context: Context, private val json: Json) {
    private val store = context.applicationContext.dataStore
    private val key = stringPreferencesKey("favorites_json")
    private val serializer = ListSerializer(Track.serializer())

    val tracks: Flow<List<Track>> = store.data.map { prefs -> decode(prefs[key]) }

    suspend fun toggle(track: Track) {
        store.edit { prefs ->
            val current = decode(prefs[key])
            val next = if (current.any { it.id == track.id }) current.filterNot { it.id == track.id }
            else listOf(track) + current
            prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private fun decode(raw: String?): List<Track> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
}
