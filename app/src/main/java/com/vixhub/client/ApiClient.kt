package com.vixhub.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Клиент Go Host API (см. API.md).
 * Base URL — из Firebase; публичные GET: /health, /web/, /image/, /download/.
 */
object ApiClient {
    const val TOKEN = "xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a"
    private const val FIREBASE_URL = "https://vix-hub-3caca-default-rtdb.firebaseio.com/base_url.json"
    const val PUBLIC_BASE = "https://vix.mstrblock.workers.dev"
    /** Секрет обновления main.go по умолчанию (сервер может переопределить UPDATE_SECRET). */
    const val DEFAULT_UPDATE_SECRET = "upd_vix_7HqL2mXp9RzT4wK8yN5cB1vJ6fD0sA3gE"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "VixHub-Android/1.7 OkHttp")
                        .build()
                )
            }
            .build()
    }

    @Volatile
    private var baseUrl: String = ""

    suspend fun fetchBaseUrl(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(FIREBASE_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Firebase error: ${response.code}"))
                }
                val raw = response.body?.string() ?: ""
                val cleaned = raw.replace("\"", "").trim().trimEnd('/')
                if (cleaned.isEmpty() || cleaned == "null") {
                    return@withContext Result.failure(Exception("Пустой URL от Firebase"))
                }
                baseUrl = cleaned
                Result.success(baseUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBaseUrl(): String = baseUrl

    private fun requireUrl(): String {
        if (baseUrl.isEmpty()) throw Exception("URL не загружен")
        return baseUrl
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun authBuilder(url: String, folderKey: String? = null): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $TOKEN")
        if (!folderKey.isNullOrBlank()) {
            b.addHeader("X-Folder-Key", folderKey)
        }
        return b
    }

    private fun parseError(code: Int, text: String): Exception {
        val msg = try {
            JSONObject(text).optString("message", text)
        } catch (_: Exception) {
            text.ifBlank { "HTTP $code" }
        }
        return Exception("HTTP $code: $msg")
    }

    private fun msgOr(text: String, fallback: String): String =
        try {
            JSONObject(text).optString("message", fallback).ifBlank { fallback }
        } catch (_: Exception) {
            text.ifBlank { fallback }
        }

    // ─── Health ───────────────────────────────────────────────────────

    suspend fun health(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${requireUrl()}/health").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(body)
                else Result.failure(parseError(response.code, body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Groups / storage ─────────────────────────────────────────────

    data class GroupInfo(val name: String, val protected: Boolean)
    data class FileInfo(val name: String, val size: Long? = null)

    suspend fun listGroups(): Result<List<GroupInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = authBuilder("${requireUrl()}/groups").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: "[]"
                if (!response.isSuccessful) return@use Result.failure(parseError(response.code, text))
                val list = mutableListOf<GroupInfo>()
                val trimmed = text.trim()
                when {
                    trimmed.startsWith("[") -> {
                        val arr = JSONArray(trimmed)
                        for (i in 0 until arr.length()) {
                            when (val item = arr.opt(i)) {
                                is JSONObject -> {
                                    val name = item.optString("name")
                                        .ifBlank { item.optString("id") }
                                    val prot = item.optBoolean("protected", false)
                                    if (name.isNotBlank()) list.add(GroupInfo(name, prot))
                                }
                                is String -> list.add(GroupInfo(item, false))
                            }
                        }
                    }
                    trimmed.startsWith("{") -> {
                        val obj = JSONObject(trimmed)
                        val arr = obj.optJSONArray("groups")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                val name = item.optString("name")
                                if (name.isNotBlank()) {
                                    list.add(GroupInfo(name, item.optBoolean("protected", false)))
                                }
                            }
                        }
                    }
                }
                list.sortBy { it.name.lowercase() }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroup(name: String, password: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject().put("name", name)
                if (!password.isNullOrBlank()) bodyJson.put("password", password)
                val body = bodyJson.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = authBuilder("${requireUrl()}/new_group").post(body).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) Result.success(msgOr(text, "Папка создана"))
                    else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteGroup(name: String, folderKey: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = authBuilder("${requireUrl()}/group/${enc(name)}", folderKey).delete().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) Result.success(msgOr(text, "Папка удалена"))
                    else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun listFiles(folder: String, folderKey: String?): Result<List<FileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val base = requireUrl()
                val e = enc(folder)
                // API.md: GET /list/{folder}; fallback — HTML /download/{folder}/
                val attempts = listOf("$base/list/$e", "$base/download/$e/")
                var lastError: Exception? = null
                for (url in attempts) {
                    try {
                        val request = authBuilder(url, folderKey).get().build()
                        client.newCall(request).execute().use { response ->
                            val text = response.body?.string() ?: ""
                            if (response.code == 429) {
                                return@withContext Result.failure(parseError(429, text))
                            }
                            if (!response.isSuccessful) {
                                lastError = parseError(response.code, text)
                                return@use
                            }
                            return@withContext Result.success(
                                parseFileList(text).sortedBy { it.name.lowercase() }
                            )
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                Result.failure(lastError ?: Exception("Не удалось получить список файлов"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseFileList(text: String): List<FileInfo> {
        val list = mutableListOf<FileInfo>()
        val trimmed = text.trim()
        try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        when (val item = arr.opt(i)) {
                            is JSONObject -> {
                                val n = item.optString("name")
                                    .ifBlank { item.optString("filename") }
                                val sz = if (item.has("size")) item.optLong("size") else null
                                if (n.isNotBlank() && n != ".meta.json") list.add(FileInfo(n, sz))
                            }
                            is String -> if (item.isNotBlank() && item != ".meta.json") list.add(FileInfo(item))
                        }
                    }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val arr = obj.optJSONArray("files")
                        ?: obj.optJSONArray("data")
                        ?: obj.optJSONArray("list")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            when (val item = arr.opt(i)) {
                                is JSONObject -> {
                                    val n = item.optString("name")
                                        .ifBlank { item.optString("filename") }
                                    val sz = if (item.has("size")) item.optLong("size") else null
                                    if (n.isNotBlank() && n != ".meta.json") list.add(FileInfo(n, sz))
                                }
                                is String -> list.add(FileInfo(item))
                            }
                        }
                    }
                }
                else -> {
                    val re = Regex("""(?i)<a\s+[^>]*href=["']([^"']+)["'][^>]*>""")
                    for (match in re.findAll(trimmed)) {
                        var href = match.groupValues[1].trim()
                        if (href.startsWith("?") || href.startsWith("#")) continue
                        href = href.substringBefore("?").trimEnd('/')
                        if (href.isBlank() || href == "." || href == "..") continue
                        val name = try {
                            URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
                        } catch (_: Exception) {
                            href.substringAfterLast('/')
                        }
                        if (name.isBlank() || name.equals("Parent Directory", true) || name == ".meta.json") continue
                        if (list.none { it.name == name }) list.add(FileInfo(name))
                    }
                }
            }
        } catch (_: Exception) { /* empty */ }
        return list
    }

    suspend fun uploadFile(
        folder: String,
        filename: String,
        bytes: ByteArray,
        folderKey: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${requireUrl()}/upload/${enc(folder)}?filename=${enc(filename)}"
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val request = authBuilder(url, folderKey).post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(msgOr(text, "Загружено"))
                else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadUrl(folder: String, filename: String, folderKey: String?): String {
        if (baseUrl.isEmpty()) return ""
        var url = "$baseUrl/download/${enc(folder)}/${enc(filename)}"
        if (!folderKey.isNullOrBlank()) {
            url += "?key=${enc(folderKey)}"
        }
        return url
    }

    fun publicDownloadUrl(folder: String, filename: String, folderKey: String?): String {
        var url = "$PUBLIC_BASE/download/${enc(folder)}/${enc(filename)}"
        if (!folderKey.isNullOrBlank()) {
            url += "?key=${enc(folderKey)}"
        }
        return url
    }

    suspend fun deleteFile(folder: String, filename: String, folderKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${requireUrl()}/download/${enc(folder)}/${enc(filename)}"
                val request = authBuilder(url, folderKey).delete().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) Result.success(msgOr(text, "Файл удалён"))
                    else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteFiles(
        folder: String,
        names: List<String>,
        folderKey: String?
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        var ok = 0
        var fail = 0
        for (n in names) {
            if (deleteFile(folder, n, folderKey).isSuccess) ok++ else fail++
        }
        Result.success(ok to fail)
    }

    suspend fun downloadBytes(
        folder: String,
        filename: String,
        folderKey: String?
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = downloadUrl(folder, filename, folderKey)
            val request = authBuilder(url, folderKey).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string() ?: ""
                    return@use Result.failure(parseError(response.code, text))
                }
                Result.success(response.body?.bytes() ?: ByteArray(0))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadText(
        folder: String,
        filename: String,
        folderKey: String?
    ): Result<String> = downloadBytes(folder, filename, folderKey).mapCatching {
        it.toString(Charsets.UTF_8)
    }

    // ─── Photos ───────────────────────────────────────────────────────

    suspend fun uploadPhoto(bytes: ByteArray): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val request = authBuilder("${requireUrl()}/upload").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: "{}"
                if (!response.isSuccessful) return@use Result.failure(parseError(response.code, text))
                Result.success(JSONObject(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Database (/base and /db) ─────────────────────────────────────

    suspend fun dbGet(name: String, path: String = ""): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val p = if (path.isBlank()) name else "$name/${path.trim('/')}".trim('/')
                // API.md предпочитает /base/{name}; /db/ — алиас
                val urls = listOf("${requireUrl()}/base/$p", "${requireUrl()}/db/$p")
                var last: Exception? = null
                for (url in urls) {
                    val request = authBuilder(url).get().build()
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string() ?: ""
                        if (response.isSuccessful) return@withContext Result.success(text)
                        last = parseError(response.code, text)
                    }
                }
                Result.failure(last ?: Exception("DB read failed"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun dbWrite(
        name: String,
        path: String,
        value: String,
        merge: Boolean = false,
        force: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val p = if (path.isBlank()) name else "$name/${path.trim('/')}".trim('/')
            var url = "${requireUrl()}/base/$p"
            // merge/force query — для совместимости, если сервер поддержит
            val q = mutableListOf<String>()
            if (merge) q.add("merge=1")
            if (force) q.add("force=1")
            if (q.isNotEmpty()) url += "?" + q.joinToString("&")
            val body = value.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = authBuilder(url).post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(msgOr(text, "Записано"))
                else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dbDelete(name: String, path: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val p = if (path.isBlank()) name else "$name/${path.trim('/')}".trim('/')
                val request = authBuilder("${requireUrl()}/base/$p").delete().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) Result.success(msgOr(text, "Удалено"))
                    else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ─── Scripts / bots ───────────────────────────────────────────────

    suspend fun loadScript(id: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = bytes.toRequestBody("application/octet-stream".toMediaType())
                val request = authBuilder("${requireUrl()}/loadfile/${enc(id)}").post(body).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) Result.success(msgOr(text, "Запущен"))
                    else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** API.md: POST /stopfile/{id} */
    suspend fun stopScript(id: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val empty = ByteArray(0).toRequestBody(null)
            val request = authBuilder("${requireUrl()}/stopfile/${enc(id)}").post(empty).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(msgOr(text, "Остановлен"))
                else {
                    // fallback GET for older builds
                    val r2 = authBuilder("${requireUrl()}/stopfile/${enc(id)}").get().build()
                    client.newCall(r2).execute().use { response2 ->
                        val t2 = response2.body?.string() ?: ""
                        if (response2.isSuccessful) Result.success(msgOr(t2, "Остановлен"))
                        else Result.failure(parseError(response.code, text))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scriptLogs(id: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = authBuilder("${requireUrl()}/logs/${enc(id)}").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (!response.isSuccessful) return@use Result.failure(parseError(response.code, text))
                try {
                    val obj = JSONObject(text)
                    Result.success(obj.optString("logs", text))
                } catch (_: Exception) {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Server update ────────────────────────────────────────────────

    suspend fun updateServer(
        secret: String,
        bytes: ByteArray,
        filename: String = "main.go"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (secret.isBlank()) return@withContext Result.failure(Exception("Укажите secret"))
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("Пустой файл"))
            val fname = filename.ifBlank { "main.go" }
            val url = "${requireUrl()}/update_server?secret=${enc(secret)}&filename=${enc(fname)}"
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val request = authBuilder(url)
                .addHeader("X-Filename", fname)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(msgOr(text, "Обновлено"))
                else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Websites ─────────────────────────────────────────────────────

    suspend fun uploadWebsite(
        projectId: String,
        files: List<Pair<String, ByteArray>>,
        update: Boolean = false
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val path = if (update) "up_web" else "new_web"
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            for ((name, bytes) in files) {
                mb.addFormDataPart(
                    "file",
                    name,
                    bytes.toRequestBody("application/octet-stream".toMediaType())
                )
            }
            val request = authBuilder("${requireUrl()}/$path/${enc(projectId)}")
                .post(mb.build())
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: "{}"
                if (!response.isSuccessful) return@use Result.failure(parseError(response.code, text))
                Result.success(JSONObject(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun siteUrl(projectId: String): String {
        val id = projectId.trim().trim('/')
        return "$PUBLIC_BASE/web/$id/"
    }
}
