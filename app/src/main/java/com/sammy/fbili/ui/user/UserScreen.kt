package com.sammy.fbili.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import kotlinx.serialization.json.JsonObject
import com.sammy.fbili.net.Owner
import com.sammy.fbili.net.RelationUser
import com.sammy.fbili.net.Stat
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.arr
import com.sammy.fbili.net.durParse
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.num
import com.sammy.fbili.net.obj
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.ErrorBox
import com.sammy.fbili.ui.common.ListRow
import com.sammy.fbili.ui.common.PagedList
import com.sammy.fbili.ui.common.Paging
import com.sammy.fbili.ui.common.VideoCard
import com.sammy.fbili.ui.common.fmtCount
import kotlinx.coroutines.launch

private fun JsonObject.toSpaceVideo(): VideoItem = VideoItem(
    bvid = str("bvid"),
    aid = num("aid", "aclick"),
    title = str("title"),
    pic = str("pic").normUrl(),
    duration = num("duration").takeIf { it > 0 } ?: str("duration").durParse(),
    owner = Owner(name = str("author")),
    stat = Stat(view = num("play")),
)

@Composable
fun UserScreen(mid: Long, onVideo: (String, Long) -> Unit, onBack: () -> Unit) {
    var uname by remember { mutableStateOf("") }
    var face by remember { mutableStateOf("") }
    var sign by remember { mutableStateOf("") }
    var fans by remember { mutableLongStateOf(0L) }
    var isFollowed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val me by Account.user.collectAsState()

    LaunchedEffect(mid, tick) {
        error = null
        try {
            val d = Api.userCard(mid)
            val c = d.obj("card") ?: d
            uname = c.str("name", "uname")
            face = c.str("face").normUrl()
            sign = c.str("sign")
            fans = Api.userStat(mid).num("follower", "fans")
            val rel = Api.relation(mid)
            isFollowed = rel.num("attribute") in listOf(2L, 6L)
        } catch (e: Exception) {
            error = e.message
        }
    }

    val paging = remember(mid) {
        Paging { p: Int ->
            Api.spaceVideos(mid, p).obj("list")?.arr("vlist")?.map { it.toSpaceVideo() } ?: emptyList()
        }
    }

    if (error != null && uname.isEmpty()) {
        ErrorBox(error) { tick++ }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = face, contentDescription = null,
                modifier = Modifier.size(56.dp).clip(CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uname, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                if (sign.isNotBlank()) Text(sign, fontSize = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("粉丝 ${fmtCount(fans)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mid != me?.mid && mid > 0) {
                if (isFollowed) OutlinedButton(onClick = {
                    scope.launch { runCatching { Api.relationModify(mid, 0); isFollowed = false } }
                }) { Text("已关注") }
                else Button(onClick = {
                    if (!Account.isLogin) { error = "请先登录"; return@Button }
                    scope.launch { runCatching { Api.relationModify(mid, 2); isFollowed = true } }
                }) { Text("关注") }
            }
        }
        Text("TA 的投稿", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        PagedList(paging, Modifier.weight(1f)) { v: VideoItem ->
            VideoCard(v, onClick = { onVideo(v.bvid, v.aid) }, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
fun FollowingScreen(onUser: (Long) -> Unit, onBack: () -> Unit) {
    val me by Account.user.collectAsState()
    val paging = remember(me?.mid) {
        Paging { p: Int ->
            me?.let { Api.relationFollowing(it.mid, p) } ?: emptyList()
        }
    }
    if (me == null) {
        ErrorBox("请先登录") { onBack() }
        return
    }
    PagedList(paging) { u: RelationUser ->
        ListRow(title = u.uname, cover = u.face.normUrl(), sub = u.sign, onClick = { onUser(u.mid) })
    }
}
