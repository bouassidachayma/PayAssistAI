package com.payassistai.app.ui.theme

import android.content.Context
import android.content.SharedPreferences

object ThemeManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME = "dark_theme"

    fun isDarkTheme(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_THEME, true) // default = dark
    }

    fun setDarkTheme(context: Context, isDark: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_THEME, isDark).apply()
    }
}