package com.wordcontextai.utils

import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

object PasswordUtils {
    
    private const val SALT_LENGTH = 16
    private const val ITERATIONS = 10000
    
    /**
     * 生成密码哈希
     */
    fun hashPassword(password: String): String {
        // 生成随机盐
        val salt = generateSalt()
        
        // 使用SHA-256哈希密码
        val hash = hashWithSalt(password, salt)
        
        // 将盐和哈希值组合存储
        return "${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }
    
    /**
     * 验证密码
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        try {
            val parts = storedHash.split(":")
            if (parts.size != 2) return false
            
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val hash = Base64.decode(parts[1], Base64.NO_WRAP)
            
            val computedHash = hashWithSalt(password, salt)
            return hash.contentEquals(computedHash)
        } catch (e: Exception) {
            return false
        }
    }
    
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }
    
    private fun hashWithSalt(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        
        // 多次迭代增加安全性
        var hash = digest.digest(password.toByteArray())
        for (i in 0 until ITERATIONS) {
            digest.reset()
            hash = digest.digest(hash)
        }
        
        return hash
    }
} 