package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiAgentTaskGraphTest {
    @Test
    fun normalizesTaskIdsAndPreservesDependencyOrder() {
        val result = AiAgentTaskGraph.normalize(
            listOf(
                AiAgentTask(" first ", " First ", " Inspect"),
                AiAgentTask("second", "Second", "Implement", listOf("first")),
                AiAgentTask("third", "Third", "Verify", listOf("second")),
            ),
        )
        assertEquals(listOf("first", "second", "third"), result.map { it.id })
        assertEquals(listOf("first"), result[1].dependsOn)
    }

    @Test
    fun rejectsUnknownDependencies() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                listOf(AiAgentTask("worker", "Worker", "Do work", listOf("missing"))),
            )
        }
    }

    @Test
    fun rejectsSelfDependencies() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                listOf(AiAgentTask("worker", "Worker", "Do work", listOf("worker"))),
            )
        }
    }

    @Test
    fun rejectsDependencyCycles() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                listOf(
                    AiAgentTask("a", "A", "A", listOf("b")),
                    AiAgentTask("b", "B", "B", listOf("c")),
                    AiAgentTask("c", "C", "C", listOf("a")),
                ),
            )
        }
    }

    @Test
    fun rejectsMoreThanFourTasks() {
        assertFailsWith<IllegalArgumentException> {
            AiAgentTaskGraph.normalize(
                (1..5).map { AiAgentTask("task-$it", "Task $it", "Do work") },
            )
        }
    }
}
