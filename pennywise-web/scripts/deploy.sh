#!/usr/bin/env bash
#
# Redeploy pennywise-web to Cloudflare.
#
# Cloudflare secrets (DATABASE_URL / DATABASE_PASSWORD / DATABASE_USER) are
# stored separately from the Worker code and PERSIST across `wrangler deploy`.
# This script therefore never re-uploads them on a normal redeploy, so you can
# ship a new build without knowing the current DB password.
#
# It only offers to set a secret when it's missing entirely (e.g. first deploy).
# To intentionally rotate a secret, run:  wrangler secret put DATABASE_PASSWORD
#
set -euo pipefail

# Always operate from the project root (the dir containing wrangler.jsonc).
cd "$(dirname "$0")/.."

# Secrets that MUST exist for a working production deploy. Missing ones block
# the deploy (prompt when interactive, hard-fail when not).
REQUIRED_SECRETS=(DATABASE_URL DATABASE_PASSWORD)
# Optional — the server falls back to a sensible default (DATABASE_USER=postgres),
# so a missing one is reported but never blocks the deploy.
OPTIONAL_SECRETS=(DATABASE_USER)

# Resolve wrangler without depending on any single package manager:
#   1. the dependency installed in this package (preferred — no network),
#   2. npx --yes (npm users; --yes skips the install confirmation prompt that
#      would otherwise hang),
#   3. bunx (bun users).
LOCAL_WRANGLER="node_modules/.bin/wrangler"
if [ -x "$LOCAL_WRANGLER" ]; then
  wrangler() { "$LOCAL_WRANGLER" "$@"; }
elif command -v npx >/dev/null 2>&1; then
  wrangler() { npx --yes wrangler "$@"; }
elif command -v bunx >/dev/null 2>&1; then
  wrangler() { bunx wrangler "$@"; }
else
  echo "error: wrangler not found. Run 'npm install' (or 'bun install') first." >&2
  exit 1
fi

# Is stdin a terminal? If not (CI, piped), we can't prompt, so a missing
# required secret must abort rather than silently deploy a broken Worker.
interactive=0
[ -t 0 ] && interactive=1

echo "==> Checking existing Cloudflare secrets (these are preserved on redeploy)"

# `wrangler secret list` returns a JSON array of {name, type}. Fall back to an
# empty list if the call fails (e.g. first-ever deploy before the Worker exists).
existing="$(wrangler secret list --format json 2>/dev/null || echo '[]')"

has_secret() { printf '%s' "$existing" | grep -q "\"$1\""; }

missing_required=()
for name in "${REQUIRED_SECRETS[@]}"; do
  if has_secret "$name"; then
    echo "    ✓ $name  (already set — will NOT be changed)"
  else
    echo "    ✗ $name  (missing — required)"
    missing_required+=("$name")
  fi
done
for name in "${OPTIONAL_SECRETS[@]}"; do
  if has_secret "$name"; then
    echo "    ✓ $name  (already set — will NOT be changed)"
  else
    echo "    • $name  (not set — server uses its default)"
  fi
done

# Handle missing required secrets.
if [ "${#missing_required[@]}" -gt 0 ]; then
  echo
  if [ "$interactive" -eq 0 ]; then
    echo "error: ${#missing_required[@]} required secret(s) missing and no TTY to set them:" >&2
    printf '       - %s\n' "${missing_required[@]}" >&2
    echo "       Refusing to deploy a Worker without its database secrets." >&2
    echo "       Set them first:  wrangler secret put <NAME>" >&2
    exit 1
  fi
  echo "==> ${#missing_required[@]} required secret(s) are not set yet."
  for name in "${missing_required[@]}"; do
    read -r -p "    Set $name now? [y/N] " answer
    case "$answer" in
      [yY]*)
        wrangler secret put "$name"
        ;;
      *)
        echo "error: $name is required; aborting deploy." >&2
        exit 1
        ;;
    esac
  done
fi

echo
echo "==> Deploying Worker (secrets above are left untouched)"
wrangler deploy "$@"

echo
echo "==> Done. Secrets were preserved; only code/vars/bindings were updated."
