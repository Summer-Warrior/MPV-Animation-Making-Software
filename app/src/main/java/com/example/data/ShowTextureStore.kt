package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Persists AI-generated custom show textures as PNG files strictly confined inside
 * `filesDir/show_textures/<showId>.png` with canonical path validation.
 */
class ShowTextureStore(private val baseFilesDir: File) {

    constructor(context: Context) : this(context.filesDir)

    val texturesDir: File
        get() = File(baseFilesDir, TEXTURES_SUBDIR)

    /**
     * Resolves a texture file strictly inside `filesDir/show_textures/` for a valid positive showId.
     * Rejects invalid IDs or any filename attempting directory traversal.
     */
    fun resolveSafeTextureFile(showId: Long): File {
        require(showId > 0L) { "showId must be a positive identifier." }
        return resolveSafeFileByName("$showId.png")
    }

    /**
     * Validates a candidate filename and guarantees its canonical path remains strictly
     * inside `filesDir/show_textures/`.
     */
    fun resolveSafeFileByName(fileName: String): File {
        require(fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\') && !fileName.contains("..")) {
            "Invalid texture filename."
        }
        require(SAFE_FILENAME_REGEX.matches(fileName)) {
            "Texture filename must match strict numeric PNG format."
        }
        val dir = texturesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val canonicalDir = dir.canonicalFile
        val candidate = File(canonicalDir, fileName).canonicalFile
        val expectedPrefix = canonicalDir.path + File.separator
        if (!candidate.path.startsWith(expectedPrefix)) {
            throw SecurityException("Path traversal blocked outside show_textures directory.")
        }
        return candidate
    }

    suspend fun saveTexture(showId: Long, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val target = resolveSafeTextureFile(showId)
        val tempFile = File(target.parentFile, "${showId}.tmp")
        FileOutputStream(tempFile).use { fos ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                throw IllegalStateException("Failed to compress AI custom texture bitmap to PNG.")
            }
            fos.flush()
        }
        if (target.exists()) {
            target.delete()
        }
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        target
    }

    suspend fun loadTexture(showId: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (showId <= 0L) return@withContext null
        val target = runCatching { resolveSafeTextureFile(showId) }.getOrNull() ?: return@withContext null
        if (!target.exists() || !target.isFile) return@withContext null
        BitmapFactory.decodeFile(target.absolutePath)
    }

    suspend fun deleteTexture(showId: Long): Boolean = withContext(Dispatchers.IO) {
        if (showId <= 0L) return@withContext false
        val target = runCatching { resolveSafeTextureFile(showId) }.getOrNull() ?: return@withContext false
        if (target.exists()) target.delete() else false
    }

    fun hasTexture(showId: Long): Boolean {
        if (showId <= 0L) return false
        val target = runCatching { resolveSafeTextureFile(showId) }.getOrNull() ?: return false
        return target.exists() && target.isFile
    }

    companion object {
        const val TEXTURES_SUBDIR = "show_textures"
        private val SAFE_FILENAME_REGEX = Regex("^[1-9][0-9]*\\.png$")
    }
}
