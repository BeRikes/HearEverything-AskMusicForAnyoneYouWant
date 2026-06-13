package com.example.aimusicplayer.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiCommandTest {

    @Test
    fun `toSteps returns actions list when present`() {
        val cmd = AiCommand(
            actions = listOf(
                AiStep(action = "search_play", songName = "Lemon", artist = "Kenshi Yonezu"),
                AiStep(action = "open_lyrics")
            )
        )
        val steps = cmd.toSteps()
        assertEquals(2, steps.size)
        assertEquals("search_play", steps[0].action)
        assertEquals("Lemon", steps[0].songName)
        assertEquals("Kenshi Yonezu", steps[0].artist)
        assertEquals("open_lyrics", steps[1].action)
    }

    @Test
    fun `toSteps handles empty actions list`() {
        val cmd = AiCommand(actions = emptyList())
        val steps = cmd.toSteps()
        // empty actions list (not null) is treated as empty, but toSteps()
        // checks `!actions.isNullOrEmpty()` - an empty list IS empty, so
        // it falls through to the legacy action check which yields unknown
        assertEquals(AiCommand.ACTION_UNKNOWN, steps[0].action)
    }

    @Test
    fun `toSteps with legacy single action`() {
        val cmd = AiCommand(
            action = "search_play",
            query = "夜曲 周杰伦"
        )
        val steps = cmd.toSteps()
        assertEquals(1, steps.size)
        assertEquals("search_play", steps[0].action)
        assertEquals("夜曲 周杰伦", steps[0].query)
    }

    @Test
    fun `toSteps with control legacy action`() {
        val cmd = AiCommand(
            action = "control",
            controlAction = "pause"
        )
        val steps = cmd.toSteps()
        assertEquals(1, steps.size)
        assertEquals("control", steps[0].action)
        assertEquals("pause", steps[0].controlAction)
    }

    @Test
    fun `toSteps with null action returns unknown`() {
        val cmd = AiCommand(action = null)
        val steps = cmd.toSteps()
        assertEquals(1, steps.size)
        assertEquals(AiCommand.ACTION_UNKNOWN, steps[0].action)
    }

    @Test
    fun `toSteps with blank action returns unknown`() {
        val cmd = AiCommand(action = "")
        val steps = cmd.toSteps()
        assertEquals(1, steps.size)
        assertEquals(AiCommand.ACTION_UNKNOWN, steps[0].action)
    }

    @Test
    fun `toSteps with import_playlist legacy action`() {
        val cmd = AiCommand(
            action = "import_playlist",
            url = "https://music.163.com/playlist?id=123"
        )
        val steps = cmd.toSteps()
        assertEquals(1, steps.size)
        assertEquals("import_playlist", steps[0].action)
        assertEquals("https://music.163.com/playlist?id=123", steps[0].url)
    }

    @Test
    fun `toSteps with set_quality legacy action`() {
        val cmd = AiCommand(
            action = "set_quality",
            quality = "lossless"
        )
        val steps = cmd.toSteps()
        assertEquals(1, steps.size)
        assertEquals("set_quality", steps[0].action)
        assertEquals("lossless", steps[0].quality)
    }

    @Test
    fun `AiCommand companion constants match AiStep constants`() {
        assertEquals(AiStep.ACTION_SEARCH_PLAY, AiCommand.ACTION_SEARCH_PLAY)
        assertEquals(AiStep.ACTION_DOWNLOAD_CURRENT, AiCommand.ACTION_DOWNLOAD_CURRENT)
        assertEquals(AiStep.ACTION_CONTROL, AiCommand.ACTION_CONTROL)
        assertEquals(AiStep.ACTION_SET_QUALITY, AiCommand.ACTION_SET_QUALITY)
        assertEquals(AiStep.ACTION_OPEN_LYRICS, AiCommand.ACTION_OPEN_LYRICS)
        assertEquals(AiStep.ACTION_OPEN_NOW_PLAYING, AiCommand.ACTION_OPEN_NOW_PLAYING)
        assertEquals(AiStep.ACTION_IMPORT_PLAYLIST, AiCommand.ACTION_IMPORT_PLAYLIST)
        assertEquals(AiStep.ACTION_AUTO_IMPORT_PLAYLIST, AiCommand.ACTION_AUTO_IMPORT_PLAYLIST)
        assertEquals(AiStep.ACTION_UNKNOWN, AiCommand.ACTION_UNKNOWN)
    }

    @Test
    fun `AiStep null defaults`() {
        val step = AiStep(action = "search_play")
        assertEquals(null, step.query)
        assertEquals(null, step.songName)
        assertEquals(null, step.artist)
        assertEquals(null, step.controlAction)
        assertEquals(null, step.quality)
        assertEquals(null, step.url)
    }
}
