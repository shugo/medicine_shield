# MedicineShield

MedicineShield is an Android medication management application that helps users track their daily medication schedule with support for various medication cycles.

## Features

- **Daily Medication View**: View and track medications scheduled for today with an intuitive interface
- **Date Navigation**: Browse medication schedules for past and future dates with a Today button for quick navigation
- **Flexible Medication Cycles**:
  - Daily: Take medication every day
  - Weekly: Take medication on specific days of the week (e.g., Monday, Wednesday, Friday)
  - Interval: Take medication every N days
  - As-Needed: Add medications to take as needed (PRN medications)
- **Medication Management**: Add, edit, and delete medications with customizable schedules
- **Intake Tracking**: Record when medications are taken with timestamp tracking, including cancel functionality
- **Dosage Support**: Track medication dosage with customizable amounts and units
- **Daily Notes**: Add notes for each day to record health observations or medication-related information
- **Period Support**: Set start and end dates for medication courses
- **Temporal Data Management**: Track historical changes to medication schedules and times
- **Medication Notifications**: Receive reminders at scheduled medication times
- **Settings**: Customize notification preferences, view app version and license information

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean Architecture with Repository pattern
- **Database**: Room (SQLite)
- **Navigation**: Jetpack Navigation Compose
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36 (Android 16)

## Project Structure

```
app/src/main/java/net/shugo/medicineshield/
├── data/
│   ├── dao/              # Room DAOs for database access
│   ├── database/         # Database configuration and migrations
│   ├── model/            # Data models and entities
│   ├── preferences/      # SharedPreferences wrapper for settings
│   └── repository/       # Repository layer for data operations
├── notification/         # Notification scheduling and handling
├── ui/
│   └── screen/           # Compose UI screens
├── utils/                # Utility classes (date handling, etc.)
├── viewmodel/            # ViewModels for UI state management
└── MainActivity.kt       # Main entry point and navigation setup
```

## Building the Project

### Requirements

- Android Studio Hedgehog or later
- JDK 17
- Android SDK with API level 34

### Build Commands

```bash
# Clean and build
./gradlew clean build

# Install debug build on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Run lint check
./gradlew lint
```

## Database Schema

The app uses Room database (version 11) with the following entities:

### Core Entities

- **medications**: Core medication information (id, name)
  - Minimal entity storing only basic medication identification

- **medication_configs**: Medication scheduling configuration with temporal validity
  - Stores `cycleType` (DAILY, WEEKLY, INTERVAL), `cycleValue`, date range
  - Temporal validity: `validFrom`/`validTo` timestamps enable tracking configuration changes over time
  - One-to-many relationship with Medication

- **medication_times**: Scheduled times for each medication with temporal validity
  - Each time has a stable `sequenceNumber` (1, 2, 3...) per medication
  - Temporal validity: `validFrom`/`validTo` timestamps track when each time is valid
  - Allows time changes without breaking intake history
  - One-to-many relationship with Medication

- **medication_intakes**: Records of actual medication intake events
  - Links to medication via `medicationId` and `sequenceNumber` (not time string)
  - Stores `scheduledDate` (YYYY-MM-DD), `takenAt` timestamp, and `canceledAt` timestamp
  - Supports three states: unchecked (not taken), taken (with timestamp), and canceled (with timestamp)
  - Unique index on `(medicationId, scheduledDate, sequenceNumber)`

- **daily_notes**: Daily notes for recording health observations
  - Stores notes for each date (YYYY-MM-DD format)
  - Includes creation and update timestamps

### Key Design Features

- **Temporal Data Management**: Both `MedicationConfig` and `MedicationTime` support temporal validity, allowing the app to show accurate historical data for past dates and current configuration for future dates

- **Sequence Number System**: Medications times use stable sequence numbers instead of time strings for identification, enabling medication time changes while preserving intake history

- **As-Needed Medications**: Support for PRN (as-needed) medications that can be added multiple times per day without a fixed schedule

- **Dosage Tracking**: Track medication dosage with customizable amounts and units (e.g., 1 tablet, 5 ml, 2.5 mg)

- **Database Migrations**: The database includes comprehensive migrations from v1 through v11, supporting evolving features including temporal data, sequence numbers, dosage tracking, and daily notes

## License

This project is licensed under MIT-0 License - see the [LICENSE.txt](LICENSE.txt) file for details.

## Author

Shugo Maeda
