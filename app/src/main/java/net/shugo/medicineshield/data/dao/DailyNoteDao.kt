package net.shugo.medicineshield.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.DailyNote

@Dao
interface DailyNoteDao {
    @Insert
    suspend fun insert(note: DailyNote)

    @Update
    suspend fun update(note: DailyNote)

    @Query("DELETE FROM daily_notes WHERE noteDate = :noteDate")
    suspend fun delete(noteDate: String)

    @Query("SELECT * FROM daily_notes WHERE noteDate = :date")
    fun getNoteByDate(date: String): Flow<DailyNote?>

    @Query("SELECT * FROM daily_notes WHERE noteDate = :date")
    suspend fun getNoteByDateSync(date: String): DailyNote?

    @Query("SELECT * FROM daily_notes WHERE noteDate < :currentDate ORDER BY noteDate DESC LIMIT 1")
    suspend fun getPreviousNote(currentDate: String): DailyNote?

    @Query("SELECT * FROM daily_notes WHERE noteDate > :currentDate ORDER BY noteDate ASC LIMIT 1")
    suspend fun getNextNote(currentDate: String): DailyNote?
}
