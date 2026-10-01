package com.sammy.fbili.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.FeedItem
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.normUrl
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtCount(n: Long): String = when {
    n >= 100000000 -> "%.1f亿".format(n / 1e8)
    n >= 10000 -> "%.1f万".format(n / 1e4)
    else -> "$n"
}

fun fmtDur(sec: Long): String = when {
    sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    sec >= 0 -> "%d:%02d".format(sec / 60, sec % 60)
    else -> ""
}

fun timeAgo(unix: Long): String {
    val d = System.currentTimeMillis() / 1000 - unix
    return when {
        d < 3600 -> "${d / 60}分钟前"
        d < 86400 -> "${d / 3600}小时前"
        d < 86400 * 30 -> "${d / 86400}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(unix * 1000))
    }
}

fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")

/** 通用分页引擎：失败保留已有数据，只露重试按钮——不会再整页白屏 */
@Stable
class Paging<T>(val pageSizeDesc: String = "下拉加载更多", private val load: suspend (Int) -> List<T>) {
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var page = 0
        private set

    fun refresh() = fetch(1) { list ->
        items.clear()
        items.addAll(list)
    }

    fun more() {
        if (loading || !hasMore) return
        fetch(page + 1) { items.addAll(it) }
    }

    private fun fetch(p: Int, apply: (List<T>) -> Unit) {
        if (loading) return
        loading = true
        error = null
        Account.AppScope.launch {
            try {
                val list = load(p)
                page = p
                apply(list)
                hasMore = list.isNotEmpty()
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun <T> PagedList(
    paging: Paging<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    itemContent: @Composable (T) -> Unit,
) {
    LaunchedEffect(paging) {
        if (paging.items.isEmpty() && !paging.loading && paging.error == null) paging.refresh()
    }
    val atBottom = androidx.compose.runtime.derivedStateOf {
        val info = state.layoutInfo
        info.visibleItemsInfo.isNotEmpty() &&
            info.visibleItemsInfo.last().index >= info.totalItemsCount - 2
    }
    LaunchedEffect(state, paging) {
        androidx.compose.runtime.snapshotFlow { atBottom.value }.collect { if (it) paging.more() }
    }
    LazyColumn(modifier.fillMaxSize(), state = state) {
        items(paging.items.size) { i -> itemContent(paging.items[i]) }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    paging.loading -> Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    paging.error != null -> Row(horizontalArrangement = Arrangement.Center) {
                        Text("${paging.error}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { if (paging.page == 0) paging.refresh() else paging.more() }) {
                            Text("重试", fontSize = 13.sp)
                        }
                    }
                    !paging.hasMore && paging.items.isNotEmpty() ->
                        Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        if (paging.items.isEmpty() && paging.error != null) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Button(onClick = { paging.refresh() }) { Text("重新加载") }
            } }
        }
    }
}
@Composable
fun Cover(url: String, modifier: Modifier = Modifier, dur: Long = -1, badge: String = "") {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        AsyncImage(
            model = url, contentDescription = null,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        if (dur >= 0 || badge.isNotEmpty()) {
            Text(
                badge.ifEmpty { fmtDur(dur) },
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .background(Color(0xB0000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Cover(item.pic, Modifier.fillMaxWidth().aspectRatio(16f / 9f), item.duration)
        Text(
            stripHtml(item.title), maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.owner?.name ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                "${fmtCount(item.stat?.view ?: 0)}播放 · ${fmtCount(item.stat?.danmaku ?: 0)}弹幕",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FeedCard(item: FeedItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    VideoCard(
        VideoItem(
            bvid = item.keyBvid(), aid = item.keyAid(), title = item.title,
            pic = item.cover.normUrl(), duration = item.duration,
            owner = item.upper?.let { com.sammy.fbili.net.Owner(name = it.name, mid = it.mid) },
            stat = com.sammy.fbili.net.Stat(view = item.count),
        ), onClick, modifier,
    )
}

/** 横条行：历史/收藏/搜索番剧通用 */
@Composable
fun ListRow(
    title: String, cover: String, sub: String, extra: String = "",
    progress: Long = -1, totalDur: Long = 0, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.width(140.dp).height(84.dp)) {
            Cover(cover, Modifier.fillMaxSize())
            if (progress in 1 until 1000000 && totalDur > 0) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .height(3.dp).background(MaterialTheme.colorScheme.outline)
                ) {
                    Box(
                        Modifier.fillMaxWidth((progress.toFloat() / totalDur).coerceIn(0f, 1f))
                            .height(3.dp).background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (extra.isNotEmpty()) {
                Text(extra, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 暂停图标：material-icons-core 不含 Pause，手工 PathNode 自绘避免引入 extended 大包 */
val IconPause: ImageVector by lazy {
    ImageVector.Builder(
        name = "FbiliPause", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = listOf(
            androidx.compose.ui.graphics.vector.PathNode.MoveTo(6f, 19f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(10f, 19f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(10f, 5f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(6f, 5f),
            androidx.compose.ui.graphics.vector.PathNode.MoveTo(14f, 5f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(14f, 19f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(18f, 19f),
            androidx.compose.ui.graphics.vector.PathNode.LineTo(18f, 5f),
        ),
    ).build()
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorBox(msg: String?, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    if (msg == null) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

/** 触底自动翻页 + 底部状态：见上方泛型 PagedList */
