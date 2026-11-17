package com.mediaforge.studio

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MediaForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required by PdfBox-Android: loads glyph lists / font metrics from the library's assets.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
