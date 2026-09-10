# Module Architecture

Modular Single-Activity Architecture. 9 Gradle modules (see `settings.gradle`):

| Module | Purpose |
|--------|---------|
| `:app` | `MainActivity`, `MainApplication`, DI setup, `NavigatorProvider` (route → StatefulViewFactory), dashboard overview card (`DashboardCmd`, `InventoryStatsCalculator`), daily alert digest (`InventoryAlertScheduler`, `AlertDigestWorker`), backup/restore, XLSX export |
| `:base` | Shared entities (`Item`, `ItemImage`, `ItemTag`), DAOs, Rx utilities, `DbMigration`, Room migrations, shared UI (`AppBar`, `ImageSV`, `SelectionPage`) |
| `:barcode` | Camera1 + ZXing scanning (`ScanBarcodePage`, `ScanBarcodePreview`) |
| `:item-usage` | `ItemUsage` entity/DAO/commands/pages |
| `:item-purchase` | `ItemPurchase` entity/DAO/commands/pages + cascade-delete event handler |
| `:item-maintenance` | `ItemMaintenance` entity/DAO/commands/pages |
| `:item-reminder` | `ItemReminder`, WorkManager alarm scheduling |
| `:item-checklist` | `ItemChecklist`, `ItemChecklistItem` |
| `:settings` | `SettingsPage`, theme, alert toggles/lead time, licenses page (licenses.html generated at build time) |

## Database strategy
Multiple Room databases, one per feature module (decoupled, independently extractable):
- `AppDatabase` (base): AndroidNotification, Item, ItemImage, ItemTag — **version 2** (`DbMigration.MIGRATION_1_2` adds `item.min_amount` low-stock threshold)
- `ItemUsageDatabase` — **version 2** (adds `usage_date_time`)
- `ItemMaintenanceDatabase`, `ItemChecklistDatabase`, `ItemPurchaseDatabase`, `ItemReminderDatabase` — one DB each
- Schemas exported per module (`schemas/` dirs); explicit migrations registered in each DB's provider module

## Core patterns (all modules follow these)
1. **StatefulView navigation**: single `MainActivity` hosts all screens; pages implement `StatefulView<Activity>` (lifecycle: `createView()` → `dispose()`), NO Fragments/Activities per screen. Route strings in `Routes`; mapped in `NavigatorProvider`.
2. **a-provider DI**: scopes = Application (`MainApplication`) → Activity (`MainActivity`) → per-StatefulView. Registration via `ProviderModule` implementations: `register()`, `registerLazy()`, `registerAsync()`, `registerPool()`.
3. **Command pattern**: stateless `*Cmd` classes (e.g. `NewItemCmd`, `QueryItemCmd`, `PagedItemMaintenanceCmd`); run on `ExecutorService`; return RxJava `Single<T>`/`Observable<T>`.
4. **ChangeNotifier event bus**: per module, RxJava `PublishSubject` → `Flowable` streams (`itemAdded()` → `getAddedItemFlow()` etc.). `*EventHandler` classes subscribe cross-module: 5 handlers listen to base `ItemChangeNotifier` for cascade deletes on item deletion (usage, purchase, maintenance, checklist items, reminders + WorkManager work cancel).
5. **Repository pattern**: `*Repo` wraps DAOs (e.g. `AndroidNotificationRepo` → `AndroidNotificationDao`).

When adding a feature: entity → DAO → database/provider module → commands → change notifier (+ event handler if cross-module) → StatefulView page(s) → route registration in `:app`.