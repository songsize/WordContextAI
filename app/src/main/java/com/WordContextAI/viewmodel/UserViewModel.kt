package com.wordcontextai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.wordcontextai.data.AppDatabase
import com.wordcontextai.data.User
import com.wordcontextai.repository.UserRepository
import com.wordcontextai.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {
    
    private val userDao = AppDatabase.getDatabase(application).userDao()
    private val userRepository = UserRepository(userDao)
    private val userPreferences = UserPreferences(application)
    
    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private var currentUserId: Long = -1
    
    init {
        // 检查是否有已登录用户
        checkLoggedInUser()
    }
    
    private fun checkLoggedInUser() {
        viewModelScope.launch {
            userPreferences.getUserId()?.let { userId ->
                userRepository.getUserById(userId)?.let { user ->
                    currentUserId = user.id
                    _currentUser.value = user
                    _loginState.value = LoginState.Success(user)
                }
            }
        }
    }
    
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            userRepository.login(username, password)
                .onSuccess { user ->
                    userPreferences.saveUserSession(user.id, user.username)
                    currentUserId = user.id
                    _currentUser.value = user
                    _loginState.value = LoginState.Success(user)
                }
                .onFailure { exception ->
                    _loginState.value = LoginState.Error(exception.message ?: "登录失败")
                }
            
            _isLoading.value = false
        }
    }
    
    fun register(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            userRepository.register(username, password)
                .onSuccess { user ->
                    userPreferences.saveUserSession(user.id, user.username)
                    currentUserId = user.id
                    _currentUser.value = user
                    _loginState.value = LoginState.Success(user)
                }
                .onFailure { exception ->
                    _loginState.value = LoginState.Error(exception.message ?: "注册失败")
                }
            
            _isLoading.value = false
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            currentUserId = -1
            userPreferences.clearUserSession()
            _currentUser.value = null
            _loginState.value = LoginState.LoggedOut
        }
    }
    
    fun updateAvatar(avatarPath: String) {
        viewModelScope.launch {
            try {
                if (currentUserId > 0) {
                    userRepository.updateAvatar(currentUserId, avatarPath)
                    // 更新当前用户对象
                    _currentUser.value?.let { user ->
                        _currentUser.value = user.copy(avatarPath = avatarPath)
                    }
                }
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }
    
    sealed class LoginState {
        data class Success(val user: User) : LoginState()
        data class Error(val message: String) : LoginState()
        object LoggedOut : LoginState()
    }
} 