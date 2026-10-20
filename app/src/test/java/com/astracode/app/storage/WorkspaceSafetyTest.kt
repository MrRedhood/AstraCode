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

    @Test fun trashCanHandleMultipleItemsAndJournalEachOperation() {
        val root = Files.createTempDirectory("safety-multiple-trash").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.writeText("one.txt", "one")
                storage.writeText("two.txt", "two")
                WorkspaceSafety.moveToTrash(storage, "one.txt", nowMillis = 1000)
                WorkspaceSafety.moveToTrash(storage, "two.txt", nowMillis = 2000)
                assertEquals(2, WorkspaceSafety.listTrash(storage).size)
                assertEquals(2, storage.listDirectory(WorkspaceSafety.OPERATIONS_DIRECTORY + "/journal").size)
                assertFalse(storage.exists("one.txt"))
                assertFalse(storage.exists("two.txt"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun verifiedCopyLocksPathsAndPreservesBytesForNewAndExistingTargets() {
        val root = Files.createTempDirectory("safety-copy-verified").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                val original = byteArrayOf(0, 1, -1, -128, 42)
                storage.writeBytes("source.bin", original)
                WorkspaceSafety.copyVerified(storage, "source.bin", "copy.bin")
                assertArrayEquals(original, storage.readBytes("copy.bin"))
                storage.writeBytes("copy.bin", byteArrayOf(2, 3, 4))
                WorkspaceSafety.copyVerified(storage, "source.bin", "copy.bin", replaceExisting = true)
                assertArrayEquals(original, storage.readBytes("copy.bin"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun systemRecoveryPathsAreHiddenFromSearchAndCannotBeUserTargets() {
        val root = Files.createTempDirectory("safety-reserved-paths").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.writeText(".astracode-tmp-abandoned", "partial")
                storage.writeText("nested/.astracode-backup-orphan", "backup")
                storage.writeText(".gitignore", "ordinary hidden file")
                assertTrue(WorkspaceSafety.isReservedPath("nested/.astracode-backup-orphan"))
                assertTrue(WorkspaceSafety.search(storage, WorkspaceSearchOptions(includeHidden = true)).none {
                    it.path.startsWith(".astracode-") || it.path.contains("/.astracode-")
                })
                assertTrue(WorkspaceSafety.healthReport(storage).largestFiles.none { it.first.startsWith(".astracode-") })
                assertTrue(WorkspaceSafety.search(storage, WorkspaceSearchOptions(includeHidden = true)).any { it.path == ".gitignore" })
                try { WorkspaceSafety.validatePath("nested/.astracode-backup-orphan"); fail("Reserved paths must be rejected.") }
                catch (_: SecurityException) { }
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

    @Test fun healthReportCountsDirectoriesWithoutLeakingRecoveryArtifacts() {
        val root = Files.createTempDirectory("safety-health-directories").toFile()
        try {
            FileManagementSystem(root.toPath()).use { storage ->
                storage.createDirectory("src")
                storage.createDirectory("src/main")
                storage.writeBytes("src/main/App.kt", "fun main()".toByteArray())
                storage.writeBytes(".astracode-backup-orphan", "backup".toByteArray())
                val report = WorkspaceSafety.healthReport(storage)
                assertEquals(1, report.fileCount)
                assertEquals(2, report.directoryCount)
                assertEquals("src/main/App.kt", report.largestFiles.single().first)
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
