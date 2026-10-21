package com.astracode.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FileFormatLabelsTest {
    @Test
    fun extensionlessNewNamesBecomeTxtAndExplicitExtensionsStayUnchanged() {
        assertEquals("my file (1).txt", normalizeNewTextFileName("my file (1)"))
        assertEquals("README.txt", normalizeNewTextFileName("README."))
        assertEquals(".env.txt", normalizeNewTextFileName(".env"))
        assertEquals("Hello.kt", normalizeNewTextFileName("Hello.kt"))
        assertEquals("README.md", normalizeNewTextFileName("README.md"))
    }

    @Test
    fun labelsIncludeExtensionAndReadableDescription() {
        assertEquals("txt file (text file)", formatFileTypeLabel("note.txt"))
        assertEquals("md file (Markdown document)", formatFileTypeLabel("README.md"))
        assertEquals("kt file (Kotlin source file)", formatFileTypeLabel("Main.kt"))
        assertEquals("zip file (ZIP archive)", formatFileTypeLabel("project.zip"))
        assertEquals("aab file (Android App Bundle)", formatFileTypeLabel("app.aab"))
        assertEquals("xyz file (xyz format)", formatFileTypeLabel("data.xyz"))
        assertEquals("File (no extension)", formatFileTypeLabel("LICENSE"))
    }
}
