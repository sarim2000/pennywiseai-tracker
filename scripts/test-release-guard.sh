#!/bin/bash
# Tests release.sh's step-0 guard (clean, current main) in throwaway git repos.
# Every case either stops at the guard or ends at the --dry-run summary, so
# nothing is built, tagged or pushed. Run: bash scripts/test-release-guard.sh
set -u

RELEASE_SH="$(cd "$(dirname "$0")" && pwd)/release.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com \
       GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
FAILED=0

# A bare origin and a clone ($TMP/work) on main, in sync. ($TMP/other) is a
# second clone used to move origin/main ahead of work.
fresh() {
    rm -rf "$TMP/origin.git" "$TMP/work" "$TMP/other"
    git init -q --bare -b main "$TMP/origin.git"
    git clone -q "$TMP/origin.git" "$TMP/work" 2>/dev/null
    cd "$TMP/work"
    git checkout -q -b main
    mkdir -p scripts app
    cp "$RELEASE_SH" scripts/release.sh
    printf 'versionCode = 1\nversionName = "1.0.0"\n' > app/build.gradle.kts
    git add -A && git commit -q -m init && git push -q origin main
    git clone -q "$TMP/origin.git" "$TMP/other"
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

exit $FAILED
