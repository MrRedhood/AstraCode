package com.astracode.editor

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit

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
    fun createsFileUrisOnlyForWorkspaceFilesAndSupportedLanguages() {
        val root = Files.createTempDirectory("astracode-lsp-document").toFile()
        try {
            val configuration = LanguageServerConfiguration(
                id = "kotlin",
                languageIds = setOf("kotlin"),
                fileExtensions = setOf("kt"),
                command = listOf("kotlin-language-server"),
            )
            val manager = LspWorkspaceManager(root, listOf(configuration))
            val source = java.io.File(root, "src/Main.kt")
            val document = manager.createDocument(source, "kotlin", 3, "fun main() = Unit")
            assertEquals(source.canonicalFile.toURI().toString(), document.uri)
            assertEquals(3, document.version)
            assertFailsWith<IllegalArgumentException> {
                manager.createDocument(source, "java", 1, "class Main")
            }
            assertFailsWith<IllegalArgumentException> {
                manager.createDocument(java.io.File(root.parentFile, "Outside.kt"), "kotlin", 1, "")
            }
            manager.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun launchesUtf8ServerOpensDocumentAndShutsDownCleanly() {
        val root = Files.createTempDirectory("astracode-lsp-process").toFile()
        try {
            val script = """
                import json, sys
                while True:
                    headers = {}
                    while True:
                        line = sys.stdin.buffer.readline()
                        if not line:
                            sys.exit(0)
                        if line in (b'\r\n', b'\n'):
                            break
                        name, value = line.decode('ascii').split(':', 1)
                        headers[name.strip().lower()] = value.strip()
                    length = int(headers['content-length'])
                    message = json.loads(sys.stdin.buffer.read(length).decode('utf-8'))
                    method = message.get('method')
                    response = None
                    if method == 'initialize':
                        response = {'jsonrpc': '2.0', 'id': message['id'], 'result': {'capabilities': {'positionEncoding': 'utf-8'}}}
                    elif method == 'shutdown':
                        response = {'jsonrpc': '2.0', 'id': message['id'], 'result': None}
                    elif method == 'exit':
                        break
                    if response is not None:
                        payload = json.dumps(response, separators=(',', ':')).encode('utf-8')
                        sys.stdout.buffer.write(('Content-Length: %d\r\n\r\n' % len(payload)).encode('ascii') + payload)
                        sys.stdout.buffer.flush()
            """.trimIndent()
            val configuration = LanguageServerConfiguration(
                id = "fake-kotlin",
                languageIds = setOf("kotlin"),
                fileExtensions = setOf("kt"),
                command = listOf("python3", "-c", script),
            )
            val manager = LspWorkspaceManager(root, listOf(configuration))
            val document = manager.createDocument(
                java.io.File(root, "Main.kt"),
                "kotlin",
                1,
                "fun main() = Unit",
            )

            val bridge = manager.connectForDocument(document).get(5, TimeUnit.SECONDS)
            bridge.openDocument(document)
            manager.closeServer("fake-kotlin").get(5, TimeUnit.SECONDS)
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
