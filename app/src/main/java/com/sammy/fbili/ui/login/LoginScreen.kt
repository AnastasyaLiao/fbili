package com.sammy.fbili.ui.login

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.Api
import com.sammy.fbili.net.BiliError
import com.sammy.fbili.net.Net
import com.sammy.fbili.net.NetError
import com.sammy.fbili.net.num
import com.sammy.fbili.net.obj
import com.sammy.fbili.net.str
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        if (tab == 0) QrLogin(onDone) else SmsLogin(onDone)
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
            Text("不便扫码？切到「手机号登录」，用短信验证码登录",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 极验滑块参数：challenge 一次性且约 2 分钟过期，所以每次点「获取验证码」都重新取 */
private data class GeetestSpec(val gt: String, val challenge: String, val token: String)

private sealed class GeeResult {
    data class Ok(val validate: String, val seccode: String, val challenge: String) : GeeResult()
    data class Fail(val msg: String) : GeeResult()
}

@Composable
private fun SmsLogin(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tel by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("输入手机号，获取短信验证码后登录") }
    var busy by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    var captchaKey by remember { mutableStateOf("") }
    var spec by remember { mutableStateOf<GeetestSpec?>(null) }

    LaunchedEffect(left) { while (left > 0) { delay(1000); left-- } }

    fun done(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        onDone()
    }

    fun sendSms(sp: GeetestSpec, r: GeeResult.Ok) {
        busy = true
        tip = "正在发送验证码…"
        scope.launch {
            try {
                val d = Api.smsSend(tel, sp.token, r.challenge.ifEmpty { sp.challenge },
                    r.validate, r.seccode)
                captchaKey = d.str("captcha_key")
                left = 60
                tip = "验证码已发送，请查收短信"
            } catch (e: Exception) {
                tip = smsErrorText(e)
            } finally { busy = false }
        }
    }

    val sp = spec
    if (sp != null) {
        GeetestVerify(sp) { r ->
            spec = null
            when (r) {
                is GeeResult.Ok -> sendSms(sp, r)
                is GeeResult.Fail -> tip = r.msg
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = tel,
            onValueChange = { v -> if (v.length <= 11 && v.all { it.isDigit() }) tel = v },
            label = { Text("手机号") },
            prefix = { Text("+86 ") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) code = v },
                label = { Text("短信验证码") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = !busy && left == 0,
                onClick = {
                    if (tel.length != 11) { tip = "请输入 11 位手机号"; return@TextButton }
                    busy = true
                    tip = "正在加载安全验证…"
                    scope.launch {
                        try {
                            val d = Api.captchaData()
                            val g = d.obj("geetest")
                            val gt = g?.str("gt").orEmpty()
                            val challenge = g?.str("challenge").orEmpty()
                            val token = d.str("token")
                            if (d.str("type") != "geetest" || gt.isEmpty() || challenge.isEmpty() || token.isEmpty()) {
                                tip = "B站要求额外核验，请改用扫码登录"
                            } else spec = GeetestSpec(gt, challenge, token)
                        } catch (e: Exception) {
                            tip = smsErrorText(e)
                        } finally { busy = false }
                    }
                },
                modifier = Modifier.width(112.dp)
            ) {
                Text(if (left > 0) "${left}s 后重发" else "获取验证码", fontSize = 13.sp)
            }
        }
        Button(
            onClick = {
                when {
                    tel.length != 11 -> tip = "请输入 11 位手机号"
                    code.length < 4 -> tip = "请输入短信验证码"
                    captchaKey.isEmpty() -> tip = "请先获取短信验证码"
                    else -> {
                        busy = true
                        tip = "正在登录…"
                        scope.launch {
                            try {
                                val d = Api.smsLogin(tel, code, captchaKey)
                                val msg = d.str("message")
                                // B站对异常登录只回 message（如要求密码验证），此时不能当成功
                                if (d.str("url").isEmpty() && msg.isNotEmpty()) {
                                    tip = msg
                                } else {
                                    Account.persistCookies(Net.cookies)
                                    runCatching { Account.refreshNav() }
                                    done("登录成功")
                                }
                            } catch (e: Exception) {
                                tip = smsErrorText(e)
                            } finally { busy = false }
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text(if (busy) "请稍候…" else "登录", fontSize = 15.sp) }
        Text(tip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 只承载极验滑块（ES5 页面，Android 5.0 起的内核都能跑）；结果用自定义 scheme 回调，不放开任何跳转 */
@Composable
private fun GeetestVerify(spec: GeetestSpec, onResult: (GeeResult) -> Unit) {
    val ctx = LocalContext.current
    val page = remember {
        runCatching {
            ctx.assets.open("geetest.html").bufferedReader().use { it.readText() }
                .replace("__GT__", spec.gt)
                .replace("__CH__", spec.challenge)
        }.getOrNull()
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { onResult(GeeResult.Fail("已取消验证")) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("安全验证", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        if (page == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("验证页面加载失败，请返回重试", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AndroidView(
                factory = { c ->
                    var settled = false
                    fun settle(r: GeeResult) {
                        if (settled) return
                        settled = true
                        c.postMain { onResult(r) }
                    }
                    WebView(c).apply {
                        setBackgroundColor(AColor.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        // 极验按 UA 里的 "wv" 标记判定为 WebView 环境，会静默不渲染滑块
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            private fun handle(url: String?): Boolean {
                                val u = runCatching { Uri.parse(url ?: return false) }.getOrNull()
                                    ?: return false
                                if (u.scheme != "fbili-captcha") return u.scheme != "http" && u.scheme != "https"
                                val validate = u.getQueryParameter("validate")
                                settle(
                                    if (u.host == "ok" && !validate.isNullOrEmpty())
                                        GeeResult.Ok(validate,
                                            u.getQueryParameter("seccode").orEmpty(),
                                            u.getQueryParameter("challenge").orEmpty())
                                    else GeeResult.Fail(
                                        Uri.decode(u.getQueryParameter("msg").orEmpty()).ifEmpty { "验证未完成" })
                                )
                                return true
                            }
                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                handle(url)
                            override fun shouldOverrideUrlLoading(view: WebView?, r: WebResourceRequest?): Boolean =
                                handle(r?.url?.toString())
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                android.util.Log.w("fbili-auth", "geetest err=${error?.errorCode} " +
                                    "url=${request?.url} main=${request?.isForMainFrame}")
                                if (request?.isForMainFrame == true) settle(GeeResult.Fail("验证组件加载失败，请稍后重试"))
                            }
                        }
                        loadDataWithBaseURL("https://static.geetest.com/", page, "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/**
 * B站登录接口的错误文案。B站这些接口回的就是中文人话，且码表会随版本变（实测 1002 已不是
 * "手机号格式错误"那档），所以一律优先原样用它给的 message，码表只在它没给文案时兜底。
 */
private fun smsErrorText(e: Throwable): String {
    android.util.Log.w("fbili-auth", "sms step failed: ${e::class.simpleName} " +
        "code=${(e as? BiliError)?.code} msg=${e.message}")
    return when (e) {
        is NetError -> "网络连接失败，请检查网络后重试"
        is BiliError -> e.message?.takeIf { it.isNotEmpty() && !it.startsWith("接口错误") }
            ?: when (e.code) {
                -105 -> "人机验证已过期，请重新验证"
                -352, -412 -> "B站风控拦截了当前网络，请稍后重试或更换网络"
                1002, 1000 -> "手机号格式不正确"
                1003 -> "验证码已发送，请稍后再试"
                1006, 1007 -> "短信验证码不正确或已过期，请重新获取"
                1025 -> "该手机号无法短信登录，请使用扫码登录"
                2400, 2406 -> "人机验证异常，请重新验证"
                86203 -> "今日短信发送次数已达上限"
                else -> "B站返回错误（${e.code}）"
            }
        else -> e.message?.takeIf { it.isNotEmpty() } ?: "操作失败，请重试"
    }
}

private fun android.content.Context.postMain(block: () -> Unit) {
    (android.os.Handler(android.os.Looper.getMainLooper())).post(block)
}
