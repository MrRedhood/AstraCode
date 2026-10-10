package com.mrredhood.astracode

import java.nio.charset.StandardCharsets

internal enum class WorkspaceAutosaveDecision { Unchanged, Save, Conflict, TooLarge }

/** Pure conflict/size policy shared by workspace writes and unit tests. */
internal object WorkspaceAutosavePolicy {
    const val MAX_DRAFT_BYTES = 2 * 1024 * 1024

    fun evaluate(
        expectedBaseline: String,
        storedText: String,
        storedTruncated: Boolean,
        draft: String,
    ): WorkspaceAutosaveDecision {
        if (draft.toByteArray(StandardCharsets.UTF_8).size > MAX_DRAFT_BYTES) {
            return WorkspaceAutosaveDecision.TooLarge
        }
        if (storedTruncated) return WorkspaceAutosaveDecision.Conflict
        if (storedText == draft) return WorkspaceAutosaveDecision.Unchanged
        if (storedText != expectedBaseline) return WorkspaceAutosaveDecision.Conflict
        if (draft == expectedBaseline) return WorkspaceAutosaveDecision.Unchanged
        return WorkspaceAutosaveDecision.Save
    }
}
