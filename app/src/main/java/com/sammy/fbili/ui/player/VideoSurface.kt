package com.sammy.fbili.ui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 视频画面层。surface 只在创建/销毁时各绑一次——若放进 AndroidView 的 update{}，
 * 播放状态每 400ms 刷新一次就会 setVideoSurface 一次，Media3 每次都重配解码器，
 * 画面永远停在第 0 帧、进度卡在 0:00（实测 pos=0 / state=READY / isPlaying=true）。
 */
@Composable
fun VideoSurface(ctl: PlayerCtl, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { c ->
            SurfaceView(c).also { sv ->
                sv.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) { ctl.exo.setVideoSurface(h.surface) }
                    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) { ctl.exo.clearVideoSurface(h.surface) }
                })
            }
        },
    )
}
