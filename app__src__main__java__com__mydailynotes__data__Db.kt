package com.mydailynotes.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val description: String = "",
    val start: Long, val end: Long,
    val category: Int = 0, val priority: Int = 1,
    val startAlarm: Boolean = true, val endAlarm: Boolean = true,
    val status: Int = 0 // 0 لم تبدأ، 1 قيد التنفيذ، 2 مكتملة، 3 انتهى وقتها
)
@Entity data class Note(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val date: Long)
@Entity data class Hist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val op: String, val time: Long, val info: String)

@Dao interface AppDao {
    @Query("SELECT * FROM Task ORDER BY start") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM Task WHERE id=:id") suspend fun task(id: Long): Task?
    @Query("SELECT * FROM Task WHERE status!=2 AND end>:now") suspend fun upcoming(now: Long): List<Task>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: Task): Long
    @Query("DELETE FROM Task WHERE id=:id") suspend fun deleteTask(id: Long)
    @Query("SELECT * FROM Note ORDER BY date DESC, id DESC") fun notes(): Flow<List<Note>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNote(n: Note)
    @Delete suspend fun deleteNote(n: Note)
    @Query("SELECT * FROM Hist ORDER BY time DESC LIMIT 100") fun hist(): Flow<List<Hist>>
    @Insert suspend fun addHist(h: Hist)
}
@Database(entities = [Task::class, Note::class, Hist::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var i: AppDb? = null
        fun get(c: Context): AppDb = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "mdn.db").build().also { i = it }
        }
    }
}
