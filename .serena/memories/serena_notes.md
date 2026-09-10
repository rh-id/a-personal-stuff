# Serena Usage Notes (for agents working in this project)

## Language server
- Backend: LSP with **Eclipse JDT LS** (`.serena/project.yml` → `language_servers: [java]`). Java only — Kotlin is not used in this repo.
- JDT LS resolves Android/Gradle classpaths lazily: if symbol tools return empty results or errors early in a session, the server is still indexing — wait/retry. Running a Gradle build first improves resolution.
- LSP limitations here: `find_declaration` may fail for symbols from external dependencies (Android SDK, RxJava3, Room, a-navigator/a-provider); `rename_symbol` renames code symbols only (not files/dirs).

## Recommended workflow in this repo
1. Start from `get_symbols_overview` on a module file, or `find_symbol` with name paths like `ItemMaintenanceChangeNotifier/itemUpdated` — this codebase has very consistent `*Cmd`/`*ChangeNotifier`/`*Repo`/`*Page` naming, so symbol search is highly effective.
2. Prefer `find_referencing_symbols` over pattern search when tracing event flows (e.g. who subscribes to `ItemChangeNotifier`).
3. Edit at symbol level (`replace_symbol_body`, `insert_after_symbol`) for classes/methods; use `safe_delete_symbol` and check its reference report before deleting.
4. Verify after edits with `get_diagnostics_for_file`, then `./gradlew build` (see suggested_commands memory).

## Memories
- Memories live in `.serena/memories/` and are meant to be committed (only `.serena/cache` and `project.local.yml` are gitignored).
- Keep memories small and non-duplicative: `README.md` (848 lines) is authoritative for features/architecture — reference it instead of copying.
- Update (don't append) stale memories; delete memories that no longer hold true.