package com.sammy.fbili.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.TreeMap

/** WBI 签名：nav 拿 img_key/sub_key，混排 32 位 key，参数排序 + wts + MD5 = w_rid */
object Wbi {
    private val mixinTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42,
        19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 20, 24, 54, 40, 61, 26, 17, 0, 1,
        60, 51, 30, 4, 22, 25, 59, 56, 57, 34, 44, 52
    )

    @Volatile private var mixinKey: String = ""
    @Volatile private var keyDate: String = ""
    private val mutex = Mutex()

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun today(): String = (System.currentTimeMillis() / 86_400_000L).toString()

    private suspend fun refreshKey() {
        val nav = Net.api(Net.API, "/x/web-interface/nav").jsonObject
        val img = nav["wbi_img"]?.jsonObject ?: return
        fun keyOf(u: String): String = u.substringAfterLast('/').substringBefore('.')
        val raw = keyOf(img["img_url"]!!.jsonPrimitive.content) + keyOf(img["sub_url"]!!.jsonPrimitive.content)
        mixinKey = buildString { for (i in mixinTab) if (i < raw.length && length < 32) append(raw[i]) }
            .take(32)
    }

    suspend fun sign(params: Map<String, String>): Map<String, String> = mutex.withLock {
        val today = today()
        if (mixinKey.isEmpty() || keyDate != today) {
            runCatching { refreshKey(); keyDate = today }
        }
        if (mixinKey.isEmpty()) return params + ("wts" to (System.currentTimeMillis() / 1000).toString())
        val m = TreeMap<String, String>(params + ("wts" to (System.currentTimeMillis() / 1000).toString()))
        val query = m.entries.joinToString("&") { (k, v) ->
            val filtered = v.filterNot { it in "!'()*~" }
            "${k.urlEncode()}=${filtered.urlEncode()}"
        }
        m["w_rid"] = md5(query + mixinKey)
        m.toMap()
    }
}

private fun String.urlEncode(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
