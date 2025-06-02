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
        // 不显示欢迎消息，保持界面简洁
        _messages.value = emptyList()
    }
    
    fun generateArticleForWord(word: String) {
        if (word.isBlank()) return
        
        // 清除之前的所有消息，保持界面简洁
        _messages.value = emptyList()
        
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
                    
                    _messages.value = listOf(responseMessage)
                    _isLoading.value = false
                },
                onFailure = { error ->
                    val errorMessage = ChatMessage(
                        content = "抱歉，生成文章时出现错误：${error.message}\n\n💡 如果您还未设置API密钥，请点击右上角设置按钮进行配置。",
                        isUser = false
                    )
                    
                    _messages.value = listOf(errorMessage)
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
        // 直接清空消息，不显示提示
        _messages.value = emptyList()
    }
    
    fun saveApiKey(apiKey: String) {
        preferenceManager.apiKey = apiKey.trim()
        // 保存后也不显示消息，保持界面简洁
        _messages.value = emptyList()
    }
    
    fun hasApiKey(): Boolean = preferenceManager.hasApiKey()
    
    fun getApiKey(): String? = preferenceManager.apiKey
} 