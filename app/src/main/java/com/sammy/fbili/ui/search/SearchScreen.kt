package com.sammy.fbili.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.data.Settings
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.SearchBangumi
import com.sammy.fbili.net.SearchUser
import com.sammy.fbili.net.SearchVideo
import com.sammy.fbili.net.listT
import com.sammy.fbili.net.Owner
import com.sammy.fbili.net.Stat
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.stripHtml
import com.sammy.fbili.ui.common.VideoCard
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    keyword: String,
    onVideo: (String, Long) -> Unit,
    onSeason: (Long) -> Unit,
    onUser: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var input by remember { mutableStateOf(keyword) }
    var query by remember { mutableStateOf(keyword) }
    var tab by remember { mutableIntStateOf(0) }
    var order by remember { mutableStateOf("") }
    var sugg by remember { mutableStateOf<List<String>>(emptyList()) }
    val hist by Settings.searchHist.collectAsState()
    var defaultWord by remember { mutableStateOf("") }

    var videos by remember { mutableStateOf<List<SearchVideo>>(emptyList()) }
    var bangs by remember { mutableStateOf<List<SearchBangumi>>(emptyList()) }
    var users by remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }

    fun submit(kw: String) {
        val t = kw.trim()
        if (t.isEmpty()) return
        input = t
        query = t
        sugg = emptyList()
        kotlinx.coroutines.MainScope().launch { Settings.addSearch(ctx, t) }
    }

    LaunchedEffect(Unit) {
        defaultWord = runCatching { Api.searchDefault() }.getOrDefault("")
    }
    LaunchedEffect(input) {
        if (input.isBlank() || input == query) { sugg = emptyList(); return@LaunchedEffect }
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
        // 顶部：返回 + 胶囊输入框（与首页搜索框同款）+ 圆角搜索钮
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text(defaultWord.ifEmpty { "搜索 B 站内容" }, fontSize = 15.sp) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { submit(input.ifBlank { defaultWord }) }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit(input.ifBlank { defaultWord }) },
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text("搜索", fontSize = 14.sp) }
        }

        if (query.isBlank()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                if (sugg.isNotEmpty()) {
                    sugg.forEach { s ->
                        Text(s, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth().clickable { submit(s) }.padding(vertical = 10.dp))
                    }
                } else if (hist.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("历史搜索", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f))
                        Text("清空", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                sco.launch { Settings.clearSearch(ctx) }
                            }.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                    hist.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)) {
                            row.forEach { h ->
                                AssistChip(onClick = { submit(h) },
                                    label = { Text(h, fontSize = 12.sp, maxLines = 1) })
                            }
                        }
                    }
                } else {
                    Text("输入关键词搜索 B 站", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp))
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

        if (tab == 0) {
            // 视频结果：双列网格，与首页推荐流同款卡片
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                items(videos.size) { i ->
                    val v = videos[i]
                    VideoCard(
                        VideoItem(
                            bvid = v.bvid, aid = v.aid, title = stripHtml(v.title),
                            pic = v.pic.normUrl(), duration = v.durSec(),
                            owner = Owner(name = stripHtml(v.author)),
                            stat = Stat(view = v.play.toLongOrNull() ?: 0),
                        ),
                        onClick = { if (v.bvid.isNotEmpty()) onVideo(v.bvid, v.aid) else onVideo("", v.aid) },
                    )
                }
                item { SearchFooter(error, loading, hasMore, page) { sco.launch { load(page + 1) } } }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                when (tab) {
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
                item { SearchFooter(error, loading, hasMore, page) { sco.launch { load(page + 1) } } }
            }
        }
    }
}

@Composable
private fun SearchFooter(
    error: String?, loading: Boolean, hasMore: Boolean, page: Int, onLoadMore: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.Center) {
        when {
            error != null && page == 0 ->
                Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onLoadMore))
            loading -> Text("搜索中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            hasMore && page in 1..4 ->
                Text("加载更多", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onLoadMore))
            page > 0 && !hasMore -> Text("没有更多了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
