package com.tuneplayer.app.data.youtube

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.MusicResponsiveListItemRenderer
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.SearchResponse
import com.tuneplayer.app.data.model.AudioInfo
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

data class ResolvedAudio(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val codec: String? = null,
)

class YouTubeMusicRepository {

    companion object {
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        @Suppress("UNUSED_PARAMETER")
        val RESOLVED_HEADERS = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
        expectSuccess = false
    }

    private val innerTube = InnerTube(httpClient)
    private val configStore = RemotePlayerConfigStore(
        httpClient = httpClient,
        repository = PlayerConfigRepository.disabled(),
    )
    private val cipher = YouTubeCipherService(httpClient, configStore)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(
            httpClient = httpClient,
            innerTube = innerTube,
            remotePlayerConfigStore = configStore,
            cipherService = cipher,
        ),
        cipherService = cipher,
        innerTube = innerTube,
    )

    /**
     * Applies a YouTube Music session captured by the in-app login.
     *
     * The InnerTube client uses SAPISID for account-bound search and playback
     * once login-for-browse is enabled.
     */
    fun applySession(
        cookie: String,
        dataSyncId: String? = null,
        authUser: String = "0",
    ) {
        innerTube.replaceSession(
            cookie = cookie,
            visitorData = innerTube.visitorData,
            dataSyncId = dataSyncId,
            authUser = authUser,
            useLoginForBrowse = true,
        )
    }

    fun clearSession() {
        innerTube.replaceSession(
            cookie = null,
            visitorData = innerTube.visitorData,
            dataSyncId = null,
            authUser = "0",
            useLoginForBrowse = false,
        )
    }

    suspend fun search(query: String): List<Track> {
        val response = innerTube.search(YouTubeClient.WEB_REMIX, query = query).body<SearchResponse>()
        val renderers = mutableListOf<JsonObject>()

        collectRenderersFromSearchResponse(response, renderers)

        return renderers.mapNotNull { rendererJson ->
            runCatching {
                val renderer = json.decodeFromJsonElement<MusicResponsiveListItemRenderer>(rendererJson)
                val columns = renderer.flexColumns.mapNotNull {
                    it.musicResponsiveListItemFlexColumnRenderer.text?.runs
                }
                val title = columns.getOrNull(0)?.firstOrNull()?.text ?: return@runCatching null
                val subtitle = columns.getOrNull(1).orEmpty()
                val artist = subtitle.firstOrNull()?.text ?: "Unknown artist"
                val videoId = renderer.playlistItemData?.videoId
                    ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                    ?: return@runCatching null
                val artwork = renderer.thumbnail
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.lastOrNull()
                    ?.url
                val duration = subtitle
                    .mapNotNull { parseDurationMs(it.text) }
                    .firstOrNull()
                    ?: 0L

                Track(
                    id = "yt:$videoId",
                    title = title,
                    artist = artist,
                    durationMs = duration,
                    artworkUri = artwork,
                    videoId = videoId,
                    isLocal = false,
                )
            }.getOrNull()
        }.distinctBy { it.id }.take(30)
    }

    private fun collectRenderersFromSearchResponse(response: SearchResponse, output: MutableList<JsonObject>) {
        val contents = response.contents
        if (contents == null) return

        contents.tabbedSearchResultsRenderer?.let { collectRenderers(it, output) }
        contents.sectionListRenderer?.let { collectRenderers(it, output) }
    }

    private fun collectRenderersDeep(element: JsonElement, output: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("musicResponsiveListItemRenderer")) {
                    element["musicResponsiveListItemRenderer"]?.let { renderer ->
                        if (renderer is JsonObject) output += renderer
                    }
                }
                element.values.forEach { collectRenderersDeep(it, output) }
            }
            is JsonArray -> element.forEach { collectRenderersDeep(it, output) }
            else -> Unit
        }
    }

    suspend fun resolveAudio(
        videoId: String,
        quality: YouTubeAudioQuality = YouTubeAudioQuality.High,
    ): ResolvedAudio {
        val stream = extractor.extract(
            videoId = videoId,
            hints = ContentHints(wantVideo = false)
                .withStreamCapabilities(allowHls = false, allowSabr = false),
            audioQuality = quality.toInnerTubeQuality(),
        ) ?: error("YouTube Music returned no playable audio stream.")

        val requestHeaders = stream.headers
            .plus("User-Agent" to WEB_USER_AGENT)
            .plus("Referer" to "https://music.youtube.com/")
            .plus("Origin" to "https://music.youtube.com")

        RESOLVED_HEADERS[stream.audioUrl] = requestHeaders

        return ResolvedAudio(
            url = stream.audioUrl,
            mimeType = stream.mimeType,
            headers = requestHeaders,
            codec = AudioInfo.codecLabel(stream.mimeType),
        )
    }

    /**
     * Estimates bitrate from the stream size and duration without downloading it.
     *
     * Runs separately from [resolveAudio] so playback can start immediately;
     * WebM/Opus does not carry a bitrate field, so this fills the gap when the
     * player cannot report one.
     */
    suspend fun estimateBitrateKbps(
        url: String,
        requestHeaders: Map<String, String>,
        durationMs: Long,
    ): Int? = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext null
        val lengthBytes = withTimeoutOrNull(3_000) {
            runCatching {
                httpClient.head(url) {
                    requestHeaders.forEach { (key, value) -> header(key, value) }
                    header("User-Agent", WEB_USER_AGENT)
                }.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            }.getOrNull()
        } ?: return@withContext null
        (lengthBytes * 8 / durationMs).toInt()
    }

    /**
     * Fetches plain-text lyrics from YouTube's timedtext endpoint.
     *
     * Returns null when the video has no caption track. Only the default track
     * is requested; translations are out of scope.
     */
    suspend fun fetchLyrics(videoId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://music.youtube.com/api/timedtext" +
            "?v=$videoId&lang=en&fmt=json3&client=WEB_REMIX&c=WEB_REMIX"
        val body = runCatching {
            httpClient.get(url) { header("User-Agent", WEB_USER_AGENT) }.bodyAsText()
        }.getOrNull().orEmpty()

        val events = runCatching {
            json.parseToJsonElement(body).jsonObject["events"]?.jsonArray
        }.getOrNull() ?: return@withContext null

        val lines = events.mapNotNull { event ->
            event.jsonObject["segs"]?.jsonArray
                ?.mapNotNull { seg -> seg.jsonObject["utf8"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    fun close() {
        innerTube.close()
        httpClient.close()
    }

    private fun collectRenderers(element: JsonElement, output: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element["musicResponsiveListItemRenderer"]?.let { renderer ->
                    if (renderer is JsonObject) output += renderer
                }
                element.values.forEach { collectRenderers(it, output) }
            }

            is JsonArray -> element.forEach { collectRenderers(it, output) }
            else -> Unit
        }
    }

    private fun parseDurationMs(text: String): Long? {
        val parts = text.trim().split(":")
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        return when (numbers.size) {
            2 -> (numbers[0] * 60 + numbers[1]) * 1000
            3 -> (numbers[0] * 3600 + numbers[1] * 60 + numbers[2]) * 1000
            else -> null
        }
    }
}

private fun YouTubeAudioQuality.toInnerTubeQuality(): AudioQuality = when (this) {
    YouTubeAudioQuality.Auto -> AudioQuality.AUTO
    YouTubeAudioQuality.High -> AudioQuality.HIGH
    YouTubeAudioQuality.DataSaver -> AudioQuality.LOW
    YouTubeAudioQuality.Mp4 -> AudioQuality.MP4
}
