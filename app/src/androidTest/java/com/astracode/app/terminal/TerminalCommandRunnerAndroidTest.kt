package com.astracode.app.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astracode.terminal.TerminalCommandRunner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalCommandRunnerAndroidTest {
    @Test
    fun executesCommandWithAndroidSystemShellAndToyboxSessionLauncher() {
        val workspace = createWorkspace()
        try {
            val runner = TerminalCommandRunner(workspace, shellOverride = "/system/bin/sh")
            val result = runner.execute(
                "printf 'android-terminal-ok'; printf 'android-terminal-error' >&2",
            )

            assertEquals("android-terminal-ok", result.standardOutput)
            assertEquals("android-terminal-error", result.standardError)
            assertEquals(0, result.exitCode)
            assertTrue(result.succeeded)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun timeoutTerminatesBackgroundChildOnAndroid() {
        val workspace = createWorkspace()
        try {
            val runner = TerminalCommandRunner(workspace, shellOverride = "/system/bin/sh")
            val marker = File(workspace, "timeout-child-finished")
            val result = runner.execute(
                "(sleep 1; printf done > '${marker.absolutePath}') & wait",
                timeoutMillis = 200L,
            )

            assertTrue(result.timedOut)
            Thread.sleep(1_200L)
            assertFalse("A background child survived the Android timeout", marker.exists())
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun normalExitCleansUpBackgroundChildOnAndroid() {
        val workspace = createWorkspace()
        try {
            val runner = TerminalCommandRunner(workspace, shellOverride = "/system/bin/sh")
            val marker = File(workspace, "normal-exit-child-finished")
            val result = runner.execute(
                "(sleep 1; printf done > '${marker.absolutePath}') >/dev/null 2>&1 & exit 0",
            )

            assertEquals(0, result.exitCode)
            Thread.sleep(1_200L)
            assertFalse("A background child survived normal shell exit", marker.exists())
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun createWorkspace(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = File(context.cacheDir, "astracode-terminal-${System.nanoTime()}")
        assertTrue("Could not create terminal test workspace", workspace.mkdirs())
        return workspace
    }
}
