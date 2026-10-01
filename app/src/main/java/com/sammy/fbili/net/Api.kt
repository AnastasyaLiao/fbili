package com.sammy.fbili.net

import com.sammy.fbili.data.Account
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

inline fun <reified T> JsonElement.asT(): T = Net.json.decodeFromJsonElement(this)
inline fun <reified T> JsonObject.listT(key: String): List<T> =
    this[key]?.let { Net.json.decodeFromJsonElement(it) } ?: emptyList()

object Api {
    private const val WEB = "https://www.bilibili.com"

    // ---------- 浏览 ----------
    suspend fun feedRcmd(freshIdx: Int): List<FeedItem> {
        val p = Wbi.sign(mapOf("feed_version" to "V3", "version" to "2", "ps" to "10", "fresh_idx" to "$freshIdx"))
        return Net.api(Net.API, "/x/web-interface/wbi/index/top/feed/rcmd", p).asT<JsonObject>().listT("item")
    }

    suspend fun popular(pn: Int): List<VideoItem> =
        Net.api(Net.API, "/x/web-interface/popular", mapOf("ps" to "20", "pn" to "$pn"))
            .asT<JsonObject>().listT("list")

    suspend fun ranking(): List<VideoItem> =
        Net.api(Net.API, "/x/web-interface/ranking/v2", mapOf("rid" to "0", "type" to "all"))
            .asT<JsonObject>().listT("list")

    suspend fun view(bvid: String, aid: Long = 0): ViewInfo {
        val p = if (bvid.isNotEmpty()) mapOf("bvid" to bvid) else mapOf("aid" to "$aid")
        return Net.api(Net.API, "/x/web-interface/view", p, referer = WEB).asT()
    }

    suspend fun related(bvid: String): List<VideoItem> =
        Net.api(Net.API, "/x/web-interface/archive/related", mapOf("bvid" to bvid)).asT<List<VideoItem>>()

    // ---------- 播放 ----------
    /** epId>0 时走 PGC 接口（付费番剧也可播） */
    suspend fun playurl(bvid: String, cid: Long, epId: Long = 0): PlayResult =
        (if (epId > 0)
            Net.api(Net.API, "/pgc/player/web/playurl",
                mapOf("ep_id" to "$epId", "cid" to "$cid", "fnver" to "0", "fnval" to "4048", "fourk" to "1"), referer = WEB)
        else
            Net.api(Net.API, "/x/player/playurl",
                mapOf("bvid" to bvid, "cid" to "$cid", "fnver" to "0", "fnval" to "4048", "fourk" to "1"), referer = WEB)).asT()

    /** 下载用：mp4 单流（fnval=0），音视频合并但清晰度受限 */
    suspend fun playurlMp4(bvid: String, cid: Long, qn: Int): PlayResult =
        Net.api(Net.API, "/x/player/playurl",
            mapOf("bvid" to bvid, "cid" to "$cid", "qn" to "$qn", "fnver" to "0", "fnval" to "0", "high_quality" to "1"),
            referer = WEB).asT()

    suspend fun danmakuXml(cid: Long): String = Net.rawText(Net.COMMENT, "/${cid}.xml", referer = WEB)

    suspend fun heartbeat(bvid: String, cid: Long, played: Long) = runCatching {
        Net.api(Net.API, "/x/click-interface/web/heartbeat", post = mapOf(
            "bvid" to bvid, "cid" to "$cid", "played_time" to "$played", "type" to "3", "platform" to "web"
        ), referer = WEB)
    }

    suspend fun reportHistory(aid: Long, cid: Long, progress: Long) {
        val csrf = Account.csrf()
        if (csrf.isEmpty() || aid <= 0) return
        runCatching {
            Net.api(Net.API, "/x/v2/history/report", post = mapOf(
                "aid" to "$aid", "cid" to "$cid", "progress" to "$progress", "platform" to "web", "csrf" to csrf
            ), referer = WEB)
        }
    }

    // ---------- 互动 ----------
    suspend fun like(bvid: String, yes: Boolean) {
        Net.api(Net.API, "/x/web-interface/archive/like", post = mapOf(
            "bvid" to bvid, "like" to if (yes) "1" else "2", "csrf" to Account.csrf()
        ), referer = WEB)
    }

    suspend fun hasLike(bvid: String): Boolean = runCatching {
        Net.api(Net.API, "/x/web-interface/archive/has/like", mapOf("bvid" to bvid))
            .jsonPrimitive.booleanOrNull ?: false
    }.getOrDefault(false)

    suspend fun coinInfo(bvid: String): Long = runCatching {
        Net.api(Net.API, "/x/web-interface/archive/coins", mapOf("bvid" to bvid))
            .asT<JsonObject>().num("multiply")
    }.getOrDefault(0)

    suspend fun coin(bvid: String, count: Int) {
        Net.api(Net.API, "/x/web-interface/coin/add", post = mapOf(
            "bvid" to bvid, "multiply" to "$count", "select_like" to "1", "csrf" to Account.csrf()
        ), referer = WEB)
    }

    suspend fun favCheck(bvid: String): Boolean = runCatching {
        Net.api(Net.API, "/x/v2/fav/video/favoured",
            mapOf("bvid" to bvid, "mid" to "${Account.uid()}"), referer = WEB)
            .asT<JsonObject>().let { it["favoured"]?.jsonPrimitive?.booleanOrNull ?: false }
    }.getOrDefault(false)

    suspend fun favDeal(aid: Long, addIds: List<Long>, delIds: List<Long>) {
        Net.api(Net.API, "/x/v3/fav/resource/deal", post = mapOf(
            "rid" to "$aid", "type" to "2",
            "actids" to addIds.joinToString(","), "del_ids" to delIds.joinToString(","),
            "csrf" to Account.csrf()
        ), referer = WEB)
    }

    suspend fun share(bvid: String) = runCatching {
        Net.api(Net.API, "/x/web-interface/share/add", post = mapOf(
            "bvid" to bvid, "csrf" to Account.csrf()
        ), referer = WEB)
    }

    // ---------- 评论 ----------
    // 旧端点 /x/v2/reply 已对未签名请求静默返回空列表；改用 WBI 签名的 wbi/main（mode 3=热度 2=最新）
    suspend fun replies(aid: Long, pn: Int, hot: Boolean): ReplyPage =
        Net.api(Net.API, "/x/v2/reply/wbi/main", Wbi.sign(mapOf(
            "type" to "1", "oid" to "$aid", "pn" to "$pn", "ps" to "20", "mode" to if (hot) "3" else "2"
        )), referer = WEB).asT()

    suspend fun subReplies(aid: Long, root: Long, pn: Int): List<Reply> =
        Net.api(Net.API, "/x/v2/reply/reply", mapOf(
            "type" to "1", "oid" to "$aid", "root" to "$root", "pn" to "$pn", "ps" to "20"
        ), referer = WEB).asT<JsonObject>().listT("replies")

    suspend fun addReply(aid: Long, message: String, root: Long = 0, parent: Long = 0) {
        Net.api(Net.API, "/x/v2/reply/add", post = mapOf(
            "oid" to "$aid", "type" to "1", "message" to message, "plat" to "1",
            "root" to "$root", "parent" to "$parent", "csrf" to Account.csrf()
        ), referer = WEB)
    }

    suspend fun likeReply(aid: Long, rpid: Long, yes: Boolean) {
        Net.api(Net.API, "/x/v2/reply/action", post = mapOf(
            "oid" to "$aid", "type" to "1", "rpid" to "$rpid",
            "like" to if (yes) "1" else "0", "action" to if (yes) "1" else "0",
            "csrf" to Account.csrf()
        ), referer = WEB)
    }

    // ---------- 搜索 ----------
    suspend fun searchDefault(): String = runCatching {
        Net.api(Net.API, "/x/web-interface/wbi/search/default", Wbi.sign(emptyMap()))
            .asT<JsonObject>().str("term")
    }.getOrDefault("")

    suspend fun hotSearch(): List<String> = runCatching {
        Net.api(Net.APP, "/x/v2/search/trending/ranking", mapOf("limit" to "20"))
            .asT<JsonObject>().arr("list").map { it.str("keyword", "show_name") }
    }.getOrDefault(emptyList())

    suspend fun suggest(term: String): List<String> = runCatching {
        Net.api(Net.SSEARCH, "/main/suggest", mapOf("term" to term, "main_ver" to "v1"))
            .asT<JsonObject>().arr("tag").map { it.str("term", "val", "show_name") }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    suspend fun searchResult(type: String, keyword: String, page: Int, order: String): JsonObject {
        val base = mutableMapOf("search_type" to type, "keyword" to keyword, "page" to "$page")
        if (order.isNotEmpty() && type == "video") base["order"] = order
        return Net.api(Net.API, "/x/web-interface/search/type", Wbi.sign(base), referer = WEB).asT()
    }

    // ---------- 用户 / 动态 / 历史 / 收藏 ----------
    suspend fun userCard(mid: Long): JsonObject =
        Net.api(Net.API, "/x/space/wbi/acc/info",
            Wbi.sign(mapOf("mid" to "$mid", "platform" to "web")), referer = WEB).asT()

    suspend fun userStat(mid: Long): JsonObject = runCatching {
        Net.api(Net.API, "/x/relation/stat", mapOf("vmid" to "$mid"), referer = WEB).asT<JsonObject>()
    }.getOrDefault(JsonObject(emptyMap()))

    suspend fun relation(mid: Long): JsonObject = runCatching {
        Net.api(Net.API, "/x/relation", mapOf("mid" to "$mid"), referer = WEB).asT<JsonObject>()
    }.getOrDefault(JsonObject(emptyMap()))

    suspend fun relationModify(mid: Long, act: Int) {
        Net.api(Net.API, "/x/relation/modify", post = mapOf(
            "fid" to "$mid", "act" to "$act", "re_src" to "11", "csrf" to Account.csrf()
        ), referer = WEB)
    }

    suspend fun spaceVideos(mid: Long, pn: Int): JsonObject {
        val p = Wbi.sign(mapOf(
            "mid" to "$mid", "pn" to "$pn", "ps" to "30",
            "order" to "pubdate", "tid" to "0", "platform" to "web"
        ))
        return Net.api(Net.API, "/x/space/wbi/arc/search", p, referer = WEB).asT()
    }

    suspend fun relationFollowing(mid: Long, pn: Int): List<RelationUser> =
        Net.api(Net.API, "/x/relation/followings", mapOf("vmid" to "$mid", "pn" to "$pn", "ps" to "20"))
            .asT<JsonObject>().listT("list")

    suspend fun dynamicFeed(offset: String, page: Int): JsonObject =
        Net.api(Net.API, "/x/polymer/web-dynamic/v1/feed/all", mapOf(
            "timezone_offset" to "-480", "type" to "all", "page" to "$page",
            "offset" to offset, "features" to "itemOpusStyle"
        ), referer = WEB).asT()

    // 历史：/x/v2/history/cursor 已废弃（返回非 JSON），改用 web 端点。
    // 参数严格按 bili_you HistoryApi.getVideoViewHistory：type=archive，首屏不发 max/view_at
    // （传 0 会被判 -400 请求错误），并额外带 WBI 签名（与网页端一致，防 -352）
    suspend fun historyCursor(max: Long, viewAt: Long): JsonObject {
        val base = mutableMapOf("type" to "archive", "business" to "archive", "ps" to "20")
        if (max > 0) base["max"] = "$max"
        if (viewAt > 0) base["view_at"] = "$viewAt"
        return Net.api(Net.API, "/x/web-interface/history/cursor", Wbi.sign(base), referer = WEB).asT()
    }

    suspend fun favFolders(mid: Long): List<FavFolder> =
        Net.api(Net.API, "/x/v3/fav/folder/created/list-all", mapOf("up_mid" to "$mid"), referer = WEB)
            .asT<JsonObject>().listT("list")

    suspend fun favResources(fid: Long, pn: Int): JsonObject =
        Net.api(Net.API, "/x/v3/fav/resource/list", mapOf(
            "media_id" to "$fid", "pn" to "$pn", "ps" to "20",
            "order" to "mtime", "type" to "0", "platform" to "web"
        ), referer = WEB).asT()

    // ---------- 番剧 ----------
    suspend fun season(seasonId: Long, epId: Long = 0): Season {
        val p = if (epId > 0) mapOf("ep_id" to "$epId") else mapOf("season_id" to "$seasonId")
        return Net.api(Net.API, "/pgc/view/web/season", p, referer = WEB).asT()
    }

    /** 番剧/国创 七日榜；返回原始条目列表 */
    suspend fun pgcRank(seasonType: Int): List<JsonObject> {
        val el = Net.api(Net.API, "/pgc/web/rank/list", mapOf("day" to "7", "season_type" to "$seasonType"))
        return runCatching {
            (el as? kotlinx.serialization.json.JsonArray)?.map { it as JsonObject }
                ?: el.asT<JsonObject>().arr("list")
        }.getOrDefault(emptyList())
    }

    // ---------- 登录 ----------
    suspend fun qrGenerate(): JsonObject =
        Net.api(Net.PASSPORT, "/x/passport-login/web/qrcode/generate").asT()

    /** 返回 code：0 成功 / 86101 未扫 / 86090 未确认 / 86038 过期；成功后 cookie 由 Net 自动落地持久化 */
    suspend fun qrPoll(key: String): JsonObject =
        Net.api(Net.PASSPORT, "/x/passport-login/web/qrcode/poll", mapOf("qrcode_key" to key)).asT()

    // ---------- 直播（轻量：推荐列表 + HLS 播放地址） ----------
    suspend fun liveRecommend(page: Int): JsonObject =
        Net.api(Net.LIVE, "/xlive/web-interface/v1/second/getUserRecommend",
            mapOf("page" to "$page", "page_size" to "12", "platform" to "web")).asT()

    suspend fun livePlayUrl(roomId: Long): String = runCatching {
        val d = Net.api(Net.LIVE, "/xlive/web-room/v2/index/getRoomPlayInfo", mapOf(
            "room_id" to "$roomId", "protocol" to "0,1", "format" to "0,1,2", "codec" to "0,1",
            "qn" to "250", "platform" to "web", "ptype" to "8"
        )).asT<JsonObject>()
        val pu = d.obj("playurl_info")?.obj("playurl") ?: return ""
        val hls = pu.obj("hls") ?: pu.obj("https_hls") ?: return ""
        val master = hls.str("master_url")
        if (master.isNotEmpty()) return master
        val fi = hls.arr("url_info").firstOrNull() ?: return ""
        (if (fi.str("host").startsWith("//")) "https:" else "") +
            fi.str("host") + fi.str("base_path") + fi.str("extra")
    }.getOrDefault("")
}
