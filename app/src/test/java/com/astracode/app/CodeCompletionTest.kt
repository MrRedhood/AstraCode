package com.astracode.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCompletionTest {
    @Test
    fun completionUsesTheDetectedKotlinLanguageProfile() {
        val source = "fun pri"
        val suggestions = codeCompletionSuggestions(source, "src/Main.kt", source.length)
        assertTrue(suggestions.contains("println"))
        assertTrue(suggestions.contains("print"))
    }

    @Test
    fun documentIdentifiersAreSuggestedWhenTheyMatchThePrefix() {
        val source = "fun greeting() = 1\nval result = gre"
        val suggestions = codeCompletionSuggestions(source, "src/Main.kt", source.length)
        assertTrue(suggestions.contains("greeting"))
    }

    @Test
    fun completionUsesPythonWordsForPythonFiles() {
        val source = "def pri"
        assertTrue(codeCompletionSuggestions(source, "main.py", source.length).contains("print"))
    }

    @Test
    fun selectingCompletionReplacesOnlyTheCurrentIdentifierPrefix() {
        val value = TextFieldValue("fun pri()", selection = TextRange(7))
        val completed = applyCodeCompletionAtRange(value, "println", TextRange(4, 7))
        assertEquals("fun println()", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun completionAtMiddleOfLinePreservesSurroundingTextAndCaret() {
        val source = "val result = pri + 1"
        val caret = source.indexOf("pri") + "pri".length
        val prefixRange = codeCompletionPrefixRange(source, caret)
        assertTrue(codeCompletionSuggestions(source, "Main.kt", caret).contains("println"))

        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "println",
            prefixRange!!,
        )

        assertEquals("val result = println + 1", completed?.text)
        assertEquals(TextRange(source.indexOf("pri") + "println".length), completed?.selection)
    }

    @Test
    fun completionAtMiddleOfIdentifierReplacesTheWholeIdentifier() {
        val source = "val result = printf + 1"
        val caret = source.indexOf("pri") + "pri".length
        val prefixRange = codeCompletionPrefixRange(source, caret)!!

        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "println",
            prefixRange,
        )

        assertEquals("val result = println + 1", completed?.text)
        assertEquals(TextRange(source.indexOf("pri") + "println".length), completed?.selection)
    }

    @Test
    fun completionRejectsARangeStartingInsideAnotherIdentifier() {
        val value = TextFieldValue("xpri", selection = TextRange(4))
        assertEquals(null, applyCodeCompletionAtRange(value, "println", TextRange(1, 4)))
    }

    @Test
    fun cssCompletionSupportsHyphenatedPropertiesAndReplacesTheWholeProperty() {
        val source = "body { font-size: 12px; }"
        val caret = source.indexOf("font-size") + "font-si".length
        val suggestions = codeCompletionSuggestions(source, "styles.css", caret)
        assertTrue(suggestions.contains("font-size"))

        val prefixRange = codeCompletionPrefixRange(source, caret, "styles.css")!!
        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "font-size",
            prefixRange,
            "styles.css",
        )

        assertEquals(source, completed?.text)
        assertEquals(
            TextRange(source.indexOf("font-size") + "font-size".length),
            completed?.selection,
        )
    }

    @Test
    fun unicodeIdentifiersCanBeSuggestedFromTheCurrentDocument() {
        val source = "val λογαριασμός = 1\nval result = λογα"
        val suggestions = codeCompletionSuggestions(source, "Main.kt", source.length)
        assertTrue(suggestions.contains("λογαριασμός"))
    }

    @Test
    fun cssCustomPropertiesAreSuggestedAndAppliedAsWholeIdentifiers() {
        val source = ":root { --brand-color: red; } .card { color: var(--brand); }"
        val prefix = "--brand"
        val tokenStart = source.lastIndexOf(prefix)
        val caret = tokenStart + prefix.length
        val suggestions = codeCompletionSuggestions(source, "styles.css", caret)

        assertTrue(suggestions.contains("--brand-color"))

        val range = codeCompletionPrefixRange(source, caret, "styles.css")!!
        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "--brand-color",
            range,
            "styles.css",
        )

        assertEquals(
            source.replaceRange(tokenStart, tokenStart + prefix.length, "--brand-color"),
            completed?.text,
        )
        assertEquals(TextRange(tokenStart + "--brand-color".length), completed?.selection)
    }

    @Test
    fun htmlHyphenatedAttributesAreSuggestedAndAppliedAsWholeIdentifiers() {
        val source = """<div data-user-name="one"></div><div data-user-x="two"></div>"""
        val prefix = "data-user-"
        val tokenStart = source.lastIndexOf(prefix)
        val caret = tokenStart + prefix.length
        val suggestions = codeCompletionSuggestions(source, "index.html", caret)

        assertTrue(suggestions.contains("data-user-name"))

        val range = codeCompletionPrefixRange(source, caret, "index.html")!!
        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "data-user-name",
            range,
            "index.html",
        )

        assertEquals(
            source.replaceRange(tokenStart, tokenStart + "data-user-x".length, "data-user-name"),
            completed?.text,
        )
        assertEquals(TextRange(tokenStart + "data-user-name".length), completed?.selection)
    }

    @Test
    fun suggestionsFollowChangedPrefixAndCaretPosition() {
        val source = "println()"
        val afterPrin = codeCompletionSuggestions(source, "Main.kt", 4)
        assertTrue(afterPrin.contains("println"))

        val afterPrint = codeCompletionSuggestions(source, "Main.kt", 5)
        assertTrue(afterPrint.contains("println"))
        assertFalse(afterPrint.contains("print"))

        assertTrue(codeCompletionSuggestions(source, "Main.kt", source.length).isEmpty())
    }

    @Test
    fun completionStillTargetsTheActiveCaretWhenTheSelectionAnchorMoves() {
        val value = TextFieldValue("fun pri()", selection = TextRange(4, 7))
        val completed = applyCodeCompletionAtRange(value, "println", TextRange(4, 7))
        assertEquals("fun println()", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun applyingCompletionUsesTheCapturedPrefixEvenIfTheCaretMoves() {
        val value = TextFieldValue("fun pri", selection = TextRange(0))
        val completed = applyCodeCompletionAtRange(value, "println", TextRange(4, 7))
        assertEquals("fun println", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun compoundFileSuffixSelectsTheMoreSpecificLanguage() {
        assertEquals("Gradle Kotlin DSL", detectSyntaxLanguage("app/build.gradle.kts")?.name)
    }

    @Test
    fun plainTextAndUnknownFilesDoNotOfferCodeCompletions() {
        assertTrue(codeCompletionSuggestions("pri", "notes.txt", 3).isEmpty())
        assertTrue(codeCompletionSuggestions("some wor", "README.md", 8).isEmpty())
    }

    @Test
    fun serverSuggestionsArePrioritizedWhenMergedWithOfflineSuggestions() {
        assertEquals(
            listOf("println", "print", "private"),
            mergeCodeCompletionSuggestions(
                languageServerSuggestions = listOf("println", "print"),
                localSuggestions = listOf("print", "private"),
            ),
        )
    }

    @Test
    fun mergedSuggestionsDropBlankLabelsAndCaseInsensitiveDuplicates() {
        assertEquals(
            listOf("println", "print", "private"),
            mergeCodeCompletionSuggestions(
                languageServerSuggestions = listOf("", "println", "Println", "print"),
                localSuggestions = listOf("PRINT", "private", "Private"),
            ),
        )
    }

    @Test
    fun mergedSuggestionsRespectTheRequestedLimitAndGlobalMaximum() {
        val suggestions = (1..20).map { "symbol$it" }

        assertEquals(
            listOf("symbol1", "symbol2", "symbol3"),
            mergeCodeCompletionSuggestions(emptyList(), suggestions, limit = 3),
        )
        assertEquals(
            16,
            mergeCodeCompletionSuggestions(emptyList(), suggestions, limit = 20).size,
        )
        assertEquals(
            emptyList<String>(),
            mergeCodeCompletionSuggestions(suggestions, emptyList(), limit = 0),
        )
    }


    @Test
    fun kotlinCompletionsIncludeCoroutineAndModernLanguageSymbols() {
        val source = "val value = susp"
        assertTrue(codeCompletionSuggestions(source, "Main.kt", source.length).contains("suspend"))
        assertTrue(codeCompletionSuggestions(source, "Main.kt", source.length).contains("supervisorScope"))
    }

    @Test
    fun javaCompletionsIncludeModernLanguageAndJdkSymbols() {
        val source = "class Example { rec"
        assertTrue(codeCompletionSuggestions(source, "Example.java", source.length).contains("record"))
    }

    @Test
    fun javascriptCompletionsIncludeBrowserAndPlatformApis() {
        val source = "const value = URL"
        assertTrue(codeCompletionSuggestions(source, "main.js", source.length).contains("URLSearchParams"))
    }

    @Test
    fun typescriptCompletionsIncludeTypeOperatorsAndUtilityTypes() {
        val source = "type Value = key"
        assertTrue(codeCompletionSuggestions(source, "main.ts", source.length).contains("keyof"))
    }

    @Test
    fun rustCompletionsIncludeOwnershipAndStandardLibrarySymbols() {
        val source = "let result: Res"
        assertTrue(codeCompletionSuggestions(source, "main.rs", source.length).contains("Result"))
    }

    @Test
    fun goCompletionsIncludeContextAndStandardLibrarySymbols() {
        val source = "ctx, cancel := context.WithT"
        assertTrue(codeCompletionSuggestions(source, "main.go", source.length).contains("WithTimeout"))
    }

    @Test
    fun cppCompletionsIncludeSmartPointersAndStandardLibrarySymbols() {
        val source = "auto value = std::unique"
        assertTrue(codeCompletionSuggestions(source, "main.cpp", source.length).contains("unique_ptr"))
    }

    @Test
    fun csharpAndCCompletionsIncludeTheirStandardLibrarySymbols() {
        val csharp = "var token = Cancel"
        val c = "size_t length = strn"
        assertTrue(codeCompletionSuggestions(csharp, "Main.cs", csharp.length).contains("CancellationToken"))
        assertTrue(codeCompletionSuggestions(c, "main.c", c.length).contains("strncmp"))
    }

    @Test
    fun swiftAndDartCompletionsIncludeTheirCoreLanguageSymbols() {
        val swift = "gua"
        val dart = "Fut"
        assertTrue(codeCompletionSuggestions(swift, "Main.swift", swift.length).contains("guard"))
        assertTrue(codeCompletionSuggestions(dart, "main.dart", dart.length).contains("Future"))
    }

    @Test
    fun groovyAndPhpCompletionsIncludeTheirCommonApis() {
        val groovy = "GStr"
        val php = "str_con"
        assertTrue(codeCompletionSuggestions(groovy, "build.gradle", groovy.length).contains("GString"))
        assertTrue(codeCompletionSuggestions(php, "index.php", php.length).contains("str_contains"))
    }

    @Test
    fun caseSensitiveLocalMatchesArePrioritized() {
        val source = "val printCount = 1\nval PrintConfig = 2\nval result = Pri"
        val suggestions = codeCompletionSuggestions(source, "Main.kt", source.length)
        assertEquals("PrintConfig", suggestions.first())
    }

    @Test
    fun nearbyLocalIdentifiersArePrioritizedOverDistantOnes() {
        val source = "val prior = 1\nval privateValue = 2\nval result = pri"
        val suggestions = codeCompletionSuggestions(source, "Main.kt", source.length)
        assertEquals("privateValue", suggestions.first())
        assertEquals("prior", suggestions[1])
    }

    @Test
    fun supplementaryUnicodeLettersAreSupportedInIdentifierRangesAndSuggestions() {
        val source = "val 𐐀printable = 1\nval result = 𐐀pri"
        val caret = source.length
        val range = codeCompletionPrefixRange(source, caret, "Main.kt")
        assertEquals(TextRange(source.lastIndexOf("𐐀pri"), caret), range)
        assertTrue(codeCompletionSuggestions(source, "Main.kt", caret).contains("𐐀printable"))
    }

    @Test
    fun completionMergeNormalizesUnicodeAndRejectsMalformedLabels() {
        assertEquals(
            listOf("println", "private"),
            mergeCodeCompletionSuggestions(
                languageServerSuggestions = listOf("  println  ", "Ｐrintln", "print\nline"),
                localSuggestions = listOf("private"),
            ),
        )
    }

    @Test
    fun applyingCompletionRejectsRangesThatSplitSupplementaryCharacters() {
        val value = TextFieldValue("𐐀pri", selection = TextRange(5))
        assertEquals(
            null,
            applyCodeCompletionAtRange(value, "print", TextRange(1, 4), "Main.kt"),
        )
    }

    @Test
    fun singleCharacterPrefixesCanShowBoundedSuggestions() {
        val source = "f"
        val suggestions = codeCompletionSuggestions(source, "Main.kt", source.length)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 16)
    }

}
