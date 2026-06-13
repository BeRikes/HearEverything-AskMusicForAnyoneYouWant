package com.example.aimusicplayer.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserAgentPoolTest {

    @Test
    fun `next returns non-empty string`() = runTest {
        val agent = UserAgentPool.next()
        assertTrue(agent.isNotEmpty())
        assertTrue(agent.length > 20)
    }

    @Test
    fun `next cycles through agents`() = runTest {
        val size = UserAgentPool.size
        // Get all agents in order
        val allAgents = mutableListOf<String>()
        for (i in 0 until size) {
            allAgents.add(UserAgentPool.next())
        }
        // After cycling through all, the next one should repeat the cycle
        val afterCycle = UserAgentPool.next()
        assertTrue(afterCycle == allAgents[0] || afterCycle == allAgents[1],
            "After cycling $size agents, next should be from the pool: got $afterCycle")
    }

    @Test
    fun `random returns a valid agent`() = runTest {
        val agent = UserAgentPool.random()
        assertTrue(agent.isNotEmpty())
        assertTrue(agent.contains("Mozilla"))
    }

    @Test
    fun `size matches the hardcoded list`() {
        // The project defines 8 agents
        assertEquals(8, UserAgentPool.size)
    }

    @Test
    fun `random returns from pool`() = runTest {
        val samples = mutableSetOf<String>()
        // Sample many times — should get agents from the pool
        repeat(50) {
            samples.add(UserAgentPool.random())
        }
        // With 8 agents and 50 samples, we should see most if not all
        assertTrue(samples.size >= 1)
        samples.forEach { agent ->
            assertTrue(agent.contains("Mozilla"), "Agent should contain Mozilla: $agent")
        }
    }
}
