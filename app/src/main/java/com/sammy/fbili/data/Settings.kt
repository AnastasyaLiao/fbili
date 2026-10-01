package com.sammy.fbili.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

enum class ThemeMode { LIGHT, DARK, SYSTEM }

object Settings {
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_QUALITY = intPreferencesKey("quality")
    private val KEY_DANMAKU = intPreferencesKey("danmaku")

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme
    private val _maxQuality = MutableStateFlow(80) // 80=1080P
    val maxQuality: StateFlow<Int> = _maxQuality
    private val _danmakuOn = MutableStateFlow(true)
    val danmakuOn: StateFlow<Boolean> = _danmakuOn

    suspend fun init(ctx: Context) {
        runCatching {
            val p = ctx.fbDataStore.data.first()
            _theme.value = runCatching { ThemeMode.valueOf(p[KEY_THEME] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM)
            _maxQuality.value = p[KEY_QUALITY] ?: 80
            _danmakuOn.value = (p[KEY_DANMAKU] ?: 1) == 1
        }
    }

    suspend fun setTheme(ctx: Context, mode: ThemeMode) {
        _theme.value = mode
        ctx.fbDataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setQuality(ctx: Context, qn: Int) {
        _maxQuality.value = qn
        ctx.fbDataStore.edit { it[KEY_QUALITY] = qn }
    }

    suspend fun setDanmaku(ctx: Context, on: Boolean) {
        _danmakuOn.value = on
        ctx.fbDataStore.edit { it[KEY_DANMAKU] = if (on) 1 else 0 }
    }
}
