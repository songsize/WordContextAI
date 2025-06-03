package com.wordcontextai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordcontextai.adapter.SearchHistoryAdapter
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.Language
import com.wordcontextai.databinding.ActivityMainBinding
import com.wordcontextai.databinding.BottomSheetSettingsBinding
import com.wordcontextai.databinding.DialogApiKeyBinding
import com.wordcontextai.viewmodel.ChatViewModel
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var markwon: Markwon
    private var currentWord: String = ""
    private var currentContent: String = "" // 保存原始内容用于复制
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 使用 ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 基本设置
        setupToolbar()
        setupInputField()
        setupCopyButton()
        setupSearchHistory()
        observeViewModel()
        
        // 设置 Markwon
        setupMarkwon()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
    }
    
    private fun setupMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()
    }
    
    private fun setupInputField() {
        binding.editTextInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchWord()
                true
            } else {
                false
            }
        }
        
        binding.buttonSend.setOnClickListener {
            searchWord()
        }
    }
    
    private fun setupCopyButton() {
        binding.buttonCopy.setOnClickListener {
            if (currentContent.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Article", currentContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "文章已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupSearchHistory() {
        // 初始化适配器
        searchHistoryAdapter = SearchHistoryAdapter(
            onItemClick = { searchHistory ->
                // 点击历史记录时，执行搜索
                binding.editTextInput.setText(searchHistory.word)
                searchWord()
            },
            onDeleteClick = { searchHistory ->
                // 删除单个历史记录
                viewModel.deleteSearchHistory(searchHistory)
            }
        )
        
        // 设置RecyclerView
        binding.recyclerSearchHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = searchHistoryAdapter
        }
        
        // 清空历史按钮
        binding.buttonClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清空搜索历史")
                .setMessage("确定要清空所有搜索历史吗？")
                .setPositiveButton("清空") { _, _ ->
                    viewModel.clearAllSearchHistory()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun searchWord() {
        val word = binding.editTextInput.text.toString().trim()
        if (word.isNotEmpty()) {
            currentWord = word
            viewModel.generateArticleForWord(word)
            // 隐藏键盘
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.editTextInput.windowToken, 0)
        }
    }
    
    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            if (messages.isNotEmpty()) {
                val latestMessage = messages.last()
                if (!latestMessage.isUser && latestMessage.content.isNotEmpty()) {
                    showContent(latestMessage.content)
                }
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.buttonSend.isEnabled = !isLoading
            binding.editTextInput.isEnabled = !isLoading
            
            if (isLoading) {
                binding.layoutEmpty.visibility = View.GONE
                binding.cardWord.visibility = View.GONE
                binding.cardArticle.visibility = View.GONE
                binding.loadingContainer.visibility = View.VISIBLE
                
                // 根据是否有API密钥显示不同的加载文本
                val hasApiKey = viewModel.hasApiKey()
                binding.textLoading.text = if (hasApiKey) {
                    "正在搜索「$currentWord」的准确定义并生成学习内容..."
                } else {
                    "正在生成「$currentWord」的示例学习内容..."
                }
            } else {
                binding.loadingContainer.visibility = View.GONE
                
                // 如果没有内容，显示空状态
                if (viewModel.messages.value?.isEmpty() == true) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
            }
        }
        
        // 观察搜索历史
        viewModel.searchHistory.observe(this) { historyList ->
            if (historyList.isNotEmpty()) {
                binding.layoutSearchHistory.visibility = View.VISIBLE
                searchHistoryAdapter.submitList(historyList)
            } else {
                binding.layoutSearchHistory.visibility = View.GONE
            }
        }
    }
    
    private fun showContent(content: String) {
        // 保存原始内容
        currentContent = content
        
        // 显示单词卡片
        binding.cardWord.visibility = View.VISIBLE
        binding.textWord.text = currentWord
        binding.textPronunciation.visibility = View.GONE // 隐藏音标，因为现在不是词汇学习
        
        // 显示文章卡片
        binding.cardArticle.visibility = View.VISIBLE
        
        // 使用Markwon渲染Markdown内容
        val spanned = markwon.toMarkdown(content)
        
        // 创建SpannableString以支持高亮
        val spannableString = SpannableString(spanned)
        
        // 高亮显示目标单词
        highlightWord(spannableString, currentWord)
        
        // 设置文本
        binding.textArticleContent.text = spannableString
        
        // 添加淡入动画
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.cardWord.startAnimation(fadeIn)
        binding.cardArticle.startAnimation(fadeIn)
    }
    
    private fun highlightWord(spannable: SpannableString, word: String) {
        val text = spannable.toString()
        val wordLower = word.lowercase()
        var index = text.lowercase().indexOf(wordLower)
        
        while (index >= 0) {
            // 使用新的高亮颜色
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#FEF7E0")), // highlight_yellow
                index,
                index + word.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#EA8600")), // highlight_text
                index,
                index + word.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = text.lowercase().indexOf(wordLower, index + 1)
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
        
        // 设置API密钥状态
        updateApiKeyStatus(bottomSheetBinding)
        
        when (currentStyle) {
            ArticleStyle.ACADEMIC -> bottomSheetBinding.radioAcademic.isChecked = true
            ArticleStyle.DAILY -> bottomSheetBinding.radioDaily.isChecked = true
            ArticleStyle.LITERATURE -> bottomSheetBinding.radioLiterature.isChecked = true
            ArticleStyle.BUSINESS -> bottomSheetBinding.radioBusiness.isChecked = true
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
    
    private fun showAboutDialog() {
        val aboutMessage = if (viewModel.hasApiKey()) {
            "WordContext AI 是一款专为中国学生设计的英语词汇学习工具，通过联网搜索和AI技术为您提供准确、全面的英语学习内容。\n\n" +
            "功能特点：\n" +
            "• 联网获取真实英文词典释义 ✅\n" +
            "• 提供准确的中文解释 ✅\n" +
            "• 地道英文例句配中文翻译 ✅\n" +
            "• 智能生成不同风格的英文文章\n" +
            "• 词汇高亮显示，方便学习\n" +
            "• 词根词缀分析，帮助记忆\n" +
            "• 一键复制分享学习内容\n\n" +
            "当前状态：已连接AI服务 🟢\n\n"
        } else {
            "WordContext AI 是一款专为中国学生设计的英语词汇学习工具，通过联网搜索和AI技术为您提供准确、全面的英语学习内容。\n\n" +
            "功能特点：\n" +
            "• 联网获取真实英文词典释义\n" +
            "• 提供准确的中文解释\n" +
            "• 地道英文例句配中文翻译\n" +
            "• 智能生成不同风格的英文文章\n" +
            "• 词汇高亮显示，方便学习\n" +
            "• 词根词缀分析，帮助记忆\n" +
            "• 一键复制分享学习内容\n\n" +
            "当前状态：演示模式 🟡\n" +
            "提示：请在设置中配置API密钥以使用完整功能\n\n"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("关于 WordContext AI")
            .setMessage(aboutMessage)
            .setPositiveButton("确定", null)
            .show()
    }
}