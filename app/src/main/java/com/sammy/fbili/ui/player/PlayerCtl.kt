package com.sammy.fbili.ui.player

import android.content.Context
import android.view.SurfaceView
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

    private val httpFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(Net.UA)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(12000)
        .setReadTimeoutMs(15000)
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com"))

    fun bindSurface(sv: SurfaceView) { exo.setVideoSurface(sv.holder.surface) }

    fun playDash(play: PlayResult, video: Stream, audio: Stream?, startMs: Long) {
        val vUrl = (listOf(video.baseUrl) + video.backupUrl).firstOrNull { it.isNotEmpty() } ?: return
        val vSrc = ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.Builder().setUri(vUrl).setMimeType(MimeTypes.VIDEO_MP4).build())
        val src = if (audio != null && audio.baseUrl.isNotEmpty()) {
            val aSrc = ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(MediaItem.Builder().setUri(audio.baseUrl).setMimeType(MimeTypes.AUDIO_MP4).build())
            MergingMediaSource(vSrc, aSrc)
        } else vSrc
        exo.setMediaSource(src)
        exo.seekTo(startMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun playSingle(url: String, startMs: Long = 0) {
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.seekTo(startMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun playFile(path: String, startMs: Long = 0) {
        val factory = DefaultDataSource.Factory(context)
        val src = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri("file://$path"))
        exo.setMediaSource(src)
        exo.seekTo(startMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun playHls(url: String) {
        val src = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(url))
        exo.setMediaSource(src)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun release() {
        runCatching { exo.release() }
    }
}

/** 清晰度/编码选择：id≤上限取最高档，同档优先 H.264（硬解省电）；音频取最高码率 */
object StreamPicker {
    fun pick(play: PlayResult, maxQn: Int): Pair<Stream, Stream?>? {
        val vids = play.dash?.video ?: return null
        if (vids.isEmpty()) return null
        val allowed = vids.map { it.id.toInt() }.distinct().sortedDescending()
        val qn = allowed.firstOrNull { it <= maxQn } ?: allowed.last()
        val group = vids.filter { it.id.toInt() == qn }
        val v = group.firstOrNull { it.codecid == 7L } ?: group.minByOrNull { it.bandwidth } ?: group[0]
        val a = play.dash?.audio?.maxByOrNull { it.bandwidth }
        return v to a
    }

    fun available(play: PlayResult): List<Int> =
        (play.dash?.video?.map { it.id.toInt() } ?: play.acceptQuality).distinct().sortedDescending()
            .ifEmpty { play.acceptQuality.sortedDescending() }

    fun desc(play: PlayResult, qn: Int): String {
        val i = play.acceptQuality.indexOf(qn)
        return if (i >= 0 && i < play.acceptDescription.size) play.acceptDescription[i] else "${qn}P"
    }
}
