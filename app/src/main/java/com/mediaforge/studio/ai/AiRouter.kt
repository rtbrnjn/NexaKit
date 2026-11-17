package com.mediaforge.studio.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

enum class AiEngine(val id: String, val label: String) {
    GROQ("groq", "Groq (Ultra-Fast)"),
    GEMINI("gemini", "Gemini")
}

enum class AiPreference(val label: String) {
    AUTO("Auto"),
    GROQ("Groq (Ultra-Fast)"),
    GEMINI("Gemini")
}

data class AiStreamOutcome(
    val engine: AiEngine,
    val modelId: String,
    val text: String
)

/** Pure engine selection & fallback rules shared cleanly across UI, ViewModels, and unit tests. */
object AiRouting {

    fun resolvePrimaryEngine(
        preference: AiPreference,
        hasGroqKey: Boolean,
        hasGeminiKey: Boolean
    ): AiEngine? = when (preference) {
        AiPreference.AUTO -> when {
            hasGroqKey -> AiEngine.GROQ
            hasGeminiKey -> AiEngine.GEMINI
            else -> null
        }
        AiPreference.GROQ -> when {
            hasGroqKey -> AiEngine.GROQ
            hasGeminiKey -> AiEngine.GEMINI
            else -> null
        }
        AiPreference.GEMINI -> when {
            hasGeminiKey -> AiEngine.GEMINI
            hasGroqKey -> AiEngine.GROQ
            else -> null
        }
    }

    fun resolveEngineSequence(
        preference: AiPreference,
        hasGroqKey: Boolean,
        hasGeminiKey: Boolean
    ): List<AiEngine> {
        val first = resolvePrimaryEngine(preference, hasGroqKey, hasGeminiKey) ?: return emptyList()
        val list = ArrayList<AiEngine>(2)
        list += first
        if (first == AiEngine.GROQ && hasGeminiKey) list += AiEngine.GEMINI
        if (first == AiEngine.GEMINI && hasGroqKey) list += AiEngine.GROQ
        return list
    }

    fun shouldAttemptFallback(error: Throwable, emittedAny: Boolean): Boolean {
        if (error is CancellationException || emittedAny) return false
        if (error is GeminiException && error.kind == GeminiException.Kind.NO_KEY) return false
        return true
    }

    fun badgeLabel(engine: AiEngine?, geminiModel: String, isVision: Boolean = false): String = when (engine) {
        AiEngine.GROQ -> if (isVision) "Groq LPU · Llama 4 Scout" else "Groq LPU · Llama 3.3"
        AiEngine.GEMINI -> "Gemini · $geminiModel"
        null -> "Offline Mode"
    }
}

/** Coordinates dual-engine SSE streaming with transparent automatic fallback between Groq and Gemini. */
class AiRouter(
    private val groq: GroqClient,
    private val gemini: GeminiClient
) {

    suspend fun streamProductCopy(
        preference: AiPreference,
        groqKey: String,
        geminiKey: String,
        geminiModel: String,
        imageJpeg: ByteArray,
        language: CopyLanguage,
        notes: String,
        onStatus: (engine: AiEngine, modelId: String, status: String) -> Unit,
        onDelta: (accumulated: String) -> Unit
    ): AiStreamOutcome = runWithFallback(
        preference = preference,
        groqKey = groqKey,
        geminiKey = geminiKey,
        geminiModel = geminiModel,
        isVision = true,
        streamForEngine = { engine ->
            when (engine) {
                AiEngine.GROQ -> groq.streamProductCopy(groqKey, imageJpeg, language, notes)
                AiEngine.GEMINI -> gemini.streamProductCopy(geminiKey, geminiModel, imageJpeg, language, notes)
            }
        },
        onStatus = onStatus,
        onDelta = onDelta
    )

    suspend fun streamChatAboutDocument(
        preference: AiPreference,
        groqKey: String,
        geminiKey: String,
        geminiModel: String,
        documentText: String?,
        pageImages: List<ByteArray>,
        history: List<GeminiContent>,
        onStatus: (engine: AiEngine, modelId: String, status: String) -> Unit,
        onDelta: (accumulated: String) -> Unit
    ): AiStreamOutcome = runWithFallback(
        preference = preference,
        groqKey = groqKey,
        geminiKey = geminiKey,
        geminiModel = geminiModel,
        isVision = pageImages.isNotEmpty(),
        streamForEngine = { engine ->
            when (engine) {
                AiEngine.GROQ -> groq.streamChatAboutDocument(groqKey, documentText, pageImages, history)
                AiEngine.GEMINI -> gemini.streamChatAboutDocument(geminiKey, geminiModel, documentText, pageImages, history)
            }
        },
        onStatus = onStatus,
        onDelta = onDelta
    )

    private suspend fun runWithFallback(
        preference: AiPreference,
        groqKey: String,
        geminiKey: String,
        geminiModel: String,
        isVision: Boolean,
        streamForEngine: (AiEngine) -> Flow<String>,
        onStatus: (engine: AiEngine, modelId: String, status: String) -> Unit,
        onDelta: (accumulated: String) -> Unit
    ): AiStreamOutcome {
        val sequence = AiRouting.resolveEngineSequence(
            preference = preference,
            hasGroqKey = groqKey.isNotBlank(),
            hasGeminiKey = geminiKey.isNotBlank()
        )
        if (sequence.isEmpty()) {
            throw GeminiException(
                GeminiException.Kind.NO_KEY,
                "Add a Groq or Google Gemini API key in Settings first."
            )
        }

        var lastError: Throwable? = null
        sequence.forEachIndexed { index, engine ->
            val modelId = when (engine) {
                AiEngine.GROQ -> if (isVision) GroqClient.VISION_PRIMARY_MODEL else GroqClient.TEXT_MODEL
                AiEngine.GEMINI -> geminiModel
            }
            val connectLabel = if (index == 0) {
                when (engine) {
                    AiEngine.GROQ -> "Menghubungkan ke Groq LPU (${if (isVision) "Llama 4 Scout" else "Llama 3.3"})..."
                    AiEngine.GEMINI -> "Menghubungkan ke Gemini ($geminiModel)..."
                }
            } else {
                when (engine) {
                    AiEngine.GROQ -> "Gemini terganggu — beralih ke Groq LPU..."
                    AiEngine.GEMINI -> "Groq terganggu — beralih ke Gemini ($geminiModel)..."
                }
            }
            onStatus(engine, modelId, connectLabel)

            val acc = StringBuilder()
            var emittedAny = false
            try {
                streamForEngine(engine).collect { delta ->
                    if (!emittedAny) {
                        emittedAny = true
                        val streamLabel = when (engine) {
                            AiEngine.GROQ -> "Groq LPU · Streaming respons..."
                            AiEngine.GEMINI -> "Gemini · Streaming respons..."
                        }
                        onStatus(engine, modelId, streamLabel)
                    }
                    acc.append(delta)
                    onDelta(acc.toString())
                }
                return AiStreamOutcome(engine = engine, modelId = modelId, text = acc.toString().trim())
            } catch (e: Throwable) {
                lastError = e
                val canTryNext = index < sequence.lastIndex && AiRouting.shouldAttemptFallback(e, emittedAny)
                if (!canTryNext) throw e
            }
        }
        throw lastError ?: GeminiException(GeminiException.Kind.UNKNOWN, "AI request failed.")
    }
}
