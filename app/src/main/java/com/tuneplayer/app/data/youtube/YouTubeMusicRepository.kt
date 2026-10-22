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
import com.tuneplayer.app.data.model.Track
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

data class ResolvedAudio(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
)

class YouTubeMusicRepository {

    companion object {
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

    suspend fun resolveAudio(videoId: String): ResolvedAudio {
        val stream = extractor.extract(
            videoId = videoId,
            hints = ContentHints(wantVideo = false)
                .withStreamCapabilities(allowHls = false, allowSabr = false),
            audioQuality = AudioQuality.HIGH,
        ) ?: error("YouTube Music returned no playable audio stream.")

        val headers = stream.headers
            .plus("User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .plus("Referer" to "https://music.youtube.com/")
            .plus("Origin" to "https://music.youtube.com")

        RESOLVED_HEADERS[stream.audioUrl] = headers

        return ResolvedAudio(
            url = stream.audioUrl,
            mimeType = stream.mimeType,
            headers = headers,
        )
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
