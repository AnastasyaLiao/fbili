package com.sammy.fbili.ui.login

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.Net
import com.sammy.fbili.net.num
import com.sammy.fbili.net.str
import kotlinx.coroutines.delay
import android.widget.Toast

private val neededCookies = setOf(
    "SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid", "buvid3", "buvid4"
)

private fun makeQr(content: String, sizePx: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) for (y in 0 until sizePx) {
        bmp.setPixel(x, y, if (matrix[x, y]) AColor.BLACK else AColor.WHITE)
    }
    bmp
}.getOrNull()

@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary) {
            listOf("扫码登录", "手机号登录").forEachIndexed { i, t ->
                Tab(selected = tab == i, text = { Text(t) }, onClick = { tab = i })
            }
        }
        if (tab == 0) QrLogin(onDone) else WebLogin(onDone)
    }
}

@Composable
private fun QrLogin(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("正在生成二维码…") }
    var expired by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    // 协程轮询：页面退出自动停止；单次网络异常只重试不崩溃——这正是原项目闪退的修法
    LaunchedEffect(tick) {
        expired = false
        try {
            val d = Api.qrGenerate()
            val url = d.str("url")
            val key = d.str("qrcode_key")
            if (url.isEmpty()) { status = "获取二维码失败，请重试"; expired = true; return@LaunchedEffect }
            bmp = makeQr(url, 460)
            status = "用哔哩哔哩 App 扫码（180 秒内有效）"
            while (!expired) {
                delay(2000)
                try {
                    val p = Api.qrPoll(key)
                    when (p.num("code").toInt()) {
                        0 -> {
                            Account.persistCookies(Net.cookies)
                            runCatching { Account.refreshNav() }
                            Toast.makeText(ctx, "登录成功", Toast.LENGTH_SHORT).show()
                            onDone()
                            return@LaunchedEffect
                        }
                        86038 -> { status = "二维码已过期，点击重新生成"; expired = true }
                        86101 -> status = "等待扫码…"
                        86090 -> status = "已扫码，请在手机上确认"
                        else -> status = p.str("message").ifEmpty { "等待扫码…" }
                    }
                } catch (e: Exception) {
                    status = "网络波动，自动重试中…"   // 不中断轮询
                }
            }
        } catch (e: Exception) {
            status = e.message ?: "二维码获取失败"; expired = true
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (bmp != null && !expired) {
                Image(bitmap = bmp!!.asImageBitmap(), contentDescription = "登录二维码",
                    modifier = Modifier.size(230.dp))
            } else {
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    if (!expired) CircularProgressIndicator()
                    else Text("点击重试", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp,
                        modifier = Modifier.clickable { tick++ })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("备选：切到「手机号登录」标签，走官方页面（含短信验证码）",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WebLogin(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var grabbed by remember { mutableStateOf(false) }

    fun harvest(): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()   // 强制把 WebView 内存 cookie 落盘，再读
        val raw = cm.getCookie("https://www.bilibili.com")
            ?: cm.getCookie("https://m.bilibili.com") ?: return false
        val map = raw.split(";").mapNotNull {
            val kv = it.trim().split("=", limit = 2)
            if (kv.size == 2 && kv[0] in neededCookies) kv[0] to kv[1] else null
        }.toMap()
        android.util.Log.d("fbili-auth", "web harvest got=${map.keys}")
        if (map["SESSDATA"] == null) return false
        Net.cookies.putAll(map)
        Account.persistCookies(Net.cookies)
        return true
    }

    fun finishLogin() {
        if (grabbed) return
        grabbed = true
        Account.AppScope.launch {
            runCatching { Account.refreshNav() }
            ctx.postMain {
                Toast.makeText(ctx, "登录成功", Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
    }

    Column {
        AndroidView(
            factory = { c ->
                WebView(c).apply {
                    setBackgroundColor(AColor.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Net.UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!grabbed && harvest()) finishLogin()
                        }
                    }
                    loadUrl(
                        "https://passport.bilibili.com/h5-app/passport-login" +
                            "?mode=1&go_url=https%3A%2F%2Fm.bilibili.com%2F"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp),
        )
        Text(
            "已完成登录？点这里进入",
            color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                if (harvest()) finishLogin()
                else Toast.makeText(ctx, "还没检测到登录状态，请先在页面完成登录", Toast.LENGTH_SHORT).show()
            }.padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
    // cookie 写入可能滞后于页面加载完成：轮询兜底 60 秒
    LaunchedEffect(Unit) {
        while (!grabbed) {
            delay(1500)
            if (harvest()) finishLogin()
        }
    }
}

private fun android.content.Context.postMain(block: () -> Unit) {
    (android.os.Handler(android.os.Looper.getMainLooper())).post(block)
}
