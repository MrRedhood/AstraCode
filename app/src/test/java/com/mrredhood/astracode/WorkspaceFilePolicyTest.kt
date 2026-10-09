package com.mrredhood.astracode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilePolicyTest {
    @Test
    fun supportsCommonTextAndCodeFiles() {
        assertTrue(WorkspaceFilePolicy.supportsTextPreview("MainActivity.kt", "application/octet-stream"))
        assertTrue(WorkspaceFilePolicy.supportsTextPreview("README.md", "application/octet-stream"))
        assertTrue(WorkspaceFilePolicy.supportsTextPreview("settings", "text/plain"))
    }

    @Test
    fun rejectsUnknownBinaryFiles() {
        assertFalse(WorkspaceFilePolicy.supportsTextPreview("screenshot.png", "image/png"))
        assertFalse(WorkspaceFilePolicy.supportsTextPreview("archive.zip", "application/zip"))
    }
}
