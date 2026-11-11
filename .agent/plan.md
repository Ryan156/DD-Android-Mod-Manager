# Project Plan

Mod manager app on Android for Darkest Dungeon (Android port by Wicked). Features include managing mods (enabling, disabling, load order ordering, importing/installing mods, scanning mod directories, viewing mod details/metadata, backup/restore mod setup, compatibility checks).

## Project Brief

# Darkest Dungeon Mod Manager - Project Brief

## Features
- **Mod Directory Scanning & List View**: Scans the Darkest Dungeon mod folder to discover installed mods and displays metadata including mod name, version, author, and enabled/disabled state.
- **Load Order Management**: Provides UI controls to reorder active mods to establish proper load priority.
- **Mod Import & Installation**: Allows importing mod `.zip` archives from local device storage and extracting them into the active mod directory.
- **Backup & Restore Setup**: Exports the active mod setup and load order state to a configuration backup file and restores it on demand.

## High-Level Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Navigation & Adaptive Strategy**: **Jetpack Navigation 3** (state-driven navigation) and **Compose Material Adaptive** library
- **Architecture**: MVVM architecture with `ViewModel` and `StateFlow`
- **Concurrency**: Kotlin Coroutines & Flow
- **File & Storage Handling**: Standard Java/Kotlin File I/O and `ZipInputStream` for mod scanning and archive extraction

## Implementation Steps
**Total Duration:** 17m 38s

### Task_1_CoreArchitectureAndModScanner: Set up core data models, storage manager for mod directory scanning, and ViewModel state management for listing installed Darkest Dungeon mods and reading metadata.
- **Status:** COMPLETED
- **Updates:** Implemented core data models (Mod, ModMetadata), ModMetadataReader for project.xml and modinfo.json, ModStorageManager supporting Java File and DocumentFile scanning, ModRepository with load order and enabled status management, and ModManagerViewModel with StateFlow. Built and passed unit tests cleanly.
- **Acceptance Criteria:**
  - Mod directory scanning logic implemented
  - ViewModel and StateFlow management ready
  - Project builds successfully
- **Duration:** 12m 13s

### Task_2_ModListAndLoadOrderUI: Build Jetpack Compose UI with Navigation 3 displaying mod list, toggle enabled/disabled status, and reorder load priority.
- **Status:** COMPLETED
- **Updates:** Implemented Jetpack Compose UI with dark fantasy Material 3 theme, ModListScreen, ModCard with load order reorder buttons, ModDetailScreen, SettingsScreen, PathPickerDialog, and state-driven navigation in ModManagerApp. Added @Previews and verified build with ./gradlew assembleDebug and testDebugUnitTest.
- **Acceptance Criteria:**
  - Mod list UI renders installed mods
  - UI controls allow toggling mod status and reordering load priority
  - Navigation 3 setup works smoothly
- **Duration:** 5m 25s

### Task_3_ModImportAndBackupRestore: Implement mod import from .zip archives into mod directory and configuration backup/restore functionality.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Zip extraction imports mods into active directory
  - Backup and restore of load order configuration functions correctly
  - Mod management workflows complete
- **StartTime:** 2026-11-10 12:27:59 GMT+08:00

### Task_4_RunAndVerify: Run and verify the complete application, instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** PENDING
- **Acceptance Criteria:**
  - make sure all existing tests pass
  - build pass
  - app does not crash
  - Application verified for stability and alignment with user requirements

