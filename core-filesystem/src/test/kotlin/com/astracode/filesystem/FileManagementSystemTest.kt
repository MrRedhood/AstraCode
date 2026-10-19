package com.astracode.filesystem

import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir

class FileManagementSystemTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `writes reads lists moves and deletes workspace files`() {
        FileManagementSystem(temporaryDirectory.resolve("workspace")).use { fileSystem ->
            val events = mutableListOf<FileChangeEvent>()
            fileSystem.addChangeListener(events::add).use {
                val saved = fileSystem.writeText("src/Main.kt", "fun main() = println(\"hi\")")
                assertEquals("fun main() = println(\"hi\")", saved.persistedText)
                assertEquals(saved.persistedText, fileSystem.readText("src/Main.kt").text)
                assertEquals(FileEntryType.DIRECTORY, fileSystem.listDirectory().single().type)
                assertEquals("Main.kt", fileSystem.listDirectory("src").single().name)

                fileSystem.move("src/Main.kt", "src/App.kt")
                assertFalse(fileSystem.exists("src/Main.kt"))
                assertTrue(fileSystem.exists("src/App.kt"))
                assertEquals(FileChangeType.MOVED, events.last().type)
                assertEquals("src/Main.kt", events.last().previousPath)

                fileSystem.delete("src/App.kt")
                fileSystem.delete("src")
                assertTrue(fileSystem.listDirectory().isEmpty())
            }
        }
    }

    @Test
    fun `directory listings include dotfiles and count direct files and folders`() {
        val workspace = temporaryDirectory.resolve("workspace")
        Files.createDirectories(workspace.resolve("app/src"))
        Files.createDirectories(workspace.resolve("empty"))
        Files.writeString(workspace.resolve(".gitignore"), "build/")
        Files.writeString(workspace.resolve("README.txt"), "readme")
        Files.writeString(workspace.resolve("app/build.gradle.kts"), "plugins {}")
        Files.writeString(workspace.resolve("app/src/Main.kt"), "fun main() = Unit")
        Files.writeString(workspace.resolve("app/src/.env"), "TOKEN=original")

        FileManagementSystem(workspace).use { fileSystem ->
            assertEquals(
                setOf(".gitignore", "README.txt", "app", "empty"),
                fileSystem.listDirectory().map { it.name }.toSet(),
            )
            assertEquals(4, fileSystem.listDirectory().size)
            assertEquals(2, fileSystem.listDirectory("app").size)
            assertEquals(2, fileSystem.listDirectory("app/src").size)
            assertEquals(0, fileSystem.listDirectory("empty").size)
            assertEquals("TOKEN=original", fileSystem.readText("app/src/.env").text)

            fileSystem.writeText("app/src/.env", "TOKEN=updated")
            assertEquals("TOKEN=updated", fileSystem.readText("app/src/.env").text)
        }
    }

    @Test
    fun `malformed UTF8 is repaired then saved as canonical UTF8`() {
        val workspace = temporaryDirectory.resolve("workspace")
        Files.createDirectories(workspace)
        val malformed = byteArrayOf('a'.code.toByte(), 0xE2.toByte(), 0x82.toByte(), 'b'.code.toByte())
        Files.write(workspace.resolve("broken.txt"), malformed)

        FileManagementSystem(workspace, FileSystemOptions(defaultReadBufferSize = 1)).use { fileSystem ->
            val loaded = fileSystem.readText("broken.txt")
            assertEquals("a\uFFFD" + "b", loaded.text)
            assertTrue(loaded.wasRepaired)
            assertEquals(1, loaded.repairedSpans.single().documentUtf16Offset)
            assertEquals(1L, loaded.repairedSpans.single().sourceByteOffset)

            val saved = fileSystem.writeText("broken.txt", loaded.text)
            assertEquals(0, saved.replacedUnpairedSurrogateCount)
            assertContentEquals(
                byteArrayOf('a'.code.toByte(), 0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte(), 'b'.code.toByte()),
                Files.readAllBytes(workspace.resolve("broken.txt")),
            )
            assertFalse(fileSystem.readText("broken.txt").wasRepaired)
        }
    }

    @Test
    fun `BOM is omitted from the in-memory text and canonical save`() {
        val workspace = temporaryDirectory.resolve("workspace")
        Files.createDirectories(workspace)
        Files.write(workspace.resolve("bom.txt"), byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "ok".toByteArray())

        FileManagementSystem(workspace).use { fileSystem ->
            val snapshot = fileSystem.readText("bom.txt")
            assertTrue(snapshot.hadLeadingBom)
            assertEquals("ok", snapshot.text)
            fileSystem.writeText("bom.txt", snapshot.text)
            assertContentEquals("ok".toByteArray(), Files.readAllBytes(workspace.resolve("bom.txt")))
        }
    }

    @Test
    fun `rejects absolute and parent traversal paths`() {
        FileManagementSystem(temporaryDirectory.resolve("workspace")).use { fileSystem ->
            assertFailsWith<SecurityException> { fileSystem.readText("../outside.txt") }
            assertFailsWith<SecurityException> { fileSystem.writeText("../../outside.txt", "bad") }
            assertFailsWith<SecurityException> { fileSystem.readText(temporaryDirectory.resolve("outside.txt").toString()) }
            assertFailsWith<SecurityException> { fileSystem.readText("..\\\\outside.txt") }
        }
    }

    @Test
    fun `refuses symlinks rather than following them outside the workspace`() {
        val workspace = temporaryDirectory.resolve("workspace")
        Files.createDirectories(workspace)
        val outside = temporaryDirectory.resolve("outside.txt")
        Files.writeString(outside, "secret")
        try {
            Files.createSymbolicLink(workspace.resolve("escape.txt"), outside)
        } catch (_: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are unavailable")
        } catch (_: IOException) {
            assumeTrue(false, "Symbolic link permission is unavailable")
        }

        FileManagementSystem(workspace).use { fileSystem ->
            assertFailsWith<SecurityException> { fileSystem.readText("escape.txt") }
            assertEquals(FileEntryType.SYMBOLIC_LINK, fileSystem.listDirectory().single().type)
        }
    }

    @Test
    fun `enforces file size limits before returning content`() {
        val workspace = temporaryDirectory.resolve("workspace")
        Files.createDirectories(workspace)
        Files.writeString(workspace.resolve("large.txt"), "12345")
        FileManagementSystem(workspace, FileSystemOptions(maxFileBytes = 4)).use { fileSystem ->
            assertFailsWith<FileTooLargeException> { fileSystem.readText("large.txt") }
            assertFailsWith<FileTooLargeException> { fileSystem.writeText("new.txt", "12345") }
        }
    }

    @Test
    fun `non-recursive deletion cannot remove a non-empty directory`() {
        FileManagementSystem(temporaryDirectory.resolve("workspace")).use { fileSystem ->
            fileSystem.writeText("folder/file.txt", "data")
            assertFailsWith<DirectoryNotEmptyException> { fileSystem.delete("folder") }
            assertTrue(fileSystem.exists("folder/file.txt"))
            fileSystem.delete("folder", recursive = true)
            assertFalse(fileSystem.exists("folder"))
        }
    }

    @Test
    fun `unpaired UTF16 surrogates are persisted as replacement characters`() {
        FileManagementSystem(temporaryDirectory.resolve("workspace")).use { fileSystem ->
            val result = fileSystem.writeText("bad-surrogate.txt", "a\uD800b\uDC00c")
            assertEquals("a\uFFFD" + "b\uFFFD" + "c", result.persistedText)
            assertEquals(2, result.replacedUnpairedSurrogateCount)
            assertEquals(result.persistedText, fileSystem.readText("bad-surrogate.txt").text)
        }
    }
}
