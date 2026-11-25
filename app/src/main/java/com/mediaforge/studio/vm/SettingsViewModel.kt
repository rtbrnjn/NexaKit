package com.mediaforge.studio.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.BuildConfig
import com.mediaforge.studio.ai.AiPreference
import com.mediaforge.studio.ai.GeminiClient
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.ai.GroqClient
import com.mediaforge.studio.core.Actions
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.data.AppUpdateInfo
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.DEFAULT_MODEL
import com.mediaforge.studio.data.DEFAULT_MODELS
import com.mediaforge.studio.data.HistoryStore
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.data.UpdateCheckResult
import com.mediaforge.studio.data.UpdateChecker
import com.mediaforge.studio.data.UpdateDownloadState
import com.mediaforge.studio.data.db.AiHistoryDao
import com.mediaforge.studio.data.db.AiHistoryEntity
import com.mediaforge.studio.pdf.PdfPageMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiVaultViewModel @Inject constructor(
    private val dao: AiHistoryDao
) : ViewModel() {
    val entries: StateFlow<List<AiHistoryEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteById(id) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearAll() }
    }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data class Connected(val modelCount: Int) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    app: Application,
    private val settings: SettingsStore,
    private val historyStore: HistoryStore,
    connectivity: Connectivity,
    private val gemini: GeminiClient,
    private val groq: GroqClient,
    private val updateChecker: UpdateChecker
) : AndroidViewModel(app) {

    val keyInput = MutableStateFlow("")
    private var editedGemini = false

    val groqKeyInput = MutableStateFlow("")
    private var editedGroq = false

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _groqConnection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val groqConnection: StateFlow<ConnectionState> = _groqConnection

    private val _groqStatus = MutableStateFlow<String?>(null)
    val groqStatus: StateFlow<String?> = _groqStatus

    val savedKey: StateFlow<String> = settings.apiKey
    val savedGroqKey: StateFlow<String> = settings.groqApiKey
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ThemeMode.SYSTEM)
    val aiPreference: StateFlow<AiPreference> = settings.aiPreference.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AiPreference.AUTO)
    val model: StateFlow<String> = settings.model.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DEFAULT_MODEL)
    val models: StateFlow<List<String>> = settings.knownModels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DEFAULT_MODELS)
    val autoShow: StateFlow<Boolean> = settings.autoShowResults.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)
    val defaultQuality: StateFlow<Int> = settings.defaultQuality.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 70)
    val defaultPdfPageMode: StateFlow<PdfPageMode> = settings.defaultPdfPageMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), PdfPageMode.A4_PORTRAIT)
    val autoStripExif: StateFlow<Boolean> = settings.autoStripExif.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)
    val totalBytesSaved: StateFlow<Long> = settings.totalBytesSaved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0L)
    val history: StateFlow<List<SavedFile>> = historyStore.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
    val online: StateFlow<Boolean> = connectivity.online

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog

    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus

    private val _updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val updateDownloadState: StateFlow<UpdateDownloadState> = _updateDownloadState

    private var updateDownloadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.apiKey.collect { if (!editedGemini) keyInput.value = it }
        }
        viewModelScope.launch {
            settings.groqApiKey.collect { if (!editedGroq) groqKeyInput.value = it }
        }
    }

    fun onKeyChanged(v: String) {
        editedGemini = true
        keyInput.value = v
        _connection.value = ConnectionState.Idle
        _status.value = null
    }

    fun saveKey() {
        viewModelScope.launch {
            settings.saveApiKey(keyInput.value)
            _status.value = if (keyInput.value.isBlank()) "Gemini API key removed." else "Gemini API key saved securely on this device."
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            settings.saveApiKey("")
            editedGemini = false
            keyInput.value = ""
            _connection.value = ConnectionState.Idle
            _status.value = "Gemini API key removed."
        }
    }

    fun testConnection() = runConnectionCheck(
        key = keyInput.value.trim(),
        stateFlow = _connection,
        save = { settings.saveApiKey(it) },
        ping = { k ->
            val available = gemini.testConnection(k)
            if (available.isNotEmpty()) {
                settings.setKnownModels((DEFAULT_MODELS + available).distinct())
            }
            available.size
        }
    )

    fun onGroqKeyChanged(v: String) {
        editedGroq = true
        groqKeyInput.value = v
        _groqConnection.value = ConnectionState.Idle
        _groqStatus.value = null
    }

    fun saveGroqKey() {
        viewModelScope.launch {
            settings.saveGroqApiKey(groqKeyInput.value)
            _groqStatus.value = if (groqKeyInput.value.isBlank()) "Groq API key removed." else "Groq API key saved securely on this device."
        }
    }

    fun clearGroqKey() {
        viewModelScope.launch {
            settings.saveGroqApiKey("")
            editedGroq = false
            groqKeyInput.value = ""
            _groqConnection.value = ConnectionState.Idle
            _groqStatus.value = "Groq API key removed."
        }
    }

    fun testGroqConnection() = runConnectionCheck(
        key = groqKeyInput.value.trim(),
        stateFlow = _groqConnection,
        save = { settings.saveGroqApiKey(it) },
        ping = { k -> groq.testConnection(k).size }
    )

    private fun runConnectionCheck(
        key: String,
        stateFlow: MutableStateFlow<ConnectionState>,
        save: suspend (String) -> Unit,
        ping: suspend (String) -> Int
    ) {
        if (key.isEmpty()) {
            stateFlow.value = ConnectionState.Failed("Enter an API key first.")
            return
        }
        viewModelScope.launch {
            stateFlow.value = ConnectionState.Testing
            try {
                val count = ping(key)
                save(key)
                stateFlow.value = ConnectionState.Connected(count)
            } catch (e: GeminiException) {
                stateFlow.value = ConnectionState.Failed(e.message ?: "Connection failed")
            } catch (e: Exception) {
                stateFlow.value = ConnectionState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setAiPreference(pref: AiPreference) { viewModelScope.launch { settings.setAiPreference(pref) } }
    fun setModel(m: String) { viewModelScope.launch { settings.setModel(m) } }
    fun setAutoShow(v: Boolean) { viewModelScope.launch { settings.setAutoShowResults(v) } }
    fun setDefaultQuality(q: Int) { viewModelScope.launch { settings.setDefaultQuality(q) } }
    fun setDefaultPdfPageMode(m: PdfPageMode) { viewModelScope.launch { settings.setDefaultPdfPageMode(m) } }
    fun setAutoStripExif(v: Boolean) { viewModelScope.launch { settings.setAutoStripExif(v) } }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheBytes.value = FileUtils.cacheSizeBytes(getApplication())
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = FileUtils.clearAllCaches(getApplication())
            _cacheBytes.value = FileUtils.cacheSizeBytes(getApplication())
            _status.value = "Cleared ${FileUtils.formatBytes(freed)} of temporary cache."
        }
    }

    fun forget(file: SavedFile, deleteFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFile) runCatching { getApplication<Application>().contentResolver.delete(file.uri, null, null) }
            historyStore.remove(file)
        }
    }

    fun clearHistory() { viewModelScope.launch { historyStore.clear() } }

    fun checkForUpdates(userInitiated: Boolean = false) {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            if (userInitiated) _updateStatus.value = "Memeriksa pembaruan…"
            when (val res = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.Available -> {
                    _availableUpdate.value = res.info
                    _showUpdateDialog.value = true
                    _updateStatus.value = "Tersedia pembaruan ${res.info.tagName}."
                }
                is UpdateCheckResult.UpToDate -> {
                    if (userInitiated) {
                        _updateStatus.value = "Sudah versi terbaru (v${res.currentVersion})."
                    }
                }
                is UpdateCheckResult.Error -> {
                    if (userInitiated) {
                        _updateStatus.value = "Gagal cek pembaruan: ${res.message}"
                    }
                }
            }
            _checkingUpdate.value = false
        }
    }

    fun openUpdateDialog() {
        if (_availableUpdate.value != null) _showUpdateDialog.value = true
    }

    fun startUpdateDownload() {
        val update = _availableUpdate.value ?: return
        val apkUrl = update.apkDownloadUrl?.trim().orEmpty()
        if (apkUrl.isEmpty()) {
            _updateDownloadState.value = UpdateDownloadState.Failed(
                "Tautan aset DocPix.apk belum tersedia pada rilis ${update.tagName}."
            )
            return
        }
        if (_updateDownloadState.value is UpdateDownloadState.Downloading) return

        val appCtx = getApplication<Application>()
        val targetFile = UpdateChecker.resolveApkDownloadFile(appCtx)
        updateDownloadJob?.cancel()
        _updateDownloadState.value = UpdateDownloadState.Downloading(0L, -1L, 0)
        _updateStatus.value = "Mengunduh ${update.tagName}…"

        updateDownloadJob = viewModelScope.launch {
            try {
                val downloadedApk = updateChecker.downloadApk(apkUrl, targetFile) { downloaded, total, pct ->
                    _updateDownloadState.value = UpdateDownloadState.Downloading(downloaded, total, pct)
                }
                if (Actions.canInstallPackages(appCtx)) {
                    _updateDownloadState.value = UpdateDownloadState.ReadyToInstall(downloadedApk)
                    _updateStatus.value = "Unduhan selesai (100%). Membuka pemasang paket…"
                } else {
                    _updateDownloadState.value = UpdateDownloadState.NeedsInstallPermission(downloadedApk)
                    _updateStatus.value = "Unduhan selesai. Izinkan pemasangan aplikasi dari sumber ini."
                }
            } catch (_: CancellationException) {
                _updateDownloadState.value = UpdateDownloadState.Idle
                _updateStatus.value = "Unduhan pembaruan dibatalkan."
            } catch (e: Exception) {
                val msg = e.message ?: "Gagal mengunduh pembaruan."
                _updateDownloadState.value = UpdateDownloadState.Failed(msg)
                _updateStatus.value = "Unduhan gagal: $msg"
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _updateDownloadState.value = UpdateDownloadState.Idle
    }

    fun onInstallPermissionReturned(granted: Boolean) {
        val current = _updateDownloadState.value
        val apkFile = when (current) {
            is UpdateDownloadState.NeedsInstallPermission -> current.apkFile
            is UpdateDownloadState.ReadyToInstall -> current.apkFile
            else -> return
        }
        if (granted) {
            _updateDownloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)
            _updateStatus.value = "Izin aktif. Membuka layar pemasangan DocPix…"
        } else {
            _updateDownloadState.value = UpdateDownloadState.NeedsInstallPermission(apkFile)
            _updateStatus.value = "Izin pemasangan belum diaktifkan."
        }
    }

    fun dismissUpdateDialog() {
        if (_updateDownloadState.value is UpdateDownloadState.Downloading) {
            cancelUpdateDownload()
        }
        _showUpdateDialog.value = false
    }
}
