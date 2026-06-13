package com.example.aimusicplayer.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAssistantTest {

    @Test
    fun `extractJsonObject from plain JSON`() {
        val result = AiAssistant.extractJsonObjectStatic("""{"action":"search_play","query":"夜曲"}""")
        assertNotNull(result)
        assertEquals("""{"action":"search_play","query":"夜曲"}""", result)
    }

    @Test
    fun `extractJsonObject from markdown code block`() {
        val input = """
            ```json
            {"action":"control","controlAction":"pause"}
            ```
        """.trimIndent()
        val result = AiAssistant.extractJsonObjectStatic(input)
        assertNotNull(result)
        assertTrue(result.contains("control"))
        assertTrue(result.contains("pause"))
    }

    @Test
    fun `extractJsonObject from text with JSON embedded`() {
        val result = AiAssistant.extractJsonObjectStatic(
            "Here is your result: {\"action\":\"search_play\",\"songName\":\"Lemon\"} hope you like it"
        )
        assertNotNull(result)
        assertTrue(result.contains("search_play"))
        assertTrue(result.contains("Lemon"))
    }

    @Test
    fun `extractJsonObject with array JSON`() {
        val result = AiAssistant.extractJsonObjectStatic("""[{"action":"search_play","query":"test"}]""")
        assertNotNull(result)
        assertEquals("""[{"action":"search_play","query":"test"}]""", result)
    }

    @Test
    fun `extractJsonObject with nested braces`() {
        val result = AiAssistant.extractJsonObjectStatic(
            """{"actions":[{"action":"search_play","songName":"Lemon","artist":"Kenshi Yonezu"},{"action":"open_lyrics"}]}"""
        )
        assertNotNull(result)
        assertTrue(result.contains("actions"))
        assertTrue(result.contains("Kenshi Yonezu"))
    }

    @Test
    fun `extractJsonObject with escaped quotes in JSON`() {
        val result = AiAssistant.extractJsonObjectStatic(
            """{"action":"search_play","query":"Hello \"World\""}"""
        )
        assertNotNull(result)
        assertTrue(result.contains("Hello \\\"World\\\""))
    }

    @Test
    fun `extractJsonObject returns null for plain text`() {
        val result = AiAssistant.extractJsonObjectStatic("This is just plain text with no JSON")
        assertNull(result)
    }

    @Test
    fun `extractJsonObject returns null for empty string`() {
        assertNull(AiAssistant.extractJsonObjectStatic(""))
    }

    @Test
    fun `extractJsonObject handles markdown code block without language`() {
        val input = """
            ```
            {"action":"search_play","query":"test"}
            ```
        """.trimIndent()
        val result = AiAssistant.extractJsonObjectStatic(input)
        assertNotNull(result)
        assertTrue(result.contains("search_play"))
    }

    @Test
    fun `extractJsonObject handles multi-line JSON`() {
        val result = AiAssistant.extractJsonObjectStatic("""
        {
            "action": "search_play",
            "songName": "Lemon",
            "artist": "Kenshi Yonezu"
        }
        """)
        assertNotNull(result)
        assertTrue(result.contains("Lemon"))
    }

    @Test
    fun `extractJsonObject with import_playlist command`() {
        val result = AiAssistant.extractJsonObjectStatic(
            """{"action":"import_playlist","url":"https://music.163.com/playlist?id=123"}"""
        )
        assertNotNull(result)
        assertTrue(result.contains("import_playlist"))
        assertTrue(result.contains("music.163.com"))
    }
}
