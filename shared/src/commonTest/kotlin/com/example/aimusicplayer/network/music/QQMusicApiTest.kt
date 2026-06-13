package com.example.aimusicplayer.network.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QQMusicApiTest {

    @Test
    fun `stripJsonp removes callback wrapper`() {
        val input = "callback({\"code\":0})"
        val result = stripJsonp(input)
        assertEquals("{\"code\":0}", result)
    }

    @Test
    fun `stripJsonp handles numbered callback`() {
        val input = "jsonp12345({\"data\":\"test\"})"
        val result = stripJsonp(input)
        assertEquals("{\"data\":\"test\"}", result)
    }

    @Test
    fun `stripJsonp leaves pure JSON unchanged`() {
        val input = """{"code":0,"data":{"song":{"list":[]}}}"""
        val result = stripJsonp(input)
        assertEquals(input, result)
    }

    @Test
    fun `stripJsonp leaves JSON array unchanged`() {
        val input = """[{"a":1},{"b":2}]"""
        val result = stripJsonp(input)
        assertEquals(input, result)
    }

    @Test
    fun `stripJsonp handles empty input`() {
        val result = stripJsonp("")
        assertEquals("", result)
    }

    @Test
    fun `stripJsonp handles JSON with nested parens`() {
        val input = """callback({"data":"test(value)"})"""
        val result = stripJsonp(input)
        assertEquals("""{"data":"test(value)"}""", result)
    }

    @Test
    fun `stripJsonp handles JSON with escaped quotes`() {
        val input = """callback({"key":"val\"ue"})"""
        val result = stripJsonp(input)
        assertTrue(result.contains("val\\\"ue") || result.contains("val\"ue"))
    }

    @Test
    fun `platform name is qq`() {
        val api = QQMusicApi()
        assertEquals("qq", api.platform)
    }
}
