# Store Listing Localization (Crowdin)

PennyWise localizes its **Google Play store listing** (not yet the in-app UI)
with [Crowdin](https://crowdin.com) under an open-source license. This lets the
listing appear in a user's language, which lifts store-listing conversion in
non-English markets.

**Why store copy first:** the install base is ~87% India, with the Gulf
(UAE + Saudi) as the next cluster — so **Hindi is the priority, Arabic second.**
The app UI itself still has hardcoded strings, so only the store metadata is
wired into Crowdin for now. Extracting Compose strings into resources is a
separate, larger effort tracked elsewhere.

## What gets translated

The fastlane / Triple-T "supply" metadata under
`fastlane/metadata/android/en-US/`:

| Source file | Play field | Limit |
|---|---|---|
| `title.txt` | App title | 30 chars |
| `short_description.txt` | Short description | 80 chars |
| `full_description.txt` | Full description | 4000 chars |
| `changelogs/default.txt` | Release notes (fallback) | 500 chars |

Translations land in sibling locale folders, e.g.
`fastlane/metadata/android/hi-IN/title.txt`, ready for `fastlane supply`.

The English → Play-folder mapping lives in [`crowdin.yml`](../crowdin.yml)
(`languages_mapping`). Only Play-supported locale codes are mapped; Hindi and
Arabic are active, others are commented in until you enable them.

## One-time setup

1. **Create the Crowdin project** (Files-based, source language: English).
2. **Add target languages**: start with **Hindi**, then **Arabic**.
3. **Get credentials** and add them as GitHub repo secrets:
   - `CROWDIN_PROJECT_ID` — Project → Tools → API, or the project settings.
   - `CROWDIN_PERSONAL_TOKEN` — a Personal Access Token with project scope.
4. That's it — [`.github/workflows/crowdin.yml`](../.github/workflows/crowdin.yml)
   handles the rest.

## How the sync works

- **Push sources**: when the English store copy changes on `main`, the workflow
  uploads the new source strings to Crowdin for translation.
- **Pull translations**: weekly (and via *Run workflow*), it downloads completed
  translations and opens a PR (`l10n/crowdin` → `main`). Review and merge it; the
  next release picks up the new locale folders automatically.

### Manual run (CLI)

```bash
# install once: npm i -g @crowdin/cli
export CROWDIN_PROJECT_ID=...      # your project id
export CROWDIN_PERSONAL_TOKEN=...  # your PAT

crowdin upload sources          # push English copy
crowdin download                # pull translations into fastlane folders
```

## Adding a language later

1. Add it as a target language in the Crowdin project.
2. Uncomment / add its `languages_mapping` entry in `crowdin.yml` using the
   **Google Play** locale code (e.g. `ru: ru-RU`). If Play doesn't support the
   language as a listing locale, do not add it — `supply` will reject the folder.
3. Re-run the workflow.

> Tip: keep `title.txt` translations within 30 characters. Translators may keep
> the "PennyWise AI" brand in Latin script and only localize "Expense Tracker".
