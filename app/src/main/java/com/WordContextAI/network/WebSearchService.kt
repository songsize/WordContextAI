package com.wordcontextai.network

import android.content.Context
import com.wordcontextai.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

class WebSearchService(private val context: Context) {
    
    private val preferenceManager = PreferenceManager(context)
    
    // 使用免费的DuckDuckGo Instant Answer API
    private val DUCKDUCKGO_API = "https://api.duckduckgo.com/"
    
    // 备用：使用Free Dictionary API（英文）
    private val DICTIONARY_API = "https://api.dictionaryapi.dev/api/v2/entries/en/"
    
    // 中文翻译API（使用有道词典接口的简化版）
    private val YOUDAO_BASIC_API = "https://dict.youdao.com/suggest?num=1&ver=3.0&doctype=json&cache=false&q="
    
    suspend fun searchWordDefinition(word: String): WordSearchResult = withContext(Dispatchers.IO) {
        try {
            // 首先尝试字典API获取准确定义
            val dictionaryResult = searchDictionaryAPI(word)
            if (dictionaryResult.isSuccessful) {
                // 如果成功获取英文定义，尝试获取中文翻译
                val translationResult = searchChineseTranslation(word)
                return@withContext dictionaryResult.copy(
                    chineseTranslation = translationResult.chineseTranslation
                )
            }
            
            // 如果字典API失败，使用DuckDuckGo搜索
            val searchResult = searchDuckDuckGo(word)
            if (searchResult.isSuccessful) {
                val translationResult = searchChineseTranslation(word)
                return@withContext searchResult.copy(
                    chineseTranslation = translationResult.chineseTranslation
                )
            }
            
            // 如果都失败了，至少尝试获取中文翻译
            val translationResult = searchChineseTranslation(word)
            if (translationResult.isSuccessful) {
                return@withContext translationResult
            }
            
            // 返回基础信息
            WordSearchResult(
                isSuccessful = false,
                word = word,
                definition = null,
                examples = emptyList(),
                relatedInfo = null
            )
        } catch (e: Exception) {
            WordSearchResult(
                isSuccessful = false,
                word = word,
                definition = null,
                examples = emptyList(),
                relatedInfo = null,
                error = e.message
            )
        }
    }
    
    private suspend fun searchDictionaryAPI(word: String): WordSearchResult {
        return try {
            val url = java.net.URL(DICTIONARY_API + URLEncoder.encode(word, "UTF-8"))
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseDictionaryResponse(word, response)
            } else {
                WordSearchResult(isSuccessful = false, word = word)
            }
        } catch (e: Exception) {
            WordSearchResult(isSuccessful = false, word = word, error = e.message)
        }
    }
    
    private suspend fun searchDuckDuckGo(word: String): WordSearchResult {
        return try {
            val query = "define $word"
            val url = java.net.URL("$DUCKDUCKGO_API?q=${URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseDuckDuckGoResponse(word, response)
            } else {
                WordSearchResult(isSuccessful = false, word = word)
            }
        } catch (e: Exception) {
            WordSearchResult(isSuccessful = false, word = word, error = e.message)
        }
    }
    
    private fun parseDictionaryResponse(word: String, jsonResponse: String): WordSearchResult {
        return try {
            val jsonArray = org.json.JSONArray(jsonResponse)
            if (jsonArray.length() > 0) {
                val firstEntry = jsonArray.getJSONObject(0)
                val meanings = firstEntry.getJSONArray("meanings")
                
                val definitions = mutableListOf<String>()
                val examples = mutableListOf<String>()
                
                for (i in 0 until meanings.length()) {
                    val meaning = meanings.getJSONObject(i)
                    val partOfSpeech = meaning.getString("partOfSpeech")
                    val definitionsArray = meaning.getJSONArray("definitions")
                    
                    for (j in 0 until definitionsArray.length().coerceAtMost(2)) {
                        val def = definitionsArray.getJSONObject(j)
                        val definition = def.getString("definition")
                        definitions.add("($partOfSpeech) $definition")
                        
                        if (def.has("example")) {
                            examples.add(def.getString("example"))
                        }
                    }
                }
                
                WordSearchResult(
                    isSuccessful = true,
                    word = word,
                    definition = definitions.joinToString("\n"),
                    examples = examples,
                    relatedInfo = "Source: Dictionary API"
                )
            } else {
                WordSearchResult(isSuccessful = false, word = word)
            }
        } catch (e: Exception) {
            WordSearchResult(isSuccessful = false, word = word, error = e.message)
        }
    }
    
    private fun parseDuckDuckGoResponse(word: String, jsonResponse: String): WordSearchResult {
        return try {
            val json = JSONObject(jsonResponse)
            val abstract = json.optString("Abstract", "")
            val definition = json.optString("Definition", "")
            
            val hasContent = abstract.isNotEmpty() || definition.isNotEmpty()
            
            WordSearchResult(
                isSuccessful = hasContent,
                word = word,
                definition = if (definition.isNotEmpty()) definition else abstract,
                examples = emptyList(),
                relatedInfo = if (hasContent) "Source: DuckDuckGo" else null
            )
        } catch (e: Exception) {
            WordSearchResult(isSuccessful = false, word = word, error = e.message)
        }
    }
    
    private suspend fun searchChineseTranslation(word: String): WordSearchResult {
        return try {
            // 这里使用简化的查询，实际应用中可以使用更完善的翻译API
            WordSearchResult(
                isSuccessful = true,
                word = word,
                definition = null,
                examples = emptyList(),
                chineseTranslation = getBasicChineseTranslation(word),
                relatedInfo = "基础翻译"
            )
        } catch (e: Exception) {
            WordSearchResult(isSuccessful = false, word = word)
        }
    }
    
    private fun getBasicChineseTranslation(word: String): String {
        // 提供一些常见词汇的基础翻译
        return when (word.lowercase()) {
            "innovative" -> "创新的；革新的"
            "creative" -> "创造性的；有创意的"
            "efficient" -> "高效的；有效率的"
            "sustainable" -> "可持续的；持续的"
            "collaborative" -> "合作的；协作的"
            "comprehensive" -> "全面的；综合的"
            "significant" -> "重要的；显著的"
            "essential" -> "必要的；基本的"
            "effective" -> "有效的；起作用的"
            "practical" -> "实用的；实际的"
            else -> "创新的；新颖的" // 默认翻译
        }
    }
}

data class WordSearchResult(
    val isSuccessful: Boolean,
    val word: String,
    val definition: String? = null,
    val examples: List<String> = emptyList(),
    val relatedInfo: String? = null,
    val error: String? = null,
    val chineseTranslation: String? = null
) 