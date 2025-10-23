package net.shugo.medicineshield.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.shugo.medicineshield.data.dao.DailyNoteDao
import net.shugo.medicineshield.data.dao.MedicationConfigDao
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationTime

@Database(
    entities = [Medication::class, MedicationTime::class, MedicationIntake::class, MedicationConfig::class, DailyNote::class],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationTimeDao(): MedicationTimeDao
    abstract fun medicationIntakeDao(): MedicationIntakeDao
    abstract fun medicationConfigDao(): MedicationConfigDao
    abstract fun dailyNoteDao(): DailyNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_intakes_medicationId` ON `medication_intakes` (`medicationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_intakes_scheduledDate` ON `medication_intakes` (`scheduledDate`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 新しいテーブルを作成
                db.execSQL(
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
                db.execSQL(
                    """
                    INSERT INTO medication_times_new (id, medicationId, time, startDate, endDate, createdAt, updatedAt)
                    SELECT id, medicationId, time, 0, NULL,
                           ${System.currentTimeMillis()},
                           ${System.currentTimeMillis()}
                    FROM medication_times
                    """.trimIndent()
                )

                // 3. 古いテーブルを削除
                db.execSQL("DROP TABLE medication_times")

                // 4. 新しいテーブルをリネーム
                db.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")

                // 5. インデックスを作成
                db.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                db.execSQL("CREATE INDEX index_medication_times_medicationId_date ON medication_times(medicationId, startDate, endDate)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()

                // 1. medication_timesテーブルのカラムをリネーム（startDate→validFrom, endDate→validTo）
                db.execSQL(
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

                db.execSQL(
                    """
                    INSERT INTO medication_times_new (id, medicationId, time, validFrom, validTo, createdAt, updatedAt)
                    SELECT id, medicationId, time, startDate, endDate, createdAt, updatedAt
                    FROM medication_times
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE medication_times")
                db.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")
                db.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                db.execSQL("CREATE INDEX index_medication_times_medicationId_date ON medication_times(medicationId, validFrom, validTo)")

                // 2. medication_configsテーブルを作成
                db.execSQL(
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

                db.execSQL("CREATE INDEX index_medication_configs_medicationId ON medication_configs(medicationId)")
                db.execSQL("CREATE INDEX index_medication_configs_medicationId_date ON medication_configs(medicationId, validFrom, validTo)")

                // 3. 既存のmedications.cycleType/cycleValue/startDate/endDateをmedication_configsに移行
                db.execSQL(
                    """
                    INSERT INTO medication_configs (medicationId, cycleType, cycleValue, medicationStartDate, medicationEndDate, validFrom, validTo, createdAt, updatedAt)
                    SELECT id, cycleType, cycleValue, startDate, endDate, startDate, NULL, $currentTime, $currentTime
                    FROM medications
                    """.trimIndent()
                )

                // 4. medicationsテーブルからcycleType/cycleValue/startDate/endDateを削除
                db.execSQL(
                    """
                    CREATE TABLE medications_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO medications_new (id, name, createdAt, updatedAt)
                    SELECT id, name, createdAt, updatedAt
                    FROM medications
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE medications")
                db.execSQL("ALTER TABLE medications_new RENAME TO medications")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. MedicationIntakeを全削除（履歴の移行は複雑すぎるため）
                db.execSQL("DELETE FROM medication_intakes")

                // 2. MedicationTimeに一時テーブルを作成
                db.execSQL(
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
                db.execSQL(
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
                db.execSQL("DROP TABLE medication_times")

                // 5. 新しいテーブルをリネーム
                db.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")

                // 6. インデックスを作成
                db.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                db.execSQL("CREATE INDEX index_medication_times_medicationId_validFrom_validTo ON medication_times(medicationId, validFrom, validTo)")

                // 7. MedicationIntakeテーブルを再作成
                db.execSQL("DROP TABLE medication_intakes")
                db.execSQL(
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
                db.execSQL("CREATE INDEX index_medication_intakes_medicationId ON medication_intakes(medicationId)")
                db.execSQL("CREATE INDEX index_medication_intakes_scheduledDate ON medication_intakes(scheduledDate)")
                db.execSQL("CREATE UNIQUE INDEX index_medication_intakes_medicationId_scheduledDate_sequenceNumber ON medication_intakes(medicationId, scheduledDate, sequenceNumber)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // medication_timesテーブルにdoseカラムを追加（デフォルト値: 1.0）
                db.execSQL("ALTER TABLE medication_times ADD COLUMN dose REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // medication_configsテーブルにisAsNeededカラムを追加（デフォルト値: 0=false）
                db.execSQL("ALTER TABLE medication_configs ADD COLUMN isAsNeeded INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // medication_configsテーブルにdoseカラムを追加（デフォルト値: 1.0）
                db.execSQL("ALTER TABLE medication_configs ADD COLUMN dose REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // medication_configsテーブルにdoseUnitカラムを追加（nullable）
                db.execSQL("ALTER TABLE medication_configs ADD COLUMN doseUnit TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // daily_notesテーブルを作成
                db.execSQL(
                    """
                    CREATE TABLE daily_notes (
                        noteDate TEXT PRIMARY KEY NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // medication_intakesテーブルにisCanceledカラムを追加（デフォルト値: 0=false）
                db.execSQL("ALTER TABLE medication_intakes ADD COLUMN isCanceled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ========== medication_configs テーブルの変換 (Long → String) ==========

                // 1. 新しいテーブルを作成
                db.execSQL(
                    """
                    CREATE TABLE medication_configs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        cycleType TEXT NOT NULL,
                        cycleValue TEXT,
                        medicationStartDate TEXT NOT NULL,
                        medicationEndDate TEXT,
                        isAsNeeded INTEGER NOT NULL DEFAULT 0,
                        dose REAL NOT NULL DEFAULT 1.0,
                        doseUnit TEXT,
                        validFrom TEXT NOT NULL,
                        validTo TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 2. 既存データを変換してコピー（タイムスタンプ → yyyy-MM-dd）
                db.execSQL(
                    """
                    INSERT INTO medication_configs_new
                    SELECT
                        id,
                        medicationId,
                        cycleType,
                        cycleValue,
                        strftime('%Y-%m-%d', medicationStartDate / 1000, 'unixepoch', 'localtime'),
                        CASE WHEN medicationEndDate IS NOT NULL
                             THEN strftime('%Y-%m-%d', medicationEndDate / 1000, 'unixepoch', 'localtime')
                             ELSE NULL END,
                        isAsNeeded,
                        dose,
                        doseUnit,
                        strftime('%Y-%m-%d', validFrom / 1000, 'unixepoch', 'localtime'),
                        CASE WHEN validTo IS NOT NULL
                             THEN strftime('%Y-%m-%d', validTo / 1000, 'unixepoch', 'localtime')
                             ELSE NULL END,
                        createdAt,
                        updatedAt
                    FROM medication_configs
                    """.trimIndent()
                )

                // 3. 古いテーブルを削除
                db.execSQL("DROP TABLE medication_configs")

                // 4. 新しいテーブルをリネーム
                db.execSQL("ALTER TABLE medication_configs_new RENAME TO medication_configs")

                // 5. インデックスを再作成
                db.execSQL("CREATE INDEX index_medication_configs_medicationId ON medication_configs(medicationId)")
                db.execSQL("CREATE INDEX index_medication_configs_medicationId_validFrom_validTo ON medication_configs(medicationId, validFrom, validTo)")

                // ========== medication_times テーブルの変換 (Long → String) ==========

                // 1. 新しいテーブルを作成
                db.execSQL(
                    """
                    CREATE TABLE medication_times_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicationId INTEGER NOT NULL,
                        sequenceNumber INTEGER NOT NULL,
                        time TEXT NOT NULL,
                        dose REAL NOT NULL DEFAULT 1.0,
                        validFrom TEXT NOT NULL,
                        validTo TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // 2. 既存データを変換してコピー（タイムスタンプ → yyyy-MM-dd）
                db.execSQL(
                    """
                    INSERT INTO medication_times_new
                    SELECT
                        id,
                        medicationId,
                        sequenceNumber,
                        time,
                        dose,
                        strftime('%Y-%m-%d', validFrom / 1000, 'unixepoch', 'localtime'),
                        CASE WHEN validTo IS NOT NULL
                             THEN strftime('%Y-%m-%d', validTo / 1000, 'unixepoch', 'localtime')
                             ELSE NULL END,
                        createdAt,
                        updatedAt
                    FROM medication_times
                    """.trimIndent()
                )

                // 3. 古いテーブルを削除
                db.execSQL("DROP TABLE medication_times")

                // 4. 新しいテーブルをリネーム
                db.execSQL("ALTER TABLE medication_times_new RENAME TO medication_times")

                // 5. インデックスを再作成
                db.execSQL("CREATE INDEX index_medication_times_medicationId ON medication_times(medicationId)")
                db.execSQL("CREATE INDEX index_medication_times_medicationId_validFrom_validTo ON medication_times(medicationId, validFrom, validTo)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_shield_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
