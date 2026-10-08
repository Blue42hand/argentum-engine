# SDK consolidation — what the type graph and card usage say

**Snapshot:** 2026-10-08, `main` @ `fa3672e42a`. Point-in-time report: the counts go stale as cards
and SDK types land. Re-run the method below rather than editing the numbers.
**Status:** Proposed. Nothing here is started. **Owner:** TBD.
**Related:** [`engine-sdk-architecture-review.md`](engine-sdk-architecture-review.md) §5 ("one
spelling per concept", largely shipped in #2348) and §6 (generate the boilerplate, still open). This
doc picks up where §5 left off: §5 removed *parallel spellings of one concept*; this one targets
*whole groups of types that should be one concept*.

## Method

Every public declaration in `mtg-sdk/src/main` (1,904 types) was parsed for its kind, sealed family,
constructor fields (`name: Type`), KDoc and `@SerialName`. Each type was then measured against the
15,002 card definitions in `mtg-sets/src/test/resources/snapshots/cards/*.json`. The walk follows the
declared field types, so a shared discriminator like `"Fixed"` is credited to the right family
(`DynamicAmount.Fixed`, not `CostReductionSource.Fixed`).

Two types count as "alike" from a weighted mix of:
- identical `name: Type` fields (the main signal);
- shared words in the type name;
- same sealed family, same source file.

The result was rendered as an interactive graph (the "Argentum SDK Atlas" artifact, private to the
author) where alike types cluster.

**Limits.**
- Usage only sees what a card's JSON contains. The 206 types outside any sealed family (DSL
  builders, models, helpers) mostly can't be measured.
- Some "used by no card" results are false positives. `ManaSymbol` is one: mana costs serialize as
  strings.
- Name and field likeness finds candidates, not proof that a merge is safe. Each proposal below
  names the engine code that confirms it.

## What the data shows

### 1. A small core carries the corpus

| Family | Types | Types covering 90% of card uses | Types used by only 1–2 cards |
|---|---|---|---|
| `Effect` | 379 | 34 | 148 |
| `StaticAbility` | 190 | 40 | 105 |
| `CardPredicate` | 117 | 28 | 46 |
| `Condition` | 115 | 21 | 54 |
| `EventPattern` | 101 | 13 | 36 |

The most-used types are the composition primitives: `CompositeEffect` (5,096 cards), `GatedEffect`
(1,635), `ForEachEffect` (1,218) and the pipeline steps (`GatherCards`, `SelectFromCollection`,
`MoveCollection`, each in about 2,000 cards). Where a composable primitive exists, authors use it.

Across all sealed families, 666 of about 1,700 serialized types are used by one or two cards.

### 2. The long tail grows in two directions

Both come from the same habit: when a card needs something new, the cheapest change is a new type or
a new Boolean.

- **Narrow types.** 148 `Effect`s serve one or two cards: `TauntEffect`, `FlipTwoCoinsEffect`,
  `DrawUpToEffect`, `AddCountersWithLimitEffect` and so on. Each also needs an executor; the engine
  has 371.
- **Ever-wider types.**

  | Type | Fields | Booleans | Cards |
  |---|---|---|---|
  | `GrantMayPlayFromExileEffect` | 18 | 9 | 179 |
  | `PreventDamageEffect` | 15 | 6 | 139 |
  | `CreateDelayedTriggerEffect` | 13 | — | 162 |
  | `CreateTokenCopyOfTargetEffect` | 27 | 6 | — |
  | `CreateTokenEffect` | 26 | 6 | — |

  The two token effects repeat most of the same token fields.

### 3. Groups of types share one exact shape

Members of the same family whose constructors are identical:

| Family | Shared shape | Members | Examples |
|---|---|---|---|
| `Effect` | `(target: EffectTarget)` | 55 | Provoke, Taunt, Goad, MarkMustAttackThisTurn, MarkMustBlockThisTurn, ReflectCombatDamage |
| `StaticAbility` | `(filter: GroupFilter)` | 28 | CantBlock, CantAttack, CantBeBlocked, CanBlockAnyNumber, MustBlockEachAttacker |
| `EventPattern` | `(player: Player)` | 24 | MillEvent, ScryEvent, LifeLossEvent, SurveiledEvent, ProliferatedEvent |
| `Effect` | `(duration, target)` | 21 | CantAttackEffect, GainControlEffect, RemoveAllAbilitiesEffect, CantCastSpellsEffect |
| `KeywordAbility` | `(cost: ManaCost)` | 14 | Dash, Plot, Foretell, Miracle, Madness, Disturb |
| `Condition` | `(player: Player)` | 10 | IsPlayersTurn, PlayerHasMostLife, PlayerHasCitysBlessing |

Not every group should merge. `KeywordAbility` members carry different rules, and their shared shape
is just "a keyword with a cost". The combat and rule-modification groups are different: their
members differ only in which rule they switch on.

### 4. The same modification is spelled once as a static and once as an effect

Six concepts exist as both a `StaticAbility` and an `Effect`:

| Static | Effect | Static fields | Effect fields |
|---|---|---|---|
| `CantBlock` | `CantBlockEffect` | `filter` | `target, duration, attacker` |
| `CantAttack` | `CantAttackEffect` | `filter` | `target, duration` |
| `GrantKeyword` | `GrantKeywordEffect` | `keyword, filter` | `keyword, target, duration, condition` |
| `RemoveKeyword` | `RemoveKeywordEffect` | `keyword, filter` | `keyword, target, duration` |
| `ModifyStats` | `ModifyStatsEffect` | `powerBonus: Int, toughnessBonus: Int, filter` | `powerModifier: DynamicAmount, toughnessModifier: DynamicAmount, target, duration` |
| `MustBeBlocked` | `MustBeBlockedEffect` | `allCreatures, filter` | `target, allCreatures` |

The two halves have already drifted. The static `ModifyStats` takes a fixed `Int`, the effect takes
a `DynamicAmount`, and the fields are named differently.

In the engine both halves end at the same rule change, by two routes:
- **Static:** `StaticAbilityHandler` maps `is CantBlock ->` to `Modification.SetCantBlock`. That
  handler has 256 `is` branches.
- **Effect:** `CantBlockExecutor` resolves the target and calls `addFloatingEffect` with
  `SerializableModification.SetCantBlock`. About 66 executors follow that same
  "resolve target → add floating effect" shape.
- **Bridge:** `SerializableModification.toModification()` converts between the engine's two
  modification types, which have 63 and 52 variants.

80 `Effect` types carry a `duration` field, together used by 3,857 cards.

### 5. Ten families each define their own combinators

| Family | "all" | "any" | "not" |
|---|---|---|---|
| `Condition` | `AllConditions` | `AnyCondition` | `NotCondition` |
| `CardPredicate` | `And` | `Or` | `Not` |
| `StatePredicate` | `And` (0 cards) | `Or` | `Not` |
| `ControllerPredicate` | `And` | `Or` (0 cards) | `Not` |
| `ActivationRestriction` | `All` | — | — |
| `CastRestriction` | `All` (0 cards) | — | — |
| `ManaRestriction` | `AllOf` (0 cards) | `AnyOf` | — |
| `EventPattern` / `Recipient` / `SpellCastPredicate` | — | `AnyOf` | — |
| `AbilityCost` / `AdditionalCost` / `WardCost` | `Composite` | `Choice` (`AdditionalCost`, `WardCost`, `PayCost`) | — |

There are five spellings of "all of" and four of "any of". Four combinators are used by no card.

### 6. Branching is built into individual effects

The house pattern for "do X; if it happened, Y" is `Effects.IfYouDo` → `Gate.DoAction`. Clash's
"if you win" reuses that pattern. But several effects still carry their own branches:

| Effect | Branch fields |
|---|---|
| `FlipCoinEffect` | `wonEffect`, `lostEffect` |
| `FlipTwoCoinsEffect` | `bothHeadsEffect`, `bothTailsEffect`, `mixedEffect` |
| `SecretBidEffect` | `highestBidderEffect`, `lowestBidderEffect`, `tiedBidderEffect` |
| `OpponentGuessesTopCardKindEffect` | `onGuessedRight`, `onGuessedWrong` |
| `OpenLifeBidEffect` | `onWin` |
| `BeholdEffect` | `ifBeheld`, `otherwise` |
| `MayRevealCardFromHandEffect` | `otherwise` |
| `ConditionalOnCollectionEffect` | `ifNotEmpty`, `ifEmpty` |

## Proposals

In order of payoff. Each one is its own PR series; each migration should be proven by the card
snapshot goldens (alpha-equivalent re-bless), as #2348 was.

### P1. One continuous-modification vocabulary, applied two ways

Give the SDK one sealed `ContinuousModification` set, with variants like `CantBlock`,
`CantAttack`, `GrantKeyword(k)`, `RemoveKeyword(k)`, `ModifyStats(p, t)` and `MustBeBlocked`. It
can be applied in two ways:
- `StaticAbility.Continuous(modification, affected: GroupFilter)`, for a permanent's static ability;
- `Effects.Apply(modification, target, duration)`, for a resolving spell or ability.

**Why first.** It removes a type, an executor and a handler branch per concept. A new rule change
becomes one SDK variant plus one applicator branch. Today it costs:
- a static type and an effect type;
- an executor;
- a `StaticAbilityHandler` branch;
- a variant in both engine modification types.

It also stops the static and effect halves drifting. The amount should be `DynamicAmount` on both
sides, locked in at application time for effects.

**Engine side.** One generic executor for `Apply`, which does what the 66 floating-effect executors
already do. `StaticAbilityHandler` gets one `is Continuous` branch. Collapsing `Modification` and
`SerializableModification` into one is a natural follow-up, not a prerequisite.

**Pilot.** Migrate `CantBlock`, `CantAttack` and `GrantKeyword` end to end, both halves, with the
facades unchanged. Cards then don't move; only goldens re-bless.

### P2. Combat rules as data

Once P1 exists, most of the 55 target-only `Effect`s and 28 filter-only `StaticAbility`s are
modification variants: `CantBlock`, `CantAttack`, `MustAttack`, `MustBlock`, `Goad`, `Provoke`,
`Taunt`, `CanBlockAnyNumber`, `CanBlockAsThoughUntapped`, `CanAttackDespiteDefender` and more.
Check each against its executor before folding. Some, like Provoke's untap-and-block or
`ReflectCombatDamage`, do more than set a flag and may stay bespoke.

### P3. One combinator spelling

Pick one name per combinator and use it in every family: `AllOf(list)`, `AnyOf(list)`, `Not(x)`
for logic, and `AllOf` / `OneOf` for costs. Share one evaluator helper per shape on the engine side.
Delete the four combinators no card uses, or keep them deliberately with a test.

This is a rename codemod with an `@SerialName` decision. Either re-bless the goldens, or keep the
old discriminators as aliases until the snapshot sweep.

### P4. Effects report results; `Gated` does the branching

Turn each effect in §6 into "perform the action and record the outcome" (won / lost, heads count,
highest bidder, guessed right). Branching then goes through `Effects.If` / `Gate.DoAction` on that
outcome. This removes about eight bespoke branch shapes and the executor code that walks them, and
any future "flip / vote / guess / bid" card composes for free.

Two prerequisites:
- the outcome has to live somewhere a `Condition` or `SuccessCriterion` can read it, such as the
  pipeline's named results;
- check the clash-won trigger-context precedent before adding a new channel.

### P5. Split the wide types into named options

- **`TokenSpec`.** Extract the token description that `CreateTokenEffect` and
  `CreateTokenCopyOfTargetEffect` share (P/T, colors, types, keywords, abilities, tapped,
  attacking, exile/sacrifice-at-step).
- **`CastPermission`.** Group `GrantMayPlayFromExileEffect`'s 18 fields:
  - what may be cast (`nonLandOnly`, `castFaceIndex`, `castColorRestriction`);
  - how it's paid (`withAnyManaType`, `colorlessAsAnyColor`, `fixedAlternativeManaCost`,
    `fixedAlternativeCostIsManaValue`, `waterbend`);
  - when (`asThoughFlash`, `expiry`, `singleUse`).
- **`PreventDamageEffect`.** Replace co-dependent Booleans with sealed options. The prevention
  rider (`gainLifeFromColors`, `gainLifeFromPrevented`, `halvePreventedDamage`, `onPrevented`)
  becomes one `onPrevented` value.

The rule this encodes: **a new Boolean on a type with four already is a design smell**. Add an axis
type instead.

### P6. A cheap guard against the long tail

Add a non-blocking report, then a lint, run when a new `Effect` / `StaticAbility` / `Condition`
subtype is added. It would:
- list the new type's three closest existing types by field and name likeness (the Atlas
  "Most alike" list);
- say how many card snapshots use it.

A new type that matches an existing one at 75% or more of its fields should usually be a field on
that type, or a composition, and the PR should say why it isn't. Track the "used by 1–2 cards"
share per family over time. It should fall as P1–P4 land.

## Not proposed

- **Merging the `KeywordAbility` `(cost: ManaCost)` group.** The members share a shape but not
  rules. Dash, Plot and Foretell rightly stay distinct.
- **Deleting every "used by no card" type.** Some are reached only through engine code, tests or
  string-serialized fields. Check each against `rules-engine` before deleting.
