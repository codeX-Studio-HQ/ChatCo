package com.badew.chatco

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Seçilen görseli önbellek dosyasına kopyalar; Storage [putFile] ile güvenilir yükleme için.
 * Yükleme bitince bu dosyayı silebilirsiniz.
 */
object ImageUploadHelper {

    fun copyUriToCacheFile(context: Context, uri: Uri): Pair<File, String>? {
        return try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png", ignoreCase = true) -> "png"
                mime.contains("webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val out = File(context.cacheDir, "chatco_upload_${UUID.randomUUID()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return null
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                return null
            }
            Pair(out, mime)
        } catch (_: Exception) {
            null
        }
    }
}
