package com.sammy.fbili.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.sammy.fbili.net.Net
import com.sammy.fbili.net.PlayResult
import com.sammy.fbili.net.Stream

/** 统一播放器封装：DASH 合并流 / 单文件 mp4 / 本地文件 / HLS，全部系统硬解 */
class PlayerCtl(private val context: Context) {

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(5000)
        .setSeekForwardIncrementMs(5000)
        .build()

    /** 播放失败对外回调（主线程），UI 层用来弹提示；同时落 logcat 便于远程排障 */
    var onError: ((String) -> Unit)? = null

    init {
        exo.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val cause = error.errorCauseMessage()
                android.util.Log.e("fbili-play", "${error.errorCodeName} cause=${error.cause?.message} line=$line", error)
                // 先换 B站给的备用线路，全部试过才向 UI 报错（mcdn 边缘节点常连上就断）
                if (line < vUrls.lastIndex) { line++; startCurrent(); return }
                mainHandler.post { onError?.invoke("播放失败：$cause") }
            }
        })
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * 线路候选。B站 playurl 的 base_url 常是 mcdn P2P 边缘节点（实测 host 形如
     * *.edge.mountaintoys.cn:4483 / *.mcdn.bilivideo.cn:8082），移动网络上连上不吐数据的概率
     * 明显高于 backup_url 里的 upos 镜像，所以取流失败或卡住时按顺序换线。
     */
    private var vUrls: List<String> = emptyList()
    private var aUrls: List<String> = emptyList()
    private var line = 0
    private var startAt = 0L
    private var merged = false

    private var lastBuf = -1L

    /**
     * 卡线看门狗：每 8 秒看一次，只有"没在播且缓冲区两次之间没有增长"才判定为线路死了。
     * 不能只看"没在播"就换线——低端机/满载机器冷启动本来就要缓冲好几秒，
     * 那样会把正在正常起播的流反复打断，结果永远播不出来（实测踩过）。
     */
    private val stall = object : Runnable {
        override fun run() {
            val buf = exo.bufferedPosition
            if (!exo.isPlaying && exo.playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                if (lastBuf >= 0 && buf <= lastBuf && line < vUrls.lastIndex) {
                    android.util.Log.w("fbili-play", "line $line 无进展($buf)，切备用线路")
                    line++; startCurrent(); return
                }
                lastBuf = buf
            } else lastBuf = -1
            armStall()
        }
    }

    private fun armStall() {
        mainHandler.removeCallbacks(stall)
        mainHandler.postDelayed(stall, 8000)
    }

    private fun startCurrent() {
        val vUrl = vUrls.getOrNull(line) ?: return
        val vSrc = ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
            MediaItem.Builder().setUri(vUrl)
                .setMimeType(if (merged) MimeTypes.APPLICATION_MP4 else MimeTypes.VIDEO_MP4).build())
        val aUrl = if (merged) null else aUrls.getOrElse(line) { aUrls.lastOrNull() }
        val src = if (aUrl != null) MergingMediaSource(vSrc,
                ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(aUrl).setMimeType(MimeTypes.AUDIO_MP4).build()))
            else vSrc
        exo.setMediaSource(src)
        exo.seekTo(startAt)
        exo.prepare()
        exo.playWhenReady = true
        lastBuf = -1
        armStall()
    }

    private fun androidx.media3.common.PlaybackException.errorCauseMessage(): String = when (errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            if (cause?.message?.contains("403") == true) "视频源 403（网络或地区限制）" else "视频源 HTTP 错误"
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "连不上视频服务器，检查网络"
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "连接视频服务器超时"
        androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "直播流断开"
        else -> errorCodeName
    }

    private val httpFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(12000)
        .setReadTimeoutMs(15000)
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com"))


    /** 视频显示比例（含像素宽高比校正）；未出画面时按 16:9 */
    fun aspect(): Float {
        val vs = exo.videoSize
        if (vs.width <= 0 || vs.height <= 0) return 16f / 9f
        val par = if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f
        return (vs.width * par) / vs.height
    }

    fun playDash(play: PlayResult, video: Stream, audio: Stream?, startMs: Long) {
        merged = false
        vUrls = (listOf(video.baseUrl) + video.backupUrl).filter { it.isNotEmpty() }
        aUrls = audio?.let { (listOf(it.baseUrl) + it.backupUrl).filter { u -> u.isNotEmpty() } }
            ?: emptyList()
        line = 0; startAt = startMs
        if (vUrls.isNotEmpty()) startCurrent()
    }

    /** 单文件 mp4（durl）：必须走同一个带桌面 UA + Referer 的数据源，
     *  否则 B站 CDN 直接 403（实测 ExoPlayer 默认的 Dalvik UA 拿不到流） */
    fun playSingle(url: String, startMs: Long = 0) {
        merged = true; vUrls = listOf(url); aUrls = emptyList(); line = 0; startAt = startMs
        startCurrent()
    }

    fun playFile(path: String, startMs: Long = 0) {
        vUrls = emptyList(); aUrls = emptyList(); line = 0   // 本地文件没有备用线路可切
        val factory = DefaultDataSource.Factory(context)
        val src = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri("file://$path"))
        exo.setMediaSource(src)
        exo.seekTo(startMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun playHls(url: String) {
        vUrls = emptyList(); aUrls = emptyList(); line = 0
        val src = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(url))
        exo.setMediaSource(src)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun release() {
        mainHandler.removeCallbacks(stall)
        runCatching { exo.release() }
    }
}

/** 清晰度/编码选择：id≤上限取最高档，优先 H.264（低端机硬解可靠省电）；音频取最高码率 */
object StreamPicker {
    fun pick(play: PlayResult, maxQn: Int): Pair<Stream, Stream?>? {
        val vids = play.dash?.video ?: return null
        if (vids.isEmpty()) return null
        val allowed = vids.map { it.id.toInt() }.distinct().sortedDescending()
        val qn = allowed.firstOrNull { it <= maxQn } ?: allowed.last()
        val a = play.dash?.audio?.maxByOrNull { it.bandwidth }
        // 从目标档往下找第一档"有 H.264 版本"的：只有 HEVC/AV1 的档位对老机型是黑屏/卡顿来源
        for (q in (listOf(qn) + allowed.filter { it < qn })) {
            val avc = vids.filter { it.id.toInt() == q }.firstOrNull { it.codecid == 7L }
            if (avc != null) return avc to a
        }
        // 整片没有 H.264：取最低码率的一档，别拿最高码率的 AV1 去压低端机
        val any = vids.filter { it.id.toInt() == allowed.last() }.minByOrNull { it.bandwidth }
            ?: vids.minByOrNull { it.bandwidth } ?: return null
        return any to a
    }

    fun available(play: PlayResult): List<Int> =
        (play.dash?.video?.map { it.id.toInt() } ?: play.acceptQuality).distinct().sortedDescending()
            .ifEmpty { play.acceptQuality.sortedDescending() }

    fun desc(play: PlayResult, qn: Int): String {
        val i = play.acceptQuality.indexOf(qn)
        return if (i >= 0 && i < play.acceptDescription.size) play.acceptDescription[i] else "${qn}P"
    }
}
