# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MedicineShield is an Android medication management application built with Kotlin and Jetpack Compose. It helps users track their daily medication schedule with support for various medication cycles (daily, weekly, interval-based).

**Package**: `net.shugo.medicineshield`
**Min SDK**: 24
**Target SDK**: 34

## Development Workflow

**IMPORTANT**: Follow this workflow for ALL changes, including bug fixes:

1. **Create Feature Branch FIRST**
   - **NEVER commit directly to main branch**
   - Always create a new branch before making any changes: `git checkout -b feature/<feature-name>` or `git checkout -b fix/<issue-name>`
   - Even for small bug fixes, create a branch first

2. **Create SOW (Statement of Work)** (for complex features)
   - For significant features, create a SOW document in `./tmp/SOW_<feature-name>.md`
   - Include:
     - Feature/fix description
     - Implementation plan
     - Affected files
     - Testing strategy
   - Wait for user approval before proceeding
   - For simple bug fixes, SOW may be skipped

3. **Implementation**
   - Proceed with implementation according to approved SOW (or directly for simple fixes)
   - Commit changes with English descriptive messages
   - Follow architecture and coding standards defined in this document

4. **Testing & Review**
   - Run appropriate tests (unit, instrumentation, lint)
   - Ensure build succeeds (`./gradlew build`)

## Build Commands

```bash
# Build the project
./gradlew build

# Clean build
./gradlew clean build

# Run debug build on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint
```

## Architecture

The app follows a clean architecture pattern with clear separation of concerns:

### Data Layer
- **Database**: Room database (`AppDatabase`) with version 5, includes migrations from v1
  - Location: `data/database/AppDatabase.kt`
  - Entities: `Medication`, `MedicationTime`, `MedicationIntake`, `MedicationConfig`
  - Migrations: v1→v2 (add intakes), v2→v3 (temporal data), v3→v4 (config separation), v4→v5 (sequence numbers)
- **DAOs**: Separate DAOs for each entity in `data/dao/`
  - `MedicationDao`, `MedicationTimeDao`, `MedicationIntakeDao`, `MedicationConfigDao`
- **Repository**: Single `MedicationRepository` coordinates all data operations
  - Location: `data/repository/MedicationRepository.kt`
  - Handles medication CRUD, intake tracking, and daily medication logic

### Domain Models
- **Medication**: Core medication entity (name only)
  - Cycle configuration moved to separate `MedicationConfig` entity
- **MedicationConfig**: Stores medication scheduling configuration with temporal validity
  - `CycleType` enum: DAILY, WEEKLY (specific days), INTERVAL (every N days)
  - `cycleValue`: Stores comma-separated day indices (0=Sunday) for WEEKLY or interval days for INTERVAL
  - `validFrom`/`validTo`: Temporal validity for configuration changes
- **MedicationTime**: Scheduled times for each medication with temporal validity
  - `sequenceNumber`: Per-medication sequence number (1, 2, 3...) for stable time identification
  - `validFrom`/`validTo`: Tracks when each time is valid (supports time changes over time)
  - One-to-many relationship with Medication
- **MedicationIntake**: Records actual intake events
  - Links to medication via `medicationId` and `sequenceNumber` (not time string)
  - `scheduledDate`: Date string (YYYY-MM-DD)
  - `takenAt`: Timestamp when taken (null = not taken)
  - Unique index on `(medicationId, scheduledDate, sequenceNumber)`
- **DailyMedicationItem**: View model for displaying daily medication status
- **MedicationWithTimes**: Relationship entity combining Medication with its MedicationTimes and MedicationConfigs

### UI Layer
- **Navigation**: Single-activity architecture with Jetpack Navigation Compose
  - Routes: `daily_medication` (start), `medication_list`, `add_medication`, `edit_medication/{id}`
- **Screens** (in `ui/screen/`):
  - `DailyMedicationScreen`: Main screen showing today's medications with date navigation
  - `MedicationListScreen`: List all medications with edit/delete
  - `MedicationFormScreen`: Add/edit medication form (reused for both operations)
- **ViewModels** (in `viewmodel/`):
  - Each screen has a dedicated ViewModel
  - Factories defined in `MainActivity.kt` for dependency injection
  - Repository passed via factory pattern

### Key Logic
- **Temporal Data Management**:
  - Both `MedicationConfig` and `MedicationTime` use `validFrom`/`validTo` for temporal validity
  - Enables tracking configuration and time changes over time
  - Past dates see historical data; future dates see current data
- **Sequence Number System**:
  - Each medication time has a stable `sequenceNumber` (1, 2, 3...)
  - Intake records link via `sequenceNumber` instead of time string
  - Allows time changes without breaking intake history
  - When time changes, old record gets `validTo=today`, new record created with same `sequenceNumber`
- **Medication Scheduling** (`MedicationRepository:shouldTakeMedication()`):
  - Determines if a medication should appear on a given date
  - Handles date range checks and cycle type logic (daily, weekly days, interval days)
- **Intake Tracking** (`MedicationRepository:updateIntakeStatus()`):
  - Creates or updates intake records
  - Uses compound key: `(medicationId, scheduledDate, sequenceNumber)`
  - Supports checking/unchecking medications

## Development Notes

### Database Migrations
- Current version: 5
- Migration history:
  - v1→v2: Add `medication_intakes` table
  - v2→v3: Add temporal validity (`startDate`/`endDate`) to `medication_times`
  - v3→v4: Separate configuration into `medication_configs` table, rename to `validFrom`/`validTo`
  - v4→v5: Add `sequenceNumber` to `medication_times`, update `medication_intakes` to use `sequenceNumber`
- Add new migrations to `AppDatabase.companion.object` when schema changes
- **Important**: Migration v4→v5 deletes all `medication_intakes` records (history migration too complex)

### Dependency Injection
- Currently uses manual DI via ViewModelFactory classes in `MainActivity.kt`
- Repository instantiated in `MainActivity.onCreate()` and passed to screens
- All four DAOs (`MedicationDao`, `MedicationTimeDao`, `MedicationIntakeDao`, `MedicationConfigDao`) injected into MedicationRepository constructor

### Date Handling
- All dates stored as timestamps (Long, milliseconds since epoch)
- Display format: "yyyy-MM-dd" for date strings in intake records
- Calendar manipulation uses `java.util.Calendar`
- Timezone-aware date parsing in `MedicationRepository`

### Compose Best Practices
- Material 3 components used throughout
- ExperimentalMaterial3Api opt-in enabled globally in build.gradle.kts
- ViewModel state observed via Compose State/Flow
