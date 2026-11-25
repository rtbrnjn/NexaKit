package com.mediaforge.studio.vm

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiRouter
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.ai.GeneratedPromptResult
import com.mediaforge.studio.ai.LightingPreset
import com.mediaforge.studio.ai.PromptAspectRatio
import com.mediaforge.studio.ai.PromptTarget
import com.mediaforge.studio.ai.VisualStyle
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.data.db.AiHistoryDao
import com.mediaforge.studio.data.db.AiHistoryEntity
import com.mediaforge.studio.data.db.AiHistoryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Dual-engine AI Prompt Maker (Groq LPU / Google Gemini) that turns short concepts into
 * detailed English prompts + negative prompts for image/video generation models.
 */
@HiltViewModel
class PromptGeneratorViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val aiRouter: AiRouter,
    private val aiHistory: AiHistoryDao
) : ToolViewModel(app, coordinator) {

    private val _idea = MutableStateFlow("")
    val idea: StateFlow<String> = _idea

    private val _target = MutableStateFlow(PromptTarget.IMAGE)
    val target: StateFlow<PromptTarget> = _target

    private val _style = MutableStateFlow(VisualStyle.CINEMATIC_PHOTO)
    val style: StateFlow<VisualStyle> = _style

    private val _lighting = MutableStateFlow(LightingPreset.SOFT_AMBIENT)
    val lighting: StateFlow<LightingPreset> = _lighting

    private val _aspectRatio = MutableStateFlow(PromptAspectRatio.SQUARE)
    val aspectRatio: StateFlow<PromptAspectRatio> = _aspectRatio

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _liveStatus = MutableStateFlow<String?>(null)
    val liveStatus: StateFlow<String?> = _liveStatus

    private val _result = MutableStateFlow<GeneratedPromptResult?>(null)
    val result: StateFlow<GeneratedPromptResult?> = _result

    private val _activeEngine = MutableStateFlow<AiEngine?>(null)
    val activeEngine: StateFlow<AiEngine?> = _activeEngine

    private var streamJob: Job? = null

    fun setIdea(text: String) {
        _idea.value = text
    }

    fun setTarget(t: PromptTarget) {
        _target.value = t
    }

    fun setStyle(s: VisualStyle) {
        _style.value = s
    }

    fun setLighting(l: LightingPreset) {
        _lighting.value = l
    }

    fun setAspectRatio(r: PromptAspectRatio) {
        _aspectRatio.value = r
    }

    fun stop() {
        streamJob?.cancel()
        _isStreaming.value = false
        _liveStatus.value = null
        val partial = _streamingText.value
        if (partial.isNotBlank()) {
            _result.value = GeneratedPromptResult.parse(partial)
        }
    }

    fun generate() {
        val concept = _idea.value.trim()
        if (concept.isEmpty()) {
            showError("Tulis ide singkat terlebih dahulu (mis. \"kucing pakai helm astronot di bulan\").")
            return
        }
        if (!connectivity.online.value) {
            showError("Mode Offline aktif — sambungkan internet untuk membuat prompt AI.")
            return
        }
        dismissMessage()
        streamJob?.cancel()
        _result.value = null
        _streamingText.value = ""
        _isStreaming.value = true
        _liveStatus.value = "Menyiapkan mesin AI..."

        streamJob = viewModelScope.launch {
            val groqKey = settings.groqApiKey.value
            val geminiKey = settings.apiKey.value
            val geminiModel = settings.model.first()
            val pref = settings.aiPreference.first()
            val targetVal = _target.value
            val styleVal = _style.value
            val lightVal = _lighting.value
            val ratioVal = _aspectRatio.value
            try {
                val outcome = aiRouter.streamPromptGeneration(
                    preference = pref,
                    groqKey = groqKey,
                    geminiKey = geminiKey,
                    geminiModel = geminiModel,
                    idea = concept,
                    target = targetVal,
                    style = styleVal,
                    lighting = lightVal,
                    ratio = ratioVal,
                    onStatus = { engine, _, status ->
                        _activeEngine.value = engine
                        _liveStatus.value = status
                    },
                    onDelta = { acc ->
                        _streamingText.value = acc
                    }
                )
                val parsed = GeneratedPromptResult.parse(outcome.text)
                _result.value = parsed
                if (parsed.mainPrompt.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        aiHistory.insert(
                            AiHistoryEntity(
                                kind = AiHistoryKind.PROMPT,
                                title = concept.take(64),
                                subtitle = "${targetVal.label} • ${styleVal.label} • ${ratioVal.label} • ${outcome.modelId}",
                                content = parsed.asCopyableText(),
                                model = outcome.modelId
                            )
                        )
                    }
                }
            } catch (_: CancellationException) {
                // Handled by stop()
            } catch (e: GeminiException) {
                showError(e.message ?: "AI engine error")
            } catch (e: Exception) {
                showError(e.message ?: e.javaClass.simpleName)
            } finally {
                _isStreaming.value = false
                _liveStatus.value = null
            }
        }
    }
}
