package com.example.aimusicplayer.network.music

import kotlin.test.Test
import kotlin.test.assertEquals

class NeteaseApiTest {

    @Test
    fun `platform name is netease`() {
        val api = NeteaseApi()
        assertEquals("netease", api.platform)
    }
}
