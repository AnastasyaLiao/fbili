package com.sammy.fbili.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sammy.fbili.net.Nav
import com.sammy.fbili.net.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.fbDataStore: DataStore<Preferences> by preferencesDataStore(name = "fbili")

object Account {
    val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var ds: DataStore<Preferences>
    private val KEY_COOKIES = stringPreferencesKey("cookies")

    private val _user = MutableStateFlow<Nav?>(null)
    val user: StateFlow<Nav?> = _user
    val isLogin: Boolean get() = _user.value?.isLogin == true

    fun csrf(): String = Net.cookies["bili_jct"] ?: ""
    fun uid(): Long = _user.value?.mid?.takeIf { it > 0 }
        ?: Net.cookies["DedeUserID"]?.toLongOrNull() ?: 0

    suspend fun init(ctx: Context) {
        ds = ctx.fbDataStore
        runCatching {
            val raw = ds.data.first()[KEY_COOKIES] ?: return
            raw.split(";").forEach { kv ->
                val parts = kv.split("=", limit = 2)
                if (parts.size == 2 && parts[1].isNotEmpty()) Net.cookies[parts[0]] = parts[1]
            }
        }
    }

    fun persistCookies(map: Map<String, String>) {
        if (!::ds.isInitialized) return
        val snapshot = map.entries.joinToString(";") { "${it.key}=${it.value}" }
        AppScope.launch {
            runCatching { ds.edit { it[KEY_COOKIES] = snapshot } }
        }
    }

    suspend fun refreshNav() {
        val nav = runCatching {
            Net.json.decodeFromJsonElement(Nav.serializer(), Net.api(Net.API, "/x/web-interface/nav"))
        }.getOrNull()
        _user.value = nav?.takeIf { it.isLogin }
    }

    suspend fun logout() {
        Net.cookies.clear()
        _user.value = null
        if (::ds.isInitialized) ds.edit { it[KEY_COOKIES] = "" }
    }
}
