package com.wordcontextai.utils

import android.content.Context
import android.os.Build
import androidx.core.os.ConfigurationCompat
import com.wordcontextai.data.Language
import java.util.Locale

object LanguageUtils {
    
    /**
     * 根据系统语言设置检测并返回相应的Language枚举
     */
    fun getSystemLanguage(context: Context): Language {
        val systemLocale = getSystemLocale(context)
        val languageCode = systemLocale.language.lowercase()
        
        return when {
            // 中文系列（简体、繁体、香港、台湾等）
            languageCode.startsWith("zh") -> Language.CHINESE
            languageCode == "cn" -> Language.CHINESE
            // 默认使用英文
            else -> Language.ENGLISH
        }
    }
    
    /**
     * 获取系统当前的语言设置
     */
    private fun getSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConfigurationCompat.getLocales(context.resources.configuration)[0]
                ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale ?: Locale.getDefault()
        }
    }
    
    /**
     * 获取语言的显示名称，用于调试
     */
    fun getLanguageDisplayName(context: Context): String {
        val locale = getSystemLocale(context)
        return "${locale.language}-${locale.country} (${locale.displayName})"
    }
    
    /**
     * 检查系统是否使用中文
     */
    fun isSystemLanguageChinese(context: Context): Boolean {
        return getSystemLanguage(context) == Language.CHINESE
    }
} 