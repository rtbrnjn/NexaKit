package com.mediaforge.studio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mediaforge.studio.pdf.PdfPageMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mediaforge_settings")

const val DEFAULT_MODEL = "gemini-2.5-flash"
val DEFAULT_MODELS = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite", "gemini-2.0-flash")

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

/** Stores the Gemini API key in EncryptedSharedPreferences (AES-256, Keystore-backed master key). */
class SecureKeyStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy { open() }

    private fun open(): SharedPreferences {
        fun create(): SharedPreferences {
            val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context, FILE, master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            create()
        } catch (_: Exception) {
            context.deleteSharedPreferences(FILE)
            create()
        }
    }

    fun read(): String = prefs.getString(KEY, "").orEmpty()
    fun write(value: String) { prefs.edit().putString(KEY, value).apply() }

    private companion object {
        const val FILE = "mediaforge_secure_vault"
        const val KEY = "gemini_api_key"
    }
}

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private val secure = SecureKeyStore(context)
    private val ds get() = context.settingsDataStore

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    val themeMode: Flow<ThemeMode> = ds.data.map { prefs ->
        val raw = prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    val model: Flow<String> = ds.data.map { it[MODEL] ?: DEFAULT_MODEL }
    val autoShowResults: Flow<Boolean> = ds.data.map { it[AUTO_SHEET] ?: true }
    val permissionAsked: Flow<Boolean> = ds.data.map { it[PERM_ASKED] ?: false }
    val knownModels: Flow<List<String>> = ds.data.map {
        it[KNOWN_MODELS]?.split(',')?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty) ?: DEFAULT_MODELS
    }

    val defaultQuality: Flow<Int> = ds.data.map { (it[DEFAULT_QUALITY] ?: 80).coerceIn(10, 95) }
    val defaultPdfPageMode: Flow<PdfPageMode> = ds.data.map { prefs ->
        val raw = prefs[DEFAULT_PDF_PAGE_MODE] ?: PdfPageMode.A4.name
        runCatching { PdfPageMode.valueOf(raw) }.getOrDefault(PdfPageMode.A4)
    }
    val autoStripExif: Flow<Boolean> = ds.data.map { it[AUTO_STRIP_EXIF] ?: true }
    val totalBytesSaved: Flow<Long> = ds.data.map { (it[TOTAL_BYTES_SAVED] ?: 0L).coerceAtLeast(0L) }

    init {
        scope.launch(Dispatchers.IO) { _apiKey.value = runCatching { secure.read() }.getOrDefault("") }
    }

    suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) {
        val trimmed = key.trim()
        secure.write(trimmed)
        _apiKey.value = trimmed
    }

    suspend fun setThemeMode(mode: ThemeMode) { ds.edit { it[THEME_MODE] = mode.name } }
    suspend fun setModel(model: String) { ds.edit { it[MODEL] = model } }
    suspend fun setAutoShowResults(v: Boolean) { ds.edit { it[AUTO_SHEET] = v } }
    suspend fun setPermissionAsked(v: Boolean) { ds.edit { it[PERM_ASKED] = v } }
    suspend fun setKnownModels(models: List<String>) { ds.edit { it[KNOWN_MODELS] = models.joinToString(",") } }
    suspend fun setDefaultQuality(q: Int) { ds.edit { it[DEFAULT_QUALITY] = q.coerceIn(10, 95) } }
    suspend fun setDefaultPdfPageMode(m: PdfPageMode) { ds.edit { it[DEFAULT_PDF_PAGE_MODE] = m.name } }
    suspend fun setAutoStripExif(v: Boolean) { ds.edit { it[AUTO_STRIP_EXIF] = v } }
    suspend fun addBytesSaved(delta: Long) {
        if (delta <= 0L) return
        ds.edit { it[TOTAL_BYTES_SAVED] = (it[TOTAL_BYTES_SAVED] ?: 0L) + delta }
    }
    suspend fun resetBytesSaved() { ds.edit { it[TOTAL_BYTES_SAVED] = 0L } }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MODEL = stringPreferencesKey("gemini_model")
        val AUTO_SHEET = booleanPreferencesKey("auto_show_results")
        val PERM_ASKED = booleanPreferencesKey("permission_asked")
        val KNOWN_MODELS = stringPreferencesKey("known_models")
        val DEFAULT_QUALITY = intPreferencesKey("default_quality")
        val DEFAULT_PDF_PAGE_MODE = stringPreferencesKey("default_pdf_page_mode")
        val AUTO_STRIP_EXIF = booleanPreferencesKey("auto_strip_exif")
        val TOTAL_BYTES_SAVED = longPreferencesKey("total_bytes_saved")
    }
}
