package com.mydailynotes.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val description: String = "",
    val start: Long, val end: Long,
    val category: Int = 0, val priority: Int = 1,
    val startAlarm: Boolean = true, val endAlarm: Boolean = true,
    val status: Int = 0 // 0 لم تبدأ، 1 قيد التنفيذ، 2 مكتملة، 3 انتهى وقتها
)
@Entity data class Note(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val date: Long, @ColumnInfo(defaultValue = "0") val pos: Int = 0)
@Entity data class SubNote(@PrimaryKey(autoGenerate = true) val id: Long = 0, val noteId: Long, val text: String, @ColumnInfo(defaultValue = "0") val pos: Int = 0)
@Entity data class Hist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val op: String, val time: Long, val info: String)

@Dao interface AppDao {
    @Query("SELECT * FROM Task ORDER BY start") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM Task WHERE id=:id") suspend fun task(id: Long): Task?
    @Query("SELECT * FROM Task WHERE status!=2 AND `end`>:now") suspend fun upcoming(now: Long): List<Task>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: Task): Long
    @Query("DELETE FROM Task WHERE id=:id") suspend fun deleteTask(id: Long)
    @Query("SELECT * FROM Note ORDER BY pos, id") fun notes(): Flow<List<Note>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertNote(n: Note)
    @Delete suspend fun deleteNote(n: Note)
    @Query("SELECT * FROM SubNote ORDER BY pos, id") fun subs(): Flow<List<SubNote>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSub(s: SubNote)
    @Delete suspend fun deleteSub(s: SubNote)
    @Query("DELETE FROM SubNote WHERE noteId=:id") suspend fun deleteSubsOf(id: Long)
    @Query("SELECT * FROM Hist ORDER BY time DESC LIMIT 100") fun hist(): Flow<List<Hist>>
    @Insert suspend fun addHist(h: Hist)
    @Query("SELECT * FROM Task") suspend fun allTasks(): List<Task>
    @Query("SELECT * FROM Note") suspend fun allNotes(): List<Note>
    @Query("SELECT * FROM SubNote") suspend fun allSubs(): List<SubNote>
    @Query("SELECT * FROM Hist") suspend fun allHist(): List<Hist>
    @Query("DELETE FROM Task") suspend fun clearTasks()
    @Query("DELETE FROM Note") suspend fun clearNotes()
    @Query("DELETE FROM SubNote") suspend fun clearSubs()
    @Query("DELETE FROM Hist") suspend fun clearHist()
}
private val M12 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `SubNote` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `text` TEXT NOT NULL)")
    }
}
private val M23 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Note` ADD COLUMN `pos` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `SubNote` ADD COLUMN `pos` INTEGER NOT NULL DEFAULT 0")
    }
}
@Database(entities = [Task::class, Note::class, SubNote::class, Hist::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var i: AppDb? = null
        fun get(c: Context): AppDb = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "mdn.db").addMigrations(M12, M23).build().also { i = it }
        }
    }
}
