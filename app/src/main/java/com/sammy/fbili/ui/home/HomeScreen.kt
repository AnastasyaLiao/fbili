package com.sammy.fbili.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.FeedItem
import kotlinx.serialization.json.JsonObject
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.arr
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.num
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.FeedCard
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.PagedList
import com.sammy.fbili.ui.common.Paging
import com.sammy.fbili.ui.common.VideoCard
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onVideo: (bvid: String, aid: Long) -> Unit,
    onSearch: () -> Unit,
    onLive: (Long) -> Unit,
) {
    val tabs = listOf("推荐", "热门", "排行榜", "直播")
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("fbili", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 7.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant,
                        androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .clickable(onClick = onSearch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, Modifier.width(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("搜索 B 站内容", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        TabRow(
            selectedTabIndex = pager.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = pager.currentPage == i, text = { Text(t) },
                    onClick = { scope.launch { pager.animateScrollToPage(i) } })
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> FeedTab(onVideo)
                1 -> PopularTab(onVideo)
                2 -> RankTab(onVideo)
                else -> LiveTab(onLive)
            }
        }
    }
}

@Composable
private fun FeedTab(onVideo: (String, Long) -> Unit) {
    val paging = remember { Paging { p: Int -> Api.feedRcmd(p) } }
    PagedList(paging) { item ->
        val b = item.keyBvid()
        FeedCard(item, onClick = { if (b.isNotEmpty()) onVideo(b, item.keyAid()) })
    }
}

@Composable
private fun PopularTab(onVideo: (String, Long) -> Unit) {
    val paging = remember { Paging { p: Int -> Api.popular(p) } }
    PagedList(paging) { item -> VideoCard(item, onClick = { onVideo(item.bvid, item.aid) }) }
}

@Composable
private fun RankTab(onVideo: (String, Long) -> Unit) {
    val paging = remember { Paging { _: Int -> Api.ranking() } }
    PagedList(paging) { item -> VideoCard(item, onClick = { onVideo(item.bvid, item.aid) }) }
}

private fun JsonObject.liveCover(): String =
    str("cover", "cover_parent", "pic").normUrl()

@Composable
private fun LiveTab(onLive: (Long) -> Unit) {
    val paging = remember {
        Paging<Triple<Long, String, String>> { p ->
            Api.liveRecommend(p).arr("list").map {
                Triple(it.num("room_id", "id"), it.str("title", "name"), it.liveCover())
            }
        }
    }
    PagedList(paging) { (id, title, cover) ->
        ListRow(title = title, cover = cover, sub = "直播中", onClick = { onLive(id) })
    }
}
