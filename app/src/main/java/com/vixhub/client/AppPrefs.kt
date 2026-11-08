package com.vixhub.client

import android.content.Context
import org.json.JSONArray

class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("vixhub_prefs", Context.MODE_PRIVATE)

    fun getViewMode(folder: String): String =
        sp.getString("view_$folder", "list") ?: "list"

    fun setViewMode(folder: String, mode: String) {
        sp.edit().putString("view_$folder", mode).apply()
    }

    fun getFolderKey(folder: String): String? =
        sp.getString("key_$folder", null)

    fun setFolderKey(folder: String, key: String) {
        sp.edit().putString("key_$folder", key).apply()
    }

    fun forgetFolderKey(folder: String) {
        sp.edit().remove("key_$folder").apply()
    }

    fun pendingApkPath(): String? = sp.getString("pending_apk", null)
    fun setPendingApkPath(path: String?) {
        sp.edit().apply {
            if (path == null) remove("pending_apk") else putString("pending_apk", path)
        }.apply()
    }

    fun pendingApkVersion(): Int = sp.getInt("pending_apk_ver", 0)
    fun setPendingApkVersion(v: Int) {
        sp.edit().putInt("pending_apk_ver", v).apply()
    }

    fun getServerSecret(): String = sp.getString("server_secret", "") ?: ""
    fun setServerSecret(s: String) {
        sp.edit().putString("server_secret", s).apply()
    }

    private fun getStringList(key: String): List<String> {
        val raw = sp.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun setStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.distinct().take(50).forEach { arr.put(it) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    fun getSites(): List<String> = getStringList("sites")
    fun addSite(id: String) {
        if (id.isBlank()) return
        setStringList("sites", listOf(id) + getSites())
    }
    fun removeSite(id: String) = setStringList("sites", getSites().filter { it != id })

    fun getRecentDbs(): List<String> = getStringList("recent_dbs")
    fun addRecentDb(name: String) {
        if (name.isBlank()) return
        setStringList("recent_dbs", listOf(name) + getRecentDbs())
    }

    fun getRecentScripts(): List<String> = getStringList("recent_scripts")
    fun addRecentScript(id: String) {
        if (id.isBlank()) return
        setStringList("recent_scripts", listOf(id) + getRecentScripts())
    }
}
