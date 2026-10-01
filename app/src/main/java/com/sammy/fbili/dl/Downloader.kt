package com.sammy.fbili.dl

import android.content.Context
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.Net
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class Task(
    val id: Int, val bvid: String, val cid: Long, val title: String, val qn: Int,
    var state: Int = 0,   // 0队列 1下载中 2完成 3失败 4取消
    var done: Long = 0, var total: Long = 0, var error: String = "",
)

object Downloader {
    const val QUEUED = 0; const val RUNNING = 1; const val DONE = 2; const val FAILED = 3; const val CANCELED = 4

    val tasks = androidx.compose.runtime.mutableStateListOf<Task>()
    private val seq = AtomicInteger(0)
    private val runningCount = AtomicInteger(0)
    private const val MAX_CONCURRENT = 2

    fun dir(ctx: Context): File {
        // 部分低端机/模拟器外部私有目录不可用，回退到内部存储
        val ext = ctx.getExternalFilesDir(null) ?: File(ctx.filesDir, "files")
        return File(ext, "downloads").apply { mkdirs() }
    }

    fun fileFor(ctx: Context, bvid: String, cid: Long): File = File(dir(ctx), "${bvid}_${cid}.mp4")

    fun enqueue(ctx: Context, bvid: String, cid: Long, title: String, qn: Int) {
        if (tasks.any { it.bvid == bvid && it.cid == cid && it.state <= RUNNING }) return
        val t = Task(seq.incrementAndGet(), bvid, cid, title, qn)
        tasks.add(t)
        pump(ctx, t)
    }

    private fun pump(ctx: Context, t: Task) {
        if (runningCount.get() >= MAX_CONCURRENT) return
        runningCount.incrementAndGet()
        Account.AppScope.launch {
            try {
                t.state = RUNNING
                doDownload(ctx, t)
                t.state = if (t.state == CANCELED) CANCELED else DONE
            } catch (e: Exception) {
                if (t.state != CANCELED) { t.state = FAILED; t.error = e.message ?: "下载失败" }
            } finally {
                runningCount.decrementAndGet()
                tasks.firstOrNull { it.state == QUEUED }?.let { pump(ctx, it) }
            }
        }
    }

    private suspend fun doDownload(ctx: Context, t: Task) {
        val play = Api.playurlMp4(t.bvid, t.cid, t.qn)
        val url = play.durl.firstOrNull()?.url ?: run {
            // 没有 mp4 单流时退化为 DASH 视频流（无独立音频合并，仅作保底可看文件）
            val s = com.sammy.fbili.ui.player.StreamPicker.pick(play, t.qn)
            s?.first?.baseUrl?.takeIf { s.first == s.second || s.second == null } ?: error("该视频不支持缓存，请换个清晰度")
        }
        val f = fileFor(ctx, t.bvid, t.cid)
        val tmp = File(f.parentFile, f.name + ".part")
        val req = Request.Builder().url(url).header("User-Agent", Net.UA)
            .header("Referer", "https://www.bilibili.com").build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            t.total = resp.body?.contentLength() ?: 0
            tmp.outputStream().use { out ->
                resp.body?.byteStream()?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (t.state == CANCELED) { t.total = 0; t.done = 0; return }
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        t.done += n
                    }
                }
            }
        }
        if (t.state == CANCELED) { t.done = 0; t.total = 0; tmp.delete(); return }
        tmp.renameTo(f)
    }

    fun cancel(t: Task) {
        if (t.state <= RUNNING) t.state = CANCELED
    }
}
