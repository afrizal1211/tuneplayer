package com.tuneplayer.app.data.youtube

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.MusicResponsiveListItemRenderer
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.GetSearchSuggestionsResponse
import com.tuneplayer.app.data.model.AudioInfo
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import com.tuneplayer.app.playback.StreamHeaders
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
    /** Total size the extractor reported, so no HEAD request is needed. */
    val contentLengthBytes: Long? = null,
)

data class YouTubeSearchSuggestions(
    val queries: List<String> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

/** One YouTube Music search page: the promoted card plus the ordinary rows. */
data class YouTubeSearchResults(
    val topResult: Track? = null,
    val tracks: List<Track> = emptyList(),
)

/**
 * YouTube Music search categories. The tokens are the `params` values used by
 * the web client; the All page is the only one with a promoted card shelf.
 */
enum class YouTubeSearchFilter(val params: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoKEAkQChAFEAMQBA=="),
    VIDEOS("EgWKAQIQAWoKEAkQChAFEAMQBA=="),
}

private data class HomeShelf(
    val title: String,
    val tracks: List<Track>,
)

/** One home-feed carousel, public so the Browse screen can render shelves. */
data class YouTubeHomeShelf(
    val title: String,
    val tracks: List<Track>,
)

class YouTubeMusicRepository {

    companion object {
        /** Refusals no retry will soften. */
        private val TERMINAL_REASONS = setOf(
            StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
            StreamResolveException.Reason.UNAVAILABLE,
            StreamResolveException.Reason.AGE_RESTRICTED,
        )

        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        @Suppress("UNUSED_PARAMETER")
        val RESOLVED_HEADERS = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

        private val SEARCH_SUGGESTION_LABELS = setOf(
            "song", "video", "album", "single", "ep", "playlist", "artist",
        )

        private val HOME_RECOMMENDATION_MARKERS =
            listOf("quick", "pick", "recommend", "mix", "trending")
        private val HOME_AVOID_MARKERS = listOf("recent", "history", "listen again")

        /** How long a stream probe may take before the URL is written off. */
        private const val PROBE_TIMEOUT_MS = 4_000L
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

    suspend fun search(
        query: String,
        filter: YouTubeSearchFilter = YouTubeSearchFilter.ALL,
    ): YouTubeSearchResults {
        val response = innerTube.search(
            client = YouTubeClient.WEB_REMIX,
            query = query,
            params = filter.params,
        ).body<JsonObject>()

        val cards = mutableListOf<JsonObject>()
        collectNamedRenderers(response, "musicCardShelfRenderer", cards)
        val topResult = cards.firstOrNull()?.let(::parseCardTopResult)

        // An artist card's rows say only "Song • 3:16"; the artist is stated
        // once on the card. Borrow that credit for those rows, then remove the
        // promoted song itself so the normal list does not repeat the card.
        val cardCredits = cards.mapNotNull { card ->
            val artist = artistCardTitle(card) ?: return@mapNotNull null
            val rows = mutableListOf<JsonObject>()
            collectRenderersDeep(card, rows)
            rows.mapNotNull(::parseTrackRenderer)
                .map { track -> track.id to artist }
        }.flatten().toMap()

        val renderers = mutableListOf<JsonObject>()
        collectRenderersDeep(response, renderers)

        return YouTubeSearchResults(
            topResult = topResult,
            tracks = renderers.mapNotNull(::parseTrackRenderer)
                .map { track -> cardCredits[track.id]?.let { track.copy(artist = it) } ?: track }
                .filterNot { it.id == topResult?.id }
                .distinctBy { it.id }
                .take(30),
        )
    }

    /**
     * YouTube Music's own typeahead: query completions plus the playable rows
     * that sit under them in the search dropdown.
     *
     * Kept separate from [search] so typing does not record every partial query
     * in history. The entity rows use the same renderer as search results, but
     * their subtitles start with a type label ("Song", "Video", ...), which the
     * track parser skips when picking the artist.
     */
    suspend fun searchSuggestions(
        input: String,
        signedIn: Boolean,
    ): YouTubeSearchSuggestions {
        val query = input.trim()
        if (query.isEmpty()) return YouTubeSearchSuggestions()

        val response = innerTube
            .getSearchSuggestions(
                client = YouTubeClient.WEB_REMIX,
                input = query,
                setLogin = signedIn,
            )
            .body<GetSearchSuggestionsResponse>()

        val queries = mutableListOf<String>()
        val tracks = mutableListOf<Track>()
        response.contents.orEmpty().forEach { section ->
            collectSuggestionContent(section, queries, tracks)
        }

        return YouTubeSearchSuggestions(
            queries = queries
                .filterNot { it.equals(query, ignoreCase = true) }
                .distinct()
                .take(6),
            tracks = tracks.distinctBy { it.id }.take(6),
        )
    }

    /**
     * Home-feed picks for the empty search screen.
     *
     * The guest feed exposes a "Quick picks" carousel; signed-in feeds add more
     * shelves, so a matching shelf is preferred over whichever one happens to
     * come first. Playlists and artist rows are skipped because only playable
     * tracks can be represented by [Track] here.
     */
    suspend fun homeRecommendations(limit: Int = 12): List<Track> =
        homeShelves().firstOrNull()?.tracks?.distinctBy { it.id }?.take(limit).orEmpty()

    /**
     * Every home-feed shelf, in YouTube's order, capped at [maxShelves] x
     * [perShelf]. Mirrors ArchiveTune's HomeScreen carousel structure.
     */
    suspend fun homeShelves(maxShelves: Int = 6, perShelf: Int = 12): List<YouTubeHomeShelf> {
        val response = innerTube
            .browse(
                client = YouTubeClient.WEB_REMIX,
                browseId = "FEmusic_home",
            )
            .body<JsonObject>()

        val shelves = mutableListOf<HomeShelf>()
        collectHomeShelves(response, shelves)
        return shelves
            .filterNot { shelf -> HOME_AVOID_MARKERS.any { marker -> marker in shelf.title.lowercase() } }
            .take(maxShelves)
            .map { shelf -> YouTubeHomeShelf(title = shelf.title, tracks = shelf.tracks.take(perShelf)) }
            .filter { it.tracks.isNotEmpty() }
    }

    private fun collectHomeShelves(element: JsonElement, output: MutableList<HomeShelf>) {
        when (element) {
            is JsonObject -> {
                val shelf = element["musicCarouselShelfRenderer"] as? JsonObject
                if (shelf != null) {
                    val renderers = mutableListOf<JsonObject>()
                    collectRenderersDeep(shelf, renderers)
                    val tracks = renderers
                        .mapNotNull(::parseTrackRenderer)
                        .distinctBy { it.id }
                    if (tracks.isNotEmpty()) {
                        output += HomeShelf(title = homeShelfTitle(shelf), tracks = tracks)
                    }
                } else {
                    element.values.forEach { collectHomeShelves(it, output) }
                }
            }

            is JsonArray -> element.forEach { collectHomeShelves(it, output) }

            else -> Unit
        }
    }

    private fun homeShelfTitle(shelf: JsonObject): String {
        val header = (shelf["header"] as? JsonObject)
            ?.get("musicCarouselShelfBasicHeaderRenderer") as? JsonObject
        val runs = (header?.get("title") as? JsonObject)?.get("runs") as? JsonArray
        return runs?.joinToString("") { run ->
            (run as? JsonObject)
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        }?.trim().orEmpty()
    }

    private fun parseTrackRenderer(rendererJson: JsonObject): Track? {
        // Try musicResponsiveListItemRenderer first (search results, browse rows).
        parseTrackRendererResponsive(rendererJson)?.let { return it }
        // Fallback: musicTwoRowItemRenderer (home feed carousel items).
        return parseTrackRendererTwoRow(rendererJson)
    }

    private fun parseTrackRendererResponsive(rendererJson: JsonObject): Track? =
        runCatching {
            val renderer = json.decodeFromJsonElement<MusicResponsiveListItemRenderer>(rendererJson)
            val columns = renderer.flexColumns.mapNotNull {
                it.musicResponsiveListItemFlexColumnRenderer.text?.runs
            }
            val title = columns.getOrNull(0)?.firstOrNull()?.text ?: return@runCatching null
            val subtitle = columns.getOrNull(1).orEmpty()
            val subtitleTexts = subtitle.map { it.text.trim() }.filter { it.isNotEmpty() }
            val artist = subtitleTexts.firstOrNull { text ->
                !isSuggestionMetadata(text) &&
                    parseDurationMs(text) == null &&
                    !text.contains("views", ignoreCase = true) &&
                    !text.contains("plays", ignoreCase = true)
            } ?: "Unknown artist"
            val videoId = renderer.playlistItemData?.videoId
                ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
                ?: return@runCatching null
            val artwork = renderer.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.lastOrNull()
                ?.url
            val duration = subtitleTexts
                .mapNotNull(::parseDurationMs)
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

    /** Parses musicTwoRowItemRenderer — the compact card used in browse home shelves. */
    private fun parseTrackRendererTwoRow(rendererJson: JsonObject): Track? =
        runCatching {
            val twoRow = rendererJson["musicTwoRowItemRenderer"] as? JsonObject ?: return@runCatching null
            val titleRuns = (twoRow["title"] as? JsonObject)?.get("runs") as? JsonArray
            val title = titleRuns?.firstOrNull()
                ?.let { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val subtitleRuns = (twoRow["subtitle"] as? JsonObject)?.get("runs") as? JsonArray
            val subtitleTexts = subtitleRuns.orEmpty().mapNotNull {
                (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull?.trim()
            }.filter { it.isNotBlank() }
            val artist = subtitleTexts.firstOrNull { text ->
                !isSuggestionMetadata(text) &&
                    parseDurationMs(text) == null &&
                    !text.contains("views", ignoreCase = true) &&
                    !text.contains("plays", ignoreCase = true)
            } ?: "Unknown artist"
            val videoId = ((twoRow["navigationEndpoint"] as? JsonObject)
                ?.get("watchEndpoint") as? JsonObject)
                ?.get("videoId")
                ?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null
            val artwork = ((twoRow["thumbnail"] as? JsonObject)
                ?.get("musicThumbnailRenderer") as? JsonObject)
                ?.get("thumbnail") as? JsonObject
            val thumbs = (artwork?.get("thumbnails") as? JsonArray)
            val artworkUrl = thumbs?.lastOrNull()
                ?.let { (it as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull }
                ?: ""
            val duration = subtitleTexts.mapNotNull(::parseDurationMs).firstOrNull() ?: 0L

            Track(
                id = "yt:$videoId",
                title = title,
                artist = artist,
                durationMs = duration,
                artworkUri = artworkUrl,
                videoId = videoId,
                isLocal = false,
            )
        }.getOrNull()

    private fun collectSuggestionContent(
        element: JsonElement,
        queries: MutableList<String>,
        tracks: MutableList<Track>,
    ) {
        when (element) {
            is JsonObject -> {
                (element["searchSuggestionRenderer"] as? JsonObject)?.let { renderer ->
                    suggestionQuery(renderer)?.let(queries::add)
                }
                (element["musicResponsiveListItemRenderer"] as? JsonObject)?.let { renderer ->
                    parseTrackRenderer(renderer)?.let(tracks::add)
                }
                element.values.forEach { collectSuggestionContent(it, queries, tracks) }
            }

            is JsonArray -> element.forEach {
                collectSuggestionContent(it, queries, tracks)
            }

            else -> Unit
        }
    }

    private fun suggestionQuery(renderer: JsonObject): String? {
        val endpoint = renderer["navigationEndpoint"] as? JsonObject
        val endpointQuery = endpoint?.get("searchEndpoint") as? JsonObject
        val fromEndpoint = endpointQuery
            ?.get("query")
            ?.jsonPrimitive
            ?.contentOrNull
        if (!fromEndpoint.isNullOrBlank()) return fromEndpoint

        val runs = (renderer["suggestion"] as? JsonObject)
            ?.get("runs") as? JsonArray
        return runs
            ?.joinToString("") { run ->
                (run as? JsonObject)
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    .orEmpty()
            }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun artistCardTitle(card: JsonObject): String? {
        val tap = card["onTap"] as? JsonObject ?: return null
        val browse = tap["browseEndpoint"] as? JsonObject ?: return null
        if (!isArtistEndpoint(browse)) return null
        return textRuns(card["title"]).takeIf { it.isNotBlank() }
    }

    /**
     * Reads the promoted card at the head of an All search.
     *
     * A song card carries its own watch endpoint, so the card itself is the
     * result. An artist card only carries a browse endpoint; its first nested
     * row is the promoted song, and the card title is the credit that row
     * omits. Album and playlist cards have no playable top result.
     */
    private fun parseCardTopResult(card: JsonObject): Track? {
        val endpoint = card["onTap"] as? JsonObject
        val watch = endpoint?.get("watchEndpoint") as? JsonObject
        if (watch != null) return parseCardTrack(card, watch)

        val browse = endpoint?.get("browseEndpoint") as? JsonObject
        if (browse != null && isArtistEndpoint(browse)) {
            val rows = mutableListOf<JsonObject>()
            collectRenderersDeep(card, rows)
            val track = rows.firstOrNull()?.let(::parseTrackRenderer) ?: return null
            val artist = textRuns(card["title"])
            return if (artist.isNotBlank()) track.copy(artist = artist) else track
        }
        return null
    }

    private fun parseCardTrack(card: JsonObject, watch: JsonObject): Track? {
        val videoId = (watch["videoId"] as? JsonPrimitive)?.contentOrNull ?: return null
        val title = textRuns(card["title"]).takeIf { it.isNotBlank() } ?: return null
        val subtitleTexts = textRunsList(card["subtitle"])
        val artist = subtitleTexts.firstOrNull { text ->
            !isSuggestionMetadata(text) &&
                parseDurationMs(text) == null &&
                !text.contains("views", ignoreCase = true) &&
                !text.contains("plays", ignoreCase = true)
        } ?: "Unknown artist"
        val duration = subtitleTexts.mapNotNull(::parseDurationMs).firstOrNull() ?: 0L

        return Track(
            id = "yt:$videoId",
            title = title,
            artist = artist,
            durationMs = duration,
            artworkUri = musicThumbnailUrl(card),
            videoId = videoId,
            isLocal = false,
        )
    }

    private fun isArtistEndpoint(browse: JsonObject): Boolean {
        val configs = browse["browseEndpointContextSupportedConfigs"] as? JsonObject
        val musicConfig = configs?.get("browseEndpointContextMusicConfig") as? JsonObject
        val pageType = musicConfig?.get("pageType")?.jsonPrimitive?.contentOrNull
        return pageType == "MUSIC_PAGE_TYPE_ARTIST"
    }

    private fun textRuns(element: JsonElement?): String {
        val runs = (element as? JsonObject)?.get("runs") as? JsonArray
        return runs?.joinToString("") { run ->
            (run as? JsonObject)
                ?.get("text")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        }.orEmpty()
    }

    private fun textRunsList(element: JsonElement?): List<String> = textRuns(element)
        .split(" • ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private fun musicThumbnailUrl(owner: JsonObject): String? {
        val thumbnailContainer = owner["thumbnail"] as? JsonObject
        val renderer = thumbnailContainer?.get("musicThumbnailRenderer") as? JsonObject
        val thumbnail = renderer?.get("thumbnail") as? JsonObject
        val thumbnails = thumbnail?.get("thumbnails") as? JsonArray
        return thumbnails
            ?.lastOrNull()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
    }

    private fun collectRenderersDeep(element: JsonElement, output: MutableList<JsonObject>) {
        // Home feed uses musicResponsiveListItemRenderer (search results) AND
        // musicTwoRowItemRenderer (browse shelves) — collect both.
        collectNamedRenderers(element, "musicResponsiveListItemRenderer", output)
        collectNamedRenderers(element, "musicTwoRowItemRenderer", output)
    }

    private fun collectNamedRenderers(
        element: JsonElement,
        key: String,
        output: MutableList<JsonObject>,
    ) {
        when (element) {
            is JsonObject -> {
                (element[key] as? JsonObject)?.let(output::add)
                element.values.forEach { collectNamedRenderers(it, key, output) }
            }

            is JsonArray -> element.forEach { collectNamedRenderers(it, key, output) }
            else -> Unit
        }
    }

    suspend fun resolveAudio(
        videoId: String,
        quality: YouTubeAudioQuality = YouTubeAudioQuality.High,
    ): ResolvedAudio {
        // The extractor walks its own client catalogue and retries across
        // identities, which is the part that actually recovers a track. Pinning
        // a client with playbackClientOverrideId is avoided on purpose: it caps
        // the request budget to one and forces PoToken minting on web clients
        // with no token provider, so a resolvable track comes back
        // NO_PLAYABLE_STREAM.
        val stream = try {
            extractor.extract(
                videoId = videoId,
                hints = ContentHints(wantVideo = false)
                    .withStreamCapabilities(allowHls = false, allowSabr = false),
                audioQuality = quality.toInnerTubeQuality(),
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Age gates and takedowns never soften; everything else is worth one
            // more pass, because a refusal is often per-client rather than
            // per-track.
            val retryable = error !is StreamResolveException ||
                error.reason !in TERMINAL_REASONS
            if (!retryable) throw error
            val retry = runCatching {
                extractor.extract(
                    videoId = videoId,
                    hints = ContentHints(wantVideo = false)
                        .withStreamCapabilities(allowHls = false, allowSabr = false),
                    audioQuality = quality.toInnerTubeQuality(),
                    excludedClients = excludedFrom(error),
                )
            }.getOrNull()
            retry ?: throw error
        } ?: error("YouTube Music returned no playable audio stream.")

        // The URL is signed against the client identity that minted it, so its
        // own headers are used exactly as given. Rewriting the User-Agent here
        // — even to a newer one — breaks the signature and the URL answers 403.
        val requestHeaders = stream.headers

        // Nothing is handed to the player, or cached, until bytes have been
        // fetched from the URL.
        val probe = probeStream(
            url = stream.audioUrl,
            requestHeaders = requestHeaders,
            rangeBytes = if (stream.useRangeChunks) {
                stream.rangeChunkSizeBytes
            } else {
                1L
            },
        )
        // An unreachable URL may still be good — the device was offline, or the
        // request timed out. Refusal is the server's own answer.
        if (probe == StreamProbe.REFUSED) {
            throw StreamResolveException(
                StreamResolveException.Reason.NO_PLAYABLE_STREAM,
                "Stream URL refused a byte-range probe.",
            )
        }

        RESOLVED_HEADERS[stream.audioUrl] = requestHeaders
        // The player and the tempo decoder fetch this URL again later, so the
        // headers it was signed against have to be reachable then too.
        StreamHeaders.put(stream.audioUrl, requestHeaders)
        return ResolvedAudio(
            url = stream.audioUrl,
            mimeType = stream.mimeType,
            headers = requestHeaders,
            codec = AudioInfo.codecLabel(stream.mimeType),
            contentLengthBytes = stream.contentLengthBytes,
        )
    }

    /**
     * The clients a failed attempt already tried, so a retry does not repeat it.
     *
     * The extractor reports which identities it walked; excluding them is what
     * makes the second pass reach clients the first one did not.
     */
    private fun excludedFrom(error: Exception): Set<String> {
        val diagnostics = (error as? StreamResolveException)?.diagnostics ?: return emptySet()
        return diagnostics.attempts.mapTo(mutableSetOf()) { it.clientName }
    }

    /**
     * Fetches a byte range from [url] to confirm it is really serving audio.
     *
     * A URL can be dead on arrival for reasons no amount of care predicts, so
     * this runs before anything is cached or handed to ExoPlayer.
     */
    private suspend fun probeStream(
        url: String,
        requestHeaders: Map<String, String>,
        rangeBytes: Long,
    ): StreamProbe {
        val status = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            runCatching {
                httpClient.get(url) {
                    requestHeaders.forEach { (key, value) -> header(key, value) }
                    // Ask for one byte, not a page. A full GET of a 4 MB stream
                    // to answer "is this alive" is a waste of the user's data.
                    // Chunked clients refuse a range that is not their own size.
                    header("Range", "bytes=0-${(rangeBytes - 1).coerceAtLeast(0L)}")
                }.status
            }.getOrNull()
        } ?: return StreamProbe.UNREACHABLE
        return if (status.value in 200..299 || status.value == 206) {
            StreamProbe.OK
        } else {
            StreamProbe.REFUSED
        }
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
        knownContentLengthBytes: Long? = null,
    ): Int? = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext null
        // A HEAD is a second chance to be refused by a URL that already played
        // fine, so the size the extractor already knows is preferred.
        val lengthBytes = knownContentLengthBytes?.takeIf { it > 0L } ?: withTimeoutOrNull(3_000) {
            runCatching {
                httpClient.head(url) {
                    (requestHeaders.ifEmpty { StreamHeaders.forUri(url).orEmpty() })
                        .forEach { (key, value) -> header(key, value) }
                }.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            }.getOrNull()
        } ?: return@withContext null
        (lengthBytes * 8 / durationMs).toInt().coerceIn(1, Int.MAX_VALUE)
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

    private fun isSuggestionMetadata(text: String): Boolean =
        text.all { it == '•' || it.isWhitespace() } ||
            text.lowercase() in SEARCH_SUGGESTION_LABELS

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
