package com.tuneplayer.app.data.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checks for the image-reference rules Discord enforces.
 *
 * These are the whole reason presence artwork was invisible: a plain https URL
 * is not an acceptable `assets.large_image`, and only Discord-hosted paths pass
 * through untouched.
 */
class DiscordAssetRegistrarTest {

    @Test
    fun `discord cdn url becomes an mp path`() {
        assertEquals(
            "mp:icons/abc.png",
            DiscordAssetRegistrar.parseDiscordCdn("https://cdn.discordapp.com/icons/abc.png"),
        )
    }

    @Test
    fun `media cdn url becomes an mp path`() {
        assertEquals(
            "mp:attachments/xyz.webp",
            DiscordAssetRegistrar.parseDiscordCdn(
                "https://media.discordapp.net/attachments/xyz.webp",
            ),
        )
    }

    @Test
    fun `http cdn url is accepted too`() {
        assertEquals(
            "mp:emojis/1.png",
            DiscordAssetRegistrar.parseDiscordCdn("http://cdn.discordapp.com/emojis/1.png"),
        )
    }

    @Test
    fun `remote artwork url is not discord hosted`() {
        assertNull(
            DiscordAssetRegistrar.parseDiscordCdn(
                "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            ),
        )
    }

    @Test
    fun `blank url is not discord hosted`() {
        assertNull(DiscordAssetRegistrar.parseDiscordCdn(""))
    }
}
