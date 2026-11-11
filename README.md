# DD Mod Manager

DD Mod Manager is an independent Android utility for managing mods for the unofficial Android port of *Darkest Dungeon* by Wicked.

It is built for players who already own the game and want a more convenient way to inspect their installed mods, maintain a load order, and transfer that order between campaigns.

## Features

- Scans the Wicked Android port mod directory.
- Displays installed mods in a folder-based browser.
- Reads mod metadata from `project.xml`, including:
  - title
  - version
  - language
  - description
  - tags
- Displays each mod's `preview_icon.png` when available.
- Separates installed mods into:
  - Mods Folder
  - Active
  - Inactive
- Enables or disables individual mods by updating the app's load order.
- Enables or disables multiple selected mods at once.
- Provides Enable All, Disable All, and Rescan Mod Directory actions.
- Supports source and target save files:
  - Import a source save from an existing campaign.
  - Import a target save from a new campaign.
  - Copy the source load order to the target save.
- Uses the project's DSON parser and writer to preserve the save structure when updating mod lists.
- Supports Android Storage Access Framework selection for folders and save files.

## Supported Wicked port locations

The default mod directory is:

```text
/storage/emulated/0/Android/Media/com.darkestdungeon/mods
```

The save files are located by the game under:

```text
/storage/emulated/0/Android/Data/com.darkestdungeon/files/
```

Android 14 and later restrict direct access to many `Android/Data` locations. For that reason, the app uses imported save files and persisted SAF permissions instead of relying on a preset `Android/Data` folder picker.

## Mod activation model

The Wicked Android port automatically detects mod folders. DD Mod Manager therefore treats activation as load-order membership:

- Enabling a mod adds it to the current app/source load order.
- Disabling a mod removes it from that load order.
- Mod folders are not moved or renamed.
- Changes are written to the imported source save when a source save is configured.
- The target save is only modified when the user explicitly copies the source order to it.

The port-generated folders `.port-nx-original` and `.port-switch` are hidden from the mod lists. They are not deleted or modified.

## Recommended workflow

1. Complete the in-game tutorial and create a save in town.
2. Import that save as the source save.
3. Select or deselect mods from the Mods Folder tab.
4. Review the Active and Inactive tabs.
5. Reorder active mods when needed.
6. Import the campaign you want to update as the target save.
7. Use Copy Source Order to Target.
8. Import the resulting target save back into the game using the port's own save-import feature.

Always keep backups of the original save files before importing or modifying them.

## Building

Requirements:

- Android Studio
- JDK 11
- Android SDK with the project's configured compile SDK
- Android 7.0 / API 24 or newer for the app

Run unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
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
- Gradle Kotlin DSL

## Disclaimer and legal notice

DD Mod Manager is a community-made, independent utility. It is not affiliated with or endorsed by Wicked, Red Hook Studios, or any publisher or rights holder.

This repository does not distribute, host, link to, or provide:

- the Darkest Dungeon game
- the unofficial Android port APK
- copyrighted game files
- paid content
- third-party mod files

Users are responsible for obtaining the game, the Wicked Android port, save files, and mods legally and independently. This project is intended only to help manage user-owned mod folders and save files.

No piracy. No game APK sharing.

## License

A project license has not been selected yet. Until a license is added, normal copyright restrictions apply to this repository.
