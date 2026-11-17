package com.mediaforge.studio.ai

import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ultra-fast Groq LPU REST client (`https://api.groq.com/openai/v1`) with OpenAI-compatible SSE streaming.
 * Uses `llama-3.3-70b-versatile` for PDF analysis and `meta-llama/llama-4-scout-17b-16e-instruct`
 * (with fallback to `llama-3.2-11b-vision-preview`) for vision tasks.
 */
class GroqClient {

    /** Validates the Groq API key (`gsk_...`) via `/openai/v1/models` and returns available model IDs. */
    suspend fun testConnection(apiKey: String): List<String> {
        val key = requireKey(apiKey)
        val body = SseHttp.getJson(
            url = "$BASE/models",
            headers = authHeaders(key),
            providerName = PROVIDER
        )
        val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return (0 until arr.length())
            .mapNotNull { i -> arr.optJSONObject(i)?.optString("id")?.takeIf(String::isNotBlank) }
            .sorted()
    }

    /** Streams Product Copywriter output using compressed JPEG (`768px` / `75%`) as OpenAI Vision data URL. */
    fun streamProductCopy(
        apiKey: String,
        imageJpeg: ByteArray,
        language: CopyLanguage,
        notes: String
    ): Flow<String> {
        val prompt = buildProductCopyPrompt(language, notes)
        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", jpegDataUrl(imageJpeg))))
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", userContent))
        return streamWithVisionFallback(apiKey, messages, temperature = 0.85, maxTokens = 1400)
    }

    /** Streams PDF Q&A / summarisation using Llama 3.3 70B Versatile (or Llama 4 Scout Vision if Vision Mode images supplied). */
    fun streamChatAboutDocument(
        apiKey: String,
        documentText: String?,
        pageImages: List<ByteArray>,
        history: List<GeminiContent>
    ): Flow<String> {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", buildDocumentSystemPrompt(documentText)))

        val firstUserIdx = history.indexOfFirst { it.role == "user" }
        history.forEachIndexed { idx, turn ->
            val role = if (turn.role == "model") "assistant" else turn.role
            val textBody = turn.parts.filterIsInstance<GeminiPart.Text>().joinToString("\n") { it.text }
            if (idx == firstUserIdx && pageImages.isNotEmpty()) {
                val arr = JSONArray()
                pageImages.forEach { img ->
                    arr.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", jpegDataUrl(img))))
                }
                arr.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", "The document consists of these scanned page images (in order).\n$textBody")
                )
                messages.put(JSONObject().put("role", role).put("content", arr))
            } else {
                messages.put(JSONObject().put("role", role).put("content", textBody))
            }
        }

        return if (pageImages.isNotEmpty()) {
            streamWithVisionFallback(apiKey, messages, temperature = 0.4, maxTokens = 2048)
        } else {
            streamModel(apiKey, TEXT_MODEL, messages, temperature = 0.4, maxTokens = 2048)
        }
    }

    private fun streamWithVisionFallback(
        apiKey: String,
        messages: JSONArray,
        temperature: Double,
        maxTokens: Int
    ): Flow<String> = flow {
        var emitted = false
        try {
            streamModel(apiKey, VISION_PRIMARY_MODEL, messages, temperature, maxTokens).collect { chunk ->
                emitted = true
                emit(chunk)
            }
        } catch (e: GeminiException) {
            if (!emitted && e.kind == GeminiException.Kind.BAD_REQUEST) {
                streamModel(apiKey, VISION_FALLBACK_MODEL, messages, temperature, maxTokens).collect { chunk ->
                    emit(chunk)
                }
            } else {
                throw e
            }
        }
    }

    private fun streamModel(
        apiKey: String,
        model: String,
        messages: JSONArray,
        temperature: Double,
        maxTokens: Int
    ): Flow<String> {
        val key = requireKey(apiKey)
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)

        return SseHttp.postSseStream(
            url = "$BASE/chat/completions",
            headers = authHeaders(key),
            body = body,
            providerName = PROVIDER,
            extractDelta = ::extractGroqDelta
        )
    }

    private fun extractGroqDelta(rawJson: String): String {
        val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return ""
        val errMsg = json.optJSONObject("error")?.optString("message").orEmpty()
        if (errMsg.isNotEmpty()) throw GeminiException(GeminiException.Kind.SERVER, errMsg)
        val choices = json.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
        val content = delta.opt("content")
        return if (content is String) content else ""
    }

    private fun jpegDataUrl(jpegBytes: ByteArray): String =
        "data:image/jpeg;base64," + Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

    private fun requireKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            throw GeminiException(GeminiException.Kind.NO_KEY, "Add your Groq API key in Settings first.")
        }
        return trimmed
    }

    private fun authHeaders(apiKey: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey"
    )

    companion object {
        const val PROVIDER = "Groq"
        const val BASE = "https://api.groq.com/openai/v1"
        const val TEXT_MODEL = "llama-3.3-70b-versatile"
        const val VISION_PRIMARY_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
        const val VISION_FALLBACK_MODEL = "llama-3.2-11b-vision-preview"
    }
}
