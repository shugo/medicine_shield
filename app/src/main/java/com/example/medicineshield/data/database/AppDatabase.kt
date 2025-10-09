package com.example.medicineshield.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.medicineshield.data.dao.MedicationDao
import com.example.medicineshield.data.dao.MedicationIntakeDao
import com.example.medicineshield.data.dao.MedicationTimeDao
import com.example.medicineshield.data.model.Medication
import com.example.medicineshield.data.model.MedicationIntake
import com.example.medicineshield.data.model.MedicationTime

@Database(
    entities = [Medication::class, MedicationTime::class, MedicationIntake::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationTimeDao(): MedicationTimeDao
    abstract fun medicationIntakeDao(): MedicationIntakeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medication_intakes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `medicationId` INTEGER NOT NULL,
                        `scheduledTime` TEXT NOT NULL,
                        `scheduledDate` TEXT NOT NULL,
                        `takenAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_intakes_medicationId` ON `medication_intakes` (`medicationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_intakes_scheduledDate` ON `medication_intakes` (`scheduledDate`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_shield_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
