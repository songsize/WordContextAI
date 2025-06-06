package com.wordcontextai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [User::class, SearchHistory::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun searchHistoryDao(): SearchHistoryDao
      companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {                // 创建用户表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        username TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        avatarPath TEXT,
                        apiKey TEXT,
                        createdAt INTEGER NOT NULL,
                        lastLoginAt INTEGER NOT NULL
                    )
                """)
                
                // 创建用户名唯一索引
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users (username)")
                
                // 备份原有的search_history数据
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS search_history_backup (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        searchTime INTEGER
                    )
                """)
                
                // 复制原有数据到备份表（如果原表存在）
                database.execSQL("""
                    INSERT INTO search_history_backup (id, word, searchTime)
                    SELECT id, word, searchTime FROM search_history WHERE EXISTS (
                        SELECT 1 FROM sqlite_master WHERE type='table' AND name='search_history'
                    )
                """)
                
                // 删除原有的search_history表
                database.execSQL("DROP TABLE IF EXISTS search_history")
                
                // 创建新的search_history表，符合实体类定义
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        userId INTEGER NOT NULL,
                        searchTime INTEGER NOT NULL,
                        FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """)
                
                // 创建索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_userId ON search_history (userId)")
                
                // 如果有备份数据，将其恢复到新表（为所有记录设置默认userId=1）
                database.execSQL("""
                    INSERT INTO search_history (id, word, userId, searchTime)
                    SELECT id, word, 1, COALESCE(searchTime, ${System.currentTimeMillis()})
                    FROM search_history_backup
                """)
                
                // 删除备份表
                database.execSQL("DROP TABLE IF EXISTS search_history_backup")
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wordcontext_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // 添加fallback机制以防迁移失败
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 