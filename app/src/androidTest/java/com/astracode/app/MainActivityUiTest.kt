package com.astracode.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.file.Files
import java.util.Comparator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("astracode_workspace", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.contentResolver.persistedUriPermissions.forEach { permission ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    permission.uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        val workspace = context.filesDir.toPath().resolve("workspace")
        if (Files.exists(workspace)) {
            Files.walk(workspace).use { paths ->
                paths.filter { it != workspace }
                    .sorted(Comparator.reverseOrder<java.nio.file.Path>())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun canCreateEditSaveAndReopenAWorkspaceFile() {
        composeRule.onNodeWithText("AstraCode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add workspace folder").assertIsDisplayed()
        composeRule.onNodeWithText("New file").performClick()
        composeRule.onNodeWithTag("new-file-name-input").performTextInput("Hello.kt")
        composeRule.onNodeWithTag("confirm-create-file").performClick()

        val editor = composeRule.onNodeWithTag("file-editor-input")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { editor.assertIsDisplayed() }.isSuccess
        }
        editor.assertIsDisplayed()
        val expectedText = "fun main() = println(\"AstraCode\")"
        editor.performTextInput(expectedText)
        composeRule.onNodeWithTag("save-file-button").performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedFile = context.filesDir.toPath().resolve("workspace/Hello.kt")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { String(Files.readAllBytes(savedFile), Charsets.UTF_8) == expectedText }.getOrDefault(false)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("FILE EDITOR").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Hello.kt").assertIsDisplayed()
        composeRule.onNodeWithText("Hello.kt").performClick()
        composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed()
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("fun main() = println(\"AstraCode\")")
    }

    @Test
    fun recreatingActivityDoesNotShowCancellationAsFolderLoadFailure() {
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Could not load folder", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun savedFilesManagerOffersFilePickerAndPersistentBookmarkArea() {
        composeRule.onNodeWithTag("manage-saved-documents-button").performClick()
        composeRule.onNodeWithText("No files saved yet. Choose a file to add one.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("choose-standalone-file-button").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Files stay here until you remove them. Removing a bookmark only removes AstraCode's access; it does not delete the original file.",
        ).assertIsDisplayed()
    }

    @Test
    fun workspaceManagerShowsPrivateWorkspaceAndAddFolderAction() {
        composeRule.onNodeWithContentDescription("Manage workspaces").performClick()
        composeRule.onNodeWithText("Saved workspaces").assertIsDisplayed()
        composeRule.onNodeWithTag("private-workspace-bookmark").assertIsDisplayed()
        composeRule.onNodeWithText("Add folder").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Removing a folder only removes its access from AstraCode; it does not delete its files.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun workspacePickerAndPrivateWorkspaceEntryAreAvailable() {
        composeRule.onNodeWithContentDescription("Add workspace folder").assertIsDisplayed()
        composeRule.onNodeWithText("Private app workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("open-standalone-file-button").assertIsDisplayed()
        composeRule.onNodeWithTag("manage-saved-documents-button").assertIsDisplayed()
    }

    @Test
    fun emptyWorkspaceExplainsHowToCreateAFile() {
        composeRule.onNodeWithText("A clean workspace.").assertIsDisplayed()
        composeRule.onNodeWithText("Create your first file").assertIsDisplayed()
        composeRule.onNodeWithText("0 ITEMS").assertIsDisplayed()
    }

    @Test
    fun folderRowsShowDirectChildCountsAndHiddenFilesCanBeEdited() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("app/src"))
        Files.createDirectories(workspace.resolve("empty"))
        Files.write(workspace.resolve(".gitignore"), "build/\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("README.txt"), "Workspace fixture\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("app/build.gradle.kts"), "plugins {}\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("app/src/Main.kt"), "fun main() = Unit\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("app/src/.env"), "TOKEN=original".toByteArray(Charsets.UTF_8))

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("4 ITEMS").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-.gitignore").assertTextContains("Hidden file", substring = true)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("entry-app").assertTextContains("2 items", substring = true)
                composeRule.onNodeWithTag("entry-empty").assertTextContains("0 items", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("entry-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-src").assertTextContains("2 items", substring = true) }.isSuccess
        }
        composeRule.onNodeWithTag("entry-src").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-.env").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-.env").assertTextContains("Hidden file · 14 B", substring = true)
        composeRule.onNodeWithTag("entry-.env").performClick()
        composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed()
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement("TOKEN=updated")
        composeRule.onNodeWithTag("save-file-button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                workspace.resolve("app/src/.env").toFile().readText() == "TOKEN=updated"
            }.getOrDefault(false)
        }
        org.junit.Assert.assertEquals(
            "TOKEN=updated",
            workspace.resolve("app/src/.env").toFile().readText(),
        )
    }

    @Test
    fun mobileBackReturnsToPreviousBottomNavigationScreen() {
        composeRule.onNodeWithTag("bottom-nav-search").performClick()
        composeRule.onNodeWithText("Search & inspect").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("A clean workspace.").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertEquals(false, activity.isFinishing)
        }
    }

    @Test
    fun inspectorIsTheOnlyFileActionEntryAndSupportsRenameCopyMove() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("folder"))
        Files.write(workspace.resolve("sample.txt"), "original".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("folder/child.txt"), "child".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("entry-sample.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-sample.txt").performTouchInput { longClick() }
        assertEquals(false, runCatching { composeRule.onNodeWithText("QUICK INSPECTOR").assertIsDisplayed() }.isSuccess)
        assertEquals(false, runCatching { composeRule.onNodeWithTag("workspace-entry-options-sample.txt").assertIsDisplayed() }.isSuccess)
        // With no long-click handler, a long hold may complete as the row's ordinary click.
        if (runCatching { composeRule.onNodeWithTag("editor-back").assertIsDisplayed() }.isSuccess) {
            composeRule.onNodeWithTag("editor-back").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeRule.onNodeWithTag("entry-sample.txt").assertIsDisplayed() }.isSuccess
            }
        }

        composeRule.onNodeWithContentDescription("Inspect sample.txt").performClick()
        composeRule.onNodeWithText("Sync").performScrollTo().performClick()
        composeRule.onNodeWithTag("sync-app-to-external").assertIsDisplayed()
        composeRule.onNodeWithTag("sync-external-to-app").assertIsDisplayed()
        composeRule.onNodeWithTag("sync-add-external-folder").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithContentDescription("Inspect sample.txt").performClick()
        composeRule.onNodeWithText("Rename").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("renamed.txt")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()
        val renamed = workspace.resolve("renamed.txt")
        composeRule.waitUntil(timeoutMillis = 5_000) { Files.exists(renamed) && !Files.exists(workspace.resolve("sample.txt")) }

        composeRule.onNodeWithContentDescription("Inspect renamed.txt").performClick()
        composeRule.onNodeWithText("Copy").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("copy.txt")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()
        val copied = workspace.resolve("copy.txt")
        composeRule.waitUntil(timeoutMillis = 5_000) { Files.exists(copied) }
        assertEquals("original", String(Files.readAllBytes(copied), Charsets.UTF_8))
        assertEquals("original", String(Files.readAllBytes(renamed), Charsets.UTF_8))

        composeRule.onNodeWithContentDescription("Inspect renamed.txt").performClick()
        composeRule.onNodeWithText("Move").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("folder/moved.txt")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()
        val moved = workspace.resolve("folder/moved.txt")
        composeRule.waitUntil(timeoutMillis = 5_000) { Files.exists(moved) && !Files.exists(renamed) }
        assertEquals("original", String(Files.readAllBytes(moved), Charsets.UTF_8))
    }

    @Test
    fun inspectorShowsMetadataForFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.write(workspace.resolve("metadata-sample.txt"), "metadata".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-metadata-sample.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Inspect metadata-sample.txt").performClick()
        composeRule.onNodeWithText("QUICK INSPECTOR").assertIsDisplayed()
        composeRule.onNodeWithText("Format").fetchSemanticsNode()
        composeRule.onNodeWithText("Created").fetchSemanticsNode()
        composeRule.onNodeWithText("Modified").fetchSemanticsNode()
    }

    @Test
    fun inspectorShowsMetadataForFolders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("metadata-folder"))
        Files.write(workspace.resolve("metadata-folder/child.txt"), "child".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-metadata-folder").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Inspect metadata-folder").performClick()
        composeRule.onNodeWithText("QUICK INSPECTOR").assertIsDisplayed()
        composeRule.onNodeWithText("Format").fetchSemanticsNode()
        composeRule.onNodeWithText("Created").fetchSemanticsNode()
        composeRule.onNodeWithText("Modified").fetchSemanticsNode()
        composeRule.onNodeWithText("Direct children").fetchSemanticsNode()
    }

    @Test
    fun multiSelectCreatesAndExtractsZipWithoutChangingFileBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("app/src"))
        val docxBytes = byteArrayOf(0, 1, -1, -128, 12, 42)
        Files.write(workspace.resolve("app/spec.docx"), docxBytes)
        Files.write(workspace.resolve("app/build.gradle.kts"), "plugins {}\\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("app/src/Main.kt"), "fun main() = Unit\\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-app").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-build.gradle.kts").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        composeRule.onNodeWithTag("workspace-select-spec.docx").performClick()
        composeRule.onNodeWithTag("workspace-select-build.gradle.kts").performClick()
        composeRule.onNodeWithTag("workspace-select-src").performClick()
        composeRule.onNodeWithTag("workspace-bulk-zip").performClick()
        composeRule.onNodeWithTag("workspace-bulk-input").performTextReplacement("release.zip")
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        // moveToZip intentionally publishes the archive before deleting sources.
        // Wait for the complete operation, not only the first observable side effect.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.exists(workspace.resolve("app/release.zip")) &&
                !Files.exists(workspace.resolve("app/spec.docx")) &&
                !Files.exists(workspace.resolve("app/build.gradle.kts")) &&
                !Files.exists(workspace.resolve("app/src"))
        }
        assertEquals(false, Files.exists(workspace.resolve("app/spec.docx")))
        assertEquals(false, Files.exists(workspace.resolve("app/build.gradle.kts")))
        assertEquals(false, Files.exists(workspace.resolve("app/src")))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-release.zip").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-icon-release.zip", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("entry-release.zip").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-extract-explanation")
            .assertTextContains("cannot be edited directly", substring = true)
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("app/restored")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.exists(workspace.resolve("app/restored/spec.docx")) &&
                Files.exists(workspace.resolve("app/restored/build.gradle.kts")) &&
                Files.exists(workspace.resolve("app/restored/src/Main.kt"))
        }
        assertArrayEquals(docxBytes, Files.readAllBytes(workspace.resolve("app/restored/spec.docx")))
        assertEquals(
            "plugins {}\\n",
            String(Files.readAllBytes(workspace.resolve("app/restored/build.gradle.kts")), Charsets.UTF_8),
        )
        assertEquals(
            "fun main() = Unit\\n",
            String(Files.readAllBytes(workspace.resolve("app/restored/src/Main.kt")), Charsets.UTF_8),
        )
        assertEquals(false, Files.exists(workspace.resolve("app/restored/app")))
    }

    @Test
    fun multiSelectCreatesZipAndExtractsBinaryAndSourceFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("src"))
        val documentBytes = byteArrayOf(0, 1, 2, 127, -1, -128, 42)
        Files.write(workspace.resolve("Document.docx"), documentBytes)
        Files.write(workspace.resolve("src/Main.kt"), "fun main() = Unit\\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("entry-Document.docx").assertIsDisplayed()
                composeRule.onNodeWithTag("entry-src").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        composeRule.onNodeWithTag("workspace-select-Document.docx").performClick()
        composeRule.onNodeWithTag("workspace-select-src").performClick()
        composeRule.onNodeWithTag("workspace-bulk-zip").performClick()
        composeRule.onNodeWithTag("workspace-bulk-input").performTextReplacement("bundle.zip")
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        val archive = workspace.resolve("bundle.zip")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.isRegularFile(archive) &&
                !Files.exists(workspace.resolve("Document.docx")) &&
                !Files.exists(workspace.resolve("src")) &&
                runCatching {
                    composeRule.onNodeWithTag("workspace-toggle-selection").assertTextContains("Select")
                    composeRule.onNodeWithTag("entry-bundle.zip").assertIsDisplayed()
                }.isSuccess
        }
        composeRule.onNodeWithTag("entry-icon-bundle.zip", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("entry-bundle.zip").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-extract-explanation")
            .assertTextContains("cannot be edited directly", substring = true)
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("restored")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        val restoredDocument = workspace.resolve("restored/Document.docx")
        val restoredSource = workspace.resolve("restored/src/Main.kt")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.isRegularFile(restoredDocument) && Files.isRegularFile(restoredSource)
        }
        assertArrayEquals(documentBytes, Files.readAllBytes(restoredDocument))
        assertEquals(
            "fun main() = Unit\\n",
            String(Files.readAllBytes(restoredSource), Charsets.UTF_8),
        )
    }

    
    @Test
    fun workspaceSafetyToolsSearchPreviewBatchRenameAndTrashUndoWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("app/src"))
        Files.write(workspace.resolve("README.txt"), "Workspace fixture\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("app/src/Main.kt"), "fun main() = Unit\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("workspace-safety-tools").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-safety-tools").performClick()
        composeRule.onNodeWithTag("workspace-safety-search-input").performTextReplacement("Main.kt")
        composeRule.onNodeWithTag("workspace-safety-search").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-safety-results")
                    .assertTextContains("app/src/Main.kt", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-preview-result-0").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-safety-preview-text")
                    .assertTextContains("fun main() = Unit", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-safety-preview-close").performScrollTo().performClick()

        composeRule.onNodeWithTag("workspace-batch-rename-input")
            .performScrollTo()
            .performTextReplacement("README.txt=README-renamed.txt")
        composeRule.onNodeWithTag("workspace-batch-rename").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-batch-rename-confirm").assertIsDisplayed()
        assertEquals(false, Files.exists(workspace.resolve("README-renamed.txt")))
        composeRule.onNodeWithTag("workspace-batch-rename-confirm").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.exists(workspace.resolve("README-renamed.txt")) &&
                !Files.exists(workspace.resolve("README.txt"))
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-safety-close").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-safety-close").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("entry-README-renamed.txt").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Inspect README-renamed.txt").performClick()
        composeRule.onNodeWithText("Move to Trash").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm-workspace-delete").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !Files.exists(workspace.resolve("README-renamed.txt"))
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithText("Undo").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.exists(workspace.resolve("README-renamed.txt")) &&
                workspace.resolve("README-renamed.txt").toFile().readText() == "Workspace fixture\n"
        }
    }

}
