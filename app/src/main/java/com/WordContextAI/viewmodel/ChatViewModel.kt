package com.wordcontextai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.wordcontextai.data.AppDatabase
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.ChatMessage
import com.wordcontextai.data.Language
import com.wordcontextai.data.SearchHistory
import com.wordcontextai.repository.WordRepository
import com.wordcontextai.utils.PreferenceManager
import com.wordcontextai.utils.LanguageUtils
import com.wordcontextai.utils.UserPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Date

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WordRepository(application)
    private val preferenceManager = PreferenceManager(application)
    private val userPreferences = UserPreferences(application)
    private val database = AppDatabase.getDatabase(application)
    private val searchHistoryDao = database.searchHistoryDao()
    
    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _currentStyle = MutableLiveData<ArticleStyle>(ArticleStyle.DAILY)
    val currentStyle: LiveData<ArticleStyle> = _currentStyle
    
    // 搜索历史 - 根据用户ID获取
    val searchHistory: LiveData<List<SearchHistory>> = userPreferences.getUserId()?.let { userId ->
        searchHistoryDao.getRecentByUser(userId).asLiveData()
    } ?: flowOf(emptyList<SearchHistory>()).asLiveData()

    init {
        // 不显示欢迎消息，保持界面简洁
        _messages.value = emptyList()
    }
    
    fun generateArticleForWord(word: String) {
        if (word.isBlank()) return
        
        // 清除之前的所有消息，保持界面简洁
        _messages.value = emptyList()
        
        _isLoading.value = true
        
        // 添加到搜索历史（如果用户已登录）
        viewModelScope.launch {
            userPreferences.getUserId()?.let { userId ->
                searchHistoryDao.insertSearch(SearchHistory(
                    word = word,
                    userId = userId
                ))
            }
        }
        
        viewModelScope.launch {
            try {
                // 使用coroutineScope并行调用两个API
                coroutineScope {
                    // 并行生成释义和文章
                    val definitionDeferred = async {
                        repository.generateDefinition(
                            word = word,
                            language = Language.ENGLISH
                        )
                    }
                    
                    val articleDeferred = async {
                        repository.generateArticle(
                            word = word,
                            style = _currentStyle.value ?: ArticleStyle.DAILY,
                            language = Language.ENGLISH
                        )
                    }
                    
                    // 等待两个结果
                    val definitionResult = definitionDeferred.await()
                    val articleResult = articleDeferred.await()
                    
                    // 处理结果
                    if (definitionResult.isSuccess && articleResult.isSuccess) {
                        val definition = definitionResult.getOrThrow()
                        val article = articleResult.getOrThrow()
                        
                        // 组合内容，使用特殊标记分隔
                        val combinedContent = """
                        $definition
                        
                        <!-- ARTICLE_SEPARATOR -->
                        
                        $article
                        """.trimIndent()
                        
                        val responseMessage = ChatMessage(
                            content = combinedContent,
                            isUser = false,
                            targetWord = word
                        )
                        
                        _messages.value = listOf(responseMessage)
                    } else {
                        // 处理错误
                        val errorMsg = when {
                            definitionResult.isFailure -> definitionResult.exceptionOrNull()?.message
                            articleResult.isFailure -> articleResult.exceptionOrNull()?.message
                            else -> "未知错误"
                        }
                        
                        val errorMessage = ChatMessage(
                            content = "抱歉，生成内容时出现错误：$errorMsg\n\n💡 如果您还未设置API密钥，请点击右上角设置按钮进行配置。",
                            isUser = false
                        )
                        
                        _messages.value = listOf(errorMessage)
                    }
                }
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    content = "抱歉，生成内容时出现错误：${e.message}\n\n💡 如果您还未设置API密钥，请点击右上角设置按钮进行配置。",
                    isUser = false
                )
                
                _messages.value = listOf(errorMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun generateArticleForMultipleWords(words: String) {
        if (words.isBlank()) return
        
        // 清除之前的所有消息
        _messages.value = emptyList()
        
        _isLoading.value = true
        
        // 为多词汇输入添加搜索历史
        viewModelScope.launch {
            userPreferences.getUserId()?.let { userId ->
                searchHistoryDao.insertSearch(SearchHistory(
                    word = "多词汇: ${words.take(20)}...",
                    userId = userId
                ))
            }
        }
        
        viewModelScope.launch {
            repository.generateArticleForMultipleWords(
                words = words,
                style = _currentStyle.value ?: ArticleStyle.DAILY,
                language = Language.ENGLISH
            ).fold(
                onSuccess = { article ->
                    val responseMessage = ChatMessage(
                        content = article,
                        isUser = false,
                        targetWord = words
                    )
                    
                    _messages.value = listOf(responseMessage)
                    _isLoading.value = false
                },
                onFailure = { error ->
                    val errorMessage = ChatMessage(
                        content = "抱歉，生成多词汇文章时出现错误：${error.message}\n\n💡 如果您还未设置API密钥，请点击右上角设置按钮进行配置。",
                        isUser = false
                    )
                    
                    _messages.value = listOf(errorMessage)
                    _isLoading.value = false
                }
            )
        }
    }
    
    suspend fun translateText(text: String): String {
        return repository.translateText(text)
    }
    
    fun deleteSearchHistory(searchHistory: SearchHistory) {
        viewModelScope.launch {
            searchHistoryDao.deleteSearch(searchHistory)
        }
    }
    
    fun clearAllSearchHistory() {
        viewModelScope.launch {
            userPreferences.getUserId()?.let { userId ->
                searchHistoryDao.deleteAllByUser(userId)
            }
        }
    }
    
    fun setArticleStyle(style: ArticleStyle) {
        _currentStyle.value = style
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

