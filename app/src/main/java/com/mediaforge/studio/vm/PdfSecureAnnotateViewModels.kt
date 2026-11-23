package com.mediaforge.studio.vm

import android.app.Application
import android.net.Uri
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.pdf.PageNumberFormat
import com.mediaforge.studio.pdf.PageNumberOptions
import com.mediaforge.studio.pdf.PageNumberPosition
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.WatermarkOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ============================================================ 9. Add Page Numbers

@HiltViewModel
class PageNumbersViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _options = MutableStateFlow(PageNumberOptions())
    val options: StateFlow<PageNumberOptions> = _options

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setFormat(f: PageNumberFormat) = _options.update { it.copy(format = f) }
    fun setPosition(p: PageNumberPosition) = _options.update { it.copy(position = p) }
    fun setFontSize(s: Float) = _options.update { it.copy(fontSize = s) }
    fun setStartNumber(n: Int) = _options.update { it.copy(startNumber = n.coerceAtLeast(1)) }

    fun export() {
        val src = _source.value ?: return
        val opts = _options.value
        runTask("Stamping page numbers…", write = true) {
            stagePdfOutput(
                defaultStem = FileUtils.defaultPdfStem("Numbered"),
                toolName = "Add Page Numbers",
                onSaved = { showInfo("Added page numbers to ${src.pageCount} page(s).") }
            ) { out ->
                PdfEngine.addPageNumbers(src.file, opts, out) { setProgress("Numbering pages…", it) }
            }
        }
    }
}

// ============================================================ 10. Add Text Watermark

@HiltViewModel
class WatermarkPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _text = MutableStateFlow("CONFIDENTIAL")
    val text: StateFlow<String> = _text
    private val _opacity = MutableStateFlow(0.22f)
    val opacity: StateFlow<Float> = _opacity
    private val _color = MutableStateFlow(0xFF64748B.toInt())
    val color: StateFlow<Int> = _color

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setText(v: String) { _text.value = v.take(40) }
    fun setOpacity(v: Float) { _opacity.value = v }
    fun setColor(c: Int) { _color.value = c }

    fun export() {
        val src = _source.value ?: return
        val txt = _text.value.trim()
        if (txt.isEmpty()) { showError("Enter watermark text first."); return }
        val wm = WatermarkOptions(txt, _opacity.value, _color.value)
        runTask("Applying watermark…", write = true) {
            stagePdfOutput(
                defaultStem = FileUtils.defaultPdfStem("Watermarked"),
                toolName = "Text Watermark",
                onSaved = { showInfo("Watermark stamped onto ${src.pageCount} page(s).") }
            ) { out ->
                PdfEngine.watermarkPdf(src.file, wm, out) { setProgress("Watermarking…", it) }
            }
        }
    }
}

// ============================================================ 11. Protect / Encrypt PDF

@HiltViewModel
class ProtectPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _userPassword = MutableStateFlow("")
    val userPassword: StateFlow<String> = _userPassword
    private val _ownerPassword = MutableStateFlow("")
    val ownerPassword: StateFlow<String> = _ownerPassword
    private val _allowPrint = MutableStateFlow(true)
    val allowPrint: StateFlow<Boolean> = _allowPrint
    private val _allowCopy = MutableStateFlow(false)
    val allowCopy: StateFlow<Boolean> = _allowCopy

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setUserPassword(v: String) { _userPassword.value = v }
    fun setOwnerPassword(v: String) { _ownerPassword.value = v }
    fun setAllowPrint(v: Boolean) { _allowPrint.value = v }
    fun setAllowCopy(v: Boolean) { _allowCopy.value = v }

    fun export() {
        val src = _source.value ?: return
        val pass = _userPassword.value
        if (pass.length < 4) { showError("Use at least 4 characters for the password."); return }
        runTask("Encrypting PDF…", write = true) {
            stagePdfOutput(
                defaultStem = FileUtils.defaultPdfStem("Protected"),
                toolName = "Protect PDF",
                onSaved = { showInfo("PDF encrypted with 128-bit password protection.") }
            ) { out ->
                PdfEngine.protect(src.file, pass, _ownerPassword.value, _allowPrint.value, _allowCopy.value, out)
            }
        }
    }
}

// ============================================================ 12. Unlock / Decrypt PDF

@HiltViewModel
class UnlockPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
    private val _source = MutableStateFlow<LockedPdfSource?>(null)
    val source: StateFlow<LockedPdfSource?> = _source
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    fun pick(uri: Uri) {
        runTask("Inspecting PDF…") {
            _password.value = ""
            _source.value = PdfInputs.loadForUnlock(getApplication(), uri)
        }
    }
    fun setPassword(v: String) { _password.value = v }

    fun export() {
        val src = _source.value ?: return
        if (!src.isEncrypted) {
            showInfo("This PDF is not password-encrypted.")
            return
        }
        val pass = _password.value
        runTask("Decrypting PDF…", write = true) {
            stagePdfOutput(
                defaultStem = FileUtils.defaultPdfStem("Unlocked"),
                toolName = "Unlock PDF",
                onSaved = { showInfo("Password restrictions removed.") }
            ) { out ->
                PdfEngine.unlock(src.file, pass, out)
            }
        }
    }
}
