# Upstream synchronization assessment — 2026-10-15

## Decision
A staged upstream sync is warranted. **This branch is preparation only, not an integration merge.** Do not merge into main until compatibility tests pass.

## Verified refs
- Fork: `Blue42hand/argentum-engine` main at `b56f50517eb75e9ffca337dd8e40d3cfd4a31626`.
- Upstream: `wingedsheep/argentum-engine` main at `c18afc598e03bc3f2b610ec4868cbe4180db27b0` at assessment time.
- Merge base: `ceb413375d69664196af1096070b535a22a9afa3`.
- GitHub cross-fork comparison: fork is 747 commits behind upstream, 409 commits ahead. The earlier coverage pin `106994eff8671f0dfa6aa66d2a53d8580d5c7f95` is **not** the latest sync base and should not be used for planning.

## Relevant upstream changes
- [#3230](https://github.com/wingedsheep/argentum-engine/pull/3230) restart notice and game resumption (service reliability).
- [#3236](https://github.com/wingedsheep/argentum-engine/pull/3236) server startup indexing improvement.
- [#3233](https://github.com/wingedsheep/argentum-engine/pull/3233) shared Kotlin multiplatform card search; browser compiled JS artifact/build impact.
- [#3221](https://github.com/wingedsheep/argentum-engine/pull/3221) Phyrexian life/mana payment correctness.
- [#3223](https://github.com/wingedsheep/argentum-engine/pull/3223) flicker trigger correctness.
- [#3226](https://github.com/wingedsheep/argentum-engine/pull/3226) optional target selection correctness.
- Large AI/combat/rules/SDK/test changes across the 747 commits, plus set-specific card coverage. A subset may already exist on the fork as cherry-picks or parallel implementations; counts are not a count of distinct features.

## Fork integration surfaces to protect
- AI callback controller and evidence contracts: `ai/.../AiPlayerController.kt`, `RecordedAiCallbackController.kt`, `AiDecisionEvidence.kt`.
- Commander Gym orchestration, callback admission/failures, service endpoints, pending-decision normalization, player privacy, legal-action IDs, sidecar transport, auto-pass and conditional waits.
- Fork workflow adaptations: `.github/workflows/gym-service-tools.yml`, `native-rollout-tools.yml`, `web-client-tests.yml`.
- Current open fork work: PRs #297 (Alela runtime), #293 (AI lobby binding), #292 (failed callback hold), #265/#264 (snapshot validation). Avoid incorporating unrelated WIP opportunistically.

## Integration sequence (not yet executed)
1. Freeze fork main and upstream SHA above. Check current branch and uncommitted changes on any working copy; do not reset, force-push, or overwrite. Coordinate with any card-coverage workers.
2. Create a separate candidate integration branch starting from fork main; fetch upstream and make a non-committed merge attempt to identify real conflicts. Record overlapping files and semantic changes; abort if unsafe.
3. Resolve conflicts by keeping native upstream semantics plus a minimal Gym adapter. Do not silently discard new cards or fork custom AI callbacks. Compare SDK/card IDs and registry data.
4. Build JVM modules, server and web client, then test: unit/rules/scenario tests; callback admission, legal decisions, sidecar reconnect/restart, 4-seat Commander games; full deck coverage against a freshly built registry.
5. Re-test selected Gym acceptance games and record API calls, completion and decision-error rate. Update deployment/runbooks only after passing.
6. Open an integration PR with pinned SHAs, exact conflict resolution, tests and rollback steps. Keep draft until reviewed.

## Status
No upstream merge, integration build, CI run or game acceptance test performed as part of this assessment. A review branch/document alone is **not** evidence of compatibility. No changes to fork main or production deployment.
