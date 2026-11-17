package com.mediaforge.studio.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

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
    suspend fun testConnection(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val body = httpGet("$BASE/models?pageSize=200", apiKey)
        val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
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
        val prompt = buildString {
            appendLine("You are a senior e-commerce copywriter and short-form video strategist.")
            appendLine("Study the product in the photo and write marketing copy in ${language.promptName}.")
            appendLine("Follow this EXACT plain section format (no markdown code fences):")
            appendLine("TITLE:")
            appendLine("<one catchy product title, max 70 characters, no quotes>")
            appendLine()
            appendLine("HOOKS:")
            appendLine("1. <15-second review video hook #1, punchy spoken sentence, max 26 words>")
            appendLine("2. <15-second review video hook #2>")
            appendLine("3. <15-second review video hook #3>")
            appendLine()
            appendLine("CAPTION:")
            appendLine("<persuasive marketplace/social caption of 60-100 words, short lines, at most 3 tasteful emojis, clear CTA, NO hashtags here>")
            appendLine()
            appendLine("HASHTAGS:")
            appendLine("<8-12 relevant hashtags separated by spaces>")
            appendLine()
            appendLine("Only describe what is visibly clear; never invent prices, specs, or certifications.")
            if (notes.isNotBlank()) appendLine("Seller notes to respect: ${notes.trim()}")
        }
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
        val system = buildString {
            appendLine("You are NexaKit's document assistant. Answer strictly from the document provided; if the answer is not in the document, say so.")
            appendLine("Reply in the language the user writes in unless asked to translate. Use clean, concise Markdown.")
            if (!documentText.isNullOrBlank()) {
                appendLine()
                appendLine("=== DOCUMENT START ===")
                appendLine(documentText)
                appendLine("=== DOCUMENT END ===")
            }
        }
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

    // ------------------------------------------------------------------ SSE streaming core

    private fun streamGenerate(
        apiKey: String,
        model: String,
        contents: List<GeminiContent>,
        systemInstruction: String?,
        temperature: Double
    ): Flow<String> = flow {
        requireKey(apiKey)
        val body = buildRequestBody(contents, systemInstruction, temperature, model)
        val conn = (URL("$BASE/models/$model:streamGenerateContent?alt=sse").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw mapError(code, errText)
            }
            val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
            val eventData = StringBuilder()
            var emittedAny = false

            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trimStart()
                        if (payload == "[DONE]") break
                        if (eventData.isNotEmpty()) eventData.append('\n')
                        eventData.append(payload)
                    }
                    line.isBlank() -> {
                        if (eventData.isNotEmpty()) {
                            val chunk = extractSseText(eventData.toString())
                            eventData.clear()
                            if (chunk.isNotEmpty()) {
                                emittedAny = true
                                emit(chunk)
                            }
                        }
                    }
                }
            }
            if (eventData.isNotEmpty()) {
                val chunk = extractSseText(eventData.toString())
                if (chunk.isNotEmpty()) {
                    emittedAny = true
                    emit(chunk)
                }
            }
            if (!emittedAny) {
                throw GeminiException(GeminiException.Kind.BLOCKED, "Gemini returned an empty response.")
            }
        } catch (e: GeminiException) {
            throw e
        } catch (_: UnknownHostException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "No internet connection – Offline Mode is active.")
        } catch (_: SocketTimeoutException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "The request timed out. Check your connection and retry.")
        } catch (e: IOException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "Network error: ${e.message ?: "connection failed"}")
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

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

    // ------------------------------------------------------------------ HTTP helpers

    private fun requireKey(key: String) {
        if (key.isBlank()) throw GeminiException(GeminiException.Kind.NO_KEY, "Add your Gemini API key in Settings first.")
    }

    private fun httpGet(url: String, apiKey: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw mapError(code, text)
            return text
        } catch (e: GeminiException) {
            throw e
        } catch (_: UnknownHostException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "No internet connection – Offline Mode is active.")
        } catch (_: SocketTimeoutException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "The request timed out. Check your connection and retry.")
        } catch (e: IOException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "Network error: ${e.message ?: "connection failed"}")
        } finally {
            conn.disconnect()
        }
    }

    private fun mapError(code: Int, body: String): GeminiException {
        val apiMsg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        return when (code) {
            400 -> if (apiMsg.contains("API key", ignoreCase = true))
                GeminiException(GeminiException.Kind.AUTH, "The API key is not valid. Check it in Settings.")
            else GeminiException(GeminiException.Kind.BAD_REQUEST, apiMsg.ifEmpty { "Bad request (400)." })
            401, 403 -> GeminiException(GeminiException.Kind.AUTH, apiMsg.ifEmpty { "The API key was rejected (HTTP $code)." })
            404 -> GeminiException(GeminiException.Kind.BAD_REQUEST, apiMsg.ifEmpty { "Model not found – pick another model in Settings." })
            429 -> GeminiException(GeminiException.Kind.QUOTA, "Rate limit or quota reached. Wait a moment and try again.")
            in 500..599 -> GeminiException(GeminiException.Kind.SERVER, "Gemini is temporarily unavailable (HTTP $code). Try again shortly.")
            else -> GeminiException(GeminiException.Kind.UNKNOWN, apiMsg.ifEmpty { "Unexpected error (HTTP $code)." })
        }
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

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
