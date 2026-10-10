package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilePolicyTest {
    @Test fun supportsCommonTextAndCodeFiles(){
        assertTrue(WorkspaceFilePolicy.supportsTextPreview("MainActivity.kt","application/octet-stream"))
        assertTrue(WorkspaceFilePolicy.supportsTextPreview("README.md","application/octet-stream"))
        assertTrue(WorkspaceFilePolicy.supportsTextPreview("settings","text/plain"))
    }
    @Test fun rejectsUnknownBinaryFiles(){
        assertFalse(WorkspaceFilePolicy.supportsTextPreview("screenshot.png","image/png"))
        assertFalse(WorkspaceFilePolicy.supportsTextPreview("archive.zip","application/zip"))
    }
    @Test fun rejectsPathTraversalNames(){
        assertNull(WorkspaceFilePolicy.validateName("MainActivity.kt"))
        assertNull(WorkspaceFilePolicy.validateName(" docs "))
        assertTrue(WorkspaceFilePolicy.validateName("").orEmpty().isNotEmpty())
        assertTrue(WorkspaceFilePolicy.validateName("..").orEmpty().isNotEmpty())
        assertTrue(WorkspaceFilePolicy.validateName("../outside.txt").orEmpty().isNotEmpty())
        assertTrue(WorkspaceFilePolicy.validateName("bad\\name").orEmpty().isNotEmpty())
    }

    @Test fun AIOnlyAllowsKnownTextAndCodeNamesForNewFiles() {
        assertTrue(WorkspaceFilePolicy.supportsAiTextCreate("Main.kt"))
        assertTrue(WorkspaceFilePolicy.supportsAiTextCreate("README.md"))
        assertTrue(WorkspaceFilePolicy.supportsAiTextCreate(".gitignore"))
        assertTrue(WorkspaceFilePolicy.supportsAiTextCreate("config.json"))
        assertFalse(WorkspaceFilePolicy.supportsAiTextCreate("picture.png"))
        assertFalse(WorkspaceFilePolicy.supportsAiTextCreate("archive.zip"))
        assertFalse(WorkspaceFilePolicy.supportsAiTextCreate("secret"))
    }
    @Test fun mapsNewFileExtensionsToMimeTypes(){
        assertEquals("application/json",WorkspaceFilePolicy.mimeTypeForNewFile("config.json"))
        assertEquals("text/html",WorkspaceFilePolicy.mimeTypeForNewFile("index.html"))
        assertEquals("text/plain",WorkspaceFilePolicy.mimeTypeForNewFile("MainActivity.kt"))
    }
}
