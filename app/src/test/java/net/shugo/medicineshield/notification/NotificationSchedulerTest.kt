package net.shugo.medicineshield.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.data.model.MedicationTime
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NotificationSchedulerTest {

    private lateinit var context: Context
    private lateinit var repository: MedicationRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var settingsPreferences: SettingsPreferences
    private lateinit var timeProvider: TimeProvider
    private lateinit var dateFormatter: SimpleDateFormat
    private lateinit var pendingIntentFactory: PendingIntentFactory
    private lateinit var scheduler: NotificationScheduler

    @Before
    fun setup() {
        // Mock Intent constructor
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        repository = mockk()
        alarmScheduler = mockk(relaxed = true)
        settingsPreferences = mockk()
        timeProvider = mockk()
        dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        pendingIntentFactory = mockk(relaxed = true)

        // Mock PendingIntent creation
        every { pendingIntentFactory.createBroadcast(any(), any(), any(), any()) } returns mockk(relaxed = true)

        // Mock getMedications to return empty list by default (will be overridden in specific tests)
        coEvery { repository.getMedications(any()) } returns flowOf(emptyList())

        scheduler = NotificationScheduler(
            context,
            repository,
            alarmScheduler,
            settingsPreferences,
            timeProvider,
            dateFormatter,
            pendingIntentFactory
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getNotificationIdForTime should convert time to integer ID`() {
        // Test various time formats
        assertEquals(900, scheduler.getNotificationIdForTime("09:00"))
        assertEquals(1430, scheduler.getNotificationIdForTime("14:30"))
        assertEquals(0, scheduler.getNotificationIdForTime("00:00"))
        assertEquals(2359, scheduler.getNotificationIdForTime("23:59"))
    }

    @Test
    fun `getNotificationIdForTime should return 0 for invalid time format`() {
        assertEquals(0, scheduler.getNotificationIdForTime("invalid"))
        assertEquals(0, scheduler.getNotificationIdForTime(""))
    }

    @Test
    fun `scheduleNextNotificationForTime should schedule notification for valid medication`() = runTest {
        // Setup: Current time is 2024-01-15 08:00:00
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: Medication with 09:00 time slot
        val dailyItem = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Test Medicine",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        coEvery { repository.getMedications("2024-01-15") } returns flowOf(listOf(dailyItem))

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should schedule alarm for today at 09:00
        val expectedTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeSlot = slot<Long>()
        verify { alarmScheduler.scheduleAlarm(capture(timeSlot), any()) }
        assertEquals(expectedTime, timeSlot.captured)
    }

    @Test
    fun `scheduleNextNotificationForTime should cancel notification if no medications found`() = runTest {
        // Setup: Current time
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: No medications
        coEvery { repository.getAllMedicationsWithTimes() } returns flowOf(emptyList())

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should cancel notification
        verify { alarmScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `scheduleNextNotificationForTime should skip PRN medications`() = runTest {
        // Setup: Current time is 2024-01-15 08:00:00
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: PRN medication
        val medication = Medication(id = 1L, name = "Test PRN Medicine")
        val config = MedicationConfig(
            medicationId = 1L,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = "2024-01-01",
            medicationEndDate = "2024-12-31",
            isAsNeeded = true,
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val time = MedicationTime(
            medicationId = 1L,
            sequenceNumber = 1,
            time = "09:00",
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val medWithTimes = MedicationWithTimes(medication, listOf(time), listOf(config))

        coEvery { repository.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes))

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should cancel notification (PRN medications don't get scheduled)
        verify { alarmScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `cancelNotificationForTime should cancel alarm`() {
        // Execute
        scheduler.cancelNotificationForTime("09:00")

        // Verify
        verify { alarmScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `scheduleDailyRefreshJob should schedule alarm at next midnight`() {
        // Setup: Current time is 2024-01-15 14:30:00
        val calendar = Calendar.getInstance()
        every { context.getSystemService(Context.ALARM_SERVICE) } returns mockk()

        // Execute
        scheduler.scheduleDailyRefreshJob()

        // Verify: Should schedule alarm
        verify { alarmScheduler.scheduleAlarm(any(), any()) }
    }

    @Test
    fun `cancelDailyRefreshJob should cancel alarm`() {
        // Execute
        scheduler.cancelDailyRefreshJob()

        // Verify
        verify { alarmScheduler.cancelAlarm(any()) }
    }

    @Test
    fun `rescheduleAllNotifications should do nothing if notifications are disabled`() = runTest {
        // Setup: Notifications disabled
        coEvery { settingsPreferences.isNotificationsEnabled() } returns false

        // Execute
        scheduler.rescheduleAllNotifications()

        // Verify: Should not call repository or schedule any alarms
        coVerify(exactly = 0) { repository.getAllMedicationsWithTimes() }
        verify(exactly = 0) { alarmScheduler.scheduleAlarm(any(), any()) }
    }

    @Test
    fun `rescheduleAllNotifications should schedule all medication times when enabled`() = runTest {
        // Setup: Notifications enabled
        coEvery { settingsPreferences.isNotificationsEnabled() } returns true

        // Setup: Current time
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: Multiple medications with different times
        val medication1 = Medication(id = 1L, name = "Medicine 1")
        val config1 = MedicationConfig(
            medicationId = 1L,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = "2024-01-01",
            medicationEndDate = "2024-12-31",
            isAsNeeded = false,
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val time1 = MedicationTime(
            medicationId = 1L,
            sequenceNumber = 1,
            time = "09:00",
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val medWithTimes1 = MedicationWithTimes(medication1, listOf(time1), listOf(config1))

        val medication2 = Medication(id = 2L, name = "Medicine 2")
        val config2 = MedicationConfig(
            medicationId = 2L,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = "2024-01-01",
            medicationEndDate = "2024-12-31",
            isAsNeeded = false,
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val time2 = MedicationTime(
            medicationId = 2L,
            sequenceNumber = 1,
            time = "14:00",
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val medWithTimes2 = MedicationWithTimes(medication2, listOf(time2), listOf(config2))

        coEvery { repository.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes1, medWithTimes2))

        // Setup: Daily medications for both times
        val dailyItem1 = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Medicine 1",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        val dailyItem2 = DailyMedicationItem(
            medicationId = 2L,
            medicationName = "Medicine 2",
            scheduledTime = "14:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        coEvery { repository.getMedications("2024-01-15") } returns flowOf(listOf(dailyItem1, dailyItem2))

        // Execute
        scheduler.rescheduleAllNotifications()

        // Verify: Should schedule alarms for both times (09:00 and 14:00) plus daily refresh
        verify(atLeast = 2) { alarmScheduler.scheduleAlarm(any(), any()) }
    }

    @Test
    fun `scheduleNextNotificationForTime should handle weekly cycle correctly`() = runTest {
        // Setup: Current time is Monday 2024-01-15 08:00:00
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)  // Monday
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: Medication scheduled for Monday and Wednesday (1,3)
        val dailyItem = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Test Medicine",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        coEvery { repository.getMedications("2024-01-15") } returns flowOf(listOf(dailyItem))

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should schedule for today (Monday) at 09:00
        val expectedTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeSlot = slot<Long>()
        verify { alarmScheduler.scheduleAlarm(capture(timeSlot), any()) }
        assertEquals(expectedTime, timeSlot.captured)
    }

    @Test
    fun `scheduleNextNotificationForTime should handle interval cycle correctly`() = runTest {
        // Setup: Current time is 2024-01-15 08:00:00 (day 0)
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: Medication with 2-day interval starting from 2024-01-15
        val dailyItem = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Test Medicine",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        coEvery { repository.getMedications("2024-01-15") } returns flowOf(listOf(dailyItem))

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should schedule for today at 09:00 (day 0, which is divisible by 2)
        val expectedTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeSlot = slot<Long>()
        verify { alarmScheduler.scheduleAlarm(capture(timeSlot), any()) }
        assertEquals(expectedTime, timeSlot.captured)
    }

    @Test
    fun `scheduleNextNotificationForTime should skip to next day when all medications are already taken`() = runTest {
        // Setup: Current time is 2024-01-15 08:00:00
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: Medication scheduled for 09:00 daily
        val medication = Medication(id = 1L, name = "Test Medicine")
        val config = MedicationConfig(
            medicationId = 1L,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = "2024-01-01",
            medicationEndDate = "2024-12-31",
            isAsNeeded = false,
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val time = MedicationTime(
            medicationId = 1L,
            sequenceNumber = 1,
            time = "09:00",
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val medWithTimes = MedicationWithTimes(medication, listOf(time), listOf(config))

        coEvery { repository.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes))

        // Setup: Today's medication is already taken
        val todayItem = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Test Medicine",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.TAKEN
        )
        coEvery { repository.getMedications("2024-01-15") } returns flowOf(listOf(todayItem))

        // Setup: Tomorrow's medication is not taken yet
        val tomorrowItem = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Test Medicine",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        coEvery { repository.getMedications("2024-01-16") } returns flowOf(listOf(tomorrowItem))

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should schedule for tomorrow at 09:00 (skip today since already taken)
        val expectedTime = Calendar.getInstance().apply {
            set(2024, 0, 16, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeSlot = slot<Long>()
        verify { alarmScheduler.scheduleAlarm(capture(timeSlot), any()) }
        assertEquals(expectedTime, timeSlot.captured)
    }

    @Test
    fun `scheduleNextNotificationForTime should schedule for today when some medications are not taken`() = runTest {
        // Setup: Current time is 2024-01-15 08:00:00
        val currentTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        every { timeProvider.currentTimeMillis() } returns currentTime

        // Setup: Two medications scheduled for 09:00 daily
        val medication1 = Medication(id = 1L, name = "Medicine A")
        val medication2 = Medication(id = 2L, name = "Medicine B")
        val config1 = MedicationConfig(
            medicationId = 1L,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = "2024-01-01",
            medicationEndDate = "2024-12-31",
            isAsNeeded = false,
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val config2 = MedicationConfig(
            medicationId = 2L,
            cycleType = CycleType.DAILY,
            cycleValue = null,
            medicationStartDate = "2024-01-01",
            medicationEndDate = "2024-12-31",
            isAsNeeded = false,
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val time1 = MedicationTime(
            medicationId = 1L,
            sequenceNumber = 1,
            time = "09:00",
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val time2 = MedicationTime(
            medicationId = 2L,
            sequenceNumber = 1,
            time = "09:00",
            validFrom = "2024-01-01",
            validTo = "9999-12-31"
        )
        val medWithTimes1 = MedicationWithTimes(medication1, listOf(time1), listOf(config1))
        val medWithTimes2 = MedicationWithTimes(medication2, listOf(time2), listOf(config2))

        coEvery { repository.getAllMedicationsWithTimes() } returns flowOf(listOf(medWithTimes1, medWithTimes2))

        // Setup: One medication is taken, one is not
        val takenItem = DailyMedicationItem(
            medicationId = 1L,
            medicationName = "Medicine A",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.TAKEN
        )
        val uncheckedItem = DailyMedicationItem(
            medicationId = 2L,
            medicationName = "Medicine B",
            scheduledTime = "09:00",
            sequenceNumber = 1,
            isAsNeeded = false,
            dose = 1.0,
            doseUnit = "tablets",
            status = MedicationIntakeStatus.UNCHECKED
        )
        coEvery { repository.getMedications("2024-01-15") } returns flowOf(listOf(takenItem, uncheckedItem))

        // Execute
        scheduler.scheduleNextNotificationForTime("09:00")

        // Verify: Should schedule for today at 09:00 (one medication is not taken yet)
        val expectedTime = Calendar.getInstance().apply {
            set(2024, 0, 15, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timeSlot = slot<Long>()
        verify { alarmScheduler.scheduleAlarm(capture(timeSlot), any()) }
        assertEquals(expectedTime, timeSlot.captured)
    }
}
