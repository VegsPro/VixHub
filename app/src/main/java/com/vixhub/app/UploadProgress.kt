package com.vixhub.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UploadProgressState(
    val active: Boolean = false,
    val folder: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val currentName: String = "",
    val ok: Int = 0,
    val fail: Int = 0,
    val done: Boolean = false,
    val message: String = ""
)

object UploadProgress {
    private val _state = MutableStateFlow(UploadProgressState())
    val state: StateFlow<UploadProgressState> = _state.asStateFlow()

    fun start(folder: String, total: Int) {
        _state.value = UploadProgressState(active = true, folder = folder, total = total, message = "Старт…")
    }

    fun update(current: Int, total: Int, name: String, ok: Int, fail: Int) {
        _state.value = _state.value.copy(
            active = true,
            current = current,
            total = total,
            currentName = name,
            ok = ok,
            fail = fail,
            message = "$current/$total · $name"
        )
    }

    fun finish(ok: Int, fail: Int, total: Int) {
        _state.value = UploadProgressState(
            active = false,
            done = true,
            ok = ok,
            fail = fail,
            total = total,
            current = total,
            message = if (fail == 0) "Загружено $ok/$total" else "Готово: $ok ок, $fail ошибок"
        )
    }

    fun clear() {
        _state.value = UploadProgressState()
    }
}
