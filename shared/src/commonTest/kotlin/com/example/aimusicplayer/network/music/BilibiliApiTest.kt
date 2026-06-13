package com.example.aimusicplayer.network.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BilibiliApiTest {

    @Test
    fun `platform name is bilibili`() {
        val api = BilibiliApi()
        assertEquals("bilibili", api.platform)
    }

    @Test
    fun `getLyrics returns null for bilibili`(): Unit = kotlinx.coroutines.test.runTest {
        // Bilibili has no native lyrics API
        // We can't really test this without a mock, but we verify constructor doesn't crash
        val api = BilibiliApi()
        assertNull(api.getLyrics("any", "bilibili"))
    }
}
