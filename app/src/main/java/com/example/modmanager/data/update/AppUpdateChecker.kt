package com.darkestdungeon.modmanager.data.update

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AppUpdate(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?
)

class AppUpdateChecker(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun check(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DD-Mod-Manager")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val payload = response.body.string()
                if (payload.isBlank()) return@use null

                val release = JSONObject(payload)
                val tag = release.optString("tag_name").trim()
                val releaseUrl = release.optString("html_url").trim()
                if (tag.isBlank() || releaseUrl.isBlank()) return@use null

                val version = tag.removePrefix("v").removePrefix("V")
                if (compareVersions(version, currentVersion) <= 0) return@use null

                AppUpdate(
                    version = version,
                    releaseUrl = releaseUrl,
                    apkUrl = findApkUrl(release.optJSONArray("assets"))
                )
            }
        }.getOrNull()
    }

    suspend fun downloadApk(context: Context, update: AppUpdate): android.net.Uri? =
        withContext(Dispatchers.IO) {
            val apkUrl = update.apkUrl ?: return@withContext null
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "DD-Mod-Manager")
                .build()

            runCatching {
                val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
                val temporaryFile = File(updateDirectory, "dd-mod-manager-update.apk.download")
                val apkFile = File(updateDirectory, "dd-mod-manager-update.apk")

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.byteStream().use { input ->
                        temporaryFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } ?: return@runCatching null

                check(temporaryFile.length() > 0L) { "The downloaded APK was empty." }
                if (apkFile.exists()) apkFile.delete()
                check(temporaryFile.renameTo(apkFile)) { "The downloaded APK could not be saved." }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            }.getOrNull()
        }

    private fun findApkUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return url
            }
        }
        return null
    }

    private fun compareVersions(first: String, second: String): Int {
        val firstParts = versionParts(first)
        val secondParts = versionParts(second)
        val size = maxOf(firstParts.size, secondParts.size)

        repeat(size) { index ->
            val firstPart = firstParts.getOrElse(index) { 0 }
            val secondPart = secondParts.getOrElse(index) { 0 }
            if (firstPart != secondPart) return firstPart.compareTo(secondPart)
        }
        return 0
    }

    private fun versionParts(version: String): List<Int> =
        version
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { part -> part.toIntOrNull() ?: 0 }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Ryan156/DD-Android-Mod-Manager/releases/latest"
    }
}
