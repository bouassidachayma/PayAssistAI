package com.payassistai.app.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val KEY_USER_ID = "user_id"
    private val KEY_ROLE = "role"

    fun saveSession(merchantId: Int, role: String) {
        prefs.edit().putInt(KEY_USER_ID, merchantId).putString(KEY_ROLE, role).apply()
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)
    fun getRole(): String = prefs.getString(KEY_ROLE, "") ?: ""

    fun clearSession() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_ROLE).apply()
    }
}