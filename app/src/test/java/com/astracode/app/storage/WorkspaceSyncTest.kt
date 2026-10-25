package com.astracode.app.storage

import com.astracode.filesystem.FileManagementSystem
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceSyncTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun syncMirrorsNestedFilesPreservesBinaryBytesAndRemovesStaleItems() {
        val sourceRoot = temporaryFolder.newFolder("source").toPath()
        val targetRoot = temporaryFolder.newFolder("target").toPath()
        FileManagementSystem(sourceRoot).use { source ->
            FileManagementSystem(targetRoot).use { target ->
                source.writeText("project/README.txt", "updated project")
                source.writeText("project/src/Main.kt", "fun main() = Unit")
                val binary = byteArrayOf(0, 1, 0x7f, 0x80.toByte(), 0xff.toByte(), 0)
                source.writeBytes("project/assets/resource.bin", binary)
                target.writeText("mirror/stale.txt", "this should be removed")

                syncWorkspaceEntry(source, "project", target, "mirror", overwriteExisting = true)

                assertEquals("updated project", target.readText("mirror/README.txt").text)
                assertEquals("fun main() = Unit", target.readText("mirror/src/Main.kt").text)
                assertArrayEquals(binary, target.readBytes("mirror/assets/resource.bin"))
                assertFalse(target.exists("mirror/stale.txt"))
                assertTrue(target.exists("mirror/src/Main.kt"))
            }
        }
    }

    @Test
    fun persistedSyncLinksRejectReservedAndUnsafePaths() {
        val normal = WorkspaceSyncLink("project", "content://workspace-tree", "project")
        assertEquals(normal, normalizeWorkspaceSyncLink(normal))
        assertEquals(
            null,
            normalizeWorkspaceSyncLink(
                WorkspaceSyncLink(".ASTRACODE-TRASH", "content://workspace-tree", "project"),
            ),
        )
        assertEquals(
            null,
            normalizeWorkspaceSyncLink(
                WorkspaceSyncLink("project", "content://workspace-tree", "folder/../outside"),
            ),
        )
        assertEquals(
            null,
            normalizeWorkspaceSyncLink(
                WorkspaceSyncLink("project", "", "project"),
            ),
        )
    }

    @Test
    fun syncDoesNotCopyOrDeleteReservedRecoveryFilesInsideFolders() {
        val sourceRoot = temporaryFolder.newFolder("source-reserved").toPath()
        val targetRoot = temporaryFolder.newFolder("target-reserved").toPath()
        FileManagementSystem(sourceRoot).use { source ->
            FileManagementSystem(targetRoot).use { target ->
                source.createDirectory("project")
                source.writeText("project/README.txt", "normal workspace content")
                source.writeText("project/.ASTRACODE-backup-orphan", "private recovery payload")
                target.createDirectory("mirror")
                target.writeText("mirror/.ASTRACODE-backup-target", "destination recovery payload")
                target.writeText("mirror/stale.txt", "stale workspace item")

                syncWorkspaceEntry(source, "project", target, "mirror", overwriteExisting = true)

                assertEquals("normal workspace content", target.readText("mirror/README.txt").text)
                assertFalse(target.exists("mirror/.ASTRACODE-backup-orphan"))
                assertTrue(target.exists("project/.ASTRACODE-backup-orphan"))
                assertTrue(target.exists("mirror/.ASTRACODE-backup-target"))
                assertEquals("destination recovery payload", target.readText("mirror/.ASTRACODE-backup-target").text)
                assertFalse(target.exists("mirror/stale.txt"))
            }
        }
    }

    @Test
    fun syncRejectsCaseVariantReservedSourceAndDestinationPaths() {
        val sourceRoot = temporaryFolder.newFolder("source-reserved-root").toPath()
        val targetRoot = temporaryFolder.newFolder("target-reserved-root").toPath()
        FileManagementSystem(sourceRoot).use { source ->
            FileManagementSystem(targetRoot).use { target ->
                source.createDirectory(".ASTRACODE-TRASH")
                source.writeText(".ASTRACODE-TRASH/recovery.txt", "must remain private")
                source.writeText("visible.txt", "normal data")

                try {
                    syncWorkspaceEntry(source, ".ASTRACODE-TRASH", target, "copied-trash", overwriteExisting = true)
                    throw AssertionError("Reserved source paths must not be synced.")
                } catch (_: SecurityException) {
                    // Expected.
                }
                try {
                    syncWorkspaceEntry(source, "visible.txt", target, ".ASTRACODE-OPERATIONS", overwriteExisting = true)
                    throw AssertionError("Reserved destination paths must not be synced.")
                } catch (_: SecurityException) {
                    // Expected.
                }

                assertFalse(target.exists("copied-trash"))
                assertFalse(target.exists(".ASTRACODE-OPERATIONS"))
                assertEquals("must remain private", source.readText(".ASTRACODE-TRASH/recovery.txt").text)
            }
        }
    }

    @Test
    fun syncRefusesToOverwriteWhenAsked() {
        val sourceRoot = temporaryFolder.newFolder("source").toPath()
        val targetRoot = temporaryFolder.newFolder("target").toPath()
        FileManagementSystem(sourceRoot).use { source ->
            FileManagementSystem(targetRoot).use { target ->
                source.writeText("source.txt", "source")
                target.writeText("target.txt", "keep target")

                try {
                    syncWorkspaceEntry(source, "source.txt", target, "target.txt", overwriteExisting = false)
                    throw AssertionError("Expected destination conflict")
                } catch (expected: IOException) {
                    assertEquals("The destination already exists: target.txt", expected.message)
                }

                assertEquals("keep target", target.readText("target.txt").text)
            }
        }
    }
}
