package com.vixhub.app

enum class FileKind {
    IMAGE, VIDEO, AUDIO, TEXT, CODE, ARCHIVE, OTHER
}

object FileType {
    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg",
        "jfif", "avif", "webpeg" // user typo support
    )
    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "mpeg", "mpg"
    )
    private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")
    private val TEXT_EXT = setOf(
        "txt", "md", "json", "xml", "csv", "log", "ini", "conf", "cfg", "yml", "yaml",
        "html", "htm", "css", "js", "ts", "kt", "java", "py", "sh", "bat", "c", "cpp",
        "h", "hpp", "rs", "go", "sql", "toml", "properties", "gradle", "gitignore"
    )
    private val CODE_EXT = setOf("py", "kt", "java", "js", "ts", "c", "cpp", "h", "rs", "go", "sh")
    private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

    fun ext(name: String): String =
        name.substringAfterLast('.', "").lowercase().trim()

    fun kind(name: String): FileKind {
        val e = ext(name)
        return when {
            e in IMAGE_EXT -> FileKind.IMAGE
            e in VIDEO_EXT -> FileKind.VIDEO
            e in AUDIO_EXT -> FileKind.AUDIO
            e in TEXT_EXT || e in CODE_EXT -> FileKind.TEXT
            e in ARCHIVE_EXT -> FileKind.ARCHIVE
            // .bin часто картинки с сервера
            e == "bin" -> FileKind.IMAGE
            else -> FileKind.OTHER
        }
    }

    fun isMedia(name: String): Boolean {
        val k = kind(name)
        return k == FileKind.IMAGE || k == FileKind.VIDEO
    }
}
