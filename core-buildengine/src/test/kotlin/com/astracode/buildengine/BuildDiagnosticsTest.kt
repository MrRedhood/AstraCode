package com.astracode.buildengine

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildDiagnosticsTest {
    @Test
    fun mapsKotlinAndJavaCompilerErrorsToWorkspaceRelativePaths() {
        val root = java.nio.file.Files.createTempDirectory("astracode-diagnostics-").toFile()
        try {
            val sourceDirectory = File(root, "src/main")
            assertTrue(sourceDirectory.mkdirs())
            val kotlinFile = File(sourceDirectory, "Main.kt")
            val javaFile = File(sourceDirectory, "Helper.java")
            kotlinFile.writeText("fun main() = Unit")
            javaFile.writeText("class Helper {}")

            val output = buildString {
                append("e: ")
                append(kotlinFile.toURI())
                appendLine(": (7, 4): Unresolved reference")
                append(javaFile.absolutePath)
                appendLine(":12: error: cannot find symbol")
                append("/outside/NotInWorkspace.java:4: error: hidden")
            }

            val diagnostics = BuildDiagnosticParser().parse(output, root)
            assertEquals(2, diagnostics.size)
            assertEquals("src/main/Main.kt", diagnostics[0].path)
            assertEquals(7, diagnostics[0].line)
            assertEquals(4, diagnostics[0].column)
            assertEquals(BuildDiagnosticSeverity.ERROR, diagnostics[0].severity)
            assertEquals("src/main/Helper.java", diagnostics[1].path)
            assertEquals(12, diagnostics[1].line)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresMalformedAndExternalCompilerLines() {
        val root = java.nio.file.Files.createTempDirectory("astracode-diagnostics-").toFile()
        try {
            val parsed = BuildDiagnosticParser().parse(
                "e: /outside/Other.kt: (2, 1): bad\n" +
                    "not a compiler diagnostic\n" +
                    "src/File.kt:zero: error: malformed",
                root,
            )
            assertTrue(parsed.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun validatesApkArchiveStructureAndComputesSha256() {
        val root = java.nio.file.Files.createTempDirectory("astracode-artifacts-").toFile()
        try {
            val apk = File(root, "app-debug.apk")
            writeZip(apk, mapOf("AndroidManifest.xml" to "manifest".toByteArray()))
            val result = BuildArtifactScanner().scan(root).single()
            assertEquals(BuildArtifactKind.APK, result.kind)
            assertTrue(result.valid)
            assertEquals(64, result.sha256.length)
            assertEquals(apk, result.file)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsAnApkWithoutAndroidManifest() {
        val root = java.nio.file.Files.createTempDirectory("astracode-artifacts-").toFile()
        try {
            val apk = File(root, "broken.apk")
            writeZip(apk, mapOf("classes.dex" to byteArrayOf(1, 2, 3)))
            val result = BuildArtifactScanner().scan(root).single()
            assertFalse(result.valid)
            assertTrue(result.validationMessage.orEmpty().contains("AndroidManifest.xml"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
