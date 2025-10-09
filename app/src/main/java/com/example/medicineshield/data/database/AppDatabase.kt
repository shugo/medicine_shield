package com.example.medicineshield.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.medicineshield.data.dao.MedicationDao
import com.example.medicineshield.data.dao.MedicationTimeDao
import com.example.medicineshield.data.model.Medication
import com.example.medicineshield.data.model.MedicationTime

@Database(
    entities = [Medication::class, MedicationTime::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationTimeDao(): MedicationTimeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_shield_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
