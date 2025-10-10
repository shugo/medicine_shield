package net.shugo.medicineshield.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationTime

@Database(
    entities = [Medication::class, MedicationTime::class, MedicationIntake::class],
    version = 3,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. 新しいテーブルを作成
                database.execSQL(
                    """
                    CREATE TABLE medication_times_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        time TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 2. 既存データを移行（既存の時刻はすべて過去から有効とする）
                database.execSQL(
                    """
                    INSERT INTO medication_times_new (id, medicationId, time, startDate, endDate, createdAt, updatedAt)
                    SELECT id, medicationId, time, 0, NULL,
                           ${System.currentTimeMillis()},
                           ${System.currentTimeMillis()}
                    FROM medication_times
                    """.trimIndent()
                )

                // 3. 古いテーブルを削除
                database.execSQL("DROP TABLE medication_times")

                // 4. 新しいテーブルをリネーム
                database.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")

                // 5. インデックスを作成
                database.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                database.execSQL("CREATE INDEX index_medication_times_medicationId_date ON medication_times(medicationId, startDate, endDate)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_shield_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
