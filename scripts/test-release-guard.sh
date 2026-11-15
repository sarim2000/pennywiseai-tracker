#!/bin/bash
# Tests release.sh's step-0 guard (clean, current main) in throwaway git repos.
# Every case either stops at the guard or ends at the --dry-run summary, so
# nothing is built, tagged or pushed. Run: bash scripts/test-release-guard.sh
set -u

RELEASE_SH="$(cd "$(dirname "$0")" && pwd)/release.sh"
TMP=$(mktemp -d) || exit 1
trap 'rm -rf "$TMP"' EXIT
cd "$TMP" || exit 1  # never run setup from the real checkout
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com \
       GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
FAILED=0

# Stand-in for `gh run list`: prints $FAKE_CI (default: a passing run) the way
# release.sh's --jq formats it, or fails like an API error when it's "error".
mkdir -p "$TMP/bin"
cat > "$TMP/bin/gh" <<'GH'
#!/bin/bash
[ "${FAKE_CI-}" = error ] && { echo "HTTP 502" >&2; exit 1; }
printf '%s\n' "${FAKE_CI-completed success}"
GH
chmod +x "$TMP/bin/gh"
export PATH="$TMP/bin:$PATH"

# A bare origin and a clone ($TMP/work) on main, in sync. ($TMP/other) is a
# second clone used to move origin/main ahead of work.
# Setup is one && chain: any failed step aborts the run, so a failed clone can
# never leave later writes, commits or pushes running in the real checkout.
fresh() {
    cd "$TMP" &&
    rm -rf "$TMP/origin.git" "$TMP/work" "$TMP/other" &&
    git init -q --bare -b main "$TMP/origin.git" &&
    git clone -q "$TMP/origin.git" "$TMP/work" 2>/dev/null &&
    cd "$TMP/work" &&
    git checkout -q -b main &&
    mkdir -p scripts app &&
    cp "$RELEASE_SH" scripts/release.sh &&
    printf 'versionCode = 1\nversionName = "1.0.0"\n' > app/build.gradle.kts &&
    git add -A && git commit -q -m init && git push -q origin main &&
    git clone -q "$TMP/origin.git" "$TMP/other" ||
        { echo "FAIL test setup"; exit 1; }
}

advance_origin() {
    (cd "$TMP/other" && git commit -q --allow-empty -m remote && git push -q origin main)
}

# expect <name> <exit code> <text the output must contain> -- <release.sh args>
expect() {
    local name=$1 code=$2 text=$3; shift 4
    local out rc
    out=$(bash scripts/release.sh "$@" 2>&1); rc=$?
    if [ "$rc" = "$code" ] && [[ "$out" == *"$text"* ]]; then
        echo "ok   $name"
    else
        echo "FAIL $name (exit $rc, wanted $code with \"$text\")"; echo "$out" | sed 's/^/     /'
        FAILED=1
    fi
}

fresh
expect "clean main passes"            0 "DRY RUN SUMMARY"          -- --dry-run --no-claude
out=$(bash scripts/release.sh --dry-run --no-claude 2>&1)
[[ "$out" != *"⚠️"* ]] && echo "ok   clean main has no warnings" || { echo "FAIL clean main warned"; FAILED=1; }

fresh; echo x >> app/build.gradle.kts
expect "unstaged edit refused"        1 "Uncommitted changes"      -- patch --yes

fresh; echo x >> app/build.gradle.kts; git add app/build.gradle.kts
expect "staged edit refused"          1 "Uncommitted changes"      -- patch --yes

fresh; touch untracked.txt
expect "untracked file allowed"       0 "DRY RUN SUMMARY"          -- --dry-run --no-claude

fresh; git checkout -q -b feature
expect "other branch refused"         1 "not main"                 -- patch --yes
expect "other branch warns on dry run" 0 "not main"                -- --dry-run --no-claude

fresh; git commit -q --allow-empty -m local
expect "ahead of origin refused"      1 "HEAD is not origin/main"  -- patch --yes

fresh; advance_origin
expect "behind origin refused"        1 "HEAD is not origin/main"  -- patch --yes

# origin/main stays stale under this refspec, so only FETCH_HEAD sees the new commit.
fresh; advance_origin; git config remote.origin.fetch '+refs/heads/nothing:refs/remotes/origin/nothing'
expect "behind under custom refspec"  1 "HEAD is not origin/main"  -- patch --yes

fresh; git remote set-url origin "$TMP/missing.git"
expect "fetch failure refused"        1 "Could not fetch"          -- patch --yes

fresh; export FAKE_CI="in_progress "
expect "CI still running refused"     1 "not 'completed success'"  -- patch --yes
export FAKE_CI="completed failure"
expect "CI failed refused"            1 "completed failure"        -- patch --yes
expect "CI failed warns on dry run"   0 "completed failure"        -- --dry-run --no-claude
export FAKE_CI=""
expect "no CI run refused"            1 "no run found"             -- patch --yes
export FAKE_CI=error
expect "CI lookup failure refused"    1 "Could not read CI"        -- patch --yes
unset FAKE_CI

exit $FAILED
