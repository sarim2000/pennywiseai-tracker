# Release Process

Releases are cut by **`scripts/release.sh`**, which replicates
`.github/workflows/release.yml` locally. This script is the **single source of
truth** for the version and changelogs.

> ⚠️ **Never hand-edit the version or changelogs.** Do not touch `versionCode` /
> `versionName` in `app/build.gradle.kts`, and never hand-write a
> `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. The script
> owns all of that — editing it by hand will conflict with the next release.

## TL;DR

> ⚠️ **Run from an up-to-date `main` with a clean working tree.** The script
> commits the version bump to your *current* branch, tags that commit, and then
> runs `git push origin main`. On any other branch — or a stale or dirty `main`
> — it will tag the wrong commit and push a `main` that doesn't contain the
> release. It does **not** guard against this for you.

```bash
git checkout main && git pull --ff-only    # be on current main
git status --porcelain                      # confirm a clean tree (expect no output)

# Preview first — prints the computed version + release notes, changes nothing:
./scripts/release.sh patch --dry-run

# Cut the release (non-interactive):
./scripts/release.sh patch --yes
```

The bump keyword is the usual SemVer choice:

- `patch` — bug fixes, minor improvements (2.17.0 → 2.17.1)
- `minor` — new features (2.17.0 → 2.18.0)
- `major` — breaking changes / major overhaul (2.17.0 → 3.0.0)

## What the script does in one run

1. Bumps `versionName` (per the keyword) and increments `versionCode` in
   `app/build.gradle.kts`.
2. Generates release notes from the commits since the last tag via the Claude
   Agent SDK (falling back to a plain commit list, or with `--no-claude`). From
   the same structured notes it writes:
   - the store changelog `changelogs/<versionCode>.txt` + `default.txt`, and
   - `RELEASE_NOTES.md` for the GitHub release.
3. Builds, renames, and (F-Droid) computes SHA256 for the standard + F-Droid
   APKs.
4. Commits `chore(release): bump version to X [skip ci]`, tags `vX`, and pushes.
5. Cuts the GitHub release with the APKs + `.sha256` attached.

If the script is interrupted, a trap reverts the version bump and changelog
changes so you're left in a clean state.

## Flags

| Flag | Effect |
|---|---|
| `-y`, `--yes` | Auto-confirm every prompt (non-interactive / CI). |
| `--dry-run` | Print the plan + release notes and change nothing. Run this first. |
| `--no-claude` | Skip Claude note generation; use the commit-list fallback. |
| `--play` | After building, also build the `.aab` and upload it to Play Console as a draft (via `scripts/upload-play.sh`). |
| `--web` | Also deploy `pennywise-web` to Cloudflare (otherwise a routine app release never redeploys the site). |

One-shot headless release with a Play draft:

```bash
./scripts/release.sh patch --yes --play
```

## Prerequisites (one-time)

- **Signing** — the release build needs the signing config wired up (keystore +
  credentials) the same way `release.yml` consumes its GitHub secrets. If the
  workflow shows an unsigned APK, the signing config/secrets are misconfigured.
- **`--play`** additionally needs the Play Console service-account credentials
  that `scripts/upload-play.sh` expects.
- **`--web`** needs the `pennywise-web` deploy toolchain (`bun`, Cloudflare
  auth); the script runs `pennywise-web/scripts/deploy.sh`.

## Conventional commits

Release notes are generated from commit messages, so use conventional commits
(`feat:`, `fix:`, `docs:`, `chore:` …). Better commit messages → better
auto-generated changelog and GitHub release notes.
