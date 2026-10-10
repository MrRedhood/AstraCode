package com.mrredhood.astracode

import android.content.Context

/** Persists only non-sensitive presentation preferences. */
internal class AstraUiPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "astracode_ui_preferences",
        Context.MODE_PRIVATE
    )

    fun themeMode(): String = preferences.getString(KEY_THEME, null)
        ?.takeIf { it in THEME_MODES } ?: DEFAULT_THEME

    fun accent(): String = preferences.getString(KEY_ACCENT, null)
        ?.takeIf { it in ACCENTS } ?: DEFAULT_ACCENT

    fun saveThemeMode(value: String) {
        if (value in THEME_MODES) preferences.edit().putString(KEY_THEME, value).apply()
    }

    fun saveAccent(value: String) {
        if (value in ACCENTS) preferences.edit().putString(KEY_ACCENT, value).apply()
    }

    fun codeFontSize(): Int = preferences.getInt(KEY_CODE_FONT_SIZE, DEFAULT_CODE_FONT_SIZE)
        .coerceIn(MIN_CODE_FONT_SIZE, MAX_CODE_FONT_SIZE)

    fun saveCodeFontSize(value: Int) {
        if (value in MIN_CODE_FONT_SIZE..MAX_CODE_FONT_SIZE) {
            preferences.edit().putInt(KEY_CODE_FONT_SIZE, value).apply()
        }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_CODE_FONT_SIZE = "code_font_size"
        const val DEFAULT_THEME = "Dark"
        const val DEFAULT_ACCENT = "Blue"
        const val DEFAULT_CODE_FONT_SIZE = 13
        const val MIN_CODE_FONT_SIZE = 11
        const val MAX_CODE_FONT_SIZE = 22
        val THEME_MODES = setOf("Dark", "Light", "System")
        val ACCENTS = setOf("Cyan", "Blue", "Purple", "Pink", "Gold", "Green")
    }
}
