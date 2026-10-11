package com.mrredhood.astracode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class AstraCodeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForStartupAnimation() {
        composeRule.waitUntil(timeoutMillis = 7_000) {
            composeRule.onAllNodesWithText(
                "Initializing your coding environment…",
                substring = false
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun shellShowsBrandAndPrimaryDestinations() {
        composeRule.onNodeWithText("Astra").assertIsDisplayed()
        composeRule.onNodeWithText("Code").assertIsDisplayed()
        listOf("Home", "Projects", "AI", "Terminal", "More").forEach { label ->
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
    fun aiProviderSettingsRequireOnlyAnApiKeyAndSelectModelAutomatically() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("AI & Models")
        composeRule.onAllNodesWithText("AI & Models", substring = false).onLast().performClick()
        composeRule.onNodeWithText("AI provider settings").assertIsDisplayed()
        composeRule.onNodeWithText("API key").assertIsDisplayed()
        composeRule.onNodeWithText("Save & connect").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("AstraCode uses the provider's standard HTTPS endpoint", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Model ID", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Custom HTTPS base URL", substring = true).assertCountEquals(0)
    }

    @Test
    fun chatShowsSetupActionUntilApiKeyConnectionIsComplete() {
        composeRule.onNodeWithText("AI").performClick()
        composeRule.onNodeWithText("Cloud AI not configured").assertIsDisplayed()
        composeRule.onNodeWithText("Attach files").assertIsDisplayed()
        composeRule.onNodeWithText("/10 attached", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("25 MiB/file", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("100 MiB/request", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Never attach secrets.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Configure AI provider").performClick()
        composeRule.onNodeWithText("AI provider settings").assertIsDisplayed()
    }

    @Test
    fun workspaceOffersFolderPickerWhenNoFolderIsSaved() {
        composeRule.onNodeWithText("Projects").performClick()
        composeRule.onNodeWithText("No workspace selected").assertIsDisplayed()
        composeRule.onNodeWithText("Choose project folder").assertIsDisplayed()
    }

    @Test
    fun searchShowsMatchingMoreEntries() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("security")
        composeRule.onNodeWithText("Security & Notifications").assertIsDisplayed()
    }

    @Test
    fun dashboardBuildShortcutClearlyReportsBuildRunnerUnavailable() {
        composeRule.onNodeWithText("Build & Run").performClick()
        composeRule.onNodeWithText("Build execution is not wired into this build yet.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start build").performScrollTo().performClick()
        composeRule.onNodeWithText("No build was started.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun terminalRejectsArbitraryShellCommandsWithoutLaunchingProcess() {
        composeRule.onNodeWithText("Terminal").performClick()
        composeRule.onNodeWithText("Scoped terminal · built-ins only").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a safe command").performTextInput("rm -rf /")
        composeRule.onNodeWithText("Run built-in").performScrollTo().performClick()
        composeRule.onNodeWithText("Blocked: this terminal currently supports only", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("No shell process was started.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeDashboardOpensCreateProjectAndExecutionStatus() {
        composeRule.onNodeWithText("New project", substring = true).performClick()
        composeRule.onNodeWithText("Create a new project").assertIsDisplayed()
        composeRule.onNodeWithText("Flutter").assertIsDisplayed()

        composeRule.onNodeWithText("Home", substring = false).performClick()
        composeRule.onNodeWithText("View execution").performScrollTo().performClick()
        composeRule.onNodeWithText("Execution lifecycle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("This page does not simulate progress or claim that a task has run.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun settingsThemeAndAccentPreferencesPersist() {
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Search tools and settings").performTextInput("Settings")
        composeRule.onAllNodesWithText("Settings", substring = false).onLast().performClick()
        composeRule.onNodeWithText("Light").performScrollTo().performClick()
        composeRule.onNodeWithText("Pink").performScrollTo().performClick()
        composeRule.onNodeWithText("14").performScrollTo().performClick()

        assertEquals("Light", AstraUiPreferences(composeRule.activity).themeMode())
        assertEquals("Pink", AstraUiPreferences(composeRule.activity).accent())
        assertEquals(14, AstraUiPreferences(composeRule.activity).codeFontSize())

        composeRule.activityRule.scenario.recreate()
        assertEquals("Light", AstraUiPreferences(composeRule.activity).themeMode())
        assertEquals("Pink", AstraUiPreferences(composeRule.activity).accent())
        assertEquals(14, AstraUiPreferences(composeRule.activity).codeFontSize())
    }
}
