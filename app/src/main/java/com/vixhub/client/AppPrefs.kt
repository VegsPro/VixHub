package com.vixhub.client

import android.content.Context

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
}
