package com.wordcontextai.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wordcontextai.R
import com.wordcontextai.data.ChatMessage
import com.wordcontextai.databinding.ItemMessageAssistantBinding
import com.wordcontextai.databinding.ItemMessageUserBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemMessageUserBinding.inflate(inflater, parent, false)
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_ASSISTANT -> {
                val binding = ItemMessageAssistantBinding.inflate(inflater, parent, false)
                AssistantMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
        }
    }
    
    inner class UserMessageViewHolder(private val binding: ItemMessageUserBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.textMessage.text = message.content
            binding.textTime.text = timeFormat.format(message.timestamp)
        }
    }
    
    inner class AssistantMessageViewHolder(private val binding: ItemMessageAssistantBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            if (message.isLoading) {
                binding.progressBar.visibility = View.VISIBLE
                binding.textMessage.text = message.content
                binding.buttonCopy.visibility = View.GONE
                binding.buttonShare.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.buttonCopy.visibility = View.VISIBLE
                binding.buttonShare.visibility = View.VISIBLE
                
                // 高亮目标词汇
                val highlightedText = if (message.targetWord != null) {
                    highlightTargetWord(message.content, message.targetWord)
                } else {
                    message.content
                }
                binding.textMessage.text = highlightedText
            }
            
            binding.textTime.text = timeFormat.format(message.timestamp)
            
            // 复制功能
            binding.buttonCopy.setOnClickListener {
                copyToClipboard(binding.root.context, message.content)
            }
            
            // 分享功能
            binding.buttonShare.setOnClickListener {
                shareText(binding.root.context, message.content)
            }
        }
        
        private fun highlightTargetWord(text: String, targetWord: String): SpannableString {
            val spannableString = SpannableString(text)
            val highlightColor = ContextCompat.getColor(binding.root.context, R.color.highlight_color)
            
            val regex = "\\b${Regex.escape(targetWord)}\\b".toRegex(RegexOption.IGNORE_CASE)
            val matches = regex.findAll(text)
            
            for (match in matches) {
                spannableString.setSpan(
                    BackgroundColorSpan(highlightColor),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableString.setSpan(
                    StyleSpan(Typeface.BOLD),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            return spannableString
        }
        
        private fun copyToClipboard(context: Context, text: String) {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WordContext AI", text)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        
        private fun shareText(context: Context, text: String) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "WordContext AI 生成的文章")
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享文章"))
        }
    }
    
    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
} 