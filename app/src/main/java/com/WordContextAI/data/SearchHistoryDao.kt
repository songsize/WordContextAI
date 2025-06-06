package com.wordcontextai.data

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(searchHistory: SearchHistory)
    
    @Delete
    suspend fun deleteSearch(searchHistory: SearchHistory)
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)
    
    @Query("SELECT * FROM search_history WHERE userId = :userId ORDER BY searchTime DESC")
    fun getAllByUser(userId: Long): Flow<List<SearchHistory>>
    
    @Query("SELECT * FROM search_history WHERE userId = :userId ORDER BY searchTime DESC LIMIT :limit")
    fun getRecentByUser(userId: Long, limit: Int = 20): Flow<List<SearchHistory>>
    
    @Query("DELETE FROM search_history WHERE userId = :userId")
    suspend fun deleteAllByUser(userId: Long)
    
    @Query("SELECT * FROM search_history WHERE word = :word AND userId = :userId LIMIT 1")
    suspend fun findByWordAndUser(word: String, userId: Long): SearchHistory?
    
    @Query("SELECT COUNT(*) FROM search_history WHERE word = :word AND userId = :userId")
    suspend fun getSearchCountByUser(word: String, userId: Long): Int
} 