package com.hybridmusic.player.data.remote.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

class InnerTubeClient(
    private val httpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val streamCache = ConcurrentHashMap<String, AudioStreamInfo>()

    @Volatile
    private var cachedVisitorData: String? = null

    companion object {
        private const val YTM_SEARCH_URL =
            "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
        private const val YT_PLAYER_URL =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val YTM_VISITOR_URL =
            "https://music.youtube.com/youtubei/v1/visitor_id?prettyPrint=false"
        private const val SONG_FILTER_PARAMS = "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D"

        // Track User-Agent per videoId / stream URL so OkHttp & ExoPlayer always send matching headers
        private val urlUserAgentMap = ConcurrentHashMap<String, String>()

        const val DEFAULT_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

        fun resolveUserAgentForUrl(url: String): String {
            urlUserAgentMap[url]?.let { return it }
            val cParam = Regex("[?&]c=([^&]+)").find(url)?.groupValues?.getOrNull(1) ?: ""
            return when {
                cParam.contains("ANDROID_VR", ignoreCase = true) -> DEFAULT_VR_USER_AGENT
                cParam.contains("IOS", ignoreCase = true) ->
                    "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
                cParam.contains("TVHTML5", ignoreCase = true) ->
                    "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
                cParam.contains("WEB", ignoreCase = true) ->
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                else -> DEFAULT_VR_USER_AGENT
            }
        }
    }

    private fun getOrFetchVisitorData(): String? {
        cachedVisitorData?.let { return it }
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20250310.01.00")
                }
            }
        }.toString()

        val req = Request.Builder()
            .url(YTM_VISITOR_URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Origin", "https://music.youtube.com")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val root = json.parseToJsonElement(body).jsonObject
                val vd = root["responseContext"]?.jsonObject
                    ?.get("visitorData")?.jsonPrimitive?.contentOrNull
                if (!vd.isNullOrBlank()) {
                    cachedVisitorData = vd
                }
                vd
            }
        }.getOrNull()
    }

    suspend fun searchSongs(query: String): List<TrackItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20250310.01.00")
                    put("hl", "th")
                    put("gl", "TH")
                }
            }
            put("query", query)
            put("params", SONG_FILTER_PARAMS)
        }.toString()

        val request = Request.Builder()
            .url(YTM_SEARCH_URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20250310.01.00")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body)
                extractTracksFromSearch(root)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getAudioStream(videoId: String, forceRefresh: Boolean = false): AudioStreamInfo? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                streamCache[videoId]?.let { cached ->
                    if (cached.expiresAtMs > now + 90_000L) {
                        return@withContext cached
                    }
                }
            } else {
                streamCache.remove(videoId)
            }

            val visitorData = getOrFetchVisitorData()

            // Client 1: ANDROID_VR (Quest 3 - returns direct unthrottled HTTPS audio URLs without PO Token)
            val vrStream = fetchAndroidVrStream(videoId, visitorData)
            if (vrStream != null && verifyStreamReachable(vrStream)) {
                cacheStream(videoId, vrStream)
                return@withContext vrStream
            }

            // Client 2: WEB_EMBEDDED_PLAYER (Works for kids/restricted videos that VR skips)
            val webEmbedStream = fetchWebEmbeddedStream(videoId, visitorData)
            if (webEmbedStream != null && verifyStreamReachable(webEmbedStream)) {
                cacheStream(videoId, webEmbedStream)
                return@withContext webEmbedStream
            }

            // Client 3: TVHTML5_SIMPLY_EMBEDDED_PLAYER
            val tvStream = fetchTvEmbeddedStream(videoId, visitorData)
            if (tvStream != null && verifyStreamReachable(tvStream)) {
                cacheStream(videoId, tvStream)
                return@withContext tvStream
            }

            // Client 4: IOS modern client fallback
            val iosStream = fetchIosStream(videoId, visitorData)
            if (iosStream != null && verifyStreamReachable(iosStream)) {
                cacheStream(videoId, iosStream)
                return@withContext iosStream
            }

            // Client 5: Piped API public fallback
            val pipedStream = fetchPipedFallbackStream(videoId)
            if (pipedStream != null && verifyStreamReachable(pipedStream)) {
                cacheStream(videoId, pipedStream)
                return@withContext pipedStream
            }

            // Last resort: return unverified candidate if one existed
            val fallback = vrStream ?: webEmbedStream ?: tvStream ?: iosStream ?: pipedStream
            if (fallback != null) {
                cacheStream(videoId, fallback)
            }
            fallback
        }

    fun invalidateStreamCache(videoId: String) {
        streamCache.remove(videoId)
    }

    private fun cacheStream(videoId: String, stream: AudioStreamInfo) {
        streamCache[videoId] = stream
        urlUserAgentMap[stream.url] = stream.userAgent
    }

    private fun verifyStreamReachable(stream: AudioStreamInfo): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(stream.url)
                .header("User-Agent", stream.userAgent)
                .header("Range", "bytes=0-1023")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                resp.code == 200 || resp.code == 206
            }
        }.getOrDefault(false)
    }

    private fun fetchAndroidVrStream(videoId: String, visitorData: String?): AudioStreamInfo? {
        val ua = DEFAULT_VR_USER_AGENT
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", "1.65.10")
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("osName", "Android")
                    put("osVersion", "12L")
                    put("androidSdkVersion", 32)
                    put("hl", "en")
                    put("gl", "US")
                    if (!visitorData.isNullOrBlank()) {
                        put("visitorData", visitorData)
                    }
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        return executePlayerRequest(
            payload = payload,
            userAgent = ua,
            clientIdHeader = "28",
            clientVersion = "1.65.10",
            visitorData = visitorData
        )
    }

    private fun fetchWebEmbeddedStream(videoId: String, visitorData: String?): AudioStreamInfo? {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_EMBEDDED_PLAYER")
                    put("clientVersion", "1.20250310.01.00")
                    put("hl", "en")
                    put("gl", "US")
                    if (!visitorData.isNullOrBlank()) {
                        put("visitorData", visitorData)
                    }
                }
                putJsonObject("thirdParty") {
                    put("embedUrl", "https://www.youtube.com/")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        return executePlayerRequest(
            payload = payload,
            userAgent = ua,
            clientIdHeader = "56",
            clientVersion = "1.20250310.01.00",
            visitorData = visitorData,
            origin = "https://www.youtube.com"
        )
    }

    private fun fetchTvEmbeddedStream(videoId: String, visitorData: String?): AudioStreamInfo? {
        val ua = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                    put("clientVersion", "2.0")
                    put("hl", "en")
                    put("gl", "US")
                    if (!visitorData.isNullOrBlank()) {
                        put("visitorData", visitorData)
                    }
                }
                putJsonObject("thirdParty") {
                    put("embedUrl", "https://www.youtube.com/")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        return executePlayerRequest(
            payload = payload,
            userAgent = ua,
            clientIdHeader = "85",
            clientVersion = "2.0",
            visitorData = visitorData,
            origin = "https://www.youtube.com"
        )
    }

    private fun fetchIosStream(videoId: String, visitorData: String?): AudioStreamInfo? {
        val ua = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS")
                    put("clientVersion", "20.10.4")
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "18.3.2.22D82")
                    put("hl", "en")
                    put("gl", "US")
                    if (!visitorData.isNullOrBlank()) {
                        put("visitorData", visitorData)
                    }
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        return executePlayerRequest(
            payload = payload,
            userAgent = ua,
            clientIdHeader = "5",
            clientVersion = "20.10.4",
            visitorData = visitorData
        )
    }

    private fun executePlayerRequest(
        payload: String,
        userAgent: String,
        clientIdHeader: String,
        clientVersion: String,
        visitorData: String?,
        origin: String? = null
    ): AudioStreamInfo? {
        val reqBuilder = Request.Builder()
            .url(YT_PLAYER_URL)
            .header("User-Agent", userAgent)
            .header("X-YouTube-Client-Name", clientIdHeader)
            .header("X-YouTube-Client-Version", clientVersion)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!visitorData.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorData)
        }
        if (!origin.isNullOrBlank()) {
            reqBuilder.header("Origin", origin)
            reqBuilder.header("Referer", "$origin/")
        }

        return runCatching {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val streamingData = root["streamingData"]?.jsonObject ?: return null
                val expiresInSec = streamingData["expiresInSeconds"]?.jsonPrimitive?.longOrNull ?: 3600L
                val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray ?: return null

                var bestMp4Audio: AudioStreamInfo? = null
                var bestAnyAudio: AudioStreamInfo? = null

                for (item in adaptiveFormats) {
                    val obj = item.jsonObject
                    val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: continue
                    if (!mimeType.startsWith("audio/")) continue
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                    if (url.isBlank()) continue
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                    val contentLength = obj["contentLength"]?.jsonPrimitive?.longOrNull ?: 0L

                    val candidate = AudioStreamInfo(
                        url = url,
                        mimeType = mimeType,
                        bitrate = bitrate,
                        contentLength = contentLength,
                        userAgent = userAgent,
                        expiresAtMs = System.currentTimeMillis() + (expiresInSec * 1000L)
                    )

                    if (mimeType.startsWith("audio/mp4")) {
                        if (bestMp4Audio == null || bitrate > bestMp4Audio.bitrate) {
                            bestMp4Audio = candidate
                        }
                    }
                    if (bestAnyAudio == null || bitrate > bestAnyAudio.bitrate) {
                        bestAnyAudio = candidate
                    }
                }
                bestMp4Audio ?: bestAnyAudio
            }
        }.getOrNull()
    }

    private fun fetchPipedFallbackStream(videoId: String): AudioStreamInfo? {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.yt"
        )
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        for (base in instances) {
            val found = runCatching {
                val req = Request.Builder()
                    .url("$base/streams/$videoId")
                    .header("User-Agent", ua)
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    val root = json.parseToJsonElement(body).jsonObject
                    val audioStreams = root["audioStreams"]?.jsonArray ?: return@runCatching null
                    var best: AudioStreamInfo? = null
                    for (el in audioStreams) {
                        val obj = el.jsonObject
                        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                        val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: "audio/mp4"
                        val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                        if (best == null || bitrate > best.bitrate) {
                            best = AudioStreamInfo(
                                url = url,
                                mimeType = mime,
                                bitrate = bitrate,
                                userAgent = ua,
                                expiresAtMs = System.currentTimeMillis() + 3600_000L
                            )
                        }
                    }
                    best
                }
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    private fun extractTracksFromSearch(root: JsonElement): List<TrackItem> {
        val results = mutableListOf<TrackItem>()
        val seenIds = mutableSetOf<String>()
        collectMusicItems(root, results, seenIds)
        return results
    }

    private fun collectMusicItems(
        element: JsonElement,
        out: MutableList<TrackItem>,
        seenIds: MutableSet<String>
    ) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicResponsiveListItemRenderer"]?.jsonObject
                if (renderer != null) {
                    parseMusicListItem(renderer)?.let { track ->
                        if (seenIds.add(track.videoId)) {
                            out.add(track)
                        }
                    }
                }
                for ((_, value) in element) {
                    collectMusicItems(value, out, seenIds)
                }
            }
            is JsonArray -> {
                for (item in element) {
                    collectMusicItems(item, out, seenIds)
                }
            }
            else -> Unit
        }
    }

    private fun parseMusicListItem(renderer: JsonObject): TrackItem? {
        val videoId = renderer["playlistItemData"]?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: findNestedVideoId(renderer)
            ?: return null

        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        val title = flexColumns.getOrNull(0)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: return null

        val secondaryRuns = flexColumns.getOrNull(1)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray

        val subtitleParts = secondaryRuns?.mapNotNull {
            it.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.trim()
        }?.filter { it.isNotEmpty() && it != "•" && it != "เพลง" && it != "Song" }.orEmpty()

        val artist = subtitleParts.firstOrNull() ?: "Unknown Artist"
        val durationText = subtitleParts.lastOrNull { it.contains(":") }
        val durationMs = parseDurationToMs(durationText)

        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray

        val rawThumb = thumbnails?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val highResThumb = rawThumb.replace(Regex("=w\d+-h\d+"), "=w544-h544")

        return TrackItem(
            videoId = videoId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            thumbnailUrl = highResThumb
        )
    }

    private fun findNestedVideoId(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                val vid = (element["videoId"] as? JsonPrimitive)?.contentOrNull
                if (!vid.isNullOrBlank() && vid.length == 11) return vid
                for ((_, v) in element) {
                    val found = findNestedVideoId(v)
                    if (found != null) return found
                }
            }
            is JsonArray -> {
                for (item in element) {
                    val found = findNestedVideoId(item)
                    if (found != null) return found
                }
            }
            else -> Unit
        }
        return null
    }

    private fun parseDurationToMs(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }
}
