package com.darkestdungeon.modmanager.data.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

sealed class ModImportState {
    object Idle : ModImportState()
    data class Extracting(val progress: Float, val currentFileName: String = "") : ModImportState()
    data class Success(val modFolderName: String, val message: String = "Mod imported successfully") : ModImportState()
    data class Error(val message: String) : ModImportState()
}

class ModImporter {

    /**
     * Checks if extracting target relative path under targetDir escapes targetDir (Zip Slip Defense).
     */
    fun isSafePath(targetDir: File, relativePath: String): Boolean {
        val destFile = File(targetDir, relativePath)
        val canonicalDest = destFile.canonicalPath
        val canonicalTarget = targetDir.canonicalPath
        return canonicalDest.startsWith(canonicalTarget + File.separator) || canonicalDest == canonicalTarget
    }

    /**
     * Extracts zip from InputStream into targetModDir.
     * Detects if zip entries already have a single top-level root directory,
     * or if loose files should be auto-wrapped inside a folder named `fallbackFolderName`.
     */
    suspend fun extractZip(
        inputStream: InputStream,
        targetModDir: File,
        fallbackFolderName: String = "imported_mod",
        onProgress: ((Float, String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        if (!targetModDir.exists()) {
            targetModDir.mkdirs()
        }

        val tempZipFile = File.createTempFile("import_mod_", ".zip", targetModDir)
        try {
            FileOutputStream(tempZipFile).use { out ->
                inputStream.copyTo(out)
            }

            extractZipFile(tempZipFile, targetModDir, fallbackFolderName, onProgress)
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }

    suspend fun extractZipFile(
        zipFile: File,
        targetModDir: File,
        fallbackFolderName: String = "imported_mod",
        onProgress: ((Float, String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val sanitizedFallback = sanitizeFolderName(fallbackFolderName)

        // 1. Inspect entries to determine if zip has a single root mod folder
        val (hasRootDir, rootDirName, totalEntries) = inspectZipEntries(zipFile)

        val destinationDir = if (hasRootDir && rootDirName != null) {
            targetModDir
        } else {
            File(targetModDir, sanitizedFallback).apply { if (!exists()) mkdirs() }
        }

        // 2. Perform safe extraction with progress reporting
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            var processedEntries = 0

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name.replace('\\', '/')

                if (entryName.isBlank()) continue

                // Zip Slip defense
                if (!isSafePath(destinationDir, entryName)) {
                    throw SecurityException("Zip Slip attack detected! Entry path '$entryName' escapes target directory.")
                }

                val destFile = File(destinationDir, entryName)

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { inStream ->
                        BufferedOutputStream(FileOutputStream(destFile)).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }

                processedEntries++
                val progress = if (totalEntries > 0) processedEntries.toFloat() / totalEntries else 1.0f
                onProgress?.invoke(progress, entry.name)
            }
        }

        // Return the root folder of extracted mod
        if (hasRootDir && rootDirName != null) {
            File(targetModDir, rootDirName)
        } else {
            destinationDir
        }
    }

    /**
     * Inspects zip entries to check for single root directory.
     */
    fun inspectZipEntries(zipFile: File): Triple<Boolean, String?, Int> {
        ZipFile(zipFile).use { zip ->
            var count = 0
            val topLevelNames = mutableSetOf<String>()

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                val normalizedName = entry.name.replace('\\', '/').trim('/')
                if (normalizedName.isEmpty()) continue

                val parts = normalizedName.split('/')
                topLevelNames.add(parts[0])
            }

            return if (topLevelNames.size == 1 && topLevelNames.first().isNotEmpty()) {
                Triple(true, topLevelNames.first(), count)
            } else {
                Triple(false, null, count)
            }
        }
    }

    fun sanitizeFolderName(name: String): String {
        val cleanName = name.removeSuffix(".zip").removeSuffix(".ZIP")
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .trim('_')
        return if (cleanName.isBlank()) "imported_mod" else cleanName
    }

    suspend fun importModFromUri(
        context: Context,
        uri: Uri,
        targetModDir: File,
        archiveName: String? = null,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val fallbackName = archiveName ?: getFileNameFromUri(context, uri) ?: "imported_mod"
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            extractZip(
                inputStream = inputStream,
                targetModDir = targetModDir,
                fallbackFolderName = fallbackName,
                onProgress = { progress, _ -> onProgress?.invoke(progress) }
            )
        } ?: throw IllegalArgumentException("Could not open input stream from URI: $uri")
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) return cursor.getString(nameIdx)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
