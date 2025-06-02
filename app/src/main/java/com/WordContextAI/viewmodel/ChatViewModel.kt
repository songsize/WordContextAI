package com.wordcontextai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.ChatMessage
import com.wordcontextai.data.Language
import com.wordcontextai.repository.WordRepository
import com.wordcontextai.utils.PreferenceManager
import com.wordcontextai.utils.LanguageUtils
import kotlinx.coroutines.launch
import java.util.Date

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WordRepository(application)
    private val preferenceManager = PreferenceManager(application)
    
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _currentStyle = MutableLiveData<ArticleStyle>(ArticleStyle.DAILY)
    val currentStyle: LiveData<ArticleStyle> = _currentStyle
    
    // 自动检测系统语言并设置为默认值
    private val _currentLanguage = MutableLiveData<Language>(LanguageUtils.getSystemLanguage(application))
    val currentLanguage: LiveData<Language> = _currentLanguage
    
    init {
        // 根据检测到的系统语言设置欢迎消息
        val systemLanguage = LanguageUtils.getSystemLanguage(getApplication())
        val welcomeMessage = if (preferenceManager.hasApiKey()) {
            if (systemLanguage == Language.CHINESE) {
                ChatMessage(
                    content = "欢迎使用WordContext AI！✨\n\n请输入您想要学习的单词，我将为您生成包含该词汇的定制化文章，帮助您在真实语境中理解和掌握这个词汇的用法。\n\n💡 提示：已自动检测到中文系统语言，默认使用中文生成。您可以通过右上角的设置调整文章风格和语言。",
                    isUser = false,
                    timestamp = Date()
                )
            } else {
                ChatMessage(
                    content = "Welcome to WordContext AI! ✨\n\nPlease enter the word you want to learn, and I will generate customized articles containing this vocabulary to help you understand and master its usage in real contexts.\n\n💡 Tip: English system language detected, defaulting to English generation. You can adjust article style and language through the settings in the top right corner.",
                    isUser = false,
                    timestamp = Date()
                )
            }
        } else {
            if (systemLanguage == Language.CHINESE) {
                ChatMessage(
                    content = "欢迎使用WordContext AI！🎯\n\n当前处于演示模式。要体验完整的AI功能，请先在设置中配置您的DeepSeek API密钥。\n\n📖 即使在演示模式下，您也可以输入单词来查看示例文章。\n\n⚙️ 点击右上角的设置按钮来配置API密钥。\n\n🌐 已自动检测到中文系统语言。",
                    isUser = false,
                    timestamp = Date()
                )
            } else {
                ChatMessage(
                    content = "Welcome to WordContext AI! 🎯\n\nCurrently in demo mode. To experience full AI functionality, please configure your DeepSeek API key in settings first.\n\n📖 Even in demo mode, you can enter words to view sample articles.\n\n⚙️ Click the settings button in the top right corner to configure API key.\n\n🌐 English system language detected automatically.",
                    isUser = false,
                    timestamp = Date()
                )
            }
        }
        _messages.value = listOf(welcomeMessage)
    }
    
    fun generateArticleForWord(word: String) {
        if (word.isBlank()) return
        
        val userMessage = ChatMessage(
            content = word,
            isUser = true,
            targetWord = word
        )
        
        val loadingMessage = ChatMessage(
            content = if (preferenceManager.hasApiKey()) 
                "正在为您生成包含「$word」的定制文章..." 
            else 
                "正在为您生成包含「$word」的示例文章...",
            isUser = false,
            isLoading = true
        )
        
        // 添加用户消息和加载消息
        val currentMessages = _messages.value.orEmpty().toMutableList()
        currentMessages.add(userMessage)
        currentMessages.add(loadingMessage)
        _messages.value = currentMessages
        
        _isLoading.value = true
        
        viewModelScope.launch {
            repository.generateArticle(
                word = word,
                style = _currentStyle.value ?: ArticleStyle.DAILY,
                language = _currentLanguage.value ?: Language.ENGLISH
            ).fold(
                onSuccess = { article ->
                    val responseMessage = ChatMessage(
                        content = article,
                        isUser = false,
                        targetWord = word
                    )
                    
                    // 移除加载消息并添加响应消息
                    val updatedMessages = _messages.value.orEmpty().toMutableList()
                    updatedMessages.removeAt(updatedMessages.size - 1) // 移除加载消息
                    updatedMessages.add(responseMessage)
                    _messages.value = updatedMessages
                    
                    _isLoading.value = false
                },
                onFailure = { error ->
                    val errorMessage = ChatMessage(
                        content = "抱歉，生成文章时出现错误：${error.message}\n\n💡 如果您还未设置API密钥，请点击右上角设置按钮进行配置。",
                        isUser = false
                    )
                    
                    // 移除加载消息并添加错误消息
                    val updatedMessages = _messages.value.orEmpty().toMutableList()
                    updatedMessages.removeAt(updatedMessages.size - 1) // 移除加载消息
                    updatedMessages.add(errorMessage)
                    _messages.value = updatedMessages
                    
                    _isLoading.value = false
                }
            )
        }
    }
    
    fun setArticleStyle(style: ArticleStyle) {
        _currentStyle.value = style
    }
    
    fun setLanguage(language: Language) {
        _currentLanguage.value = language
    }
    
    fun clearChat() {
        val welcomeMessage = ChatMessage(
            content = "聊天记录已清除。请输入新的单词开始学习！",
            isUser = false,
            timestamp = Date()
        )
        _messages.value = listOf(welcomeMessage)
    }
    
    fun saveApiKey(apiKey: String) {
        preferenceManager.apiKey = apiKey.trim()
        
        // 更新欢迎消息
        val updatedWelcomeMessage = ChatMessage(
            content = "API密钥已设置成功！✅\n\n现在您可以享受完整的AI生成功能。请输入您想要学习的单词开始体验吧！",
            isUser = false,
            timestamp = Date()
        )
        _messages.value = listOf(updatedWelcomeMessage)
    }
    
    fun hasApiKey(): Boolean = preferenceManager.hasApiKey()
    
    fun getApiKey(): String? = preferenceManager.apiKey
} 