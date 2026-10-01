@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sammy.fbili.ui.video

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.sammy.fbili.MainActivity
import com.sammy.fbili.data.Account
import com.sammy.fbili.data.Settings
import com.sammy.fbili.dl.Downloader
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.BiliError
import com.sammy.fbili.net.NetError
import com.sammy.fbili.net.PlayResult
import com.sammy.fbili.net.Reply
import com.sammy.fbili.net.Season
import com.sammy.fbili.net.ViewInfo
import com.sammy.fbili.net.VideoItem
import com.sammy.fbili.net.normUrl
import com.sammy.fbili.net.str
import com.sammy.fbili.ui.common.Cover
import com.sammy.fbili.ui.common.ErrorBox
import com.sammy.fbili.ui.common.IconPause
import com.sammy.fbili.ui.common.fmtCount
import com.sammy.fbili.ui.common.fmtDur
import com.sammy.fbili.ui.common.timeAgo
import com.sammy.fbili.ui.danmaku.DanmakuRepo
import com.sammy.fbili.ui.danmaku.DanmakuView
import com.sammy.fbili.ui.player.PlayerCtl
import com.sammy.fbili.ui.player.StreamPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 视频播放页（布局规格取自 bili_you 的 bili_video 页）：
 * 播放器 16:9 黑框常驻顶部、画面按原始比例 contain；下方「简介 / 评论」双 Tab。
 * 手势：单击=显隐控制层，双击=播放/暂停，横拖=±60s 微调 seek，左半竖拖=亮度，右半竖拖=音量。
 * 控制层播放中 3.5s 自动隐藏（bili_you 原版没有，这里补上）。
 */
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

    var isPlaying by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var full by remember { mutableStateOf(false) }
    var ctrlVisible by remember { mutableStateOf(true) }
    var ctrlTick by remember { mutableIntStateOf(0) }
    var rate by remember { mutableFloatStateOf(1f) }
    var rateDialog by remember { mutableStateOf(false) }
    var qnMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var dmDialog by remember { mutableStateOf(false) }
    var dmLocal by remember { mutableStateOf("") }
    // 手势反馈浮层：类型 + 文案（seek/亮度/音量），松手清除
    var osd by remember { mutableStateOf<String?>(null) }

    var dmOn by remember { mutableStateOf(Settings.danmakuOn.value) }
    var dmView by remember { mutableStateOf<DanmakuView?>(null) }

    var liked by remember { mutableStateOf(false) }
    var coined by remember { mutableIntStateOf(0) }
    var faved by remember { mutableStateOf(false) }
    var coinDialog by remember { mutableStateOf(false) }
    var favSheet by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<com.sammy.fbili.net.FavFolder>>(emptyList()) }
    var inFids by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var tab by remember { mutableIntStateOf(0) }

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
    LaunchedEffect(ctl) { ctl.onError = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } }

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
            ctl.exo.setPlaybackSpeed(rate)
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

    // 播放状态轮询（含画面比例）+ 心跳
    LaunchedEffect(ctl) {
        var tick = 0
        while (true) {
            isPlaying = ctl.exo.isPlaying
            posMs = ctl.exo.currentPosition.coerceAtLeast(0)
            durMs = ctl.exo.duration.coerceAtLeast(0)
            aspect = ctl.aspect()
            delay(400)
            if (isPlaying && ++tick % 750 == 0) {
                runCatching { Api.heartbeat(curBvid(), curCid(), posMs / 1000) }
            }
        }
    }

    // 控制层自动隐藏
    LaunchedEffect(ctrlVisible, ctrlTick, isPlaying) {
        if (ctrlVisible && isPlaying) { delay(3500); ctrlVisible = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = ctl.exo.currentPosition
            val a = curAid(); val c = curCid()
            if (pos > 5000) Account.AppScope.launch { runCatching { Api.reportHistory(a, c, pos / 1000) } }
            act?.setLandscape(false)
            runCatching { // 恢复系统亮度，不让手势调节泄漏到其他页面
                val lp = act?.window?.attributes
                if (lp != null && lp.screenBrightness >= 0f) { lp.screenBrightness = -1f; act?.window?.attributes = lp }
            }
            ctl.release()
        }
    }

    fun toggleFull() {
        full = !full; ctrlVisible = true; ctrlTick++
        // 与 bili_you 一致：横屏视频进全屏转横屏；竖屏视频全屏=竖屏沉浸
        act?.setLandscape(full && aspect >= 1f)
    }

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

    Column(Modifier.fillMaxSize()) {
        // ===================== 播放区：16:9 黑框，画面原始比例 contain =====================
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (full) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { c -> SurfaceView(c) },
                update = { sv -> ctl.bindSurface(sv) },
                modifier = Modifier.align(Alignment.Center)
                    .then(if (full) Modifier.fillMaxSize().aspectRatio(aspect) else Modifier.aspectRatio(aspect)),
            )
            AndroidView(
                factory = { c ->
                    DanmakuView(c).also {
                        it.timeProvider = { ctl.exo.currentPosition.coerceAtLeast(0) }
                        dmView = it
                    }
                },
                update = { it.paused = !ctl.exo.isPlaying },
                modifier = Modifier.matchParentSize(),
            )
            if (loadingStream && posMs == 0L) {
                CircularProgressIndicator(color = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(34.dp))
            }
            // 中央播放/暂停大按钮（暂停时常驻）
            if (!isPlaying && !loadingStream) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xE6FFFFFF),
                    modifier = Modifier.align(Alignment.Center).size(54.dp)
                        .clip(CircleShape).background(Color(0x55000000))
                        .clickable { ctl.exo.playWhenReady = true; ctrlTick++ })
            }
            // 手势层（在控制条之下）
            var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
            Box(Modifier.matchParentSize().playerGestures(
                ctx = ctx,
                curMs = { ctl.exo.currentPosition.coerceAtLeast(0) },
                durMs = { durMs },
                onTap = { ctrlVisible = !ctrlVisible; ctrlTick++ },
                onDouble = { ctl.exo.playWhenReady = !ctl.exo.isPlaying; ctrlTick++ },
                onSeekStart = { wasPlayingBeforeDrag = ctl.exo.isPlaying; ctl.exo.playWhenReady = false; ctrlVisible = true },
                onSeekPreview = { osd = it },
                onSeekCommit = { to ->
                    ctl.exo.seekTo(to); dmView?.seekTo(to); posMs = to
                    if (wasPlayingBeforeDrag) ctl.exo.playWhenReady = true
                },
                onBrightness = { v ->
                    if (v < 0) osd = null
                    else { osd = "亮度 ${(v * 100).roundToInt()}%"; applyBrightness(ctx, v) }
                },
                onVolume = { f ->
                    if (f < 0) osd = null
                    else { osd = "音量 ${(f * 100).roundToInt()}%"; applyVolume(ctx, f) }
                },
            ))
            // OSD 反馈
            osd?.let {
                Text(it, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
            // 顶部控制条
            if (ctrlVisible) Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Color(0x40000000)).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (full) toggleFull() else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
                }
                Text(curTitle(), color = Color.White, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { moreMenu = true; ctrlTick++ }) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = Color.White)
                    }
                    DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text(if (dmOn) "弹幕：开" else "弹幕：关") }, onClick = {
                            moreMenu = false
                            dmOn = !dmOn; dmView?.show = dmOn
                            scope.launch { Settings.setDanmaku(ctx, dmOn) }
                        })
                        DropdownMenuItem(text = { Text("播放速度：${rate}X") }, onClick = { moreMenu = false; rateDialog = true })
                        DropdownMenuItem(text = { Text("清晰度：${StreamPicker.desc(play ?: PlayResult(), curQn)}") },
                            onClick = { moreMenu = false; qnMenu = true })
                        DropdownMenuItem(text = { Text("发本地弹幕（仅自己可见）") }, onClick = { moreMenu = false; dmDialog = true })
                    }
                }
            }
            // 底部控制条：播放键 + 进度 + 时间 + 全屏
            if (ctrlVisible || !isPlaying) Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0x40000000)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { ctl.exo.playWhenReady = !ctl.exo.isPlaying; ctrlTick++ }) {
                        Icon(if (isPlaying) IconPause else Icons.Filled.PlayArrow, null, tint = Color.White)
                    }
                    Text("${fmtDur(posMs / 1000)}/${fmtDur(durMs / 1000)}",
                        color = Color.White, fontSize = 11.sp)
                    var drag by remember { mutableStateOf<Float?>(null) }
                    Slider(
                        value = drag ?: if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { drag = it },
                        onValueChangeFinished = {
                            drag?.let { f ->
                                val to = (f * durMs).toLong()
                                ctl.exo.seekTo(to); dmView?.seekTo(to); posMs = to
                            }
                            drag = null; ctrlTick++
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { toggleFull() }) {
                        Text(if (full) "退出全屏" else "全屏", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        if (full) return@Column

        // ===================== 简介 / 评论 双 Tab（bili_you 结构） =====================
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary) {
            listOf("简介", "评论 ${fmtCount(view?.stat?.reply ?: 0)}").forEachIndexed { i, t ->
                Tab(selected = tab == i, text = { Text(t, fontSize = 14.sp) }, onClick = { tab = i })
            }
        }
        if (tab == 0) IntroTab(
            view = view, season = season, play = play, curQn = curQn, related = related,
            liked = liked, coined = coined, faved = faved, loadErr = loadErr,
            onUser = onUser, onSeason = onSeason, onVideo = onVideo,
            onLike = {
                if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@IntroTab }
                scope.launch {
                    runCatching { Api.like(curBvid(), !liked); liked = !liked }
                        .onFailure { Toast.makeText(ctx, errText(it), Toast.LENGTH_SHORT).show() }
                }
            },
            onCoin = {
                if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@IntroTab }
                coinDialog = true
            },
            onFav = {
                if (!Account.isLogin) { Toast.makeText(ctx, "请先登录", Toast.LENGTH_SHORT).show(); return@IntroTab }
                favSheet = true
                if (folders.isEmpty()) scope.launch {
                    folders = runCatching { Api.favFolders(Account.uid()) }.getOrDefault(emptyList())
                }
            },
            onShare = {
                scope.launch { runCatching { Api.share(curBvid()) } }
                val i = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://www.bilibili.com/video/${curBvid()} ${curTitle()}")
                }, "分享")
                runCatching { ctx.startActivity(i) }
            },
            onCache = {
                if (season != null) { Toast.makeText(ctx, "番剧暂不支持缓存", Toast.LENGTH_SHORT).show(); return@IntroTab }
                Downloader.enqueue(ctx, curBvid(), curCid(), curTitle(), curQn.coerceAtMost(64))
                Toast.makeText(ctx, "已加入下载队列", Toast.LENGTH_SHORT).show()
            },
            onPart = { i -> pageIdx = i; scope.launch { startStream(0) } },
            onEp = { i -> epIdx = i; scope.launch { startStream(0) } },
            onRetryStream = { scope.launch { startStream(posMs) } },
        )
        else CommentsTab(
            comments = comments, cMore = cMore, cLoading = cLoading,
            replyCount = view?.stat?.reply ?: 0,
            onLoadMore = { scope.launch { loadComments(false) } },
            onSort = { cHot = !cHot; scope.launch { loadComments(true) } },
            hot = cHot,
            onLike = { r ->
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
            onReply = { replyTarget = it.rpid to it.member.uname },
            onSub = { subSheet = it },
        )
        if (tab == 1) CommentInputBar(
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

    // 清晰度选择（从更多菜单进入）
    if (qnMenu) AlertDialog(
        onDismissRequest = { qnMenu = false }, title = { Text("清晰度") },
        text = {
            Column {
                StreamPicker.available(play ?: PlayResult()).forEach { q ->
                    Text(if (q > 80 && (Account.user.value?.vip?.status ?: 0) != 1)
                        "${StreamPicker.desc(play ?: PlayResult(), q)}（需大会员）"
                    else StreamPicker.desc(play ?: PlayResult(), q),
                        fontSize = 15.sp, color = if (q == curQn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable {
                            qnMenu = false
                            if (q != curQn) {
                                maxQn = q
                                scope.launch { Settings.setQuality(ctx, q); startStream(posMs) }
                            }
                        }.padding(vertical = 12.dp))
                }
            }
        }, confirmButton = {},
    )

    // 倍速
    if (rateDialog) AlertDialog(
        onDismissRequest = { rateDialog = false }, title = { Text("播放速度") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).chunked(3).forEach { row ->
                    Column(Modifier.weight(1f)) {
                        row.forEach { r ->
                            OutlinedButton(onClick = {
                                rateDialog = false; rate = r
                                ctl.exo.setPlaybackSpeed(r)
                            }, modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(if (r == rate) "${r}X ✓" else "${r}X", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }, confirmButton = {},
    )

    // 本地弹幕输入
    if (dmDialog) AlertDialog(
        onDismissRequest = { dmDialog = false }, title = { Text("发本地弹幕") },
        text = {
            OutlinedTextField(value = dmLocal, onValueChange = { dmLocal = it },
                placeholder = { Text("仅显示在自己的屏幕上", fontSize = 12.sp) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    dmView?.addLocal(dmLocal); dmLocal = ""; dmDialog = false
                }))
        },
        confirmButton = {
            TextButton(onClick = { dmView?.addLocal(dmLocal); dmLocal = ""; dmDialog = false }) { Text("发送") }
        },
    )

    // 投币
    if (coinDialog) AlertDialog(
        onDismissRequest = { coinDialog = false }, title = { Text("投币") },
        text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1, 2).forEach { n ->
                androidx.compose.material3.Button(onClick = {
                    coinDialog = false
                    scope.launch {
                        runCatching { Api.coin(curBvid(), n); coined = n; if (!liked) { Api.like(curBvid(), true); liked = true } }
                            .onFailure { Toast.makeText(ctx, errText(it), Toast.LENGTH_SHORT).show() }
                    }
                }, modifier = Modifier.padding(vertical = 4.dp)) { Text("$n 币") }
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
                    Icon(Icons.Filled.FavoriteBorder, null,
                        tint = if (inF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("${f.title} (${f.mediaCount})", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }

    subSheet?.let { target -> SubReplySheet(target, aid = curAid(), onDismiss = { subSheet = null }) }
}

// ---------- 手势层：单击/双击 + 横拖 seek（全程±60s，松手提交）+ 左半亮度/右半音量 ----------
private fun Modifier.playerGestures(
    ctx: Context,
    curMs: () -> Long,
    durMs: () -> Long,
    onTap: () -> Unit,
    onDouble: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekPreview: (String?) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    var lastTapAt = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val sx = down.position.x
        val sy = down.position.y
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        var axis = 0 // 0=未定 1=横拖seek 2=亮度 3=音量 9=边缘忽略
        var seekStart = 0L
        var seekTarget = 0L
        var brightBase = -1f
        var volBase = -1f
        while (true) {
            val event = awaitPointerEvent()
            val c = event.changes.firstOrNull() ?: break
            if (!c.pressed) {
                when (axis) {
                    1 -> { onSeekPreview(null); onSeekCommit(seekTarget) }
                    2 -> onBrightness(-1f)
                    3 -> onVolume(-1f)
                    0 -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTapAt < 320) { onDouble(); lastTapAt = 0L }
                        else { lastTapAt = now; onTap() }
                    }
                }
                break
            }
            val dx = c.position.x - sx
            val dy = c.position.y - sy
            if (axis == 0 && (abs(dx) > 18f || abs(dy) > 18f)) {
                axis = when {
                    abs(dx) >= abs(dy) -> 1
                    sx < w * 0.1f || sx > w * 0.9f -> 9
                    else -> if (sx < w / 2) 2 else 3
                }
                when (axis) {
                    1 -> { seekStart = curMs(); seekTarget = seekStart; onSeekStart() }
                    2 -> brightBase = currentBrightness(ctx)
                    3 -> volBase = currentVolume(ctx)
                }
            }
            when (axis) {
                1 -> {
                    seekTarget = (seekStart + dx / w * 60_000f).toLong().coerceIn(0, durMs().coerceAtLeast(1))
                    val delta = seekTarget - seekStart
                    onSeekPreview("${if (delta >= 0) "+" else "-"}${fmtDurRaw(abs(delta) / 1000)}  ${fmtDurRaw(seekTarget / 1000)}")
                }
                2 -> onBrightness((brightBase - dy / h).coerceIn(0.02f, 1f))
                3 -> onVolume((volBase - dy / h).coerceIn(0f, 1f))
            }
            if (axis != 0 && axis != 9) c.consume()
        }
    }
}

private fun currentBrightness(ctx: Context): Float {
    (ctx as? android.app.Activity)?.window?.attributes?.screenBrightness?.let { if (it >= 0f) return it }
    val sys = runCatching {
        android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
    }.getOrDefault(128)
    return (sys / 255f).coerceIn(0.02f, 1f)
}

private fun currentVolume(ctx: Context): Float {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0.5f
}

private fun fmtDurRaw(sec: Long): String {
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun applyBrightness(ctx: Context, v: Float) {
    runCatching {
        val act = ctx as? android.app.Activity ?: return
        val lp = act.window.attributes
        lp.screenBrightness = v
        act.window.attributes = lp
    }
}

private fun applyVolume(ctx: Context, f: Float) {
    runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0)
    }
}

// ---------- 简介 Tab ----------
@Composable
private fun IntroTab(
    view: ViewInfo?, season: Season?, play: PlayResult?, curQn: Int,
    related: List<VideoItem>, liked: Boolean, coined: Int, faved: Boolean, loadErr: String?,
    onUser: (Long) -> Unit, onSeason: (Long) -> Unit, onVideo: (String, Long) -> Unit,
    onLike: () -> Unit, onCoin: () -> Unit, onFav: () -> Unit, onShare: () -> Unit, onCache: () -> Unit,
    onPart: (Int) -> Unit, onEp: (Int) -> Unit, onRetryStream: () -> Unit,
) {
    var descExpanded by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(12.dp)) {
                // UP 主行
                view?.let { v ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { v.owner?.mid?.let(onUser) }) {
                        AsyncImage(model = v.owner?.face?.normUrl(), contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(v.owner?.name ?: "", fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // 标题 + 元信息 + BV
                Text(season?.title ?: (view?.title ?: ""), fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground)
                view?.let { v ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${fmtCount(v.stat?.view ?: 0)}播放 · ${fmtCount(v.stat?.danmaku ?: 0)}弹幕 · " +
                            "${fmtCount(v.stat?.reply ?: 0)}评论 · ${timeAgo(v.pubdate)}发布",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("BV号 ${v.bvid}" + if (v.pages.size > 1) " · ${v.pages.size}P" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (v.desc.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(v.desc, fontSize = 13.sp, maxLines = if (descExpanded) 100 else 6,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { descExpanded = !descExpanded })
                        Text(if (descExpanded) "收起" else "展开", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).clickable { descExpanded = !descExpanded })
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
                if (loadErr != null) Text(loadErr, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable(onClick = onRetryStream))
                Spacer(Modifier.height(12.dp))
                // 操作条：点赞 投币 收藏 分享 缓存（等宽，bili_you 排布）
                Row(Modifier.fillMaxWidth()) {
                    ActBtn("赞", if (liked) "已赞 ${fmtCount((view?.stat?.like ?: 0) + 1)}"
                        else "点赞 ${fmtCount(view?.stat?.like ?: 0)}", active = liked, onClick = onLike)
                    ActBtn("币", if (coined > 0) "已币" else "投币", active = coined > 0, onClick = onCoin)
                    ActBtn("藏", if (faved) "已藏" else "收藏", active = faved, onClick = onFav)
                    ActBtn("享", "分享", active = false, onClick = onShare)
                    ActBtn("存", "缓存", active = false, onClick = onCache)
                }
                // 分 P / 话
                view?.pages?.takeIf { it.size > 1 }?.let { pages ->
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pages.size) { i ->
                            AssistChip(onClick = { onPart(i) },
                                label = { Text("${i + 1} ${pages[i].part.take(12)}", fontSize = 12.sp) })
                        }
                    }
                }
                season?.eps()?.takeIf { it.size > 1 }?.let { eps ->
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(eps.size) { i ->
                            AssistChip(onClick = { onEp(i) },
                                label = { Text(eps[i].part.ifEmpty { "${i + 1}" }.take(10), fontSize = 12.sp) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(8.dp))
            }
        }
        if (related.isNotEmpty()) {
            item { Text("相关推荐", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
            items(related.take(20)) { v ->
                RelatedTile(v) { onVideo(v.bvid, v.aid) }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun RowScope.ActBtn(glyph: String, text: String, active: Boolean, onClick: () -> Unit) {
    val c = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Text(glyph, fontSize = 18.sp, color = c)
        Spacer(Modifier.height(3.dp))
        Text(text, fontSize = 10.sp, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 相关推荐横卡：左封面 16:10 + 右侧标题/UP/数据（bili_you VideoTileItem 规格） */
@Composable
private fun RelatedTile(v: VideoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box {
            Cover(v.pic, Modifier.width(150.dp).height(94.dp), dur = v.duration)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.fillMaxHeight()) {
            Text(v.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            Text(v.owner?.name ?: "", fontSize = 12.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${fmtCount(v.stat?.view ?: 0)}播放 · ${timeAgo(v.pubdate)}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- 评论 Tab ----------
@Composable
private fun CommentsTab(
    comments: List<Reply>, cMore: Boolean, cLoading: Boolean, replyCount: Long, hot: Boolean,
    onLoadMore: () -> Unit, onSort: () -> Unit,
    onLike: (Reply) -> Unit, onReply: (Reply) -> Unit, onSub: (Reply) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("评论 ${fmtCount(replyCount)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSort) { Text(if (hot) "最热" else "最新", fontSize = 13.sp) }
            }
        }
        items(comments) { r ->
            CommentItem(r, onLike = { onLike(r) }, onReply = { onReply(r) }, onSub = { onSub(r) })
        }
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    cLoading -> Text("加载中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    cMore -> TextButton(onClick = onLoadMore) { Text("加载更多评论") }
                    comments.isNotEmpty() -> Text("没有更多评论了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CommentItem(r: Reply, onLike: () -> Unit, onReply: () -> Unit, onSub: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = r.member.face.normUrl(), contentDescription = null,
                modifier = Modifier.size(26.dp).clip(CircleShape))
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
            androidx.compose.material3.Button(onClick = onSend) { Text("发送") }
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
                val moreScope = rememberCoroutineScope()
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
