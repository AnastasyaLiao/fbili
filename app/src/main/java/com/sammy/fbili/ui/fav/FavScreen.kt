package com.sammy.fbili.ui.fav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.FavFolder
import com.sammy.fbili.net.FavMedia
import com.sammy.fbili.net.listT
import com.sammy.fbili.net.num
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.ErrorBox
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.fmtCount
import com.sammy.fbili.ui.common.fmtDur
import kotlinx.coroutines.launch

@Composable
fun FavFoldersScreen(onFolder: (Long, String) -> Unit, onBack: () -> Unit) {
    val me by Account.user.collectAsState()
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var covers by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(me?.mid, tick) {
        val mid = Account.uid().takeIf { it > 0 } ?: me?.mid ?: run { error = "请先登录"; return@LaunchedEffect }
        error = null
        try { folders = Api.favFolders(mid) } catch (e: Exception) { error = e.message }
        // 封面取每个收藏夹第一条视频：逐夹轻量请求，先到先上屏，失败留占位
        folders.forEach { f ->
            runCatching {
                val d = Api.favResources(f.id, 1)
                val first: FavMedia? = d.listT<FavMedia>("medias").firstOrNull()
                first?.cover?.takeIf { it.isNotEmpty() }
                    ?.let { covers = covers + (f.id to it) }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("我的收藏", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        if (error != null && folders.isEmpty()) {
            ErrorBox(error, Modifier.weight(1f)) { tick++ }
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            items(folders) { f ->
                ListRow(
                    title = f.title, cover = covers[f.id].orEmpty(), sub = "",
                    extra = "${f.mediaCount} 个内容",
                    onClick = { onFolder(f.id, f.title) },
                )
            }
        }
    }
}

@Composable
fun FavResourcesScreen(
    fid: Long, title: String,
    onVideo: (String, Long) -> Unit, onBack: () -> Unit,
) {
    var medias by remember { mutableStateOf<List<FavMedia>>(emptyList()) }
    var pn by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true; error = null
        try {
            val p = if (reset) 1 else pn + 1
            val d = Api.favResources(fid, p)
            val list: List<FavMedia> = d.listT("medias")
            medias = if (reset) list else medias + list
            pn = p
            hasMore = (d["has_more"]?.toString() != "false") && list.isNotEmpty()
        } catch (e: Exception) {
            error = e.message
        } finally { loading = false }
    }
    LaunchedEffect(fid) { load(true) }

    if (error != null && medias.isEmpty()) {
        ErrorBox(error) { scope.launch { load(true) } }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(14.dp))
        }
        items(medias) { m ->
            ListRow(
                title = m.title,
                cover = m.cover.normUrl(),
                sub = m.upper?.name ?: "",
                extra = m.intro.take(40).ifEmpty { fmtDur(m.duration) },
                onClick = {
                    // medias.id 是数字 aid，bvid 才是可播标识；番剧等非稿件条目给出提示
                    when {
                        m.bvid.startsWith("BV") -> onVideo(m.bvid, 0)
                        else -> android.widget.Toast.makeText(
                            ctx, "该内容类型暂不支持在此打开", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    loading -> Text("加载中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hasMore -> TextButton(onClick = { scope.launch { load(false) } }) { Text("加载更多") }
                    medias.isNotEmpty() -> Text("没有更多了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
