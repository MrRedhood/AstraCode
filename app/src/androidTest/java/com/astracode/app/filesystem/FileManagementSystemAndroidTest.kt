package com.astracode.app.filesystem

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astracode.filesystem.FileManagementSystem
import java.nio.file.Files
import java.util.Comparator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileManagementSystemAndroidTest {
    @Test
    fun workspaceCanReadAndAtomicallySaveRepairedUtf8OnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = Files.createTempDirectory(context.cacheDir.toPath(), "astracode-filesystem-")
        try {
            val malformedBytes = byteArrayOf(
                'a'.code.toByte(),
                0xE2.toByte(),
                0x82.toByte(),
                'b'.code.toByte(),
            )
            Files.write(workspace.resolve("sample.txt"), malformedBytes)

            FileManagementSystem(workspace).use { fileSystem ->
                val snapshot = fileSystem.readText("sample.txt", readBufferSize = 1)
                assertEquals("a\uFFFD" + "b", snapshot.text)
                assertTrue(snapshot.wasRepaired)

                fileSystem.writeText("sample.txt", snapshot.text)
                assertArrayEquals(
                    byteArrayOf('a'.code.toByte(), 0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte(), 'b'.code.toByte()),
                    Files.readAllBytes(workspace.resolve("sample.txt")),
                )
            }
        } finally {
            Files.walk(workspace).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
