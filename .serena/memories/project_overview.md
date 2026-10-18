# Project Overview

**a-personal-stuff** — open-source native Android (Java) app for tracking personal belongings: expiration dates, stock levels, usage, purchases, maintenance, reminders, and checklists.

- Production-ready rewrite of the Flutter app "My-Personal-Stuff"
- Serves as a practical showcase of two author-owned libraries: [a-navigator](https://github.com/rh-id/a-navigator) (navigation) and [a-provider](https://github.com/rh-id/a-provider) (DI)
- Repo: `rh-id/a-personal-stuff`; CI via GitHub Actions (build, emulator tests, release)
- Key features: dashboard inventory overview card with alert-aware deep links (rows and the daily digest notification open the item list pre-filtered behind a clearable chip), daily WorkManager alert digest (configurable item-name cap 3/5/10), low-stock thresholds, manual + automatic scheduled backup/restore (ZIP format v2), XLSX export (API 26+), ZXing barcode scanning, Material You dynamic color toggle, item view mode (detailed/compact), 11 UI languages
- Authoritative documentation: `README.md` (~875 lines) — consult it before changing user-facing behavior; do not duplicate it here.