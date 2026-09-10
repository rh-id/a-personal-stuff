# Suggested Commands

## Build
- Full build (what CI runs): `./gradlew build` (Windows: `gradlew.bat build` or `./gradlew build` in PowerShell)
- Single module: `./gradlew :base:build` (substitute module: app, base, barcode, item-usage, item-purchase, item-maintenance, item-reminder, item-checklist, settings)
- Debug APK: `./gradlew :app:assembleDebug`
- Release (matches android-release.yml): `./gradlew build`

## Test
- Instrumented tests on emulator (CI: android-emulator-test.yml): `./gradlew connectedCheck`
- Module-scoped: `./gradlew :item-usage:connectedCheck`

## Lint / checks
- Lint runs as part of `./gradlew build` (CI does not invoke a separate lint task)

## Other
- `./gradlew tasks` — list available tasks
- Gradle wrapper is committed (`gradlew` / `gradlew.bat`); Java 17 toolchain (see code_style memory)
- `local.properties` at repo root is machine-specific (Android Studio SDK paths) — never edit/commit
## Release (tag-triggered)
- Releases are triggered by pushing a tag: `git tag v{versionName} && git push origin v{versionName}` → `android-release.yml` runs `./gradlew build`, signs APKs (secrets: ALIAS, KEY_PASSWORD, KEY_STORE_PASSWORD, SIGNING_KEY), and creates a GitHub Release
- `fastlane/` contains NO Fastfile/Appfile — it is only Google Play store metadata in the fastlane supply structure (see task_completion_checklist for release steps)

## Store metadata (fastlane/)
- Directory: `fastlane/metadata/android/<locale>/` — title.txt (≤30 chars), short_description.txt (≤80 chars), full_description.txt, images/ (icon 512x512, featureGraphic 1024x500, phoneScreenshots/), changelogs/{versionCode}.txt
- See `fastlane/README.md` for the full guide