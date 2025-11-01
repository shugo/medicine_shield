package net.shugo.medicineshield.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.utils.DateUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Tests for MedicationRepository.cleanupOldData() using in-memory database
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MedicationRepositoryCleanupTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: MedicationRepository

    @Before
    fun setup() {
        // Create in-memory database
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        // Create repository with DAOs from in-memory database
        repository = MedicationRepository(
            medicationDao = database.medicationDao(),
            medicationTimeDao = database.medicationTimeDao(),
            medicationIntakeDao = database.medicationIntakeDao(),
            medicationConfigDao = database.medicationConfigDao(),
            dailyNoteDao = database.dailyNoteDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========== Helper Functions ==========

    private fun formatDate(daysFromNow: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysFromNow)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(calendar.time)
    }

    private fun parseDate(dateString: String): Long {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.parse(dateString)?.time ?: 0L
    }

    private suspend fun createMedication(
        name: String,
        startDateOffset: Int = -100,
        endDateOffset: Int? = null
    ): Long {
        val medication = Medication(name = name)
        val medicationId = database.medicationDao().insert(medication)

        val startDate = formatDate(startDateOffset)
        val endDate = endDateOffset?.let { formatDate(it) } ?: DateUtils.MAX_DATE

        // IMPORTANT: The current (most recent) config ALWAYS has validTo = MAX_DATE
        // This matches the actual implementation in MedicationRepository
        val config = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = startDate,
            medicationEndDate = endDate,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE  // Always MAX_DATE for current config
        )
        database.medicationConfigDao().insert(config)

        return medicationId
    }

    private suspend fun createIntake(
        medicationId: Long,
        dateOffset: Int,
        sequenceNumber: Int = 1,
        takenAt: String? = "08:00"
    ) {
        val intake = MedicationIntake(
            medicationId = medicationId,
            sequenceNumber = sequenceNumber,
            scheduledDate = formatDate(dateOffset),
            takenAt = takenAt
        )
        database.medicationIntakeDao().insert(intake)
    }

    private suspend fun createDailyNote(
        dateOffset: Int,
        content: String
    ) {
        val note = DailyNote(
            noteDate = formatDate(dateOffset),
            content = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.dailyNoteDao().insert(note)
    }

    // ========== Tests for cleanupOldData() ==========

    @Test
    fun `cleanupOldData should delete intake records older than retention period`() = runTest {
        // Given: Create medications and intake records
        val medicationId = createMedication("Test Med")

        // Create intakes at different dates
        createIntake(medicationId, -40)  // 40 days ago - should be deleted (40 > 30)
        createIntake(medicationId, -31)  // 31 days ago - should be deleted (31 > 30)
        createIntake(medicationId, -30)  // 30 days ago - should be KEPT (boundary, scheduledDate < cutoffDate)
        createIntake(medicationId, -29)  // 29 days ago - should be kept
        createIntake(medicationId, -20)  // 20 days ago - should be kept
        createIntake(medicationId, -10)  // 10 days ago - should be kept
        createIntake(medicationId, 0)    // today - should be kept

        // Verify all intakes exist
        val allIntakesBefore = database.medicationIntakeDao()
            .getIntakesByMedicationAndDate(medicationId, formatDate(-40))
        assertEquals(1, allIntakesBefore.size)

        // When: Clean up data with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Old intakes should be deleted
        val intake40DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-40), 1)
        assertNull("Intake from 40 days ago should be deleted", intake40DaysAgo)

        val intake31DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-31), 1)
        assertNull("Intake from 31 days ago should be deleted", intake31DaysAgo)

        val intake30DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-30), 1)
        assertNotNull("Intake from exactly 30 days ago should be KEPT (boundary, scheduledDate < cutoffDate)", intake30DaysAgo)

        val intake29DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-29), 1)
        assertNotNull("Intake from 29 days ago should be kept", intake29DaysAgo)

        val intake20DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-20), 1)
        assertNotNull("Intake from 20 days ago should be kept", intake20DaysAgo)

        val intakeToday = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(0), 1)
        assertNotNull("Today's intake should be kept", intakeToday)
    }

    @Test
    fun `cleanupOldData should delete daily notes older than retention period`() = runTest {
        // Given: Create daily notes at different dates
        createDailyNote(-50, "Note from 50 days ago")  // Should be deleted
        createDailyNote(-31, "Note from 31 days ago")  // Should be deleted
        createDailyNote(-30, "Note from 30 days ago")  // Should be KEPT (boundary, noteDate < cutoffDate)
        createDailyNote(-29, "Note from 29 days ago")  // Should be kept
        createDailyNote(-15, "Note from 15 days ago")  // Should be kept
        createDailyNote(0, "Note from today")          // Should be kept

        // When: Clean up data with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Old notes should be deleted
        val note50DaysAgo = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-50))
        assertNull("Note from 50 days ago should be deleted", note50DaysAgo)

        val note31DaysAgo = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-31))
        assertNull("Note from 31 days ago should be deleted", note31DaysAgo)

        val note30DaysAgo = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-30))
        assertNotNull("Note from exactly 30 days ago should be KEPT (boundary, noteDate < cutoffDate)", note30DaysAgo)

        val note29DaysAgo = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-29))
        assertNotNull("Note from 29 days ago should be kept", note29DaysAgo)

        val note15DaysAgo = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-15))
        assertNotNull("Note from 15 days ago should be kept", note15DaysAgo)

        val noteToday = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(0))
        assertNotNull("Today's note should be kept", noteToday)
    }

    @Test
    fun `cleanupOldData should delete medications ended before retention period`() = runTest {
        // Given: Create medications with different end dates
        // All have validTo = MAX_DATE, but different medicationEndDate
        val med1 = createMedication("Old Med 1", startDateOffset = -100, endDateOffset = -50)  // Ended 50 days ago
        val med2 = createMedication("Old Med 2", startDateOffset = -100, endDateOffset = -31)  // Ended 31 days ago
        val med3 = createMedication("Current Med", startDateOffset = -100, endDateOffset = -29) // Ended 29 days ago
        val med4 = createMedication("Active Med", startDateOffset = -100, endDateOffset = null)  // Still active

        // Verify all medications exist
        assertNotNull(database.medicationDao().getMedicationById(med1))
        assertNotNull(database.medicationDao().getMedicationById(med2))
        assertNotNull(database.medicationDao().getMedicationById(med3))
        assertNotNull(database.medicationDao().getMedicationById(med4))

        // When: Clean up data with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Medications ended more than 30 days ago should be deleted
        val deletedMed1 = database.medicationDao().getMedicationById(med1)
        assertNull("Medication ended 50 days ago should be deleted (MAX(medicationEndDate) < cutoffDate)", deletedMed1)

        val deletedMed2 = database.medicationDao().getMedicationById(med2)
        assertNull("Medication ended 31 days ago should be deleted (MAX(medicationEndDate) < cutoffDate)", deletedMed2)

        // Medications ended within or after retention period should be kept
        val keptMed3 = database.medicationDao().getMedicationById(med3)
        assertNotNull("Medication ended 29 days ago should be kept", keptMed3)

        val activeMed = database.medicationDao().getMedicationById(med4)
        assertNotNull("Active medication (MAX_DATE) should be kept", activeMed)
    }

    @Test
    fun `cleanupOldData should delete medications with only old configs`() = runTest {
        // Given: Create medication with only old configs (no current config with validTo = MAX_DATE)
        val medication = Medication(name = "Old Config Only Med")
        val oldMedId = database.medicationDao().insert(medication)

        // Create an old config that became invalid 40 days ago
        val oldConfig = MedicationConfig(
            medicationId = oldMedId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = formatDate(-50),
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = formatDate(-40)  // Config became invalid 40 days ago
        )
        database.medicationConfigDao().insert(oldConfig)

        createIntake(oldMedId, -50, sequenceNumber = 1)
        createIntake(oldMedId, -35, sequenceNumber = 1)

        // Create recent medication with intakes
        val recentMedId = createMedication("Recent Med", startDateOffset = -20, endDateOffset = null)
        createIntake(recentMedId, -10, sequenceNumber = 1)

        // When: Clean up data with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Old medication (with only old configs) should be deleted
        val deletedMed = database.medicationDao().getMedicationById(oldMedId)
        assertNull("Medication with only old configs should be deleted", deletedMed)

        // Recent medication should be kept
        val keptMed = database.medicationDao().getMedicationById(recentMedId)
        assertNotNull("Recent medication should be kept", keptMed)

        val recentIntake = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(recentMedId, formatDate(-10), 1)
        assertNotNull("Recent intake should be kept", recentIntake)
    }

    @Test
    fun `cleanupOldData with zero retention should delete all past data`() = runTest {
        // Given: Create data from yesterday and today
        val medicationId = createMedication("Test Med")
        createIntake(medicationId, -1)  // Yesterday
        createIntake(medicationId, 0)   // Today
        createDailyNote(-1, "Yesterday's note")
        createDailyNote(0, "Today's note")

        // When: Clean up with 0 days retention
        repository.cleanupOldData(retentionDays = 0)

        // Then: Yesterday's data should be deleted
        val yesterdayIntake = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-1), 1)
        assertNull("Yesterday's intake should be deleted", yesterdayIntake)

        val yesterdayNote = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-1))
        assertNull("Yesterday's note should be deleted", yesterdayNote)

        // Today's data should be kept
        val todayIntake = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(0), 1)
        assertNotNull("Today's intake should be kept", todayIntake)

        val todayNote = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(0))
        assertNotNull("Today's note should be kept", todayNote)
    }

    @Test
    fun `cleanupOldData should handle empty database gracefully`() = runTest {
        // Given: Empty database
        // (no data created)

        // When: Clean up data
        repository.cleanupOldData(retentionDays = 30)

        // Then: Should complete without errors
        // No assertion needed - test passes if no exception is thrown
    }

    @Test
    fun `cleanupOldData should keep all data when retention period is very large`() = runTest {
        // Given: Create old data
        val medicationId = createMedication("Old Med", startDateOffset = -500, endDateOffset = -400)
        createIntake(medicationId, -450)
        createDailyNote(-450, "Very old note")

        // When: Clean up with very large retention period
        repository.cleanupOldData(retentionDays = 1000)

        // Then: All data should be kept
        val oldMed = database.medicationDao().getMedicationById(medicationId)
        assertNotNull("Old medication should be kept with large retention", oldMed)

        val oldIntake = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(medicationId, formatDate(-450), 1)
        assertNotNull("Old intake should be kept with large retention", oldIntake)

        val oldNote = database.dailyNoteDao()
            .getNoteByDateSync(formatDate(-450))
        assertNotNull("Old note should be kept with large retention", oldNote)
    }

    @Test
    fun `cleanupOldData should delete only intakes older than cutoff date`() = runTest {
        // Given: Create multiple medications with intakes at boundary
        val med1 = createMedication("Med 1")
        val med2 = createMedication("Med 2")

        // Create intakes exactly at the cutoff date and one day before/after
        createIntake(med1, -31, sequenceNumber = 1)  // Should be deleted (31 > 30)
        createIntake(med1, -30, sequenceNumber = 2)  // Should be KEPT (boundary, scheduledDate < cutoffDate)
        createIntake(med2, -29, sequenceNumber = 1)  // Should be kept

        // When: Clean up with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Check boundary conditions
        val intake31DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(med1, formatDate(-31), 1)
        assertNull("Intake from 31 days ago should be deleted", intake31DaysAgo)

        val intake30DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(med1, formatDate(-30), 2)
        assertNotNull("Intake from exactly 30 days ago should be KEPT (boundary, scheduledDate < cutoffDate)", intake30DaysAgo)

        val intake29DaysAgo = database.medicationIntakeDao()
            .getIntakeByMedicationAndDateAndSeq(med2, formatDate(-29), 1)
        assertNotNull("Intake from 29 days ago should be kept", intake29DaysAgo)
    }

    @Test
    fun `cleanupOldData should handle medications with multiple configs correctly`() = runTest {
        // Given: Create medication that was restarted (has multiple configs)
        val medication = Medication(name = "Restarted Med")
        val medicationId = database.medicationDao().insert(medication)

        // Old config: ended 50 days ago
        val oldConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = formatDate(-50),
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = formatDate(-40)
        )
        database.medicationConfigDao().insert(oldConfig)

        // New config: currently active
        val newConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-10),
            medicationEndDate = DateUtils.MAX_DATE,
            isAsNeeded = false,
            dose = 2.0,
            doseUnit = "tablets",
            validFrom = formatDate(-10),
            validTo = DateUtils.MAX_DATE
        )
        database.medicationConfigDao().insert(newConfig)

        // When: Clean up with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Medication should NOT be deleted because it has active config
        val med = database.medicationDao().getMedicationById(medicationId)
        assertNotNull("Medication with active config should be kept even if old config ended", med)
    }

    @Test
    fun `cleanupOldData should delete medication that was initially MAX_DATE then ended with all old configs`() = runTest {
        // Given: Create medication that initially had no end date, then was later ended,
        // but only has old configs (no current config with validTo = MAX_DATE)
        val medication = Medication(name = "Initially Endless Med")
        val medicationId = database.medicationDao().insert(medication)

        // First config: no end date (MAX_DATE)
        val initialConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = DateUtils.MAX_DATE,  // Initially no end date
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = formatDate(-60)  // Config became invalid 60 days ago
        )
        database.medicationConfigDao().insert(initialConfig)

        // Second config: end date set to 50 days ago
        val endedConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = formatDate(-50),  // Ended 50 days ago
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = formatDate(-60),
            validTo = formatDate(-50)  // Config became invalid 50 days ago (when medication ended)
        )
        database.medicationConfigDao().insert(endedConfig)

        // When: Clean up with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Medication should be deleted because all configs have validTo older than retention period
        val med = database.medicationDao().getMedicationById(medicationId)
        assertNull("Medication with all configs having old validTo should be deleted", med)
    }

    @Test
    fun `cleanupOldData should delete medication that was initially MAX_DATE then ended long ago`() = runTest {
        // Given: Create medication that initially had no end date, then was later ended,
        // with a CURRENT config (validTo = MAX_DATE) but ended before retention period
        val medication = Medication(name = "Ended But Current Config Med")
        val medicationId = database.medicationDao().insert(medication)

        // First config: no end date (MAX_DATE)
        val initialConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = DateUtils.MAX_DATE,  // Initially no end date
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = formatDate(-60)  // Config became invalid 60 days ago
        )
        database.medicationConfigDao().insert(initialConfig)

        // Second config: end date set to 50 days ago, validTo = MAX_DATE (current config)
        // This matches actual implementation behavior
        val endedConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = formatDate(-50),  // Ended 50 days ago
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = formatDate(-60),
            validTo = DateUtils.MAX_DATE  // CURRENT config (validTo = MAX_DATE)
        )
        database.medicationConfigDao().insert(endedConfig)

        // When: Clean up with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Medication should be DELETED because MAX(medicationEndDate) < cutoffDate
        // Even though validTo = MAX_DATE, the medication ended 50 days ago (before retention period)
        val med = database.medicationDao().getMedicationById(medicationId)
        assertNull("Medication ended before retention period should be deleted (MAX(medicationEndDate) < cutoffDate)", med)
    }

    @Test
    fun `cleanupOldData should respect validTo boundary condition with equals`() = runTest {
        // Given: Create medication with config that has validTo exactly at cutoff date
        val medication = Medication(name = "Boundary Med")
        val medicationId = database.medicationDao().insert(medication)

        // Config with validTo exactly 30 days ago (boundary condition)
        // This medication has NO current config (validTo != MAX_DATE)
        val boundaryConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = formatDate(-30),
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = formatDate(-30)  // validTo exactly at cutoff (should be deleted with <=)
        )
        database.medicationConfigDao().insert(boundaryConfig)

        // When: Clean up with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Config should be deleted (validTo <= cutoffDate), and medication should be deleted
        val configs = database.medicationConfigDao().getCurrentConfigForMedication(medicationId)
        assertNull("Config with validTo at cutoff date should be deleted", configs)

        val med = database.medicationDao().getMedicationById(medicationId)
        assertNull("Medication should be deleted when its only config has validTo <= cutoffDate", med)
    }

    @Test
    fun `cleanupOldData should keep medication with validTo one day after cutoff`() = runTest {
        // Given: Create medication with config that has validTo one day after cutoff
        val medication = Medication(name = "Recent Med")
        val medicationId = database.medicationDao().insert(medication)

        // Config with validTo 29 days ago (one day after 30-day cutoff)
        // This is NOT a current config (validTo != MAX_DATE)
        val recentConfig = MedicationConfig(
            medicationId = medicationId,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = formatDate(-100),
            medicationEndDate = DateUtils.MAX_DATE,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            validFrom = DateUtils.MIN_DATE,
            validTo = formatDate(-29)  // validTo one day after cutoff (should be kept)
        )
        database.medicationConfigDao().insert(recentConfig)

        // When: Clean up with 30 days retention
        repository.cleanupOldData(retentionDays = 30)

        // Then: Config and medication should be kept
        val configs = database.medicationConfigDao().getCurrentConfigForMedication(medicationId)
        assertNull("Config is not current because validTo is in the past", configs)

        // But medication should still exist since config was not deleted
        val med = database.medicationDao().getMedicationById(medicationId)
        assertNotNull("Medication should be kept when its only config has validTo > cutoffDate", med)
    }
}
