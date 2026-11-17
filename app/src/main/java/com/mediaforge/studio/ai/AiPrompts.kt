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
    appendLine("You are NexaKit's document assistant. Answer strictly from the document provided; if the answer is not in the document, say so.")
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
