# Argentum issue #19 — updated handoff and stop checkpoint

Update requested **2026-10-03 10:03:31 UTC**. This revision captures the work reported after the original handoff and the repository state observed while preparing this update. It is not a new implementation, test run, or coverage qualification.

The complete original 449-line preservation record remains available at its immutable commit, including the older branch inventory, changed-file lists, original test ledger, and access-error receipts:

[Original handoff at 1e525b8e11db073f35032da8a055365e5a84a3b6](https://github.com/Blue42hand/argentum-engine/blob/1e525b8e11db073f35032da8a055365e5a84a3b6/HANDOFF-issue-19-2026-10-03.md)

This updated entrypoint supersedes that document's current-state statements where explicitly noted below. The original attachment package is retained unchanged. No implementation branch is reset, rebased, merged, deleted, or overwritten by this update.

## 1. Stop boundary and evidence labels

The user requested: update the handoff, **then pause the Argentum Card Coverage task**. The only requested automation change is disabling that task; retain its existing prompt and hourly schedule, and leave other tasks untouched. Resume implementation only through a separately authorized coding task or explicit reactivation. The task-disable result belongs in the delivery receipt, not an assumed success in this document.

Target automation: **Argentum Card Coverage**, ID `6aaef998c0d48191be27ec1828f6fd56`. Its enabled state was observed before the requested pause. This update does not authorize stopping or modifying independent coding workers.

- **V — directly observed now:** a repository ref, content, comparison, or Actions lookup returned during this update.
- **R — recorded evidence:** a previously retrieved PR receipt or later scheduled report; not independently reproduced in this update.
- **H — hypothesis:** a proposed rules/implementation boundary, not verified current engine behavior.
- **Unknown:** no original response, execution receipt, or accessible worktree establishes the fact.

Repeated reclassification of the same card is not a newly closed gap. Earlier reports contradict one another about capabilities at the same immutable SHA. Do not use their wording as source-level proof, and do not infer permission-denial causes from narrative summaries.

## 2. Accepted coverage versus current engine main

| Item | State at this update | Evidence |
|---|---|---|
| Public Commander Gym main | `63b330734054996735d925edd46610626e3a5a5d` | V: ref read |
| Public `argentum-coverage-source.json` | Still pins engine `106994eff8671f0dfa6aa66d2a53d8580d5c7f95` | V: file read |
| Accepted engine coverage pin | `106994eff8671f0dfa6aa66d2a53d8580d5c7f95`, Spire of Industry merge | V pin; R qualification |
| Accepted live registry | **13,542 names** | R: accepted #125 qualification |
| Accepted public active roster | **400/400 exact and normalized slots; 284/284 unique names; zero missing**; Krenko, Talrand, Sythis, Lathril | R: accepted qualification |
| Accepted selected benchmark | **474/865 implemented names; 391 missing** | R: accepted checkpoint; no new credit here |
| Current Argentum main | **`da9ca7ff9189cf14e1b1a7cac4a1e40732f8ee12`** | V: ref and comparison |
| Current private repository main | `0167f6dbef05d4d80bf5e1d6f9f59fb2728be7c4` | V: ref read |

Accepted evidence links:
- [Commander Gym #125](https://github.com/Blue42hand/commander-gym/pull/125)
- [Coverage run 37080987053](https://github.com/Blue42hand/commander-gym/actions/runs/37080987053)
- [Registry artifact 11258816556](https://github.com/Blue42hand/commander-gym/actions/runs/37080987053/artifacts/11258816556)
- [Companion Tests run 37080987011](https://github.com/Blue42hand/commander-gym/actions/runs/37080987011)
- [Exact accepted coverage manifest](https://github.com/Blue42hand/commander-gym/blob/63b330734054996735d925edd46610626e3a5a5d/argentum-coverage-source.json)

The 04:12 UTC scheduled report also cited postmerge coverage run `37081493597` and artifact `11258907230`. Those are **R**, not newly inspected or substituted for the accepted evidence above. No artifact bytes were re-downloaded or registry intersection recomputed for this update.

### New external merge: Crystalline Crawler

**V:** engine main advanced while this handoff update was being prepared. Commit `da9ca7ff9189cf14e1b1a7cac4a1e40732f8ee12`, dated **2026-10-03 10:04:29 UTC**, is `Add Crystalline Crawler to Commander 2016 (#215)`, one commit after the accepted Spire pin. This merge was not performed by this handoff task.

[Engine #215](https://github.com/Blue42hand/argentum-engine/pull/215)

Exact changed files relative to the accepted pin:
```text
manual-scenarios/cards/c/crystalline-crawler.json
mtg-sets/2008-2016/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/c16/cards/CrystallineCrawler.kt
mtg-sets/2008-2016/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/pz2/cards/CrystallineCrawlerReprint.kt
mtg-sets/2008-2016/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/CrystallineCrawlerScenarioTest.kt
mtg-sets/src/test/resources/snapshots/cards/C16.json
```

**Do not implement Crystalline Crawler again or initiate another Converge backport based on the old reports.** Its code is now merged. The public coverage manifest still points to the preceding accepted commit. No exact-merge live CardRegistry receipt for Crawler was inspected here, so this handoff retains **474/865**, not 475/865. The next coding task should first coordinate with #215's owner and any coverage-repin worker; consume existing qualification if present instead of duplicating it. The earlier coverage remains valid for its own pin but does not qualify the newer engine main.

### Private-corpus migration

The original handoff identified the private repository migration from `eb83aa60eac7c6feab84eb2bc3c1d7696e817110` to `0167f6dbef05d4d80bf5e1d6f9f59fb2728be7c4`. The later **05:22 UTC scheduled report** states that all 16 selected benchmark deck files retain identical Git blob SHAs across the migration, with **1,600 slots / 865 normalized unique names**, and reproduces 474/865 against the accepted registry.

That is **R — a subsequent reported verification**, not a fresh reconstruction by this update; its 16-file mapping is not attached here. The private main ref itself is freshly verified. Preserve the distinction between the four active Binding-selected decks and the historical 16-deck benchmark. Do not restore the retired private runtime or obsolete coverage workflow to continue this work. The original private `argentum-source.json` pin differed from the public coverage pin; that manifest was not re-read or changed in this update.

## 3. Vibranium — two new commits preserved

Repository: `Blue42hand/argentum-engine`.

Preferred continuation branch: **`codex/issue-19-vibranium-token-tests`**.

**V exact tip:** `e8b33f811d040eef23e6797a573d560fbdcd956a`.

The branch is now **4 commits ahead / 1 behind current engine main** `da9ca7ff9189cf14e1b1a7cac4a1e40732f8ee12`. Its merge base is the accepted Spire commit `106994eff8671f0dfa6aa66d2a53d8580d5c7f95`. The prior reports' “4 ahead / 0 behind main” became stale when Crawler merged. Do not auto-rebase it as part of this handoff.

[Preserved branch](https://github.com/Blue42hand/argentum-engine/tree/codex/issue-19-vibranium-token-tests)

| Commit | Preserved work | Evidence |
|---|---|---|
| `4459e20e2019d0a219f39a663b9182ec1cfc69da` | Original token definition and facade; original `codex/issue-19-vibranium-token` branch was preserved by the first handoff | Original V inventory; ref not re-read now |
| `0a8624a40e8f628f44b045bb9f3d31f28d94138d` | Two structural definition/facade tests; previous handoff head | V: comparison base |
| `c1da39dedabd083c5a43edbe8b8d2be92545c91e` | New negative-mana-restriction regression, reported in the 04:12 UTC run | R commit attribution; V file present at current tip |
| `e8b33f811d040eef23e6797a573d560fbdcd956a` | `Document Vibranium predefined token`, dated 08:07:10 UTC | V: exact commit read |

Exact current branch diff from its merge base:

| File | Additions / deletions |
|---|---:|
| `docs/card-sdk-language-reference.md` | +6 / -1 |
| `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/Effects.kt` | +16 / -0 |
| `mtg-sets/src/main/kotlin/com/wingedsheep/mtg/sets/tokens/PredefinedTokens.kt` | +23 / -0 |
| `rules-engine/src/test/kotlin/com/wingedsheep/engine/handlers/mana/ManaSpendRestrictionCannotCastNonArtifactSpellsTest.kt` | +57 / -0 |
| `rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/VibraniumTokenDefinitionTest.kt` | +30 / -0 |

The original token/facade composition is unchanged: an indestructible artifact/Vibranium token with a tap ability adding one restricted colorless mana, plus `Effects.CreateVibranium(count, tapped, controller, imageUri)` using the predefined-token path. No new mana executor was added.

### What the new test actually establishes

**V source inspection:** `ManaSpendRestrictionCannotCastNonArtifactSpellsTest` has five tests that call `ManaPool.canPay` with `ManaRestriction.CannotCastSpellsOtherThan(ARTIFACT)`:
1. artifact-spell context accepted;
2. nonartifact-creature-spell context rejected;
3. activated-ability context accepted;
4. empty/non-cast context accepted, described as a tax or ward payment;
5. turn-face-up special-action context accepted.

These are **tests written, not tests observed passing**. They exercise pool affordability under supplied contexts, not a complete cast/payment/ward-resolution transaction. They do not prove that real ward or ability flows supply those contexts, consume the mana correctly, or preserve token characteristics. The two original tests remain structural checks, not live registration/entry/destruction scenarios.

**V:** the SDK reference now documents the token/facade and negative spending restriction. The previously missing documentation is no longer an outstanding coding task.

**V:** the all-event Actions lookup for `e8b33f8...` returned `total_count: 0`. The open-PR lookup filtered to this exact branch returned `[]`. No CI pass or existing open PR is established; local execution history is unknown. Do not call this branch qualified or merge-ready.

### Remaining work and smallest next action

When a separate coding task is authorized, resume this branch rather than creating another token implementation. Establish branch ownership, reconcile the one-commit base movement without discarding work, and run the two existing focused classes. Record exact source SHA, command, output, and exit status. Then assess remaining engine-level scenarios for real registration, tapped entry, indestructibility, actual restricted-mana spending, and non-spell payments. Complete normal source/rules/token-data, build, review, and CI gates.

Shuri's Fabricator, Vibranium Mining Mech, and The Great Mound remain a **reported three-card / nine-benchmark-slot dependency cluster**. Their card definitions are not in this branch. Each still needs its own canonical Scryfall/printing/rulings verification and applicable per-card test/snapshot gates. The token alone earns none of their benchmark credit.

## 4. Resourceful Defense preflight branch

**V:** `Blue42hand/argentum-engine:codex/issue-19-resourceful-defense-preflight` exists at **`106994eff8671f0dfa6aa66d2a53d8580d5c7f95`**. It is a pointer to the accepted base, not an implementation commit. There are no unique branch changes at that SHA; it now trails the Crawler merge.

[Preserved preflight branch](https://github.com/Blue42hand/argentum-engine/tree/codex/issue-19-resourceful-defense-preflight)

The 09:19 UTC report proposed `.github/workflows/issue19-resourceful-defense-preflight.yml` but claimed the file creation failed. The observed branch tip establishes that the proposed file/code is not committed on this branch. No recoverable uncommitted workflow patch was supplied.

**H/R:** Resourceful Defense was again classified as existing-primitives card work using observer-leave LKI counters and chosen counter movement. This was already discussed in the original handoff and is not a newly closed engine mechanic. The next authorized task should verify the exact source/target semantics and canonical NCC data before authoring; no card or test was executed here. Do not create a preflight workflow merely to work around a denied tool operation.

## 5. Everflowing Chalice and other analysis-only changes

**Everflowing Chalice:** no new implementation commit, branch, or test receipt was identified in the post-handoff reports. Its status remains **H — design proposal**, not a completed capability. The proposed missing seam is preserving a repeatable optional-cost payment count from cast action through stack/copy state into permanent entry and numeric cast choices. Earlier reports conflict about `declaredCostTimes` versus `additionalCostChoices`, so the next coding task must inspect exact source before editing.

Smallest next action: reconcile current upstream repeatable-cost work with the selected fork revision and the concurrent casting/payment owner. Do not implement a second Multikicker system. Proposed regression cases remain 0/1/N payments, ordinary Kicker compatibility, as-enters visibility, copied-spell inheritance, zone-change reset, and later mana scaling with current counters. None is a preserved Chalice test result.

Historical dependency: [upstream #2537](https://github.com/wingedsheep/argentum-engine/pull/2537), cited implementation `4cbd0efb5f3e9c89527677e2561132c8debebf19`. Not re-audited here.

Other post-handoff reports, retained as **R/H rather than new proof**:
- Buried Alive reportedly landed upstream in [#2680](https://github.com/wingedsheep/argentum-engine/pull/2680), commit `42d822192e45bf17a88b7340db68e3803d74f09d`. Check and consume that work rather than duplicating it.
- Mentor was again traced to upstream [#2660](https://github.com/wingedsheep/argentum-engine/pull/2660), commit `f75d7c141467277d5f477e28f8bdc9e3bf6cd3cb`, as the reusable prerequisite for Legion Warboss. This was already known; no new local Mentor implementation is claimed.
- Organic Extinction was again classified as Improvise plus filtered destruction, with possible NEC scaffolding. No new card branch/test was established by those reports.
- Old Crystalline Crawler/Converge missing-capability statements are superseded by the directly observed #215 merge. No new Crawler implementation should be started.
- Saw in Half and Animate Dead remain analysis-only paths described in the original handoff. Their exact causal/LKI-copy and cross-zone Aura boundaries must be verified, not inferred from repeated classifications.

The 05:22 UTC report listed thirteen missing three-deck names at the accepted Spire pin: Animate Dead, Crystalline Crawler, Everflowing Chalice, Hangarback Walker, Legion Warboss, Organic Extinction, Patrolling Peacemaker, Resourceful Defense, Saw in Half, Shuri's Fabricator, Surge Conductor, The Great Mound, Vibranium Mining Mech. This is **R**. Crawler is now merged but not credited by this update; do not confuse merged source with accepted exact-pin coverage.

## 6. Test and qualification ledger

**No tests, builds, games, workflow dispatches, CI reruns, or merges were performed in this handoff update.**

| Scope | Exact command / lookup | Result |
|---|---|---|
| Latest Vibranium head `e8b33f8...` | All-event Actions lookup by full `head_sha` | V: zero workflow runs. Not PASSED, FAILED, or SKIPPED. |
| Existing structural test | `GRADLE_LOCK_SLOTS=1 just test-class VibraniumTokenDefinitionTest` | Not run in this update; historical local execution unknown. |
| New pool restriction test | `GRADLE_LOCK_SLOTS=1 just test-class ManaSpendRestrictionCannotCastNonArtifactSpellsTest` | Not run in this update; historical local execution unknown. |
| Broad engine/scenario gate | `GRADLE_LOCK_SLOTS=1 just test-rules` | Not run here for Vibranium. |
| Build gate | `GRADLE_LOCK_SLOTS=1 just build` | Not run here for Vibranium. |
| Diff check | `git diff --check` | Not run on a source checkout in this update. |
| Accepted coverage run `37080987053` | Command below | R: PASSED in accepted checkpoint, not rerun. |
| Companion Tests run `37080987011` | Existing workflow | R: PASSED in accepted checkpoint, not rerun. |
| Crawler #215 | Test file and source merge observed | Execution results not inspected here; do not invent a passed count. |
| Everflowing Chalice | No preserved test command/implementation | No result available. |

Exact accepted coverage command, copied from the original handoff's workflow evidence:
```bash
python3 scripts/run_argentum_roster_coverage.py \
  --argentum-source "$GITHUB_WORKSPACE/argentum-engine" \
  --output "$GITHUB_WORKSPACE/argentum-coverage.json" \
  --registry-names-output "$GITHUB_WORKSPACE/argentum-registry-card-names.txt"
```

**FAILED:** no new engine-test failure was observed. The 04:12 report's local GitHub DNS/checkout failure has no original execution response here and remains unverified, not an engine-test failure.

**SKIPPED:** no specific skipped Vibranium job was observed. A missing run or a user-imposed stop is not a CI skip result.

The original handoff retains exact historical Spire commands/results and older receipts; those results must not be attributed to Vibranium or Crawler.

## 7. Other branches/workers and uncommitted-work boundary

The original handoff's complete legacy branch and changed-file inventory remains preserved at its immutable link above. Except for the directly observed Vibranium/Resourceful/Crawler changes, it is a historical inventory, not a fresh claim that all branch tips or workers are unchanged.

Protect these previously observed concurrent stacks and re-read their current owners/heads before editing related files:

| Repository / PR | Previously observed branch / exact head | Overlap |
|---|---|---|
| Argentum #212 | `codex/72-binding-debug-game` / `8af2fa4653cf357c18cd172bfd49c73724b9fb11` | Native profile/game-server integration |
| Argentum #214 | `codex/abrade-payment-window` / `ae69016b809eb35a0f3b3c2cb0a1b7b2f6087865` | Casting/payment; coordinate before Chalice work |
| Commander Gym #124 | `codex/72-game-server-baseline` / `581634c642591638760fc07e87ab2b53f4e70d31` | Binding game runner |
| Commander Gym #126 | `codex/72-early-wake-routing` / `5fbe1ddb9e5dd51264ec1ab12576c660b00bdf47` | Pilot routing |

Their current open/merged state was not re-audited in this update. No worker was contacted, stopped, or presumed idle. The externally observed Crawler merge is additional evidence of concurrent work.

Retain the original Hangarback placeholder, legacy coverage/card branches, and private `codex/coverage-dedupe-legacy-aliases` inventory. The old private dedupe branch was only at historical `eb83aa60...`; do not restore retired workflows from it without a new, justified plan.

No source checkout or other worker's dirty state was inspected in this update. Uncommitted work on the user's Mac, Linode, or other coding environments remains **unknown**. No uncommitted changes were discarded or claimed exported. The original ZIP backs up committed Vibranium work only through `0a8624a...`; it does not include the two later commits. Their exact branch/source links above are the current preservation locations. Never present an earlier backup patch as the latest branch contents.

## 8. Permissions and error evidence

The later scheduled reports quote the following error for assorted writes:
```text
Script error: This tool call was blocked by OpenAI's safety checks. Please double check what you are sending.
```
The 08:16 report quotes the same text without the `Script error: ` prefix. Preserve that distinction rather than manufacturing a uniform original response.

| Report timestamp UTC | Reported tool(s) | Original response / request ID available in this update? | Repository-change conclusion |
|---|---|---|---|
| 04:12 | `mcp__GitHub__create_pull_request` | No; reported error only. Report says no request ID returned. | Current no-open-PR result does not prove the earlier call or rejection cause. |
| 05:22 | `mcp__GitHub__update_file`, `mcp__GitHub__create_pull_request`, `mcp__GitHub__add_comment_to_issue` | No; reported error only. Report says no request IDs returned. | Subsequent documentation commit exists; its existence does not authenticate any prior rejection. |
| 06:18 | `mcp__GitHub__update_file` | No; reported error only. | Unknown for that particular attempted call. |
| 07:16 | `mcp__GitHub__update_file`, `mcp__GitHub__add_comment_to_issue` | No; reported error only. | Unknown for those particular attempted calls. |
| 08:16 | Draft-PR operation and issue-comment operation; exact names not supplied in that report | No; quoted text lacks the prefix above. | Current exact-branch open-PR lookup is empty; no causal inference. |
| 09:19 | `mcp__GitHub__create_file`, `mcp__GitHub__create_pull_request`, `mcp__GitHub__add_comment_to_issue` | No; reported error only. Report says no request IDs returned. | Resourceful branch is still its base SHA, so no unique committed file is preserved there. |

These are **UNVERIFIED historical failure claims**, even where a current branch state independently confirms that no resulting commit or open PR is present. Do not infer that GitHub received nothing, that repository permissions were missing, or that a platform safety layer caused the failure without the original response. Connector IDs, commits, workflow IDs, and artifact IDs are not request IDs.

This update does not retry any of those denied implementation/PR/comment operations. Only the newly requested handoff document update and task-disable action are authorized writes. Their actual result/commit and disable status are reported in the delivery. If the document write is denied, retain the exact response and return the prepared attachment without an alternate write route; still honor the separately requested task pause through its normal action.

## 9. Guidance and primary-source entry points

The original handoff preserves the retrieved repository guidance. Before later implementation, use the matching add-card/add-feature/verify workflow and current branch instructions. Composition first; generic engine, data-driven cards, server-authoritative rules; do not revert or discard others' changes. Heavy builds use `just`, not raw Gradle. Keep per-card evidence/test files separate even when batching existing-primitives content.

- [Accepted-pin AGENTS.md](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/AGENTS.md)
- [add-card](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/.agents/skills/add-card/SKILL.md)
- [add-feature](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/.agents/skills/add-feature/SKILL.md)
- [verify](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/.agents/skills/verify/SKILL.md)
- [Updated Vibranium SDK reference](https://github.com/Blue42hand/argentum-engine/blob/e8b33f811d040eef23e6797a573d560fbdcd956a/docs/card-sdk-language-reference.md)
- [Upstream contribution guidance](https://github.com/wingedsheep/argentum-engine/blob/main/CONTRIBUTING.md)
- [Official Comprehensive Rules](https://magic.wizards.com/en/rules)
- [Everflowing Chalice: WWK Scryfall API](https://api.scryfall.com/cards/named?exact=Everflowing%20Chalice&set=wwk)
- [Resourceful Defense: NCC Scryfall API](https://api.scryfall.com/cards/named?exact=Resourceful%20Defense&set=ncc)
- [Shuri's Fabricator: MSC Scryfall API](https://api.scryfall.com/cards/named?exact=Shuri%27s%20Fabricator&set=msc)
- [Vibranium Mining Mech: MSC Scryfall API](https://api.scryfall.com/cards/named?exact=Vibranium%20Mining%20Mech&set=msc)
- [The Great Mound: MSC Scryfall API](https://api.scryfall.com/cards/named?exact=The%20Great%20Mound&set=msc)

These are reference/lookup entry points, not new successful fetches. No new rules/card-data research was performed here. A future card task must obtain canonical Oracle/rulings/printing/image evidence and verify any rule number before using it. Assay declines are not passes; registry presence is not rules fidelity or full-game qualification.

## 10. Bounded continuation after explicit authorization

First coordinate Crawler's existing merge/coverage work; do not duplicate a repin or increase benchmark totals without exact live evidence. Then resume the preserved Vibranium branch for focused execution and missing behavioral qualification, not another round of repeated card classification. Keep Chalice's cast-provenance proposal separate from concurrent payment work. Preserve all unavailable-worktree limitations.

This handoff update makes no implementation or coverage-credit change. Save this document, then disable only the named card-coverage automation. The final delivery confirms the actual save and pause outcomes.
