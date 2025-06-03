package com.wordcontextai.repository

import android.content.Context
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.Language
import com.wordcontextai.network.ApiClient
import com.wordcontextai.network.ApiMessage
import com.wordcontextai.network.ApiRequest
import com.wordcontextai.network.WebSearchService
import com.wordcontextai.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.io.IOException

class WordRepository(private val context: Context) {
    
    private val apiClient = ApiClient.getInstance(context)
    private val webSearchService = WebSearchService(context)
    
    suspend fun generateArticle(
        word: String, 
        style: ArticleStyle, 
        language: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查网络连接
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return@withContext Result.failure(Exception("网络连接不可用，请检查您的网络设置"))
            }
            
            // 先尝试搜索词语的真实定义和信息（不需要API密钥）
            val searchResult = try {
                webSearchService.searchWordDefinition(word)
            } catch (e: Exception) {
                // 搜索失败时使用空结果
                com.wordcontextai.network.WordSearchResult(false, word)
            }
            
            // 检查是否有API密钥
            if (!apiClient.hasApiKey()) {
                // 没有API密钥时，返回基于搜索结果的模拟数据
                return@withContext Result.success(generateEnhancedMockArticle(word, style, language, searchResult))
            }
            
            // 创建增强的prompt，包含搜索到的真实信息
            val prompt = createEnhancedPrompt(word, style, language, searchResult)
            
            val messages = listOf(
                ApiMessage("system", "你是一个专业的语言学习助手，能够基于真实信息生成高质量的教学文章。"),
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
    
    private fun createEnhancedPrompt(
        word: String, 
        style: ArticleStyle, 
        language: Language,
        searchResult: com.wordcontextai.network.WordSearchResult
    ): String {
        // 对于英语学习，始终用中文解释
        val isLearningEnglish = true // 可以后续从设置中读取
        
        val styleDescription = when (style) {
            ArticleStyle.ACADEMIC -> "学术性的"
            ArticleStyle.DAILY -> "日常生活的"
            ArticleStyle.LITERATURE -> "文学性的"
            ArticleStyle.BUSINESS -> "商务场景的"
        }
        
        // 如果搜索到了真实定义，加入到prompt中
        val referenceInfo = if (searchResult.isSuccessful) {
            val definitionPart = if (searchResult.definition != null) {
                "英文定义：${searchResult.definition}"
            } else ""
            
            val translationPart = if (searchResult.chineseTranslation != null) {
                "中文翻译：${searchResult.chineseTranslation}"
            } else ""
            
            val examplesPart = if (searchResult.examples.isNotEmpty()) {
                "例句参考：\n${searchResult.examples.joinToString("\n")}"
            } else ""
            
            """
            参考信息（基于真实词典数据）：
            $definitionPart
            $translationPart
            $examplesPart
            
            请基于以上真实信息，确保生成的内容准确无误。特别注意使用准确的中文释义。
            """.trimIndent()
        } else {
            "请确保生成的内容准确、真实，避免虚构信息。请为英语学习者提供准确的中文解释。"
        }
        
        return """
        作为英语学习助手，请用中文为中国学生详细解释英语单词"$word"的学习内容。
        
        $referenceInfo
        
        ## 1. 词语释义
        用中文详细解释"$word"，包括：
        - 词性（如：名词 noun, 动词 verb, 形容词 adjective等，用中英对照）
        - 中文含义（提供准确、易懂的中文解释，如有多个含义请分别列出）
        - 发音提示（标准音标，如 /ˈɪnəveɪtɪv/）
        - 词根词缀分析（帮助记忆，如：in-进入 + nov-新的 + -ative形容词后缀）
        - 记忆技巧（联想记忆法或其他有效方法）
        
        ## 2. 句子应用
        提供6-8个展示"$word"不同用法的英文例句：
        - 例句必须地道、实用，体现真实语境
        - 每个例句都要用**粗体**标记目标词汇
        - 每句后面提供准确的中文翻译（不是直译，要符合中文表达习惯）
        - 用中文括号说明该句的语境或用法特点
        - 例句难度循序渐进，从简单到复杂
        
        ## 3. 文章示例
        创作一篇200-300字的${styleDescription}英文短文：
        - 文章主题明确，逻辑清晰，语言地道
        - 自然地融入"$word"至少4-5次，用**粗体**标记
        - 文章要符合${style.displayName}的文体特征
        - 文章结尾提供【中文大意】，用流畅的中文概括文章内容
        
        重要提示：
        1. 所有解释说明使用中文，帮助中国学生理解
        2. 英文例句和文章必须地道准确，符合英语母语者的表达习惯
        3. 中文翻译要意译而非直译，符合中文表达习惯
        
        目标词汇：$word
        """.trimIndent()
    }
    
    private fun createPrompt(word: String, style: ArticleStyle, language: Language): String {
        // 保留原方法作为备用
        return createEnhancedPrompt(word, style, language, 
            com.wordcontextai.network.WordSearchResult(false, word))
    }
    
    private fun generateMockArticle(word: String, style: ArticleStyle, language: Language): String {
        // 始终返回中文解释的英语学习内容
        return """
            ## 1. 词语释义
            
            **词性**: 形容词 (adjective)
            
            **中文含义**: 
            创新的；革新的；有创意的
            
            **发音**: /ˈɪnəveɪtɪv/
            
            **主要含义**:
            1. 引入新想法、新方法或新事物的
            2. 具有创造性和原创性的
            3. 善于创新和改革的
            
            **记忆技巧**: 
            可以将 innovative 分解为 in(进入) + nov(新的) + ative(形容词后缀)
            联想：把"新的"东西"带进来"→ 创新的
            
            ## 2. 句子应用
            
            1. The company's **innovative** approach to customer service set them apart from competitors.
               （这家公司**创新的**客户服务方式使他们在竞争对手中脱颖而出。）[商业创新]
            
            2. She presented an **innovative** solution to the environmental problem.
               （她提出了一个**创新的**环境问题解决方案。）[解决问题]
            
            3. The school adopted **innovative** teaching methods to engage students.
               （学校采用了**创新的**教学方法来吸引学生。）[教育创新]
            
            4. This **innovative** technology could revolutionize the healthcare industry.
               （这项**创新**技术可能会彻底改变医疗行业。）[技术突破]
            
            5. Being **innovative** requires courage to challenge the status quo.
               （**创新**需要挑战现状的勇气。）[个人品质]
            
            6. The chef's **innovative** menu combines traditional and modern cuisine.
               （厨师的**创新**菜单结合了传统和现代美食。）[创意融合]
            
            ## 3. 文章示例
            
            ### ${style.displayName}风格
            
            ${generateEnglishArticleWithChineseExplanation(word, style)}
            
            ---
            *这是演示内容。配置API密钥后可获得更准确、更丰富的学习内容。*
        """.trimIndent()
    }
    
    private fun generateEnglishArticleWithChineseExplanation(word: String, style: ArticleStyle): String {
        return when (style) {
            ArticleStyle.ACADEMIC -> """
                In contemporary academic discourse, **$word** approaches have become increasingly vital for addressing complex challenges. Research demonstrates that **$word** thinking in educational settings leads to enhanced student engagement and improved learning outcomes. 
                
                Universities worldwide are embracing **$word** methodologies to prepare students for a rapidly changing world. The integration of **$word** practices in curriculum design has shown measurable benefits. As educators continue to explore **$word** strategies, the future of education looks increasingly dynamic and adaptive.
                
                【中文大意】
                在当代学术讨论中，创新方法对于解决复杂挑战变得越来越重要。研究表明，教育环境中的创新思维能够提高学生参与度和学习效果。全球大学正在采用创新方法来帮助学生应对快速变化的世界。创新实践在课程设计中的整合已显示出可衡量的好处。
            """.trimIndent()
            
            ArticleStyle.DAILY -> """
                Have you noticed how **$word** ideas are everywhere these days? From the coffee shop that lets you order with an app to the **$word** ways we stay connected with friends, creativity is reshaping our daily routines.
                
                Last week, I discovered an **$word** solution to my morning rush - a smart alarm that adjusts based on traffic conditions. These **$word** tools aren't just fancy gadgets; they're practical improvements that make life easier. It's amazing how **$word** thinking can transform even the simplest daily tasks.
                
                【中文大意】
                你有没有注意到现在到处都是创新的想法？从可以用手机应用点单的咖啡店，到我们与朋友保持联系的创新方式，创造力正在重塑我们的日常生活。这些创新工具不仅仅是花哨的小玩意，它们是让生活更轻松的实用改进。
            """.trimIndent()
            
            ArticleStyle.LITERATURE -> """
                In the garden of human imagination, **$word** ideas bloom like exotic flowers, each petal unfolding to reveal new possibilities. The artist's **$word** vision transforms blank canvases into windows to other worlds.
                
                There is a certain magic in **$word** expression - it whispers of futures yet unwritten and dreams yet undreamed. Those who embrace **$word** thinking find themselves on journeys of discovery, where each step reveals new horizons. In this dance of creativity, the **$word** spirit soars beyond conventional boundaries.
                
                【中文大意】
                在人类想象力的花园里，创新的想法如异国花朵般绽放。艺术家的创新视野将空白画布转变为通往其他世界的窗口。创新表达中有一种魔力，它诉说着未来的可能性。拥抱创新思维的人会踏上发现之旅，每一步都展现新的地平线。
            """.trimIndent()
            
            ArticleStyle.BUSINESS -> """
                In today's competitive marketplace, **$word** strategies are no longer optional - they're essential for survival. Companies that foster **$word** cultures report 40% higher employee satisfaction and increased market share.
                
                The most successful businesses understand that **$word** thinking drives growth. By implementing **$word** solutions, organizations can streamline operations and enhance customer experiences. Leaders who champion **$word** approaches position their companies for long-term success in an ever-evolving business landscape.
                
                【中文大意】
                在当今竞争激烈的市场中，创新策略不再是可选项，而是生存的必需品。培养创新文化的公司报告显示员工满意度提高40%，市场份额也有所增加。通过实施创新解决方案，组织可以简化运营并提升客户体验。
            """.trimIndent()
        }
    }
    
    private fun generateEnhancedMockArticle(
        word: String, 
        style: ArticleStyle, 
        language: Language,
        searchResult: com.wordcontextai.network.WordSearchResult
    ): String {
        // 如果有真实的搜索结果，基于它生成内容
        if (searchResult.isSuccessful && searchResult.definition != null) {
            return when (language) {
                Language.ENGLISH -> generateEnglishContentWithDefinition(word, style, searchResult)
                Language.CHINESE -> generateChineseContentWithDefinition(word, style, searchResult)
            }
        }
        
        // 否则使用原来的模拟数据
        return generateMockArticle(word, style, language)
    }
    
    private fun generateEnglishContentWithDefinition(
        word: String,
        style: ArticleStyle,
        searchResult: com.wordcontextai.network.WordSearchResult
    ): String {
        val definition = searchResult.definition ?: ""
        val examples = searchResult.examples
        val chineseTranslation = searchResult.chineseTranslation ?: getBasicChineseTranslation(word)
        
        return """
            ## 1. 词语释义
            
            **基于真实词典数据：**
            
            ${if (definition.isNotEmpty()) definition else "暂无英文释义"}
            
            **中文解释**：
            $chineseTranslation
            
            ${searchResult.relatedInfo ?: ""}
            
            ## 2. 句子应用
            
            ${if (examples.isNotEmpty()) {
                examples.mapIndexed { index, example ->
                    val highlighted = example.replace(word, "**$word**", ignoreCase = true)
                    "${index + 1}. $highlighted\n   （${getChineseTranslation(example)}）"
                }.joinToString("\n\n")
            } else {
                """
                1. The company's **$word** approach to customer service set them apart.
                   （这家公司${chineseTranslation.split("；")[0]}的客户服务方式使他们脱颖而出。）[商业语境]
                
                2. We need more **$word** solutions to tackle this challenge.
                   （我们需要更多${chineseTranslation.split("；")[0]}的解决方案来应对这个挑战。）[解决问题]
                
                3. Her **$word** ideas impressed everyone at the meeting.
                   （她${chineseTranslation.split("；")[0]}的想法给会议上的每个人留下了深刻印象。）[职场环境]
                
                4. The **$word** technology is changing our daily lives.
                   （这项${chineseTranslation.split("；")[0]}技术正在改变我们的日常生活。）[科技领域]
                
                5. Being **$word** requires courage and persistence.
                   （保持${chineseTranslation.split("；")[0]}需要勇气和坚持。）[个人品质]
                """.trimIndent()
            }}
            
            ## 3. 文章示例
            
            ### ${style.displayName}风格
            
            ${generateEnglishArticleWithChineseExplanation(word, style)}
            
            ---
            *内容基于真实词典数据增强。配置API密钥可获得AI个性化内容。*
        """.trimIndent()
    }
    
    private fun getBasicChineseTranslation(word: String): String {
        // 提供一些常见词汇的基础翻译
        return when (word.lowercase()) {
            "innovative" -> "创新的；革新的；有创意的"
            "creative" -> "创造性的；有创意的；独创的"
            "efficient" -> "高效的；有效率的；效率高的"
            "sustainable" -> "可持续的；持续的；可维持的"
            "collaborative" -> "合作的；协作的；共同完成的"
            "comprehensive" -> "全面的；综合的；广泛的"
            "significant" -> "重要的；显著的；有意义的"
            "essential" -> "必要的；基本的；本质的"
            "effective" -> "有效的；起作用的；生效的"
            "practical" -> "实用的；实际的；切实可行的"
            else -> "创新的；新颖的" // 默认翻译
        }
    }
    
    private fun generateChineseContentWithDefinition(
        word: String,
        style: ArticleStyle,
        searchResult: com.wordcontextai.network.WordSearchResult
    ): String {
        // 对于中文用户学习英语，应该用中文解释
        return generateEnglishContentWithDefinition(word, style, searchResult)
    }
    
    private fun getChineseTranslation(sentence: String): String {
        // 这里是简化的翻译，实际应用中可以调用翻译API
        return when {
            sentence.contains("customer service") -> "客户服务相关"
            sentence.contains("solution") -> "解决方案相关"
            sentence.contains("meeting") -> "会议场景"
            sentence.contains("technology") -> "技术相关"
            else -> "一般用法"
        }
    }
} 