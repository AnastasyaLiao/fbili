@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sammy.fbili.ui.video

import android.content.Intent
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.BoxScope
import com.sammy.fbili.ui.common.IconPause
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sammy.fbili.MainActivity
import com.sammy.fbili.data.Account
import com.sammy.fbili.data.Settings
import com.sammy.fbili.dl.Downloader
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.Episode
import com.sammy.fbili.net.NetError
import com.sammy.fbili.net.PlayResult
import com.sammy.fbili.net.Reply
import com.sammy.fbili.net.Season
import com.sammy.fbili.net.ViewInfo
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.BiliError
import com.sammy.fbili.ui.common.Cover
import com.sammy.fbili.ui.common.ErrorBox
import com.sammy.fbili.ui.common.LoadingBox
import com.sammy.fbili.ui.common.fmtCount
import com.sammy.fbili.ui.common.fmtDur
import com.sammy.fbili.ui.common.timeAgo
import com.sammy.fbili.ui.danmaku.DanmakuRepo
import com.sammy.fbili.ui.danmaku.DanmakuView
import com.sammy.fbili.ui.player.PlayerCtl
import com.sammy.fbili.ui.player.StreamPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    bvid: String, aid: Long, epId: Long,
    onVideo: (String, Long) -> Unit,
    onSeason: (Long) -> Unit,
    onUser: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val act = ctx as? MainActivity
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf<ViewInfo?>(null) }
    var season by remember { mutableStateOf<Season?>(null) }
    var epIdx by remember { mutableIntStateOf(0) }
    var pageIdx by remember { mutableIntStateOf(0) }
    var play by remember { mutableStateOf<PlayResult?>(null) }
    var related by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    var loadErr by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var loadingStream by remember { mutableStateOf(false) }

    var maxQn by remember { mutableIntStateOf(Settings.maxQuality.value) }
    var curQn by remember { mutableIntStateOf(0) }
    var qnMenu by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var full by remember { mutableStateOf(false) }
    var ctrlVisible by remember { mutableStateOf(true) }

    var dmOn by remember { mutableStateOf(Settings.danmakuOn.value) }
    var dmView by remember { mutableStateOf<DanmakuView?>(null) }
    var dmLocal by remember { mutableStateOf("") }

    var liked by remember { mutableStateOf(false) }
    var coined by remember { mutableIntStateOf(0) }
    var faved by remember { mutableStateOf(false) }
    var coinDialog by remember { mutableStateOf(false) }
    var favSheet by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<com.sammy.fbili.net.FavFolder>>(emptyList()) }
    var inFids by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 评论
    var comments by remember { mutableStateOf<List<Reply>>(emptyList()) }
    var cPage by remember { mutableIntStateOf(0) }
    var cMore by remember { mutableStateOf(true) }
    var cHot by remember { mutableStateOf(false) }
    var cLoading by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var cInput by remember { mutableStateOf("") }
    var subSheet by remember { mutableStateOf<Reply?>(null) }

    val ctl = remember { PlayerCtl(ctx) }

    // ---- 当前生效的 bvid/aid/cid/ep ----
    fun curBvid(): String = season?.eps()?.getOrNull(epIdx)?.bvid ?: (view?.bvid ?: bvid)
    fun curAid(): Long = season?.eps()?.getOrNull(epIdx)?.aid ?: (view?.aid ?: aid)
    fun curCid(): Long = if (season != null) season!!.eps()[epIdx].cid
        else view?.pages?.getOrNull(pageIdx)?.cid ?: (view?.cid ?: 0)
    fun curEpId(): Long = if (season != null) season!!.eps()[epIdx].id else 0
    fun curTitle(): String = season?.title ?: (view?.title ?: "")

    fun errText(e: Throwable): String = when (e) {
        is BiliError -> e.message
        is NetError -> e.message ?: "网络错误"
        else -> e.message ?: "出错了"
    }

    suspend fun startStream(seek: Long = 0) {
        loadingStream = true
        try {
            val cid = curCid()
            if (cid <= 0) return
            val p = Api.playurl(curBvid(), cid, curEpId())
            play = p
            val picked = StreamPicker.pick(p, maxQn)
            if (picked != null) {
                curQn = picked.first.id.toInt()
                ctl.playDash(p, picked.first, picked.second, seek)
            } else p.durl.firstOrNull()?.url?.let {
                curQn = p.quality
                ctl.playSingle(it, seek)
            }
            // 弹幕
            dmView?.let { dv ->
                val list = DanmakuRepo.cached(cid)
                    ?: DanmakuRepo.parse(Api.danmakuXml(cid)).also { DanmakuRepo.put(cid, it) }
                dv.setData(list); dv.seekTo(seek)
            }
        } catch (e: Exception) {
            loadErr = errText(e)
        } finally {
            loadingStream = false
        }
    }

    LaunchedEffect(bvid, aid, epId, reloadTick) {
        loadErr = null
        try {
            if (epId > 0) {
                season = Api.season(0, epId)
                epIdx = season!!.eps().indexOfFirst { it.id == epId }.coerceAtLeast(0)
                pageIdx = 0
            } else {
                val v = Api.view(bvid, aid)
                view = v
                pageIdx = v.pages.indexOfFirst { it.cid == v.cid }.coerceAtLeast(0)
                related = runCatching { Api.related(v.bvid) }.getOrDefault(emptyList())
                liked = runCatching { Api.hasLike(v.bvid) }.getOrDefault(false)
                coined = runCatching { Api.coinInfo(v.bvid).toInt() }.getOrDefault(0)
                faved = v.isFav
            }
            startStream()
        } catch (e: Exception) {
            loadErr = errText(e)
        }
    }

    // 播放状态轮询 + 心跳
    LaunchedEffect(ctl) {
        var tick = 0
        while (true) {
            isPlaying = ctl.exo.isPlaying
            posMs = ctl.exo.currentPosition.coerceAtLeast(0)
            durMs = ctl.exo.duration.coerceAtLeast(0)
            delay(400)
            if (isPlaying && ++tick % 750 == 0) { // ~5 分钟
                runCatching { Api.heartbeat(curBvid(), curCid(), posMs / 1000) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = ctl.exo.currentPosition
            val a = curAid(); val c = curCid()
            if (pos > 5000) Account.AppScope.launch { runCatching { Api.reportHistory(a, c, pos / 1000) } }
            act?.setLandscape(false)
            ctl.release()
        }
    }

    // ---- 评论加载 ----
    suspend fun loadComments(reset: Boolean) {
        if (cLoading) return
        cLoading = true
        try {
            val p = if (reset) 1 else cPage + 1
            val page = Api.replies(curAid(), p, cHot)
            if (reset) { comments = page.replies; cMore = page.replies.isNotEmpty() && p < (page.cursor?.allCount?.div(20)?.plus(1) ?: 999) }
            else { comments = comments + page.replies; cMore = page.replies.isNotEmpty() }
            cPage = p
        } catch (e: Exception) { /* 保留已有评论 */ }
        cLoading = false
    }
    LaunchedEffect(view, season, reloadTick) { if (curAid() > 0) loadComments(true) }

    val title = curTitle()

    Column(Modifier.fillMaxSize()) {
        // ===== 播放区 =====
        val playerH = if (full) 0.dp else 230.dp
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (full) Modifier.fillMaxSize() else Modifier.height(playerH))
                .background(Color.Black),
        ) {
            AndroidSv(ctl, this)
            AndroidDanmaku(ctl, this, onCreated = { dmView = it; it.show = dmOn })
            // 点击播放器切换控制条（位于控制层之下）
            Box(Modifier.matchParentSize().clickable { ctrlVisible = !ctrlVisible })
            if (loadingStream && posMs == 0L) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
            // 中央播放/暂停
            if (!isPlaying && !loadingStream) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(52.dp)
                        .clip(RoundedCornerShape(50)).clickable { ctl.exo.playWhenReady = true })
            }
            // 底部控制条
            if (ctrlVisible || !isPlaying) Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { ctl.exo.playWhenReady = !ctl.exo.isPlaying }) {
                    Icon(if (isPlaying) IconPause else Icons.Filled.PlayArrow, null, tint = Color.White)
                }
                Text("${fmtDur(posMs / 1000)}/${fmtDur(durMs / 1000)}", color = Color.White, fontSize = 11.sp)
                var drag by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = drag ?: if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { f ->
                            val to = (f * durMs).toLong()
                            ctl.exo.seekTo(to); dmView?.seekTo(to); posMs = to
                        }
                        drag = null
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                TextButton(onClick = { dmOn = !dmOn; dmView?.show = dmOn; scope.launch { Settings.setDanmaku(ctx, dmOn) } }) {
                    Text(if (dmOn) "弹幕开" else "弹幕关", color = Color.White, fontSize = 12.sp)
                }
                Box {
                    TextButton(onClick = { qnMenu = true }) {
                        Text(StreamPicker.desc(play ?: return@TextButton, curQn), color = Color.White, fontSize = 12.sp)
                    }
                    DropdownMenu(qnMenu, onDismissRequest = { qnMenu = false }) {
                        StreamPicker.available(play ?: PlayResult()).forEach { q ->
                            DropdownMenuItem(
                                text = { Text(StreamPicker.desc(play ?: PlayResult(), q) +
                                    if (q > 80 && (Account.user.value?.vip?.status ?: 0) != 1) " (需大会员)" else "") },
                                onClick = {
                                    qnMenu = false
                                    maxQn = q
                                    scope.launch {
                                        Settings.setQuality(ctx, q)
                                        startStream(posMs)
                                    }
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    full = !full; ctrlVisible = true
                    act?.setLandscape(full)
                }) { Text(if (full) "退出全屏" else "全屏", color = Color.White, fontSize = 12.sp) }
            }
            // 顶部：返回 + 本地弹幕输入
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(if (ctrlVisible) Color(0x66000000) else Color.Transparent)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (full) { full = false; act?.setLandscape(false) } else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                }
                if (dmOn) OutlinedTextField(
                    value = dmLocal, onValueChange = { dmLocal = it },
                    placeholder = { Text("发本地弹幕", color = Color.Gray, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(44.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        dmView?.addLocal(dmLocal); dmLocal = ""
                    }),
                )
                Spacer(Modifier.width(8.dp))
            }
        }

        if (full) return@Column

        // ===== 内容区 =====
        if (loadErr != null && view == null && season == null) {
            ErrorBox(loadErr, onRetry = { reloadTick++ }, modifier = Modifier.weight(1f))
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(12.dp)) {
                    Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    view?.let { v ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${fmtCount(v.stat?.view ?: 0)}播放 · ${fmtCount(v.stat?.danmaku ?: 0)}弹幕 · " +
                                "${fmtCount(v.stat?.reply ?: 0)}评论 · 发布 ${timeAgo(v.pubdate)}" +
                                if (v.pages.size > 1) " · ${v.pages.size}P" else "",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { v.owner?.mid?.let(onUser) }) {
                            AsyncImage(model = v.owner?.face, contentDescription = null,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text(v.owner?.name ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    season?.let { s ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("番剧 · ${s.eps().size}话", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                            TextButton(onClick = { onSeason(s.seasonId) }) { Text("详情页") }
                        }
                    }
                    // 操作行
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = {
                            if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@AssistChip }
                            scope.launch {
                                runCatching { Api.like(curBvid(), !liked); liked = !liked }
                                    .onFailure { Toast.makeText(ctx, errText(it), Toast.LENGTH_SHORT).show() }
                            }
                        }, label = { Text(if (liked) "已赞 ${fmtCount((view?.stat?.like ?: 0) + 1)}" else "点赞 ${fmtCount(view?.stat?.like ?: 0)}") })
                        AssistChip(onClick = {
                            if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@AssistChip }
                            coinDialog = true
                        }, label = { Text(if (coined > 0) "已币" else "投币") })
                        AssistChip(onClick = {
                            if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@AssistChip }
                            favSheet = true
                            if (folders.isEmpty()) scope.launch {
                                folders = runCatching { Api.favFolders(Account.uid()) }.getOrDefault(emptyList())
                            }
                        }, label = { Text(if (faved) "已藏" else "收藏") })
                        AssistChip(onClick = {
                            scope.launch { runCatching { Api.share(curBvid()) } }
                            val i = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://www.bilibili.com/video/${curBvid()} $title")
                            }, "分享")
                            runCatching { ctx.startActivity(i) }
                        }, label = { Text("分享") })
                        AssistChip(onClick = {
                            if (season != null) { Toast.makeText(ctx, "番剧暂不支持缓存", Toast.LENGTH_SHORT).show(); return@AssistChip }
                            Downloader.enqueue(ctx, curBvid(), curCid(), title, curQn.coerceAtMost(64))
                            Toast.makeText(ctx, "已加入下载队列", Toast.LENGTH_SHORT).show()
                        }, label = { Text("缓存") })
                    }
                    // 分 P / 话
                    view?.pages?.takeIf { it.size > 1 }?.let { pages ->
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pages.size) { i ->
                                AssistChip(
                                    onClick = { pageIdx = i; scope.launch { startStream(0) } },
                                    label = { Text("${i + 1} ${pages[i].part.take(12)}", fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                    season?.eps()?.takeIf { it.size > 1 }?.let { eps ->
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(eps.size) { i ->
                                AssistChip(
                                    onClick = { epIdx = i; scope.launch { startStream(0) } },
                                    label = { Text(eps[i].part.ifEmpty { "${i + 1}" }.take(10), fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                    if (loadErr != null) Text(loadErr!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp).clickable { scope.launch { startStream(0) } })
                    if (view?.desc?.isNotBlank() == true) {
                        Spacer(Modifier.height(8.dp))
                        Text(view!!.desc.take(300), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (related.isNotEmpty()) {
                item { Text("相关推荐", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                items(related.take(10).chunked(2)) { row ->
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        row.forEach { it2 ->
                            Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                com.sammy.fbili.ui.common.VideoCard(item = it2, onClick = { onVideo(it2.bvid, it2.aid) })
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            // 评论区
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("评论 ${fmtCount(view?.stat?.reply ?: 0)}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { cHot = !cHot; scope.launch { loadComments(true) } }) {
                        Text(if (cHot) "热门" else "最新", fontSize = 12.sp)
                    }
                }
            }
            items(comments) { r ->
                CommentItem(
                    r,
                    onLike = {
                        scope.launch {
                            runCatching { Api.likeReply(curAid(), r.rpid, !r.liked) }
                                .onSuccess {
                                    comments = comments.map { c ->
                                        if (c.rpid == r.rpid) c.copy(liked = !c.liked, like = c.like + if (c.liked) -1 else 1)
                                        else c
                                    }
                                }
                        }
                    },
                    onReply = { replyTarget = r.rpid to r.member.uname },
                    onSub = { subSheet = r },
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    when {
                        cLoading -> Text("加载中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        cMore -> TextButton(onClick = { scope.launch { loadComments(false) } }) { Text("加载更多评论") }
                        comments.isNotEmpty() -> Text("没有更多评论了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        // 评论输入栏
        CommentInputBar(
        enabled = Account.isLogin,
        hint = replyTarget?.second?.let { "回复 @$it" } ?: "写评论…",
        value = cInput, onValueChange = { cInput = it },
        onSend = {
            if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@CommentInputBar }
            val msg = cInput.trim(); if (msg.isEmpty()) return@CommentInputBar
            scope.launch {
                try {
                    val target = replyTarget?.first ?: 0L
                    Api.addReply(curAid(), msg, if (target == 0L) 0L else target, target)
                    cInput = ""; replyTarget = null
                    loadComments(true)
                    Toast.makeText(ctx, "发布成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(ctx, errText(e), Toast.LENGTH_SHORT).show() }
            }
        },
        onCancelReply = { replyTarget = null },
    )
    }

    // 投币对话框
    if (coinDialog) AlertDialog(
        onDismissRequest = { coinDialog = false },
        title = { Text("投币") },
        text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 2).forEach { n ->
                Button2("$n 币") {
                    coinDialog = false
                    scope.launch {
                        runCatching { Api.coin(curBvid(), n); coined = n; if (!liked) { Api.like(curBvid(), true); liked = true } }
                            .onFailure { Toast.makeText(ctx, errText(it), Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        } },
        confirmButton = {},
    )

    // 收藏面板
    if (favSheet) ModalBottomSheet(onDismissRequest = { favSheet = false }) {
        Column(Modifier.padding(16.dp)) {
            Text("选择收藏夹", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (folders.isEmpty()) Text("加载中… / 若无收藏夹请先在手机 B 站 App 建一个", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            folders.forEach { f ->
                val inF = f.id in inFids || (faved && folders.firstOrNull()?.id == f.id)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            try {
                                if (inF) { Api.favDeal(curAid(), emptyList(), listOf(f.id)); inFids -= f.id; faved = false }
                                else { Api.favDeal(curAid(), listOf(f.id), emptyList()); inFids += f.id; faved = true }
                                favSheet = false
                            } catch (e: Exception) { Toast.makeText(ctx, errText(e), Toast.LENGTH_SHORT).show() }
                        }
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (inF) Icons.Filled.Favorite else Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = if (inF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("${f.title} (${f.mediaCount})", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }

    // 楼中楼
    subSheet?.let { target -> SubReplySheet(target, aid = curAid(), onDismiss = { subSheet = null }) }

    // 点击评论回复的对话框目标通过 replyTarget 设置，见 CommentItem
}

@Composable
private fun Button2(text: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick) { Text(text) }
}

@Composable
private fun AndroidSv(ctl: PlayerCtl, box: BoxScope) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c -> SurfaceView(c) },
        update = { sv -> ctl.bindSurface(sv) },
        modifier = with(box) { Modifier.matchParentSize() },
    )
}

@Composable
private fun AndroidDanmaku(ctl: PlayerCtl, box: BoxScope, onCreated: (DanmakuView) -> Unit) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            DanmakuView(c).also {
                it.timeProvider = { ctl.exo.currentPosition.coerceAtLeast(0) }
                onCreated(it)
            }
        },
        update = { it.paused = !ctl.exo.isPlaying },
        modifier = with(box) { Modifier.matchParentSize() },
    )
}

@Composable
private fun CommentItem(r: Reply, onLike: () -> Unit, onReply: () -> Unit, onSub: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = r.member.face.normUrl(), contentDescription = null,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)))
            Spacer(Modifier.width(8.dp))
            Text(r.member.uname, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onLike)) {
                Icon(Icons.Filled.ThumbUp, null, Modifier.size(15.dp),
                    tint = if (r.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("${r.like}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(r.content.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(timeAgo(r.ctime), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("回复", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onReply))
            if (r.count > 0) {
                Spacer(Modifier.width(12.dp))
                Text("共${r.count}条回复", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.clickable(onClick = onSub))
            }
        }
    }
}

@Composable
private fun CommentInputBar(
    enabled: Boolean, hint: String, value: String,
    onValueChange: (String) -> Unit, onSend: () -> Unit, onCancelReply: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            placeholder = { Text(hint, fontSize = 13.sp) },
            singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Spacer(Modifier.width(8.dp))
        if (value.isNotEmpty()) {
            Button2("发送", onClick = onSend)
        } else if (!enabled) {
            Text("登录后可评论", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hint.startsWith("回复")) {
            Spacer(Modifier.width(6.dp))
            Text("取消", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable(onClick = onCancelReply))
        }
    }
}

@Composable
private fun SubReplySheet(target: Reply, aid: Long, onDismiss: () -> Unit) {
    var subs by remember { mutableStateOf<List<Reply>>(emptyList()) }
    var pn by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        loading = true
        try { subs = Api.subReplies(aid, target.rpid, 1); pn = 1 } catch (e: Exception) {}
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxHeight(0.7f)) {
            item {
                Column(Modifier.padding(12.dp)) {
                    Text(target.content.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("全部回复", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            items(subs) { s ->
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(s.member.uname, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.content.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(timeAgo(s.ctime), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                val moreScope = androidx.compose.runtime.rememberCoroutineScope()
                if (loading) Text("加载中…", modifier = Modifier.padding(12.dp))
                else if (subs.isNotEmpty()) TextButton(onClick = {
                    loading = true
                    moreScope.launch {
                        try { subs = subs + Api.subReplies(aid, target.rpid, pn + 1); pn++ } catch (e: Exception) {}
                        loading = false
                    }
                }) { Text("更多回复") }
            }
        }
    }
}
