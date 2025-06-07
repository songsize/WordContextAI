package com.wordcontextai.network

import android.content.Context
import com.wordcontextai.utils.PreferenceManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient private constructor(context: Context) {
    
    private val preferenceManager = PreferenceManager(context)
    
    companion object {
        private const val BASE_URL = "https://api.deepseek.com/"
        
        @Volatile
        private var INSTANCE: ApiClient? = null
        
        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val authInterceptor = Interceptor { chain ->
        val apiKey = preferenceManager.apiKey
        val request = if (!apiKey.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
      private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
    
    fun hasApiKey(): Boolean = preferenceManager.hasApiKey()
} 