package com.astracode.editor

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspWorkspaceManagerTest {
    @Test
    fun resolvesConfiguredServerByCaseInsensitiveFileExtension() {
        val configuration = LanguageServerConfiguration(
            id = "kotlin",
            languageIds = setOf("kotlin"),
            fileExtensions = setOf("kt", "kts"),
            command = listOf("kotlin-language-server"),
        )
        val root = Files.createTempDirectory("astracode-lsp-workspace").toFile()
        try {
            val manager = LspWorkspaceManager(root, listOf(configuration))
            assertEquals("kotlin", manager.configurationForFile("Main.kt")?.id)
            assertEquals("kotlin", manager.configurationForFile("build.gradle.KTS")?.id)
            assertNull(manager.configurationForFile("README"))
            assertNull(manager.configurationForFile(".gitignore"))
            manager.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsAmbiguousExtensionsAndUnsafeWorkingDirectories() {
        val root = Files.createTempDirectory("astracode-lsp-config").toFile()
        try {
            val first = LanguageServerConfiguration(
                id = "first",
                languageIds = setOf("kotlin"),
                fileExtensions = setOf("kt"),
                command = listOf("server-one"),
            )
            val second = LanguageServerConfiguration(
                id = "second",
                languageIds = setOf("kotlin"),
                fileExtensions = setOf("kt"),
                command = listOf("server-two"),
            )
            assertFailsWith<IllegalArgumentException> {
                LspWorkspaceManager(root, listOf(first, second))
            }
            assertFailsWith<IllegalArgumentException> {
                LanguageServerConfiguration(
                    id = "unsafe",
                    languageIds = setOf("kotlin"),
                    fileExtensions = setOf("kt"),
                    command = listOf("server"),
                    workingDirectoryRelativePath = "../outside",
                )
            }
            assertTrue(root.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
