package com.sammy.fbili.ui.bangumi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sammy.fbili.net.Api
import kotlinx.serialization.json.JsonObject
import com.sammy.fbili.net.Season
import com.sammy.fbili.net.asT
import com.sammy.fbili.net.num
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.obj
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.ErrorBox
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.fmtDur
import com.sammy.fbili.ui.common.stripHtml
import kotlinx.coroutines.launch

@Composable
fun BangumiScreen(onSeason: (Long) -> Unit, onVideo: (String, Long) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        loading = true; error = null
        scope.launch {
            try {
                items = Api.pgcRank(if (tab == 0) 1 else 4)
                if (items.isEmpty()) error = "榜单暂无数据"
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            } finally { loading = false }
        }
    }
    LaunchedEffect(tab) { load() }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary) {
            listOf("番剧榜", "国创榜").forEachIndexed { i, t ->
                Tab(selected = tab == i, text = { Text(t) }, onClick = { tab = i })
            }
        }
        if (error != null && items.isEmpty()) {
            ErrorBox("$error（也可从搜索找番剧）", onRetry = { load() }, modifier = Modifier.weight(1f))
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            items(items) { it ->
                val url = it.str("url", "link")
                val sid = Regex("ss(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
                    ?: it.num("season_id", "media_id", "rid")
                val title = stripHtml(it.str("title", "main_title"))
                val sub = it.str("index_show", "new_ep", "areas").ifEmpty {
                    it.obj("stat")?.let { s -> "${s.num("views")}播放" } ?: ""
                }
                val top = it.num("rank")
                Row(
                    Modifier.fillMaxWidth().clickable { if (sid > 0) onSeason(sid) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (top in 1..20) Text(
                        "$top", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(30.dp).align(Alignment.CenterVertically),
                    )
                    AsyncImage(model = it.str("cover", "square_cover").normUrl(), contentDescription = null,
                        modifier = Modifier.width(84.dp).height(112.dp).clip(RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
            item {
                Text(
                    "提示：想看具体番剧可在搜索页切到「番剧」标签查找",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
fun SeasonScreen(seasonId: Long, onVideo: (String, Long, Long) -> Unit, onBack: () -> Unit) {
    var season by remember { mutableStateOf<Season?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(seasonId, tick) {
        error = null
        try { season = Api.season(seasonId) } catch (e: Exception) { error = e.message }
    }

    if (error != null && season == null) {
        ErrorBox(error) { tick++ }
        return
    }
    val s = season ?: run {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.padding(14.dp)) {
                AsyncImage(model = s.cover.normUrl(), contentDescription = null,
                    modifier = Modifier.width(100.dp).height(134.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    if (s.evaluate.isNotBlank()) Text(
                        "评分 ${s.evaluate}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("共 ${s.eps().size} 话", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(s.eps()) { ep ->
            ListRow(
                title = ep.part.ifEmpty { "第 ${s.eps().indexOf(ep) + 1} 话" },
                cover = ep.cover.normUrl(),
                sub = fmtDur(ep.duration),
                onClick = { onVideo(ep.bvid, ep.aid, ep.id) },
            )
        }
    }
}
