# Manual mana and colorless affordability

A recorded four-player callback offered `{2}{C}` as affordable because a ready Springleaf Drum could produce a manual any-color unit. Actual production offered only the five colors. Choosing blue left the colorless pip unpaid. The callback's native state, masked view and digest were reproduced exactly before changing the solver.

`ManaSolver.allocateAnyColorManaToPool` already assigns fixed colored deficits first. It now represents remaining any-color units with a real color for generic-cost accounting. Genuine colorless production retains its separate allocation. This local estimate does not change a player's stored mana pool or the actual production/choice handlers.

The specification is the [Comprehensive Rules effective November 6, 2026](https://media.wizards.com/2026/downloads/MagicCompRules%2020261106.txt), checked against the downloaded official text: 105.4 distinguishes colors from colorless; 107.4b permits any mana type for generic costs; 107.4c requires colorless mana for `{C}`. The regressions cover all five colors, generic payment, rejection of fabricated colorless from manual any-color and Treasure production, and successful payment with genuine colorless production.

The path is `ManaSolver.canPay` → activated-ability enumeration → `LegalActionEnricher.isAffordable` → existing client/pilot action filtering. The client already reads that flag in `web-client/src/utils/actionOptions.ts`. This is an internal correction with no new UI, decision, SDK type, card definition, serialization shape, trigger, state projection or cleanup mechanism. The current mana handlers continue to produce events and perform payment. No new Assay vocabulary or card AST is introduced.

The regression first failed three of four tests on the original engine; genuine colorless production passed. After correction, the full rules-engine and every per-era card scenario suite passed: 31,480 tests, zero failures, errors or skips. Verification ran through `just gradle :rules-engine:test :mtg-sets:scenarioTest --max-workers=2 --no-daemon`, the tasks used by `just test-rules`. The repository's normal Kotlin compiler heap was required to compile the full test source set.

This qualifies the core correction only. A matched harness/adapter, exported facts, original-state replay and a complete naturally finished recorded game remain separate acceptance checks. No production activation or publication is implied.
