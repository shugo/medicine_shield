package net.shugo.medicineshield.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.DailyNote

@Dao
interface DailyNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: DailyNote)

    @Update
    suspend fun update(note: DailyNote)

    @Query("DELETE FROM daily_notes WHERE noteDate = :noteDate")
    suspend fun delete(noteDate: String)

    @Query("SELECT * FROM daily_notes WHERE noteDate = :date")
    fun getNoteByDate(date: String): Flow<DailyNote?>

    @Query("SELECT * FROM daily_notes WHERE noteDate = :date")
    suspend fun getNoteByDateSync(date: String): DailyNote?
}
