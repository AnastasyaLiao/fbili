package com.sammy.fbili.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.FBiliApp
import com.sammy.fbili.dl.Downloader
import com.sammy.fbili.dl.Task
import com.sammy.fbili.ui.common.ListRow
import java.io.File

private fun fmtSize(b: Long): String = when {
    b >= 1_000_000_000 -> "%.2fGB".format(b / 1e9)
    b >= 1_000_000 -> "%.1fMB".format(b / 1e6)
    else -> "${b / 1000}KB"
}

@Composable
fun DownloadsScreen(onPlay: (String, String, Long) -> Unit, onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    val files = remember(tick) { Downloader.dir(FBiliApp.ctx).listFiles()?.toList() ?: emptyList() }

    LazyColumn(Modifier.fillMaxSize()) {
        val running = Downloader.tasks.filter { it.state <= com.sammy.fbili.dl.Downloader.RUNNING }
        if (running.isNotEmpty()) {
            item {
                Text("下载中", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(14.dp))
            }
            items(running) { t -> TaskRow(t) }
        }
        item {
            Text("本地视频", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(14.dp))
        }
        items(files.filter { it.name.endsWith(".mp4") }) { f ->
            ListRow(
                title = f.name.substringBeforeLast(".mp4"),
                cover = "",
                sub = fmtSize(f.length()),
                onClick = {
                    val bvid = f.name.substringBefore('_')
                    onPlay(f.absolutePath, f.name, if (bvid.isNotEmpty()) 0 else 0)
                },
            )
            Row(Modifier.fillMaxWidth().padding(start = 26.dp, bottom = 4.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { f.delete(); tick++ }) { Text("删除", fontSize = 12.sp) }
            }
        }
        if (files.none { it.name.endsWith(".mp4") }) {
            item {
                Text("在视频页点「缓存」即可离线", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun TaskRow(t: Task) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(t.title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            when (t.state) {
                0 -> "排队中"
                1 -> if (t.total > 0) "下载中 ${fmtSize(t.done)}/${fmtSize(t.total)}" else "下载中 ${fmtSize(t.done)}"
                2 -> "已完成"
                3 -> "失败：${t.error}"
                else -> "已取消"
            },
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (t.state == 1 && t.total > 0) {
            LinearProgressIndicator(progress = { (t.done.toFloat() / t.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Row {
            Spacer(Modifier.weight(1f))
            if (t.state <= 1) {
                TextButton(onClick = { Downloader.cancel(t) }) { Text("取消", fontSize = 12.sp) }
            }
            if (t.state >= 2) {
                TextButton(onClick = { Downloader.tasks.remove(t) }) { Text("移除", fontSize = 12.sp) }
            }
        }
    }
}

