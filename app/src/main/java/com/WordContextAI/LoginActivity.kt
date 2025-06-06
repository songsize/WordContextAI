package com.wordcontextai

import android.content.Intent
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordcontextai.databinding.ActivityLoginBinding
import com.wordcontextai.databinding.DialogRegisterBinding
import com.wordcontextai.viewmodel.UserViewModel

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val userViewModel: UserViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        observeViewModel()
    }
    
    private fun setupViews() {
        // 登录按钮
        binding.buttonLogin.setOnClickListener {
            val username = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()
            
            if (validateInput(username, password)) {
                userViewModel.login(username, password)
            }
        }
        
        // 注册链接
        binding.textViewRegister.setOnClickListener {
            showRegisterDialog()
        }
        
        // 跳过登录
        binding.textViewSkip.setOnClickListener {
            startMainActivity()
        }
    }
    
    private fun observeViewModel() {
        userViewModel.loginState.observe(this) { state ->
            when (state) {
                is UserViewModel.LoginState.Success -> {
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                }
                is UserViewModel.LoginState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                UserViewModel.LoginState.LoggedOut -> {
                    // 已登出状态
                }
            }
        }
        
        userViewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.buttonLogin.isEnabled = !isLoading
        }
    }
    
    private fun validateInput(username: String, password: String): Boolean {
        var isValid = true
        
        if (username.isEmpty()) {
            binding.textInputLayoutUsername.error = "请输入用户名"
            isValid = false
        } else {
            binding.textInputLayoutUsername.error = null
        }
        
        if (password.isEmpty()) {
            binding.textInputLayoutPassword.error = "请输入密码"
            isValid = false
        } else if (password.length < 6) {
            binding.textInputLayoutPassword.error = "密码至少6位"
            isValid = false
        } else {
            binding.textInputLayoutPassword.error = null
        }
        
        return isValid
    }
    
    private fun showRegisterDialog() {
        val dialogBinding = DialogRegisterBinding.inflate(layoutInflater)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("注册新账号")
            .setView(dialogBinding.root)
            .setPositiveButton("注册", null)
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
        
        // 设置注册按钮点击事件
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val username = dialogBinding.editTextUsername.text.toString().trim()
            val password = dialogBinding.editTextPassword.text.toString().trim()
            val confirmPassword = dialogBinding.editTextConfirmPassword.text.toString().trim()
            
            if (validateRegisterInput(dialogBinding, username, password, confirmPassword)) {
                userViewModel.register(username, password)
                dialog.dismiss()
            }
        }
    }
    
    private fun validateRegisterInput(
        binding: DialogRegisterBinding,
        username: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true
        
        if (username.isEmpty()) {
            binding.textInputLayoutUsername.error = "请输入用户名"
            isValid = false
        } else if (username.length < 3) {
            binding.textInputLayoutUsername.error = "用户名至少3个字符"
            isValid = false
        } else {
            binding.textInputLayoutUsername.error = null
        }
        
        if (password.isEmpty()) {
            binding.textInputLayoutPassword.error = "请输入密码"
            isValid = false
        } else if (password.length < 6) {
            binding.textInputLayoutPassword.error = "密码至少6位"
            isValid = false
        } else {
            binding.textInputLayoutPassword.error = null
        }
        
        if (confirmPassword.isEmpty()) {
            binding.textInputLayoutConfirmPassword.error = "请确认密码"
            isValid = false
        } else if (confirmPassword != password) {
            binding.textInputLayoutConfirmPassword.error = "两次密码不一致"
            isValid = false
        } else {
            binding.textInputLayoutConfirmPassword.error = null
        }
        
        return isValid
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

