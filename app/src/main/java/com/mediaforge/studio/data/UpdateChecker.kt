package com.mediaforge.studio.data

import android.content.Context
import com.mediaforge.studio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
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

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int?
    ) : UpdateDownloadState
    data class NeedsInstallPermission(val apkFile: File) : UpdateDownloadState
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * Checks GitHub Releases for newer versions of DocPix and downloads `DocPix.apk`
 * directly inside the app with manual GitHub CDN redirect following and real-time progress.
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

    /**
     * Downloads the release APK from [downloadUrl] into [destinationFile] on [Dispatchers.IO],
     * explicitly following HTTP 301/302/303/307/308 redirects (such as GitHub Releases → CDN),
     * validating the APK/ZIP header, and streaming live progress via [onProgress].
     */
    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, progressPercent: Int?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        val conn = openFollowingRedirects(downloadUrl)
        try {
            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
            onProgress(0L, totalBytes, if (totalBytes > 0L) 0 else null)

            var downloaded = 0L
            var lastReportedPct = -1
            var lastReportedBytes = 0L
            val buffer = ByteArray(32 * 1024)

            conn.inputStream.use { input ->
                tempFile.outputStream().buffered(64 * 1024).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        val pct = computeProgressPercent(downloaded, totalBytes)
                        val shouldEmit = when {
                            pct != null && pct != lastReportedPct -> true
                            pct == null && (downloaded - lastReportedBytes) >= 128 * 1024L -> true
                            else -> false
                        }
                        if (shouldEmit) {
                            lastReportedPct = pct ?: -1
                            lastReportedBytes = downloaded
                            onProgress(downloaded, totalBytes, pct)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded <= 0L || !isValidApkHeader(tempFile)) {
                tempFile.delete()
                throw IOException("File unduhan bukan paket APK yang valid. Coba unduh ulang.")
            }

            if (destinationFile.exists()) destinationFile.delete()
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded, 100)
            destinationFile
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl.trim()
        repeat(MAX_REDIRECTS + 1) { hop ->
            currentCoroutineContext().ensureActive()
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/octet-stream, */*")
                setRequestProperty("User-Agent", "DocPix-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = try {
                conn.responseCode
            } catch (e: Throwable) {
                conn.disconnect()
                throw e
            }
            if (code in REDIRECT_CODES) {
                val location = conn.getHeaderField("Location")?.trim().orEmpty()
                conn.disconnect()
                if (location.isEmpty()) {
                    throw IOException("Server redirect kosong (HTTP $code).")
                }
                if (hop == MAX_REDIRECTS) {
                    throw IOException("Terlalu banyak redirect saat mengunduh APK.")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IOException("Gagal mengunduh pembaruan (HTTP $code).")
            }
        }
        throw IOException("Gagal menghubungi server unduhan.")
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
        const val UPDATE_APK_FILENAME = "DocPix-update.apk"
        private const val MAX_REDIRECTS = 8
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        val ENDPOINTS = listOf(
            "https://api.github.com/repos/rtbrnjn/DocPix/releases/latest",
            "https://api.github.com/repos/rtbrnjn/NexaKit/releases/latest"
        )

        /** Resolves the internal FileProvider-backed target APK location (`cacheDir/updates/DocPix-update.apk`). */
        fun resolveApkDownloadFile(context: Context): File {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            return File(dir, UPDATE_APK_FILENAME)
        }

        /** Computes integer percentage `0..100`, or `null` when server omits `Content-Length`. */
        fun computeProgressPercent(downloadedBytes: Long, totalBytes: Long): Int? {
            if (totalBytes <= 0L) return null
            return ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        }

        /** Checks ZIP/APK magic bytes (`PK\x03\x04`) so broken HTML error pages are rejected. */
        fun isValidApkHeader(file: File): Boolean {
            if (!file.exists() || file.length() < 4L) return false
            val header = ByteArray(4)
            return runCatching {
                file.inputStream().use { it.read(header) == 4 } &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }.getOrDefault(false)
        }

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
