package com.tuneplayer.app.data.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the YouTube home/search parsers.
 *
 * These functions are the app's front door for YouTube data; three shipped
 * bugs (blank Quick picks titles, dropped responsive items, first-run-only
 * title parsing) all lived here with no coverage.
 */
class YouTubeParserTest {

    private val repository = YouTubeMusicRepository()
    private val json = Json

    private fun responsiveRenderer(
        title: String? = "Thriller",
        artist: String? = "Michael Jackson",
        extraSubtitle: String? = null,
        videoId: String? = "Z85lxckrtzg",
        usePlaylistItemData: Boolean = true,
    ): JsonObject {
        val subtitleRuns = buildList {
            artist?.let { add(buildJsonObject { put("text", it) }) }
            extraSubtitle?.let { add(buildJsonObject { put("text", it) }) }
        }
        return buildJsonObject {
            putJsonObject("musicResponsiveListItemRenderer") {
                putJsonArray("flexColumns") {
                    add(
                        buildJsonObject {
                            putJsonObject("musicResponsiveListItemFlexColumnRenderer") {
                                putJsonObject("text") {
                                    putJsonArray("runs") {
                                        title?.let {
                                            add(buildJsonObject { put("text", it) })
                                        }
                                    }
                                }
                            }
                        },
                    )
                    add(
                        buildJsonObject {
                            putJsonObject("musicResponsiveListItemFlexColumnRenderer") {
                                putJsonObject("text") {
                                    putJsonArray("runs") { subtitleRuns.forEach { add(it) } }
                                }
                            }
                        },
                    )
                }
                if (usePlaylistItemData) {
                    putJsonObject("playlistItemData") { put("videoId", videoId) }
                } else {
                    putJsonObject("navigationEndpoint") {
                        putJsonObject("watchEndpoint") { put("videoId", videoId) }
                    }
                }
                putJsonObject("thumbnail") {
                    putJsonObject("musicThumbnailRenderer") {
                        putJsonObject("thumbnail") {
                            putJsonArray("thumbnails") {
                                add(buildJsonObject { put("url", "https://img/small") })
                                add(buildJsonObject { put("url", "https://img/big") })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun twoRowRenderer(
        title: String? = "santai",
        subtitle: String = "Irudiana00 • 5.2M views",
        videoId: String? = null,
    ): JsonObject = buildJsonObject {
        putJsonObject("musicTwoRowItemRenderer") {
            putJsonObject("title") {
                putJsonArray("runs") {
                    title?.let { add(buildJsonObject { put("text", it) }) }
                }
            }
            putJsonObject("subtitle") {
                putJsonArray("runs") {
                    add(buildJsonObject { put("text", subtitle) })
                }
            }
            putJsonObject("navigationEndpoint") {
                videoId?.let { videoId ->
                    putJsonObject("watchEndpoint") { put("videoId", videoId) }
                } ?: putJsonObject("browseEndpoint") { put("browseId", "VLPL123") }
            }
            putJsonObject("thumbnail") {
                putJsonObject("musicThumbnailRenderer") {
                    putJsonObject("thumbnail") {
                        putJsonArray("thumbnails") {
                            add(buildJsonObject { put("url", "https://img/cover") })
                        }
                    }
                }
            }
        }
    }

    // --- parseTrackRendererResponsive ---

    @Test
    fun `responsive renderer parses title artist videoId artwork`() {
        val track = repository.parseTrackRendererResponsive(responsiveRenderer())
        assertEquals("Thriller", track?.title)
        assertEquals("Michael Jackson", track?.artist)
        assertEquals("Z85lxckrtzg", track?.videoId)
        assertEquals("https://img/big", track?.artworkUri)
    }

    @Test
    fun `responsive renderer joins multi-run titles`() {
        val track = repository.parseTrackRendererResponsive(
            buildJsonObject {
                putJsonObject("musicResponsiveListItemRenderer") {
                    putJsonArray("flexColumns") {
                        add(
                            buildJsonObject {
                                putJsonObject("musicResponsiveListItemFlexColumnRenderer") {
                                    putJsonObject("text") {
                                        putJsonArray("runs") {
                                            add(buildJsonObject { put("text", "Hello ") })
                                            add(buildJsonObject { put("text", "World") })
                                        }
                                    }
                                }
                            },
                        )
                    }
                    putJsonObject("playlistItemData") { put("videoId", "abc") }
                }
            },
        )
        assertEquals("Hello World", track?.title)
    }

    @Test
    fun `responsive renderer with missing navigationEndpoint still parses`() {
        // Regression: all 20 Quick picks items lack navigationEndpoint; the
        // typed decode path used to drop every one of them.
        val track = repository.parseTrackRendererResponsive(responsiveRenderer())
        assertEquals("Thriller", track?.title)
    }

    @Test
    fun `responsive renderer falls back to watchEndpoint when playlistItemData absent`() {
        val track = repository.parseTrackRendererResponsive(
            responsiveRenderer(usePlaylistItemData = false),
        )
        assertEquals("Z85lxckrtzg", track?.videoId)
    }

    @Test
    fun `responsive renderer with blank title yields empty not artist`() {
        val track = repository.parseTrackRendererResponsive(responsiveRenderer(title = null))
        assertEquals("", track?.title)
        assertEquals("Michael Jackson", track?.artist)
    }

    @Test
    fun `responsive renderer without any renderer key returns null`() {
        val track = repository.parseTrackRendererResponsive(buildJsonObject { put("other", 1) })
        assertNull(track)
    }

    // --- parseTrackRendererTwoRow ---

    @Test
    fun `twoRow playlist card parses title artist and empty videoId`() {
        val track = repository.parseTrackRendererTwoRow(twoRowRenderer())
        assertEquals("santai", track?.title)
        assertEquals("Irudiana00", track?.artist)
        assertEquals("", track?.videoId)
        assertEquals("https://img/cover", track?.artworkUri)
    }

    @Test
    fun `twoRow song card extracts videoId from watchEndpoint`() {
        val track = repository.parseTrackRendererTwoRow(
            twoRowRenderer(title = "bloodstream", videoId = "CLSuAhT71yU"),
        )
        assertEquals("bloodstream", track?.title)
        assertEquals("CLSuAhT71yU", track?.videoId)
    }

    @Test
    fun `twoRow with blank title still returns track`() {
        val track = repository.parseTrackRendererTwoRow(twoRowRenderer(title = ""))
        assertEquals("", track?.title)
    }

    @Test
    fun `twoRow without renderer key returns null`() {
        assertNull(repository.parseTrackRendererTwoRow(buildJsonObject { put("x", 1) }))
    }

    // --- subtitleArtist + parseDurationMs ---

    @Test
    fun `subtitleArtist skips views and durations`() {
        assertEquals(
            "Dadang Rustiawan",
            repository.subtitleArtist(listOf("Dadang Rustiawan", "17M views")),
        )
        assertEquals("Unknown artist", repository.subtitleArtist(emptyList()))
        assertEquals("Unknown artist", repository.subtitleArtist(listOf("3:45", "1.2M views")))
    }

    @Test
    fun `parseDurationMs handles m ss and h mm ss`() {
        assertEquals(205_000L, repository.parseDurationMs("3:25"))
        assertEquals(3_725_000L, repository.parseDurationMs("1:02:05"))
        assertNull(repository.parseDurationMs("5.2M views"))
        assertNull(repository.parseDurationMs("3:75"))
        assertNull(repository.parseDurationMs(""))
    }

    // --- parseTrackRenderer dispatch ---

    @Test
    fun `dispatch picks responsive when flexColumns present`() {
        val track = repository.parseTrackRenderer(responsiveRenderer())
        assertEquals("Thriller", track?.title)
    }

    @Test
    fun `dispatch picks twoRow when responsive missing`() {
        val track = repository.parseTrackRenderer(twoRowRenderer())
        assertEquals("santai", track?.title)
    }
}
