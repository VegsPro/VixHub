package com.vixhub.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TOKEN = "xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a"
    private const val FIREBASE_URL = "https://vix-hub-3caca-default-rtdb.firebaseio.com/base_url.json"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
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
                val cleaned = raw.replace("\"", "").trim()
                if (cleaned.isEmpty() || cleaned == "null") {
                    return@withContext Result.failure(Exception("Пустой URL от Firebase"))
                }
                baseUrl = cleaned.trimEnd('/')
                Result.success(baseUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBaseUrl(): String = baseUrl

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

    private fun requireUrl(): String {
        if (baseUrl.isEmpty()) throw Exception("URL не загружен")
        return baseUrl
    }

    suspend fun health(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val request = Request.Builder().url("$base/health").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(body)
                else Result.failure(parseError(response.code, body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class GroupInfo(val name: String, val protected: Boolean)
    data class FileInfo(val name: String, val size: Long? = null)

    suspend fun listGroups(): Result<List<GroupInfo>> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val request = authBuilder("$base/groups").get().build()
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
                                        .ifBlank { item.optString("folder") }
                                    val prot = item.optBoolean("protected", item.optBoolean("password", false))
                                    if (name.isNotBlank()) list.add(GroupInfo(name, prot))
                                }
                                is String -> list.add(GroupInfo(item, false))
                            }
                        }
                    }
                    trimmed.startsWith("{") -> {
                        val obj = JSONObject(trimmed)
                        val arr = when {
                            obj.has("groups") -> obj.optJSONArray("groups")
                            obj.has("data") -> obj.optJSONArray("data")
                            obj.has("folders") -> obj.optJSONArray("folders")
                            else -> null
                        }
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                val name = item.optString("name").ifBlank { item.optString("id") }
                                val prot = item.optBoolean("protected", false)
                                if (name.isNotBlank()) list.add(GroupInfo(name, prot))
                            }
                        } else {
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                if (k in listOf("status", "message", "count")) continue
                                val v = obj.opt(k)
                                val prot = when (v) {
                                    is JSONObject -> v.optBoolean("protected", false)
                                    is Boolean -> v
                                    else -> false
                                }
                                list.add(GroupInfo(k, prot))
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

    suspend fun createGroup(name: String, password: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val bodyJson = JSONObject().put("name", name)
            if (!password.isNullOrBlank()) bodyJson.put("password", password)
            val body = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = authBuilder("$base/new_group").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val msg = try { JSONObject(text).optString("message", "Папка создана") } catch (_: Exception) { text.ifBlank { "Папка создана" } }
                    Result.success(msg)
                } else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val enc = URLEncoder.encode(name, "UTF-8")
            val request = authBuilder("$base/group/$enc").delete().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val msg = try { JSONObject(text).optString("message", "Папка удалена") } catch (_: Exception) { text.ifBlank { "Папка удалена" } }
                    Result.success(msg)
                } else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(folder: String, folderKey: String?): Result<List<FileInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val base = requireUrl()
                val enc = URLEncoder.encode(folder, "UTF-8")
                val request = authBuilder("$base/list/$enc", folderKey).get().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: "[]"
                    if (!response.isSuccessful) return@use Result.failure(parseError(response.code, text))
                    val list = mutableListOf<FileInfo>()
                    val trimmed = text.trim()
                    when {
                        trimmed.startsWith("[") -> {
                            val arr = JSONArray(trimmed)
                            for (i in 0 until arr.length()) {
                                when (val item = arr.opt(i)) {
                                    is JSONObject -> {
                                        val n = item.optString("name").ifBlank { item.optString("filename") }.ifBlank { item.optString("file") }
                                        val sz = if (item.has("size")) item.optLong("size") else null
                                        if (n.isNotBlank()) list.add(FileInfo(n, sz))
                                    }
                                    is String -> list.add(FileInfo(item))
                                }
                            }
                        }
                        trimmed.startsWith("{") -> {
                            val obj = JSONObject(trimmed)
                            val arr = obj.optJSONArray("files") ?: obj.optJSONArray("data") ?: obj.optJSONArray("list")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    when (val item = arr.opt(i)) {
                                        is JSONObject -> {
                                            val n = item.optString("name").ifBlank { item.optString("filename") }
                                            val sz = if (item.has("size")) item.optLong("size") else null
                                            if (n.isNotBlank()) list.add(FileInfo(n, sz))
                                        }
                                        is String -> list.add(FileInfo(item))
                                    }
                                }
                            } else {
                                val keys = obj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    if (k in listOf("status", "message", "count", "protected")) continue
                                    list.add(FileInfo(k))
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

    suspend fun uploadFile(folder: String, filename: String, bytes: ByteArray, folderKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val base = requireUrl()
                val encFolder = URLEncoder.encode(folder, "UTF-8")
                val encName = URLEncoder.encode(filename, "UTF-8")
                val url = "$base/upload/$encFolder?filename=$encName"
                val body = bytes.toRequestBody("application/octet-stream".toMediaType())
                val request = authBuilder(url, folderKey).post(body).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val msg = try { JSONObject(text).optString("message", "Загружено") } catch (_: Exception) { text.ifBlank { "Загружено" } }
                        Result.success(msg)
                    } else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun downloadUrl(folder: String, filename: String, folderKey: String?): String {
        if (baseUrl.isEmpty()) return ""
        val encFolder = URLEncoder.encode(folder, "UTF-8")
        val encName = URLEncoder.encode(filename, "UTF-8")
        var url = "$baseUrl/download/$encFolder/$encName"
        if (!folderKey.isNullOrBlank()) {
            url += "?key=${URLEncoder.encode(folderKey, "UTF-8")}"
        }
        return url
    }

    suspend fun deleteFile(folder: String, filename: String, folderKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val base = requireUrl()
                val encFolder = URLEncoder.encode(folder, "UTF-8")
                val encName = URLEncoder.encode(filename, "UTF-8")
                val request = authBuilder("$base/download/$encFolder/$encName", folderKey).delete().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val msg = try { JSONObject(text).optString("message", "Файл удалён") } catch (_: Exception) { text.ifBlank { "Файл удалён" } }
                        Result.success(msg)
                    } else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun dbGet(name: String, path: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val p = if (path.isBlank()) name else "$name/$path".trim('/')
            val request = authBuilder("$base/db/$p").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text) else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dbWrite(name: String, path: String, value: String, merge: Boolean = false, force: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val base = requireUrl()
                val p = if (path.isBlank()) name else "$name/$path".trim('/')
                var url = "$base/db/$p"
                val params = mutableListOf<String>()
                if (merge) params.add("merge=1")
                if (force) params.add("force=1")
                if (params.isNotEmpty()) url += "?" + params.joinToString("&")
                val body = value.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = authBuilder(url).post(body).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) Result.success(text) else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun dbDelete(name: String, path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val p = if (path.isBlank()) name else "$name/$path".trim('/')
            val request = authBuilder("$base/db/$p").delete().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text) else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadScript(id: String, bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val request = authBuilder("$base/loadfile/$id").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text) else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopScript(id: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val request = authBuilder("$base/stopfile/$id").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text) else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scriptLogs(id: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            val request = authBuilder("$base/logs/$id").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text) else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun downloadBytes(folder: String, filename: String, folderKey: String?): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val url = downloadUrl(folder, filename, folderKey)
                if (url.isBlank()) return@withContext Result.failure(Exception("URL пустой"))
                val request = authBuilder(url, folderKey).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val text = response.body?.string() ?: ""
                        return@use Result.failure(parseError(response.code, text))
                    }
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    Result.success(bytes)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadText(folder: String, filename: String, folderKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            downloadBytes(folder, filename, folderKey).mapCatching { bytes ->
                bytes.toString(Charsets.UTF_8)
            }
        }

    suspend fun updateServer(secret: String, bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = requireUrl()
            if (secret.isBlank()) return@withContext Result.failure(Exception("Укажите secret"))
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("Пустой файл"))
            val url = "$base/update_server?secret=${URLEncoder.encode(secret, "UTF-8")}"
            val body = bytes.toRequestBody("text/x-python; charset=utf-8".toMediaType())
            val request = authBuilder(url).post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val msg = try { JSONObject(text).optString("message", text) } catch (_: Exception) { text.ifBlank { "OK" } }
                    Result.success(msg)
                } else Result.failure(parseError(response.code, text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // ─── Websites ───────────────────────────────────────────────────

    suspend fun uploadWebsite(projectId: String, files: List<Pair<String, ByteArray>>, update: Boolean = false): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val base = requireUrl()
                val path = if (update) "up_web" else "new_web"
                val enc = URLEncoder.encode(projectId, "UTF-8")
                val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
                for ((name, bytes) in files) {
                    mb.addFormDataPart(
                        "file",
                        name,
                        bytes.toRequestBody("application/octet-stream".toMediaType())
                    )
                }
                val request = authBuilder("$base/$path/$enc").post(mb.build()).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: "{}"
                    if (response.isSuccessful) {
                        Result.success(try { JSONObject(text) } catch (_: Exception) { JSONObject().put("message", text) })
                    } else Result.failure(parseError(response.code, text))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun siteUrl(projectId: String): String {
        if (baseUrl.isEmpty()) return ""
        return "$baseUrl/web/$projectId/"
    }

    suspend fun deleteFiles(folder: String, names: List<String>, folderKey: String?): Result<Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            var ok = 0
            var fail = 0
            for (n in names) {
                val r = deleteFile(folder, n, folderKey)
                if (r.isSuccess) ok++ else fail++
            }
            Result.success(ok to fail)
        }
}
