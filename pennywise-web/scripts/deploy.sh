#!/usr/bin/env bash
#
# Redeploy pennywise-web to Cloudflare.
#
# Cloudflare secrets (DATABASE_URL / DATABASE_USER / DATABASE_PASSWORD) are
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

REQUIRED_SECRETS=(DATABASE_URL DATABASE_USER DATABASE_PASSWORD)

echo "==> Checking existing Cloudflare secrets (these are preserved on redeploy)"

# `wrangler secret list` returns a JSON array of {name, type}. Fall back to an
# empty list if the call fails (e.g. first-ever deploy before the Worker exists).
existing="$(bunx wrangler secret list --format json 2>/dev/null || echo '[]')"

missing=()
for name in "${REQUIRED_SECRETS[@]}"; do
  if printf '%s' "$existing" | grep -q "\"$name\""; then
    echo "    ✓ $name  (already set — will NOT be changed)"
  else
    echo "    ✗ $name  (missing)"
    missing+=("$name")
  fi
done

# Prompt to set any missing secrets before deploying.
if [ "${#missing[@]}" -gt 0 ]; then
  echo
  echo "==> ${#missing[@]} secret(s) are not set yet."
  for name in "${missing[@]}"; do
    read -r -p "    Set $name now? [y/N] " answer
    case "$answer" in
      [yY]*)
        bunx wrangler secret put "$name"
        ;;
      *)
        echo "    Skipping $name — deploy will continue, but the server"
        echo "    will fall back to its localhost default for this value."
        ;;
    esac
  done
fi

echo
echo "==> Deploying Worker (secrets above are left untouched)"
bunx wrangler deploy "$@"

echo
echo "==> Done. Secrets were preserved; only code/vars/bindings were updated."
