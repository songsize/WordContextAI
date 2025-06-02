package com.wordcontextai.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_NAME = "wordcontextai_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }
    
    var apiKey: String?
        get() = sharedPreferences.getString(KEY_API_KEY, null)
        set(value) = sharedPreferences.edit().putString(KEY_API_KEY, value).apply()
    
    var isFirstLaunch: Boolean
        get() = sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
    
    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()
} 