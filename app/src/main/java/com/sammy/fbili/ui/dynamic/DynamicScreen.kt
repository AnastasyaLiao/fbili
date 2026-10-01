package com.sammy.fbili.ui.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import kotlinx.serialization.json.JsonObject
import com.sammy.fbili.net.arr
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.num
import com.sammy.fbili.net.obj
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.timeAgo
import kotlinx.coroutines.launch

private class DynCard(
    val name: String, val face: String, val timeText: String,
    val text: String, val cover: String, val title: String,
    val bvid: String, val view: String, val dm: String, val idStr: String,
)

private fun JsonObject.toCard(): DynCard {
    val mods = obj("modules") ?: JsonObject(emptyMap())
    val author = mods.obj("module_author") ?: JsonObject(emptyMap())
    val dyn = mods.obj("module_dynamic") ?: JsonObject(emptyMap())
    val major = dyn.obj("major") ?: JsonObject(emptyMap())
    val arc = major.obj("archive") ?: JsonObject(emptyMap())
    val draw = major.obj("draw")?.arr("images")?.firstOrNull() ?: JsonObject(emptyMap())

    var text = dyn.obj("desc")?.str("text", "desc_text", "raw_text") ?: ""
    if (text.isEmpty()) {
        text = dyn.obj("opus")?.obj("summary")?.arr("rich_text_nodes")
            ?.joinToString("") { it.str("word") } ?: ""
    }
    if (text.isEmpty()) text = arc.str("title")
    val pubDesc = author.str("pub_time_desc", "pub_action")
    val timeText = pubDesc.ifEmpty { timeAgo(author.num("pub_time")) }
    val bv = arc.str("bvid").ifEmpty {
        Regex("BV[0-9A-Za-z]{10}").find(arc.str("uri", "short_link_v2"))?.value ?: ""
    }
    val stat = arc.obj("stat") ?: JsonObject(emptyMap())
    return DynCard(
        name = author.str("name"), face = author.str("face").normUrl(),
        timeText = timeText, text = text,
        cover = (arc.str("cover").ifEmpty { draw.str("src", "img_src") }).normUrl(),
        title = arc.str("title"), bvid = bv,
        view = stat.str("view", "votes"), dm = stat.str("danmaku"),
        idStr = str("id_str"),
    )
}

@Composable
fun DynamicScreen(
    onVideo: (String, Long) -> Unit,
    onUser: (Long) -> Unit,
    onLogin: () -> Unit,
) {
    val user by Account.user.collectAsState()
    var cards by remember { mutableStateOf<List<DynCard>>(emptyList()) }
    var offset by remember { mutableStateOf("") }
    var pageNo by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true; error = null
        try {
            val p = if (reset) 1 else pageNo + 1
            val d = Api.dynamicFeed(if (reset) "" else offset, p)
            val items = d.arr("items").map { it.toCard() }
            cards = if (reset) items else cards + items
            offset = d.str("offset")
            pageNo = p
            hasMore = d["has_more"]?.let { it.toString() != "false" } ?: items.isNotEmpty()
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally { loading = false }
    }

    LaunchedEffect(user?.mid) {
        if (Account.isLogin) load(true)
    }

    if (!Account.isLogin) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("登录后查看动态", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onLogin) { Text("去登录") }
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("动态", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(if (loading) "刷新中…" else "刷新", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { scope.launch { load(true) } })
            }
        }
        if (error != null && cards.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { scope.launch { load(true) } }) { Text("重试") }
            }
        }
        items(cards) { c ->
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        // 点头像进 UP 主页需要 mid；无 mid 时忽略
                    }) {
                    AsyncImage(model = c.face, contentDescription = null,
                        modifier = Modifier.size(34.dp).clip(CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(c.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(c.timeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (c.text.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(c.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                }
                if (c.cover.isNotEmpty() || c.title.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { if (c.bvid.isNotEmpty()) onVideo(c.bvid, 0) }
                            .padding(8.dp),
                    ) {
                        if (c.cover.isNotEmpty()) {
                            AsyncImage(model = c.cover, contentDescription = null,
                                modifier = Modifier.width(100.dp).height(62.dp).clip(RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            if (c.title.isNotEmpty()) Text(c.title, fontSize = 13.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                            if (c.view.isNotEmpty() || c.dm.isNotEmpty()) Text(
                                "${c.view}播放 · ${c.dm}弹幕", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.background))
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> Text("加载中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hasMore -> Text("加载更多", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { scope.launch { load(false) } })
                    cards.isNotEmpty() -> Text("没有更多了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
