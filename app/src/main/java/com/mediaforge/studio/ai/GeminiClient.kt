package com.mediaforge.studio.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
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
    val captionWithTags get() = caption.trim() + "\n\n" + hashtagLine
    fun asPlainText(): String = buildString {
        appendLine("TITLE"); appendLine(title); appendLine()
        appendLine("VIDEO HOOKS"); hooks.forEachIndexed { i, h -> appendLine("${i + 1}. $h") }; appendLine()
        appendLine("CAPTION"); appendLine(captionWithTags)
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
 * Minimal Gemini REST client (HttpURLConnection + org.json) – no SDK, no version conflicts.
 * The API key travels in the `x-goog-api-key` header, never in the URL.
 */
class GeminiClient {

    // ------------------------------------------------------------------ public API

    /** Validates the key and returns the models that support generateContent. */
    suspend fun testConnection(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val body = http("GET", "$BASE/models?pageSize=200", apiKey, null)
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

    suspend fun generateProductCopy(
        apiKey: String,
        model: String,
        imageJpeg: ByteArray,
        language: CopyLanguage,
        notes: String
    ): ProductCopy = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val prompt = buildString {
            appendLine("You are a senior e-commerce copywriter and short-form video strategist.")
            appendLine("Study the product in the photo and write marketing copy in ${language.promptName}.")
            appendLine("- title: one catchy product title, max 70 characters, no quotes.")
            appendLine("- hooks: EXACTLY 3 different 15-second review-style video hooks (each 1-2 punchy spoken sentences, max 28 words).")
            appendLine("- caption: a persuasive marketplace / social caption of 60-110 words with short lines, at most 3 tasteful emojis and a clear call to action. Do NOT include hashtags in the caption.")
            appendLine("- hashtags: 8-12 relevant hashtags (each starting with #, no spaces).")
            appendLine("Only describe what is plausibly visible; never invent specifications, prices or certifications.")
            if (notes.isNotBlank()) appendLine("Seller notes to respect: ${notes.trim()}")
        }
        val schema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("title", JSONObject().put("type", "STRING"))
                    .put("hooks", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")))
                    .put("caption", JSONObject().put("type", "STRING"))
                    .put("hashtags", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")))
            )
            .put("required", JSONArray(listOf("title", "hooks", "caption", "hashtags")))

        val text = generate(
            apiKey, model,
            listOf(GeminiContent("user", listOf(GeminiPart.Image(imageJpeg), GeminiPart.Text(prompt)))),
            systemInstruction = null,
            jsonSchema = schema,
            temperature = 0.9
        )
        parseCopy(text)
    }

    /** Document Q&A / summarisation. `history` must end with the latest user turn. */
    suspend fun chatAboutDocument(
        apiKey: String,
        model: String,
        documentText: String?,
        pageImages: List<ByteArray>,
        history: List<GeminiContent>
    ): String = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val system = buildString {
            appendLine("You are MediaForge's document assistant. Answer strictly from the document provided; if the answer is not in the document, say so.")
            appendLine("Reply in the language the user writes in unless asked to translate. Use clean Markdown.")
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
        generate(apiKey, model, contents, system, null, temperature = 0.4)
    }

    // ------------------------------------------------------------------ core call

    private fun generate(
        apiKey: String,
        model: String,
        contents: List<GeminiContent>,
        systemInstruction: String?,
        jsonSchema: JSONObject?,
        temperature: Double
    ): String {
        val body = JSONObject()
        body.put("contents", JSONArray().apply { contents.forEach { put(it.toJson()) } })
        if (systemInstruction != null) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
        }
        val gen = JSONObject().put("temperature", temperature)
        if (jsonSchema != null) {
            gen.put("responseMimeType", "application/json").put("responseSchema", jsonSchema)
        }
        body.put("generationConfig", gen)

        val response = http("POST", "$BASE/models/$model:generateContent", apiKey, body.toString())
        return extractText(JSONObject(response))
    }

    private fun extractText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw GeminiException(
                GeminiException.Kind.BLOCKED,
                if (reason.isNotEmpty()) "Request blocked by Gemini safety filters ($reason)." else "Gemini returned no answer."
            )
        }
        val cand = candidates.getJSONObject(0)
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false) && p.has("text")) sb.append(p.getString("text"))
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) {
            val finish = cand.optString("finishReason")
            throw GeminiException(
                GeminiException.Kind.BLOCKED,
                "Gemini returned an empty answer" + if (finish.isNotEmpty()) " (finish reason: $finish)." else "."
            )
        }
        return text
    }

    private fun parseCopy(raw: String): ProductCopy {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val o = JSONObject(cleaned)
            fun list(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).map { a.getString(it).trim() }.filter { it.isNotEmpty() }
            }
            ProductCopy(
                title = o.optString("title").trim(),
                hooks = list("hooks").take(3),
                caption = o.optString("caption").trim(),
                hashtags = list("hashtags")
            )
        } catch (e: Exception) {
            throw GeminiException(GeminiException.Kind.UNKNOWN, "Gemini replied in an unexpected format. Please try again.")
        }
    }

    // ------------------------------------------------------------------ HTTP

    private fun requireKey(key: String) {
        if (key.isBlank()) throw GeminiException(GeminiException.Kind.NO_KEY, "Add your Gemini API key in Settings / Vault first.")
    }

    private fun http(method: String, url: String, apiKey: String, body: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw mapError(code, text)
            return text
        } catch (e: GeminiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw GeminiException(GeminiException.Kind.OFFLINE, "No internet connection – Offline Mode is active.")
        } catch (e: SocketTimeoutException) {
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
                GeminiException(GeminiException.Kind.AUTH, "The API key is not valid. Check it in Settings / Vault.")
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
