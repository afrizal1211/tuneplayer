package com.tuneplayer.app.data.discord

import com.tuneplayer.app.log.LogCatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers remote artwork URLs with Discord so they can be shown in presence.
 *
 * Discord only accepts two kinds of image reference: a key of an asset uploaded
 * to an application, or `mp:<path>` for an asset registered through the
 * external-assets API. A plain `https://` URL is rejected outright, which is why
 * artwork was missing: the URL was being sent as-is.
 *
 * Registration is per application id and costs one API call per URL, so results
 * are cached for the process. A failure returns null and the caller simply
 * omits the artwork — presence must never fail because an image did.
 */
object DiscordAssetRegistrar {

    private const val TAG = "DiscordAssetRegistrar"
    private const val API_BASE = "https://discord.com/api/v10"

    private val client = HttpClient(OkHttp)
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Resolves [imageUrl] to a value Discord accepts in `assets.large_image`,
     * or null when the image cannot be registered.
     *
     * [applicationId] is the developer application that will own the asset.
     * [accessToken] is the same user token that identifies the gateway session.
     */
    suspend fun resolveImage(
        applicationId: String,
        accessToken: String,
        imageUrl: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext null
        if (applicationId.isBlank() || accessToken.isBlank()) return@withContext null

        // Already-registered Discord CDN paths pass straight through.
        parseDiscordCdn(imageUrl)?.let { return@withContext it }

        cache[imageUrl]?.let { return@withContext "mp:$it" }

        val registered = registerExternal(applicationId, accessToken, imageUrl)
        // A registration failure is not a reason to drop the artwork: Discord
        // accepts a plain https URL for large_image on a gateway activity, so
        // the raw URL is the fallback and the card keeps its sleeve.
        if (registered == null) return@withContext imageUrl
        cache[imageUrl] = registered
        "mp:$registered"
    }

    /** Discards cached registrations, for when the token or app id changes. */
    fun invalidate() {
        cache.clear()
    }

    /**
     * Strips a cdn.discordapp.com URL down to its `mp:`-prefixed path, or null
     * when [imageUrl] is not Discord-hosted.
     */
    internal fun parseDiscordCdn(imageUrl: String): String? {
        val prefixes = listOf(
            "https://cdn.discordapp.com/",
            "http://cdn.discordapp.com/",
            "https://media.discordapp.net/",
            "http://media.discordapp.net/",
        )
        val prefix = prefixes.firstOrNull { imageUrl.startsWith(it, ignoreCase = true) }
            ?: return null
        return "mp:" + imageUrl.substring(prefix.length)
    }

    private suspend fun registerExternal(
        applicationId: String,
        accessToken: String,
        imageUrl: String,
    ): String? {
        val payload = JSONObject().apply {
            put("urls", JSONArray().put(imageUrl))
        }
        return runCatching {
            val response = client.post("$API_BASE/applications/$applicationId/external-assets") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(payload.toString())
            }
            val body = response.bodyAsText()
            if (response.status.value !in 200..299) {
                LogCatcher.w(TAG, "external-assets ${response.status.value}: $body")
                return null
            }
            val array = JSONArray(body)
            if (array.length() == 0) return null
            array.getJSONObject(0).optString("external_asset_path").takeIf { it.isNotBlank() }
        }.onFailure {
            LogCatcher.w(TAG, "external-assets failed: ${it.message}")
        }.getOrNull()
    }
}
