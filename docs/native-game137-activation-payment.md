# Activated-ability automatic mana payment preserves source side effects

Game136 callback10482 pays Skullclamp equip with Elves of Deep Shadow. Automatic payment taps the Elf but leaves life at33; manual mana generation followed by the identical equip leaves32. The source definition already composes black mana with self damage. ActivationAutoTapper emitted taps/activation events and charged life costs, but deliberately skipped nonmana effect resolution.

Reuse ManaAbilitySideEffectExecutor.tapSourcesWithSideEffects for this source-tapping stage. Keep the activation payer's separate mana-pool assembly and deduction, including restricted production, excess and aura bonuses. The shared executor selects the actual produced-color ability, charges printed life costs and runs only its nonmana effects. It emits existing events once. No card-name branch, SDK shape, card definition, event/DTO, masking, legal action, continuation or client change is needed. The existing damage events already carry the corrected life change to clients and trigger detection. No new UI interaction is introduced.

The regression uses inline mechanic-level sources: painful mana, a painless/colored painful choice, multiple mana and life-as-cost. It covers automatic and selected-source payment, sufficient floating mana, correct mode matching, event counts and excess preservation. Existing source-activation trigger tests remain in the focused gate. Native saved-state comparisons authenticate the initial masked state/digest and compare automatic versus manually seeded identical payment.

Mana abilities resolve their effects immediately while costs are being funded. Official rules text was checked at https://media.wizards.com/2026/downloads/MagicCompRules%2020261106.txt (local frozen verified copy from Game135, mana-ability activation/resolution and instruction order). No new rule-number citation is needed in implementation.

Native full rules/card-scenario qualification and candidate adapter/facts requalification are required before another recorded game. Old549f95a remains frozen; this candidate is local only.
