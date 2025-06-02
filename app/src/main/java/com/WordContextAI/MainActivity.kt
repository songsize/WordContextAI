package com.wordcontextai

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordcontextai.adapter.ChatAdapter
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.Language
import com.wordcontextai.databinding.ActivityMainBinding
import com.wordcontextai.databinding.BottomSheetSettingsBinding
import com.wordcontextai.databinding.DialogApiKeyBinding
import com.wordcontextai.viewmodel.ChatViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private val viewModel: ChatViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupInputField()
        observeViewModel()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "WordContext AI"
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }
    }
    
    private fun setupInputField() {
        binding.editTextInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
        
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
    }
    
    private fun sendMessage() {
        val word = binding.editTextInput.text.toString().trim()
        if (word.isNotEmpty()) {
            viewModel.generateArticleForWord(word)
            binding.editTextInput.text?.clear()
        }
    }
    
    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages) {
                // 滚动到最新消息
                if (messages.isNotEmpty()) {
                    binding.recyclerViewChat.scrollToPosition(messages.size - 1)
                }
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.buttonSend.isEnabled = !isLoading
            binding.editTextInput.isEnabled = !isLoading
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsBottomSheet()
                true
            }
            R.id.action_clear -> {
                showClearChatDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSettingsBottomSheet() {
        val bottomSheetBinding = BottomSheetSettingsBinding.inflate(layoutInflater)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)
        
        // 设置当前选择的样式
        val currentStyle = viewModel.currentStyle.value ?: ArticleStyle.DAILY
        val currentLanguage = viewModel.currentLanguage.value ?: Language.ENGLISH
        
        // 设置API密钥状态
        updateApiKeyStatus(bottomSheetBinding)
        
        when (currentStyle) {
            ArticleStyle.ACADEMIC -> bottomSheetBinding.radioAcademic.isChecked = true
            ArticleStyle.DAILY -> bottomSheetBinding.radioDaily.isChecked = true
            ArticleStyle.LITERATURE -> bottomSheetBinding.radioLiterature.isChecked = true
            ArticleStyle.BUSINESS -> bottomSheetBinding.radioBusiness.isChecked = true
        }
        
        when (currentLanguage) {
            Language.ENGLISH -> bottomSheetBinding.radioEnglish.isChecked = true
            Language.CHINESE -> bottomSheetBinding.radioChinese.isChecked = true
        }
        
        // API密钥管理按钮
        bottomSheetBinding.buttonManageApiKey.setOnClickListener {
            bottomSheetDialog.dismiss()
            showApiKeyDialog()
        }
        
        // 监听样式选择
        bottomSheetBinding.radioGroupStyle.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.radio_academic -> ArticleStyle.ACADEMIC
                R.id.radio_daily -> ArticleStyle.DAILY
                R.id.radio_literature -> ArticleStyle.LITERATURE
                R.id.radio_business -> ArticleStyle.BUSINESS
                else -> ArticleStyle.DAILY
            }
            viewModel.setArticleStyle(style)
        }
        
        // 监听语言选择
        bottomSheetBinding.radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val language = when (checkedId) {
                R.id.radio_english -> Language.ENGLISH
                R.id.radio_chinese -> Language.CHINESE
                else -> Language.ENGLISH
            }
            viewModel.setLanguage(language)
        }
        
        bottomSheetDialog.show()
    }
    
    private fun updateApiKeyStatus(binding: BottomSheetSettingsBinding) {
        if (viewModel.hasApiKey()) {
            binding.textApiStatus.text = "已设置 ✅"
            binding.buttonManageApiKey.text = "修改"
        } else {
            binding.textApiStatus.text = "未设置 ⚠️"
            binding.buttonManageApiKey.text = "设置"
        }
    }
    
    private fun showApiKeyDialog() {
        val dialogBinding = DialogApiKeyBinding.inflate(layoutInflater)
        
        // 如果已有API密钥，显示部分内容
        viewModel.getApiKey()?.let { apiKey ->
            if (apiKey.length > 10) {
                dialogBinding.editTextApiKey.setText("${apiKey.take(10)}****")
            }
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        dialogBinding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.buttonSave.setOnClickListener {
            val apiKey = dialogBinding.editTextApiKey.text.toString().trim()
            if (apiKey.isNotEmpty() && !apiKey.contains("****")) {
                viewModel.saveApiKey(apiKey)
                dialog.dismiss()
                
                // 显示成功提示
                MaterialAlertDialogBuilder(this)
                    .setTitle("设置成功")
                    .setMessage("API密钥已保存，现在您可以使用完整的AI功能了！")
                    .setPositiveButton("确定", null)
                    .show()
            } else if (apiKey.contains("****")) {
                // 如果包含****，说明用户没有修改，保持原有密钥
                dialog.dismiss()
            } else {
                dialogBinding.editTextApiKey.error = "请输入有效的API密钥"
            }
        }
        
        dialog.show()
    }
    
    private fun showClearChatDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清除聊天记录")
            .setMessage("确定要清除所有聊天记录吗？此操作不可撤销。")
            .setPositiveButton("确定") { _, _ ->
                viewModel.clearChat()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showAboutDialog() {
        val aboutMessage = if (viewModel.hasApiKey()) {
            "WordContext AI 是一款智能词汇学习工具，通过生成式AI技术为您创建包含目标词汇的定制化文章，帮助您在真实语境中掌握词汇用法。\n\n" +
            "功能特点：\n" +
            "• 智能文章生成 ✅\n" +
            "• 词汇高亮显示\n" +
            "• 多种文章风格\n" +
            "• 中英文双语支持\n" +
            "• 一键复制分享\n\n" +
            "当前状态：已连接AI服务 🟢\n\n" +
            "版本：1.0\n" +
            "© 2024 WordContext AI"
        } else {
            "WordContext AI 是一款智能词汇学习工具，通过生成式AI技术为您创建包含目标词汇的定制化文章，帮助您在真实语境中掌握词汇用法。\n\n" +
            "功能特点：\n" +
            "• 智能文章生成\n" +
            "• 词汇高亮显示\n" +
            "• 多种文章风格\n" +
            "• 中英文双语支持\n" +
            "• 一键复制分享\n\n" +
            "当前状态：演示模式 🟡\n" +
            "提示：请在设置中配置API密钥以使用完整功能\n\n" +
            "版本：1.0\n" +
            "© 2024 WordContext AI"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("关于 WordContext AI")
            .setMessage(aboutMessage)
            .setPositiveButton("确定", null)
            .show()
    }
}