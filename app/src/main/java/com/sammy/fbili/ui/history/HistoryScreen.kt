package com.sammy.fbili.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import kotlinx.serialization.json.JsonObject
import com.sammy.fbili.net.arr
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.num
import com.sammy.fbili.net.obj
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.ErrorBox
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.fmtDur
import com.sammy.fbili.ui.common.timeAgo
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onVideo: (String, Long) -> Unit, onBack: () -> Unit) {
    val me by Account.user.collectAsState()
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var max by remember { mutableLongStateOf(0L) }
    var viewAt by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        if (loading || (!reset && !hasMore)) return
        loading = true; error = null
        try {
            val d = Api.historyCursor(if (reset) 0 else max, if (reset) 0 else viewAt)
            val list = d.arr("list")
            if (list.isEmpty() && reset) {
                if (d["cursor"] == null) { hasMore = false; error = null }
            }
            rows = if (reset) list else rows + list
            val c = d.obj("cursor") ?: JsonObject(emptyMap())
            max = c.num("max"); viewAt = c.num("view_at")
            hasMore = list.isNotEmpty()
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally { loading = false }
    }

    LaunchedEffect(me?.mid, tick) {
        if (Account.isLogin) load(true)
    }

    if (!Account.isLogin) {
        ErrorBox("登录后查看观看历史") { onBack() }
        return
    }
    if (error != null && rows.isEmpty()) {
        ErrorBox(error) { scope.launch { load(true) } }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(rows) { it ->
            val h = it.obj("history") ?: JsonObject(emptyMap())
            var bvid = it.str("bvid").ifEmpty { h.str("bvid") }
            val aid = it.num("aid").takeIf { a -> a > 0 } ?: h.num("aid")
            if (bvid.isEmpty()) {
                bvid = Regex("BV[0-9A-Za-z]{10}").find(it.str("uri", "short_link_v2"))?.value ?: ""
            }
            val title = it.str("title", "show_title")
            ListRow(
                title = title,
                cover = it.str("cover", "pic").normUrl(),
                sub = it.str("device", "keyword"),
                extra = it.str("view_at", "name").let { _ ->
                    "观看于 ${timeAgo(it.num("view_at"))} · ${fmtDur(it.num("duration"))}"
                },
                progress = it.num("progress"),
                totalDur = it.num("duration"),
                onClick = { if (bvid.isNotEmpty() || aid > 0) onVideo(bvid, aid) },
            )
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> Text("加载中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hasMore -> TextButton(onClick = { scope.launch { load(false) } }) { Text("加载更多") }
                    rows.isNotEmpty() -> Text("没有更多了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
