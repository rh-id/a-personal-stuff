# Fastlane Metadata

This directory contains store listing metadata following the [fastlane supply](https://docs.fastlane.tools/actions/supply/) structure for Google Play.

## Directory Structure

```
fastlane/
└── metadata/
    └── android/
        ├── en-US/                                 # English (US) — fallback locale
        │   ├── title.txt                          # App title (max 30 characters)
        │   ├── short_description.txt              # Short description (max 80 characters)
        │   ├── full_description.txt               # Full store listing description
        │   ├── images/
        │   │   ├── featureGraphic.png             # Feature graphic (1024x500)
        │   │   ├── icon.png                       # App icon (512x512)
        │   │   └── phoneScreenshots/              # Phone screenshots
        │   │       ├── 1.png
        │   │       ├── 2.png
        │   │       ├── 3.png
        │   │       ├── 4.png
        │   │       ├── 5.png
        │   │       ├── 6.png
        │   │       ├── 7.png
        │   │       └── 8.png
        │   └── changelogs/
        │       └── {versionCode}.txt              # Per-version release notes
        ├── de-DE/                                 # German
        ├── et/                                    # Estonian
        ├── fr-FR/                                 # French
        ├── id/                                    # Indonesian
        ├── is-IS/                                 # Icelandic
        ├── it-IT/                                 # Italian
        ├── nb-NO/                                 # Norwegian Bokmål
        ├── nn-NO/                                 # Norwegian Nynorsk
        ├── rm/                                    # Romansh
        └── zh-CN/                                 # Chinese (Simplified)
```

## How Changelogs Work

Changelog files are named by `versionCode` (e.g., `16.txt` corresponds to `versionCode 16` in `app/build.gradle`); there is no `default.txt` fallback. At build time `app/build.gradle` copies `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt` to `app/build/changelog.txt`, and the release pipeline (`android-release.yml`) uploads it as `changelog.txt` / uses it as the GitHub Release body.

## How to Update for a New Release

1. Bump `versionCode` and `versionName` in `app/build.gradle`
2. Create a new changelog file: `metadata/android/en-US/changelogs/{newVersionCode}.txt`
3. Update `full_description.txt` or `short_description.txt` if features have changed
4. Replace screenshots in `images/phoneScreenshots/` if the UI has changed
5. Commit, tag (`v{versionName}`), and push the tag to trigger the release workflow

## Adding a New Locale

To add support for another language:

1. Create `metadata/android/{locale}/` (e.g., `de-DE/` for German)
2. Add `title.txt`, `short_description.txt`, and `full_description.txt`
3. Optionally add `images/` with locale-specific screenshots
4. Optionally add `changelogs/` with translated release notes

## References

- [fastlane supply documentation](https://docs.fastlane.tools/actions/supply/)
- [fastlane Android setup guide](https://docs.fastlane.tools/getting-started/android/setup/)