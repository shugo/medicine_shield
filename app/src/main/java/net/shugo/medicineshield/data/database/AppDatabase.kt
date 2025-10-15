package net.shugo.medicineshield.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.shugo.medicineshield.data.dao.MedicationConfigDao
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationTime

@Database(
    entities = [Medication::class, MedicationTime::class, MedicationIntake::class, MedicationConfig::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationTimeDao(): MedicationTimeDao
    abstract fun medicationIntakeDao(): MedicationIntakeDao
    abstract fun medicationConfigDao(): MedicationConfigDao

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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()

                // 1. medication_timesテーブルのカラムをリネーム（startDate→validFrom, endDate→validTo）
                database.execSQL(
                    """
                    CREATE TABLE medication_times_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        time TEXT NOT NULL,
                        validFrom INTEGER NOT NULL,
                        validTo INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO medication_times_new (id, medicationId, time, validFrom, validTo, createdAt, updatedAt)
                    SELECT id, medicationId, time, startDate, endDate, createdAt, updatedAt
                    FROM medication_times
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE medication_times")
                database.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")
                database.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                database.execSQL("CREATE INDEX index_medication_times_medicationId_date ON medication_times(medicationId, validFrom, validTo)")

                // 2. medication_configsテーブルを作成
                database.execSQL(
                    """
                    CREATE TABLE medication_configs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        cycleType TEXT NOT NULL,
                        cycleValue TEXT,
                        medicationStartDate INTEGER NOT NULL,
                        medicationEndDate INTEGER,
                        validFrom INTEGER NOT NULL,
                        validTo INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                database.execSQL("CREATE INDEX index_medication_configs_medicationId ON medication_configs(medicationId)")
                database.execSQL("CREATE INDEX index_medication_configs_medicationId_date ON medication_configs(medicationId, validFrom, validTo)")

                // 3. 既存のmedications.cycleType/cycleValue/startDate/endDateをmedication_configsに移行
                database.execSQL(
                    """
                    INSERT INTO medication_configs (medicationId, cycleType, cycleValue, medicationStartDate, medicationEndDate, validFrom, validTo, createdAt, updatedAt)
                    SELECT id, cycleType, cycleValue, startDate, endDate, startDate, NULL, $currentTime, $currentTime
                    FROM medications
                    """.trimIndent()
                )

                // 4. medicationsテーブルからcycleType/cycleValue/startDate/endDateを削除
                database.execSQL(
                    """
                    CREATE TABLE medications_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO medications_new (id, name, createdAt, updatedAt)
                    SELECT id, name, createdAt, updatedAt
                    FROM medications
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE medications")
                database.execSQL("ALTER TABLE medications_new RENAME TO medications")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. MedicationIntakeを全削除（履歴の移行は複雑すぎるため）
                database.execSQL("DELETE FROM medication_intakes")

                // 2. MedicationTimeに一時テーブルを作成
                database.execSQL(
                    """
                    CREATE TABLE medication_times_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        sequenceNumber INTEGER NOT NULL,
                        time TEXT NOT NULL,
                        validFrom INTEGER NOT NULL,
                        validTo INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 3. 既存データを移行（sequenceNumberを割り当て）
                // valid_to IS NULLのレコードのみ移行し、medicationId毎にtime順で連番を割り当て
                database.execSQL(
                    """
                    INSERT INTO medication_times_new (id, medicationId, sequenceNumber, time, validFrom, validTo, createdAt, updatedAt)
                    SELECT
                        id,
                        medicationId,
                        ROW_NUMBER() OVER (PARTITION BY medicationId ORDER BY time) as sequenceNumber,
                        time,
                        validFrom,
                        validTo,
                        createdAt,
                        updatedAt
                    FROM medication_times
                    WHERE validTo IS NULL
                    """.trimIndent()
                )

                // 4. 古いテーブルを削除
                database.execSQL("DROP TABLE medication_times")

                // 5. 新しいテーブルをリネーム
                database.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")

                // 6. インデックスを作成
                database.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                database.execSQL("CREATE INDEX index_medication_times_medicationId_validFrom_validTo ON medication_times(medicationId, validFrom, validTo)")

                // 7. MedicationIntakeテーブルを再作成
                database.execSQL("DROP TABLE medication_intakes")
                database.execSQL(
                    """
                    CREATE TABLE medication_intakes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        sequenceNumber INTEGER NOT NULL,
                        scheduledDate TEXT NOT NULL,
                        takenAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 8. インデックスを作成
                database.execSQL("CREATE INDEX index_medication_intakes_medicationId ON medication_intakes(medicationId)")
                database.execSQL("CREATE INDEX index_medication_intakes_scheduledDate ON medication_intakes(scheduledDate)")
                database.execSQL("CREATE UNIQUE INDEX index_medication_intakes_medicationId_scheduledDate_sequenceNumber ON medication_intakes(medicationId, scheduledDate, sequenceNumber)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // medication_timesテーブルにdoseカラムを追加（デフォルト値: 1.0）
                database.execSQL("ALTER TABLE medication_times ADD COLUMN dose REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // medication_configsテーブルにisAsNeededカラムを追加（デフォルト値: 0=false）
                database.execSQL("ALTER TABLE medication_configs ADD COLUMN isAsNeeded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // medication_configsテーブルにdoseカラムを追加（デフォルト値: 1.0）
                database.execSQL("ALTER TABLE medication_configs ADD COLUMN dose REAL NOT NULL DEFAULT 1.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_shield_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
