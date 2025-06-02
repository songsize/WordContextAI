package com.wordcontextai.data

import java.util.Date

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Date = Date(),
    val targetWord: String? = null,
    val isLoading: Boolean = false
)

data class WordRequest(
    val word: String,
    val style: ArticleStyle = ArticleStyle.DAILY,
    val language: Language = Language.ENGLISH
)

enum class ArticleStyle(val displayName: String, val prompt: String) {
    ACADEMIC("学术", "academic style"),
    DAILY("日常", "daily conversation style"),
    LITERATURE("文学", "literary style"),
    BUSINESS("商务", "business style")
}

enum class Language(val displayName: String, val code: String) {
    ENGLISH("英文", "en"),
    CHINESE("中文", "zh")
} 