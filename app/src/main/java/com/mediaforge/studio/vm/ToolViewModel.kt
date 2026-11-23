package com.mediaforge.studio.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.PendingPdfSave
import com.mediaforge.studio.core.Permissions
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.pdf.PdfException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

data class Progress(val label: String, val fraction: Float? = null)
data class UiMessage(val text: String, val isError: Boolean = false)

/** Shared plumbing: progress, one-shot messages, error handling and result publishing. */
abstract class ToolViewModel(
    app: Application,
    private val coordinator: AppCoordinator
) : AndroidViewModel(app) {
    protected val ctx: Context get() = getApplication()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message

    fun dismissMessage() { _message.value = null }
    protected fun showInfo(text: String) { _message.value = UiMessage(text, false) }
    protected fun showError(text: String) { _message.value = UiMessage(text, true) }

    protected fun setProgress(label: String, fraction: Float? = null) {
        _progress.value = Progress(label, fraction)
    }

    /** Runs `block` on Dispatchers.IO showing `label`; any failure becomes a user message. */
    protected fun runTask(label: String, write: Boolean = false, block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(Dispatchers.IO) {
            if (_progress.value != null) return@launch
            if (write && Permissions.writeBlocked(ctx)) {
                showError("Storage permission is required on this Android version. Grant it in Settings / Vault.")
                return@launch
            }
            _progress.value = Progress(label, null)
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfException) {
                _message.value = UiMessage(e.message ?: "PDF error", true)
            } catch (e: OutOfMemoryError) {
                _message.value = UiMessage("Not enough memory for this file. Try lower quality/DPI or fewer items.", true)
            } catch (e: SecurityException) {
                _message.value = UiMessage("Access denied – the file may be password protected.", true)
            } catch (e: Exception) {
                _message.value = UiMessage(e.message ?: e.javaClass.simpleName, true)
            } finally {
                _progress.value = null
            }
        }

    protected fun publish(files: List<SavedFile>) = coordinator.publish(files)

    protected fun stagePdfFile(
        file: File,
        defaultStem: String,
        toolName: String,
        onSaved: (suspend (SavedFile) -> Unit)? = null
    ) {
        coordinator.stagePdfSave(
            PendingPdfSave(
                tempFile = file,
                defaultStem = defaultStem,
                toolName = toolName,
                onSaved = onSaved,
                onFailed = { showError(it) }
            )
        )
    }

    /** Generates PDF content into a staged cache file, then prompts the Liquid Glass Rename Dialog before saving. */
    protected fun stagePdfOutput(
        defaultStem: String,
        toolName: String,
        onSaved: (suspend (SavedFile) -> Unit)? = null,
        writePdf: (OutputStream) -> Unit
    ) {
        val dir = File(ctx.cacheDir, "pdf_staged").apply { mkdirs() }
        val tmp = File(dir, "staged_${System.nanoTime()}.pdf")
        try {
            tmp.outputStream().use { out ->
                writePdf(out)
                out.flush()
            }
            stagePdfFile(tmp, defaultStem, toolName, onSaved)
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }
}
