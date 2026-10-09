package com.mrredhood.astracode

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class AstraCodeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun shellShowsBrandAndPrimaryDestinations() {
        composeRule.onNodeWithText("ASTRACODE").assertIsDisplayed()
        listOf("Chat", "Code", "Git", "Build", "More").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun moreContainsSearchableSettingsAndOpensHelpGuide() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Tools and settings").assertIsDisplayed()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("help")
        composeRule.onNodeWithText("Help & Guide").assertIsDisplayed()
        composeRule.onNodeWithText("Help & Guide").performClick()
        composeRule.onNodeWithText("AstraCode Help & Guide").assertIsDisplayed()
        composeRule.onNodeWithText("Build verification").assertIsDisplayed()
    }

    @Test
    fun searchFiltersUnmatchedMoreEntries() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("security")
        composeRule.onNodeWithText("Security & Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("AI & Models").assertDoesNotExist()
    }
}
