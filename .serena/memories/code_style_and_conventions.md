# Code Style & Conventions

- **Language: Java only** (no Kotlin anywhere). `sourceCompatibility`/`targetCompatibility` = `JavaVersion.VERSION_17` in all modules.
- **UI: View-based, no Fragments/Compose.** All pages are `StatefulView<Activity>` with `createView()`/`dispose()`. Never introduce Activities/Fragments per screen.
- **Async: RxJava3 everywhere.** Commands return `Single<T>`/`Observable<T>`; events via `PublishSubject` in `*ChangeNotifier` classes exposed as `Flowable`. No coroutines.
- **DI: a-provider only.** Register in `ProviderModule` implementations; never instantiate services/pages directly.
- **Naming patterns (follow them strictly):**
  - Commands: `*Cmd` (`NewItemCmd`, `QueryItemCmd`, `PagedItemMaintenanceCmd`)
  - Notifiers: `*ChangeNotifier` (e.g. `ItemChangeNotifier`, `ItemMaintenanceChangeNotifier`)
  - Event handlers: `*EventHandler` (cross-module cascade logic)
  - Repos: `*Repo` wrapping `*Dao`
  - Provider modules: `*ProviderModule` (e.g. `ItemMaintenanceCmdProviderModule`)
  - Pages: `*Page` (e.g. `ItemUsagesPage`, `ItemUsageDetailPage`); lists use plural, details use `Detail` suffix
- **Room**: one database per feature module; schemas exported to module `schemas/` dir; version bumps require explicit `Migration` registered in that DB's provider module (`DbMigration` in `:base`).
- **String resources: 11 locales must all be updated** when adding/changing UI text: `values` (en) + `values-de`, `-et`, `-fr`, `-in` (Indonesian), `-is`, `-it`, `-nb`, `-nn`, `-rm`, `-zh`. Special dirs `values-land`, `values-night` also exist. Missing translations = incomplete task.
- **Backup format**: ZIP format is versioned (currently v2 = low-stock threshold; v1 imports must keep working). Any schema/behavior change to backups → bump format version + keep old importer.
- Respect module boundaries: features must not depend on each other, only on `:base` (enforced by design, not tooling).
- **Store metadata locales ≠ res/ locale codes** (`fastlane/metadata/android/`): res `values-de`→`de-DE`, `values-fr`→`fr-FR`, `values-in`→`id` (Indonesian!), `values-is`→`is-IS`, `values-it`→`it-IT`, `values-nb`→`nb-NO`, `values-nn`→`nn-NO`, `values-zh`→`zh-CN`; `et` and `rm` are identical. A new app language needs BOTH a `values-*` dir and a `fastlane/metadata/android/<store-locale>/` dir.