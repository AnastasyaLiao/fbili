package com.sammy.fbili.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 宽容数字：字符串/数字/null 都能解析成 Long */
object LLong : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Long {
        val e = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        return e.longOrNull ?: e.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
    }
}

/** 宽容字符串 */
object LStr : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LStr", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): String =
        (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.contentOrNull ?: ""
}

/** 时长统一成秒：支持 "12:34"、"1:02:03"、"834"、834 */
object Dur : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Dur", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Long {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        val p = runCatching { el.jsonPrimitive }.getOrNull() ?: return 0
        p.longOrNull?.let { return if (it > 100000) 0 else it }
        val s = p.contentOrNull ?: return 0
        if (s.contains(':')) return s.split(':').reversed()
            .mapIndexed { i, seg -> (seg.toDoubleOrNull() ?: 0.0) * Math.pow(60.0, i.toDouble()) }
            .sum().toLong()
        return s.toDoubleOrNull()?.toLong() ?: 0
    }
}

fun String.normUrl(): String = when {
    startsWith("//") -> "https:$this"
    startsWith("http") -> this
    else -> this
}

// ---------- 账号 ----------
@Serializable
data class Nav(
    val isLogin: Boolean = false,
    @Serializable(with = LLong::class) val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val vip: Vip? = null,
    @SerialName("level_info") val levelInfo: LevelInfo? = null,
)
@Serializable data class Vip(val status: Int = 0, @SerialName("vipType") val vipType: Int = 0)
@Serializable data class LevelInfo(@SerialName("current_level") val currentLevel: Int = 0)

// ---------- 视频条目（热门/排行/相关/UP主稿件通用） ----------
@Serializable
data class VideoItem(
    val bvid: String = "",
    @Serializable(with = LLong::class) val aid: Long = 0,
    val title: String = "",
    val pic: String = "",
    @Serializable(with = Dur::class) val duration: Long = 0,
    val owner: Owner? = null,
    val stat: Stat? = null,
    @Serializable(with = LLong::class) val pubdate: Long = 0,
    val tname: String = "",
)
@Serializable data class Owner(@Serializable(with = LLong::class) val mid: Long = 0, val name: String = "", val face: String = "")
@Serializable
data class Stat(
    @Serializable(with = LLong::class) val view: Long = 0,
    @Serializable(with = LLong::class) val danmaku: Long = 0,
    @Serializable(with = LLong::class) val reply: Long = 0,
    @Serializable(with = LLong::class) val favorite: Long = 0,
    @Serializable(with = LLong::class) val like: Long = 0,
    @Serializable(with = LLong::class) val coin: Long = 0,
    @Serializable(with = LLong::class) val share: Long = 0,
)

// ---------- 首页推荐 ----------
@Serializable
data class FeedItem(
    val title: String = "",
    val param: String = "",
    @Serializable(with = LLong::class) val id: Long = 0,
    val cover: String = "",
    @Serializable(with = LLong::class) val count: Long = 0,
    @Serializable(with = Dur::class) val duration: Long = 0,
    val upper: Owner? = null,
    @SerialName("short_link_v2") val shortLink: String = "",
    val uri: String = "",
) {
    fun keyBvid(): String = when {
        param.startsWith("BV") -> param
        uri.contains("BV") -> "BV" + uri.substringAfter("BV").take(10)
        shortLink.contains("BV") -> "BV" + shortLink.substringAfter("BV").take(10)
        else -> ""
    }
    fun keyAid(): Long = if (param.startsWith("av")) param.drop(2).toLongOrNull() ?: id else id
}

// ---------- 视频详情 ----------
@Serializable
data class ViewInfo(
    @Serializable(with = LLong::class) val aid: Long = 0,
    val bvid: String = "",
    @Serializable(with = LLong::class) val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    @Serializable(with = Dur::class) val duration: Long = 0,
    val owner: Owner? = null,
    val stat: Stat? = null,
    val pages: List<PageInfo> = emptyList(),
    @SerialName("is_fav") val isFav: Boolean = false,
    @SerialName("staff") val staff: List<Owner> = emptyList(),
    @Serializable(with = LLong::class) val pubdate: Long = 0,
)
@Serializable
data class PageInfo(
    @Serializable(with = LLong::class) val cid: Long = 0,
    @Serializable(with = LLong::class) val page: Long = 1,
    val part: String = "",
    @Serializable(with = Dur::class) val duration: Long = 0,
)

// ---------- 播放流 ----------
@Serializable
data class PlayResult(
    val quality: Int = 0,
    @SerialName("accept_quality") val acceptQuality: List<Int> = emptyList(),
    @SerialName("accept_description") val acceptDescription: List<String> = emptyList(),
    val support_formats: List<SupportFmt> = emptyList(),
    val dash: Dash? = null,
    val durl: List<Durl> = emptyList(),
)
@Serializable data class SupportFmt(@SerialName("qn") val qn: Int = 0, @SerialName("format") val format: String = "")
@Serializable data class Durl(@SerialName("total_bytes") @Serializable(with = LLong::class) val totalBytes: Long = 0, val url: String = "")
@Serializable
data class Dash(
    val video: List<Stream> = emptyList(),
    val audio: List<Stream> = emptyList(),
    @Serializable(with = LLong::class) val duration: Long = 0,
)
@Serializable
data class Stream(
    @Serializable(with = LLong::class) val id: Long = 0,
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("backup_url") val backupUrl: List<String> = emptyList(),
    val codecs: String = "",
    @SerialName("codecid") @Serializable(with = LLong::class) val codecid: Long = 0,
    @Serializable(with = LLong::class) val width: Long = 0,
    @Serializable(with = LLong::class) val height: Long = 0,
    @Serializable(with = LLong::class) val bandwidth: Long = 0,
)

// ---------- 评论 ----------
@Serializable
data class ReplyPage(
    val replies: List<Reply> = emptyList(),
    val cursor: Cursor? = null,
    @SerialName("top_replies") val topReplies: List<Reply> = emptyList(),
)
@Serializable data class Cursor(@Serializable(with = LLong::class) val allCount: Long = 0)
@Serializable
data class Reply(
    @Serializable(with = LLong::class) val rpid: Long = 0,
    val content: RContent = RContent(),
    val member: Member = Member(),
    @Serializable(with = LLong::class) val like: Long = 0,
    @Serializable(with = LLong::class) val count: Long = 0,
    @Serializable(with = LLong::class) val ctime: Long = 0,
    val liked: Boolean = false,
)
@Serializable data class RContent(val message: String = "")
@Serializable data class Member(val uname: String = "", val face: String = "", @Serializable(with = LLong::class) val mid: Long = 0)

// ---------- 搜索 ----------
@Serializable
data class SearchVideo(
    val bvid: String = "",
    @Serializable(with = LLong::class) val aid: Long = 0,
    val title: String = "",
    val author: String = "",
    val play: String = "",
    @Serializable(with = LStr::class) @SerialName("duration") val durStr: String = "",
    @Serializable(with = LStr::class) @SerialName("length") val lengthStr: String = "",
    val upic: String = "",
    val pic: String = "",
) {
    fun durSec(): Long = (durStr.ifEmpty { lengthStr }).durParse()
}
@Serializable
data class SearchBangumi(
    @SerialName("season_id") @Serializable(with = LLong::class) val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("index_info") val indexInfo: String = "",
    @Serializable(with = LStr::class) val evaluation: String = "",
    @Serializable(with = LStr::class) val rating: String = "",
    val url: String = "",
    val areas: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    @SerialName("bangumi_id") @Serializable(with = LLong::class) val bangumiId: Long = 0,
) {
    fun score(): String = rating.ifEmpty { evaluation }
}
@Serializable
data class SearchUser(
    @Serializable(with = LLong::class) val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    @Serializable(with = LStr::class) val fans: String = "",
    @SerialName("video_count") val videoCount: Int = 0,
)

fun String.durParse(): Long = Dur.let {
    if (contains(':')) split(':').reversed()
        .mapIndexed { i, s -> (s.toDoubleOrNull() ?: 0.0) * Math.pow(60.0, i.toDouble()) }.sum().toLong()
    else toDoubleOrNull()?.toLong() ?: 0
}

// ---------- 番剧 ----------
@Serializable
data class Season(
    val title: String = "",
    val cover: String = "",
    val evaluate: String = "",
    @SerialName("season_id") @Serializable(with = LLong::class) val seasonId: Long = 0,
    val episodes: List<Episode> = emptyList(),
    val positive: Positive? = null,
) { fun eps(): List<Episode> = episodes.ifEmpty { positive?.episodes ?: emptyList() } }
@Serializable data class Positive(val episodes: List<Episode> = emptyList())
@Serializable
data class Episode(
    @Serializable(with = LLong::class) val id: Long = 0,
    @Serializable(with = LLong::class) val aid: Long = 0,
    val bvid: String = "",
    @Serializable(with = LLong::class) val cid: Long = 0,
    val part: String = "",
    @Serializable(with = Dur::class) val duration: Long = 0,
    val cover: String = "",
)

// ---------- 收藏 / 关注 ----------
@Serializable data class FavFolder(@Serializable(with = LLong::class) val id: Long = 0, val title: String = "", @SerialName("media_count") @Serializable(with = LLong::class) val mediaCount: Long = 0)
@Serializable data class FavMedia(val id: String = "", val title: String = "", val cover: String = "", val intro: String = "", val upper: Owner? = null, @Serializable(with = Dur::class) val duration: Long = 0)
@Serializable data class RelationUser(@Serializable(with = LLong::class) val mid: Long = 0, val uname: String = "", val face: String = "", val sign: String = "")

// ---------- 宽松取值助手（易变接口专用） ----------
fun JsonObject.str(vararg keys: String, def: String = ""): String {
    for (k in keys) {
        val v = this[k] ?: continue
        val p = runCatching { v.jsonPrimitive }.getOrNull() ?: continue
        val c = p.contentOrNull ?: continue
        if (c.isNotEmpty()) return c
    }
    return def
}
fun JsonObject.num(vararg keys: String, def: Long = 0): Long {
    for (k in keys) {
        val v = this[k] ?: continue
        val p = runCatching { v.jsonPrimitive }.getOrNull() ?: continue
        (p.longOrNull ?: p.contentOrNull?.toDoubleOrNull()?.toLong())?.let { return it }
    }
    return def
}
fun JsonObject.obj(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()
fun JsonObject.arr(key: String): List<JsonObject> = runCatching {
    this[key]?.jsonArray?.map { it.jsonObject } ?: emptyList()
}.getOrDefault(emptyList())


