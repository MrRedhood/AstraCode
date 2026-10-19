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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.file.Files
import java.util.Comparator
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
}
