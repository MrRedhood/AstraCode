package com.astracode.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun currentEditableEditorText(): String = composeRule
        .onNodeWithTag("file-editor-input")
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    @Test
    fun canCreateEditSaveAndReopenAWorkspaceFile() {
        composeRule.onNodeWithText("AstraCode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add workspace folder").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-create-menu-button").performClick()
        composeRule.onNodeWithTag("new-file-button").performClick()
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
        composeRule.onNodeWithTag("file-editor-input")
            .assertTextContains("fun main() = println(\"AstraCode\")", substring = true)
    }

    @Test
    fun kotlinAutocompleteSuggestsAndAppliesThroughTheWorkspaceEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val sourceFile = workspace.resolve("Completion.kt")
        Files.write(sourceFile, byteArrayOf())
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-file-list").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("entry-Completion.kt"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-Completion.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-Completion.kt").performClick()
        val editor = composeRule.onNodeWithTag("file-editor-input")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { editor.assertIsDisplayed() }.isSuccess
        }
        editor.performClick().performTextInput("fun pri")
        composeRule.onNodeWithTag("editor-completion-println").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-completion-println").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { assertEquals("1 │ fun println", currentEditableEditorText()) }.isSuccess
        }
        assertEquals("1 │ fun println", currentEditableEditorText())
        composeRule.waitUntil(timeoutMillis = 10_000) {
            String(Files.readAllBytes(sourceFile), Charsets.UTF_8) == "fun println"
        }
        assertEquals("fun println", String(Files.readAllBytes(sourceFile), Charsets.UTF_8))
    }


    @Test
    fun multipleWorkspaceEditorTabsKeepDraftsSeparateAndSaveCorrectFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        Files.write(workspace.resolve("README.txt"), "Workspace fixture\n".toByteArray(Charsets.UTF_8))
        Files.write(workspace.resolve("Main.kt"), "fun main() = Unit\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-README.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("Workspace fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement("README unsaved draft")
        composeRule.onNodeWithTag("editor-back").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-Main.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-Main.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("fun main() = Unit", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement("fun main() = 42")

        composeRule.onNodeWithTag("editor-tabs")
            .performScrollToNode(hasTestTag("editor-tab-README.txt"))
        composeRule.onNodeWithTag("editor-tab-README.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { assertEquals("1 │ README unsaved draft", currentEditableEditorText()) }.isSuccess
        }
        composeRule.onNodeWithTag("editor-tabs")
            .performScrollToNode(hasTestTag("editor-tab-Main.kt"))
        composeRule.onNodeWithTag("editor-tab-Main.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { assertEquals("1 │ fun main() = 42", currentEditableEditorText()) }.isSuccess
        }
        composeRule.onNodeWithTag("save-file-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { String(Files.readAllBytes(workspace.resolve("Main.kt")), Charsets.UTF_8) == "fun main() = 42" }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("editor-tabs")
            .performScrollToNode(hasTestTag("editor-tab-README.txt"))
        composeRule.onNodeWithTag("editor-tab-README.txt").performClick()
        composeRule.onNodeWithTag("save-file-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { String(Files.readAllBytes(workspace.resolve("README.txt")), Charsets.UTF_8) == "README unsaved draft" }.getOrDefault(false)
        }
        assertEquals("fun main() = 42", String(Files.readAllBytes(workspace.resolve("Main.kt")), Charsets.UTF_8))
        assertEquals("README unsaved draft", String(Files.readAllBytes(workspace.resolve("README.txt")), Charsets.UTF_8))
    }

    @Test
    fun editorTabClosePromptDoesNotRaceAutosave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val file = workspace.resolve("README.txt")
        val original = "original\n"
        val draft = "unsaved draft"
        Files.write(file, original.toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-README.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement(draft)
        composeRule.onNodeWithTag("editor-tab-close-README.txt").performClick()

        val promptVisible = runCatching {
            composeRule.onNodeWithTag("editor-tab-discard-confirm").assertIsDisplayed()
        }.isSuccess

        if (promptVisible) {
            // If a save began just before the prompt appeared, wait until it finishes.
            // If the file is still unchanged, autosave is correctly paused and discard is safe.
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { composeRule.onNodeWithTag("editor-tab-discard-confirm").assertIsEnabled() }.isSuccess
            }
            val currentDiskText = String(Files.readAllBytes(file), Charsets.UTF_8)
            if (currentDiskText == original) {
                composeRule.onNodeWithTag("editor-tab-discard-confirm").performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
                }
                assertEquals(original, String(Files.readAllBytes(file), Charsets.UTF_8))
                return
            }

            // The save finished before the user could decide. Cancel leaves the saved
            // content in place; the tab must no longer pretend those changes are discardable.
            composeRule.onNodeWithTag("editor-tab-discard-cancel").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("file-editor-input").assertTextContains(draft, substring = true)
                }.isSuccess
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { String(Files.readAllBytes(file), Charsets.UTF_8) == draft }.getOrDefault(false)
        }
        if (runCatching { composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed() }.isSuccess) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("editor-tab-README.txt").assertTextEquals("README.txt")
                }.isSuccess
            }
            composeRule.onNodeWithTag("editor-tab-close-README.txt").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        assertEquals(draft, String(Files.readAllBytes(file), Charsets.UTF_8))
    }

    @Test
    fun editorTabsCanCloseAndFilesCanReopenFromWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        Files.write(workspace.resolve("WorkspaceClose.txt"), "Workspace close fixture\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-WorkspaceClose.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-WorkspaceClose.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("Workspace close fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("editor-tab-close-WorkspaceClose.txt").assertIsDisplayed()

        // Going back to Workspace keeps the tab open, but the file row itself has no close control.
        composeRule.onNodeWithTag("editor-back").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-WorkspaceClose.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onAllNodesWithTag("workspace-close-open-file-WorkspaceClose.txt").assertCountEquals(0)

        // Reopen the existing tab, then close it with the control beside its editor-tab filename.
        composeRule.onNodeWithTag("entry-WorkspaceClose.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("Workspace close fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("editor-tab-close-WorkspaceClose.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-WorkspaceClose.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onAllNodesWithTag("editor-tab-close-WorkspaceClose.txt").assertCountEquals(0)
        composeRule.onAllNodesWithTag("workspace-close-open-file-WorkspaceClose.txt").assertCountEquals(0)

        composeRule.onNodeWithTag("entry-WorkspaceClose.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("Workspace close fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("editor-tab-close-WorkspaceClose.txt").assertIsDisplayed()
    }

    @Test
    fun switchingEditorTabsRestoresEachUnsavedDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val firstFile = workspace.resolve("DraftOne.kt")
        val secondFile = workspace.resolve("DraftTwo.kt")
        Files.write(firstFile, "first saved".toByteArray(Charsets.UTF_8))
        Files.write(secondFile, "second saved".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("entry-DraftOne.kt"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-DraftOne.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-DraftOne.kt").performClick()
        val editor = composeRule.onNodeWithTag("file-editor-input")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { editor.assertTextContains("first saved", substring = true) }.isSuccess
        }
        editor.performTextReplacement("first unsaved draft")

        composeRule.onNodeWithTag("editor-back").performClick()
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("entry-DraftTwo.kt"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-DraftTwo.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-DraftTwo.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { editor.assertTextContains("second saved", substring = true) }.isSuccess
        }
        editor.performTextReplacement("second unsaved draft")

        composeRule.onNodeWithTag("editor-tab-DraftOne.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                assertEquals(
                    "1 │ first unsaved draft",
                    editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
                )
            }.isSuccess
        }
        composeRule.onNodeWithTag("editor-tab-DraftTwo.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                assertEquals(
                    "1 │ second unsaved draft",
                    editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
                )
            }.isSuccess
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            String(Files.readAllBytes(firstFile), Charsets.UTF_8) == "first unsaved draft" &&
                String(Files.readAllBytes(secondFile), Charsets.UTF_8) == "second unsaved draft"
        }
        assertEquals("first unsaved draft", String(Files.readAllBytes(firstFile), Charsets.UTF_8))
        assertEquals("second unsaved draft", String(Files.readAllBytes(secondFile), Charsets.UTF_8))
    }

    @Test
    fun editorKeeps25TabsAndAutoSavesOldestBeforeOpening26th() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        (1..26).forEach { index ->
            Files.write(workspace.resolve("tab-$index.txt"), "tab content $index".toByteArray(Charsets.UTF_8))
        }
        composeRule.activityRule.scenario.recreate()
        // AstraCode starts on Screen.Files; wait for the real lazy file list to load.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-file-list").assertIsDisplayed() }.isSuccess
        }

        (1..25).forEach { index ->
            composeRule.onNodeWithTag("workspace-file-list")
                .performScrollToNode(hasTestTag("entry-tab-$index.txt"))
            composeRule.onNodeWithTag("entry-tab-$index.txt").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("file-editor-input")
                        .assertTextContains("tab content $index", substring = true)
                }.isSuccess
            }
            composeRule.onNodeWithTag("editor-back").performClick()
        }

        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("entry-tab-1.txt"))
        composeRule.onNodeWithTag("entry-tab-1.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("tab content 1", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement("tab 1 autosaved draft")
        composeRule.onNodeWithTag("editor-back").performClick()

        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("entry-tab-26.txt"))
        composeRule.onNodeWithTag("entry-tab-26.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("tab content 26", substring = true)
            }.isSuccess
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            String(Files.readAllBytes(workspace.resolve("tab-1.txt")), Charsets.UTF_8) == "tab 1 autosaved draft"
        }
        composeRule.onAllNodesWithTag("editor-tab-close-tab-1.txt").assertCountEquals(0)
        assertEquals("tab 1 autosaved draft", String(Files.readAllBytes(workspace.resolve("tab-1.txt")), Charsets.UTF_8))
        assertEquals(26, (1..26).count { Files.isRegularFile(workspace.resolve("tab-$it.txt")) })
    }

    @Test
    fun workspaceEditorAutoSavesDraftWithoutPressingSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val file = workspace.resolve("autosave-sample.txt")
        Files.write(file, "before".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-autosave-sample.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-autosave-sample.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input").assertTextContains("before", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement("saved by autosave")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                String(Files.readAllBytes(file), Charsets.UTF_8) == "saved by autosave"
            }.getOrDefault(false)
        }
        assertEquals("saved by autosave", String(Files.readAllBytes(file), Charsets.UTF_8))
    }

    @Test
    fun extensionlessNewFilesBecomeTxtAndInspectorShowsReadableFormat() {
        composeRule.onNodeWithTag("workspace-create-menu-button").performClick()
        composeRule.onNodeWithTag("new-file-button").performClick()
        composeRule.onNodeWithTag("new-file-name-input").performTextInput("my file (1)")
        composeRule.onNodeWithTag("confirm-create-file").performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedFile = context.filesDir.toPath().resolve("workspace/my file (1).txt")
        composeRule.waitUntil(timeoutMillis = 10_000) { Files.isRegularFile(savedFile) }
        val editor = composeRule.onNodeWithTag("file-editor-input")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { editor.assertIsDisplayed() }.isSuccess
        }

        val expectedText = "This is a plain text file."
        editor.performTextInput(expectedText)
        composeRule.onNodeWithTag("save-file-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                String(Files.readAllBytes(savedFile), Charsets.UTF_8) == expectedText
            }.getOrDefault(false)
        }
        assertEquals(expectedText, String(Files.readAllBytes(savedFile), Charsets.UTF_8))

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithText("my file (1).txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Inspect my file (1).txt").performClick()
        composeRule.onNodeWithText("txt file (text file)").assertIsDisplayed()
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
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed() }.isSuccess
        }
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
    fun contextMenuAndInspectorSupportRenameCopyMove() {
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
        composeRule.onNodeWithTag("workspace-context-rename").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-context-close").performClick()
        assertEquals(false, runCatching { composeRule.onNodeWithText("QUICK INSPECTOR").assertIsDisplayed() }.isSuccess)
        assertEquals(false, runCatching { composeRule.onNodeWithTag("workspace-entry-options-sample.txt").assertIsDisplayed() }.isSuccess)

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


    @Test
    fun editorCanCollapseAndExpandCodeWithoutChangingSavedText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val sample = workspace.resolve("FoldSample.kt")
        val source = "fun main() {\n    println(\"first\")\n    println(\"second\")\n}\n"
        Files.write(sample, source.toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("entry-FoldSample.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-FoldSample.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("fun main()", substring = true)
            }.isSuccess
        }

        val foldButton = composeRule.onNodeWithTag("editor-fold-toggle-1")
        foldButton.assertIsDisplayed()
        foldButton.performClick()
        foldButton.assertTextContains("▸", substring = true)
        assertEquals(source, String(Files.readAllBytes(sample), Charsets.UTF_8))

        foldButton.performClick()
        foldButton.assertTextContains("▾", substring = true)
        assertEquals(source, String(Files.readAllBytes(sample), Charsets.UTF_8))
    }

    @Test
    fun editorCanInsertEmojiAndSymbolsToggleWordWrapAndAutosave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val sample = workspace.resolve("UnicodeEditor.txt")
        Files.write(sample, "start".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("entry-UnicodeEditor.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-UnicodeEditor.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("start", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("file-editor-input").performTextReplacement("hello ")
        composeRule.onNodeWithTag("file-editor-input").performTextInput("😀")
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("hello 😀", substring = true)

        composeRule.onNodeWithTag("editor-word-wrap-toggle").assertTextContains("Word wrap: On")
        composeRule.onNodeWithTag("editor-word-wrap-toggle").performClick()
        composeRule.onNodeWithTag("editor-word-wrap-toggle").assertTextContains("Word wrap: Off")
        composeRule.onNodeWithTag("editor-word-wrap-toggle").performClick()
        composeRule.onNodeWithTag("editor-word-wrap-toggle").assertTextContains("Word wrap: On")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            String(Files.readAllBytes(sample), Charsets.UTF_8) == "hello 😀"
        }
        assertEquals("hello 😀", String(Files.readAllBytes(sample), Charsets.UTF_8))
    }

    @Test
    fun editorCanFindReplaceAndKeepsLineNumbersOutOfSavedText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace)
        val sample = workspace.resolve("SearchReplace.kt")
        Files.write(sample, "val target = 1\nval target = 2\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("entry-SearchReplace.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-SearchReplace.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input")
                    .assertTextContains("val target = 1", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("editor-find-toggle").performClick()
        composeRule.onNodeWithTag("editor-find-input").performTextReplacement("target")
        composeRule.onNodeWithTag("editor-replace-input").performTextReplacement("answer")
        composeRule.onNodeWithTag("editor-find-next").performClick()
        composeRule.onNodeWithTag("editor-replace-one").performClick()
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("val answer = 1", substring = true)
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("val target = 2", substring = true)

        composeRule.onNodeWithTag("editor-replace-input").performTextReplacement("item")
        composeRule.onNodeWithTag("editor-replace-all").performClick()
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("val item = 2", substring = true)
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("val answer = 1", substring = true)

        composeRule.onNodeWithTag("save-file-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            String(Files.readAllBytes(sample), Charsets.UTF_8) == "val answer = 1\nval item = 2\n"
        }
        assertEquals("val answer = 1\nval item = 2\n", String(Files.readAllBytes(sample), Charsets.UTF_8))
    }


    @Test
    fun plusMenuAndFolderLongPressCanCreateNestedFilesAndFolders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")

        composeRule.onNodeWithTag("workspace-create-menu-button").performClick()
        composeRule.onNodeWithTag("new-folder-button").performClick()
        composeRule.onNodeWithTag("new-folder-name-input").performTextInput("parent")
        composeRule.onNodeWithTag("confirm-create-folder").performClick()
        val parent = workspace.resolve("parent")
        composeRule.waitUntil(timeoutMillis = 5_000) { Files.isDirectory(parent) }
        composeRule.onNodeWithTag("entry-parent").assertIsDisplayed()

        composeRule.onNodeWithTag("entry-parent").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-context-new-folder").performClick()
        composeRule.onNodeWithTag("new-folder-name-input").performTextInput("child")
        composeRule.onNodeWithTag("confirm-create-folder").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { Files.isDirectory(parent.resolve("child")) }

        composeRule.onNodeWithTag("entry-parent").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-context-new-file").performClick()
        composeRule.onNodeWithTag("new-file-name-input").performTextInput("Inside.kt")
        composeRule.onNodeWithTag("confirm-create-file").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { Files.isRegularFile(parent.resolve("Inside.kt")) }
        assertTrue(Files.isDirectory(parent.resolve("child")))
        assertTrue(Files.isRegularFile(parent.resolve("Inside.kt")))
    }

    @Test
    fun changePathMovesToTappedFolderAndCreateNewFolderMovesAutomatically() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")

        composeRule.onNodeWithTag("workspace-create-menu-button").performClick()
        composeRule.onNodeWithTag("new-folder-button").performClick()
        composeRule.onNodeWithTag("new-folder-name-input").performTextInput("existing")
        composeRule.onNodeWithTag("confirm-create-folder").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { Files.isDirectory(workspace.resolve("existing")) }

        composeRule.onNodeWithTag("workspace-create-menu-button").performClick()
        composeRule.onNodeWithTag("new-file-button").performClick()
        composeRule.onNodeWithTag("new-file-name-input").performTextInput("Moved.txt")
        composeRule.onNodeWithTag("confirm-create-file").performClick()
        val original = workspace.resolve("Moved.txt")
        composeRule.waitUntil(timeoutMillis = 10_000) { Files.isRegularFile(original) }
        composeRule.onNodeWithTag("editor-back").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-Moved.txt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("entry-Moved.txt").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-context-rename").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-context-delete").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-context-change-path").performClick()
        composeRule.onNodeWithTag("change-path-destination-existing").performClick()
        val moved = workspace.resolve("existing/Moved.txt")
        composeRule.waitUntil(timeoutMillis = 10_000) { Files.isRegularFile(moved) && !Files.exists(original) }
        assertEquals(false, Files.exists(original))

        composeRule.onNodeWithTag("entry-existing").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-Moved.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-Moved.txt").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-context-change-path").performClick()
        composeRule.onNodeWithTag("change-path-new-folder").performClick()
        composeRule.onNodeWithTag("change-path-new-folder-name").performTextInput("created-destination")
        composeRule.onNodeWithTag("change-path-create-folder-and-move").performClick()
        val movedAgain = workspace.resolve("existing/created-destination/Moved.txt")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.isRegularFile(movedAgain) && !Files.exists(moved)
        }
        assertTrue(Files.isDirectory(workspace.resolve("existing/created-destination")))
    }


    @Test
    fun creationRejectsReservedNamesIgnoringCase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")

        composeRule.onNodeWithTag("workspace-create-menu-button").performClick()
        composeRule.onNodeWithTag("new-folder-button").performClick()
        composeRule.onNodeWithTag("new-folder-name-input").performTextInput(".ASTRACODE-trash")
        composeRule.onNodeWithTag("confirm-create-folder").performClick()

        assertEquals(false, Files.exists(workspace.resolve(".ASTRACODE-trash")))
        composeRule.onNodeWithTag("new-folder-name-input").assertIsDisplayed()
    }

    @Test
    fun renameSavesDirtyDraftBeforeChangingPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        val original = workspace.resolve("README.txt")
        val renamed = workspace.resolve("README-renamed.txt")
        val draft = "Draft must survive a rename"
        Files.write(original, "Original text".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-README.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("file-editor-input").assertTextContains("Original text", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement(draft)
        composeRule.onNodeWithTag("editor-back").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("entry-README.txt").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-context-rename").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("README-renamed.txt")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            Files.isRegularFile(renamed) &&
                String(Files.readAllBytes(renamed), Charsets.UTF_8) == draft &&
                !Files.exists(original)
        }
        // Give the autosave debounce a chance to expose any stale-path write.
        Thread.sleep(700)
        assertEquals(draft, String(Files.readAllBytes(renamed), Charsets.UTF_8))
        assertEquals(false, Files.exists(original))
    }

    @Test
    fun bulkDeletePreservesDirtyEditorContentsInTrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        val original = workspace.resolve("README.txt")
        val draft = "Unsaved content that must remain recoverable"
        Files.write(original, "Original text".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-README.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("file-editor-input").performTextReplacement(draft)
        composeRule.onNodeWithTag("editor-back").performClick()

        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        composeRule.onNodeWithTag("workspace-select-README.txt").performClick()
        composeRule.onNodeWithTag("workspace-bulk-delete").performClick()
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        val trash = workspace.resolve(".astracode-trash/items")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            !Files.exists(original) && Files.isDirectory(trash) &&
                Files.walk(trash).use { paths ->
                    paths.filter { it.fileName.toString() == "payload" && Files.isRegularFile(it) }
                        .anyMatch { String(Files.readAllBytes(it), Charsets.UTF_8) == draft }
                }
        }
        assertEquals(false, Files.exists(original))
    }


    @Test
    fun floatingEditorToolsCanMoveAndApplyBasicAndAstraCodeActions() {
        val workspaceRoot = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "workspace")
        val sample = File(workspaceRoot, "FloatingTools.kt")
        sample.writeText("val answer = 42")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("entry-FloatingTools.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("entry-FloatingTools.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("editor-tools-drag-handle").performTouchInput {
            down(center)
            moveBy(Offset(-120f, -120f))
            up()
        }
        composeRule.onNodeWithTag("editor-tools-open").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor-tools-group-basic").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-tool-duplicate_line").performClick()
        composeRule.onNodeWithTag("workspace-save-file").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            sample.isFile && sample.readText() == "val answer = 42\nval answer = 42"
        }
        assertEquals("val answer = 42\nval answer = 42", sample.readText())

        composeRule.onNodeWithTag("workspace-editor-find-toggle").performClick()
        composeRule.onNodeWithTag("editor-tools-open").performClick()
        composeRule.onNodeWithTag("editor-tools-group-astracode").performClick()
        composeRule.onNodeWithTag("editor-tool-context_capsule").performClick()
        composeRule.onNodeWithTag("editor-find-message").assertTextContains("Copied AstraCode Context Capsule to clipboard.")
    }

}
