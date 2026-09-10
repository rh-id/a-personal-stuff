# Project Overview

**a-personal-stuff** — open-source native Android (Java) app for tracking personal belongings: expiration dates, stock levels, usage, purchases, maintenance, reminders, and checklists.

- Production-ready rewrite of the Flutter app "My-Personal-Stuff"
- Serves as a practical showcase of two author-owned libraries: [a-navigator](https://github.com/rh-id/a-navigator) (navigation) and [a-provider](https://github.com/rh-id/a-provider) (DI)
- Repo: `rh-id/a-personal-stuff`; CI via GitHub Actions (build, emulator tests, release)
- Key features: dashboard inventory overview card, daily WorkManager alert digest, low-stock thresholds, ZIP backup/restore (format v2), XLSX export (API 26+), ZXing barcode scanning, 11 UI languages
- Authoritative documentation: `README.md` (848 lines) — consult it before changing user-facing behavior; do not duplicate it here.