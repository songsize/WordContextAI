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
            Language.ENGLISH -> "Please write in English"
            Language.CHINESE -> "请用中文写作"
        }
        
        val styleInstruction = when (style) {
            ArticleStyle.ACADEMIC -> "academic and scholarly style with formal language"
            ArticleStyle.DAILY -> "casual and conversational style suitable for daily reading"
            ArticleStyle.LITERATURE -> "literary and artistic style with rich descriptions"
            ArticleStyle.BUSINESS -> "professional business style with clear and concise language"
        }
        
        return """
        $languageInstruction. Write an engaging and natural article that incorporates the word "$word" multiple times throughout the text.
        
        Requirements:
        1. The article should be 300-500 words long
        2. Use "$word" naturally at least 5-8 times in different contexts
        3. The article should be coherent, interesting, and educational
        4. Write in ${styleInstruction}
        5. The content should help readers understand how to use "$word" in real contexts
        6. Make the article feel like a genuine piece of writing, not a vocabulary exercise
        7. Include a compelling title that may or may not contain the word "$word"
        
        Important: Do NOT write vocabulary definitions, pronunciation guides, or explicit language learning content. Instead, create a real article where "$word" appears naturally in context.
        
        Target word to incorporate: $word
        Writing style: ${style.displayName}
        """.trimIndent()
    }
    
    private fun generateMockArticle(word: String, style: ArticleStyle, language: Language): String {
        return when (language) {
            Language.ENGLISH -> """
                ## The Power of Innovation in Modern Technology
                
                In today's rapidly evolving world, **$word** has become more than just a buzzword—it's a fundamental driver of progress. Companies that embrace **$word** are consistently outperforming their competitors in ways that were unimaginable just a decade ago.
                
                Consider how **$word** has transformed the technology sector. Silicon Valley giants have built their empires on a foundation of continuous **$word**, creating products that reshape how we live, work, and communicate. This commitment to **$word** isn't limited to tech companies; traditional industries are also discovering that **$word** is essential for survival in the 21st century.
                
                The most successful leaders understand that fostering a culture of **$word** requires more than just encouraging creative thinking. It demands a willingness to challenge conventional wisdom, invest in experimentation, and accept that failure is often a stepping stone to breakthrough **$word**. Organizations that create environments where **$word** can flourish are the ones writing the future.
                
                As we look ahead, the importance of **$word** will only continue to grow. Those who master the art of sustainable **$word** will shape tomorrow's world, while those who resist change risk being left behind. The question isn't whether to embrace **$word**, but how quickly we can adapt to its transformative power.
                
                ---
                *This is a demo article. Configure your API key to generate personalized content for any word.*
            """.trimIndent()
            
            Language.CHINESE -> """
                ## 创新思维改变世界
                
                在这个快速变化的时代，**$word** 已经不仅仅是一个流行词汇——它已成为推动社会进步的核心力量。那些真正拥抱 **$word** 的企业，正在以前所未有的方式超越竞争对手。
                
                让我们看看 **$word** 是如何改变科技行业的。硅谷的科技巨头们正是建立在持续 **$word** 的基础上，创造出改变我们生活、工作和交流方式的产品。这种对 **$word** 的承诺不仅限于科技公司；传统行业也在发现，**$word** 是在21世纪生存的关键。
                
                最成功的领导者明白，培养 **$word** 文化需要的不仅仅是鼓励创造性思维。它需要挑战传统智慧的勇气，需要在实验上的投资，需要接受失败往往是突破性 **$word** 的垫脚石。那些能够创造让 **$word** 蓬勃发展环境的组织，正是在书写未来的组织。
                
                展望未来，**$word** 的重要性只会继续增长。那些掌握可持续 **$word** 艺术的人将塑造明天的世界，而那些抗拒改变的人则有被时代抛弃的风险。问题不是是否要拥抱 **$word**，而是我们能多快适应它的变革力量。
                
                ---
                *这是演示文章。配置API密钥后可为任意词汇生成个性化内容。*
            """.trimIndent()
        }
    }
} 