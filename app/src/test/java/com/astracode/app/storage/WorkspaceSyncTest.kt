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
