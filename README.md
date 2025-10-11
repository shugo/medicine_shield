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
│   └── repository/       # Repository layer for data operations
├── ui/
│   └── screen/           # Compose UI screens
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

The app uses Room database with the following entities:

- **medications**: Core medication information (name, cycle type, date range)
- **medication_times**: Scheduled times for each medication
- **medication_intakes**: Records of actual medication intake events

## License

This project is licensed under MIT-0 License - see the [LICENSE.txt](LICENSE.txt) file for details.

## Author

Shugo Maeda
