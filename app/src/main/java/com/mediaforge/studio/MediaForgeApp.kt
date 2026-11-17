package com.mediaforge.studio

import android.app.Application
import com.mediaforge.studio.ai.GeminiClient
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.HistoryStore
import com.mediaforge.studio.data.SettingsStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual service locator: one place that wires singletons shared by all ViewModels. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app, scope)
    val history = HistoryStore(app)
    val connectivity = Connectivity(app)
    val gemini = GeminiClient()

    private val _results = MutableStateFlow<List<SavedFile>?>(null)
    /** Non-null while the "Open / Share" action sheet should be showing. */
    val results: StateFlow<List<SavedFile>?> = _results

    fun publish(files: List<SavedFile>) {
        if (files.isEmpty()) return
        scope.launch {
            history.add(files)
            if (settings.autoShowResults.first()) _results.value = files
        }
    }

    fun dismissResults() { _results.value = null }
}

class MediaForgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Required by PdfBox-Android: loads glyph lists / font metrics from the library's assets.
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}

val Application.container: AppContainer get() = (this as MediaForgeApp).container
