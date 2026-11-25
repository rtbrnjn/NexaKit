package com.mediaforge.studio

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MediaForgeApp : Application() {
    private val bootScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize PdfBox-Android glyph/font tables off the main thread so frame 0 renders immediately.
        bootScope.launch {
            runCatching { PDFBoxResourceLoader.init(applicationContext) }
        }
    }
}
