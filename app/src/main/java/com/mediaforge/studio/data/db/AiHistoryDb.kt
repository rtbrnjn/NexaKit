package com.mediaforge.studio.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

object AiHistoryKind {
    const val PDF_SUMMARY = "PDF_SUMMARY"
    const val COPYWRITING = "COPYWRITING"
}

@Entity(tableName = "ai_history")
data class AiHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val subtitle: String,
    val content: String,
    val model: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface AiHistoryDao {
    @Query("SELECT * FROM ai_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AiHistoryEntity): Long

    @Query("DELETE FROM ai_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_history")
    suspend fun clearAll()
}

@Database(entities = [AiHistoryEntity::class], version = 1, exportSchema = false)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun aiHistoryDao(): AiHistoryDao

    companion object {
        @Volatile
        private var instance: NexaDatabase? = null

        fun get(context: Context): NexaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    "nexakit_ai_vault.db"
                ).build().also { instance = it }
            }
    }
}
