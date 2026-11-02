package com.vixhub.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

object FileUtils {
    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        } catch (_: Exception) { }

        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        if (name.isNullOrBlank()) {
            name = "file_${System.currentTimeMillis()}"
        }

        // content://.../image:43496 → убрать префикс до двоеточия только если нет точки (расширения)
        if (':' in name && '.' !in name.substringAfterLast('/')) {
            val after = name.substringAfterLast(':')
            if (after.isNotBlank()) name = after
        }

        // расширение из MIME если отсутствует
        if (!name.contains('.')) {
            val mime = context.contentResolver.getType(uri)
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            name = name + "." + (ext ?: "bin")
        }

        name = name.replace(Regex("[^a-zA-Z0-9._\\-\\u0400-\\u04FF]"), "_")
        name = name.trim('_', '.')
        if (name.isBlank() || name.startsWith('.')) {
            name = "file_${System.currentTimeMillis()}.bin"
        }
        if (name.length > 180) {
            val ext = name.substringAfterLast('.', "bin")
            name = name.take(160) + "." + ext.take(10)
        }
        return name
    }

    fun readBytes(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Не удалось открыть файл")
    }
}
