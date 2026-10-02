package com.example.data

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
import java.io.File

@Entity(tableName = "analysis_history")
data class AnalysisHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val modeTitle: String,
    val modeEmoji: String,
    val userPrompt: String,
    val aiResponse: String,
    val modelUsed: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AnalysisHistoryDao {
    @Query("SELECT * FROM analysis_history ORDER BY timestamp DESC LIMIT 30")
    fun getRecentHistory(): Flow<List<AnalysisHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: AnalysisHistoryItem)

    @Query("DELETE FROM analysis_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM analysis_history")
    suspend fun clearAll()
}

@Database(entities = [AnalysisHistoryItem::class], version = 1, exportSchema = false)
abstract class ScreenAiDatabase : RoomDatabase() {
    abstract fun historyDao(): AnalysisHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: ScreenAiDatabase? = null

        fun getInstance(context: Context): ScreenAiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScreenAiDatabase::class.java,
                    "screenai_temp_history.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}

class HistoryRepository(
    private val dao: AnalysisHistoryDao,
    private val appContext: Context
) {
    val recentHistory: Flow<List<AnalysisHistoryItem>> = dao.getRecentHistory()

    suspend fun recordAnalysis(
        mode: AiMode,
        customQuery: String?,
        response: String,
        modelId: String
    ) {
        dao.insertHistory(
            AnalysisHistoryItem(
                modeTitle = mode.title,
                modeEmoji = mode.emoji,
                userPrompt = customQuery?.takeIf { it.isNotBlank() } ?: mode.shortDescription,
                aiResponse = response,
                modelUsed = modelId
            )
        )
    }

    suspend fun deleteItem(id: Int) = dao.deleteById(id)

    suspend fun clearAllTemporaryData(): Int {
        dao.clearAll()
        var deletedFiles = 0
        val tempDir = File(appContext.cacheDir, "screen_captures")
        if (tempDir.exists()) {
            tempDir.listFiles()?.forEach { file ->
                if (file.delete()) deletedFiles++
            }
        }
        return deletedFiles
    }

    companion object {
        @Volatile
        private var INSTANCE: HistoryRepository? = null

        fun getInstance(context: Context): HistoryRepository {
            return INSTANCE ?: synchronized(this) {
                val db = ScreenAiDatabase.getInstance(context)
                INSTANCE ?: HistoryRepository(db.historyDao(), context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
