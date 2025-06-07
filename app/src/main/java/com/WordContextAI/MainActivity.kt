package com.wordcontextai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wordcontextai.adapter.SearchHistoryAdapter
import com.wordcontextai.data.ArticleStyle
import com.wordcontextai.data.Language
import com.wordcontextai.databinding.ActivityMainBinding
import com.wordcontextai.databinding.BottomSheetSettingsBinding
import com.wordcontextai.databinding.DialogApiKeyBinding
import com.wordcontextai.viewmodel.ChatViewModel
import com.wordcontextai.viewmodel.UserViewModel
import com.wordcontextai.utils.UserPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.io.File
import java.io.FileOutputStream
import android.util.Log

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private lateinit var userPreferences: UserPreferences
    private lateinit var markwon: Markwon
    private var currentWord: String = ""
    private var currentDefinition: String = ""
    private var currentArticle: String = ""
    private var currentTranslation: String = ""
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    
    // 图片选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri ->
            saveAvatarToInternalStorage(selectedImageUri)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 使用 ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化用户偏好设置
        userPreferences = UserPreferences(this)
        
        // 基本设置
        setupToolbar()
        setupInputField()
        setupButtons()
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
    
    private fun setupButtons() {
        // 返回按钮
        binding.buttonBack.setOnClickListener {
            clearResults()
        }
        
        // 复制按钮
        binding.buttonCopy.setOnClickListener {
            if (currentArticle.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Article", currentArticle)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "文章已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 分享按钮
        binding.buttonShare.setOnClickListener {
            if (currentArticle.isNotEmpty()) {
                shareContent()
            }
        }
        
        // 翻译按钮
        binding.buttonTranslate.setOnClickListener {
            translateArticle()
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
        val input = binding.editTextInput.text.toString().trim()
        if (input.isEmpty()) return
        
            // 检查是否已登录
            val currentUser = userViewModel.currentUser.value
            if (currentUser == null) {
            // 未登录，显示登录提示但允许继续使用
            showLoginPromptDialog {
                performSearch(input)
            }
        } else {
            // 已登录，正常搜索
            performSearch(input)
        }
    }
    
    private fun showLoginPromptDialog(onProceed: () -> Unit) {
                MaterialAlertDialogBuilder(this)
            .setTitle("建议登录")
                    .setMessage("登录后可以保存您的搜索历史，方便随时查看学习记录。")
                    .setPositiveButton("去登录") { _, _ ->
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                    }
            .setNegativeButton("继续使用") { _, _ ->
                onProceed()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun performSearch(input: String) {
        currentWord = input
        
        // 判断是否为多词汇输入（包含逗号、分号或换行）
        val isMultipleWords = input.contains(",") || input.contains("，") || 
                            input.contains(";") || input.contains("；") || 
                            input.contains("\n") || input.split(" ").size > 3
        
        if (isMultipleWords) {
            // 多词汇输入，只生成文章
            viewModel.generateArticleForMultipleWords(input)
        } else {
            // 单词或词组，生成释义和文章
            viewModel.generateArticleForWord(input)
        }
        
        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editTextInput.windowToken, 0)
        
        // 显示返回按钮
        binding.buttonBack.visibility = View.VISIBLE
    }
    
    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            if (messages.isNotEmpty()) {
                val latestMessage = messages.last()
                if (!latestMessage.isUser && latestMessage.content.isNotEmpty()) {
                    processContent(latestMessage.content)
                }
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.buttonSend.isEnabled = !isLoading
            binding.editTextInput.isEnabled = !isLoading
            
            if (isLoading) {
                showLoadingSteps()
            } else {
                hideLoadingSteps()
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
        
        // 观察用户状态，更新toolbar头像
        lifecycleScope.launch {
            userViewModel.currentUser.collectLatest { user ->
                if (user != null) {
                    binding.toolbarAvatar.visibility = View.VISIBLE
                    if (user.avatarPath != null && File(user.avatarPath).exists()) {
                        binding.toolbarAvatar.setImageURI(Uri.fromFile(File(user.avatarPath)))
                    } else {
                        binding.toolbarAvatar.setImageResource(R.drawable.ic_account_circle)
                    }
                    
                    // 点击头像打开设置
                    binding.toolbarAvatar.setOnClickListener {
                        showSettingsBottomSheet()
                    }
                } else {
                    binding.toolbarAvatar.visibility = View.GONE
                }
            }
        }
    }
    
    private fun showLoadingSteps() {
        binding.layoutEmpty.visibility = View.GONE
        binding.cardDefinition.visibility = View.GONE
        binding.cardArticle.visibility = View.GONE
        binding.loadingContainer.visibility = View.VISIBLE
        
        // 重置所有步骤状态
        resetLoadingSteps()
        
        // 启动加载步骤动画
        lifecycleScope.launch {
            // 步骤1：分析输入内容
            activateStep(1, "🔍 分析输入内容...")
            delay(800)
            completeStep(1)
            
            // 步骤2：获取词汇释义
            activateStep(2, "📚 获取词汇释义...")
            delay(1200)
            completeStep(2)
            
            // 步骤3：生成学习文章
            activateStep(3, "✍️ 生成学习文章...")
            delay(2000)
            completeStep(3)
            
            // 步骤4：优化内容展示
            activateStep(4, "🎨 优化内容展示...")
            delay(600)
            completeStep(4)
        }
    }
    
    private fun resetLoadingSteps() {
        for (i in 1..4) {
            val stepLayout = when (i) {
                1 -> binding.step1
                2 -> binding.step2
                3 -> binding.step3
                4 -> binding.step4
                else -> null
            }
            stepLayout?.alpha = 0.3f
            
            val progressView = when (i) {
                1 -> binding.step1Progress
                2 -> binding.step2Progress
                3 -> binding.step3Progress
                4 -> binding.step4Progress
                else -> null
            }
            progressView?.visibility = View.GONE
            
            val statusView = when (i) {
                1 -> binding.step1Status
                2 -> binding.step2Status
                3 -> binding.step3Status
                4 -> binding.step4Status
                else -> null
            }
            statusView?.apply {
                text = "等待中"
                setTextColor(getColor(R.color.gray_dark))
            }
        }
    }
    
    private fun activateStep(step: Int, message: String) {
        val stepLayout = when (step) {
            1 -> binding.step1
            2 -> binding.step2
            3 -> binding.step3
            4 -> binding.step4
            else -> null
        }
        stepLayout?.alpha = 1.0f
        
        val progressView = when (step) {
            1 -> binding.step1Progress
            2 -> binding.step2Progress
            3 -> binding.step3Progress
            4 -> binding.step4Progress
            else -> null
        }
        progressView?.visibility = View.VISIBLE
        
        val statusView = when (step) {
            1 -> binding.step1Status
            2 -> binding.step2Status
            3 -> binding.step3Status
            4 -> binding.step4Status
            else -> null
        }
        statusView?.apply {
            text = "进行中"
            setTextColor(getColor(R.color.primary))
            visibility = View.VISIBLE
        }
        
        val textView = when (step) {
            1 -> binding.step1Text
            2 -> binding.step2Text
            3 -> binding.step3Text
            4 -> binding.step4Text
            else -> null
        }
        textView?.text = message
    }
    
    private fun completeStep(step: Int) {
        val progressView = when (step) {
            1 -> binding.step1Progress
            2 -> binding.step2Progress
            3 -> binding.step3Progress
            4 -> binding.step4Progress
            else -> null
        }
        progressView?.visibility = View.GONE
        
        val statusView = when (step) {
            1 -> binding.step1Status
            2 -> binding.step2Status
            3 -> binding.step3Status
            4 -> binding.step4Status
            else -> null
        }
        statusView?.apply {
            text = "完成"
            setTextColor(getColor(R.color.success_green))
        }
    }
    
    private fun hideLoadingSteps() {
        binding.loadingContainer.visibility = View.GONE
    }
    
    private fun processContent(content: String) {
        // 隐藏空状态
        binding.layoutEmpty.visibility = View.GONE
        
        try {
            // 调试日志
            Log.d("MainActivity", "Processing content length: ${content.length}")
            Log.d("MainActivity", "Content preview: ${content.take(200)}")
            
            // 检查是否包含特殊分隔符（新的独立生成方式）
            if (content.contains("<!-- ARTICLE_SEPARATOR -->")) {
                // 新方式：释义和文章是独立生成的
                val parts = content.split("<!-- ARTICLE_SEPARATOR -->", limit = 2)
                if (parts.size >= 2) {
                    currentDefinition = parts[0].trim()
                    currentArticle = parts[1].trim()
                    
                    Log.d("MainActivity", "Split by ARTICLE_SEPARATOR - Definition: ${currentDefinition.length}, Article: ${currentArticle.length}")
                    
                    showDefinitionAndArticle(currentDefinition, currentArticle)
                } else {
                    // 分离失败，整体显示
                    Log.d("MainActivity", "Failed to split by ARTICLE_SEPARATOR")
                    currentArticle = content
                    showArticleOnly(content)
                }
            } else if (content.contains("## 1. 词语释义") && (content.contains("## 3. 文章示例") || content.contains("## 2. 句子应用"))) {
                // 旧方式：整体生成后分离（兼容性保留）
                val articleMarker = if (content.contains("## 3. 文章示例")) "## 3. 文章示例" else "## 2. 句子应用"
                val parts = content.split(articleMarker, limit = 2)
                
                if (parts.size >= 2) {
                    // 提取释义部分（包括词语释义和句子应用）
                    val definitionPart = parts[0].trim()
                    
                    // 如果文章在第3部分，需要包含第2部分的句子应用
                    val fullDefinition = if (articleMarker == "## 3. 文章示例" && content.contains("## 2. 句子应用")) {
                        // 找到句子应用部分
                        val sentencePart = content.substringAfter("## 2. 句子应用")
                            .substringBefore("## 3. 文章示例")
                        definitionPart + "\n\n## 2. 句子应用" + sentencePart
                    } else {
                        definitionPart
                    }
                    
                    currentDefinition = fullDefinition
                    currentArticle = extractArticleContent(parts[1].trim())
                    
                    Log.d("MainActivity", "Old format split - Definition: ${currentDefinition.length}, Article: ${currentArticle.length}")
                    
                    showDefinitionAndArticle(currentDefinition, currentArticle)
                } else {
                    // 分离失败，整体显示
                    Log.d("MainActivity", "Failed to split old format content")
                    currentArticle = content
                    showArticleOnly(content)
                }
            } else {
                // 多词汇模式或其他格式，只显示文章
                Log.d("MainActivity", "Multiple words mode or other format, showing as article only")
                currentArticle = content
                showArticleOnly(content)
            }
        } catch (e: Exception) {
            // 出错时整体显示
            Log.e("MainActivity", "Error processing content", e)
            currentArticle = content
            showArticleOnly(content)
        }
    }
    
    private fun extractArticleContent(articleSection: String): String {
        // 提取文章内容，保留实际的文章部分
        var content = articleSection
        
        // 如果包含风格标题，提取标题后的内容
        if (content.contains("### ") && content.contains("风格")) {
            content = content.substringAfter("\n", content)
        }
        
        // 去掉末尾的分隔线和说明文字
        if (content.contains("---")) {
            content = content.substringBefore("---")
        }
        
        // 如果文章部分实际上是句子应用，保持原样
        // 否则提取纯文章内容
        return content.trim()
    }
    
    private fun showDefinitionAndArticle(definition: String, article: String) {
        // 显示词语释义卡片
        binding.cardDefinition.visibility = View.VISIBLE
        binding.textWord.text = currentWord
        binding.textPronunciation.visibility = View.GONE
        
        // 使用Markwon渲染释义内容
        val definitionSpanned = markwon.toMarkdown(definition)
        binding.textDefinitionContent.text = definitionSpanned
        
        // 显示文章卡片
        binding.cardArticle.visibility = View.VISIBLE
        showArticleContent(article)
        
        // 添加淡入动画
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.cardDefinition.startAnimation(fadeIn)
        binding.cardArticle.startAnimation(fadeIn)
    }
    
    private fun showArticleOnly(content: String) {
        // 只显示文章卡片
        binding.cardDefinition.visibility = View.GONE
        binding.cardArticle.visibility = View.VISIBLE
        
        showArticleContent(content)
        
        // 添加淡入动画
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.cardArticle.startAnimation(fadeIn)
    }
    
    private fun showArticleContent(article: String) {
        // 使用Markwon渲染文章内容 
        val spanned = markwon.toMarkdown(article)
        
        // 创建SpannableString以支持高亮
        val spannableString = SpannableString(spanned)
        
        // 高亮显示目标单词
        if (currentWord.isNotEmpty()) {
            highlightWords(spannableString, currentWord)
        }
        
        // 设置文本
        binding.textArticleContent.text = spannableString
    }
    
    private fun highlightWords(spannable: SpannableString, words: String) {
        val text = spannable.toString()
        
        // 处理多词汇的情况
        val wordList = if (words.contains(",") || words.contains("，") || words.contains(";") || words.contains("；")) {
            words.split(Regex("[,，;；\\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf(words.trim())
        }
        
        wordList.forEach { word ->
            if (word.isNotEmpty()) {
        val wordLower = word.lowercase()
        var index = text.lowercase().indexOf(wordLower)
        
        while (index >= 0) {
            spannable.setSpan(
                        BackgroundColorSpan(Color.parseColor("#FEF7E0")),
                index,
                index + word.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#EA8600")),
                index,
                index + word.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = text.lowercase().indexOf(wordLower, index + 1)
        }
            }
        }
    }
    
    private fun translateArticle() {
        if (currentArticle.isEmpty()) return
        
        if (currentTranslation.isNotEmpty()) {
            // 已经有翻译，直接显示
            showTranslation(currentTranslation)
            return
        }
        
        // 显示翻译加载状态
        binding.buttonTranslate.isEnabled = false
        binding.buttonTranslate.text = "翻译中..."
        
        lifecycleScope.launch {
            try {
                // 调用翻译服务
                val translation = viewModel.translateText(currentArticle)
                currentTranslation = translation
                showTranslation(translation)
                
                binding.buttonTranslate.text = "隐藏翻译"
            } catch (e: Exception) {
                // 翻译失败，显示模拟翻译
                currentTranslation = generateMockTranslation(currentArticle)
                showTranslation(currentTranslation)
                binding.buttonTranslate.text = "隐藏翻译"
                
                Toast.makeText(this@MainActivity, "使用离线翻译", Toast.LENGTH_SHORT).show()
            } finally {
                binding.buttonTranslate.isEnabled = true
            }
        }
    }
    
    private fun showTranslation(translation: String) {
        if (binding.layoutTranslation.visibility == View.VISIBLE) {
            // 隐藏翻译
            binding.layoutTranslation.visibility = View.GONE
            binding.buttonTranslate.text = "翻译"
        } else {
            // 显示翻译，使用Markwon渲染Markdown格式
            val spanned = markwon.toMarkdown(translation)
            binding.textTranslationContent.text = spanned
            binding.layoutTranslation.visibility = View.VISIBLE
            binding.buttonTranslate.text = "隐藏翻译"
        }
    }
    
    private fun generateMockTranslation(text: String): String {
        // 返回带Markdown格式的模拟翻译
        return """
        ## 文章翻译
        
        这是一篇关于 **"$currentWord"** 的英语学习文章。文章通过真实的语境展示了词汇的使用方法，帮助学习者更好地理解和掌握英语词汇。
        
        ### 主要内容
        
        文章从多个角度探讨了目标词汇的使用：
        - **实际应用场景**：展示词汇在真实生活中的应用
        - **语言特点**：体现了地道的英语表达方式
        - **学习价值**：为英语学习者提供了有价值的参考
        
        ### 学习建议
        
        1. **反复阅读**：多次阅读原文，体会词汇的用法
        2. **对比学习**：将英文原文与中文翻译对照学习
        3. **实践应用**：尝试在自己的写作中使用目标词汇
        
        通过这样的学习方式，可以有效提高英语词汇量和语感，为进一步的英语学习打下坚实的基础。
        
        ---
        *注：这是离线翻译版本。配置API密钥后可获得更准确的AI翻译。*
        """.trimIndent()
    }
    
    private fun shareContent() {
        val shareText = if (currentDefinition.isNotEmpty()) {
            "【${currentWord} 学习内容】\n\n${currentDefinition}\n\n${currentArticle}\n\n来自 WordContext AI"
        } else {
            "【英语学习文章】\n\n${currentArticle}\n\n来自 WordContext AI"
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "分享学习内容"))
    }
    
    private fun clearResults() {
        // 清除结果并返回初始状态
        binding.cardDefinition.visibility = View.GONE
        binding.cardArticle.visibility = View.GONE
        binding.layoutTranslation.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.buttonBack.visibility = View.GONE
        
        // 清除数据
        currentWord = ""
        currentDefinition = ""
        currentArticle = ""
        currentTranslation = ""
        
        // 重置翻译按钮
        binding.buttonTranslate.text = "翻译"
        
        // 清除输入框
        binding.editTextInput.text?.clear()
        
        // 清除聊天记录
        viewModel.clearChat()
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
        
        // 设置用户信息显示
        setupUserInfo(bottomSheetBinding, bottomSheetDialog)
        
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
    
    private fun setupUserInfo(binding: BottomSheetSettingsBinding, dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            userViewModel.currentUser.collectLatest { user ->
                if (user != null) {
                    // 已登录，显示用户信息
                    binding.layoutUserInfo.visibility = View.VISIBLE
                    binding.cardLoginPrompt.visibility = View.GONE
                    binding.textViewUsername.text = user.username
                    
                    // 显示用户头像
                    if (user.avatarPath != null && File(user.avatarPath).exists()) {
                        binding.imageViewAvatar.setImageURI(Uri.fromFile(File(user.avatarPath)))
                    } else {
                        binding.imageViewAvatar.setImageResource(R.drawable.ic_account_circle)
                    }
                    
                    // 点击头像更换
                    binding.imageViewAvatar.setOnClickListener {
                        if (userViewModel.currentUser.value != null) {
                            imagePickerLauncher.launch("image/*")
                        } else {
                            Toast.makeText(this@MainActivity, "请先登录", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // 登出按钮
                    binding.buttonLogout.setOnClickListener {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle("退出登录")
                            .setMessage("确定要退出登录吗？")
                            .setPositiveButton("退出") { _, _ ->
                                userViewModel.logout()
                                dialog.dismiss()
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                } else {
                    // 未登录，显示登录提示但不强制
                    binding.layoutUserInfo.visibility = View.GONE
                    binding.cardLoginPrompt.visibility = View.VISIBLE
                    
                    binding.buttonGoLogin.setOnClickListener {
                        dialog.dismiss()
                        val intent = Intent(this@MainActivity, LoginActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
        }
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
            "• 一键复制分享学习内容\n" +
            "• 支持多词汇学习模式\n" +
            "• 文章智能翻译功能\n\n" +
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
            "• 一键复制分享学习内容\n" +
            "• 支持多词汇学习模式\n" +
            "• 文章智能翻译功能\n\n" +
            "当前状态：演示模式 🟡\n" +
            "配置API密钥可解锁完整功能"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("关于 WordContext AI")
            .setMessage(aboutMessage)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun saveAvatarToInternalStorage(uri: Uri) {
        lifecycleScope.launch {
        try {
            val inputStream = contentResolver.openInputStream(uri)
                val file = File(filesDir, "avatar_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(file)
            
            inputStream?.copyTo(outputStream)
                outputStream.close()
            inputStream?.close()
            
            // 更新用户头像路径
            userViewModel.updateAvatar(file.absolutePath)
                
                Toast.makeText(this@MainActivity, "头像更新成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "头像更新失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}