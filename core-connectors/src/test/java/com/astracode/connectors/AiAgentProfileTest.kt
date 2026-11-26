package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiAgentProfileTest {
    @Test
    fun profileSupportsProviderModelToolAndResourceOverrides() {
        val profile = AiAgentProfile(
            id = "reviewer",
            name = "Review agent",
            role = "Inspect source changes.",
            providerOverride = AiProvider.ANTHROPIC,
            modelOverride = "claude-model",
            allowedToolNames = setOf("read_workspace_file", "search_workspace_files"),
            maxAgentTasks = 2,
            maxToolRounds = 5,
            maxParallelAgents = 2,
            maxContextBytes = 256L * 1024L,
            maxDependencyFileBytes = 48 * 1024,
            maxDependencyContextBytes = 256L * 1024L,
        )

        assertEquals(AiProvider.ANTHROPIC, profile.providerOverride)
        assertEquals("claude-model", profile.modelOverride)
        assertEquals(
            setOf("read_workspace_file", "search_workspace_files"),
            profile.allowedToolNames,
        )
        assertEquals(2, profile.maxAgentTasks)
        assertEquals(2, profile.maxParallelAgents)
        assertEquals(5, profile.maxToolRounds)
        assertEquals(256L * 1024L, profile.maxContextBytes)
    }

    @Test
    fun profileRejectsBlankModelAndEndpointOverrides() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(modelOverride = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(customBaseUrlOverride = "")
        }
    }

    @Test
    fun profileDefaultsToBoundedReadOnlyWorkspaceTools() {
        val profile = AiAgentProfile()
        assertEquals(AiToolRegistry.DEFAULT_TOOL_NAMES, profile.allowedToolNames)
        assertEquals(4, profile.maxAgentTasks)
        assertEquals(3, profile.maxParallelAgents)
        assertEquals(4, profile.maxToolRounds)
    }

    @Test
    fun profileRejectsShellAndOtherUndeclaredTools() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(allowedToolNames = setOf("run_shell"))
        }
    }

    @Test
    fun profileRejectsUnsafeResourceLimits() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(maxParallelAgents = 4)
        }
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(maxAgentTasks = 5)
        }
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(maxContextBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            AiAgentProfile(maxDependencyFileBytes = 2 * 1024 * 1024)
        }
    }
}
