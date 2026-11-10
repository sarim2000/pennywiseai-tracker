#!/usr/bin/env bash
# Package dependency matrix for :app, :feature:* and :core:* (all in
# com.pennywiseai.tracker), then every pair of packages that import each other
# (a cycle).
#
# Counts `import com.pennywiseai.tracker.…` lines in main + flavor sources
# (R and BuildConfig excluded). Fully qualified names in code and same-package
# references are not counted, so read the numbers as a trend, not a census.
#
# Usage: scripts/arch/package-deps.sh [depth]
#   depth 1 (default) = top-level packages (data, ui, …)
#   depth 2           = one level deeper (ui.components, data.repository, …)
set -euo pipefail
cd "$(dirname "$0")/../.."

# shellcheck disable=SC2016  # the single-quoted text is an awk program
# Feature and core modules keep com.pennywiseai.tracker packages, so they count too.
find app/src/main app/src/standard app/src/fdroid feature/*/src/main core/*/src/main -name '*.kt' -print0 |
xargs -0 awk -v depth="${1:-1}" '
function key(p, strip,   a, n, i, k) {
  sub(/^com\.pennywiseai\.tracker\.?/, "", p)
  n = split(p, a, ".")
  if (strip) n--                               # drop the imported symbol
  k = ""
  for (i = 1; i <= n && i <= depth; i++) {
    if (a[i] ~ /^[A-Z*]/) break                # a class or wildcard, not a package
    k = k (k == "" ? "" : ".") a[i]
  }
  return k == "" ? "(root)" : k
}
function add(p) { if (!(p in seen)) { seen[p] = 1; pk[++np] = p } }
FNR == 1 { src = "" }
/^package com\.pennywiseai\.tracker/ { src = key($2, 0); add(src) }
/^import com\.pennywiseai\.tracker\./ && src != "" && $2 !~ /\.(R|BuildConfig)$/ {
  d = key($2, 1); add(d); if (d != src) n[src, d]++
}
END {
  for (i = 2; i <= np; i++)                     # insertion sort (no asort in BSD awk)
    for (j = i; j > 1 && pk[j-1] > pk[j]; j--) { t = pk[j]; pk[j] = pk[j-1]; pk[j-1] = t }
  printf "Imports between packages, depth %d (row imports column)\n%19s", depth, ""
  for (j = 1; j <= np; j++) printf "%5d", j
  print ""
  for (i = 1; i <= np; i++) {
    printf "%2d %-16s", i, pk[i]
    for (j = 1; j <= np; j++) printf "%5s", ((pk[i], pk[j]) in n) ? n[pk[i], pk[j]] : "."
    print ""
  }
  print "\nCycles (packages that import each other; counts are a->b / b->a)"
  for (i = 1; i <= np; i++) for (j = i + 1; j <= np; j++)
    if (((pk[i], pk[j]) in n) && ((pk[j], pk[i]) in n))
      printf "  %-24s <-> %-24s %4d / %d\n", pk[i], pk[j], n[pk[i], pk[j]], n[pk[j], pk[i]]
}'
