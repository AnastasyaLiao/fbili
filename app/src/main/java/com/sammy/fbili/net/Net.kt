package com.sammy.fbili.net

import android.content.Context
import com.sammy.fbili.data.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BiliError(val code: Int, override val message: String) : Exception(message)
class NetError(cause: Throwable?) : Exception("网络连接失败：${cause?.message ?: "未知错误"}")

object Net {
    const val API = "https://api.bilibili.com"
    const val PASSPORT = "https://passport.bilibili.com"
    const val APP = "https://app.bilibili.com"
    const val SSEARCH = "https://s.search.bilibili.com"
    const val LIVE = "https://api.live.bilibili.com"
    const val COMMENT = "https://comment.bilibili.com"
    const val UA =
        "Mozilla/5.0 (Linux; Android 13; PHU110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.119 Mobile Safari/537.36"

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    val cookies = ConcurrentHashMap<String, String>()

    lateinit var client: OkHttpClient
        private set

    fun init(ctx: Context) {
        client = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
                    for (c in list) {
                        if (c.name in cookieNames) cookies[c.name] = c.value
                    }
                    Account.persistCookies(cookies)
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    cookies.mapNotNull { (k, v) ->
                        runCatching {
                            Cookie.Builder().name(k).value(v)
                                .domain(".bilibili.com").httpOnly().build()
                        }.getOrNull()
                    }
            })
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val cookieNames = setOf(
        "SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid",
        "buvid3", "buvid4", "b_nut", "fingerprint"
    )

    private fun buildGet(base: String, path: String, params: Map<String, String>, referer: String?): Request {
        val url = (base + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> url.addQueryParameter(k, v) }
        return Request.Builder().url(url.build()).get()
            .header("User-Agent", UA)
            .header("Referer", referer ?: "https://m.bilibili.com")
            .build()
    }

    private fun buildPost(base: String, path: String, params: Map<String, String>, referer: String?): Request {
        val form = FormBody.Builder()
        params.forEach { (k, v) -> form.add(k, v) }
        return Request.Builder().url(base + path).post(form.build())
            .header("User-Agent", UA)
            .header("Referer", referer ?: "https://m.bilibili.com")
            .header("Origin", referer?.trimEnd('/', ':', '/') ?: "https://m.bilibili.com")
            .build()
    }

    private suspend fun raw(req: Request): String = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        repeat(3) { i ->
            try {
                client.newCall(req).execute().use { r ->
                    val body = r.body?.string() ?: ""
                    if (r.code in 200..299) return@withContext body
                    last = IOException("HTTP ${r.code}")
                }
            } catch (e: IOException) {
                last = e
            }
            if (i < 2) delay(500L * (i + 1))
        }
        throw NetError(last)
    }

    /** 带 {code,message,data} 信封的接口；重试与统一错误已内置。post 非空时走表单提交 */
    suspend fun api(
        base: String, path: String, params: Map<String, String> = emptyMap(),
        post: Map<String, String>? = null, referer: String? = null,
    ): kotlinx.serialization.json.JsonElement {
        val req = if (post != null) buildPost(base, path, post, referer)
        else buildGet(base, path, params, referer)
        return unwrap(raw(req))
    }

    private fun unwrap(text: String): JsonElement {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw BiliError(-1, "返回数据解析失败")
        }
        val code = obj["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 0) {
            val msg = when (code) {
                -352 -> "触发风控，请稍后重试"
                -412 -> "请求被限制，稍后自动重试"
                -101 -> "账号未登录，请重新登录"
                else -> obj["message"]?.jsonPrimitive?.contentOrNull ?: "接口错误 $code"
            }
            throw BiliError(code, msg)
        }
        if (code == null && obj["error"] != null) {
            throw BiliError(-2, obj["error"]?.jsonPrimitive?.contentOrNull ?: "未知错误")
        }
        return obj["data"] ?: obj["result"] ?: JsonObject(emptyMap())
    }

    /** 裸 JSON（无信封），如弹幕 XML 之外的接口 */
    suspend fun rawText(base: String, path: String, params: Map<String, String> = emptyMap(), referer: String? = null): String =
        raw(buildGet(base, path, params, referer))

    /** 二进制 GET（备用，当前弹幕走 XML） */
    suspend fun rawBytes(base: String, path: String, params: Map<String, String> = emptyMap(), referer: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            var last: Throwable? = null
            repeat(3) { i ->
                try {
                    client.newCall(buildGet(base, path, params, referer)).execute().use { r ->
                        if (r.code in 200..299) return@withContext r.body?.bytes() ?: ByteArray(0)
                        last = IOException("HTTP ${r.code}")
                    }
                } catch (e: IOException) { last = e }
                if (i < 2) delay(500L * (i + 1))
            }
            throw NetError(last)
        }

    suspend fun ensureBuvid() {
        if (cookies.containsKey("buvid3")) return
        runCatching {
            val d = api(API, "/x/frontend/finger/spi").jsonObject
            (d["b_3"] ?: d["b_4"])?.jsonPrimitive?.contentOrNull?.let {}
            d["b_3"]?.jsonPrimitive?.contentOrNull?.let { cookies["buvid3"] = it }
            d["b_4"]?.jsonPrimitive?.contentOrNull?.let { cookies["buvid4"] = it }
            Account.persistCookies(cookies)
        }
    }
}
