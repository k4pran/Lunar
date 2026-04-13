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

1. Add sources — local files, local folders, or cloud (Supabase) connections.
2. Each source imports or syncs PDFs into the shared repository.
3. The library aggregates scores from all configured sources.
4. Browse the combined library UI with search, sort, and filters.
5. Open a score in a dialog preview.
6. Expand into fullscreen viewing when needed.
7. Remove individual sources to clean up their contributed scores.

## Storage Model

Lunar currently uses local-first storage.

- Metadata is stored as JSON through the shared repository layer.
- Source configuration is stored as JSON through the shared source registry layer.
- Global app settings are stored as JSON through the shared settings store.
- Imported PDFs are copied into app-managed storage.
- Cloud-synced PDFs are also copied into app-managed storage and reused on later launches.
- Android stores app data under app-private storage and tracks persisted SAF permissions for imported documents/folders.
- Desktop stores app data under:
  - `%APPDATA%\Lunar` on Windows
  - `~/.lunar` as a fallback when `APPDATA` is unavailable

This keeps the first milestone simple while preserving a clean seam for future sync work.

### On-disk cache and offline behavior

Lunar already uses an on-disk filesystem cache rather than a database for the first milestone.

- `library.json` stores the aggregated library metadata
- `sources.json` stores configured local/cloud sources
- `app_settings.json` stores global defaults such as viewer mode, theme, refresh cadence, and network timeouts
- `scores/` stores managed PDF copies for imported and synced scores

This means:

- the library can open from disk even when the network is unavailable
- cached PDFs can still be viewed offline
- cloud refreshes reuse the existing local PDF when the remote version has not changed

The app now surfaces cache details in the **Sources** screen so you can inspect how many PDFs are cached locally and how much disk space they use.

## Cloud Sync

Lunar supports multiple library sources that can be mixed and matched:

- **Local file sources** — pick individual PDF files to import
- **Local folder sources** — scan a folder for all PDFs
- **Cloud sources** — connect to a Supabase Storage bucket

Multiple sources of any type can be active at the same time. The library aggregates scores from all configured sources. Each source can be removed individually, which also cleans up its contributed scores.

Even for cloud-backed libraries, Lunar remains local-first:

- synced PDFs are stored on disk locally
- the shared library metadata stays on disk locally
- later refreshes check for changes, but unchanged PDFs are reused from the local cache

### Cloud provider: Supabase public storage

The cloud implementation is pull-based:

- Lunar calls the Supabase Storage API to list PDFs in your bucket automatically
- No manifest file is required — PDFs are discovered by scanning the bucket
- Metadata (title, composer, collection, tags) is inferred from your folder structure
- Lunar checks on startup and at the auto-refresh interval, or you can force a refresh any time

### Why Supabase first?

Supabase is a practical first step because it gives Lunar:

- hosted file storage for PDFs
- a REST API to list bucket contents without requiring a separate manifest file
- simple public URLs for a first milestone
- room to grow into authenticated/private storage later
- a free starting tier for early testing

### Supabase setup

1. Create a Supabase project.
2. Create a **public** storage bucket, for example `sheet-music`.
3. Upload your PDF files into the bucket using a folder structure that matches one of the layout strategies below.
4. In Lunar, go to the **Sources** tab and tap **Add cloud source**.
5. Enter:
   - **Project URL** — for example `https://your-project.supabase.co`
   - **Bucket name** — for example `sheet-music`
   - **Root directory** — the folder inside the bucket where your PDFs live, for example `scores`. Leave empty to scan the entire bucket.
   - **Layout strategy** — how your folders are organised (see below)
6. Tap **Refresh now**.

Lunar calls `POST /storage/v1/object/list/{bucket}` recursively to discover all PDFs. No manifest file is needed or used.

### Folder layout strategies

Pick the strategy that matches how your PDFs are organised in the bucket.

| Strategy | Bucket layout | What Lunar infers |
|---|---|---|
| **Flat** | `root/*.pdf` | Title from file name |
| **By composer** | `root/{Composer}/*.pdf` | Composer from folder, title from file name |
| **By collection** | `root/{Collection}/*.pdf` | Collection from folder, title from file name |
| **Composer → Collection** | `root/{Composer}/{Collection}/*.pdf` | Both composer and collection |
| **By instrument** | `root/{Instrument}/*.pdf` | Instrument added as a tag, title from file name |
| **By date** | `root/{YYYY-MM}/*.pdf` | Date-added from folder name, title from file name |

#### Examples

```
# Flat — all PDFs in one folder
scores/Für Elise.pdf
scores/Moonlight Sonata.pdf

# By composer — one folder per composer
scores/Beethoven/Für Elise.pdf
scores/Chopin/Nocturne Op.9 No.2.pdf

# Composer → Collection — two folder levels
scores/Beethoven/Piano Sonatas/Moonlight Sonata.pdf
scores/Chopin/Nocturnes/Nocturne Op.9 No.2.pdf
```

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
