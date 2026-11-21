package com.mediaforge.studio.data

import com.mediaforge.studio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

sealed interface UpdateCheckResult {
    data class Available(val info: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Checks GitHub Releases for newer versions of DocPix.
 * Tries the primary DocPix repository first and falls back to NexaKit automatically.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (endpoint in ENDPOINTS) {
            try {
                val json = fetchReleaseJson(endpoint) ?: continue
                val info = parseReleaseJson(json) ?: continue
                return@withContext if (isNewerVersion(info.tagName, currentVersion)) {
                    UpdateCheckResult.Available(info)
                } else {
                    UpdateCheckResult.UpToDate(currentVersion)
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        UpdateCheckResult.Error(lastError ?: "Gagal memeriksa pembaruan dari GitHub.")
    }

    private fun fetchReleaseJson(endpoint: String): String? {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "DocPix-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val ENDPOINTS = listOf(
            "https://api.github.com/repos/rtbrnjn/DocPix/releases/latest",
            "https://api.github.com/repos/rtbrnjn/NexaKit/releases/latest"
        )

        /** Parses a GitHub `/releases/latest` JSON payload into [AppUpdateInfo]. */
        fun parseReleaseJson(rawJson: String): AppUpdateInfo? = runCatching {
            val obj = JSONObject(rawJson)
            val tag = obj.optString("tag_name").trim()
            if (tag.isEmpty()) return null

            val cleanVer = normalizeTag(tag)
            val name = obj.optString("name").trim().ifEmpty { "DocPix $tag" }
            val body = obj.optString("body").trim().ifEmpty { "Pembaruan aplikasi DocPix ($tag) siap dipasang." }
            val htmlUrl = obj.optString("html_url").trim()

            var preferredApk: String? = null
            var fallbackApk: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name").trim()
                    val url = asset.optString("browser_download_url").trim()
                    if (url.isEmpty()) continue
                    if (assetName.equals("DocPix.apk", ignoreCase = true)) {
                        preferredApk = url
                        break
                    }
                    if (fallbackApk == null && assetName.endsWith(".apk", ignoreCase = true)) {
                        fallbackApk = url
                    }
                }
            }

            AppUpdateInfo(
                tagName = tag,
                versionName = cleanVer,
                title = name,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                apkDownloadUrl = preferredApk ?: fallbackApk
            )
        }.getOrNull()

        /** Strips optional `v` prefix and pre-release suffix to return the core version string. */
        fun normalizeTag(tag: String): String =
            tag.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+').trim()

        /** Returns true strictly when [remoteTag] represents a higher semantic version than [currentVersion]. */
        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val rParts = parseVersionParts(remoteTag)
            val cParts = parseVersionParts(currentVersion)
            if (rParts.isEmpty() || cParts.isEmpty()) return false
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r != c) return r > c
            }
            return false
        }

        private fun parseVersionParts(version: String): List<Int> {
            val core = normalizeTag(version)
            if (core.isEmpty()) return emptyList()
            val parts = core.split('.').map { token ->
                token.takeWhile { it.isDigit() }.toIntOrNull() ?: return emptyList()
            }
            return parts
        }
    }
}
