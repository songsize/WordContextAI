package com.wordcontextai.utils

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "user_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    fun saveUserSession(userId: Long, username: String) {
        prefs.edit().apply {
            putLong(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
    }
    
    fun clearUserSession() {
        prefs.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
    }
    
    fun getUserId(): Long? {
        val userId = prefs.getLong(KEY_USER_ID, -1)
        return if (userId == -1L) null else userId
    }
    
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
} 