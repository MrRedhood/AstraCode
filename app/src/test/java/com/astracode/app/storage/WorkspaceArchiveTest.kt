package com.astracode.app.storage

import com.astracode.filesystem.FileManagementSystem
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceArchiveTest {
    @Test
    fun zipRoundTripPreservesBytesAndUsesCurrentFolderRelativePaths() {
        val root = Files.createTempDirectory("astracode-workspace-archive").toFile()
        val storage = FileManagementSystem(root.toPath())
        try {
            storage.createDirectory("project")
            storage.createDirectory("project/src")
            val binaryBytes = byteArrayOf(0, 1, -1, -128, 12, 42)
            storage.writeBytes("project/plan.docx", binaryBytes)
            storage.writeText("project/src/Main.kt", "fun main() = Unit\n")

            WorkspaceArchive.createZip(
                storage,
                listOf("project/plan.docx", "project/src"),
                "project/release.zip",
                "project",
            )
            assertTrue(storage.exists("project/release.zip"))

            WorkspaceArchive.extractZip(storage, "project/release.zip", "project/restored")
            assertArrayEquals(binaryBytes, storage.readBytes("project/restored/plan.docx"))
            assertEquals("fun main() = Unit\n", storage.readText("project/restored/src/Main.kt").text)
            assertFalse(storage.exists("project/restored/project"))
        } finally {
            storage.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun moveToZipRemovesSelectedSourcesAfterWritingAndPreservesArchiveBytes() {
        val root = Files.createTempDirectory("astracode-workspace-move-to-zip").toFile()
        val storage = FileManagementSystem(root.toPath())
        try {
            storage.createDirectory("project/src")
            val binaryBytes = byteArrayOf(0, 1, -1, -128, 12, 42)
            storage.writeBytes("project/plan.docx", binaryBytes)
            storage.writeText("project/src/Main.kt", "fun main() = Unit\\n")

            WorkspaceArchive.moveToZip(
                storage,
                listOf("project/plan.docx", "project/src"),
                "project/release.zip",
                "project",
            )

            assertTrue(storage.exists("project/release.zip"))
            assertFalse(storage.exists("project/plan.docx"))
            assertFalse(storage.exists("project/src"))
            WorkspaceArchive.extractZip(storage, "project/release.zip", "project/restored")
            assertArrayEquals(binaryBytes, storage.readBytes("project/restored/plan.docx"))
            assertEquals("fun main() = Unit\\n", storage.readText("project/restored/src/Main.kt").text)
        } finally {
            storage.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsZipSlipAndRollsBackTheExtractionFolder() {
        val root = Files.createTempDirectory("astracode-workspace-zip-slip").toFile()
        val storage = FileManagementSystem(root.toPath())
        try {
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../outside.txt"))
                zip.write("unsafe".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            storage.writeBytes("malicious.zip", output.toByteArray())

            try {
                WorkspaceArchive.extractZip(storage, "malicious.zip", "restored")
                fail("Unsafe archive path must be rejected.")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("unsafe", ignoreCase = true))
            }
            assertFalse(storage.exists("restored"))
            assertFalse(Files.exists(root.parentFile.toPath().resolve("outside.txt")))
        } finally {
            storage.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesToOverwriteAnExistingExtractionDestination() {
        val root = Files.createTempDirectory("astracode-workspace-no-overwrite").toFile()
        val storage = FileManagementSystem(root.toPath())
        try {
            storage.writeText("source.txt", "payload")
            WorkspaceArchive.createZip(storage, listOf("source.txt"), "source.zip")
            storage.createDirectory("restored")
            storage.writeText("restored/keep.txt", "keep")

            try {
                WorkspaceArchive.extractZip(storage, "source.zip", "restored")
                fail("Existing destination must not be overwritten.")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message.orEmpty().contains("already exists", ignoreCase = true))
            }
            assertEquals("keep", storage.readText("restored/keep.txt").text)
        } finally {
            storage.close()
            root.deleteRecursively()
        }
    }
}
