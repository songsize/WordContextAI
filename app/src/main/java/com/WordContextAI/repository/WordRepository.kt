package com.wordcontextai.repository

import android.content.Context
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.Language
import com.wordcontextai.network.ApiClient
import com.wordcontextai.network.ApiMessage
import com.wordcontextai.network.ApiRequest
import com.wordcontextai.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException

class WordRepository(private val context: Context) {
    
    private val apiClient = ApiClient.getInstance(context)
    
    suspend fun generateArticle(
        word: String, 
        style: ArticleStyle, 
        language: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查是否有API密钥
            if (!apiClient.hasApiKey()) {
                return@withContext Result.failure(Exception("请先设置DeepSeek API密钥"))
            }
            
            // 检查网络连接
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return@withContext Result.failure(Exception("网络连接不可用，请检查您的网络设置"))
            }
            
            val prompt = createPrompt(word, style, language)
            val messages = listOf(
                ApiMessage("system", "你是一个专业的语言学习助手，能够生成高质量的教学文章。"),
                ApiMessage("user", prompt)
            )
            
            val request = ApiRequest(messages = messages)
            val response = apiClient.apiService.generateArticle(request)
            
            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (!content.isNullOrBlank()) {
                    Result.success(content.trim())
                } else {
                    Result.failure(Exception("AI服务返回了空内容，请重试"))
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "API密钥无效，请在设置中检查您的密钥"
                    403 -> "API访问被拒绝，请检查您的账户权限"
                    429 -> "请求过于频繁，请稍后再试"
                    500, 502, 503 -> "AI服务暂时不可用，请稍后重试"
                    else -> "API请求失败 (${response.code()}): ${response.message()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: UnknownHostException) {
            // DNS解析失败
            val errorMessage = if (e.message?.contains("api.deepseek.com") == true) {
                "无法连接到DeepSeek服务器，可能的解决方案：\n\n" +
                "🔧 Android模拟器用户：\n" +
                "• 重启模拟器\n" +
                "• 检查模拟器网络设置\n" +
                "• 尝试冷启动模拟器\n\n" +
                "🔧 真实设备用户：\n" +
                "• 检查网络连接\n" +
                "• 尝试切换WiFi/移动数据\n" +
                "• 检查是否有网络限制"
            } else {
                NetworkUtils.getNetworkErrorMessage(context, "api.deepseek.com")
            }
            Result.failure(Exception(errorMessage))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("连接超时，请检查网络连接并重试"))
        } catch (e: IOException) {
            val errorMessage = when {
                e.message?.contains("Connection refused") == true -> "服务器拒绝连接，请稍后重试"
                e.message?.contains("timeout") == true -> "网络连接超时，请检查网络状态"
                else -> "网络连接异常：${e.message}"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            // 如果没有API密钥，返回模拟数据以便测试
            if (!apiClient.hasApiKey()) {
                Result.success(generateMockArticle(word, style, language))
            } else {
                val errorMessage = when {
                    e.message?.contains("SSL") == true -> "SSL连接失败，请检查网络安全设置"
                    e.message?.contains("timeout") == true -> "连接超时，请重试"
                    else -> "请求失败：${e.message}"
                }
                Result.failure(Exception(errorMessage))
            }
        }
    }
    
    private fun createPrompt(word: String, style: ArticleStyle, language: Language): String {
        val languageInstruction = when (language) {
            Language.ENGLISH -> "Please respond in English"
            Language.CHINESE -> "请用中文回答"
        }
        
        return """
        $languageInstruction. You are a professional vocabulary learning assistant. Create a comprehensive vocabulary learning content for the word "$word" in ${style.prompt}.
        
        Please provide:
        
        1. **Definition**: Clear and accurate definition of "$word"
        2. **Pronunciation**: Phonetic transcription if writing in English
        3. **Part of Speech**: Noun, verb, adjective, etc.
        4. **Usage Examples**: 3-4 authentic example sentences showing "$word" in different contexts
        5. **Memory Tips**: Effective techniques to remember this word (etymology, word associations, etc.)
        6. **Synonyms & Antonyms**: Related words that help expand vocabulary
        7. **Common Collocations**: Frequent word combinations with "$word"
        8. **Context Application**: How to use "$word" effectively in ${style.displayName.lowercase()} situations
        
        Make the content educational, engaging, and practical for vocabulary learners. Focus on helping users truly understand and remember how to use "$word" correctly.
        
        Target word: $word
        Learning style: ${style.displayName}
        """.trimIndent()
    }
    
    private fun generateMockArticle(word: String, style: ArticleStyle, language: Language): String {
        return when (language) {
            Language.ENGLISH -> """
                **📚 Vocabulary Learning: "$word"**
                
                **Definition**: [A comprehensive explanation of what "$word" means]
                
                **Pronunciation**: /${word.lowercase()}/ 
                
                **Part of Speech**: [Noun/Verb/Adjective/etc.]
                
                **Usage Examples**:
                • The project requires innovative thinking to achieve success.
                • She presented an innovative solution to the problem.
                • Our company values innovative approaches in all departments.
                • His innovative ideas revolutionized the industry.
                
                **Memory Tips**: 
                💡 Think of "in + nova + tive" - bringing in something "nova" (new/star-like)
                💡 Associate with "invention" - both start with "in" and relate to creativity
                
                **Synonyms**: creative, original, groundbreaking, revolutionary
                **Antonyms**: traditional, conventional, outdated
                
                **Common Collocations**:
                • innovative approach/solution/technology
                • highly innovative, truly innovative
                • innovative thinking/design/methods
                
                **${style.displayName} Application**:
                In ${style.displayName.lowercase()} contexts, "$word" is frequently used to describe new methods, technologies, or approaches that bring positive change.
                
                📝 **Demo Mode**: Configure your DeepSeek API key in settings for AI-powered vocabulary learning content.
            """.trimIndent()
            
            Language.CHINESE -> """
                **📚 词汇学习："$word"**
                
                **定义**: [详细解释"$word"的含义]
                
                **发音**: /${word.lowercase()}/
                
                **词性**: [名词/动词/形容词等]
                
                **用法示例**:
                • 这个项目需要创新思维才能取得成功。
                • 她提出了一个创新的解决方案。
                • 我们公司重视各部门的创新方法。
                • 他的创新想法彻底改变了整个行业。
                
                **记忆技巧**:
                💡 将"innovative"分解为"in + nova + tive" - 引入"nova"(新星)般的事物
                💡 与"invention"(发明)联系 - 都以"in"开头，都与创造力相关
                
                **同义词**: creative, original, groundbreaking, revolutionary
                **反义词**: traditional, conventional, outdated
                
                **常用搭配**:
                • innovative approach/solution/technology (创新方法/解决方案/技术)
                • highly innovative, truly innovative (高度创新的，真正创新的)
                • innovative thinking/design/methods (创新思维/设计/方法)
                
                **${style.displayName}应用场景**:
                在${style.displayName}语境中，"$word"经常用于描述带来积极变化的新方法、技术或途径。
                
                📝 **演示模式**: 在设置中配置您的DeepSeek API密钥以获取AI驱动的词汇学习内容。
            """.trimIndent()
        }
    }
} 