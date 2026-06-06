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

## Translation style guide (paste into the Crowdin project)

Put this in **Crowdin → Settings → project description / translator instructions**
so it shows to every translator and MT pass. It's the single biggest lever for
not getting stiff, robotic translations.

> **Voice:** write the way real people in this market *speak*, not formal/literary
> language. For Hindi specifically, use everyday conversational Hindi in
> Devanagari and **keep the English loanwords people actually say** — "SMS",
> "bank", "app", "budget", "AI". Avoid Sanskritized/government-form Hindi.
>
> **Always keep in Latin / untranslated:** `PennyWise AI`, `PennyWise`, `Pro`,
> `SMS`, `AI`, `AGPL`, `Qwen`, `UPI`, currency symbols (₹ $ AED …) and numbers.
>
> **Length:** the app **title** must stay ≤ 30 characters and the **short
> description** ≤ 80. If a literal translation is too long, shorten the meaning —
> don't exceed the limit.
>
> **Tone:** benefit-first and friendly, same as the English ("auto-reads your
> bank SMS", "private, on-device"). Don't translate word-for-word; translate the
> *promise*.

### Glossary

Import [`crowdin/glossary.csv`](../crowdin/glossary.csv) under **Crowdin →
Glossaries → Upload**. It lists the do-not-translate terms above with notes, so
they surface inline while translating and Crowdin's QA flags it if they get
changed.

> Note: this only governs the **store listing** copy. The in-app UI is still
> English-only until its strings are extracted into resources.
