# Argentum issue #19 — final card-coverage handoff

Handoff requested **2026-10-03 03:51:05 UTC** (October 2, America/Los_Angeles). This is a preservation and transfer record, not a new implementation or qualification run.

## 1. Stop boundary and evidence conventions

Implementation is stopped. Existing implementation branches were not reset, rebased, merged, overwritten, deleted, or force-pushed. No test, build, game, workflow dispatch, CI rerun, PR creation/merge, deployment, or schedule change was performed for this handoff. Only repository reads, local attachment preparation, and the explicitly authorized dedicated handoff branch/document write are in scope.

Evidence labels throughout:
- **V — directly observed:** repository ref, file, comparison, or workflow status returned by a read during this handoff.
- **R — recorded evidence:** a fact reported in a retrieved PR/document, not independently reproduced here.
- **H — hypothesis / historical analysis:** earlier chat analysis or a proposed implementation boundary. This is not proof of current engine behavior.
- **Unknown:** required records or worktrees were not accessible.

The earlier scheduled reports repeatedly reclassified the same cards and sometimes contradicted themselves about capabilities at the same immutable commit. Do not count repeated classification as progress or use those reports as source-level proof. No new coverage credit is claimed by this handoff.

## 2. Accepted checkpoint, pins, and coverage evidence

| Item | Exact value | Evidence |
|---|---|---|
| Argentum fork main | `106994eff8671f0dfa6aa66d2a53d8580d5c7f95` | V: branch read; Spire of Industry merge |
| Public Commander Gym main | `63b330734054996735d925edd46610626e3a5a5d` | V: ref read |
| Public coverage manifest | `argentum-coverage-source.json` at the public main above, pins Argentum `106994eff8671f0dfa6aa66d2a53d8580d5c7f95` | V: file read |
| Spire card PR | Argentum #213; source head `955d3e8fd21a7d3197ecc455bcada06100cc29c5`; merged as `106994eff8671f0dfa6aa66d2a53d8580d5c7f95` | V: PR metadata |
| Coverage repin PR | Commander Gym #125; source head `12b8d9cb7de79c80c1be3cb427fac11968743fa9`; merged as `63b330734054996735d925edd46610626e3a5a5d` | V: PR metadata |
| CardRegistry workflow | `37080987053`, completed / success | V: exact-head workflow lookup |
| Companion tests | `37080987011`, completed / success | V: exact-head workflow lookup |
| Registry artifact | ID `11258816556`, named `argentum-active-roster-coverage` | R: merged #125 body and workflow definition |
| Registry total | **13,542 unique names** | R: #125 body |
| Public active roster | **400/400 exact and normalized slots, 284/284 unique names, zero missing**; Krenko, Talrand, Sythis, Lathril | R: #125 body |
| Last accepted historical benchmark | **474/865 implemented unique names, 391 missing**; last credited Spire delta = one name / three slots | R: #125 body and previous accepted checkpoint |

Primary repository evidence:
- [Argentum #213](https://github.com/Blue42hand/argentum-engine/pull/213)
- [Commander Gym #125](https://github.com/Blue42hand/commander-gym/pull/125)
- [Coverage run 37080987053](https://github.com/Blue42hand/commander-gym/actions/runs/37080987053)
- [Tests run 37080987011](https://github.com/Blue42hand/commander-gym/actions/runs/37080987011)
- [Recorded artifact link](https://github.com/Blue42hand/commander-gym/actions/runs/37080987053/artifacts/11258816556)
- [Exact public coverage pin](https://github.com/Blue42hand/commander-gym/blob/63b330734054996735d925edd46610626e3a5a5d/argentum-coverage-source.json)
- [Exact coverage workflow](https://github.com/Blue42hand/commander-gym/blob/63b330734054996735d925edd46610626e3a5a5d/.github/workflows/argentum-coverage.yml)

**Artifact limitation:** the artifact bytes were not downloaded or re-counted during this handoff. The artifact identifier, totals, and reported `+ Spire of Industry / - none` delta come from the merged PR record. The workflow success statuses were independently read. The attempted artifact-metadata read failed as documented in section 9; no alternate artifact route was tried.

The artifact is expected to contain `argentum-coverage.json` and the sorted `argentum-registry-card-names.txt`. Registry-name presence is not full-game qualification, rules-fidelity proof for every card, or a deployed-server revision.

### Private instance divergence — do not inherit the old “unchanged corpus” claim

**V:** `Blue42hand/commander-gym-private:main` is now **`0167f6dbef05d4d80bf5e1d6f9f59fb2728be7c4`**, 30 commits ahead / zero behind the repeatedly cited historical `eb83aa60eac7c6feab84eb2bc3c1d7696e817110`.

The current README identifies `rosters/foundation-active-four-seat.json` as authoritative. That file identifies roster revision `2026-10-02.1`, derived from public `rosters/argentum-native-v1`, and selects Krenko/Talrand/Sythis/Lathril through exact Bindings and the `foundation-openai@2026-09-24.1` Pilot. Current private decks live under `decks/argentum-native-v1/`; other normalized lineages and seven original Archidekt imports are historical archives, not active seats.

**V:** the old private `.github/workflows/argentum-coverage.yml` was removed in the intervening changes. Several legacy `test-game/*-Gym-Decklist.txt` files moved into canonical lineage revisions. The private repository is now instance data, not a second runtime or reusable coverage implementation.

**V:** nevertheless its current `argentum-source.json` still pins **`0f56e60347137546512f9c810a74d23c06403145`**, not the public coverage pin. It also records:
- original coverage probe commit `a8fbbf99b75db515c5a7f48692ce0e29d9c6080f`;
- upstream baseline `3f46367d87c88bcf156a843a9e69fd29e1693872`.

Do not silently overwrite these different-purpose pins. Resolve which source is authoritative for each subsequent qualification.

The historical benchmark was described as 16 logical decks / 1,600 slots / 865 normalized unique names at `eb83aa60...`, excluding the duplicate legacy Alibou alias and the excluded Rocco list. **That full benchmark was not recomputed against the current private layout in this handoff.** Retain 474/865 as the accepted historical checkpoint, not as a newly certified current-private-corpus result.

References, access-controlled where applicable:
- [Current private README](https://github.com/Blue42hand/commander-gym-private/blob/0167f6dbef05d4d80bf5e1d6f9f59fb2728be7c4/README.md)
- [Current private roster manifest](https://github.com/Blue42hand/commander-gym-private/blob/0167f6dbef05d4d80bf5e1d6f9f59fb2728be7c4/rosters/foundation-active-four-seat.json)
- [Current private source pin](https://github.com/Blue42hand/commander-gym-private/blob/0167f6dbef05d4d80bf5e1d6f9f59fb2728be7c4/argentum-source.json)

No private deck payloads, primers, runtime observations, credentials, or personal account data are reproduced here.

## 3. Preserved implementation: Vibranium

### Exact branches, commits, and files — V

Repository: **Blue42hand/argentum-engine**. Both existing branches were left untouched.

| Existing branch | Exact tip | Relation to accepted engine main |
|---|---|---|
| `codex/issue-19-vibranium-token` | `4459e20e2019d0a219f39a663b9182ec1cfc69da` | 1 ahead / 0 behind |
| `codex/issue-19-vibranium-token-tests` | `0a8624a40e8f628f44b045bb9f3d31f28d94138d` | 2 ahead / 0 behind; preferred continuation |

The second branch includes the first implementation plus its structural tests. They are not independent implementations to duplicate.

Full diff against `106994eff8671f0dfa6aa66d2a53d8580d5c7f95`:
1. `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/Effects.kt` — **+16 / -0**.
2. `mtg-sets/src/main/kotlin/com/wingedsheep/mtg/sets/tokens/PredefinedTokens.kt` — **+23 / -0**.
3. `rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/VibraniumTokenDefinitionTest.kt` — **+30 / -0**; only on the tests branch.

[Exact comparison](https://github.com/Blue42hand/argentum-engine/compare/106994eff8671f0dfa6aa66d2a53d8580d5c7f95...0a8624a40e8f628f44b045bb9f3d31f28d94138d)

The code defines and adds `PredefinedTokens.Vibranium` to `allTokens`, with artifact/Vibranium type, indestructible, a tap mana ability producing one colorless mana, and `ManaRestriction.CannotCastSpellsOtherThan(setOf(CardType.ARTIFACT))`. It adds `Effects.CreateVibranium(count, tapped, controller, imageUri)` through `CreatePredefinedTokenEffect`. It does not add a new mana executor.

The two existing tests inspect the token definition and the facade's returned data. They check artifact type, indestructible, the configured restriction, token type, count, and `tapped=true`. **They do not actually exercise card-registry lookup, battlefield entry, destruction, payment legality, or ward/ability payment.** A test's name mentioning “registered” is not a registry-execution assertion.

**V:** the all-event Actions lookup for exact head `0a8624a...` returned `total_count: 0`. No Vibranium PR appeared in the connected-user open-PR inventory. That inventory does not establish the absence of every possible external worker.

### What remains / dependencies

This branch is **not merge-ready**. Historical claims of a “tested implementation” must be read as “tests written,” not “tests passed.”

Smallest next action for the next authorized coding task: resume the existing tests branch and run the existing focused test once, recording the exact SHA and output. Then add behavior-level tests, rather than treating definition wiring as rules proof:
- creation enters tapped when requested and has the intended token characteristics;
- indestructibility affects actual destruction;
- its restricted mana pays an artifact spell but not a nonartifact spell;
- the same negative restriction does not reject otherwise legal activated-ability or ward/non-spell payments;
- real registration and activated mana behavior work.

Update `docs/card-sdk-language-reference.md` with the new reusable facade; that required file is absent from this branch's diff. Complete the applicable token rules/metadata/art and verification gates. The code includes a `CR 111.10w` comment, but that rule number was **not independently reverified during this handoff**.

**H / historical scope:** Shuri's Fabricator, Vibranium Mining Mech, and The Great Mound were each ranked at three benchmark slots, making nine potential slots behind this shared content. Their card definitions are not part of the preserved branch. Canonical MSC placement/scaffold status and individual Oracle/rulings evidence need confirmation before authoring. Prior summaries contradicted one another about whether MSC was scaffolded.

Adding the predefined token alone is not credit for any of those three cards. After future merge, qualification must distinguish a possible token registry-name addition from actual benchmark-card additions.

## 4. Everflowing Chalice — design handoff, not preserved implementation

**V:** branch searches for `chalice` and `kicker` found no matching fork branches. No implementation commit or test file was identified for this slice. This does not prove that no differently named branch or inaccessible worktree contains work.

**H:** the last analysis cited upstream repeatable-cost PR #2537 / commit `4cbd0efb5f3e9c89527677e2561132c8debebf19`. It proposed that payment repetition is represented by `CastSpell.declaredCostTimes`, but lost before an entering permanent can read it:

`CastSpellHandler.putSpellOnStack` → `StackResolver.castSpell` → `SpellCaster.castSpell` → `SpellOnStackComponent` → `PermanentEntry.withCastChoices` → `CastChoicesComponent`.

The proposed minimal change was durable numeric optional-cost provenance, consumed by existing `ChoiceValue.NumberChoice` / `DynamicAmount.CastChoice`, while retaining ordinary `WasKicked` semantics. Existing entry-counter and counter-scaled mana facilities were proposed for the card after this seam.

**Do not treat that diagnosis as currently verified.** Earlier reports alternatively claimed full `additionalCostChoices` support already existed. No fresh engine-mechanic research was performed for this handoff. The last cited upstream audit ref was `f28de120f65a4f32560ecfd1b69bbb22f786d801`, not an accepted integration pin.

Smallest next action: reconcile this hypothesis with exact current upstream code and any existing PR, then check overlap with active engine #214 before touching casting/payment files. Reuse upstream work rather than recreating a second multikicker system.

Proposed acceptance cases, not tests that already exist or passed:
- zero, one, and multiple payments; affordable options and server validation;
- ordinary one-time Kicker unchanged;
- repetition count visible during as-enters counter placement;
- spell copies preserve the original announced kick count without additional payment; copied permanent spells retain it on entry;
- zone changes clear obsolete cast provenance;
- current charge counters, rather than initial kick count, drive later mana production.

Card-specific Oracle/printing/ruling verification is still needed through the standard add-card workflow. No Chalice tests were run, skipped by CI, or passed in this handoff; no local historic test receipt is available.

Historical upstream reference, **not freshly audited**: [PR #2537](https://github.com/wingedsheep/argentum-engine/pull/2537).

## 5. Other unfinished and historical queue slices

All classifications in this section are **H**, except the explicit branch/ref observations. They preserve potentially useful analysis without certifying conflicting prior claims. None received new implementation or research here.

| Slice | Preserved state / historical direction | Smallest next action |
|---|---|---|
| Hangarback Walker | V: `codex/issue-19-hangarback-walker` is exactly accepted engine main, with no unique code commit. H: ordinary double-X, entry counters, departed-source counter count, flying artifact tokens; historically three slots. | Coordinate ownership of the existing placeholder, confirm exact primitives and ORI evidence before authoring. |
| Resourceful Defense | No implementation branch identified here. H: observer leave-trigger LKI counters and arbitrary-source counter movement; earlier reports conflict on fork availability. | Confirm those exact source/target semantics and NCC data before deciding card-only versus integration. |
| Crystalline Crawler | No implementation commit identified. H: Converge plus counter-removal mana; reports conflict on fork capability and once mislabeled reach as two slots. | Verify payment provenance and full corpus reach rather than inheriting a summary. |
| Organic Extinction | H: Improvise and nonartifact-creature destruction, with minimal NEC scaffold reportedly needed. | Confirm existing Improvise behavior and canonical set placement; do not invent another payment mechanic from an absence claim. |
| Surge Conductor / Patrolling Peacemaker | H: shared EOC content batch, proliferate plus artifact ETB / opponent crime triggers; reports conflict on crime/scaffold presence. | Confirm exact EOC placement and event behavior, including simultaneous entry and opponent perspective. |
| Legion Warboss | H: Mentor reportedly merged upstream as #2660 / `f75d7c141467277d5f477e28f8bdc9e3bf6cd3cb`. | Reconcile the existing upstream feature first; preserve resolution-time relative-power and source-LKI behavior. Do not implement Mentor twice. |
| Saw in Half | H: dynamic/LKI copy P/T exceptions and exact “dies this way” causality. Earlier reports disagreed on whether the causal/copy-source machinery already suffices. | Resolve that narrow uncertainty with source and regression evidence; do not create a bespoke card executor or presume only one blocker. |
| Animate Dead | H: Aura host in graveyard, per-instance enchant restriction change, new-object reattachment, and linked sacrifice relationship. | Check all four lifecycle requirements; ordinary reanimation or “Aura entering attached” is not by itself proof. |
| Historical two-slot candidates | Cabaretti Courtyard, Astral Cornucopia, Buried Alive, Coretapper, Empowered Autogenerator were proposed card-only candidates. Moonsilver Key was proposed to need a semantic mana-ability predicate including triggered abilities. Solar Array + Lux Artillery were proposed to share grant-aware Sunburst. | Reuse retained findings only after checking the now-migrated benchmark selection and exact source. |
| Existing upstream cards | K'rrik, Hoarding Broodlord, Vat of Rebirth were reported already upstream; Bubbling Muck was linked to upstream High Tide machinery. | Check existing upstream work before local authoring. The old proposed Vat oil-counter gap is not reverified here. |

The task's next implementation should not restart a rolling “classify the same 3-slot tier” loop. One bounded, tested change on a preserved branch is preferable once implementation is authorized again.

## 6. Additional preserved refs and changed files

All refs below were read during this handoff and left unchanged. “Ahead/behind” uses the observed merge-base comparison; squash-merged feature branches can still look diverged. It is not proof that their features are missing from main.

### Coverage-side refs

| Repository / branch | Exact commit | State |
|---|---|---|
| `Blue42hand/commander-gym` / `codex/coverage-spire-industry-106994` | `40a988419caaefa165f019441c0a6a08c34fd46d` | One-file `argentum-coverage-source.json` repin; 1 ahead / 1 behind public main. Superseded by merged #125; do not open duplicate repin. |
| `Blue42hand/commander-gym` / #125 source `codex/coverage-spire-of-industry-106994` | `12b8d9cb7de79c80c1be3cb427fac11968743fa9` | Verified as merged PR head; only `argentum-coverage-source.json`. Note the different branch spelling (“of”). |
| `Blue42hand/commander-gym-private` / `codex/coverage-dedupe-legacy-aliases` | `eb83aa60eac7c6feab84eb2bc3c1d7696e817110` | Empty proposal branch at old base, no dedupe implementation commit. Current private main is 30 commits newer and retired the workflow. Do not revive the old runtime. |

### Legacy engine code to retain, not silently duplicate

- `automation/issue-19-cloud-key-card` — `287bb6084a2680afbcd34fd089e1b1e97405de1d`; 6 ahead / 23 behind main. Files:
  - `mtg-sets/2003-2007/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/fut/cards/CloudKey.kt`
  - `mtg-sets/src/test/resources/snapshots/cards/FUT.json`
- `codex/issue-19-cloud-key-preflight` — `620b9f1d14002a994253b1c8582e288b355d2e8c`; 1 ahead / 216 behind. Only `.github/workflows/issue19-cloud-key-preflight.yml`.
- `codex/issue-19-pitiless-plunderer` — `36a828e6a2a60b414e705f04ad1bb32a161d84cf`; 7 ahead / 28 behind. Files:
  - `mtg-sets/2017-2022/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/rix/cards/PitilessPlunderer.kt`
  - `mtg-sets/src/test/resources/snapshots/cards/RIX.json`
- `codex/issue-19-enters-card-type` — `95ddc155c03fe43a4d78d2a5cae6759019e338e0`; ancestor of main, zero new commits / 213 behind; comparison has no branch-side files.
- `codex/issue-19-tear-asunder-refresh` — `70190d782430bf102b7796fdd53a4f1c747e518f`; old-main placeholder, not the final Tear implementation.
- `codex/issue-19-tear-asunder` — `9953470e6be48c53b19b601a42d4b6ae2c466ebf`; comparison 4 ahead / 2 behind; retained files:
  - `backlog/sets/bloomburrow-commander/cards.md`
  - `backlog/sets/bloomburrow-commander/decks/squirreled-away.md`
  - `manual-scenarios/cards/t/tear-asunder.json`
  - `mtg-sets/2017-2022/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/dmu/cards/TearAsunder.kt`
  - `mtg-sets/2017-2022/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/TearAsunderScenarioTest.kt`
  - `mtg-sets/2024/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/blc/cards/TearAsunderReprint.kt`
  - `mtg-sets/src/test/resources/snapshots/cards/DMU.json`

Tear Asunder is already represented by main's preceding merge `0a0f04a141d2b9b0e10951f82b48409f5403a64e`; Spire #213 is already merged. Do not treat their surviving source refs as new coverage gaps.

Other legacy issue-19 refs observed in branch inventory, **not file-audited or certified active**:
- `automation/issue-19-generous-gift-refresh` → `0f56e60347137546512f9c810a74d23c06403145`
- `automation/issue-19-pitiless-evidence` → `b709c499243b47d4449d65ae312a81ae86847841`
- `automation/issue-19-registry-export` → `98573c5a002101ca0c4602ad9dbacb6f9eed4076`
- `automation/issue-19-scryfall-evidence` → `65aedb5c33f1bcba04fecba6fde94548a04a45e9`
- `codex/issue-19-riveteers-clean` → `0f56e60347137546512f9c810a74d23c06403145`
- `codex/issue-19-riveteers-overlook` → `a9122ac916dee83c55b2b35274f63f3301a2e0dd`
- `codex/issue-19-steel-overseer` → `1f5fa3661b1ed531bf42a52c69faa3c1d0b226c2`
- `codex/issue-19-spire-of-industry` → `955d3e8fd21a7d3197ecc455bcada06100cc29c5`

Branch search also found `issue-19-generous-gift` and `issue-19-idol-of-oblivion`; their tips and files were not fetched. This inventory is scoped, not a claim to enumerate every repository worktree or worker.

### Uncommitted work

**Unknown on other workers.** No `.git` worktree was found in this runtime's inspected `/home/oai`, `/tmp`, or `/mnt/data` trees (maximum depth 6); `/workspace` and `/workspaces` were absent. There was no accessible source checkout on which to run `git status`.

No recoverable uncommitted bytes were available to export. This does not mean the user's Mac, Linode, earlier task environments, or parallel workers are clean. No attempt was made to reset, stash, discard, or manufacture their changes.

The accompanying `vibranium-COMMITTED-106994e-to-0a8624a.patch` is a **backup of already committed work**, reconstructed from the successful comparison read, not a patch of uncommitted changes. It has three files / 69 additions / zero deletions, with hunk counts checked as attachment integrity only. It was not applied or compiled. The separately preserved test text matches Git blob `c8e0508e091b1260e261a073409b2f927e7ed78f`.

Before changing an existing worker checkout, obtain its owner-approved branch/HEAD, staged/unstaged diffs and untracked-file inventory. Export real dirty work without overwriting anything and screen for credentials before sharing. The attachment `uncommitted-work-status.txt` records this limitation.

## 7. Exact test commands and result ledger

**No commands in this section were executed for this handoff.** Recorded successes and absent executions are intentionally separate.

### V: existing workflow statuses read

| Subject / exact head | Status | Link |
|---|---|---|
| Public coverage / `12b8d9cb7de79c80c1be3cb427fac11968743fa9` | **PASSED**: run completed / success | [37080987053](https://github.com/Blue42hand/commander-gym/actions/runs/37080987053) |
| Public tests / same head | **PASSED**: run completed / success | [37080987011](https://github.com/Blue42hand/commander-gym/actions/runs/37080987011) |
| Vibranium / `0a8624a40e8f628f44b045bb9f3d31f28d94138d` | **NO CI RUN FOUND**: all-event exact-head lookup returned zero runs. Not “passed” and not “skipped.” | Exact SHA is the lookup key. |

Exact coverage invocation from the pinned workflow:
```bash
python3 scripts/run_argentum_roster_coverage.py \
  --argentum-source "$GITHUB_WORKSPACE/argentum-engine" \
  --output "$GITHUB_WORKSPACE/argentum-coverage.json" \
  --registry-names-output "$GITHUB_WORKSPACE/argentum-registry-card-names.txt"
```

### R: completed Spire commands recorded in merged engine #213

These receipts concern source SHA `955d3e8fd21a7d3197ecc455bcada06100cc29c5`, **not Vibranium**. Exact outputs were not locally reproduced.

| Command | Recorded result |
|---|---|
| `just check-card-printing 'Spire of Industry'` | **PASSED** |
| `just assay-differential --set AER` | **PASSED**: 10 compared, zero divergent |
| `GRADLE_LOCK_SLOTS=1 just test-class SpireOfIndustryScenarioTest` | **PASSED**: 2/2 |
| `GRADLE_LOCK_SLOTS=1 just rebless-cards` | **PASSED**: only Spire added to AER golden |
| `GRADLE_LOCK_SLOTS=1 just build` | **PASSED** |
| `just check-backlog` | **PASSED** |
| `git diff --check` | **PASSED** |

The PR also records a successful browser playthrough and Scryfall/image verification; no exact browser command is preserved in that body. These records do not establish present Scryfall access in this session.

### Future commands — NEVER RUN BY THIS HANDOFF

```bash
# On the existing Vibranium tests branch, after ownership is confirmed:
GRADLE_LOCK_SLOTS=1 just test-class VibraniumTokenDefinitionTest

# Broader gates after the next task completes its changes:
GRADLE_LOCK_SLOTS=1 just test-rules
GRADLE_LOCK_SLOTS=1 just build
git diff --check

# Card workflow examples, only after fresh canonical evidence:
just assay parse "Everflowing Chalice"
just assay explain "Everflowing Chalice"
just check-card-printing "Everflowing Chalice"
just where WWK
```

Vibranium local historical execution is **unknown**, not certified never-run on every machine. Missing payment-behavior and Chalice tests are **not yet preserved as implementation**, so no passing or failing result exists here for them.

**FAILED:** no new engine-test failure was observed because no engine test ran. Earlier chat's JVM `PortInUseException` account is historical and unverified without its original log; it is not a current merge blocker asserted here.

**SKIPPED:** no specific skipped Vibranium test/job was observed. The user prohibited execution; that is not the same as a CI skip result.

Local attachment checks only verified patch hunk lengths and the preserved test's blob hash; they are not build, scenario, or rules-behavior tests.

## 8. Guidance, primary sources, and concurrent ownership

### Repository guidance

Read at exact accepted fork `106994eff8671f0dfa6aa66d2a53d8580d5c7f95`:
- [AGENTS.md](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/AGENTS.md)
- [add-card workflow](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/.agents/skills/add-card/SKILL.md)
- [add-feature workflow](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/.agents/skills/add-feature/SKILL.md)
- [verify workflow](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/.agents/skills/verify/SKILL.md)

Additional linked guidance to consult when coding resumes, not newly audited here:
- [Upstream CONTRIBUTING.md](https://github.com/wingedsheep/argentum-engine/blob/main/CONTRIBUTING.md)
- [SDK design principles](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/docs/sdk-design-principles.md)
- [SDK language reference](https://github.com/Blue42hand/argentum-engine/blob/106994eff8671f0dfa6aa66d2a53d8580d5c7f95/docs/card-sdk-language-reference.md)

Load-bearing requirements: compose existing DSL first; keep engine generic and server authoritative; update the SDK reference with SDK additions; use `just` rather than raw Gradle for heavy builds; preserve other agents' work; one scenario file per card when tests are required; existing-primitives batches may share a PR but keep per-card evidence/commits. Assay is the applicable Oracle-to-SDK gate; mtgish-only limitations are reported but do not masquerade as engine failures. An Assay decline is not a pass.

Canonical card data must come from set-specific Scryfall records, their `rulings_uri`, printing history and exact image URI, followed by final re-verification. Never guess printing IDs or waive behavioral edge cases because the facade name looks suitable.

### Primary rules/card-data entry points — references, NOT new fetches

- [Official Comprehensive Rules](https://magic.wizards.com/en/rules)
- [Everflowing Chalice, set-specific Scryfall API](https://api.scryfall.com/cards/named?exact=Everflowing%20Chalice&set=wwk)
- [Hangarback Walker, set-specific Scryfall API](https://api.scryfall.com/cards/named?exact=Hangarback%20Walker&set=ori)
- [Shuri's Fabricator, set-specific Scryfall API](https://api.scryfall.com/cards/named?exact=Shuri%27s%20Fabricator&set=msc)
- [Vibranium Mining Mech, set-specific Scryfall API](https://api.scryfall.com/cards/named?exact=Vibranium%20Mining%20Mech&set=msc)
- [The Great Mound, set-specific Scryfall API](https://api.scryfall.com/cards/named?exact=The%20Great%20Mound&set=msc)

These URLs are future canonical lookup entry points, not evidence that a request succeeded here. Follow returned rulings/printing/token links rather than inventing them. Historical official-release-note claims were not refreshed because the user prohibited new research.

### Existing parallel PRs — V metadata, do not duplicate or alter

Open PRs were read through the connected user's PR inventory. Their continued existence is verified; actual worker-process liveness and dirty worktrees are unknown.

| Repository / PR | Branch | Observed exact head | Dependency |
|---|---|---|---|
| Argentum [#212](https://github.com/Blue42hand/argentum-engine/pull/212) | `codex/72-binding-debug-game` | `8af2fa4653cf357c18cd172bfd49c73724b9fb11` | Draft game-server profile work |
| Argentum [#214](https://github.com/Blue42hand/argentum-engine/pull/214) | `codex/abrade-payment-window` | `ae69016b809eb35a0f3b3c2cb0a1b7b2f6087865` | Draft stacked on #212; overlaps casting/payment |
| Commander Gym [#124](https://github.com/Blue42hand/commander-gym/pull/124) | `codex/72-game-server-baseline` | `581634c642591638760fc07e87ab2b53f4e70d31` | Draft consuming engine #212 |
| Commander Gym [#126](https://github.com/Blue42hand/commander-gym/pull/126) | `codex/72-early-wake-routing` | `5fbe1ddb9e5dd51264ec1ab12576c660b00bdf47` | Draft stacked on #124 |

Exact changed files returned for engine #212:
```text
game-server/src/main/kotlin/com/wingedsheep/gameserver/controller/AiTournamentController.kt
game-server/src/main/kotlin/com/wingedsheep/gameserver/handler/LobbyHandler.kt
game-server/src/test/kotlin/com/wingedsheep/gameserver/controller/AiTournamentControllerProfileTest.kt
```

Exact changed files returned for engine #214:
```text
e2e-scenarios/tests/general/abrade-payment-ui.spec.ts
rules-engine/src/main/kotlin/com/wingedsheep/engine/core/ManaContinuations.kt
rules-engine/src/main/kotlin/com/wingedsheep/engine/core/Serialization.kt
rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/spell/CastSpellHandler.kt
rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/continuations/CastModalContinuationResumer.kt
rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/ManaPaymentWindow.kt
rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/ManaSolver.kt
rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/AbradePaymentAgreementTest.kt
```

Exact changed files returned for public Gym #124:
```text
.github/workflows/python-tests.yml
commander_gym/game_server_binding_openai_sidecar.py
commander_gym/game_server_bindings.py
commander_gym/game_server_seat.py
commander_gym/game_server_sidecar.py
commander_gym/openai_responses_pilot.py
commander_gym/openai_run_budget.py
commander_gym/two_luna_debug.py
jvm-adapter/build.gradle.kts
jvm-adapter/src/main/kotlin/org/commandergym/argentum/CommanderGymControllerProvider.kt
jvm-adapter/src/test/kotlin/org/commandergym/argentum/CommanderGymPlayerControllerParameterTest.kt
jvm-adapter/src/test/kotlin/org/commandergym/argentum/LocalGuiGameServer.kt
scripts/run_two_luna_binding_game.py
tests/test_game_server_seat.py
tests/test_game_server_sidecar.py
tests/test_openai_responses_pilot.py
tests/test_openai_run_budget.py
tests/test_two_luna_debug.py
```

Exact changed files returned for public Gym #126:
```text
commander_gym/delegated_autopass.py
commander_gym/game_server_binding_openai_sidecar.py
commander_gym/openai_responses_pilot.py
commander_gym/pilot_routing.py
commander_gym/two_luna_debug.py
docs/delegated-autopass.md
scripts/analyze_early_wakes.py
tests/test_delegated_autopass.py
tests/test_game_server_binding_openai_sidecar.py
tests/test_openai_responses_pilot.py
tests/test_pilot_routing.py
tests/test_two_luna_debug.py
```

Other observed open public Gym PRs, outside this implementation handoff:
- #118: `codex/114-transfer-benchmark-v4` at `0a586f41f7e453e4da67c60e6a527455c1705256`.
- #68: stale coverage branch `coverage/argentum-695a86f` at `f01b52fe6e59fa754dcfed7f3637be14ce13c31e`; do not merge an obsolete coverage pin.
- #63: `codex/live-human-three-luna-proof` at `7eb5652322728167183a2907d44b428fdd607f1e`.
- #62: `codex/game-server-action-params` at `fb609096deb16130b3c7a33b6ef40aa069836e43`.

Their file sets were not separately audited; they remain untouched. No other worker was contacted, stopped, or assumed idle.

## 9. Exact access/error record

### Historical claims — UNVERIFIED

Earlier reports claimed rejection of file writes, draft-PR creation, issue comments, PR readiness changes, merges, reruns, and canonical Scryfall fetches. The underlying tool calls/responses were not available in the inherited record.

For those claims:
- exact tool name: **unavailable**;
- verbatim returned error: **unavailable**;
- request ID: **unavailable**;
- whether repository state changed: **unknown without an independently observed state comparison**;
- alleged “execution safety layer” / “before reaching GitHub” cause: **unverified**.

Do not substitute commit hashes, workflow IDs, artifact IDs, connector IDs, or narrative wording for request IDs. This handoff does not repeat old write attempts to test those claims.

### Actual read errors observed during this handoff

**A. Tool `GitHub.fetch`**

Request URL:
```text
https://github.com/Blue42hand/argentum-engine/compare/106994eff8671f0dfa6aa66d2a53d8580d5c7f95...0a8624a40e8f628f44b045bb9f3d31f28d94138d.diff
```

Verbatim returned error:
```json
{"message": "Not Found", "documentation_url": "https://docs.github.com/rest/commits/commits#compare-two-commits", "status": "404", "is_error": true}
```

Request ID: **not returned**. Repository state: no mutation was requested by this read; the response does not report a mutation. Cause beyond the returned 404: **unknown**, not established as a permission denial. The backup patch uses content from the previously successful JSON comparison, not a retry of this URL through another route.

**B. Tool `GitHub.fetch`**

Request URL:
```text
https://api.github.com/repos/Blue42hand/commander-gym/actions/artifacts/11258816556
```

Verbatim returned error:
```text
ToolError: INVALID_ARGUMENT: Error code: INVALID_ARGUMENT; Error: HTTPError: 400: GitHub Fetch URL is not an allowed public GitHub repository or search endpoint. (Response: None)
```

Request ID: **not returned**. Repository state: no mutation requested by the GET. The tool says that this URL form is unsupported; that is not proof of GitHub permission failure. No alternative artifact download route was attempted.

### Authorized handoff-only write

Tool `GitHub.create_branch` succeeded for:
```text
repository_full_name: Blue42hand/argentum-engine
branch_name: handoff/issue-19-20261003T035105Z
sha: 106994eff8671f0dfa6aa66d2a53d8580d5c7f95
```

Returned result: `{"branch":"handoff/issue-19-20261003T035105Z"}`; error fields null; request ID not returned. State change: the new dedicated branch was created. No existing implementation branch was moved.

The handoff document is intended as the only new file on that branch, `HANDOFF-issue-19-2026-10-03.md`. Its final write outcome and exact resulting commit are supplied in the delivery receipt. If the document write fails, preserve that exact error and return this complete document and attachments without another write route.

The inspected engine CI and Web Client Tests workflows trigger on main pushes or PR events; service tests are PR-only, deployment is manual, and nightly E2E uses schedule/manual triggers. No PR or dispatch accompanies this handoff branch. Existing schedules remain unchanged.

## 10. Bounded continuation for the next coding task

1. Establish ownership and protect any dirty worktrees the current session could not access. Use the exact preserved refs, not remembered branch names.
2. Resolve the public coverage/private source-pin and historical-benchmark selection distinction. Do not restore the retired private runtime to reuse its old coverage script.
3. Resume **`codex/issue-19-vibranium-token-tests@0a8624a40e8f628f44b045bb9f3d31f28d94138d`** as the smallest existing implementation. Its first required receipt is the focused structural test; behavioral tests and SDK documentation remain.
4. Keep Everflowing Chalice as an explicitly unimplemented design proposal until current upstream and the concurrent casting/payment work are reconciled.
5. Credit future benchmark changes only after the relevant card is merged and an exact-pin live CardRegistry artifact proves presence. Preserve distinct counts for unique names, logical deck slots, and tokens.

No continuation step was executed here. Implementation is stopped; schedules were not changed.
