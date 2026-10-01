package com.sammy.fbili.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.SearchBangumi
import com.sammy.fbili.net.SearchUser
import com.sammy.fbili.net.SearchVideo
import com.sammy.fbili.net.asT
import com.sammy.fbili.net.listT
import kotlinx.serialization.json.JsonObject
import com.sammy.fbili.net.Owner
import com.sammy.fbili.net.Stat
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.stripHtml
import com.sammy.fbili.ui.common.fmtDur
import com.sammy.fbili.ui.common.VideoCard
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    keyword: String,
    onVideo: (String, Long) -> Unit,
    onSeason: (Long) -> Unit,
    onUser: (Long) -> Unit,
) {
    var input by remember { mutableStateOf(keyword) }
    var query by remember { mutableStateOf(keyword) }
    var tab by remember { mutableIntStateOf(0) }
    var order by remember { mutableStateOf("") }
    var sugg by remember { mutableStateOf<List<String>>(emptyList()) }
    var hot by remember { mutableStateOf<List<String>>(emptyList()) }
    var defaultWord by remember { mutableStateOf("") }

    var videos by remember { mutableStateOf<List<SearchVideo>>(emptyList()) }
    var bangs by remember { mutableStateOf<List<SearchBangumi>>(emptyList()) }
    var users by remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        hot = runCatching { Api.hotSearch() }.getOrDefault(emptyList())
        defaultWord = runCatching { Api.searchDefault() }.getOrDefault("")
    }
    LaunchedEffect(input) {
        if (input.isBlank()) { sugg = emptyList(); return@LaunchedEffect }
        sugg = runCatching { Api.suggest(input) }.getOrDefault(emptyList())
    }

    val sco = rememberCoroutineScope()

    fun type() = when (tab) { 0 -> "video"; 1 -> "media_bangumi"; else -> "bili_user" }
    suspend fun load(p: Int) {
        if (query.isBlank()) return
        loading = true; error = null
        try {
            val d = Api.searchResult(type(), query, p, order)
            var size = 0
            when (tab) {
                0 -> { val r: List<SearchVideo> = d.listT("result"); videos = if (p == 1) r else videos + r; size = r.size }
                1 -> { val r: List<SearchBangumi> = d.listT("result"); bangs = if (p == 1) r else bangs + r; size = r.size }
                else -> { val r: List<SearchUser> = d.listT("result"); users = if (p == 1) r else users + r; size = r.size }
            }
            page = p
            hasMore = size > 0 && p < 5
        } catch (e: Exception) {
            error = e.message
        } finally { loading = false }
    }
    LaunchedEffect(query, tab, order) {
        if (query.isNotBlank()) load(1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text(defaultWord.ifEmpty { "搜索" }, fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { query = input.ifBlank { defaultWord } }),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Search, "搜索", tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { query = input.ifBlank { defaultWord } })
        }

        if (query.isBlank()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                if (sugg.isNotEmpty()) {
                    sugg.forEach { s ->
                        Text(s, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth().clickable { input = s; query = s }.padding(vertical = 10.dp))
                    }
                } else {
                    Text("热门搜索", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        hot.take(16).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { h ->
                                    AssistChip(onClick = { query = h }, label = { Text(h, fontSize = 12.sp) })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            return@Column
        }

        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary) {
            listOf("视频", "番剧", "UP主").forEachIndexed { i, t ->
                Tab(selected = tab == i, text = { Text(t) }, onClick = { tab = i })
            }
        }
        if (tab == 0) Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to "综合", "play" to "播放量", "pubdate" to "最新").forEach { (k, v) ->
                AssistChip(onClick = { order = k }, label = { Text(v, fontSize = 12.sp) },
                    leadingIcon = if (order == k) { { Text("✓", fontSize = 12.sp) } } else null)
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            if (error != null && page == 0) item {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp).clickable { /*重试*/ })
            }
            when (tab) {
                0 -> items(videos) { v ->
                    VideoCard(
                        VideoItem(
                            bvid = v.bvid, aid = v.aid, title = stripHtml(v.title),
                            pic = v.pic.normUrl(), duration = v.durSec(),
                            owner = Owner(name = stripHtml(v.author)),
                            stat = Stat(view = v.play.toLongOrNull() ?: 0),
                        ),
                        onClick = { if (v.bvid.isNotEmpty()) onVideo(v.bvid, v.aid) else onVideo("", v.aid) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                1 -> items(bangs) { b ->
                    ListRow(
                        title = stripHtml(b.title),
                        cover = b.cover.normUrl(),
                        sub = b.indexInfo.ifEmpty { b.areas.joinToString("/") },
                        extra = listOf("评分:${b.score()}", stripHtml(b.url)).firstOrNull { it.length > 4 } ?: "",
                        onClick = {
                            val sid = if (b.seasonId > 0) b.seasonId
                            else Regex("ss(\\d+)").find(b.url)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                            if (sid > 0) onSeason(sid)
                        },
                    )
                }
                else -> items(users) { u ->
                    ListRow(
                        title = stripHtml(u.uname), cover = u.face.normUrl(),
                        sub = stripHtml(u.sign).take(40), extra = "粉丝 ${u.fans} · 稿件 ${u.videoCount}",
                        onClick = { if (u.mid > 0) onUser(u.mid) },
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center) {
                    when {
                        loading -> Text("搜索中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        hasMore && page in 1..4 ->
                            Text("加载更多", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { sco.launch { load(page + 1) } })
                        page > 0 && !hasMore -> Text("没有更多了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
