package com.mrredhood.astracode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithText("Find tools, configuration and help from one place.").assertIsDisplayed()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("help")
        composeRule.onNodeWithText("Help & Guide").assertIsDisplayed()
        composeRule.onNodeWithText("Help & Guide").performClick()
        composeRule.onNodeWithText("AstraCode Help & Guide").assertIsDisplayed()
        composeRule.onNodeWithText("Build verification").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun moreSearchAndSelectedEntrySurviveActivityRecreation() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("security")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Security & Notifications").assertIsDisplayed()

        composeRule.onNodeWithText("Security & Notifications").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("This entry is a navigation placeholder", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aiProviderSettingsExposeProviderModelAndSecureKeyControls() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("AI & Models")
        composeRule.onNodeWithText("AI & Models").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("AI provider settings").assertIsDisplayed()
        composeRule.onNodeWithText("Model ID").assertIsDisplayed()
        composeRule.onNodeWithText("API key (optional when already saved)").assertIsDisplayed()
        composeRule.onNodeWithText("Discover models").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save & test").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun chatShowsSetupActionUntilAProviderAndModelAreConfigured() {
        composeRule.onNodeWithText("Chat").performClick()
        composeRule.onNodeWithText("Cloud AI not configured").assertIsDisplayed()
        composeRule.onNodeWithText("Attach files").assertIsDisplayed()
        composeRule.onNodeWithText("25 MiB/file", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Never attach secrets.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Configure AI provider").performClick()
        composeRule.onNodeWithText("AI provider settings").assertIsDisplayed()
    }

    @Test
    fun workspaceOffersFolderPickerWhenNoFolderIsSaved() {
        composeRule.onNodeWithText("Code").performClick()
        composeRule.onNodeWithText("No workspace selected").assertIsDisplayed()
        composeRule.onNodeWithText("Choose project folder").assertIsDisplayed()
    }

    @Test
    fun searchShowsMatchingMoreEntries() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("security")
        composeRule.onNodeWithText("Security & Notifications").assertIsDisplayed()
    }
}
