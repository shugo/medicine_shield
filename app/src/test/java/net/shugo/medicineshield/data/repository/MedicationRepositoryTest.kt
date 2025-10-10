package net.shugo.medicineshield.data.repository

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class MedicationRepositoryTest {

    private lateinit var medicationDao: MedicationDao
    private lateinit var medicationTimeDao: MedicationTimeDao
    private lateinit var medicationIntakeDao: MedicationIntakeDao
    private lateinit var repository: MedicationRepository

    @Before
    fun setup() {
        medicationDao = mockk()
        medicationTimeDao = mockk()
        medicationIntakeDao = mockk()
        repository = MedicationRepository(medicationDao, medicationTimeDao, medicationIntakeDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== CRUD Operations ==========

    @Test
    fun `insertMedicationWithTimes should insert medication and times`() = runTest {
        // Given
        val medication = createSampleMedication(id = 0)
        val times = listOf("08:00", "20:00")
        val medicationId = 1L

        coEvery { medicationDao.insert(medication) } returns medicationId
        coEvery { medicationTimeDao.insertAll(any()) } just Runs

        // When
        val result = repository.insertMedicationWithTimes(medication, times)

        // Then
        assertEquals(medicationId, result)
        coVerify { medicationDao.insert(medication) }
        coVerify {
            medicationTimeDao.insertAll(match { list ->
                list.size == 2 &&
                list[0].medicationId == medicationId &&
                list[0].time == "08:00" &&
                list[1].medicationId == medicationId &&
                list[1].time == "20:00"
            })
        }
    }

    @Test
    fun `updateMedicationWithTimes should update medication and replace times`() = runTest {
        // Given
        val medication = createSampleMedication(id = 1)
        val newTimes = listOf("09:00", "21:00")

        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationTimeDao.deleteAllForMedication(medication.id) } just Runs
        coEvery { medicationTimeDao.insertAll(any()) } just Runs

        // When
        repository.updateMedicationWithTimes(medication, newTimes)

        // Then
        coVerify { medicationDao.update(match { it.id == medication.id }) }
        coVerify { medicationTimeDao.deleteAllForMedication(medication.id) }
        coVerify { medicationTimeDao.insertAll(match { it.size == 2 }) }
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
        val medication = createSampleMedication(
            id = 1,
            cycleType = CycleType.DAILY,
            startDate = parseDate("2025-10-01")
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, time = "20:00"),
            MedicationTime(id = 2, medicationId = 1, time = "08:00")
        )
        val medWithTimes = MedicationWithTimes(medication, times)
        val intake = MedicationIntake(
            id = 1,
            medicationId = 1,
            scheduledTime = "08:00",
            scheduledDate = dateString,
            takenAt = System.currentTimeMillis()
        )

        every { medicationDao.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes))
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
        val fridayMedication = createSampleMedication(
            id = 1,
            name = "Friday Med",
            cycleType = CycleType.WEEKLY,
            cycleValue = "5", // Friday only
            startDate = parseDate("2025-10-01")
        )
        val mondayMedication = createSampleMedication(
            id = 2,
            name = "Monday Med",
            cycleType = CycleType.WEEKLY,
            cycleValue = "1", // Monday only
            startDate = parseDate("2025-10-01")
        )

        val medWithTimes1 = MedicationWithTimes(
            fridayMedication,
            listOf(MedicationTime(id = 1, medicationId = 1, time = "08:00"))
        )
        val medWithTimes2 = MedicationWithTimes(
            mondayMedication,
            listOf(MedicationTime(id = 2, medicationId = 2, time = "08:00"))
        )

        every { medicationDao.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes1, medWithTimes2))
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
        val medication = createSampleMedication(
            id = 1,
            cycleType = CycleType.INTERVAL,
            cycleValue = "3",
            startDate = parseDate("2025-10-01")
        )
        val medWithTimes = MedicationWithTimes(
            medication,
            listOf(MedicationTime(id = 1, medicationId = 1, time = "08:00"))
        )

        every { medicationDao.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes))
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
        val medication = createSampleMedication(
            id = 1,
            cycleType = CycleType.DAILY,
            startDate = parseDate("2025-10-05"),
            endDate = parseDate("2025-10-15")
        )
        val medWithTimes = MedicationWithTimes(
            medication,
            listOf(MedicationTime(id = 1, medicationId = 1, time = "08:00"))
        )

        every { medicationDao.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes))
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
        name: String = "Sample Med",
        cycleType: CycleType = CycleType.DAILY,
        cycleValue: String? = null,
        startDate: Long = System.currentTimeMillis(),
        endDate: Long? = null
    ): Medication {
        return Medication(
            id = id,
            name = name,
            cycleType = cycleType,
            cycleValue = cycleValue,
            startDate = startDate,
            endDate = endDate,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseDate(dateString: String): Long {
        val calendar = Calendar.getInstance()
        val parts = dateString.split("-")
        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
