package com.sammy.fbili.ui.video

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import com.sammy.fbili.ui.common.IconPause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.sammy.fbili.net.BiliError
import com.sammy.fbili.ui.player.PlayerCtl
import kotlinx.coroutines.delay

@Composable
fun LocalPlayScreen(path: String, title: String, aid: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ctl = remember { PlayerCtl(ctx) }
    LaunchedEffect(ctl) { ctl.onError = { msg -> android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show() } }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    LaunchedEffect(path) { ctl.playFile(path) }
    LaunchedEffect(ctl) {
        while (true) {
            playing = ctl.exo.isPlaying
            pos = ctl.exo.currentPosition.coerceAtLeast(0)
            dur = ctl.exo.duration.coerceAtLeast(0)
            delay(400)
        }
    }
    DisposableEffect(Unit) { onDispose { ctl.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { c -> SurfaceView(c) }, update = { ctl.bindSurface(it) },
            modifier = Modifier.matchParentSize())
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { ctl.exo.playWhenReady = !ctl.exo.isPlaying }) {
                Icon(if (playing) IconPause else Icons.Filled.PlayArrow, null, tint = Color.White)
            }
            Slider(
                value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { ctl.exo.seekTo((it * dur).toLong()) },
                modifier = Modifier.weight(1f),
            )
            Text("${pos / 1000 / 60}:${"%02d".format(pos / 1000 % 60)}", color = Color.White, fontSize = 12.sp)
        }
    }
}
