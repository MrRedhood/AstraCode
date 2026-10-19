package com.astracode.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        composeRule.onNodeWithText("New file").performClick()
        composeRule.onNodeWithTag("new-file-name-input").performTextInput("Hello.kt")
        composeRule.onNodeWithText("Create file").performClick()

        val editor = composeRule.onNodeWithTag("file-editor-input")
        editor.assertIsDisplayed()
        editor.performTextInput("fun main() = println(\"AstraCode\")")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Saved Hello.kt").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Hello.kt").assertIsDisplayed()
        composeRule.onNodeWithText("Hello.kt").performClick()
        composeRule.onNodeWithTag("file-editor-input").assertIsDisplayed()
        composeRule.onNodeWithTag("file-editor-input").assertTextContains("fun main() = println(\"AstraCode\")")
    }

    @Test
    fun emptyWorkspaceExplainsHowToCreateAFile() {
        composeRule.onNodeWithText("A clean workspace.").assertIsDisplayed()
        composeRule.onNodeWithText("Create your first file").assertIsDisplayed()
    }
}
