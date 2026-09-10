# External Dependencies Instructions

## Author-owned libraries (core to the architecture — read their repos before changing navigation/DI)
- **[a-navigator](https://github.com/rh-id/a-navigator)**: single-activity navigation. `Navigator`, `StatefulView<T>`, `StatefulViewFactory`, route strings. Routes registered in `NavigatorProvider` (`:app`).
- **[a-provider](https://github.com/rh-id/a-provider)**: lightweight DI. `Provider` scopes (Application/Activity/StatefulView), `ProviderModule` with `register()`, `registerLazy()`, `registerAsync()`, `registerPool()`. Initialized in `MainApplication`/`MainActivity`.
- Both resolve via **JitPack** (`maven { url 'https://jitpack.io' }` in `settings.gradle`). Repositories are locked down: `RepositoriesMode.FAIL_ON_PROJECT_REPOS` — never add repos in module build files.

## Other notable dependencies
- **RxJava3**: all async/event flows (see code_style memory)
- **Room**: per-module databases, schema export on
- **WorkManager**: daily alert digest (`AlertDigestWorker` in `:app`) and reminder alarms (`:item-reminder`)
- **ZXing** (via `:barcode` module): 1D barcode decoding with Camera1 API — no CameraX/Camera2
- **XLSX export**: requires API 26+; export button hidden below that (do not lower this silently)

## Environment constraints
- Java 17 toolchain required
- `local.properties` holds machine-specific SDK paths; never commit
- CI mirrors these constraints (GitHub Actions: gradlew-build.yml, android-emulator-test.yml, android-release.yml)