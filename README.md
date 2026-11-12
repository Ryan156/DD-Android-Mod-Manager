# DD Mod Manager

DD Mod Manager is an independent Android utility for managing Darkest Dungeon mods and campaign load orders on mobile devices.

It is intended for users who already own a legitimate PC copy of the game and use a compatible Android port or porter project. Some porter projects require users to provide files from their own legally purchased PC copy. DD Mod Manager does not include, extract, or distribute the game, port files, or copyrighted game assets.

For one example of a community mobile ports project, see the [Darkest Dungeon Porter releases](https://github.com/KatayR/darkest-dungeon-porter/releases). This project is not affiliated with, operated by, or endorsed by that repository or its contributors.

## Features

- Scan a user-selected mod directory.
- Browse installed mods in a folder-based list.
- Read metadata from each mod's `project.xml`, including:
  - title
  - version
  - language
  - description
  - tags
- Display each mod's `preview_icon.png` when available.
- Organize mods into:
  - Mods Folder
  - Active
  - Inactive
- Enable or disable individual mods through the current load order.
- Select and enable or disable multiple mods together.
- Reorder selected active mods as a group.
- Enable all, disable all, and rescan the mod directory.
- Import source and target campaign save files.
- Copy a source campaign's load order to a target campaign.
- Write supported load-order changes back to the imported source save.
- Use the Android Storage Access Framework for folders and save files.
- Parse and write the game's serialized DSON save data while preserving the surrounding save structure.

## Storage and save files

Storage locations depend on the Android port being used. Configure the mod directory from **Settings & Storage** instead of relying on a fixed path.

Android restricts direct access to many protected app-data directories, especially on newer Android versions. DD Mod Manager therefore supports importing save files through the Android file picker and using persisted Storage Access Framework permissions where available.

## Mod activation model

DD Mod Manager treats load-order membership as the source of truth for activation:

- Enabling a mod adds it to the current source load order.
- Disabling a mod removes it from the current source load order.
- Mod folders are not moved or renamed.
- Source-save changes are written when a source save has been imported.
- The target save is changed only when the user explicitly copies the source order to it.

Some Android ports generate internal folders used by the port itself. The app hides known generated folders from its mod lists and does not delete or modify them.

## Recommended workflow

1. Complete the required in-game setup and create a campaign save.
2. Import that file as the source save.
3. Select mods from the Mods Folder tab.
4. Review the Active and Inactive tabs.
5. Reorder active mods individually or as a group.
6. Import the campaign that should receive the load order as the target save.
7. Use **Copy source order to target**.
8. Import the resulting target save back into the game using the port's own save-import process.

Always keep backups of original save files before importing or modifying them.

## Building

Requirements:

- Android Studio
- JDK 11
- Android SDK with the project's configured compile SDK
- Android 7.0 / API 24 or newer

Run unit tests:

```powershell
.\\gradlew.bat :app:testDebugUnitTest
```

Build a debug APK:

```powershell
.\\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release signing

Release signing is configured through environment variables so keystore credentials are not committed to the repository:

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

The GitHub Actions release workflow expects these repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Never commit a keystore, signing password, or decoded release key to the repository.

## Technical overview

- Kotlin
- Jetpack Compose and Material 3
- Kotlin Coroutines
- Android Storage Access Framework
- DocumentFile
- Kotlin serialization
- Custom DSON parsing, editing, validation, and round-trip tests
- Firebase Analytics
- Gradle Kotlin DSL

## Disclaimer and legal notice

DD Mod Manager is an independent, community-made utility. It is not affiliated with or endorsed by Red Hook Studios, any publisher, any rights holder, or any third-party porter project.

This repository does not distribute, host, or provide:

- the Darkest Dungeon game
- Android port or porter APKs
- copyrighted game files or assets
- paid content
- third-party mod files

Users are responsible for obtaining the game, any compatible Android port, save files, and mods legally and independently. This project is intended only to manage user-owned files.

No piracy. No game APK distribution.

## License

A project license has not been selected yet. Until a license is added, normal copyright restrictions apply to this repository.
