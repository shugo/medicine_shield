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
   - Commit changes with descriptive messages
   - Follow architecture and coding standards defined in this document

4. **Testing & Review**
   - Run appropriate tests (unit, instrumentation, lint)
   - Ensure build succeeds (`./gradlew build`)
   - Create pull request when ready for review
   - **WAIT for user approval before merging**
   - **NEVER merge PR without explicit user approval**

5. **Merge to Main**
   - Only merge after receiving explicit user approval
   - Use `/merge` command or `gh pr merge` to merge the PR
   - Verify merge was successful

6. **Branch Cleanup**
   - Delete feature branch after PR is merged
   - Confirm you're back on main branch with latest changes

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
- **Database**: Room database (`AppDatabase`) with version 2, includes migration from v1
  - Location: `data/database/AppDatabase.kt`
  - Entities: `Medication`, `MedicationTime`, `MedicationIntake`
- **DAOs**: Separate DAOs for each entity in `data/dao/`
- **Repository**: Single `MedicationRepository` coordinates all data operations
  - Location: `data/repository/MedicationRepository.kt`
  - Handles medication CRUD, intake tracking, and daily medication logic

### Domain Models
- **Medication**: Core medication entity with cycle configuration
  - `CycleType` enum: DAILY, WEEKLY (specific days), INTERVAL (every N days)
  - `cycleValue`: Stores comma-separated day indices (0=Sunday) for WEEKLY or interval days for INTERVAL
- **MedicationTime**: Scheduled times for each medication (one-to-many relationship)
- **MedicationIntake**: Records actual intake events (timestamped)
- **DailyMedicationItem**: View model for displaying daily medication status
- **MedicationWithTimes**: Relationship entity combining Medication with its MedicationTimes

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
- **Medication Scheduling** (`MedicationRepository:144-169`):
  - `shouldTakeMedication()` determines if a medication should appear on a given date
  - Handles date range checks and cycle type logic (daily, weekly days, interval days)
- **Intake Tracking** (`MedicationRepository:98-139`):
  - `updateIntakeStatus()` creates or updates intake records
  - Supports checking/unchecking medications
  - Uses compound key: `medicationId_scheduledTime_scheduledDate`

## Development Notes

### Database Migrations
- Current version: 2
- Migration 1→2 adds `medication_intakes` table
- Add new migrations to `AppDatabase.companion.object` when schema changes

### Dependency Injection
- Currently uses manual DI via ViewModelFactory classes in `MainActivity.kt`
- Repository instantiated in `MainActivity.onCreate()` and passed to screens
- All three DAOs injected into MedicationRepository constructor

### Date Handling
- All dates stored as timestamps (Long, milliseconds since epoch)
- Display format: "yyyy-MM-dd" for date strings in intake records
- Calendar manipulation uses `java.util.Calendar`
- Timezone-aware date parsing in `MedicationRepository`

### Compose Best Practices
- Material 3 components used throughout
- ExperimentalMaterial3Api opt-in enabled globally in build.gradle.kts
- ViewModel state observed via Compose State/Flow
