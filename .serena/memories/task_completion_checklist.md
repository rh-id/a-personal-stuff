# Task Completion Checklist

Before declaring any task done, verify:

1. **Build passes**: `./gradlew build` (same as CI `gradlew-build.yml` and release workflow)
2. **Module scope**: changes landed in the correct module; no new cross-feature dependencies (features depend only on `:base` and `:app`)
3. **Translations**: every new/changed user-facing string added to all 11 locales (`values` + de, et, fr, in, is, it, nb, nn, rm, zh)
4. **Room schema changes** (if DB touched):
   - database version bumped
   - explicit `Migration` written and registered in that DB's provider module
   - exported schema JSON updated in module `schemas/`
   - old app→new app upgrade path tested or reasoned through
5. **Backup compatibility** (if entities/backup affected): ZIP backup format version bumped if needed; v1 backup import still works
6. **Cascade behavior** (if Item or entity deletion touched): check the 5 `ItemChangeNotifier` event handlers (usage, purchase, maintenance, checklist-items, reminders incl. WorkManager unique work cancel)
7. **README.md**: user-facing features described in README updated to match behavior (README is the source of truth for features)
8. **Instrumented tests** (if UI/DB behavior changed): consider `./gradlew connectedCheck` locally; CI runs it on emulator
9. **git status clean**: no stray files (e.g. `local.properties`, build outputs) staged

## If releasing a new version
- Bump `versionCode` + `versionName` in `app/build.gradle`
- Create `fastlane/metadata/android/<locale>/changelogs/{newVersionCode}.txt` release notes — historically maintained in ALL 11 store locales (en-US is the fallback locale and mandatory); the file for the release versionCode becomes the GitHub Release body
- Update store `title.txt`/`short_description.txt`/`full_description.txt`/screenshots if features or UI changed (limits: title ≤30 chars, short ≤80 chars)
- Tag `v{versionName}` and push the tag to trigger the release workflow