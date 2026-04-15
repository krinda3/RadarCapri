# AGENTS.md

## Repo Shape
- Single-module Android app: the only Gradle project is `:app` (`settings.gradle.kts`).
- Build/runtime baseline: `compileSdk`/`targetSdk` 35, `minSdk` 26, Java/Kotlin JVM target 11, Jetpack Compose enabled (`app/build.gradle.kts`).
- UI is Compose-only. `MainActivity.kt` handles permission requests and switches between `ui/MainScreen.kt` and `ui/ConfigScreen.kt`; there are no XML layout files.
- `MainViewModel.kt` owns screen state, starts/stops `RadarService`, and receives service updates through the app broadcast `RadarService.ACTION_STATUS_UPDATE`.
- Most product logic lives in `app/src/main/java/com/pridalac/radarcapri/service/RadarService.kt`: location updates, Lufop fetch cadence, direction filtering, TTS/beep alerts, passed-radar detection, distance tracking, notification updates, and KML export.
- There is no DI framework. `MainViewModel` and `RadarService` instantiate `PreferencesManager`, `RadarRepository`, and related collaborators directly.
- Data flow is `RadarService -> RadarRepository -> LufopApiService -> SpeedCamera`. New settings belong in `preferences/PreferencesManager.kt` and must also be wired into `ui/ConfigScreen.kt`.
- User-facing copy and TTS are Spanish. Keep new strings, labels, and spoken text in Spanish unless the task is explicitly about localization.

## Commands
- Use the checked-in Windows wrapper from repo root: `./gradlew.bat`.
- Build APK: `./gradlew.bat :app:assembleDebug`
- Install on a connected device/emulator: `./gradlew.bat :app:installDebug`
- Main verification command: `./gradlew.bat :app:check`
- Verified by `:app:check --dry-run`: `:app:check` already runs `:app:lint` and `:app:testDebugUnitTest`, so do not document a separate required `lint -> test` order unless you have a task-specific reason.
- Focused verification:
- `./gradlew.bat :app:lintDebug`
- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:testDebugUnitTest --tests "com.example.MyTest"`
- `./gradlew.bat :app:connectedDebugAndroidTest`
- There are currently no repo test sources under `app/src/test` or `app/src/androidTest`, so test tasks exist but have nothing meaningful to run yet.

## Android Gotchas
- `AndroidManifest.xml` declares fine/coarse/background location, foreground-service, foreground-service-location, notification, internet, and wake-lock permissions. If you change service startup/background behavior, keep `MainActivity`'s permission flow aligned.
- Network access is locked down by `app/src/main/res/xml/network_security_config.xml`: only HTTPS traffic to `api.lufop.net` is allowed. Any new host requires updating that XML too.
- The external radar API is hardcoded in `network/LufopApiService.kt` (`https://api.lufop.net/api`, country `es`, format `json`, margin `50`). Preserve those defaults unless the task is explicitly changing backend behavior.
- KML export is implemented inside `RadarService` and writes to the app-specific external documents directory via `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`; it does not use SAF or a storage-permission flow.
- `gradle.properties` enables configuration cache and contains several deprecated AGP flags. Gradle currently prints deprecation warnings during configuration even on successful runs; treat them as existing repo state, not as evidence your change failed.
- `local.properties` is checked in but machine-specific. Do not rely on or update the committed `sdk.dir` unless the user explicitly asks.
