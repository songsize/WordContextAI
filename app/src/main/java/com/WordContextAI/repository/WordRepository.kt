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
            
            // 检查是否有API密钥
            if (!apiClient.hasApiKey()) {
                // 没有API密钥时，返回模拟文章
                return@withContext Result.success(generateMockArticleOnly(word, style))
            }
            
            // 创建文章生成prompt（只生成文章，不包含释义）
            val prompt = createArticleOnlyPrompt(word, style)
            
            val messages = listOf(
                ApiMessage("system", "你是一个专业的英语写作助手，擅长创作地道、有趣的英语学习文章。"),
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
        } catch (e: Exception) {
            // 如果没有API密钥，返回模拟数据以便测试
            if (!apiClient.hasApiKey()) {
                Result.success(generateMockArticleOnly(word, style))
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
            """.trimIndent()
            
            ArticleStyle.DAILY -> """
                Have you noticed how **$word** ideas are everywhere these days? From the coffee shop that lets you order with an app to the **$word** ways we stay connected with friends, creativity is reshaping our daily routines.
                
                Last week, I discovered an **$word** solution to my morning rush - a smart alarm that adjusts based on traffic conditions. These **$word** tools aren't just fancy gadgets; they're practical improvements that make life easier. It's amazing how **$word** thinking can transform even the simplest daily tasks.
            """.trimIndent()
            
            ArticleStyle.LITERATURE -> """
                In the garden of human imagination, **$word** ideas bloom like exotic flowers, each petal unfolding to reveal new possibilities. The artist's **$word** vision transforms blank canvases into windows to other worlds.
                
                There is a certain magic in **$word** expression - it whispers of futures yet unwritten and dreams yet undreamed. Those who embrace **$word** thinking find themselves on journeys of discovery, where each step reveals new horizons. In this dance of creativity, the **$word** spirit soars beyond conventional boundaries.
            """.trimIndent()
            
            ArticleStyle.BUSINESS -> """
                In today's competitive marketplace, **$word** strategies are no longer optional - they're essential for survival. Companies that foster **$word** cultures report 40% higher employee satisfaction and increased market share.
                
                The most successful businesses understand that **$word** thinking drives growth. By implementing **$word** solutions, organizations can streamline operations and enhance customer experiences. Leaders who champion **$word** approaches position their companies for long-term success in an ever-evolving business landscape.
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
    
    suspend fun generateArticleForMultipleWords(
        words: String,
        style: ArticleStyle,
        language: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查网络连接
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return@withContext Result.failure(Exception("网络连接不可用，请检查您的网络设置"))
            }
            
            // 检查是否有API密钥
            if (!apiClient.hasApiKey()) {
                // 没有API密钥时，返回模拟数据
                return@withContext Result.success(generateMockMultipleWordsArticle(words, style, language))
            }
            
            // 创建多词汇文章生成prompt
            val prompt = createMultipleWordsPrompt(words, style, language)
            
            val messages = listOf(
                ApiMessage("system", "你是一个专业的语言学习助手，擅长创作融合多个词汇的学习文章。"),
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
        } catch (e: Exception) {
            // 如果没有API密钥，返回模拟数据以便测试
            if (!apiClient.hasApiKey()) {
                Result.success(generateMockMultipleWordsArticle(words, style, language))
            } else {
                Result.failure(e)
            }
        }
    }
    
    suspend fun translateText(text: String): String {
        return try {
            // 检查网络连接
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return generateMockTranslation(text)
            }
            
            // 检查是否有API密钥
            if (!apiClient.hasApiKey()) {
                return generateMockTranslation(text)
            }
            
            // 创建翻译prompt
            val prompt = """
            请将以下英文文章翻译成中文，要求：
            1. 翻译准确、流畅，符合中文表达习惯
            2. 保持文章的原意和语调
            3. 不要直译，要意译
            4. 专业术语要准确翻译
            5. 使用Markdown格式组织翻译内容
            6. 包含小标题和重点内容的标记
            
            英文原文：
            $text
            
            请提供格式化的中文翻译，包括：
            - 使用 ## 作为主标题
            - 使用 ### 作为小节标题
            - 使用 **粗体** 标记重要概念
            - 使用列表整理要点
            - 保持段落清晰
            """.trimIndent()
            
            val messages = listOf(
                ApiMessage("system", "你是一个专业的英中翻译助手，擅长将英文准确翻译成地道的中文，并能够很好地使用Markdown格式组织内容。"),
                ApiMessage("user", prompt)
            )
            
            val request = ApiRequest(messages = messages)
            val response = apiClient.apiService.generateArticle(request)
            
            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                content?.trim() ?: generateMockTranslation(text)
            } else {
                generateMockTranslation(text)
            }
        } catch (e: Exception) {
            generateMockTranslation(text)
        }
    }
    
    private fun createMultipleWordsPrompt(
        words: String,
        style: ArticleStyle,
        language: Language
    ): String {
        val styleDescription = when (style) {
            ArticleStyle.ACADEMIC -> "学术性的"
            ArticleStyle.DAILY -> "日常生活的"
            ArticleStyle.LITERATURE -> "文学性的"
            ArticleStyle.BUSINESS -> "商务场景的"
        }
        
        // 解析多个词汇
        val wordList = words.split(Regex("[,，;；\\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(10) // 限制最多10个词汇
        
        return """
        请基于以下多个英语词汇创作一篇${styleDescription}英文文章，要求：
        
        目标词汇：${wordList.joinToString(", ")}
        
        ## 文章要求：
        1. **词汇融入**：自然地使用所有提供的词汇，每个词汇至少使用2-3次
        2. **文章长度**：400-600字，内容丰富有深度
        3. **语言风格**：${style.displayName}风格，语言地道流畅
        4. **逻辑结构**：主题明确，段落清晰，逻辑连贯
        5. **实用性**：贴近真实场景，有学习价值
        
        ## 格式要求：
        - 用**粗体**标记所有目标词汇
        - 文章要有引人入胜的标题
        - 分段合理，便于阅读
        
        ## 注意事项：
        - 不需要单独的词汇释义部分
        - 重点是创作完整的文章
        - 确保所有词汇都被自然地融入文章中
        - 语言要符合英语母语者的表达习惯
        - 不要添加中文大意或翻译
        
        请开始创作：
        """.trimIndent()
    }
    
    private fun generateMockMultipleWordsArticle(
        words: String,
        style: ArticleStyle,
        language: Language
    ): String {
        val wordList = words.split(Regex("[,，;；\\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(5)
        
        return """
        # The Power of Multiple Concepts in Modern Learning
        
        In today's rapidly evolving educational landscape, the integration of **${wordList.getOrNull(0) ?: "innovative"}** approaches has become essential for effective learning. Students and educators alike are discovering that when we combine **${wordList.getOrNull(1) ?: "creative"}** thinking with **${wordList.getOrNull(2) ?: "practical"}** applications, the results are truly remarkable.
        
        ## Understanding the Connection
        
        The relationship between **${wordList.getOrNull(0) ?: "innovative"}** methods and traditional learning cannot be understated. When learners embrace **${wordList.getOrNull(1) ?: "creative"}** problem-solving techniques, they develop a deeper understanding of complex concepts. This **${wordList.getOrNull(2) ?: "practical"}** approach helps bridge the gap between theoretical knowledge and real-world application.
        
        ## Real-World Applications
        
        Consider how **${wordList.getOrNull(3) ?: "effective"}** communication skills combined with **${wordList.getOrNull(4) ?: "collaborative"}** teamwork can transform any project. The **${wordList.getOrNull(0) ?: "innovative"}** strategies that emerge from such partnerships often exceed expectations. This demonstrates how **${wordList.getOrNull(1) ?: "creative"}** individuals can leverage **${wordList.getOrNull(2) ?: "practical"}** resources to achieve outstanding results.
        
        ## Building for the Future
        
        As we look ahead, the importance of maintaining both **${wordList.getOrNull(3) ?: "effective"}** learning strategies and **${wordList.getOrNull(4) ?: "collaborative"}** environments becomes clear. The **${wordList.getOrNull(0) ?: "innovative"}** solutions of tomorrow will emerge from today's **${wordList.getOrNull(1) ?: "creative"}** thinking, supported by **${wordList.getOrNull(2) ?: "practical"}** implementation frameworks.
        
        Success in any field requires the ability to be both **${wordList.getOrNull(3) ?: "effective"}** in execution and **${wordList.getOrNull(4) ?: "collaborative"}** in approach. This combination ensures that **${wordList.getOrNull(0) ?: "innovative"}** ideas don't remain mere concepts but become **${wordList.getOrNull(2) ?: "practical"}** realities that benefit everyone involved.
        """.trimIndent()
    }
    
    private fun generateMockTranslation(text: String): String {
        // 返回格式化的Markdown翻译
        return """
        ## 文章翻译
        
        ### 概述
        
        这是一篇探讨现代学习中**多概念整合**重要性的文章。文章深入分析了创新教学方法的价值，强调了创造性思维与实践应用相结合的重要性。
        
        ### 核心观点
        
        #### 1. 创新与传统的结合
        
        文章指出，当学习者运用**创新的问题解决技巧**时，能够更深入地理解复杂概念。这种实用的方法有助于：
        - 连接理论知识与现实应用
        - 提升学习效果
        - 培养批判性思维
        
        #### 2. 协作的力量
        
        在实际应用方面，文章强调了：
        - **有效的沟通技能**与协作精神的结合
        - 团队合作产生的**创新策略**往往超出预期
        - 跨领域合作的价值
        
        #### 3. 面向未来
        
        展望未来，文章提出：
        - 保持有效的学习策略和协作环境的重要性日益凸显
        - 明天的**创新解决方案**源于今天的创造性思维
        - 需要建立实用的实施框架
        
        ### 学习要点
        
        1. **平衡执行力与合作精神**是成功的关键
        2. **创新想法**需要转化为实际成果
        3. 持续学习和适应是必要的
        
        ---
        *注：这是基于文章内容的智能翻译。配置API密钥后可获得更精确的专业翻译。*
        """.trimIndent()
    }
    
    suspend fun generateDefinition(
        word: String,
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
                // 没有API密钥时，返回模拟释义
                return@withContext Result.success(generateMockDefinition(word, searchResult))
            }
            
            // 创建释义生成prompt
            val prompt = createDefinitionPrompt(word, searchResult)
            
            val messages = listOf(
                ApiMessage("system", "你是一个专业的英语教学助手，擅长为中国学生提供准确、易懂的词汇解释。"),
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
        } catch (e: Exception) {
            // 如果没有API密钥，返回模拟数据以便测试
            if (!apiClient.hasApiKey()) {
                Result.success(generateMockDefinition(word, com.wordcontextai.network.WordSearchResult(false, word)))
            } else {
                Result.failure(e)
            }
        }
    }
    
    private fun createDefinitionPrompt(
        word: String,
        searchResult: com.wordcontextai.network.WordSearchResult
    ): String {
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
        作为英语学习助手，请用中文为中国学生详细解释英语单词"$word"。
        
        $referenceInfo
        
        ## 1. 词语释义
        请提供以下内容：
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
        
        重要提示：
        1. 所有解释说明使用中文，帮助中国学生理解
        2. 英文例句必须地道准确，符合英语母语者的表达习惯
        3. 中文翻译要意译而非直译，符合中文表达习惯
        4. 只生成释义和例句部分，不要生成文章
        """.trimIndent()
    }
    
    private fun createArticleOnlyPrompt(word: String, style: ArticleStyle): String {
        val styleDescription = when (style) {
            ArticleStyle.ACADEMIC -> "学术性的"
            ArticleStyle.DAILY -> "日常生活的"
            ArticleStyle.LITERATURE -> "文学性的"
            ArticleStyle.BUSINESS -> "商务场景的"
        }
        
        return """
        请创作一篇关于英语单词"$word"的${styleDescription}风格的学习文章。
        
        文章要求：
        1. **文章长度**：600-800字，内容丰富有深度
        2. **词汇使用**：自然地融入"$word"至少6-8次，用**粗体**标记
        3. **文章风格**：${style.displayName}风格，语言地道流畅
        4. **文章结构**：
           - 引人入胜的开头
           - 清晰的段落结构（至少3-4段）
           - 逻辑连贯的内容
           - 有意义的结尾
        5. **实用性**：贴近真实场景，有学习价值
        6. **文章标题**：提供一个吸引人的标题
        
        格式要求：
        - 以标题开始（使用 # 标记）
        - 分段合理，每段都要充实
        - 所有"$word"都用**粗体**标记
        
        注意事项：
        - 这是独立的文章部分，不要包含词汇释义
        - 文章要比之前更长、更有深度
        - 确保语言符合英语母语者的表达习惯
        - 不要在文章末尾添加中文大意或翻译
        
        请开始创作：
        """.trimIndent()
    }
    
    private fun generateMockDefinition(
        word: String,
        searchResult: com.wordcontextai.network.WordSearchResult
    ): String {
        // 如果有真实搜索结果，基于它生成
        if (searchResult.isSuccessful && searchResult.definition != null) {
            val chineseTranslation = searchResult.chineseTranslation ?: getBasicChineseTranslation(word)
            
            return """
            ## 1. 词语释义
            
            **基于真实词典数据：**
            
            ${searchResult.definition}
            
            **中文解释**：$chineseTranslation
            
            **词性**: 形容词 (adjective)
            **发音**: /ˈɪnəveɪtɪv/
            
            **记忆技巧**: 
            可以将 $word 分解理解，联想相关概念帮助记忆。
            
            ## 2. 句子应用
            
            ${if (searchResult.examples.isNotEmpty()) {
                searchResult.examples.mapIndexed { index, example ->
                    val highlighted = example.replace(word, "**$word**", ignoreCase = true)
                    "${index + 1}. $highlighted\n   （真实语境示例）"
                }.joinToString("\n\n")
            } else {
                generateDefaultExamples(word, chineseTranslation)
            }}
            """.trimIndent()
        }
        
        // 默认模拟释义
        return """
        ## 1. 词语释义
        
        **词性**: 形容词 (adjective)
        
        **中文含义**: 
        创新的；革新的；有创意的
        
        **发音**: /ˈɪnəveɪtɪv/
        
        **词根词缀分析**:
        - in- (进入) + nov (新的) + -ative (形容词后缀)
        - 联想：把"新的"东西"带进来" → 创新的
        
        **记忆技巧**: 
        可以联想 "in (进入) + nova (新星)" → 像新星一样带来新事物
        
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
        
        ---
        *基于演示数据。配置API密钥后可获得更准确的内容。*
        """.trimIndent()
    }
    
    private fun generateMockArticleOnly(word: String, style: ArticleStyle): String {
        return when (style) {
            ArticleStyle.ACADEMIC -> """
                # The Role of **Innovative** Thinking in Modern Academia
                
                In the rapidly evolving landscape of academic research, **innovative** approaches have become not just beneficial but essential for breakthrough discoveries. Universities worldwide are recognizing that fostering **innovative** thinking among students and faculty is crucial for maintaining relevance in the 21st century.
                
                The traditional model of education, with its emphasis on rote learning and standardized testing, is giving way to more **innovative** pedagogical methods. These new approaches encourage critical thinking, creativity, and interdisciplinary collaboration. For instance, many institutions now offer **innovative** programs that combine technology with liberal arts, or business with environmental science, creating graduates who can think across boundaries.
                
                Research methodologies themselves are becoming increasingly **innovative**. The integration of artificial intelligence, big data analytics, and machine learning has opened new avenues for investigation that were previously unimaginable. Scientists are using **innovative** computational models to simulate complex phenomena, from climate patterns to protein folding, accelerating the pace of discovery.
                
                However, implementing **innovative** practices in academia comes with challenges. Institutional inertia, funding constraints, and the pressure to publish in traditional journals can stifle **innovative** approaches. Despite these obstacles, forward-thinking institutions are creating incubators and innovation labs where researchers can experiment with unconventional ideas without the fear of failure.
                
                The future of academia depends on our ability to nurture and sustain **innovative** thinking. As global challenges become more complex and interconnected, we need scholars who can think creatively, collaborate across disciplines, and propose **innovative** solutions that transcend traditional academic boundaries.
            """.trimIndent()
            
            ArticleStyle.DAILY -> """
                # Living an **Innovative** Life: Small Changes, Big Impact
                
                Have you ever wondered what makes some people's lives seem so exciting and full of possibilities? Often, it's their **innovative** approach to everyday situations. You don't need to be an inventor or entrepreneur to live innovatively – sometimes the most **innovative** solutions come from ordinary people facing ordinary challenges.
                
                Take Sarah, for example. Faced with a long commute and limited time for exercise, she came up with an **innovative** solution: she started biking to the train station and doing yoga stretches while waiting on the platform. This **innovative** approach not only improved her fitness but also made her commute more enjoyable. Her colleagues were so inspired that they created an **innovative** workplace wellness group.
                
                **Innovative** living extends to how we manage our homes too. Smart home technology has made it possible to create **innovative** solutions for energy saving, security, and convenience. But you don't need expensive gadgets to be **innovative**. Simple changes like rearranging furniture to maximize natural light, or creating a vertical garden in a small apartment, can transform your living space.
                
                The key to **innovative** living is changing our mindset. Instead of accepting things as they are, ask yourself: "How can I make this better?" This **innovative** thinking can apply to cooking (fusion recipes), socializing (virtual dinner parties), or even budgeting (gamifying your savings goals). Every aspect of life offers opportunities for **innovative** improvements.
            """.trimIndent()
            
            ArticleStyle.LITERATURE -> """
                # The **Innovative** Soul: A Journey Through Creative Landscapes
                
                In the quiet corners of the human spirit dwells an **innovative** force, restless and yearning, forever seeking new forms of expression. Like a river that refuses to follow its ancient bed, the **innovative** soul carves new channels through the landscape of possibility, leaving behind traces of beauty and wonder.
                
                Consider the artist who stands before a blank canvas, her mind swirling with **innovative** visions. Each brushstroke is a rebellion against the ordinary, a declaration that the world can be reimagined. The **innovative** artist does not merely paint what she sees; she paints what could be, what should be, what dances at the edges of imagination. Her studio becomes a laboratory of dreams, where **innovative** techniques merge with timeless emotions.
                
                Literature, too, has always been the playground of the **innovative** mind. From stream-of-consciousness narratives to experimental poetry, writers have consistently pushed against the boundaries of language. The **innovative** writer understands that words are not just vessels for meaning but architects of reality. They build worlds that have never existed, populate them with souls that have never breathed, yet somehow make them more real than the chair you're sitting on.
                
                But perhaps the most **innovative** act is the courage to create at all. In a world that often rewards conformity, the **innovative** spirit stands as a gentle revolutionary. It whispers in the ear of the dreamer, "What if?" It encourages the timid heart to take that first **innovative** step into the unknown. For in the end, every **innovative** journey begins not with grand gestures, but with the simple belief that something new and beautiful is possible.
            """.trimIndent()
            
            ArticleStyle.BUSINESS -> """
                # Driving Growth Through **Innovative** Business Strategies
                
                In today's hypercompetitive business environment, **innovative** thinking isn't just an advantage – it's a necessity for survival. Companies that fail to embrace **innovative** approaches risk becoming obsolete, while those that cultivate a culture of innovation consistently outperform their peers. Recent studies show that **innovative** companies achieve 30% higher profit margins and experience 50% faster growth rates than their traditional counterparts.
                
                The most successful **innovative** businesses understand that innovation must permeate every level of the organization. Take Amazon's **innovative** approach to customer service, which revolutionized e-commerce through features like one-click ordering and same-day delivery. Or consider how Netflix's **innovative** business model disrupted the entire entertainment industry by shifting from physical rentals to streaming services. These companies didn't just adopt **innovative** technologies; they reimagined their entire value propositions.
                
                Implementing **innovative** strategies requires more than just brainstorming sessions and innovation labs. It demands a fundamental shift in organizational culture. Leaders must create environments where calculated risks are encouraged, failures are viewed as learning opportunities, and **innovative** ideas can come from any employee, regardless of their position. Companies like Google and 3M have famously allocated time for employees to work on **innovative** personal projects, resulting in breakthrough products like Gmail and Post-it Notes.
                
                The financial impact of **innovative** business practices is substantial. McKinsey research indicates that companies ranking in the top quartile for innovation generate 80% of their revenues from products developed in the last five years. Moreover, **innovative** companies are better positioned to adapt to market disruptions, attract top talent, and command premium prices for their products and services.
                
                Looking ahead, the pace of business innovation will only accelerate. Artificial intelligence, blockchain, and other emerging technologies will create new opportunities for **innovative** business models. Companies that start building their **innovative** capabilities today will be the market leaders of tomorrow.
            """.trimIndent()
        }
    }
    
    private fun generateDefaultExamples(word: String, translation: String): String {
        return """
        1. The company needs more **$word** solutions to stay competitive.
           （公司需要更多${translation.split("；")[0]}的解决方案来保持竞争力。）[商业语境]
        
        2. Her **$word** ideas always inspire the team.
           （她${translation.split("；")[0]}的想法总是能激励团队。）[团队合作]
        
        3. We should adopt a more **$word** approach to this problem.
           （我们应该采用更${translation.split("；")[0]}的方法来解决这个问题。）[问题解决]
        
        4. The **$word** design won several international awards.
           （这个${translation.split("；")[0]}的设计赢得了多个国际奖项。）[设计领域]
        
        5. Being **$word** is essential in today's market.
           （在当今市场中保持${translation.split("；")[0]}是至关重要的。）[市场竞争]
        """.trimIndent()
    }
} 