package com.wordcontextai.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkUtils {
    
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    fun canReachHost(hostname: String): Boolean {
        return try {
            InetAddress.getByName(hostname)
            true
        } catch (e: UnknownHostException) {
            false
        }
    }
    
    fun getNetworkErrorMessage(context: Context, hostname: String): String {
        return when {
            !isNetworkAvailable(context) -> "网络连接不可用，请检查您的网络设置"
            !canReachHost(hostname) -> "无法访问AI服务器，可能是网络环境限制。如果使用模拟器，请尝试重启模拟器或检查代理设置"
            else -> "网络连接正常，但请求失败。请稍后重试"
        }
    }
} 