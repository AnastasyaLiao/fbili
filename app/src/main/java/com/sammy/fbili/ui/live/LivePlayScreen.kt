package com.sammy.fbili.ui.live

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sammy.fbili.net.Api
import com.sammy.fbili.ui.player.PlayerCtl
import kotlinx.coroutines.delay

@Composable
fun LivePlayScreen(roomId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ctl = remember { PlayerCtl(ctx) }
    var state by remember { mutableStateOf("正在获取直播流…") }
    var ok by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    LaunchedEffect(ctl) { ctl.onError = { msg -> err = msg } }
    LaunchedEffect(ctl) { while (true) { aspect = ctl.aspect(); delay(800) } }

    LaunchedEffect(roomId, tick) {
        ok = false; err = null; state = "正在获取直播流…"
        try {
            val url = Api.livePlayUrl(roomId)
            if (url.isEmpty()) {
                err = "无法获取直播流（可能是回放/H5 专用房间，或需要更高权限）"
            } else {
                ctl.playHls(url)
                ok = true
            }
        } catch (e: Exception) {
            err = e.message ?: "获取失败"
        }
    }
    DisposableEffect(Unit) { onDispose { ctl.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { c -> SurfaceView(c) }, update = { ctl.bindSurface(it) },
            modifier = Modifier.align(Alignment.Center).aspectRatio(aspect))
        if (!ok) {
            Column(
                Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (err == null) CircularProgressIndicator(color = Color.White)
                else {
                    Text(err ?: "", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { tick++ }) { Text("重试") }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Text("直播间 $roomId", color = Color.White, fontSize = 12.sp)
        }
    }
}
