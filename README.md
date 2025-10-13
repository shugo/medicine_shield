# MedicineShield

MedicineShield is an Android medication management application that helps users track their daily medication schedule with support for various medication cycles.

## Features

- **Daily Medication View**: View and track medications scheduled for today with an intuitive checkbox interface
- **Date Navigation**: Browse medication schedules for past and future dates
- **Flexible Medication Cycles**:
  - Daily: Take medication every day
  - Weekly: Take medication on specific days of the week (e.g., Monday, Wednesday, Friday)
  - Interval: Take medication every N days
- **Medication Management**: Add, edit, and delete medications with customizable schedules
- **Intake Tracking**: Record when medications are taken with timestamp tracking
- **Period Support**: Set start and end dates for medication courses
- **Temporal Data Management**: Track historical changes to medication schedules and times
- **Medication Notifications**: Receive reminders at scheduled medication times
- **Settings**: Customize notification preferences and app behavior

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: Clean Architecture with Repository pattern
- **Database**: Room (SQLite)
- **Navigation**: Jetpack Navigation Compose
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

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

The app uses Room database (version 5) with the following entities:

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
  - Stores `scheduledDate` (YYYY-MM-DD) and `takenAt` timestamp
  - Unique index on `(medicationId, scheduledDate, sequenceNumber)`

### Key Design Features

- **Temporal Data Management**: Both `MedicationConfig` and `MedicationTime` support temporal validity, allowing the app to show accurate historical data for past dates and current configuration for future dates

- **Sequence Number System**: Medications times use stable sequence numbers instead of time strings for identification, enabling medication time changes while preserving intake history

- **Database Migrations**: The database includes migrations from v1 through v5, with v4→v5 adding the sequence number system (note: this migration clears intake history due to complexity)

## License

This project is licensed under MIT-0 License - see the [LICENSE.txt](LICENSE.txt) file for details.

## Author

Shugo Maeda
