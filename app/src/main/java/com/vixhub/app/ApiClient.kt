package com.vixhub.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TOKEN = "xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a"
    private const val FIREBASE_URL = "https://vix-hub-3caca-default-rtdb.firebaseio.com/base_url.json"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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

    private fun authRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $TOKEN")
    }

    suspend fun health(): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(body)
                else Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Photos ---
    suspend fun uploadPhoto(bytes: ByteArray, filename: String = "photo.jpg"): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        filename,
                        bytes.toRequestBody("application/octet-stream".toMediaType())
                    )
                    .build()
                val request = authRequest("$baseUrl/upload")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Result.success(JSONObject(text))
                    } else {
                        Result.failure(Exception("HTTP ${response.code}: $text"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun listImages(): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val request = authRequest("$baseUrl/images").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("HTTP ${response.code}: $text"))
                }
                val list = mutableListOf<Pair<String, String>>()
                val root = JSONObject(text)

                // Формат сервера: {"count":N,"images":{key:filename,...},"status":"ok"}
                val imagesObj: JSONObject = when {
                    root.has("images") && root.opt("images") is JSONObject ->
                        root.getJSONObject("images")
                    else -> root
                }

                val keys = imagesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val filename = imagesObj.optString(key, key)
                    // /image/<id> принимает filename (значение)
                    val id = filename.ifBlank { key }
                    if (id.isNotBlank()) list.add(id to filename)
                }
                list.sortBy { it.second }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun imageUrl(id: String): String {
        return if (baseUrl.isEmpty()) "" else "$baseUrl/image/$id"
    }

    suspend fun deleteImage(id: String): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        if (id.isBlank()) return@withContext Result.failure(Exception("Пустой id"))
        try {
            // Пробуем несколько возможных эндпоинтов
            val candidates = listOf(
                "$baseUrl/image/$id",
                "$baseUrl/images/$id",
                "$baseUrl/upload/$id"
            )
            var lastError = "Не удалось удалить"
            for (url in candidates) {
                val request = authRequest(url).delete().build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        return@withContext Result.success(text.ifBlank { "OK" })
                    }
                    // 405 = method not allowed, 404 = not found — пробуем следующий
                    if (response.code != 404 && response.code != 405) {
                        lastError = "HTTP ${response.code}: $text"
                    } else {
                        lastError = "HTTP ${response.code}: $text"
                    }
                }
            }
            Result.failure(Exception(lastError))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Database ---
    suspend fun dbGet(name: String, path: String = ""): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val p = if (path.isBlank()) name else "$name/$path".trim('/')
            val request = authRequest("$baseUrl/db/$p").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${response.code}: $text"))
            }
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
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val p = if (path.isBlank()) name else "$name/$path".trim('/')
            var url = "$baseUrl/db/$p"
            val params = mutableListOf<String>()
            if (merge) params.add("merge=1")
            if (force) params.add("force=1")
            if (params.isNotEmpty()) url += "?" + params.joinToString("&")

            val body = value.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = authRequest(url).post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${response.code}: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dbDelete(name: String, path: String): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val p = if (path.isBlank()) name else "$name/$path".trim('/')
            val request = authRequest("$baseUrl/db/$p").delete().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${response.code}: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Scripts ---
    suspend fun loadScript(id: String, bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val request = authRequest("$baseUrl/loadfile/$id").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${response.code}: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopScript(id: String): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val request = authRequest("$baseUrl/stopfile/$id").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${response.code}: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scriptLogs(id: String): Result<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext Result.failure(Exception("URL не загружен"))
        try {
            val request = authRequest("$baseUrl/logs/$id").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: ""
                if (response.isSuccessful) Result.success(text)
                else Result.failure(Exception("HTTP ${response.code}: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
