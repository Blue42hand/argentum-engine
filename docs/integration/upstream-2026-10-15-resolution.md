# Upstream merge resolution work — 2026-10-15

This is work on `integration/upstream-2026-10-15`, merging upstream
`c18afc598e03bc3f2b610ec4868cbe4180db27b0` into integration HEAD
`a8748f354b359f2be25f3c24f87a5af5043ed42c`. Main, remotes and other worktrees were not changed.

## Environment blockers

- `.git` is mounted read-only. `git add` fails with `Unable to create .../.git/index.lock: Read-only file system`.
  Consequently the index still lists the original 215 unmerged paths; worktree edits cannot be staged or committed here.
- Normal `just gradle` / `just rebless-cards` fail before task execution. The default Gradle home is
  read-only. Copying the existing cache to `/tmp/argentum-integration-gradle` gets past that, but
  Gradle initialization fails because sandbox socket creation is denied (`NetworkInterface.getAll`,
  `Could not determine a usable wildcard IP for this machine`). IPv4 preference does not fix it.
- The five fork PRs named in the integration inventory (#292, #293, #297, #264, #265) could not be
  retrieved: `gh pr view` cannot connect to GitHub, web retrieval fails, and no matching PR refs
  exist locally. Their live state and diffs have **not** been audited.

## Resolution choices

- Combined fork payment announcement snapshots, manual-mana windows, source restrictions, repeatable
  optional-cost bounds and action identity with upstream optional-cost grants, life-payment purposes,
  mana-unit provenance and spending-color rules. Kept the pool conversion helpers and extended them
  to preserve upstream mana units.
- Combined fork entry-evaluation views and pre-event replacement sources with upstream aggregated
  entry counters, departed-object copy reads, LKI policies, static-ability projection and trigger batching.
  Preserved keyword prohibitions and added the chosen-creature scope to granted attack projections.
- Preserved ordered callback delivery, callback evidence, external controller selection, epoch checks,
  retry/pause paths and viewer masking. Kept upstream personas, sideboards, restarted-game mulligans,
  activity tracking and restart handling. Adapted Gym's existing commander request to the engine's list API.
- Kept client controller-catalog and epoch/reset behavior together with restart/table-talk handling.
  Preserved library-only commander submission, added partner designation for pasted decks, retained
  companion sideboards, and reconciled the new per-attacker target-map test fixtures.
- Reviewed conflicting canonical card definitions individually. Examples: Reanimate freezes graveyard
  mana value; Bident keeps its live turn-scoped attack requirement; Professional Face-Breaker retains the
  controller restriction; Chittering Witch explicitly counts opponents; unrestricted tutors retain
  mandatory selection. Animate Dead uses the upstream enchant-return primitive while its fork tests
  remain in the same card-specific test file alongside upstream coverage.
- Consolidated 16 duplicate reprint declarations and six further repeated set/collector rows. Preserved
  alternate art/collector printings and richer metadata, including both BLC Gilded Goose printings,
  extended-art Goose and Heroic Intervention treatments, CMR Generous Gift treatments and MH3 Laelia treatments.
- Reconciled the two MAT set objects into one discoverable object with both names available, retaining
  extension-set metadata and basic-land discovery.
- Added the four fork SDK leaves to the upstream surface baseline with their rationale, regenerated
  `docs/sdk-index.md`, combined the language-reference additions, and synchronized the BLC checklist count.

## Verification method

Normal Gradle gates remain blocked as described above. Additional checks use the cached Kotlin 2.4.0
compiler and serialization plugin directly, JDK 21 target, and Kotest/JUnit Platform 6.0.3. Temporary
`just` recipes in `/tmp/argentum-offline-compile.just` serialize compilation with
`flock /tmp/commander-wall-time-build.lock`. These are standalone source/test checks, **not** a claim
that Gradle tasks, Spring boot packaging, CI or socket-based acceptance tests passed.

Scripts and full logs are under `/tmp/argentum-*` and `/tmp/upstream-*`. Snapshot generation uses the
same `CardExporter`, sorting and golden formatting as `CardDefinitionSnapshotTest`, and checks the
same `CardLoader.fromJsonPreservingIds` round-trip. It also checks duplicate canonical names and
set/collector printing keys.

## Confirmed results so far

- Client: `npm ci --offline` from a copied existing cache succeeded; production `npm run build` passed.
- Client: 97 Vitest files, 1,209 tests passed.
- SDK: standalone production and test compilation passed; all 644 Kotest tests passed, including the
  surface baseline and regenerated SDK index.
- Rules engine: standalone production compilation passed. Five focused suites passed all 17 tests
  (entry-evaluation views, projected-set initialization/ownership/type names, and mana-color-spent reads).
- SDK tooling, AI, search, Assay and game-server: standalone production compilation passed.
- All nine card-era modules and the card-catalog aggregator compiled standalone.
- Gym: standalone production compilation passed after adapting the commander argument.
- `just check-backlog`: all 17 card-count headers in sync.

Further verification and final conflict-marker counts are recorded below.

## Completed catalog checks

- Snapshot generation completed for **193 sets, 15,840 canonical cards and 8,164 explicit printings**.
- All **15,840** card JSON round-trips passed; no duplicate canonical card names or set/collector
  printing keys remained. Golden files came from compiled source, not either side of the JSON conflicts.
- Roster comparison against both sides' conflicted snapshots: **2,187 fork names checked, 0 missing**;
  **2,924 upstream names checked, 0 missing**. This comparison covers the conflicted snapshot roster,
  not unimplemented cards or an external Scryfall completeness claim.
- All working-tree conflict markers removed. `git diff --check` passes.
- Gym-server and the shared engine test fixtures compile standalone.
- A final engine compilation passed after retaining the nullable external-source distinction in
  counter-placement replacements (ordinary placements must not acquire entrant-only self replacements).

## Behavioral integration fixes found by the broader tests

The first complete core-engine run found 6,639 passing and 16 failing tests. Fixes preserve both
continuation representations: fork announcements retain their complete payment snapshots, while
upstream persisted/internal continuations that carry only locked additional costs retain their
original resume path. Forced-mana contribution safety remains enforced on that path.

Other fixes clear departed-host bookkeeping when an attachment explicitly becomes unattached,
reject graveyard hosts after an Aura gains a battlefield-only enchant restriction, keep carried
pipeline targets symbolic inside delayed effects (including nested gates/coin-flip branches), credit
a token recipient with creation while preserving explicit lower-level creator tracking, and count
identifiable replacement-directed mill results for radiation. Two tests were adapted to upstream's
existing behavior: target selection precedes optional resolution, and driver-created creatures have
summoning sickness. Their behavioral assertions remain; the target-identity test additionally
asserts that resolution finished with no pending decision or stack item.

A standalone hygiene check required the expected `rules-engine/build/classes/kotlin/main` path;
an ignored local symlink points it at the temporary compiler output. The production sources passed
full compilation, followed by targeted recompilation of the final two attachment/delayed-trigger
fixes. The temporary compiler preserves the full module metadata when recompiling a single file.

- All 97 tests in the previously failing engine suites passed after the first set of fixes.
- All 162 Gym tests passed.
- All 53 selected non-network server callback, evidence, identity and decision-freshness tests passed.
- Five Gym-server registry/deck-coverage tests passed.
- A broader server selection ran 69 tests: 53 passed; 16 WebSocket lobby cases could not create
  sockets in this sandbox, including the four-player lobby acceptance cases. These are blocked,
  not claimed as passing. The four-seat synthetic evidence test is among the 53 passing checks.
- Game-server's complete test source set and Gym-server's complete test source set compile standalone.
- Gym-trainer production compilation passed.

## Final verification results

| Check | Result | Evidence |
| --- | --- | --- |
| Core rules-engine suite | **6,655 passed, 0 failed, 0 skipped** | `/tmp/upstream-rules-all-tests3.log` |
| SDK suite | **644 passed, 0 failed, 0 skipped** | `/tmp/upstream-sdk-tests3.log` |
| Affected card scenarios, 32 files | **121 passed, 0 failed, 0 skipped** | 24 + 8 + 3 + 10 + 76 across the five era logs below |
| Corpus snapshot, lint, facade and discovery gates | **399 passed, 0 failed, 0 skipped** | `/tmp/upstream-corpus-tests.log` |
| Gym suite | **162 passed, 0 failed, 0 skipped** | `/tmp/upstream-gym-tests.log` |
| Selected non-network server callbacks/evidence/seat lifecycle | **53 passed, 0 failed, 0 skipped** | `/tmp/upstream-server-focused-tests3.log` |
| Gym-server registry/deck-coverage checks | **5 passed, 0 failed, 0 skipped** | `/tmp/upstream-gym-server-tests.log` |
| Client suite | **97 files, 1,209 passed** | `/tmp/upstream-web-test.log` |
| Client production build | **Passed** (bundle-size warnings only) | `/tmp/upstream-web-build2.log` |
| Snapshot generation and JSON round-trip | **193 sets, 15,840 cards, 8,164 printings; no duplicates; 15,840 round-trips passed** | `/tmp/upstream-snapshots3.log` |
| Backlog headers | **All 17 in sync** | `/tmp/upstream-final-backlog.log` |
| Working-tree conflict markers | **0** | Whole-worktree marker scan |
| Whitespace/conflict check | **`git diff --check` passed** | `/tmp/upstream-diff-check.log` and final rerun |

The test counts above total **9,248 passing tests** without adding the focused engine reruns again.
Kotlin results are standalone compiler/Kotest checks using cached JARs, not Gradle-resolved builds.
JVM production sources in all non-deprecated modules, including the nine card eras and Gym-trainer, compiled (search was checked on the JVM target);
final private implementation changes were recompiled against that output. Normal Gradle build,
packaging and CI verification remain outstanding. The whole card-scenario corpus and full server
integration suite were not run; the selected card files and server checks above were.

Card scenario logs:

- `/tmp/upstream-card-1993-1999-tests.log`: 24 passed.
- `/tmp/upstream-card-2000-2002-tests.log`: 8 passed.
- `/tmp/upstream-card-2003-2007-tests.log`: 3 passed.
- `/tmp/upstream-card-2008-2016-tests.log`: 10 passed.
- `/tmp/upstream-card-2017-2022-tests2.log`: 76 passed.

The final card checks also retained Bident's live attack requirement, including later arrivals;
the earlier fixed-group implementation from upstream did not satisfy that rule. Its THS golden
was regenerated from the corrected source. Woe Strider's test now checks that keeping its single
scry card finishes without an unnecessary reorder prompt and leaves that exact card on top.
Card-lint warnings were printed under the existing non-blocking warning policy; all lint error and
stale-allowlist assertions passed.

## Handoff state and outstanding blockers

- **No commit, push, merge into main, or other-worktree mutation occurred.**
- The integration branch's HEAD remains `a8748f354b359f2be25f3c24f87a5af5043ed42c` and MERGE_HEAD
  remains `c18afc598e03bc3f2b610ec4868cbe4180db27b0`.
- Git still reports **215 unmerged paths in the index** because `.git` is read-only. Their working-tree
  contents have no conflict markers. The additional integration fixes, duplicate-row deletions,
  regenerated index/baseline and this report also need staging when Git becomes writable.
- Normal `just`/Gradle gates and the 16 WebSocket lobby acceptance cases remain blocked by sandbox
  socket restrictions. The five live fork PR audits remain blocked by GitHub access. These must
  be completed before certifying the integration or committing it as fully verified.
- The temporary hygiene output symlink was removed after verification so a future normal Gradle
  invocation can create its own output directory. Compiler outputs, recipes, launcher and logs
  remain under `/tmp` for review/reuse.

## Tolaria host continuation — October 15, 2026, 16:06 UTC

The earlier read-only Git and socket restrictions applied to the isolated coding sandbox, not the Tolaria host. All 215 conflicts were staged successfully: zero unmerged index paths, zero unstaged changes, and `git diff --cached --check` passed. No source conflict markers remain.

The normal JVM gate `GRADLE_REAP=0 just gradle :mtg-sdk:test :rules-engine:test :gym:test :gym-server:test :game-server:test --max-workers=2 --no-daemon` passed (`BUILD SUCCESSFUL in 9m 28s`), with 8,387 passing test-case results and no failures. Evidence: `/tmp/upstream-integration-gradle-normal-20261015.log`. Web `npm run build` passed, followed by 97 Vitest files and 1,209 passing tests. Evidence: `/tmp/upstream-integration-web-normal-20261015.log`.

Fork PR inventory audited via GitHub: #292 merged; #293 and #297 remain open but their heads are ancestors of fork main. #264 and #265 are validation-only upstream-submission packets: their Gym snapshot ownership and step-count behavior is already present in the integration and the tests passed. Engine PR #307 is open but its head is already an ancestor of fork main through #309; Commander Gym PR #268 merged into its main. The latest fork main is `10ec3736841b`, 24 commits after the pinned fork baseline `b56f50517e`. Reconcile those commits and repeat the affected tests before declaring the integration branch ready.

### Outstanding action blocked by remote command safety check

A normal local `git commit` for this resolved upstream merge was attempted through Remote Desktop Commander and rejected by the execution tool with `This tool call was blocked by OpenAI's safety checks. Please double check what you are sending.` No commit was created, no push was attempted, and fork main and production were untouched. There was no tool request ID supplied. The tool permitted `git add` and the staged index is complete and verified. An authorized local shell must commit the staged upstream merge; after that, latest fork main (`10ec3736841b` as of 16:06 UTC) must be reconciled and the affected tests rerun before this branch may be pushed or called merge-ready.
