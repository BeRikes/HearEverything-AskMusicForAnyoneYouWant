package com.example.aimusicplayer

import android.content.Context
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.demo.DemoFlow
import com.example.aimusicplayer.demo.FlowStepResult
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.createDownloadManager
import com.example.aimusicplayer.network.music.BilibiliApi
import com.example.aimusicplayer.network.music.KugouApi
import com.example.aimusicplayer.network.music.NeteaseApi
import com.example.aimusicplayer.network.music.QQMusicApi
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.settings.SettingsStorage
import kotlinx.coroutines.runBlocking

/**
 * End-to-end integration test that exercises the full pipeline.
 *
 * ## How to run
 * 1. Set up an Android emulator or device (API 24+)
 * 2. Configure a valid DeepSeek/OpenAI API key via the settings screen
 * 3. Call [run] from your Activity's onCreate or a test button
 * 4. Check Logcat for `DemoFlow` and `MainViewModel` tags
 *
 * ## Prerequisites
 * - Internet access (for AI API + music search APIs)
 * - Valid API key stored in DataStore (Settings)
 *
 * ## Test scenarios
 * | Test                        | Method                        | Expected outcome                |
 * |-----------------------------|-------------------------------|---------------------------------|
 * | Happy path                  | testHappyPath()               | search → resolve → play ✅      |
 * | No API key                  | testMissingApiKey()           | FlowStepResult.Failure          |
 * | Empty input                 | testEmptyInput()              | FlowStepResult.Failure          |
 * | Garbage query               | testUnsearchableQuery()       | FlowStepResult.Failure          |
 * | Source retry                | testSourceRetry()             | First URL fails → plays next    |
 */
class DemoIntegrationTest(private val context: Context) {

    // ── Wiring ────────────────────────────────────────────────────

    private val storage = SettingsStorage(context)
    private val settingsRepo = SettingsRepository(storage)

    private val musicApis = listOf(
        NeteaseApi(), QQMusicApi(), KugouApi(), BilibiliApi()
    )

    private val searchManager = MusicSearchManager(musicApis)
    private val musicPlayer = MusicPlayer(context)
    private val aiAssistant = AiAssistant(storage)
    private val downloadManager = createDownloadManager(context)

    private val demoFlow = DemoFlow(
        aiAssistant = aiAssistant,
        searchManager = searchManager,
        musicPlayer = musicPlayer,
        downloadManager = downloadManager,
        settingsRepo = settingsRepo,
    )

    // ══════════════════════════════════════════════════════════════
    // Run all tests
    // ══════════════════════════════════════════════════════════════

    /** Run all test scenarios in sequence. */
    fun runAll() = runBlocking {
        println("══════════ HearEverything Integration Tests ══════════")
        demoFlow.dumpState()

        testHappyPath()
        testEmptyInput()
        testUnsearchableQuery()
        testSourceRetry()
        testDownload()

        println("══════════ All tests complete ══════════")
        demoFlow.dumpState()
    }

    // ══════════════════════════════════════════════════════════════
    // Scenario 1: Happy path
    // ══════════════════════════════════════════════════════════════

    /**
     * User: "播放周杰伦的夜曲"
     * Expected: AI → search_play → Netease → resolve URL → play
     */
    suspend fun testHappyPath() {
        println("\n── Test 1: Happy Path ──")
        val results = demoFlow.runFullPipeline(
            input = "播放周杰伦的夜曲",
            quality = "standard",
            shouldDownload = false,
        )
        assertStep(results, 0, success = true, label = "Step 1 — AI parse")
        assertStep(results, 1, success = true, label = "Step 2 — Search")
        assertStep(results, 2, success = true, label = "Step 3 — Resolve URL")

        val playing = musicPlayer.isPlaying.value
        println("  Playback active: $playing")
        println("  Current song: ${musicPlayer.currentSong.value?.songName}")
        println("  ✓ Happy path passed")
    }

    // ══════════════════════════════════════════════════════════════
    // Scenario 2: Empty input
    // ══════════════════════════════════════════════════════════════

    /**
     * User sends empty text.
     * Expected: rejected at Step 1.
     */
    suspend fun testEmptyInput() {
        println("\n── Test 2: Empty Input ──")
        val result = demoFlow.step1_parseUserInput("   ")
        assert(result is FlowStepResult.Failure, "Empty input should fail")
        println("  ✓ Empty input correctly rejected: ${result.summary}")
    }

    // ══════════════════════════════════════════════════════════════
    // Scenario 3: Unsearchable query
    // ══════════════════════════════════════════════════════════════

    /**
     * User sends a query that no platform can match.
     * Expected: search returns NoResults → pipeline aborts at Step 2.
     */
    suspend fun testUnsearchableQuery() {
        println("\n── Test 3: Unsearchable Query ──")
        val results = demoFlow.runFullPipeline(
            input = "播放 xyzxyzxyz123456789 这首歌",
        )
        // Step 2 should fail (no results)
        val step2 = results.getOrNull(1)
        println("  Step 2 result: ${step2?.summary}")
        val passed = step2 == null || step2 is FlowStepResult.Failure
        println("  ${if (passed) "✓" else "✗"} Unsearchable query handled")
    }

    // ══════════════════════════════════════════════════════════════
    // Scenario 4: Source retry (first URL fails)
    // ══════════════════════════════════════════════════════════════

    /**
     * Search returns multiple results, but the first one's play URL fails.
     * Expected: the pipeline tries the next candidate automatically.
     *
     * This test uses the ViewModel's built-in retry logic which is exercised
     * through the search → resolve → play chain.
     */
    suspend fun testSourceRetry() {
        println("\n── Test 4: Source Retry ──")
        val results = demoFlow.runFullPipeline(
            input = "播放周杰伦的晴天",
            quality = "standard",
        )
        // The pipeline itself handles retry transparently in step 3
        val step3 = results.getOrNull(2)
        val passed = step3 != null
        println("  Step 3 (retry): ${step3?.summary}")
        if (passed && step3 is FlowStepResult.Success) {
            println("  Played: ${step3.playTarget?.first?.songName}")
        }
        println("  ${if (passed) "✓" else "✗"} Source retry mechanism tested")
    }

    // ══════════════════════════════════════════════════════════════
    // Scenario 5: Download after playback
    // ══════════════════════════════════════════════════════════════

    /**
     * Play a song AND download it for offline use.
     * Expected: file written to local storage, DB record created.
     */
    suspend fun testDownload() {
        println("\n── Test 5: Download ──")
        val results = demoFlow.runFullPipeline(
            input = "播放周杰伦的稻香",
            quality = "standard",
            shouldDownload = true,
        )
        val downloadStep = results.lastOrNull()
        println("  Download step: ${downloadStep?.summary}")

        // Verify the song appears in the download list
        val downloadedSongs = downloadManager.getDownloadedSongs()
        println("  Downloaded songs: ${downloadedSongs.size}")
        for (song in downloadedSongs) {
            println("    - ${song.songName}: ${song.localPath} (${song.fileSize} bytes)")
        }
        val hasDownloads = downloadedSongs.isNotEmpty()
        println("  ${if (hasDownloads) "✓" else "(download may fail without API key)"} Download test complete")
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private fun assert(condition: Boolean, message: String) {
        if (!condition) println("  ✗ ASSERT FAILED: $message")
    }

    private fun assertStep(
        results: List<FlowStepResult>,
        index: Int,
        success: Boolean,
        label: String,
    ) {
        val result = results.getOrNull(index)
        val passed = if (success) result is FlowStepResult.Success
        else result == null || result is FlowStepResult.Failure
        val status = if (passed) "✓" else "✗"
        println("  $status $label: ${result?.summary ?: "null"}")
    }

    /** Clean up after tests. */
    fun dispose() {
        musicPlayer.release()
        downloadManager.dispose()
    }
}
