package com.wordcontextai.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun generateArticle(@Body request: ApiRequest): Response<ApiResponse>
}

data class ApiRequest(
    val model: String = "deepseek-chat",
    val messages: List<ApiMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2000  // 大幅增加token限制，支持更长文章
)

data class ApiMessage(
    val role: String,
    val content: String
)

data class ApiResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ApiMessage
) 