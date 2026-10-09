package com.mrredhood.astracode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun selectingMoreShowsToolsAndSettingsPlaceholder() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Tools and settings").assertIsDisplayed()
    }
}
