package com.wordcontextai.repository

import com.wordcontextai.data.User
import com.wordcontextai.data.UserDao
import com.wordcontextai.utils.PasswordUtils
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {
    
    suspend fun register(username: String, password: String): Result<User> {
        return try {
            // 检查用户名是否已存在
            val existingUser = userDao.getUserByUsername(username)
            if (existingUser != null) {
                return Result.failure(Exception("用户名已存在"))
            }
            
            // 创建新用户
            val passwordHash = PasswordUtils.hashPassword(password)
            val user = User(
                username = username,
                passwordHash = passwordHash
            )
            
            val userId = userDao.insert(user)
            val newUser = user.copy(id = userId)
            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(username: String, password: String): Result<User> {
        return try {
            val user = userDao.getUserByUsername(username)
                ?: return Result.failure(Exception("用户不存在"))
            
            if (!PasswordUtils.verifyPassword(password, user.passwordHash)) {
                return Result.failure(Exception("密码错误"))
            }
            
            // 更新最后登录时间
            userDao.updateLastLogin(user.id)
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateAvatar(userId: Long, avatarPath: String?) {
        userDao.updateAvatar(userId, avatarPath)
    }
    
    suspend fun getUserById(userId: Long): User? {
        return userDao.getUserById(userId)
    }
    
    fun getUserByIdFlow(userId: Long): Flow<User?> {
        return userDao.getUserByIdFlow(userId)
    }
} 