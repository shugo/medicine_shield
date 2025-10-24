package net.shugo.medicineshield.data.repository

import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.shugo.medicineshield.data.dao.DailyNoteDao
import net.shugo.medicineshield.data.dao.MedicationConfigDao
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.data.model.MedicationTime
import net.shugo.medicineshield.utils.DateUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MedicationRepositoryTest {

    private lateinit var medicationDao: MedicationDao
    private lateinit var medicationTimeDao: MedicationTimeDao
    private lateinit var medicationIntakeDao: MedicationIntakeDao
    private lateinit var medicationConfigDao: MedicationConfigDao
    private lateinit var dailyNoteDao: DailyNoteDao
    private lateinit var repository: MedicationRepository

    @Before
    fun setup() {
        medicationDao = mockk()
        medicationTimeDao = mockk()
        medicationIntakeDao = mockk()
        medicationConfigDao = mockk()
        dailyNoteDao = mockk()
        repository = MedicationRepository(medicationDao, medicationTimeDao, medicationIntakeDao, medicationConfigDao, dailyNoteDao)
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
        val startDateString = "2025-10-01"
        val startDate = parseDate(startDateString)
        val endDate: Long? = null
        val times = listOf("08:00" to 1.0, "20:00" to 1.0)
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
            it.medicationStartDate == startDateString &&
            it.validTo == DateUtils.MAX_DATE
        }) }
        coVerify {
            medicationTimeDao.insertAll(match { list ->
                list.size == 2 &&
                list[0].medicationId == medicationId &&
                list[0].sequenceNumber == 1 &&
                list[0].time == "08:00" &&
                list[0].validTo == DateUtils.MAX_DATE &&
                list[1].medicationId == medicationId &&
                list[1].sequenceNumber == 2 &&
                list[1].time == "20:00" &&
                list[1].validTo == DateUtils.MAX_DATE
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
        val startDateString = "2025-10-01"
        val startDate = parseDate(startDateString)
        val endDate: Long? = null
        val existingConfig = MedicationConfig(
            id = 1, medicationId = medicationId, cycleType = CycleType.DAILY,
            cycleValue = null, medicationStartDate = startDateString, medicationEndDate = DateUtils.MAX_DATE,
            validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE
        )
        val existingTimes = listOf(
            MedicationTime(id = 1, medicationId = medicationId, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE),
            MedicationTime(id = 2, medicationId = medicationId, sequenceNumber = 2, time = "20:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
        )
        val newTimes = listOf(Triple(1, "09:00", 1.0), Triple(2, "21:00", 1.0))

        coEvery { medicationDao.getMedicationById(medicationId) } returns Medication(id = medicationId, name = "Old Med")
        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationConfigDao.getCurrentConfigForMedication(medicationId) } returns existingConfig
        coEvery { medicationConfigDao.insert(any()) } returns 1L
        coEvery { medicationConfigDao.update(any()) } just Runs
        coEvery { medicationTimeDao.getCurrentTimesForMedication(medicationId) } returns existingTimes
        coEvery { medicationTimeDao.update(any()) } just Runs
        coEvery { medicationTimeDao.insert(any()) } returns 1L
        coEvery { medicationTimeDao.insertAll(any()) } just Runs

        // When
        repository.updateMedicationWithTimes(medicationId, name, cycleType, cycleValue, startDate, endDate, newTimes)

        // Then
        coVerify { medicationDao.update(match { it.id == medicationId && it.name == name }) }
        coVerify { medicationTimeDao.getCurrentTimesForMedication(medicationId) }
        coVerify(exactly = 2) { medicationTimeDao.update(match { it.validTo != DateUtils.MAX_DATE }) } // Set validTo for 08:00 and 20:00
        coVerify(exactly = 2) { medicationTimeDao.insert(any()) } // Insert 09:00 and 21:00
    }

    @Test
    fun `updateMedicationWithTimes should update existing config not create duplicate when DAO returns valid config`() = runTest {
        // Given - このテストは、DAOが正しく現在有効なconfigを取得できた場合の動作を確認する
        // バグがあると、getCurrentConfigForMedicationがnullを返し、validFrom=MIN_DATEの新規configが作成されてしまう
        val medicationId = 1L
        val startDate = parseDate("2025-10-01")

        val medication = Medication(id = medicationId, name = "Old Med")
        val existingConfig = MedicationConfig(
            id = 1, medicationId = medicationId, cycleType = CycleType.DAILY,
            cycleValue = null, medicationStartDate = "2025-10-01", medicationEndDate = DateUtils.MAX_DATE,
            validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE
        )

        coEvery { medicationDao.getMedicationById(medicationId) } returns medication
        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationTimeDao.getCurrentTimesForMedication(medicationId) } returns emptyList()
        coEvery { medicationConfigDao.getCurrentConfigForMedication(medicationId) } returns existingConfig
        coEvery { medicationConfigDao.insert(any()) } returns 2L
        coEvery { medicationConfigDao.update(any()) } just Runs

        // When - cycleTypeを変更（DAILY -> WEEKLY）
        repository.updateMedicationWithTimes(
            medicationId, "Med Updated", CycleType.WEEKLY, "1,2,3", startDate, null, emptyList()
        )

        // Then - 既存のconfigを無効化し、新しいconfigを作成する（今日からvalidFrom）
        coVerify(exactly = 1) { medicationConfigDao.update(match { it.validTo != DateUtils.MAX_DATE }) }
        coVerify(exactly = 1) { medicationConfigDao.insert(match { it.validFrom != DateUtils.MIN_DATE }) }
    }

    @Test
    fun `updateMedicationWithTimes should create config with MIN_DATE validFrom when DAO returns null config`() = runTest {
        // Given - このテストは、getCurrentConfigForMedicationがnullを返した場合の動作を確認する
        // これは正常なケース（初回の設定時）だが、バグがあるとDAOが不正にnullを返してしまう
        val medicationId = 1L
        val cycleType = CycleType.DAILY
        val startDate = parseDate("2025-10-01")

        val medication = Medication(id = medicationId, name = "Med")

        coEvery { medicationDao.getMedicationById(medicationId) } returns medication
        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationTimeDao.getCurrentTimesForMedication(medicationId) } returns emptyList()
        coEvery { medicationConfigDao.getCurrentConfigForMedication(medicationId) } returns null
        coEvery { medicationConfigDao.insert(any()) } returns 1L

        // When
        repository.updateMedicationWithTimes(
            medicationId, "Med", cycleType, null, startDate, null, emptyList()
        )

        // Then - 新規作成（validFrom = MIN_DATE）
        coVerify(exactly = 0) { medicationConfigDao.update(any()) }
        coVerify(exactly = 1) { medicationConfigDao.insert(match { it.validFrom == DateUtils.MIN_DATE }) }
    }

    @Test
    fun `updateMedicationWithTimes should update existing times not create duplicates when DAO returns valid times`() = runTest {
        // Given - このテストは、DAOが正しく現在有効なtimesを取得できた場合の動作を確認する
        // バグがあると、getCurrentTimesForMedicationが空リストを返し、timeが追加され続ける
        val medicationId = 1L
        val cycleType = CycleType.DAILY
        val startDate = parseDate("2025-10-01")

        val medication = Medication(id = medicationId, name = "Med")
        val config = MedicationConfig(
            id = 1, medicationId = medicationId, cycleType = CycleType.DAILY,
            cycleValue = null, medicationStartDate = "2025-10-01", medicationEndDate = DateUtils.MAX_DATE,
            validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE
        )

        val existingTime = MedicationTime(
            id = 1, medicationId = medicationId, sequenceNumber = 1,
            time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE
        )

        coEvery { medicationDao.getMedicationById(medicationId) } returns medication
        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationConfigDao.getCurrentConfigForMedication(medicationId) } returns config
        coEvery { medicationTimeDao.getCurrentTimesForMedication(medicationId) } returns listOf(existingTime)
        coEvery { medicationTimeDao.update(any()) } just Runs
        coEvery { medicationTimeDao.insert(any()) } returns 2L
        coEvery { medicationIntakeDao.getIntakeByMedicationAndDateTime(any(), any(), any()) } returns null

        // When - 時刻を変更（08:00 -> 09:00）
        repository.updateMedicationWithTimes(
            medicationId, "Med", cycleType, null, startDate, null,
            listOf(Triple(1, "09:00", 1.0))
        )

        // Then - 古いtimeが無効化され、新しいtimeが作成される（今日からvalidFrom）
        coVerify(exactly = 1) { medicationTimeDao.update(match { it.id == 1L && it.validTo != DateUtils.MAX_DATE }) }
        coVerify(exactly = 1) { medicationTimeDao.insert(match { it.validFrom != DateUtils.MIN_DATE }) }
    }

    @Test
    fun `updateMedicationWithTimes should create new times with today validFrom when DAO returns empty times`() = runTest {
        // Given - このテストは、getCurrentTimesForMedicationが空リストを返した場合の動作を確認する
        // バグがあるとDAOが不正に空リストを返してしまい、既存のtimeが無効化されない
        val medicationId = 1L
        val cycleType = CycleType.DAILY
        val startDate = parseDate("2025-10-01")

        val medication = Medication(id = medicationId, name = "Med")
        val config = MedicationConfig(
            id = 1, medicationId = medicationId, cycleType = CycleType.DAILY,
            cycleValue = null, medicationStartDate = "2025-10-01", medicationEndDate = DateUtils.MAX_DATE,
            validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE
        )

        coEvery { medicationDao.getMedicationById(medicationId) } returns medication
        coEvery { medicationDao.update(any()) } just Runs
        coEvery { medicationConfigDao.getCurrentConfigForMedication(medicationId) } returns config
        coEvery { medicationTimeDao.getCurrentTimesForMedication(medicationId) } returns emptyList()
        coEvery { medicationTimeDao.insert(any()) } returns 1L

        // When - 新しい時刻を追加
        repository.updateMedicationWithTimes(
            medicationId, "Med", cycleType, null, startDate, null,
            listOf(Triple(1, "08:00", 1.0))
        )

        // Then - 新規作成（validFrom = 今日）
        coVerify(exactly = 0) { medicationTimeDao.update(any()) }
        coVerify(exactly = 1) { medicationTimeDao.insert(match { it.validFrom != DateUtils.MIN_DATE }) }
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
            medicationStartDate = "2025-10-01",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "20:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE),
            MedicationTime(id = 2, medicationId = 1, sequenceNumber = 2, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
        )
        val intake = MedicationIntake(
            id = 1,
            medicationId = 1,
            sequenceNumber = 2,
            scheduledDate = dateString,
            takenAt = "09:30"
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
            assertEquals(MedicationIntakeStatus.TAKEN, items[0].status)
            assertNotNull(items[0].takenAt)
            assertEquals("20:00", items[1].scheduledTime)
            assertEquals(MedicationIntakeStatus.UNCHECKED, items[1].status)
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
            medicationStartDate = "2025-10-01",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        val mondayConfig = createSampleConfig(
            id = 2,
            medicationId = 2,
            cycleType = CycleType.WEEKLY,
            cycleValue = "1", // Monday only
            medicationStartDate = "2025-10-01",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE),
            MedicationTime(id = 2, medicationId = 2, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
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
            medicationStartDate = "2025-10-01",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
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
            medicationStartDate = "2025-10-05",
            medicationEndDate = "2025-10-15",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
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
    fun `getMedications should respect start and end dates for as-needed medications`() = runTest {
        // Given
        val medication = createSampleMedication(id = 1, name = "Date Range Med")
        val config = createSampleConfig(
            id = 1,
            medicationId = 1,
            isAsNeeded = true,
            medicationStartDate = "2025-10-05",
            medicationEndDate = "2025-10-15",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
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
            medicationStartDate = "2025-10-01",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        // Times that were valid yesterday (including 08:00 which was deleted today)
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = "2025-10-10"),
            MedicationTime(id = 2, medicationId = 1, sequenceNumber = 2, time = "20:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE)
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
            medicationStartDate = "2025-10-01",
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        // Only 08:00 was valid yesterday (18:00 starts from 2025-10-10)
        val times = listOf(
            MedicationTime(id = 1, medicationId = 1, sequenceNumber = 1, time = "08:00", validFrom = DateUtils.MIN_DATE, validTo = DateUtils.MAX_DATE),
            MedicationTime(id = 2, medicationId = 1, sequenceNumber = 2, time = "18:00", validFrom = "2025-10-10", validTo = DateUtils.MAX_DATE)
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
        val sequenceNumber = 1
        val scheduledDate = "2025-10-10"

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, sequenceNumber)
        } returns null
        coEvery { medicationIntakeDao.insert(any()) } returns 1L

        // When
        repository.updateIntakeStatus(medicationId, sequenceNumber, true, scheduledDate)

        // Then
        coVerify {
            medicationIntakeDao.insert(match {
                it.medicationId == medicationId &&
                it.sequenceNumber == sequenceNumber &&
                it.scheduledDate == scheduledDate &&
                it.takenAt != null
            })
        }
    }

    @Test
    fun `updateIntakeStatus with isTaken true should update existing intake`() = runTest {
        // Given
        val medicationId = 1L
        val sequenceNumber = 1
        val scheduledDate = "2025-10-10"
        val existingIntake = MedicationIntake(
            id = 1,
            medicationId = medicationId,
            sequenceNumber = sequenceNumber,
            scheduledDate = scheduledDate,
            takenAt = null
        )

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, sequenceNumber)
        } returns existingIntake
        coEvery { medicationIntakeDao.update(any()) } just Runs

        // When
        repository.updateIntakeStatus(medicationId, sequenceNumber, true, scheduledDate)

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
        val sequenceNumber = 1
        val scheduledDate = "2025-10-10"
        val existingIntake = MedicationIntake(
            id = 1,
            medicationId = medicationId,
            sequenceNumber = sequenceNumber,
            scheduledDate = scheduledDate,
            takenAt = "10:30"
        )

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, sequenceNumber)
        } returns existingIntake
        coEvery { medicationIntakeDao.update(any()) } just Runs

        // When
        repository.updateIntakeStatus(medicationId, sequenceNumber, false, scheduledDate)

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
        val sequenceNumber = 1
        val scheduledDate = "2025-10-10"

        coEvery {
            medicationIntakeDao.getIntakeByMedicationAndDateTime(medicationId, scheduledDate, sequenceNumber)
        } returns null

        // When
        repository.updateIntakeStatus(medicationId, sequenceNumber, false, scheduledDate)

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
        isAsNeeded: Boolean = false,
        cycleType: CycleType = CycleType.DAILY,
        cycleValue: String? = null,
        medicationStartDate: String = DateUtils.MIN_DATE,
        medicationEndDate: String = DateUtils.MAX_DATE,
        validFrom: String = DateUtils.MIN_DATE,
        validTo: String = DateUtils.MAX_DATE
    ): MedicationConfig {
        return MedicationConfig(
            id = id,
            medicationId = medicationId,
            isAsNeeded = isAsNeeded,
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
