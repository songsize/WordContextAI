package com.wordcontextai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wordcontextai.R
import com.wordcontextai.data.SearchHistory
import java.text.SimpleDateFormat
import java.util.*

class SearchHistoryAdapter(
    private val onItemClick: (SearchHistory) -> Unit,
    private val onDeleteClick: (SearchHistory) -> Unit
) : ListAdapter<SearchHistory, SearchHistoryAdapter.ViewHolder>(SearchHistoryDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchHistory = getItem(position)
        holder.bind(searchHistory)
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textWord: TextView = itemView.findViewById(R.id.text_word)
        private val textTime: TextView = itemView.findViewById(R.id.text_time)
        private val buttonDelete: ImageButton = itemView.findViewById(R.id.button_delete)
        
        fun bind(searchHistory: SearchHistory) {
            textWord.text = searchHistory.word
            textTime.text = formatTime(searchHistory.searchTime)
            
            itemView.setOnClickListener {
                onItemClick(searchHistory)
            }
            
            buttonDelete.setOnClickListener {
                onDeleteClick(searchHistory)
            }
        }
        
        private fun formatTime(date: Date): String {
            val now = Date()
            val diff = now.time - date.time
            
            return when {
                diff < 60 * 1000 -> "刚刚"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
                else -> SimpleDateFormat("MM月dd日", Locale.CHINA).format(date)
            }
        }
    }
    
    class SearchHistoryDiffCallback : DiffUtil.ItemCallback<SearchHistory>() {
        override fun areItemsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
            return oldItem == newItem
        }
    }
} 