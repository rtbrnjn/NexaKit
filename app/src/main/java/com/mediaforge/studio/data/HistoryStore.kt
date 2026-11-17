package com.mediaforge.studio.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mediaforge.studio.core.SavedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "mediaforge_history")

/** Persists the export history as a JSON array inside Preferences DataStore. */
class HistoryStore(private val context: Context) {
    private val ds get() = context.historyDataStore

    val entries: Flow<List<SavedFile>> = ds.data.map { parse(it[KEY]) }

    suspend fun add(files: List<SavedFile>) {
        if (files.isEmpty()) return
        ds.edit { prefs ->
            val merged = (files.reversed() + parse(prefs[KEY])).distinctBy { it.uri.toString() }.take(MAX)
            prefs[KEY] = serialize(merged)
        }
    }

    suspend fun remove(file: SavedFile) {
        ds.edit { prefs ->
            prefs[KEY] = serialize(parse(prefs[KEY]).filterNot { it.uri == file.uri })
        }
    }

    suspend fun clear() { ds.edit { it.remove(KEY) } }

    private fun serialize(list: List<SavedFile>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("uri", it.uri.toString())
                    .put("name", it.name)
                    .put("mime", it.mime)
                    .put("size", it.sizeBytes)
                    .put("folder", it.folder)
                    .put("tool", it.tool)
                    .put("ts", it.timestamp)
            )
        }
        return arr.toString()
    }

    private fun parse(json: String?): List<SavedFile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedFile(
                    uri = Uri.parse(o.getString("uri")),
                    name = o.getString("name"),
                    mime = o.getString("mime"),
                    sizeBytes = o.optLong("size"),
                    folder = o.optString("folder"),
                    tool = o.optString("tool"),
                    timestamp = o.optLong("ts")
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY = stringPreferencesKey("entries_json")
        const val MAX = 200
    }
}
