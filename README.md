# Lunar

Lunar is a Kotlin Multiplatform sheet music library and PDF viewer built with Kotlin and Compose Multiplatform.

The current milestone focuses on a clean shared foundation for:

- importing PDF sheet music
- browsing a library across devices and screen sizes
- searching, sorting, filtering, and organizing scores
- previewing and viewing PDFs in-app
- leaving room for future file and metadata sync

## Can I run this on Windows?

Yes.

This repo includes a desktop JVM target, and on Windows you can:

- run the app directly during development with `.\gradlew.bat :composeApp:run`
- build a Windows installer with `.\gradlew.bat :composeApp:packageReleaseMsi`

Desktop packaging is currently configured for:

- `MSI` on Windows
- `DEB` on Linux
- `DMG` on macOS

## Current Platform Support

| Target | Status | Notes |
| --- | --- | --- |
| Android | Active | First-class target. PDF import, folder import, SAF permission tracking, library UI, dialog preview, and fullscreen viewer are wired. |
| Desktop JVM (Windows) | Active | First-class target. File/folder import, local storage, library UI, dialog preview, fullscreen viewer, and MSI packaging are wired. |
| Desktop JVM (Linux) | Active | Same shared desktop app flow as Windows. `DEB` packaging is configured. |
| iOS | Preview shell | Build targets exist, but PDF import/viewing are not fully wired yet. |
| Web / Wasm | Preview shell | Build targets exist, but PDF import/viewing are not fully wired yet. |
| Server | Placeholder | Ktor module exists for future sync/back-end work, but it is not part of the current app flow. |

## Architecture

The project is split into a shared core plus thin platform integrations.

### Modules

- `shared/`
  - Shared domain models for sheet music items and library queries
  - Shared repository and persistence abstractions
  - Shared sort, filter, search, and metadata logic
  - Shared tests for repository and query behavior
- `composeApp/`
  - Shared Compose UI and app state
  - Shared library, import, preview, and fullscreen viewer flows
  - Platform-specific import and PDF rendering integrations
- `iosApp/`
  - Native iOS launcher shell for the Compose app target
- `server/`
  - Ktor-based placeholder for future sync services

### Source set responsibilities

- `composeApp/src/commonMain`
  - App shell, navigation, library screen, import screen, viewer screen, and shared state
- `composeApp/src/androidMain`
  - Android SAF file/folder import
  - persisted URI permission tracking
  - PDF rendering via Android `PdfRenderer`
- `composeApp/src/jvmMain`
  - Desktop file/folder chooser
  - PDF rendering via Apache PDFBox
- `shared/src/commonMain`
  - Library models
  - query/filter/sort logic
  - repository logic
  - metadata persistence helpers

### Current app flow

1. Import PDFs from file or folder.
2. Register them in the shared repository.
3. Persist library metadata locally.
4. Browse the shared library UI with search, sort, and filters.
5. Open a score in a dialog preview.
6. Expand into fullscreen viewing when needed.

## Storage Model

Lunar currently uses local-first storage.

- Metadata is stored as JSON through the shared repository layer.
- Imported PDFs are copied into app-managed storage.
- Android stores app data under app-private storage and tracks persisted SAF permissions for imported documents/folders.
- Desktop stores app data under:
  - `%APPDATA%\Lunar` on Windows
  - `~/.lunar` as a fallback when `APPDATA` is unavailable

This keeps the first milestone simple while preserving a clean seam for future sync work.

## Tech Stack

- Kotlin Multiplatform
- Compose Multiplatform
- Kotlin Coroutines
- kotlinx-datetime
- kotlinx-serialization
- Okio
- Android `PdfRenderer`
- Apache PDFBox
- Ktor (server placeholder)

## Requirements

Recommended local setup:

- JDK 17 or newer for Gradle and Android tooling
- Android Studio or IntelliJ IDEA with Kotlin/Compose support
- Android SDK 36
- A connected Android device or emulator for Android testing

## How to Run

### Android

From Android Studio, run the `composeApp` Android target on a device or emulator.

From the terminal on Windows:

```powershell
.\gradlew.bat :composeApp:installDebug
```

Useful Android commands:

```powershell
.\gradlew.bat :composeApp:assembleDebug
.\gradlew.bat :composeApp:compileDebugKotlinAndroid
```

### Windows desktop

Run the desktop app directly:

```powershell
.\gradlew.bat :composeApp:run
```

Hot reload development run:

```powershell
.\gradlew.bat :composeApp:hotRunJvm
```

### Linux desktop

On Linux, the equivalent development run is:

```bash
./gradlew :composeApp:run
```

### Shared tests

Run the shared repository/query tests:

```powershell
.\gradlew.bat :shared:jvmTest
```

## How to Build

### Android artifacts

Debug APK:

```powershell
.\gradlew.bat :composeApp:assembleDebug
```

Release APK:

```powershell
.\gradlew.bat :composeApp:assembleRelease
```

Release Android App Bundle:

```powershell
.\gradlew.bat :composeApp:bundleRelease
```

### Desktop artifacts

Build a distributable for the current OS:

```powershell
.\gradlew.bat :composeApp:packageDistributionForCurrentOS
```

Build release distributables for the current OS:

```powershell
.\gradlew.bat :composeApp:packageReleaseDistributionForCurrentOS
```

Windows MSI:

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi
```

Linux DEB:

```bash
./gradlew :composeApp:packageReleaseDeb
```

macOS DMG:

```bash
./gradlew :composeApp:packageReleaseDmg
```

Desktop outputs are written under `composeApp/build/compose/`.

## How to Publish / Distribute

There is not yet an automated publishing pipeline for app stores or package repositories.

Current practical distribution options are:

- Android
  - build a signed `APK` for direct distribution, or
  - build a signed `AAB` for Google Play submission later
- Windows
  - distribute the generated `MSI`
- Linux
  - distribute the generated `DEB`
- macOS
  - distribute the generated `DMG`

What still needs to be added for production publishing:

- release signing configuration and secret management
- CI/CD build automation
- store-specific metadata and release workflows
- installer signing and notarization where required
- update delivery strategy

## Versioning

Release metadata is centralized in `gradle.properties`.

- `lunar.versionName`
  - app-facing release version
- `lunar.versionCode`
  - Android version code
- `lunar.desktopPackageVersion`
  - desktop package version used for `MSI`, `DEB`, and `DMG` packaging
- `lunar.windowsUpgradeUuid`
  - stable Windows installer identity used for upgrade-aware MSI installs

The desktop package version may need to differ from the app-facing version name because native installer formats apply stricter version rules than Android does.

Keep `lunar.windowsUpgradeUuid` unchanged once Windows installers are distributed, otherwise upgrades may install side-by-side instead of replacing older versions.

## Useful Gradle Tasks

### App development

- `:composeApp:run`
- `:composeApp:hotRunJvm`
- `:composeApp:compileKotlinJvm`
- `:composeApp:compileDebugKotlinAndroid`

### Packaging

- `:composeApp:packageDistributionForCurrentOS`
- `:composeApp:packageReleaseDistributionForCurrentOS`
- `:composeApp:packageReleaseMsi`
- `:composeApp:packageReleaseDeb`
- `:composeApp:packageReleaseDmg`

### Verification

- `:shared:jvmTest`
- `:composeApp:jvmTest`
- `:composeApp:testDebugUnitTest`

## Roadmap Direction

Near-term priorities:

- improve PDF viewing ergonomics and performance
- strengthen library organization features
- add thumbnails and richer metadata flows
- prepare sync boundaries for metadata and file transport

Longer-term possibilities:

- setlists
- performance mode
- pedal support
- cross-device sync
- broader platform support

## Notes

- The first milestone is intentionally PDF-only.
- Lunar is a viewing and library-management app, not a notation editor.
- Most application logic is shared; platform code is isolated around import, storage access, and PDF rendering.
