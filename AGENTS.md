# Agent Guide → see CLAUDE.md

The full, authoritative guide for coding agents lives in
**[CLAUDE.md](CLAUDE.md)** at the repository root. **Read it before making
changes** — it covers the hard constraints, the verification gate (`./init.sh`),
and links to every topic doc.

This file exists so agents that follow the `AGENTS.md` convention find the
guide. It is intentionally a thin pointer and a *real file* (not a symlink), so
it resolves the same on every checkout mode — Windows without symlink support,
sparse checkouts, the GitHub web UI, and archive/zip exports. `CLAUDE.md` is the
single source of truth; keep guidance there, not here.
