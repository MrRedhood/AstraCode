package com.astracode.app.storage

import com.astracode.filesystem.FileManagementSystem
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceSafetyTest {
    @Test fun trashRestoreUsesCollisionSafePaths() {
        val root = Files.createTempDirectory("safety-trash").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.writeText("notes.txt", "original")
                val record = WorkspaceSafety.moveToTrash(storage, "notes.txt", nowMillis = 1000)
                assertFalse(storage.exists("notes.txt"))
                storage.writeText("notes.txt", "new")
                try { WorkspaceSafety.restoreFromTrash(storage, record.id); fail("Must not overwrite.") }
                catch (expected: java.io.IOException) { assertTrue(expected.message.orEmpty().contains("already exists")) }
                WorkspaceSafety.restoreFromTrash(storage, record.id, "notes-restored.txt")
                assertEquals("original", storage.readText("notes-restored.txt").text)
                assertEquals("new", storage.readText("notes.txt").text)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun trashRetentionDeletesOnlyExpiredValidEntries() {
        val root = Files.createTempDirectory("safety-trash-retention").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.writeText("old.txt", "old")
                val record = WorkspaceSafety.moveToTrash(storage, "old.txt", nowMillis = 1000)
                storage.writeText("keep.txt", "keep")
                assertEquals(1, WorkspaceSafety.cleanupExpiredTrash(storage, nowMillis = 10000, retentionMillis = 5000))
                assertTrue(WorkspaceSafety.listTrash(storage).none { it.id == record.id })
                assertEquals("keep", storage.readText("keep.txt").text)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun previewSearchDuplicatesAndHealthReportWork() {
        val root = Files.createTempDirectory("safety-preview").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.writeBytes("a.txt", "same".toByteArray())
                storage.writeBytes("b.txt", "same".toByteArray())
                storage.writeBytes("c.txt", "diff".toByteArray())
                assertEquals("same", WorkspaceSafety.preview(storage, "a.txt").text)
                assertTrue(WorkspaceSafety.findDuplicates(storage).groups.any { it.paths.containsAll(listOf("a.txt", "b.txt")) })
                assertTrue(WorkspaceSafety.search(storage, WorkspaceSearchOptions(query = "a.txt")).any { it.path == "a.txt" })
                val health = WorkspaceSafety.healthReport(storage)
                assertEquals(3, health.fileCount)
                assertEquals(12L, health.totalBytes)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun batchRenameHandlesCyclesAndRejectsDuplicateTargets() {
        val root = Files.createTempDirectory("safety-batch-rename").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.writeText("a.txt", "A")
                storage.writeText("b.txt", "B")
                try {
                    WorkspaceSafety.batchRename(storage, "", mapOf("a.txt" to "same", "b.txt" to "same"))
                    fail("Duplicate targets must fail.")
                } catch (_: java.io.IOException) {
                    assertEquals("A", storage.readText("a.txt").text)
                    assertEquals("B", storage.readText("b.txt").text)
                }
                assertEquals(2, WorkspaceSafety.batchRename(storage, "", mapOf("a.txt" to "b.txt", "b.txt" to "a.txt")).size)
                assertEquals("A", storage.readText("b.txt").text)
                assertEquals("B", storage.readText("a.txt").text)
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun verifiedWritePreservesContentsAndBlocksTraversal() {
        val root = Files.createTempDirectory("safety-write").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                WorkspaceSafety.writeVerified(storage, "config.json", "{\"v\":1}".toByteArray())
                WorkspaceSafety.writeVerified(storage, "config.json", "{\"v\":2}".toByteArray())
                assertArrayEquals("{\"v\":2}".toByteArray(), storage.readBytes("config.json"))
                try { WorkspaceSafety.writeVerified(storage, "../outside.txt", byteArrayOf(1)); fail("Traversal must fail.") }
                catch (_: SecurityException) { assertFalse(Files.exists(root.parentFile.toPath().resolve("outside.txt"))) }
                try { WorkspaceSafety.validatePath(".astracode-trash/items"); fail("Recovery paths must be reserved.") }
                catch (_: SecurityException) { }
            }
        } finally { root.deleteRecursively() }
    }
}
