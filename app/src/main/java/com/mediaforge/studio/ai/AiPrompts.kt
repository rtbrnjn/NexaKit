package com.mediaforge.studio.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

enum class PromptTarget(val label: String, val promptKind: String) {
    IMAGE("AI Gambar", "AI Image Generation (Midjourney, Flux, Imagen, Gemini)"),
    VIDEO("AI Video", "AI Video Generation (Veo, Sora, Runway, Kling)")
}

enum class VisualStyle(val label: String, val cue: String) {
    CINEMATIC_PHOTO(
        "Cinematic Photo",
        "cinematic photorealistic photography, 35mm anamorphic lens, shallow depth of field, natural film grain, ultra-detailed"
    ),
    LIQUID_GLASS_3D(
        "Liquid Glass 3D",
        "3D Octane render, clear refractive liquid glass material, caustics, specular rim highlights, clean minimalist studio lighting"
    ),
    ANIME(
        "Anime",
        "high-budget theatrical anime key visual, crisp cel shading, expressive lighting, richly detailed atmospheric background"
    ),
    MINIMALIST_VECTOR(
        "Minimalist Vector",
        "minimalist geometric vector artwork, clean precise silhouettes, balanced negative space, modern editorial design"
    ),
    PRODUCT_STUDIO(
        "Product Studio",
        "luxury commercial product studio photography, crisp reflections, macro material detail, seamless studio backdrop"
    )
}

enum class LightingPreset(val label: String, val cue: String) {
    SOFT_AMBIENT("Soft Ambient", "soft diffused ambient lighting, delicate shadow falloff, clean studio glow"),
    GOLDEN_HOUR("Golden Hour", "warm golden hour sunlight, luminous rim backlighting, subtle atmospheric flare"),
    NEON_CYBERPUNK("Neon Cyberpunk", "dramatic neon cyan and magenta rim lighting, moody dark atmosphere, glossy wet reflections")
}

enum class ImageAspectRatio(val label: String, val apiValue: String, val ratioFloat: Float) {
    SQUARE("1:1", "1:1", 1f),
    LANDSCAPE("16:9", "16:9", 16f / 9f),
    PORTRAIT_STORY("9:16", "9:16", 9f / 16f),
    PORTRAIT_3_4("3:4", "3:4", 3f / 4f)
}

enum class PromptAspectRatio(val label: String, val imageRatio: ImageAspectRatio) {
    SQUARE("1:1", ImageAspectRatio.SQUARE),
    LANDSCAPE("16:9", ImageAspectRatio.LANDSCAPE),
    PORTRAIT_STORY("9:16", ImageAspectRatio.PORTRAIT_STORY),
    PORTRAIT_FEED("4:5", ImageAspectRatio.PORTRAIT_3_4)
}

data class GeneratedPromptResult(
    val mainPrompt: String,
    val negativePrompt: String,
    val tips: String = ""
) {
    fun asCopyableText(): String = buildString {
        appendLine(mainPrompt.trim())
        if (negativePrompt.isNotBlank()) {
            appendLine()
            appendLine("Negative Prompt: ${negativePrompt.trim()}")
        }
    }.trim()

    companion object {
        /** Parses structured sections (`PROMPT:`, `NEGATIVE PROMPT:`, `TIPS:`) cleanly from streamed LLM text. */
        fun parse(raw: String): GeneratedPromptResult {
            val cleaned = raw.replace("```", "").trim()
            val lines = cleaned.lines()
            var section = ""
            val mainLines = ArrayList<String>()
            val negLines = ArrayList<String>()
            val tipLines = ArrayList<String>()

            val headerRegex = Regex(
                """^(?:\*{0,2}|#+\s*)?(MAIN PROMPT|POSITIVE PROMPT|PROMPT|NEGATIVE PROMPT|NEGATIVE|TIPS|CATATAN)(?:\*{0,2})?\s*:\s*(.*)$""",
                RegexOption.IGNORE_CASE
            )
            for (rawLine in lines) {
                val line = rawLine.trim()
                val match = headerRegex.matchEntire(line)
                if (match != null) {
                    val key = match.groupValues[1].uppercase()
                    val inlineVal = match.groupValues[2].trim()
                    section = when {
                        key.contains("NEGATIVE") -> "NEGATIVE"
                        key.contains("TIP") || key.contains("CATATAN") -> "TIPS"
                        key.contains("PROMPT") -> "PROMPT"
                        else -> section
                    }
                    if (inlineVal.isNotEmpty()) {
                        when (section) {
                            "PROMPT" -> mainLines += inlineVal
                            "NEGATIVE" -> negLines += inlineVal
                            "TIPS" -> tipLines += inlineVal
                        }
                    }
                    continue
                }
                if (line.isNotEmpty()) {
                    when (section) {
                        "PROMPT" -> mainLines += line
                        "NEGATIVE" -> negLines += line
                        "TIPS" -> tipLines += line
                    }
                }
            }

            val parsedMain = mainLines.joinToString(" ").trim()
            val parsedNeg = negLines.joinToString(" ").trim()
            val parsedTips = tipLines.joinToString(" ").trim()
            return GeneratedPromptResult(
                mainPrompt = parsedMain.ifEmpty { if (section.isEmpty()) cleaned else "" },
                negativePrompt = parsedNeg,
                tips = parsedTips
            )
        }
    }
}

class GeneratedImageResult(
    val bytes: ByteArray,
    val mimeType: String,
    val modelId: String,
    val caption: String = ""
) {
    val fileExtension: String
        get() = when {
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "png"
        }
}

internal fun buildPromptMakerSystemPrompt(): String = buildString {
    appendLine("You are an elite AI visual prompt engineer for Midjourney, Flux, Google Imagen, Gemini Image, and cinematic AI video generators.")
    appendLine("Transform the user's short concept (in any language) into a cohesive, vivid, production-ready English prompt plus a crisp Negative Prompt.")
    appendLine("Follow this EXACT plain-text section format without markdown code fences:")
    appendLine("PROMPT:")
    appendLine("<one rich, well-structured English prompt describing subject, pose/action, environment, camera lens/composition, material texture, lighting, and visual style; 60-110 words>")
    appendLine()
    appendLine("NEGATIVE PROMPT:")
    appendLine("<concise comma-separated English negative prompt avoiding artifacts, distorted anatomy, watermark, text, low quality, blur; 18-35 words>")
    appendLine()
    appendLine("TIPS:")
    appendLine("<1 short helpful suggestion in Bahasa Indonesia, max 22 words>")
}

internal fun buildPromptMakerUserPrompt(
    idea: String,
    target: PromptTarget,
    style: VisualStyle,
    lighting: LightingPreset,
    ratio: PromptAspectRatio
): String = buildString {
    appendLine("User Concept: ${idea.trim()}")
    appendLine("Target Medium: ${target.promptKind}")
    appendLine("Visual Style: ${style.label} (${style.cue})")
    appendLine("Lighting: ${lighting.label} (${lighting.cue})")
    appendLine("Composition / Aspect Ratio: ${ratio.label}")
    if (target == PromptTarget.VIDEO) {
        appendLine("Include subtle, realistic camera motion and temporal continuity cues suited for AI video.")
    }
}

internal fun buildProductCopyPrompt(language: CopyLanguage, notes: String): String = buildString {
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

internal fun buildDocumentSystemPrompt(documentText: String?): String = buildString {
    appendLine("You are DocPix's document assistant. Answer strictly from the document provided; if the answer is not in the document, say so.")
    appendLine("Reply in the language the user writes in unless asked to translate. Use clean, concise Markdown.")
    if (!documentText.isNullOrBlank()) {
        appendLine()
        appendLine("=== DOCUMENT START ===")
        appendLine(documentText)
        appendLine("=== DOCUMENT END ===")
    }
}

internal object SseHttp {

    suspend fun getJson(
        url: String,
        headers: Map<String, String>,
        providerName: String
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw mapHttpError(providerName, code, text)
            text
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

    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        providerName: String,
        readTimeoutMs: Int = 90_000
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = readTimeoutMs
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw mapHttpError(providerName, code, text)
            text
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

    fun postSseStream(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        providerName: String,
        extractDelta: (String) -> String
    ): Flow<String> = flow {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw mapHttpError(providerName, code, errText)
            }
            val emitted = readSseLines(conn.inputStream, extractDelta)
            if (!emitted) {
                throw GeminiException(GeminiException.Kind.BLOCKED, "$providerName returned an empty response.")
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

    private suspend fun FlowCollector<String>.readSseLines(
        stream: InputStream,
        extractDelta: (String) -> String
    ): Boolean {
        val reader = stream.bufferedReader(Charsets.UTF_8)
        val eventData = StringBuilder()
        var emittedAny = false

        suspend fun flushEvent() {
            if (eventData.isEmpty()) return
            val chunk = extractDelta(eventData.toString())
            eventData.clear()
            if (chunk.isNotEmpty()) {
                emittedAny = true
                emit(chunk)
            }
        }

        while (currentCoroutineContext().isActive) {
            val line = reader.readLine() ?: break
            when {
                line.startsWith("data:") -> {
                    val payload = line.removePrefix("data:").trimStart()
                    if (payload == "[DONE]") {
                        flushEvent()
                        break
                    }
                    if (eventData.isNotEmpty()) eventData.append('\n')
                    eventData.append(payload)
                }
                line.isBlank() -> flushEvent()
            }
        }
        flushEvent()
        return emittedAny
    }

    fun mapHttpError(provider: String, code: Int, body: String): GeminiException {
        val apiMsg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        return when (code) {
            400 -> if (apiMsg.contains("API key", ignoreCase = true) || apiMsg.contains("invalid_api_key", ignoreCase = true)) {
                GeminiException(GeminiException.Kind.AUTH, "$provider API key is not valid. Check Settings.")
            } else {
                GeminiException(GeminiException.Kind.BAD_REQUEST, apiMsg.ifEmpty { "Bad request ($code)." })
            }
            401, 403 -> GeminiException(GeminiException.Kind.AUTH, apiMsg.ifEmpty { "$provider API key was rejected (HTTP $code)." })
            404 -> GeminiException(GeminiException.Kind.BAD_REQUEST, apiMsg.ifEmpty { "$provider model not found (404)." })
            429 -> GeminiException(GeminiException.Kind.QUOTA, "$provider rate limit or quota reached. Wait a moment and retry.")
            in 500..599 -> GeminiException(GeminiException.Kind.SERVER, "$provider is temporarily unavailable (HTTP $code).")
            else -> GeminiException(GeminiException.Kind.UNKNOWN, apiMsg.ifEmpty { "$provider unexpected error (HTTP $code)." })
        }
    }
}
