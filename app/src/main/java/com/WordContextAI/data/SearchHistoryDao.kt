package com.wordcontextai.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SearchHistoryDao {
    
    @Query("SELECT * FROM search_history ORDER BY searchTime DESC LIMIT 20")
    fun getRecentSearches(): LiveData<List<SearchHistory>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(searchHistory: SearchHistory)
    
    @Delete
    suspend fun deleteSearch(searchHistory: SearchHistory)
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)
    
    @Query("DELETE FROM search_history")
    suspend fun deleteAllSearches()
    
    @Query("SELECT COUNT(*) FROM search_history WHERE word = :word")
    suspend fun getSearchCount(word: String): Int
} 