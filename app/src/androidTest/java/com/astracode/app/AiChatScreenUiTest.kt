package com.astracode.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.astracode.connectors.AiConversationStore
import com.astracode.connectors.WorkspaceAiAssistant
import com.astracode.ui.theme.AstraCodeTheme
import org.junit.Rule
import org.junit.Test

class AiChatScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatScreenShowsComposerModelPickerAndConversationDrawer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("ai_chat_ui_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val assistant = WorkspaceAiAssistant(context)
        val store = AiConversationStore(context)

        try {
            composeRule.setContent {
                AstraCodeTheme {
                    AiChatScreen(
                        activeWorkspaceId = "ui-test-workspace",
                        assistant = assistant,
                        conversationStore = store,
                        preferences = preferences,
                        onOpenWorkspacePlanner = {},
                    )
                }
            }

            composeRule.onNodeWithText("Chat Stream").assertIsDisplayed()
            composeRule.onNodeWithText("Ask anything, @ files/tools, / commands…").assertExists()

            composeRule.onNodeWithText("Models").performClick()
            composeRule.onNodeWithText("AI model").assertIsDisplayed()
            composeRule.onNodeWithTag("ai-provider-openai").assertIsDisplayed()
            composeRule.onNodeWithTag("ai-manual-model-toggle").performClick()
            composeRule.onNodeWithText("Model ID").assertExists()

            composeRule.onNodeWithContentDescription("Close model selector").assertExists()
            composeRule.onNodeWithContentDescription("Close model selector").performClick()

            composeRule.onNodeWithText("History").performClick()
            composeRule.onNodeWithText("Conversations").assertIsDisplayed()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithText("No conversations in this workspace yet.")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("No conversations in this workspace yet.").assertIsDisplayed()
        } finally {
            store.close()
        }
    }
}
