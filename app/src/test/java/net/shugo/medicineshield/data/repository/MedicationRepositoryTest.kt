package net.shugo.medicineshield.data.repository

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.dao.MedicationConfigDao
import net.shugo.medicineshield.data.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class MedicationRepositoryTest {

    private lateinit var medicationDao: MedicationDao
    private lateinit var medicationTimeDao: MedicationTimeDao
    private lateinit var medicationIntakeDao: MedicationIntakeDao
    private lateinit var medicationConfigDao: MedicationConfigDao
    private lateinit var repository: MedicationRepository

    @Before
    fun setup() {
        medicationDao = mockk()
        medicationTimeDao = mockk()
        medicationIntakeDao = mockk()
        medicationConfigDao = mockk()
        repository = MedicationRepository(medicationDao, medicationTimeDao, medicationIntakeDao, medicationConfigDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== CRUD Operations ==========

    @Test
    fun `insertMedicationWithTimes should insert medication and times with valid dates`() = runTest {
        // Given
        val name = "Sample Med"
        val cycleType = CycleType.DAILY
        val cycleValue: String? = null
        val startDate = parseDate("2025-10-01")
        val endDate: Long? = null
        val times = listOf("08:00", "20:00")
        val medicationId = 1L

        coEvery { medicationDao.insert(any()) } returns medicationId
        coEvery { medicationConfigDao.insert(any()) } returns 1L
        coEvery { medicationTimeDao.insertAll(any()) } just Runs

        // When
        val result = repository.insertMedicationWithTimes(name, cycleType, cycleValue, startDate, endDate, times)

        // Then
        assertEquals(medicationId, result)
        coVerify { medicationDao.insert(match { it.name == name }) }
        coVerify { medicationConfigDao.insert(match {
            it.medicationId == medicationId &&
            it.cycleType == cycleType &&
            it.medicationStartDate == startDate &&
            it.validTo == null
        }) }
        coVerify {
            medicationTimeDao.insertAll(match { list ->
                list.size == 2 &&
                list[0].medicationId == medicationId &&
                list[0].time == "08:00" &&
                list[0].validTo == null &&
                list[1].medicationId == medicationId &&
                list[1].time == "20:00" &&
                list[1].validTo == null
            })
        }
    }

    @Test
    fun `updateMedicationWithTimes should set validTo for removed times`() = runTest {
        // Given
        val medicationId = 1L
        val name = "Updated Med"
        val cycleType = CycleType.DAILY
        val cycleValue: String? = null
        val startDate = parseDate("2025-10-01")
        val endDate: Long? = null
        val existingConfig = MedicationConfig(
            id = 1, medicationId = medicationId, cycleType = CycleType.DAILY,
            cycleValue = null, medicationStartDate = startDate, medicationEndDate = null,
            validFrom = 0, validTo = null
        )
        val existingTimes = listOf(
            MedicationTime(id = 1, medicationId = medicationId, time = "08:00", validFrom = 0, validTo = null),
            MedicationTime(id = 2, medicationId = medicationId, time = "20:00", validFrom = 0, validTo = null)
        )
        val newTimes = listOf("09:00", "21:00")

        coEvery { medicationDao.getMedicationById(medicationId) } returns Medication(id = medicationId, name = "Old Med")
        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationConfigDao.getCurrentConfigForMedication(medicationId) } returns existingConfig
        coEvery { medicationTimeDao.getCurrentTimesForMedication(medicationId) } returns existingTimes
        coEvery { medicationTimeDao.update(any()) } just Runs
        coEvery { medicationTimeDao.insertAll(any()) } just Runs

        // When
        repository.updateMedicationWithTimes(medicationId, name, cycleType, cycleValue, startDate, endDate, newTimes)

        // Then
        coVerify { medicationDao.update(match { it.id == medicationId && it.name == name }) }
        coVerify { medicationTimeDao.getCurrentTimesForMedication(medicationId) }
        coVerify(exactly = 2) { medicationTimeDao.update(match { it.validTo != null }) } // Set validTo for 08:00 and 20:00
        coVerify { medicationTimeDao.insertAll(match { it.size == 2 }) } // Insert 09:00 and 21:00
    }

    @Test
    fun `deleteMedication should call dao delete`() = runTest {
        // Given
        val medication = createSampleMedication(id = 1)
        coEvery { medicationDao.delete(medication) } just Runs

        // When
        repository.deleteMedication(medication)

        // Then
        coVerify { medicationDao.delete(medication) }
    }

    @Test
    fun `deleteMedicationById should call dao deleteById`() = runTest {
        // Given
        val medicationId = 1L
        coEvery { medicationDao.deleteById(medicationId) } just Runs

        // When
        repository.deleteMedicationById(medicationId)

        // Then
        coVerify { medicationDao.deleteById(medicationId) }
    }

    // ========== Daily Medication Functions ==========

    @Test
    fun `getMedications should return sorted daily medication items for DAILY cycle`() = runTest {
        // Given
        val dateString = "2025-10-10"
        val medication = createSampleMedication(id = 1, name = "Sample Med")
        val config = createSampleConfig(
            id = 1,
            medicationId = 1,
            cycleType = CycleType.DAILY,
            medicationStartDate = parseDate("2025-10-01"),
            validFrom = 0,
            validTo = null
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "20:00", validFrom = 0, validTo = null),
            MedicationTime(id = 2, medicationId = 1, time = "08:00", validFrom = 0, validTo = null)
        )
        val intake = MedicationIntake(
            id = 1,
            medicationId = 1,
            scheduledTime = "08:00",
            scheduledDate = dateString,
            takenAt = System.currentTimeMillis()
        )

        every { medicationDao.getAllMedications() } returns flowOf(listOf(medication))
        every { medicationTimeDao.getAllTimesFlow() } returns flowOf(times)
        every { medicationConfigDao.getAllConfigsFlow() } returns flowOf(listOf(config))
        every { medicationIntakeDao.getIntakesByDate(dateString) } returns flowOf(listOf(intake))

        // When & Then
        repository.getMedications(dateString).test {
            val items = awaitItem()
            assertEquals(2, items.size)
            // Sorted by time
            assertEquals("08:00", items[0].scheduledTime)
            assertTrue(items[0].isTaken)
            assertNotNull(items[0].takenAt)
            assertEquals("20:00", items[1].scheduledTime)
            assertFalse(items[1].isTaken)
            assertNull(items[1].takenAt)
            awaitComplete()
        }
    }

    @Test
    fun `getMedications should filter by WEEKLY cycle correctly`() = runTest {
        // Given - 2025-10-10 is Friday (day 5)
        val dateString = "2025-10-10"
        val fridayMedication = createSampleMedication(id = 1, name = "Friday Med")
        val mondayMedication = createSampleMedication(id = 2, name = "Monday Med")
        val fridayConfig = createSampleConfig(
            id = 1,
            medicationId = 1,
            cycleType = CycleType.WEEKLY,
            cycleValue = "5", // Friday only
            medicationStartDate = parseDate("2025-10-01"),
            validFrom = 0,
            validTo = null
        )
        val mondayConfig = createSampleConfig(
            id = 2,
            medicationId = 2,
            cycleType = CycleType.WEEKLY,
            cycleValue = "1", // Monday only
            medicationStartDate = parseDate("2025-10-01"),
            validFrom = 0,
            validTo = null
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "08:00", validFrom = 0, validTo = null),
            MedicationTime(id = 2, medicationId = 2, time = "08:00", validFrom = 0, validTo = null)
        )

        every { medicationDao.getAllMedications() } returns flowOf(listOf(fridayMedication, mondayMedication))
        every { medicationTimeDao.getAllTimesFlow() } returns flowOf(times)
        every { medicationConfigDao.getAllConfigsFlow() } returns flowOf(listOf(fridayConfig, mondayConfig))
        every { medicationIntakeDao.getIntakesByDate(dateString) } returns flowOf(emptyList())

        // When & Then
        repository.getMedications(dateString).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("Friday Med", items[0].medicationName)
            awaitComplete()
        }
    }

    @Test
    fun `getMedications should filter by INTERVAL cycle correctly`() = runTest {
        // Given - Medication starts on 2025-10-01, interval is 3 days
        // Should appear on: 10-01 (day 0), 10-04 (day 3), 10-07 (day 6), 10-10 (day 9)
        val medication = createSampleMedication(id = 1, name = "Interval Med")
        val config = createSampleConfig(
            id = 1,
            medicationId = 1,
            cycleType = CycleType.INTERVAL,
            cycleValue = "3",
            medicationStartDate = parseDate("2025-10-01"),
            validFrom = 0,
            validTo = null
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "08:00", validFrom = 0, validTo = null)
        )

        every { medicationDao.getAllMedications() } returns flowOf(listOf(medication))
        every { medicationTimeDao.getAllTimesFlow() } returns flowOf(times)
        every { medicationConfigDao.getAllConfigsFlow() } returns flowOf(listOf(config))
        every { medicationIntakeDao.getIntakesByDate(any()) } returns flowOf(emptyList())

        // Test day 9 (should appear)
        repository.getMedications("2025-10-10").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            awaitComplete()
        }

        // Test day 10 (should NOT appear)
        repository.getMedications("2025-10-11").test {
            val items = awaitItem()
            assertEquals(0, items.size)
            awaitComplete()
        }
    }

    @Test
    fun `getMedications should respect start and end dates`() = runTest {
        // Given
        val medication = createSampleMedication(id = 1, name = "Date Range Med")
        val config = createSampleConfig(
            id = 1,
            medicationId = 1,
            cycleType = CycleType.DAILY,
            medicationStartDate = parseDate("2025-10-05"),
            medicationEndDate = parseDate("2025-10-15"),
            validFrom = 0,
            validTo = null
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "08:00", validFrom = 0, validTo = null)
        )

        every { medicationDao.getAllMedications() } returns flowOf(listOf(medication))
        every { medicationTimeDao.getAllTimesFlow() } returns flowOf(times)
        every { medicationConfigDao.getAllConfigsFlow() } returns flowOf(listOf(config))
        every { medicationIntakeDao.getIntakesByDate(any()) } returns flowOf(emptyList())

        // Before start date
        repository.getMedications("2025-10-04").test {
            assertEquals(0, awaitItem().size)
            awaitComplete()
        }

        // Within range
        repository.getMedications("2025-10-10").test {
            assertEquals(1, awaitItem().size)
            awaitComplete()
        }

        // After end date
        repository.getMedications("2025-10-16").test {
            assertEquals(0, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun `getMedications should show past times even after deletion`() = runTest {
        // Given: Yesterday was 2025-10-09, today is 2025-10-10
        val yesterday = "2025-10-09"
        val medication = createSampleMedication(id = 1, name = "History Med")
        val config = createSampleConfig(
            id = 1,
            medicationId = 1,
            cycleType = CycleType.DAILY,
            medicationStartDate = parseDate("2025-10-01"),
            validFrom = 0,
            validTo = null
        )
        // Times that were valid yesterday (including 08:00 which was deleted today)
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "08:00", validFrom = 0, validTo = parseDate("2025-10-10")),
            MedicationTime(id = 2, medicationId = 1, time = "20:00", validFrom = 0, validTo = null)
        )

        every { medicationDao.getAllMedications() } returns flowOf(listOf(medication))
        every { medicationTimeDao.getAllTimesFlow() } returns flowOf(times)
        every { medicationConfigDao.getAllConfigsFlow() } returns flowOf(listOf(config))
        every { medicationIntakeDao.getIntakesByDate(yesterday) } returns flowOf(emptyList())

        // When & Then
        repository.getMedications(yesterday).test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertEquals("08:00", items[0].scheduledTime)
            assertEquals("20:00", items[1].scheduledTime)
            awaitComplete()
        }
    }

    @Test
    fun `getMedications should not show newly added time in past dates`() = runTest {
        // Given: Yesterday was 2025-10-09, 18:00 was added today (2025-10-10)
        val yesterday = "2025-10-09"
        val medication = createSampleMedication(id = 1, name = "New Time Med")
        val config = createSampleConfig(
            id = 1,
            medicationId = 1,
            cycleType = CycleType.DAILY,
            medicationStartDate = parseDate("2025-10-01"),
            validFrom = 0,
            validTo = null
        )
        // Only 08:00 was valid yesterday (18:00 starts from 2025-10-10)
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "08:00", validFrom = 0, validTo = null),
            MedicationTime(id = 2, medicationId = 1, time = "18:00", validFrom = parseDate("2025-10-10"), validTo = null)
        )

        every { medicationDao.getAllMedications() } returns flowOf(listOf(medication))
        every { medicationTimeDao.getAllTimesFlow() } returns flowOf(times)
        every { medicationConfigDao.getAllConfigsFlow() } returns flowOf(listOf(config))
        every { medicationIntakeDao.getIntakesByDate(yesterday) } returns flowOf(emptyList())

        // When & Then
        repository.getMedications(yesterday).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("08:00", items[0].scheduledTime)
            awaitComplete()
        }
    }

    // ========== Intake Status Update ==========

    @Test
    fun `updateIntakeStatus with isTaken true should create new intake when not exists`() = runTest {
        // Given
        val medicationId = 1L
        val scheduledTime = "08:00"
        val scheduledDate = "2025-10-10"

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, scheduledTime)
        } returns null
        coEvery { medicationIntakeDao.insert(any()) } returns 1L

        // When
        repository.updateIntakeStatus(medicationId, scheduledTime, true, scheduledDate)

        // Then
        coVerify {
            medicationIntakeDao.insert(match {
                it.medicationId == medicationId &&
                it.scheduledTime == scheduledTime &&
                it.scheduledDate == scheduledDate &&
                it.takenAt != null
            })
        }
    }

    @Test
    fun `updateIntakeStatus with isTaken true should update existing intake`() = runTest {
        // Given
        val medicationId = 1L
        val scheduledTime = "08:00"
        val scheduledDate = "2025-10-10"
        val existingIntake = MedicationIntake(
            id = 1,
            medicationId = medicationId,
            scheduledTime = scheduledTime,
            scheduledDate = scheduledDate,
            takenAt = null
        )

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, scheduledTime)
        } returns existingIntake
        coEvery { medicationIntakeDao.update(any()) } just Runs

        // When
        repository.updateIntakeStatus(medicationId, scheduledTime, true, scheduledDate)

        // Then
        coVerify {
            medicationIntakeDao.update(match {
                it.id == existingIntake.id &&
                it.takenAt != null
            })
        }
    }

    @Test
    fun `updateIntakeStatus with isTaken false should set takenAt to null`() = runTest {
        // Given
        val medicationId = 1L
        val scheduledTime = "08:00"
        val scheduledDate = "2025-10-10"
        val existingIntake = MedicationIntake(
            id = 1,
            medicationId = medicationId,
            scheduledTime = scheduledTime,
            scheduledDate = scheduledDate,
            takenAt = System.currentTimeMillis()
        )

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, scheduledTime)
        } returns existingIntake
        coEvery { medicationIntakeDao.update(any()) } just Runs

        // When
        repository.updateIntakeStatus(medicationId, scheduledTime, false, scheduledDate)

        // Then
        coVerify {
            medicationIntakeDao.update(match {
                it.id == existingIntake.id &&
                it.takenAt == null
            })
        }
    }

    @Test
    fun `updateIntakeStatus with isTaken false should do nothing when intake not exists`() = runTest {
        // Given
        val medicationId = 1L
        val scheduledTime = "08:00"
        val scheduledDate = "2025-10-10"

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, scheduledTime)
        } returns null

        // When
        repository.updateIntakeStatus(medicationId, scheduledTime, false, scheduledDate)

        // Then
        coVerify(exactly = 0) { medicationIntakeDao.insert(any()) }
        coVerify(exactly = 0) { medicationIntakeDao.update(any()) }
    }

    // ========== Cleanup Old Intakes ==========

    @Test
    fun `cleanupOldIntakes should delete intakes older than specified days`() = runTest {
        // Given
        val daysToKeep = 30
        coEvery { medicationIntakeDao.deleteOldIntakes(any()) } just Runs

        // When
        repository.cleanupOldIntakes(daysToKeep)

        // Then
        coVerify { medicationIntakeDao.deleteOldIntakes(match { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }) }
    }

    // ========== Helper Functions ==========

    private fun createSampleMedication(
        id: Long,
        name: String = "Sample Med"
    ): Medication {
        return Medication(
            id = id,
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createSampleConfig(
        id: Long,
        medicationId: Long,
        cycleType: CycleType = CycleType.DAILY,
        cycleValue: String? = null,
        medicationStartDate: Long = System.currentTimeMillis(),
        medicationEndDate: Long? = null,
        validFrom: Long = 0,
        validTo: Long? = null
    ): MedicationConfig {
        return MedicationConfig(
            id = id,
            medicationId = medicationId,
            cycleType = cycleType,
            cycleValue = cycleValue,
            medicationStartDate = medicationStartDate,
            medicationEndDate = medicationEndDate,
            validFrom = validFrom,
            validTo = validTo,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseDate(dateString: String): Long {
        val localDate = LocalDate.parse(dateString)
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
