package com.mrredhood.astracode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPreviewPolicyTest {
    @Test
    fun supportsWebPreviewExtensionsCaseInsensitively() {
        assertTrue(WebPreviewPolicy.supports("index.HTML"))
        assertTrue(WebPreviewPolicy.supports("style.css"))
        assertTrue(WebPreviewPolicy.supports("app.mjs"))
        assertFalse(WebPreviewPolicy.supports("MainActivity.kt"))
        assertFalse(WebPreviewPolicy.supports("image.png"))
    }

    @Test
    fun injectsRestrictivePolicyIntoExistingHtmlHead() {
        val result = requireNotNull(
            WebPreviewPolicy.buildDocument("index.html", "<html><head><title>Demo</title></head><body>Hi</body></html>")
        )
        assertTrue(result.contains("Content-Security-Policy"))
        assertTrue(result.contains("default-src 'none'"))
        assertTrue(result.contains("connect-src 'none'"))
        assertTrue(result.indexOf("<head>") < result.indexOf("Content-Security-Policy"))
        assertTrue(result.contains("<title>Demo</title>"))
    }

    @Test
    fun wrapsHtmlFragmentsAndCreatesCssAndJsPlaygrounds() {
        val html = requireNotNull(WebPreviewPolicy.buildDocument("page.htm", "<h1>Hello</h1>"))
        assertTrue(html.contains("<body><h1>Hello</h1></body>"))

        val css = requireNotNull(WebPreviewPolicy.buildDocument("style.css", "h1 { color: red; }"))
        assertTrue(css.contains("<style>h1 { color: red; }</style>"))
        assertTrue(css.contains("CSS preview"))

        val js = requireNotNull(WebPreviewPolicy.buildDocument("app.js", "document.title = 'Updated';"))
        assertTrue(js.contains("<script>document.title = 'Updated';</script>"))
        assertTrue(js.contains("JavaScript preview"))
    }

    @Test
    fun rejectsUnsupportedAndOversizedPreviewDocuments() {
        assertNull(WebPreviewPolicy.buildDocument("Main.kt", "fun main() {}"))
        assertNull(WebPreviewPolicy.buildDocument("index.html", "x".repeat(WebPreviewPolicy.MAX_PREVIEW_BYTES + 1)))
        assertNotNull(WebPreviewPolicy.buildDocument("index.html", "x".repeat(1024)))
    }
}
