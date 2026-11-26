package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiGitIgnoreMatcherTest {
    @Test
    fun matchesRootAndNestedIgnorePatterns() {
        val matcher = AiGitIgnoreMatcher(
            listOf(
                AiGitIgnoreFile(
                    "",
                    """
                    # Generated and downloaded content
                    build/
                    *.tmp
                    node_modules/
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue(matcher.isIgnored("build/output.apk"))
        assertTrue(matcher.isIgnored("src/cache.tmp"))
        assertTrue(matcher.isIgnored("node_modules/react/index.js"))
        assertFalse(matcher.isIgnored("src/Main.kt"))
    }

    @Test
    fun appliesNestedGitIgnoreRulesOnlyInsideTheirDirectory() {
        val matcher = AiGitIgnoreMatcher(
            listOf(
                AiGitIgnoreFile("", "*.log"),
                AiGitIgnoreFile("app", "!keep.log\nprivate/"),
            ),
        )
        assertTrue(matcher.isIgnored("root.log"))
        assertFalse(matcher.isIgnored("app/keep.log"))
        assertTrue(matcher.isIgnored("app/private/config.json"))
        assertTrue(matcher.isIgnored("other/keep.log"))
    }

    @Test
    fun negatedRulesCanReIncludeFiles() {
        val matcher = AiGitIgnoreMatcher(
            listOf(
                AiGitIgnoreFile("", "*.tmp\n!important.tmp"),
            ),
        )
        assertTrue(matcher.isIgnored("cache.tmp"))
        assertFalse(matcher.isIgnored("important.tmp"))
    }

    @Test
    fun supportsAnchoredAndSlashContainingPatterns() {
        val matcher = AiGitIgnoreMatcher(
            listOf(
                AiGitIgnoreFile("", "/root-only.txt\nlogs/debug.log"),
            ),
        )
        assertTrue(matcher.isIgnored("root-only.txt"))
        assertFalse(matcher.isIgnored("src/root-only.txt"))
        assertTrue(matcher.isIgnored("logs/debug.log"))
        assertFalse(matcher.isIgnored("src/logs/debug.log"))
    }

    @Test
    fun supportsDoubleStarPatternsAtAnyDirectoryDepth() {
        val matcher = AiGitIgnoreMatcher(
            listOf(AiGitIgnoreFile("", "**/generated/**/*.kt")),
        )
        assertTrue(matcher.isIgnored("generated/Build.kt"))
        assertTrue(matcher.isIgnored("app/generated/ui/Screen.kt"))
        assertFalse(matcher.isIgnored("app/src/ui/Screen.kt"))
    }

    @Test
    fun ignoresInvalidAbsoluteOrBackslashPaths() {
        val matcher = AiGitIgnoreMatcher(emptyList())
        assertTrue(matcher.isIgnored("../outside.kt"))
        assertTrue(matcher.isIgnored("C:\\workspace\\secret.kt"))
    }
}
