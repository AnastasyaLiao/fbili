package com.sammy.fbili.ui.danmaku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import kotlin.math.ceil

data class DanmakuItem(val timeMs: Long, val mode: Int, val color: Int, val text: String)

object DanmakuRepo {
    private val cache = object : LinkedHashMap<Long, List<DanmakuItem>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<DanmakuItem>>?) = size > 4
    }

    fun cached(cid: Long): List<DanmakuItem>? = synchronized(cache) { cache[cid] }

    fun put(cid: Long, list: List<DanmakuItem>) = synchronized(cache) { cache[cid] = list }

    private val pattern = Regex("<d p=\"([^\"]+)\">(.*?)</d>", RegexOption.DOT_MATCHES_ALL)

    fun parse(xml: String): List<DanmakuItem> {
        val out = ArrayList<DanmakuItem>(2048)
        for (m in pattern.findAll(xml)) {
            val parts = m.groupValues[1].split(",")
            if (parts.size < 4) continue
            val t = (parts[0].toDoubleOrNull() ?: 0.0) * 1000
            val mode = parts[1].toIntOrNull() ?: 1
            var color = parts[3].toLongOrNull()?.toInt() ?: 0xFFFFFF
            color = color or 0xFF000000.toInt()
            val text = m.groupValues[2]
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
            if (text.isNotBlank()) out.add(DanmakuItem(t.toLong(), mode, color, text.trim()))
        }
        out.sortBy { it.timeMs }
        // 防御超大数据：低配机每 10 秒采样上限
        if (out.size > 6000) {
            val keep = ArrayList<DanmakuItem>(6000)
            val step = out.size / 6000 + 1
            for (i in out.indices step step) keep.add(out[i])
            return keep
        }
        return out
    }
}

/** 轻量自绘弹幕：单线程 Canvas、对象复用、无第三方库，低端机友好 */
class DanmakuView(context: Context) : View(context) {

    private class Active {
        var text = ""; var x = 0f; var y = 0f; var speed = 0f; var color = 0xFFFFFFFF.toInt()
        var width = 0f; var mode = 1; var fixed = false; var startMs = 0L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(2f, 1f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val strokePaint = Paint(paint).apply { style = Paint.Style.STROKE }

    private val pool = ArrayList<Active>(64)
    private val active = ArrayList<Active>(64)
    private var items: List<DanmakuItem> = emptyList()
    private var pointer = 0
    var show = true
        set(v) { field = v; if (!v) active.clear(); invalidate() }
    var timeProvider: () -> Long = { 0L }
    var paused = false
    private var lastFrameNs = 0L
    private var density = resources.displayMetrics.density
    private val textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
    private val laneH = textSize * 1.45f
    private var laneCount = 10

    private var attached = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!attached || !isShown) return
            val dt = if (lastFrameNs == 0L) 0f else (frameTimeNanos - lastFrameNs) / 1_000_000_000f
            lastFrameNs = frameTimeNanos
            advance(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        attached = false // doFrame 不再自续期；不依赖 removeFrameCallbacks（新 API）
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        laneCount = maxOf(1, ceil(h / laneH).toInt() - 2)
    }

    fun setData(list: List<DanmakuItem>) {
        items = list; pointer = 0; active.forEach { pool.add(it) }; active.clear()
    }

    /** 拖动进度后重定位 */
    fun seekTo(ms: Long) {
        pointer = items.binarySearchIndex(ms)
        active.forEach { pool.add(it) }; active.clear()
    }

    fun addLocal(text: String) {
        if (text.isBlank() || !show) return
        spawn(DanmakuItem(timeProvider(), 1, 0xFF00FFFF.toInt() or 0xFF000000.toInt(), text))
    }

    private fun advance(dt: Float) {
        if (!show) return
        val now = timeProvider()
        if (!paused) {
            // 出屏 2 秒内的补发（避免卡顿后弹幕堆一屏）
            while (pointer < items.size && items[pointer].timeMs <= now && items[pointer].timeMs > now - 2000) {
                spawn(items[pointer]); pointer++
            }
            while (pointer < items.size && items[pointer].timeMs <= now - 2000) pointer++
        }
        val it = active.iterator()
        while (it.hasNext()) {
            val a = it.next()
            if (!paused) a.x -= a.speed * dt
            if (a.x < -a.width || (a.fixed && now - a.startMs > 5000)) {
                pool.add(a); it.remove()
            }
        }
    }

    private fun spawn(item: DanmakuItem) {
        if (active.size >= 48 || pool.isEmpty() && active.size >= 24) return
        val a = pool.removeLastOrNull() ?: Active()
        a.text = item.text
        a.color = item.color
        a.mode = item.mode
        a.startMs = timeProvider()
        a.fixed = item.mode == 5 || item.mode == 4
        paint.textSize = textSize
        a.width = paint.measureText(item.text)
        if (a.fixed) {
            a.x = (width - a.width) / 2f
            a.y = if (item.mode == 4) laneH else (height - laneH * 2)
        } else {
            a.x = width.toFloat()
            a.speed = (width / 4.5f).coerceIn(120f, 400f)
            a.y = (0 until laneCount).randomOrNull()?.let { it * laneH + laneH * 0.2f } ?: return
        }
        active.add(a)
    }

    override fun onDraw(canvas: Canvas) {
        if (!show) return
        paint.textSize = textSize
        strokePaint.textSize = textSize
        strokePaint.strokeWidth = 3f
        for (a in active) {
            paint.color = a.color
            strokePaint.color = Color.argb(120, 0, 0, 0)
            canvas.drawText(a.text, a.x, a.y + textSize, strokePaint)
            canvas.drawText(a.text, a.x, a.y + textSize, paint)
        }
    }
}

fun <T> List<T>.binarySearchIndexBy(keySelector: (T) -> Long, target: Long): Int {
    var lo = 0; var hi = size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (keySelector(this[mid]) < target) lo = mid + 1 else hi = mid
    }
    return lo
}

private fun List<DanmakuItem>.binarySearchIndex(ms: Long): Int =
    binarySearchIndexBy({ it.timeMs }, ms)
