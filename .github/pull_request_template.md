## Summary

<!-- Briefly explain the change and why it’s needed. Keep it focused. -->

## Type of change

- [ ] feat: New feature
- [ ] fix: Bug fix
- [ ] docs: Documentation update
- [ ] refactor: Code improvement (no behavior change)
- [ ] perf: Performance improvement
- [ ] style: Formatting (no behavior change)
- [ ] test: Add/update tests
- [ ] chore/ci: Build or CI changes

## Screenshots/Recordings (optional)

<!-- Add images or videos for UI changes. -->

## How to test

<!-- Steps for reviewers to verify the change locally. -->
1. 
2. 
3. 

## Breaking changes

- [ ] No breaking changes
- [ ] Yes (describe): 

## Related issues

<!-- e.g., Closes #123, Fixes #456 -->

## Checklist

- [ ] Focused PR (single purpose)
- [ ] Tests added/updated (if applicable)
- [ ] Docs updated (if applicable)
- [ ] `./init.sh` passes locally (scoped: `./init.sh parser` / `./init.sh app`; paste the target and result under "How to test")
- [ ] UI/runtime change: checked on a device or emulator, light and dark (screenshots above)
- [ ] Fixtures, logs and screenshots contain no real personal or financial data
- [ ] Follows existing code style and patterns

## AI assistance

<!-- See "AI-Assisted Contributions" in CONTRIBUTING.md. -->

- [ ] No AI tools used
- [ ] AI-assisted (tool: ___ ); I've reviewed every line and can explain it

## Refactor PRs

<!-- Only for `refactor:` PRs. Delete this section otherwise. See docs/adr/0003-incremental-architecture-modernization.md. -->

- [ ] Behavior unchanged; scope stated above, no features or dependency upgrades mixed in
- [ ] Money stays currency-tagged and nothing sums across currencies
- [ ] Backup fields keep their defaults; any migration was run on the API 26 AVD
- [ ] Worker, widget, tile and service classes moved only with a plan for existing installs; no DataStore key, Room column or backup field renamed
- [ ] No new package cycle or back-edge (`scripts/arch/package-deps.sh`)
- [ ] `./init.sh` passes and both flavors build (`:app:assembleStandardDebug`, `:app:compileFdroidDebugKotlin`)
- [ ] Runtime/UI changes checked on a device or emulator

<details>
<summary>Prompt summary (AI-assisted PRs)</summary>

<!-- What you asked the agent for and the decisions you made along the way. Delete if not applicable. -->
</details>


