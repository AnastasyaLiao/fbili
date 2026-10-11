package com.sammy.fbili.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.dl.Downloader
import kotlinx.coroutines.delay

/**
 * 应用内缓存进度悬浮窗：不申请 SYSTEM_ALERT_WINDOW，只是盖在自己窗口最上层的一张小卡。
 * 有任务时出现，4.5 秒后自动收成角标，点角标重新展开，可上下拖动，点卡片进缓存页。
 */
@Composable
fun DownloadBubble(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val active = Downloader.tasks.filter { it.state <= Downloader.RUNNING }
    val lastDone = Downloader.tasks.lastOrNull { it.state == Downloader.DONE }
    var expanded by remember { mutableStateOf(true) }
    var hintDone by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active.size) {
        if (active.isNotEmpty()) { expanded = true; delay(4500); expanded = false }
    }
    LaunchedEffect(lastDone?.id) {
        if (lastDone != null && active.isEmpty()) { hintDone = true; delay(5000); hintDone = false }
    }
    if (active.isEmpty() && !hintDone) return

    val total = active.sumOf { it.total }
    val frac = if (total > 0)
        (active.sumOf { it.done }.toFloat() / total).coerceIn(0f, 1f) else 0f
    val pct = (frac * 100).toInt()
    val label = when {
        active.isEmpty() -> "已完成"
        total <= 0 -> "排队"
        else -> "${pct}%"
    }
    val detail = if (active.isEmpty()) "" else {
        val run = active.count { it.state == Downloader.RUNNING }
        val wait = active.size - run
        val t = active.firstOrNull { it.state == Downloader.RUNNING } ?: active.first()
        t.title + if (wait > 0) "　另有 $wait 个排队" else ""
    }
    val drag = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { _, dy ->
            dragY = (dragY + dy).coerceIn(-(size.height - 220f).coerceAtLeast(0f), 0f)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (expanded && active.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 18.dp)
                    .graphicsLayer { translationY = dragY }
                    .then(drag)
                    .width(238.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { expanded = false; onOpen() }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("离线缓存", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text("　–", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expanded = false })
                }
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp))
                Text(detail, fontSize = 11.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp)
                    .graphicsLayer { translationY = dragY }
                    .then(drag)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { if (active.isEmpty()) onOpen() else expanded = true },
                contentAlignment = Alignment.Center
            ) { Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
