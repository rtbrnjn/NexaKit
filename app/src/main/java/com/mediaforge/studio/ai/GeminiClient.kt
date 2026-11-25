package com.mediaforge.studio.ai

import android.util.Base64
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class GeminiException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, OFFLINE, AUTH, QUOTA, BLOCKED, BAD_REQUEST, SERVER, UNKNOWN }
}

sealed interface GeminiPart {
    data class Text(val text: String) : GeminiPart
    class Image(val bytes: ByteArray, val mime: String = "image/jpeg") : GeminiPart
}

data class GeminiContent(val role: String, val parts: List<GeminiPart>) {
    companion object {
        fun user(text: String) = GeminiContent("user", listOf(GeminiPart.Text(text)))
        fun model(text: String) = GeminiContent("model", listOf(GeminiPart.Text(text)))
    }
}

data class ProductCopy(
    val title: String,
    val hooks: List<String>,
    val caption: String,
    val hashtags: List<String>
) {
    val hashtagLine get() = hashtags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
    val captionWithTags get() = (caption.trim() + if (hashtagLine.isNotBlank()) "\n\n$hashtagLine" else "").trim()
    fun asPlainText(): String = buildString {
        appendLine("TITLE"); appendLine(title); appendLine()
        appendLine("VIDEO HOOKS"); hooks.forEachIndexed { i, h -> appendLine("${i + 1}. $h") }; appendLine()
        appendLine("CAPTION"); appendLine(captionWithTags)
    }

    companion object {
        /** Parses streamed section text (`TITLE:`, `HOOKS:`, `CAPTION:`, `HASHTAGS:`) incrementally or completely. */
        fun parseStreaming(raw: String): ProductCopy {
            val cleaned = raw.replace("```", "").trim()
            val lines = cleaned.lines()
            var section = ""
            val titleLines = ArrayList<String>()
            val hookLines = ArrayList<String>()
            val captionLines = ArrayList<String>()
            val tagTokens = ArrayList<String>()

            val headerRegex = Regex("""^(?:\*{0,2})?(TITLE|HOOKS|VIDEO HOOKS|CAPTION|HASHTAGS)(?:\*{0,2})?\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
            for (rawLine in lines) {
                val line = rawLine.trim()
                val match = headerRegex.matchEntire(line)
                if (match != null) {
                    val key = match.groupValues[1].uppercase()
                    val inlineVal = match.groupValues[2].trim()
                    section = when {
                        key.contains("TITLE") -> "TITLE"
                        key.contains("HOOK") -> "HOOKS"
                        key.contains("CAPTION") -> "CAPTION"
                        key.contains("HASHTAG") -> "HASHTAGS"
                        else -> section
                    }
                    if (inlineVal.isNotEmpty()) {
                        when (section) {
                            "TITLE" -> titleLines += inlineVal
                            "HOOKS" -> hookLines += inlineVal
                            "CAPTION" -> captionLines += inlineVal
                            "HASHTAGS" -> tagTokens += inlineVal.split(Regex("\\s+")).filter { it.isNotBlank() }
                        }
                    }
                    continue
                }
                when (section) {
                    "TITLE" -> if (line.isNotEmpty()) titleLines += line
                    "HOOKS" -> if (line.isNotEmpty()) {
                        val stripped = line.replace(Regex("""^(?:\d+[.)]|[-*•])\s*"""), "").trim()
                        if (stripped.isNotEmpty()) hookLines += stripped
                    }
                    "CAPTION" -> captionLines += rawLine.trimEnd()
                    "HASHTAGS" -> if (line.isNotEmpty()) {
                        tagTokens += line.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
                    }
                }
            }

            val parsedTitle = titleLines.joinToString(" ").trim()
            val parsedHooks = hookLines.filter { it.isNotBlank() }.take(3)
            val parsedCaption = captionLines.joinToString("\n").trim()
            val parsedTags = tagTokens
                .map { it.trim().trimEnd(',', '.') }
                .filter { it.length > 1 }
                .map { if (it.startsWith("#")) it else "#$it" }
                .distinct()

            return ProductCopy(
                title = parsedTitle.ifEmpty { if (section.isEmpty()) cleaned.lines().firstOrNull().orEmpty() else "" },
                hooks = parsedHooks,
                caption = parsedCaption,
                hashtags = parsedTags
            )
        }
    }
}

enum class CopyLanguage(val label: String, val promptName: String) {
    ENGLISH("English", "English"),
    INDONESIAN("Bahasa Indonesia", "Indonesian (Bahasa Indonesia)")
}

enum class DocTask(val label: String, val prompt: String) {
    SUMMARY(
        "Executive summary",
        "Write an executive summary of the document: a 2-sentence overview followed by a concise summary of at most 200 words. Use clear Markdown."
    ),
    TAKEAWAYS(
        "Key takeaways",
        "List the 6-10 most important key takeaways of the document as concise bullet points (Markdown '- ' bullets). Bold the key term at the start of each bullet."
    ),
    TRANSLATE_ID(
        "Translate → Indonesian",
        "Translate the document faithfully into Indonesian (Bahasa Indonesia). Preserve headings, lists and structure."
    ),
    TRANSLATE_EN(
        "Translate → English",
        "Translate the document faithfully into English. Preserve headings, lists and structure."
    )
}

/**
 * Lightweight Gemini REST client using `HttpURLConnection` + `org.json` + SSE Kotlin `Flow`.
 * Configured for Gemini 3.8 / Gemini 4 low-latency SSE streaming (`thinkingLevel: LOW`) and legacy 2.5 Flash compatibility.
 */
class GeminiClient {

    /** Validates the key and returns models supporting generateContent. */
    suspend fun testConnection(apiKey: String): List<String> {
        val key = requireKey(apiKey)
        val body = SseHttp.getJson(
            url = "$BASE/models?pageSize=200",
            headers = mapOf("x-goog-api-key" to key),
            providerName = "Gemini"
        )
        val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val methods = m.optJSONArray("supportedGenerationMethods")
            val supports = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
            val name = m.optString("name").removePrefix("models/")
            if (supports && name.startsWith("gemini") && !name.contains("embedding") &&
                !name.contains("tts") && !name.contains("image") && !name.contains("live")
            ) name else null
        }.sortedDescending()
    }

    /** Streams Product Copywriter output word-by-word via `:streamGenerateContent?alt=sse`. */
    fun streamProductCopy(
        apiKey: String,
        model: String,
        imageJpeg: ByteArray,
        language: CopyLanguage,
        notes: String
    ): Flow<String> {
        val prompt = buildProductCopyPrompt(language, notes)
        return streamGenerate(
            apiKey = apiKey,
            model = model,
            contents = listOf(GeminiContent("user", listOf(GeminiPart.Image(imageJpeg), GeminiPart.Text(prompt)))),
            systemInstruction = null,
            temperature = 0.85
        )
    }

    /** Streams PDF Q&A / summarisation word-by-word via `:streamGenerateContent?alt=sse`. */
    fun streamChatAboutDocument(
        apiKey: String,
        model: String,
        documentText: String?,
        pageImages: List<ByteArray>,
        history: List<GeminiContent>
    ): Flow<String> {
        val system = buildDocumentSystemPrompt(documentText)
        val contents = history.toMutableList()
        if (pageImages.isNotEmpty() && contents.isNotEmpty()) {
            val first = contents.indexOfFirst { it.role == "user" }
            if (first >= 0) {
                val imgs = pageImages.map { GeminiPart.Image(it) }
                val intro = GeminiPart.Text("The document consists of these scanned page images (in order).")
                contents[first] = contents[first].copy(parts = imgs + intro + contents[first].parts)
            }
        }
        return streamGenerate(apiKey, model, contents, system, temperature = 0.4)
    }

    /** Streams structured English AI image/video prompt + negative prompt via SSE. */
    fun streamPromptGeneration(
        apiKey: String,
        model: String,
        idea: String,
        target: PromptTarget,
        style: VisualStyle,
        lighting: LightingPreset,
        ratio: PromptAspectRatio
    ): Flow<String> {
        val userPrompt = buildPromptMakerUserPrompt(idea, target, style, lighting, ratio)
        return streamGenerate(
            apiKey = apiKey,
            model = model,
            contents = listOf(GeminiContent.user(userPrompt)),
            systemInstruction = buildPromptMakerSystemPrompt(),
            temperature = 0.85
        )
    }

    /**
     * Generates an AI image using the saved Gemini API key:
     * 1. Tries Gemini native image generation (`generateContent` with `IMAGE` modality)
     * 2. Automatically falls back to Imagen REST `:predict` if needed
     * Provides actionable Indonesian error diagnostics when quota/key lacks image generation access.
     */
    suspend fun generateImage(
        apiKey: String,
        prompt: String,
        aspectRatio: ImageAspectRatio,
        negativePrompt: String = ""
    ): GeneratedImageResult {
        val key = requireKey(apiKey)
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            throw GeminiException(GeminiException.Kind.BAD_REQUEST, "Masukkan prompt gambar terlebih dahulu.")
        }
        val composedPrompt = buildString {
            append(cleanPrompt)
            append(". Aspect ratio: ${aspectRatio.apiValue}.")
            if (negativePrompt.isNotBlank()) {
                append(" Avoid: ${negativePrompt.trim()}.")
            }
        }

        val headers = mapOf("x-goog-api-key" to key)
        var lastError: GeminiException? = null

        // 1. Try Gemini native multimodal image generation models via :generateContent
        for (modelId in GEMINI_IMAGE_MODELS) {
            val attempts = listOf(true, false)
            for (includeImageConfig in attempts) {
                val genConfig = JSONObject()
                    .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                if (includeImageConfig) {
                    genConfig.put("imageConfig", JSONObject().put("aspectRatio", aspectRatio.apiValue))
                }
                val body = JSONObject()
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", composedPrompt)))
                        )
                    )
                    .put("generationConfig", genConfig)

                try {
                    val raw = SseHttp.postJson(
                        url = "$BASE/models/$modelId:generateContent",
                        headers = headers,
                        body = body,
                        providerName = "Gemini Image"
                    )
                    val parsed = extractGeneratedImageFromGenerateContent(raw, modelId)
                    if (parsed != null) return parsed
                    lastError = GeminiException(
                        GeminiException.Kind.BLOCKED,
                        "Model $modelId tidak mengembalikan gambar. Coba perjelas prompt Anda."
                    )
                    break
                } catch (e: GeminiException) {
                    lastError = e
                    if (e.kind == GeminiException.Kind.OFFLINE) throw e
                    // Retry same model without imageConfig if field rejected with 400
                    if (includeImageConfig && e.kind == GeminiException.Kind.BAD_REQUEST &&
                        (e.message?.contains("imageConfig", ignoreCase = true) == true ||
                            e.message?.contains("aspectRatio", ignoreCase = true) == true ||
                            e.message?.contains("Unknown name", ignoreCase = true) == true)
                    ) {
                        continue
                    }
                    break
                }
            }
        }

        // 2. Fallback to Imagen REST :predict endpoints
        for (imagenModel in IMAGEN_MODELS) {
            val body = JSONObject()
                .put("instances", JSONArray().put(JSONObject().put("prompt", composedPrompt)))
                .put(
                    "parameters",
                    JSONObject()
                        .put("sampleCount", 1)
                        .put("aspectRatio", aspectRatio.apiValue)
                )
            try {
                val raw = SseHttp.postJson(
                    url = "$BASE/models/$imagenModel:predict",
                    headers = headers,
                    body = body,
                    providerName = "Imagen"
                )
                val parsed = extractGeneratedImageFromImagenPredict(raw, imagenModel)
                if (parsed != null) return parsed
            } catch (e: GeminiException) {
                lastError = e
                if (e.kind == GeminiException.Kind.OFFLINE) throw e
            }
        }

        throw enrichImageError(lastError)
    }

    private fun enrichImageError(error: GeminiException?): GeminiException {
        if (error == null) {
            return GeminiException(
                GeminiException.Kind.UNKNOWN,
                "Gagal membuat gambar AI. Periksa koneksi dan API Key Gemini Anda di Settings."
            )
        }
        val msg = error.message.orEmpty()
        return when {
            error.kind == GeminiException.Kind.AUTH || msg.contains("billed", ignoreCase = true) ||
                msg.contains("billing", ignoreCase = true) || msg.contains("permission", ignoreCase = true) ->
                GeminiException(
                    GeminiException.Kind.AUTH,
                    "API Key Gemini Anda belum mendukung akses generasi gambar (atau memerlukan kuota/billing aktif di Google AI Studio). Detail: $msg"
                )
            error.kind == GeminiException.Kind.QUOTA || msg.contains("quota", ignoreCase = true) ||
                msg.contains("429") ->
                GeminiException(
                    GeminiException.Kind.QUOTA,
                    "Kuota generasi gambar Gemini telah mencapai batas (rate limit / free-tier limit). Tunggu beberapa saat atau cek kuota di Google AI Studio."
                )
            error.kind == GeminiException.Kind.BLOCKED -> error
            else -> GeminiException(
                error.kind,
                "Pembuatan gambar gagal: $msg. Pastikan API Key Gemini Anda valid dan memiliki akses model gambar."
            )
        }
    }

    // ------------------------------------------------------------------ SSE streaming core

    private fun streamGenerate(
        apiKey: String,
        model: String,
        contents: List<GeminiContent>,
        systemInstruction: String?,
        temperature: Double
    ): Flow<String> {
        val key = requireKey(apiKey)
        val body = buildRequestBody(contents, systemInstruction, temperature, model)
        return SseHttp.postSseStream(
            url = "$BASE/models/$model:streamGenerateContent?alt=sse",
            headers = mapOf("x-goog-api-key" to key),
            body = body,
            providerName = "Gemini",
            extractDelta = ::extractSseText
        )
    }

    private fun buildRequestBody(
        contents: List<GeminiContent>,
        systemInstruction: String?,
        temperature: Double,
        model: String
    ): JSONObject {
        val body = JSONObject()
        body.put("contents", JSONArray().apply { contents.forEach { put(it.toJson()) } })
        if (systemInstruction != null) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }
        val lower = model.lowercase()
        val isDeepModel = lower.contains("hydrogen") || lower.contains("pro")
        val gen = JSONObject()
            .put("temperature", temperature)
            .put("maxOutputTokens", if (isDeepModel) 4096 else 2048)
        when {
            lower.contains("2.5") -> {
                val budget = if (lower.contains("pro")) 128 else 0
                gen.put("thinkingConfig", JSONObject().put("thinkingBudget", budget))
            }
            lower.startsWith("gemini-3") || lower.startsWith("gemini-4") -> {
                // Gemini 3.8 Flash rejects `MINIMAL`; `LOW` provides minimum reasoning latency without 400 errors.
                val level = if (isDeepModel) "MEDIUM" else "LOW"
                gen.put("thinkingConfig", JSONObject().put("thinkingLevel", level))
            }
        }
        body.put("generationConfig", gen)
        return body
    }

    private fun extractSseText(rawJson: String): String {
        val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return ""
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            if (reason.isNotEmpty()) {
                throw GeminiException(GeminiException.Kind.BLOCKED, "Request blocked by Gemini safety filters ($reason).")
            }
            return ""
        }
        val cand = candidates.getJSONObject(0)
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false) && p.has("text")) {
                sb.append(p.getString("text"))
            }
        }
        return sb.toString()
    }

    private fun requireKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) throw GeminiException(GeminiException.Kind.NO_KEY, "Add your Gemini API key in Settings first.")
        return trimmed
    }

    private fun GeminiContent.toJson(): JSONObject {
        val arr = JSONArray()
        parts.forEach { p ->
            when (p) {
                is GeminiPart.Text -> arr.put(JSONObject().put("text", p.text))
                is GeminiPart.Image -> arr.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", p.mime)
                            .put("data", Base64.encodeToString(p.bytes, Base64.NO_WRAP))
                    )
                )
            }
        }
        return JSONObject().put("role", role).put("parts", arr)
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        val GEMINI_IMAGE_MODELS = listOf(
            "gemini-2.5-flash-image",
            "gemini-2.0-flash-exp-image-generation"
        )
        val IMAGEN_MODELS = listOf(
            "imagen-4.0-generate-001",
            "imagen-3.0-generate-002"
        )

        /** Parses base64 `inlineData` image payload from a Gemini `:generateContent` JSON response. */
        fun extractGeneratedImageFromGenerateContent(rawJson: String, modelId: String): GeneratedImageResult? {
            val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                if (reason.isNotEmpty()) {
                    throw GeminiException(
                        GeminiException.Kind.BLOCKED,
                        "Prompt diblokir oleh filter keamanan Gemini ($reason). Coba ubah deskripsi gambar."
                    )
                }
                return null
            }
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts") ?: return null
            var imageBytes: ByteArray? = null
            var mime = "image/png"
            val captions = ArrayList<String>()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                if (inline != null && imageBytes == null) {
                    val data = inline.optString("data").trim()
                    if (data.isNotEmpty()) {
                        mime = inline.optString("mimeType").ifBlank {
                            inline.optString("mime_type").ifBlank { "image/png" }
                        }
                        imageBytes = decodeBase64Flexible(data)
                    }
                }
                val text = part.optString("text").trim()
                if (text.isNotEmpty() && !part.optBoolean("thought", false)) {
                    captions += text
                }
            }
            val bytes = imageBytes ?: return null
            return GeneratedImageResult(
                bytes = bytes,
                mimeType = mime,
                modelId = modelId,
                caption = captions.joinToString("\n").trim()
            )
        }

        /** Parses `predictions[0].bytesBase64Encoded` from a Google Imagen `:predict` JSON response. */
        fun extractGeneratedImageFromImagenPredict(rawJson: String, modelId: String): GeneratedImageResult? {
            val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            val preds = json.optJSONArray("predictions") ?: return null
            if (preds.length() == 0) return null
            val first = preds.optJSONObject(0) ?: return null
            val b64 = first.optString("bytesBase64Encoded").trim()
            if (b64.isEmpty()) return null
            val mime = first.optString("mimeType").ifBlank { "image/png" }
            val bytes = decodeBase64Flexible(b64) ?: return null
            return GeneratedImageResult(bytes = bytes, mimeType = mime, modelId = modelId)
        }

        private fun decodeBase64Flexible(b64: String): ByteArray? =
            runCatching { Base64.decode(b64, Base64.DEFAULT) }
                .recoverCatching { java.util.Base64.getMimeDecoder().decode(b64) }
                .getOrNull()
    }
}
