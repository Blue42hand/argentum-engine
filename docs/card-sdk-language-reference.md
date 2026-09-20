Warning: truncated output (original token count: 332904)
... 283039 bytes omitted ...

# Card SDK Language Reference

A complete catalog of every building block available to card authors in the Argentum
Engine `mtg-sdk`, with a one-line description for each. Designed to be scanned and
searched. For step-by-step authoring workflow see [`api-guide.md`](api-guide.md) (and use the
`add-card` skill); for hard cases see
[`managing-complex-and-rare-abilities.md`](managing-complex-and-rare-abilities.md).

**Maintenance rule:** this document is the canonical SDK catalog. **Every change to the
SDK — new effect, trigger, condition, filter, cost, keyword, dynamic amount, modal
shape, replacement effect, etc. — must update the matching section here in the same
change.** If the entry doesn't fit cleanly in an existing section, add or rename a
section; do not let SDK additions land without a corresponding doc update.

---

## 0. Game formats

`Format` is the runtime rules configuration, separate from deck-construction `DeckFormat`.

- `Format.Commander` — enables per-player commanders, command zones, commander damage, command-zone
  casting tax, and the command-zone replacement choice. Its configuration includes
  `commanderDamageThreshold`, `deckSize`, `startingLife`, `startingHandSize`, and
  `alwaysDivertToCommand`. Nothing in it is per-seat-count: commander damage is tallied per
  *(commander, defending player)* pair, so the same instance runs a 1v1 match and an N-player pod.
- `CommanderPreset` — the life / commander-damage / deck-size tunings a limited Commander lobby
  converts to a `Format.Commander` at match start: `BRAWL` (60/25/16) and `COMMANDER` (60/30/21) pace
  a 1v1 match, `POD` (60/40/21) is paper multiplayer Commander and is what any multiplayer table
  plays at. `toFormat()` does the conversion.
- `Format.TeamVsTeam` — team membership with individual turns and life totals. By default it is a
  normal 20-life format with no commanders. Supplying `commanderDamageThreshold` and `deckSize`
  opts it into the same Commander rules while retaining Team-vs-Team seating and win conditions.
- `Format.TwoHeadedGiant` — shared life, turns, combat, and team loss. It deliberately does not expose
  Commander configuration because Two-Headed Giant and Commander have conflicting starting-life rules.
- `Format.usesCommanders` — capability flag derived from a non-null `commanderDamageThreshold`; engine
  systems use this instead of checking for one concrete format subtype.
- `GameRules` — the *lobby-facing* rules selection (`STANDARD` / `COMMANDER`) that resolves to a
  `Format` at match start, with `usesCommanders` mirroring the engine's flag. It is its own axis:
  orthogonal to `DeckFormat` (what may go in a deck), to where the cards came from (a brought deck,
  a sealed pool or any draft shape — the Commander-Legends 20-card pack is a pool property, not a
  rules one), and to the table. `GameRules.inferred(commanderPackShape, deckFormat)` is the single
  back-compat derivation for lobbies created before the axis existed. Oathbreaker and Pauper
  Commander become values here rather than new draft shapes.

## 1. Top-level card DSL

**Entry points**

- `card("Name") { ... }` — open the builder for a standard card.
- `basicLand("Plains" | "Island" | "Swamp" | "Mountain" | "Forest" | "Wastes")` — shortcut for basic lands (sets
  type line, intrinsic mana ability, supertype). The five colored types get a subtype after the dash and tap for
  their color; `"Wastes"` is the colorless basic — type line `Basic Land` with no subtype, intrinsic `{T}: Add {C}`.
  Supports `collectorNumber`, `artist`, `flavorText`, `imageUri`, `rarity`, and `inBooster` (set `false` to keep an
  art variant defined but exclude it from the draft/sealed deck-building basic pool).
  A set declares one `basicLand(...)` per art variant. Limited deck building hands out exactly **one** printing per
  land type — the set's *standard* art, i.e. its lowest-numbered `inBooster` variant (`BasicLandArt.standardFirst`),
  since paper numbers the plain booster arts inside the main set numbering and appends full-art / extended /
  borderless treatments above the set's card count. So `collectorNumber` is what decides which art a drafted deck
  is played with: give a special treatment a number below the regular art and limited will use the treatment.

**Card builder properties**

- `manaCost: String` — mana cost in `{X}{R}{U}` syntax. Supported pip forms: generic (`{2}`),
  colored (`{R}`), colorless (`{C}`), variable (`{X}`), hybrid (`{W/U}` — either colour),
  Phyrexian (`{W/P}` — colour or 2 life), and monocolored hybrid / "twobrid" (`{2/B}` — two
  generic **or** one mana of the colour; mana value counts the generic side per CR 202.3f).
  Gurmag Nightwatch's `{2/B}{2/G}{2/U}` is the canonical twobrid example.
- `typeLine: String` — full type line including supertypes and subtypes. A `Legendary Instant` /
  `Legendary Sorcery` automatically gets the CR 205.4e casting restriction (can be cast only while
  its controller controls a legendary creature or legendary planeswalker) — the engine enforces this
  from the type line in both legal-action enumeration and the cast handler; no per-card opt-in needed.
- `oracleText: String` — rules text; auto-generated from abilities if omitted.
- `power: Int?`, `toughness: Int?` — base P/T for creatures.
- `dynamicPower`, `dynamicToughness` — characteristic-defining P/T (e.g. `*/*` Tarmogoyf), as
  `CharacteristicValue` properties. Prefer the builder helpers below over assigning them directly.
- `dynamicPower(source, offset?)` / `dynamicToughness(source, offset?)` — set one
  characteristic-defining stat from a `DynamicAmount` with an optional `±` delta. Use these when
  only one stat is dynamic (Duelist of the Mind's `*`/3) or when the two read *different* sources
  (Yavimaya Kavu: power = red creatures, toughness = green creatures).
- `dynamicStats(source, powerOffset?, toughnessOffset?)` — the `*`/`*` cycle: composes
  `dynamicPower` + `dynamicToughness` over one shared source, with optional `±` deltas
  (Tarmogoyf's `toughnessOffset = 1`).
- `startingLoyalty: Int?` — starting loyalty for planeswalkers. Placed as loyalty counters by the
  engine's intrinsic enters-with replacement (CR 306.5b) on *every* battlefield entry — resolving from
  the stack, reanimation, an "exile until this leaves" return, any other put-onto-the-battlefield
  effect, and token copies (loyalty is a copiable value, CR 707.2). Card definitions never place them.
- `startingDefense: Int?` — printed defense for **battles** (`typeLine = "Battle — Siege"`), the number
  in the card's lower right corner (CR 310.4a). Placed as defense counters by the same intrinsic
  enters-with replacement as `startingLoyalty` (CR 310.4b), on every battlefield entry. A battle's
  defense on the battlefield *is* its defense-counter count (CR 310.4c), so nothing reads this field
  once it is in play. See the **Battles** section below; `CardValidator` rejects a battle without it.
- `colorIdentity: String?` — override (normally auto-detected). Treated as authoritative in this repo.
- `colorIndicator: String?` — explicit color indicator (CR 204), e.g. `"B"`. `null` (default) = no
  indicator; the card's color is its mana-cost colors alone. Set it on a face printed with a color
  indicator instead of colored mana symbols — most often a transforming DFC back face with an empty
  mana cost (e.g. The Grim Captain's black back face reads as black despite `manaCost = ""`). The
  indicated colors combine with any mana-cost colors (CR 202.2) and fold into color identity (CR 903.4).
  Prefer this over the older `colorIdentity`-only approximation, which left such faces colourless.
- `auraTarget: TargetRequirement?` — what this Aura enchants. Usually a permanent (`Targets.Creature`),
  but `Targets.Player` makes it an **"enchant player"** Aura: it attaches to a player via
  `AttachedToComponent` (players are entities too), survives state-based actions while that player is in
  the game, and exposes the player through `Player.EnchantedPlayer` / `EventPattern.LifeGainEvent(EnchantedPlayer)`
  and a `takesDamage(binding = ATTACHED)` "whenever enchanted player is dealt damage" trigger. (Grievous Wound.)
  The filter is **not** just a cast-time target check: per CR 303.4c it restricts what the Aura may stay
  attached to, so the engine re-evaluates it against the projected host after every state change and sends
  the Aura to its owner's graveyard once the host stops matching (CR 704.5m). Write the filter to cover
  every type the Aura's *own* effects can turn its host into, exactly as the printed card does — Imprisoned
  in the Moon makes its host a land and so enchants "creature, land, or planeswalker"; an Aura that turns a
  creature into a land while enchanting only `Targets.Creature` would destroy itself on resolution.
- `morph: String?` — morph mana cost (cast face-down).
- `morphCost: PayCost?` — non-mana morph cost.
- `morphFaceUpEffect: Effect?` — effect that fires when this morph turns face up.
- `disguise: String?` — disguise mana cost (CR 702.168): cast face down for `{3}` as a 2/2 **with
  ward {2}**, flip for this cost.
- `disguiseCost: PayCost?` — non-mana disguise cost.
- **`{X}` in a turn-up cost** — `Disguise {X}{3}{W}` (Aurelia's Vindicator) is legal, and the chosen X
  is readable by the card's `Triggers.TurnedFaceUp` ability as **`DynamicAmount.XValue`** — carried
  `TurnFaceUp.xValue` → `TurnFaceUpEvent.xValue` → `TriggerContext.xValue` → the trigger's stack
  object → `EffectContext.xValue`. It is **not `DynamicAmount.CastX`**, which is the X paid to cast a
  *spell*: a disguised card was cast face down for `{3}`, with no X anywhere in that cost, so `CastX`
  reads 0 here. The same `XValue` feeds a `dynamicMaxCount` target cap ("exile up to X other target
  creatures"), snapshotted when the trigger goes on the stack. Turn-up X applies to morph costs
  identically; disguise is simply where it is printed.
- `disguiseFaceUpEffect: Effect?` — the disguise-side sibling of `morphFaceUpEffect`, for the
  "As this creature is turned face up, …" replacement clause (Bubble Smuggler = "put four +1/+1
  counters on it" → `Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 4, EffectTarget.Self)`). It is
  applied **as part of the turn-up special action**, so it doesn't use the stack and can't be
  responded to — that is what separates it from a `Triggers.TurnedFaceUp` ability ("When this
  creature is turned face up, …", Granite Witness / Exit Specialist), which does use the stack.
  Like `morphFaceUpEffect` it rides the *turn-up procedure* (CR 702.37b's megamorph treatment), so
  a card put face down by cloak or manifest and flipped for its mana cost instead of its disguise
  cost does not get it.
- `disguiseCostReduction: CostReductionSource?` — "Disguise {5}{R}. This cost is reduced by {1} for
  each instant and sorcery card in your graveyard" (Fugitive Codebreaker). A **self**-scoped generic
  reduction on this card's own disguise cost, carried on `KeywordAbility.Disguise.costReduction` and
  travelling with the card into the face-down permanent's turn-up procedure — as distinct from
  `SpellCostTarget.MorphActivation`, which is the battlefield-scanned modifier that prices *every*
  player's turn-up (Exiled Doomsayer). Takes the same `CostReductionSource` values a
  `ModifySpellCost` static does, and obeys the same rules: increases first, then reductions
  (CR 601.2f), generic mana only, so a disguise cost's colored pips are a floor. Re-read at every
  price check, so a card that hits the graveyard after the permanent came down moves the price. The
  enumerated turn-up action quotes the reduced cost, so the button matches what is charged.
- `warp: String?` — Warp alt-cost; exiles at end of turn.
- `dash: String?` — Dash alt-cost (CR 702.109); gains haste and returns to owner's hand at the
  beginning of the next end step.
- `evoke: String?` — Evoke alt-cost; sacrifices on ETB.
- `selfAlternativeCost: SelfAlternativeCost?` — generic alternative-cost slot.
- `castTimeCreatureTypeChoice: CastTimeCreatureTypeSource?` — forces a creature-type choice at cast time.
- `cantBeCountered: Boolean` — spell is uncounterable.
- `cantBeCopied: Boolean` — spell can't be copied (CR 707.10); copy effects that name it create no copy (Display of Power).
- `conditionalFlash: Condition?` — gains flash while condition holds.
- `layout: CardLayout` — physical layout shape (see §2).
- `meldResult: Boolean` — this card is the permanent a **meld pair** combines into (CR 701.42) — Chittering Host,
  Brisela, Voice of Nightmares, Hanweir, the Writhing Township, Ragnarok, Divine Deliverance. Set it on the *result*,
  never on the meld parts (those are ordinary cards). A meld result is physically the two parts' back halves, so it is
  never opened, drafted, or put in a deck: the flag drops it from the booster/sealed pool (`BoosterGenerator`), the
  constructed pool (`FormatCardPool`), and random AI decks. Scryfall can't be the source — it reports meld results as
  `booster: true` and format-legal, because the card they're printed on is. The result is still authored as a normal
  card so the corpus carries its characteristics (meld itself is not modelled; the parts' meld triggers are unwired),
  and scenario tests can still put it directly onto the battlefield.

**Ability blocks inside `card { ... }`**

- `triggeredAbility { ... }` — "when/whenever/at" abilities.
- `staticAbility { ... }` — continuous effects.
- `activatedAbility { ... }` — `cost: effect` abilities.
- `loyaltyAbility(±N) { ... }` — planeswalker loyalty abilities.
- `replacementEffect { ... }` — "instead/if … would" replacement.
- `keywords(...)` / `keywordAbility(...)` / `keywordAbilities(...)` — add keyword abilities.
- `spell { ... }` — define the spell payload for instants/sorceries and Adventure / Omen faces.
- `mayBeginGameOnBattlefield()` — the "If this card is in your opening hand, you may begin the game with it on
  the battlefield" ability (CR 103.6a). Reusable across the Leyline enchantment cycles and non-"Leyline of X"
  cards alike (e.g. Leyline Axe). Sets `CardScript.mayStartOnBattlefield = true`. After all mulligans and
  bottoming resolve, the engine walks each player in turn order from the active player and presents a yes/no
  decision per such card in their opening hand; a "yes" routes the card to the battlefield through the standard
  zone-change pipeline before the first turn begins, a "no" leaves it in hand.

### Battles (CR 310)

A **battle** is a permanent that gets *attacked* rather than one that attacks. It is a card type
(`CardType.BATTLE`), not a keyword, and everything about it is intrinsic — a battle card declares only
its type line and its printed defense:

```kotlin
val invasionOfSomewhere = card("Invasion of Somewhere") {
    manaCost = "{2}{B}{B}"
    typeLine = "Battle — Siege"
    startingDefense = 5
    // triggered/activated abilities as usual
}
```

The engine supplies the rest; **do not** write any of it onto the card:

- **Defense is counters.** The battle enters with `startingDefense` defense counters (CR 310.4b) via the
  same intrinsic enters-with replacement that places planeswalker loyalty, so it applies to every entry
  path. On the battlefield its defense *is* that count (CR 310.4c), and damage dealt to it removes that
  many counters (CR 120.3h) rather than being marked. A battle at 0 defense is put into its owner's
  graveyard as a state-based action (CR 704.5v for a Siege, CR 704.5w for any other battle — only a
  Siege gets the reprieve that keeps it alive while its own defeat trigger is on the stack).
- **A protector defends it.** Every battle has a player designated as its protector (CR 310.9), stored in
  `ProtectorComponent` and assigned by the CR 704.5x / 704.5y state-based actions — silently when only one
  player is eligible (every two-player game), otherwise by prompting the battle's controller. A **Siege**'s
  protector must be an opponent of its controller (CR 310.12a); a battle with no battle types is protected
  by its own controller (CR 310.9a). If no player qualifies, the battle is put into its owner's graveyard.
- **Its protector, not its controller, is the defending player** for every rule and effect while it is
  being attacked (CR 310.9d). That asymmetry is the point of a Siege: you cast it, an opponent protects it,
  and *you* attack it. Its protector can never attack it (CR 310.9b) and is the only player who may block
  creatures attacking it (CR 310.9c).

- **A Siege is defeated, not destroyed.** Every Siege has the intrinsic trigger "when the last defense
  counter is removed from this permanent, exile it, then you may cast it transformed without paying its
  mana cost" (CR 310.12b), supplied as `com.wingedsheep.sdk.scripting.Sieges.defeatAbility` and granted by
  `TriggerAbilityResolver` to any permanent whose *projected* types make it a Siege. It is a
  `countersRemovedFrom(counterType = Counters.DEFENSE, lastRemoved = true, binding = SELF)` trigger over a
  `GatherCards(Self) → MoveCollection(→ exile) → MayEffect(CastFromCollectionWithoutPayingCost(castTransformed = true))`
  pipeline. **A Siege card therefore only needs its `startingDefense` and its back face** — write the front
  face's own abilities and nothing else. Two consequences worth knowing: a Siege that never had a defense
  counter (a permanent that became a copy of one) can't have its "last" removed, so CR 704.5v bins it and
  nothing is exiled or cast; and a Siege with no transforming back face is exiled and simply stays there.

Engine-side helpers all live on `com.wingedsheep.engine.mechanics.battle.Battles` (`protectorOf`,
`defenseOf`, `eligibleProtectors`, `canBeAttackedBy`); `ProjectedState.isBattle(entityId)` is the type
check. The client receives a battle's defense in the ordinary `counters` map and its protector as
`ClientCard.protectorId`. A battle is a legal choice for "any target" (CR 115.4) alongside creatures,
players and planeswalkers.

Battles are printed **landscape** — the image is a portrait file holding a sideways card, as a
Room's is. **`CardDefinition.isLandscapePrint` is the single place that decides what counts as
printed sideways** (split layouts including Rooms, plus battles); a future landscape card type is
one clause there and nothing else. It reaches the client as `ClientCard.isLandscapeFace` (in-game:
battlefield, stack, hover preview) and `SealedCardInfo.isLandscape` (sealed / draft / deckbuilder /
cube previews, via the one `landscapeImageRotateDeg` helper). Renderers read the flag rather than
re-deriving orientation from `isRoom` / `cardFaces` / type lines — doing that in three different
ways is exactly how battles ended up rendering sideways. The flag is per *face*: a Siege reports
true, the portrait back face it becomes when defeated reports false, and `backFaceIsLandscape`
carries the other side for the hover preview's flip toggle.

---

## 2. Card faces, layouts, printings, set metadata

**`CardLayout`**

- `NORMAL` — standard single face (default).
- `SPLIT` — two or more halves on one card; combined characteristics apply off-battlefield (CR 709.4c). Used for Rooms,
  Fuse, Aftermath, and the classic Invasion split cards (Pain // Suffering, Stand // Deliver, Wax // Wane). Each half is
  cast independently via `CastSpell.faceIndex`; only the chosen half goes on the stack (CR 709.4). A non-permanent half
  carries its effect in a `face("Name") { spell { … } }` block (with its own `target(...)` requirements); a permanent
  half (Room) carries triggered/activated/static abilities instead.
  - **Room face abilities are door-gated (CR 709.5).** A Room face's abilities function only while that door is
    unlocked. Triggered abilities are scoped to the unlocked face in `TriggerDetector`; **static** abilities —
    continuous effects, `GrantActivatedAbility` (incl. granted mana abilities), and `NoMaximumHandSize` — are folded
    in by the engine helper `RoomFaceStatics.activeStaticAbilities(container, cardDef)`, the single source of truth
    every battlefield static-ability scan reads (continuous-effect projection, clickable granted abilities, the mana
    auto-payer, no-maximum-hand-size). So a static printed on a Room face (e.g. Greenhouse's "Lands you control have
    '{T}: Add one mana of any color.'") works exactly like the same static on a normal permanent, but only once its
    door is unlocked. The baked continuous-effect component is refreshed when a door unlocks (via `RoomDoorUnlocker`),
    the same way a transform re-bakes it. (Replacement effects on a Room face are not yet door-gated — no current card
    needs one.)
- `ADVENTURE` — primary face is a permanent (usually a creature; **may also be a land** — FIN Towns), `cardFaces[0]`
  is an instant/sorcery Adventure (CR 715). Resolving the Adventure exiles the card and grants permission to play
  the primary face from exile — *cast* the creature, or *play* the land for a **land // spell** Adventure
  (`Land — Town // Sorcery — Adventure`, e.g. Ishgard, the Holy See // Faith & Grief). The generic may-play
  permission covers both; from hand a land-primary Adventure offers *play the land* (PlayLandEnumerator) **and**
  *cast the Adventure spell* (`CastSpell.faceIndex = 0`). An `{X}` in the *face's* own mana cost is supported —
  the face's cast action carries `hasXCost`/`maxAffordableX`, so the client opens its X picker and
  `DynamicAmount.XValue` in the face's `spell { }` reads the declared X (An Unexpected Party // At the Door,
  `{X}{2}{W}` "Create X 2/2 red Dwarf creature tokens").
- `OMEN` — primary face is a permanent (creature), `cardFaces[0]` is an instant/sorcery Omen (Tarkir: Dragonstorm).
  Casts exactly like an Adventure (creature face, or Omen via `CastSpell.faceIndex = 0`), but resolving the Omen
  **shuffles the card into its owner's library** instead of exiling it — no cast-from-exile linkage. DSL:
  `card { omen("Name") { spell { … } } }`.
- `MODAL_DFC` — primary characteristics are the front face; the caster picks one face before the card goes on the
  stack and only that face is evaluated (CR 712.11b/712.11c). Two shapes, by what the back face *is*:
  - **Spell back** — `cardFaces[0]`, cast via `CastSpell.faceIndex = 0`. No exile-then-recast linkage: it resolves
    as an ordinary spell (graveyard, or exile when its script sets `selfExileOnResolve` via `spell { selfExile() }`).
    DSL: `card { modalBack("Name") { spell { … } } }`. Flamescroll Celebrant // Revel in Silence.
  - **Permanent back** — a full `CardDefinition` in `backFace`, because it needs P/T, keywords and battlefield
    abilities. Built with `CardDefinition.modalDoubleFacedPermanent(front, back)`, cast via
    `CastSpell(useAlternativeCost = true, alternativeCostType = MODAL_BACK_FACE)` for the **back face's own printed
    mana cost**, and put on the stack *transformed* — the same engine path as disturb. CR 712.3 lets such a card
    also transform, so the front's `{cost}: Transform …` ability reaches the same back face. The back keeps its
    printed mana cost and takes **no** color indicator: per CR 712.8f a modal back face has its own mana value
    (unlike CR 712.8e for nonmodal DFCs, where it stays the front's). The Marvel Super Heroes hero cycle —
    Jennifer Walters // The Sensational She-Hulk, Bruce Banner // The Incredible Hulk, King T'Challa, Tony Stark,
    Monica Rambeau.
  - **Land back** — a full `CardDefinition` in `backFace`, built with
    `CardDefinition.modalDoubleFacedLand(front, back)`. Neither face is ever *cast*: CR 712.12 makes this a
    **play-a-land** choice — *"A player playing a modal double-faced card as a land chooses one of its faces
    that's a land before putting it onto the battlefield. It enters the battlefield with that face up."* So the
    card shows up as **two land plays** in hand (`PlayLandEnumerator` emits one per land face, each named for the
    face it plays) and `PlayLand.asBackFace` says which was taken. Neither face carries a mana cost or a color
    indicator. Once down the permanent has only the played face's characteristics (CR 712.8f) and can never turn
    over (CR 712.9 excludes modal DFCs from transforming); off the battlefield the card is its front face again
    (CR 712.8a). The ten-card Pathway cycle, split across Zendikar Rising (six) and Kaldheim (four) —
    Riverglide Pathway // Lavaglide Pathway, Hengegate Pathway // Mistgate Pathway, and the rest.
- `PREPARE` — primary characteristics are the creature face, `cardFaces[0]` is the **prepare spell** (an
  instant/sorcery) (Secrets of Strixhaven). The card is only ever cast as the creature; the prepare spell is never
  cast from hand. A creature that carries `Keyword.PREPARED` ("This creature enters prepared") becomes prepared on
  enter; one without the keyword (e.g. Leech Collector) only becomes prepared via an effect — `Effects.BecomePrepared(target)`.
  When it becomes prepared, the engine creates a **copy of the prepare spell in exile** that the controller may cast for
  the face's cost — surfaced by the cast-from-exile enumerator as `CastSpell(..., faceIndex = 0)` from `EXILE`.
  Casting the copy unprepares the creature; the copy ceases to exist on resolution. The exile copy persists in
  exile (exempt from the 707.10a phantom-copy SBA) until the source leaves the battlefield or stops being prepared,
  at which point it is cleaned up. A creature already prepared does not re-prepare. DSL: `card { prepare("Name") { spell { … } } }`.

**`CardFace` (SPLIT / ADVENTURE / OMEN / MODAL_DFC / PREPARE)**

- `name` — face name.
- `manaCost` — face mana cost.
- `typeLine` — face type line.
- `script { ... }` — that face's abilities; for instant/sorcery SPLIT halves, Adventures, and modal DFC spell
  faces this includes a `spell { effect = …; target(...) }` block holding the face's effect and target
  requirements (plus `selfExile()` for faces that exile themselves on resolution).
- `keywords` — face-local keywords.
- `imageUri` — face art when it differs from the front (MODAL_DFC backs have their own Scryfall image).

**`metadata { ... }`**

- `rarity: Rarity` — `COMMON | UNCOMMON | RARE | MYTHIC | SPECIAL | BONUS`.
- `collectorNumber: String` — Scryfall collector number.
- `artist: String` — illustrator credit.
- `flavorText: String` — italicized flavor.
- `imageUri: String?` — art URL; auto-fetched from Scryfall if omitted.
- `imageUriByCreatureSubtype: Map<String, String>` — optional display-only alternate art selected
  from a battlefield permanent's projected creature subtypes. Because the server evaluates the
  projected subtype, the art appears and reverts with continuous type-changing effects; the client
  only renders the selected URI.
- `imageRotation: Int` — clockwise degrees to rotate the art when rendered (default `0`). Set `180` for
  flip-layout tokens whose only Scryfall image shows the other face upright — the WOE Role tokens are printed
  two-to-a-card (`Wicked // Cursed`, `Monster // Sorcerer`), so the bottom face (`Cursed`, `Sorcerer`) reads
  upside-down on the single image. Purely cosmetic: flows SDK → `ClientCard.imageRotation` → client CSS transform;
  the engine never reads it.
- `scryfallId: String?` — Scryfall UUID.
- `releaseDate: String?` — `YYYY-MM-DD`.
- `inBooster: Boolean` — part of the draft/sealed product (default `true`; `false` for Special Guests / starter
  exclusives). Gates both the booster pool and the basic-land variants offered during limited deck building.
  It mirrors Scryfall's product-level `booster` flag, so it is *not* the lever for meld results (Scryfall marks
  those `true`) — use the card-level `meldResult` flag in §1 for those.
- `oracleTextOverride: String?` — bypass auto-generated oracle text.

**Reprints** — add a `Printing` row in the new set's `Reprints.kt` and wire it into `MtgSet.printings`. Never duplicate
the `CardDefinition`.

**`Printing`** — a presentation-only row for one printing of a card (oracle identity stays on the `CardDefinition`).
Carries `setCode`, `collectorNumber`, `scryfallId`, `artist`, `imageUri`, `backFaceImageUri`, `releaseDate`, `rarity`,
plus the frame fields:

- `isFullArt: Boolean` — Scryfall full-art treatment.
- `frameEffects: List<String>` — Scryfall `frame_effects` (e.g. `["showcase"]`, `["inverted"]`).
- `borderColor: String?` — Scryfall `border_color` (`"black" | "white" | "borderless"`).
- `isAlternateFrame: Boolean` (derived) — true when the printing is a **showcase** frame
  (`"showcase" in frameEffects`) or **borderless** (`borderColor == "borderless"`). This is the predicate the booster
  variant slot selects on; plain full-art / promo treatments are not counted.

`CardDefinition.withPrinting(printing)` returns a copy presenting that printing — it overlays only presentation
metadata (set code, collector number, art, artist, Scryfall id, and the back-face art for genuine DFCs) and leaves the
card's oracle identity untouched.

**Showcase / borderless in boosters** — a set advertises a per-card variant rate via `MtgSet.boosterVariantChance`
(default `0.0`). When non-zero, `BoosterGenerator` rolls each generated card independently and, on a hit, re-skins it
with one of its `isAlternateFrame` `printings` of the same name (via `applyVariantPrintings` →
`CardDefinition.withPrinting`). The swap is presentation-only: it changes the art shown in the draft/sealed pool, not
the card's rules or its in-game (name-resolved) art. Lorwyn Eclipsed sets `boosterVariantChance = 0.15` and contributes
its showcase/borderless rows via `LorwynEclipsedVariantPrintings` — the play-booster treatments only, with the
collector-only ones (reversible shocklands, Japanese Showcase, Fracture Foil, serialized/headliner chase cards)
excluded.

---

## 3. Costs (`Costs.*`)

Spell mana costs containing Phyrexian symbols (for example `{B/P}`) are paid per pip with either
one mana of that color or 2 life. Manual payment records the chosen life-paid pip colors as a
multiset on `PaymentStrategy.Explicit.phyrexianLifePayments`; the engine validates that those pips
exist in the cost and charges the life through the shared life-payment service.

> **One cost vocabulary (`CostAtom`).** The payable things shared across cost *contexts* — mana, life,
> sacrifice, discard, exile-from-zone, tap, return-to-hand, reveal — are defined **once** in the
> `CostAtom` sealed hierarchy (`scripting/costs/CostAtom.kt`). All three context wrappers carry them via
> an `Atom(atom)` member: `PayCost.Atom`, `AdditionalCost.Atom`, and `AbilityCost.Atom` each hold one
> `CostAtom`, leaving only their genuinely context-specific members on the wrapper
> (`PayCost.OwnManaCost` / `PayCost.Choice`; `AdditionalCost`'s Behold / Blight / Forage / ChooseEntity
> / per-target life / variable exile; `AbilityCost`'s `Free`, `Tap`/`Untap`, the X-variable costs
> (`PayXLife`, `ExileXFromGraveyard`, `TapXPermanents`), the self-referential `SacrificeSelf` /
> `ExileSelf` / `ReturnSelfToHand` / `ExileGrantingPermanent`, counter-removal, `Loyalty`, `Composite`, and named mechanics
> `Forage` / `Blight` / `Craft`). The `Costs.*` facades below are unchanged — they construct the right
> `…Atom(CostAtom.X(…))` for you, so card authoring is identical. A *new* payable thing is one
> `CostAtom` variant + one engine payment branch, available in every context.

- `Costs.Free` — costs nothing (`{0}`).
- `Costs.Tap` — `{T}`; tap this permanent.
- `Costs.Untap` — `{Q}`; untap this permanent.
- `Costs.Exert` — exert this permanent (CR 701.43a, `AbilityCost.Exert`): it won't untap during its
  controller's next untap step. Always payable regardless of tapped/exerted state (701.43b) —
  `canPayAbilityCost` returns `true` unconditionally, and re-exerting before the next untap step is
  a no-op (doesn't stack multiple skips). Backed by a new per-object marker component
  (`ExertedComponent`), not a continuous static ability like `AbilityFlag.DOESNT_UNTAP` — the
  untap step (`BeginningPhaseManager`) both skips untapping an exerted permanent and clears the
  marker *unconditionally* every untap step for that permanent's controller, whether or not it
  actually prevented an untap (2024-06-07 ruling), unlike a stun counter which is only consumed
  when it does. Exposed client-side as `ClientCard.isExerted`. Distinct from the "you may exert
  [this] as it attacks" attack-cost template (701.43d) — that's a separate optional-cost-to-attack
  shape, not an ability cost; only the cost-component shape is implemented so far. First user: Arena
  of Glory (MH3) — `Costs.Composite(Costs.Mana("{R}"), Costs.Tap, Costs.Exert)`.
- `Costs.Mana("{2}{U}")` — pay the given mana cost (string or `ManaCost`).
- `Costs.PayLife(amount)` — pay N life.
- `Costs.PayXLife` — pay X life, where X is the value chosen for the ability's `{X}` mana cost
  (e.g. "{X}{B}, {T}, Pay X life: …" on Krumar Initiate). The X-linked counterpart to
  `Costs.PayLife`; `calculateMaxAffordableX` caps X at the controller's life total — X may go as
  high as their current life, paying down to exactly 0 (legal per CR 119.4; they then lose to a
  state-based action).
- `Costs.Sacrifice(filter)` — sacrifice a permanent matching the filter (may include self).
- `Costs.SacrificeAnother(filter)` — sacrifice a *different* permanent matching the filter.
- `Costs.SacrificeMultiple(count, filter = Any, distinctNames = false)` — sacrifice `count` matching permanents. With `distinctNames = true` the chosen permanents must all have **different names** ("sacrifice three artifact tokens with different names" — Transmutation Font); the cost is only payable when ≥ `count` distinctly-named candidates exist, and the activation always pauses for the selection (it's a real choice even when candidates == count).
- `Costs.SacrificeSelf` — sacrifice this permanent (the ability's source).
- `Costs.SacrificeGrantingPermanent` — sacrifice the permanent that *granted* this activated ability, resolved from the static-grant lookup at activation time (no filter, no prompt). The self-sacrifice sibling of `Costs.ExileGrantingPermanent`: use for an Equipment/Aura whose granted activated ability says "Sacrifice [this permanent]" — e.g. Deconstruction Hammer's "{3}, {T}, Sacrifice Deconstruction Hammer: ...". Per CR 201.5a the name refers only to the specific granting permanent, so this sacrifices exactly that one even with another same-named permanent on the battlefield.
- `Costs.DiscardCard` — discard a card you choose (any card).
- `Costs.Discard(filter, count = 1, atRandom = false)` — discard `count` cards matching the filter.
  When `atRandom` is true the engine picks the cards (no player selection); otherwise the player
  chooses which cards to discard.
- `Costs.DiscardAtRandom(count, filter)` — discard `count` cards chosen at random (Meteor Storm:
  "Discard two cards at random").
- `Costs.DiscardHand` — discard your entire hand.
- `Costs.DiscardSelf` — discard this card (cycling-style).
- `Costs.DiscardLastDrawnThisTurn` — discard the specific card you drew most recently this turn
  (Jandor's Ring: "{2}, {T}, Discard the last card you drew this turn: Draw a card."). The engine
  tracks the per-player most-recently-drawn entity on `GameState.lastCardDrawnThisTurnByPlayer`
  (updated at every `CardsDrawnEvent` emit site during a turn; the last id of a multi-card draw
  wins; cleared at every turn boundary) and discards it automatically — no player selection. The
  cost is unpayable when the controller has not drawn a card this turn or the tracked card has
  since left their hand (matches the Scryfall ruling: "If you do not have the card still in your
  hand, you can't pay the cost").
- `Costs.MillCard` / `Costs.Mill(count)` — mill a card / `count` cards as a cost ("{T}, Mill a card:
  Add {C}" — Deranged Assistant). No player selection: the milled cards are the top of the library.
  Per **CR 701.17b** a player *can't pay a cost that includes milling more cards than their library
  holds*, so — unlike the mill *effect*, which mills as many as possible — the cost is **unpayable**
  on a short library and gates legal-action enumeration (including mana-ability enumeration). A
  `ModifyMillAmount` replacement (Bruvac) still applies to the announced count when the cost is
  actually paid, and the library→graveyard moves go through `ZoneTransitionService`, so mill triggers
  fire exactly as they do for an effect's mill.
- `Costs.ExileTopOfLibrary(count)` — exile the top `count` cards of your library as a cost
  ("{R}, Exile the top ten cards of your library" — Arc-Slogger). The exile twin of `Costs.Mill`:
  no player selection (the cards are the top of the library), and per **CR 118.3** — a player can't
  pay a cost without the resources to pay it fully — the cost is **unpayable** on a short library and
  gates legal-action enumeration, rather than exiling as many as possible the way the exile *effect*
  would. Unlike mill, no `ModifyMillAmount` replacement applies: exiling from the top is not milling
  (CR 701.17a), so the announced count is the paid count. Distinct from the `ExileFromGraveyard`-style
  *chosen*-card costs, which mean "choose N", not "the top N".
- `Costs.ExileSelf` — exile this permanent (or graveyard card, for graveyard-activated abilities).
- `Costs.ReturnSelfToHand` — return this permanent to its owner's hand (Maze's End: "{3}, {T},
  Return this land to its owner's hand: …"). The bounce-to-hand sibling of `Costs.SacrificeSelf` /
  `Costs.ExileSelf`: deterministic, so there is no player selection and no `additionalCostInfo` is
  surfaced to the client — the engine pays it during activation, before the ability goes on the
  stack (CR 601.2h), and the ability still resolves with its source gone. Contrast
  `Costs.ReturnToHand(filter, count, youControl = true)`, the choose-a-permanent bounce cost, which
  deliberately excludes the source and is scoped to permanents you control unless `youControl` is
  turned off. Like the other self-removing costs it snapshots the source's
  counters first, so the resolving effect can still read them via
  `DynamicAmounts.lastKnownSourceCounters(...)`.
- `Costs.ExileFromGraveyard(count, filter)` — exile N matching cards from your graveyard.
- `Costs.ExileXFromGraveyard(filter)` — **variable-count** "exile X cards from your graveyard".
  X *is* the size of the graveyard selection, so activating raises a single `SelectCardsDecision`
  over the matching graveyard cards and the count the player picks becomes the ability's X — read it
  back with `DynamicAmount.XValue`. A `{X}` in the mana cost is therefore optional: **Winter, Cursed
  Rider** ("{2}{U}{B}, {T}, Exile X artifact cards from your graveyard: Each other nonartifact
  creature gets -X/-X") has none and the selection is free (0..matching cards); **Necropolis Fiend**
  ("{X}, {T}, Exile X cards from your graveyard") pays X in mana too, so the mana-X picker fixes the
  count first and the selection is pinned to exactly that many. Selecting nothing is legal and
  settles as X = 0.
- `Costs.ExilePermanentsFixed(count = 1, filter = Any)` — **fixed-count** "exile N permanents you
  control matching `filter`" activated-ability cost (City of Shadows: "{T}, Exile a creature you
  control:"). The counted sibling of the variable-count `Costs.ExilePermanents` below — reach for
  this whenever the card names a specific number and nothing downstream reads an X. Pass a
  controller-scoped filter (`.youControl()`): the battlefield zone map is keyed by **owner**, so the
  filter is what enforces "you control". Its selection is recorded, so a resolving effect can name
  the exiled cards via `CardSource.ExiledAsCost`.
- `Costs.ExilePermanents(filter = Any, minCount = 1, excludeSelf = true, xMeasure = TOTAL_MANA_VALUE, minMeasure = 0)`
  / `Costs.SacrificePermanents(filter = Any, minCount = 1, excludeSelf = false, xMeasure = COUNT, minMeasure = 0)`
  / `Costs.TapPermanentsVariable(filter = Creature, minCount = 1, excludeSelf = false, xMeasure = COUNT, minMeasure = 0)` —
  **variable-count** "exile/sacrifice/tap one or more permanents you control matching `filter`"
  activated-ability cost (CR 601.2b — the player chooses how many, at least `minCount`, as the
  ability is activated). One atom, `CostAtom.VariablePermanents`, with three orthogonal axes; the two
  facades are the named entry points to it. With `excludeSelf` the ability's own source is excluded
  ("one or more *other* …"); leave it false when the source may pay for itself.
  - **`action`** (set by which facade you call) — `EXILE` moves the permanents via the normal
    battlefield→exile transition; `SACRIFICE` puts them in their owners' graveyards through the same
    path a fixed-count sacrifice cost uses, so "whenever you sacrifice" triggers and Food tracking
    fire. Either way Auras fall off, tokens cease to exist, and leaves-the-battlefield triggers fire.
    `TAP` taps them in place, leaving them on the battlefield — the Teamwork N shape, reached through
    `Costs.additional.TapForTotalPower(n)` as a spell's additional cost and through
    `Costs.TapPermanentsVariable(...)` as an activated-ability cost (Mossbridge Troll: "Tap any number
    of untapped creatures you control other than this creature with total power 10 or greater:"). Only untapped permanents are candidates (CR 701.26a) and
    summoning sickness never applies (CR 302.6 is about the `{T}` symbol, not a tap paid as a cost).
  - **`xMeasure`** — how the choice is measured, both as the ability's **X** (read with
    `DynamicAmount.XValue`) and as the quantity a `minMeasure` floor is compared against.
    `TOTAL_MANA_VALUE` sums the chosen permanents' mana values, for "…with total mana value X" cards
    whose target is bounded by `GameObjectFilter.manaValueAtMostX()`; `COUNT` is simply how many were
    chosen, for "…for each permanent sacrificed this way"; `TOTAL_POWER` sums their **projected**
    power, for "…with total power N or more". Either value is fixed at activation and
    stored on the stack, so an X-bounded target is re-validated against it at resolution (CR 608.2b)
    and a resolution-time `XValue` read can't be changed by removal in response.
  - **`minMeasure`** — a floor on the *measure* rather than on the count (0 = none): "any number …
    with total power N or more". Pair it with `minCount = 0` for the free-count shapes; the engine
    marks the whole cost unpayable when every candidate together falls short (CR 601.2h).

  This atom is also a **spell additional cost**, not only an activated-ability cost: teamwork
  (CR 702.194a) rides it through `AdditionalCost.Atom`, paying from
  `AdditionalCostPayment.variableCostPermanents`.

  Backs **Fabrication Foundry** ("{2}{W}, {T}, Exile one or more other artifacts you control with
  total mana value X: Return target artifact card with mana value X or less from your graveyard to
  the battlefield") and **Radiant Lotus** ("{T}, Sacrifice one or more artifacts: Choose a color.
  Target player adds three mana of the chosen color for each artifact sacrificed this way" —
  `Costs.SacrificePermanents(Artifact, excludeSelf = false)` plus
  `AddManaOfChoice(amount = Multiply(XValue, 3), recipient = <the target>)`). The engine drives the
  activation in order: it pauses for the on-battlefield selection (min = `minCount`, max = all
  eligible), computes X, then pauses again for the ability's target — so an over-X target can never
  be chosen. Both pauses precede cost payment, so cancelling either is side-effect-free. Pair with
  `TimingRule.SorcerySpeed` where the card says "Activate only as a sorcery."
- `Costs.Forage()` (ability cost) / `Costs.additional.Forage` (additional cost) — Forage (CR
  701.59a): "exile three cards from your graveyard **or** sacrifice a Food." A *choice* between two
  sub-costs that belongs to the player. All cost-shaped forage payment is unified in the engine's
  `ForageCostResolver`: the enumerators surface the available modes as separate legal actions (the
  same multi-action pattern the "OrPay" costs use — `ExileFromGraveyard` and `SacrificePermanent`
  cost-info, so the client's existing pickers let the player choose the mode *and* which cards/Food),
  and payment honors that choice, only auto-paying a legal mode when none was supplied (AI /
  engine-direct). Used as an activated/mana-ability cost (Camellia, Thornvault Forager), a modal
  additional cost (Feed the Cycle), and the graveyard-cast permission (Osteomancer Adept, where the
  card being cast is excluded from the exile pool). For a "you may forage" *effect* (not a cost) use
  `Patterns.Mechanic.forage(afterEffect?)` instead. Every one of these paths emits the foraged event
  that fires `Triggers.WheneverYouForage` — the cost forms from `ForageCostResolver.pay`, the effect
  form from a marker inside each of its modes — so no context can forage without the payoffs seeing
  it. A forage that was declined, or one no mode was feasible for, emits nothing: forage has no
  "even if you can't" clause.
- `Costs.RevealNotedCreatureType` (ability cost) — "Reveal the creature type you chose" (MKM — A Killer Among Us). Publishes the secret creature type this permanent's controller noted with `Effects.SecretlyChooseCreatureType(...)` (§ effects) and hands it to the ability's own effect as `chosenValues["chosenCreatureType"]` — the key `CardPredicate.HasSubtypeFromVariable` reads, so "if target attacking creature token is the chosen type" is an ordinary `Conditions.TargetMatchesFilter(Filters.creature.withSubtypeFromVariable("chosenCreatureType"))` test rather than new vocabulary. Two rules make it more than a formality. **Only the player who made the note can pay it**: for anyone else the cost is unpayable, so a permanent whose control changed hands stops offering the ability at all (the card's own ruling; CR 702.106d's linkage). And the type is **captured at activation, not at resolution** (CR 113.7a) — the same cost usually sacrifices the source, so by the time the ability resolves the permanent and its note are gone. Activated-ability-only: a spell has no source permanent to carry a note, and every other cost context reports it unpayable rather than half-paying it.
- `Costs.Unattach` (ability cost) — "**Unattach this Equipment**" (RAV — Sunforger). Detaches the
  ability's source from the permanent it is attached to, without moving it between zones (CR 701.3d).
  The cost twin of `Effects.UnattachEquipment` (§ effects): the *effect* has existed since Stolen
  Uniform's rider, the *cost* had not, and it is not a lookalike of any other atom — a sacrifice moves
  zones, a tap can be restored, this does neither. Its affordability gate is the card's own ruling
  ("You can't pay the cost of unattaching Sunforger unless Sunforger is attached to a creature"), so
  the ability is offered as unaffordable while the Equipment sits loose. Payment runs through the same
  `ZoneMovementUtils.unattachEmittingEvent` chokepoint as the effect, so a `Triggers.becomesUnattached`
  trigger cannot tell the two apart. Activated-ability-only: a spell on the stack is attached to
  nothing, so every other cost context reports it unpayable rather than half-paying it. Sunforger is
  `Costs.Composite(Costs.Mana("{R}{W}"), Costs.Unattach)`.
- `Costs.CollectEvidence(n)` (ability cost) / `Costs.additional.CollectEvidence(n)` (mandatory
  additional cost) / `card { collectEvidence(n) }` (the optional **linked** cast cost) — Collect
  evidence N (CR 701.59a): "exile any number of cards from your graveyard with total mana value N or
  greater." Backed by one shared `CostAtom.CollectEvidence`, so the same payable thing serves an
  activated-ability cost (Cryptex, Forensic Researcher, Polygraph Orb), a cast-time additional cost
  (Extract a Confession, Vitu-Ghazi Inspector), and a `PayCost`. All of them route through the
  engine's `CollectEvidenceResolver` — one reachability gate, one legality rule, one exile, one
  `EvidenceCollectedEvent`.

  **The threshold is a floor on total mana value, not a card count.** Exiling *more* than N is legal,
  and mana-value-0 cards (lands) are legal selections contributing nothing — so "enough cards" never
  implies "enough evidence". The picker is therefore a variable-size selection with a sum gate:
  `AdditionalCostData.exileMinTotalWeight` (with the per-card `exileCardWeights` the client sums and
  the `exileWeightUnit` it labels the tally with — the same payload the filtered
  `ExileFromGraveyardForTotal` uses, so there is one sum-gated picker rather than one per cost) and
  `SelectCardsDecision.minTotalManaValue` (the mirror of the existing `maxTotalManaValue` cap) carry
  the floor, and the client shows a running total and keeps Confirm disabled until it is met.

  Per **CR 701.59b** a player who cannot reach N *can't choose to collect evidence*: every
  affordability check fails closed on the summed mana value, so the option is never payable and an
  under-total submission is rejected rather than trimmed. For collect evidence as an *effect* rather
  than a cost, use `Effects.CollectEvidence(n)` (§ effects).

  `Costs.additional.CollectEvidenceForTargetsTotalManaValue` is the one shape whose threshold isn't
  printed — **Urgent Necropsy**'s "collect evidence X, where X is the total mana value of the
  permanents this spell targets". `CostAtom.CollectEvidence.amount` is a `DynamicAmount` for it, and
  accepts exactly the three shapes a cost can be priced from before it is paid: a literal, the
  cast's `XValue`, and `ContextPropertyKey.TARGETS_TOTAL_MANA_VALUE` (an `init` guard rejects the
  rest, so a cost can never carry an amount the cost-time evaluator has no context to read). This is
  the opposite call from `Effects.CollectEvidenceChosenAmount` (§ effects), which stayed a separate
  effect precisely because a player-*chosen* X isn't a `DynamicAmount` at all — a derived one is.

  Two consequences worth knowing, both from the card's own rulings. **X is locked in after targets,
  before payment** (CR 601.2c → 601.2f → 601.2h): the engine prices it from `CastSpell.targets`, so
  it counts what the caster actually chose, not what they could have. And a graveyard that can't
  reach it makes the cast **illegal, not cheaper** (CR 601.2e) — the reachability gate has nowhere to
  fail closed at enumeration time, since the price doesn't exist yet, so the check moves to cast-time
  validation. Client-side that is why the evidence picker runs **after** the targeting step for this
  cost: the enumerator ships `AdditionalCostData.exileWeightPerTarget` (what each legal target would
  add), whose presence is both the per-target price list and the instruction to defer — the same
  deferral `manaCostPerExtraTarget` already does for mana-source selection. With no targets chosen X
  is 0, which collects evidence 0: legal, exiles nothing, and still counts as having collected
  evidence per the 2024-02-02 ruling.

  `Costs.CollectEvidence(n, linkToSource = true)` tethers the cards this payment exiles to the
  *source permanent's* `LinkedExileComponent`, so a later ability on that same permanent can name
  them — "cards exiled **with it**". That is the only thing the flag does: it grants no permission
  and changes no legality, it just leaves a handle for `CardSource.FromLinkedExile()` to gather.
  **Kylox's Voltstrider** ("Collect evidence 6: This Vehicle becomes an artifact creature until end
  of turn" + "Whenever this Vehicle attacks, you may cast an instant or sorcery spell from among
  cards exiled with it") is the whole reason it exists. Off by default, because an ordinary
  collection exiles the cards and forgets them, and an unread pile is state the client would
  otherwise tether to the permanent for no reason. The pile is cumulative across activations and
  prunes itself: a card leaving exile is dropped from every linked-exile pile
  (`ZoneMovementUtils.unlinkFromAllLinkedExiles`), so a spell already cast off the pile is gone from
  it without the card doing any bookkeeping.

  It also serves as the non-mana half of an **alternative** casting cost — Conspiracy Unraveler's
  "You may collect evidence 10 rather than pay the mana cost for spells you cast", i.e.
  `GrantAlternativeCastingCost("{0}", listOf(Costs.additional.CollectEvidence(10)))` (§ casting
  permissions). That path stamps no `ChoiceSlot`, so unlike the linked `card { collectEvidence(n) }`
  form it does **not** make `Conditions.WasEvidenceCollected` read true on the spell being cast.
- `Costs.ExileFromGraveyardForTotal(minTotal, measure, filter = Any)` /
  `Costs.ExileFromGraveyardForColoredSymbols(minSymbols, vararg colors)` — the **unnamed, filtered
  generalization of collect evidence**: "exile any number of `<filter>` cards from your graveyard
  whose summed `<measure>` is `minTotal` or more". Backed by `CostAtom.ExileFromGraveyardForTotal`
  and by the *same* engine implementation collect evidence uses — `GraveyardTotalExileResolver`,
  which `CollectEvidenceResolver` now delegates to, so the two can never drift apart on
  reachability, legality, auto-selection or the exile itself.

  Two axes distinguish it from `Costs.CollectEvidence(n)`, which is otherwise the identical mechanic:
  the **filter** (collect evidence spends *any* graveyard card, CR 701.59a; here non-matching cards
  are never offered), and the **measure** — the per-card quantity that is summed, a `CardMeasure`:
  - `CardMeasure.ManaValue` — mana value (CR 202.3); what collect evidence uses;
  - `CardMeasure.ColoredManaSymbols(colors)` — how many mana symbols of those colours appear in the
    card's **printed** mana cost, counted by `ManaCost.coloredSymbolCount` — the single counting rule
    also behind `CardPredicate.ColoredManaSymbolsAtLeast` and
    `EntityNumericProperty.ColoredManaSymbolCount`, so a group total and a per-card read can never
    disagree (hybrid/Phyrexian pips count for their colour(s), CR 107.4e/f; generic, `{C}` and `{X}`
    count for none).

  `ExileFromGraveyardForColoredSymbols(15, Color.BLACK)` is **Baron Helmut Zemo**'s boast cost,
  "exile any number of black cards from your graveyard with fifteen or more black mana symbols among
  their mana costs" — it derives the colour filter and the pip measure from one list of colours so
  they can't drift. The colour filter and the pip count are *not* redundant: colour is a
  characteristic, the count reads printed pips, so the filter is what keeps a black card with no
  black pip on the right side of the printed wording.

  Same three consequences as collect evidence, for the same reason: **the threshold is a floor on the
  measure, never on the card count** (overpaying is legal, and a matching card whose measure is 0 is
  a legal selection contributing nothing), and the cost **fails closed** — a graveyard that can't
  reach the floor makes the ability not offered at all rather than offered and refused. Measures read
  the **base** card (mana value and printed cost are intrinsic, and a graveyard card has no
  battlefield projection); the *filter* evaluates against projected state like every other cost
  filter.

  Client-side it *is* the collect-evidence picker — one branch, not a parallel one. Both costs ship
  `AdditionalCostData.exileMinTotalWeight` + `exileCardWeights` + `exileWeightUnit` (the unit label
  comes from `CardMeasure.unitLabel`, so the measure names itself and the client never has to know
  which cost it is looking at); only `costType` differs. The weights are server-computed for both,
  because a pip total is a reading of the printed cost the client can't do — and sending mana values
  it *could* have computed is what buys the single code path. The server re-validates the submitted
  selection regardless — a submitted selection that doesn't pay is **rejected**, never silently
  replaced with the engine's own pick.

  Activated-ability cost only today: it is deliberately reported unpayable as a spell's additional
  cost and as a `PayCost`, since no printed card wants either and an offered-then-unpayable cost is
  worse than an absent one.
- `Costs.Craft(filter, minCount = 1, maxCount = null)` — Craft material cost (CR 702.167a): exile
  this permanent **and** exile at least `minCount` (and, when `maxCount` is set, at most `maxCount`)
  cards matching `filter` selected from the combined pool of
  permanents you control and cards in your graveyard. Exact-count crafts ("Craft with artifact" =
  exactly one, "Craft with two creatures" = exactly two) set `maxCount == minCount`; "... or more"
  wordings leave `maxCount = null`. Atomic because CR 702.167a pairs the
  self-exile with the materials-exile in one clause. Records the chosen materials on the source's
  `CraftedFromExiledComponent` so the back face's CDA can read them after the source returns
  transformed. Always combined with `Mana(...)` and used with the
  `Effects.ReturnSelfFromExileTransformed` resolution effect (the `card { craft(filter, cost) }`
  helper wires the whole pattern).
  - **Heterogeneous per-slot craft** — `card { craft(slots = listOf(f1, f2, ...), cost, materialDescription?) }`
    for crafts that name one material of *each* of several kinds ("Craft with a Dinosaur, a Merfolk, a
    Pirate, and a Vampire" — Throne of the Grim Captain). Each slot is filled by exactly **one distinct**
    material, so validating a chosen set is a bipartite perfect-matching problem, not a per-subtype count
    (a single Merfolk Pirate fills only one slot; four Vampires cannot cover four different subtypes). The
    built `AbilityCost.Craft` carries the per-slot filters in `slots` plus a union `filter` (`anyOf` of the
    slots) with `minCount == maxCount == slots.size`, so the flat BF+GY candidate gathering, `canPay`, the
    legal-action enumerator, and the client material overlay work unchanged; the engine layers the
    matching check (`CraftSlotMatching`, Kuhn's augmenting-path — same routine as `BlockPhaseManager`) on
    top in `canPay`, enumeration, and payment. The legal action still ships one flat material list
    (min = max = slot count); an illegal set that can't fill every slot is rejected at payment time
    (no per-slot selection UI).
- `Costs.PutCounterOnSelf(counterType, count = 1)` — "Put a [kind] counter on this permanent" as
  part of the activation cost (Mazemind Tome: "{T}, Put a page counter on this artifact: Scry 1").
  The *accruing* mirror of `Costs.RemoveCounterFromSelf`, and the only cost that adds something
  rather than spending it: it is **always payable**, which is exactly what lets Mazemind Tome reach
  the fourth page counter that exiles it. Paid at activation (so the counter lands before the
  ability resolves, and stays even if the ability is countered), and routed through the normal
  counter-placement chokepoint — the "can't have counters put on it" gate and the placement
  replacements (Hardened Scales, Doubling Season) all apply. Activated-ability scoped: there is no
  additional-cost or `PayCost` form, since a spell on the stack has no permanent to accrue them on.
- `Costs.TapGrantingPermanent` — tap the permanent whose static ability *granted* this activated
  ability, the third member of the granter-cost family alongside `Costs.ExileGrantingPermanent` and
  `Costs.SacrificeGrantingPermanent`. Use for an Equipment/Aura whose granted ability names the
  Equipment itself: Fishing Pole's "Equipped creature has '{1}, {T}, **Tap Fishing Pole**: …'",
  where `Costs.Tap` taps the *host creature* and this taps the *Equipment* — compose both in a
  `Costs.Composite`. Per CR 201.5a the name refers only to the granting permanent, so an
  already-tapped (or departed) granter makes the ability unactivatable even with another same-named
  Equipment untapped elsewhere; the enumerator and `ActivateAbilityHandler` both gate on it.
- `Costs.Composite(c1, c2, ...)` — multiple costs paid together.
- `Costs.RemoveCounters(count = 1, counterType = null, filter = Any)` — remove `count` counters
  from among permanents matching `filter` you control. When `counterType` is set (e.g. `"+1/+1"`),
  only counters of that type are removed; when `null`, counters of any type may be removed in any
  combination (Tayam, Luminous Enigma).
- `Costs.RemoveXCounters(counterType = "+1/+1", filter = Permanent, self = false)` — remove X
  counters, where X is the activated ability's chosen variable-cost value. Use
  `Costs.RemoveXCounters()` (the default) to remove X counters of any type. By default the removal
  is spread across permanents matching `filter` — the player is asked to distribute it, the
  Retribution of the Ancients shape. Pass **`self = true`** for "remove any number of counters from
  ~" (The Astonishing Ant-Man), where the counters come off the ability's own source: that takes
  the direct payment path and caps X by the source's own counters. The filter-based form is not
  merely imprecise for a self-scoped cost, it is *unpayable* — nothing is ever distributed, so
  payment fails with a total of 0. Don't reach for `GameObjectFilter.Permanent.sourceItself()`.

**Spell-level alternatives**

- `selfAlternativeCost` — generic "cast instead for" alt-cost. Optional `condition` gates whether
  the alternative is available at all — the "…rather than pay this spell's mana cost **if**
  <condition>" clause (Blasphemous Edict: `condition = Conditions.CompareAmounts(
  DynamicAmount.AggregateBattlefield(Player.Each, GameObjectFilter.Creature), GTE,
  DynamicAmount.Fixed(13))`). Evaluated with no target/trigger context at two mirrored sites —
  `CastSpellEnumerator` (so the action isn't offered) and `CastSpellHandler.validate` (so an
  authorization can't outlive the enumeration that offered it). Omit for an unconditional
  alternative (Zahid, Djinn of the Lamp).
- `evoke` — pay evoke cost; creature is sacrificed at ETB.
- `morph` — cast face-down for `{3}`-ish.
- `disguise` — cast face-down for `{3}` as a 2/2 with ward {2} (CR 702.168a); same sorcery-speed
  timing and the same `MorphCastEnumerator` as morph.
- `warp` — cast from anywhere; exiled at end of turn.
- `dash` — cast from hand for the dash cost (CR 702.109); gains haste, returned to owner's hand at
  the beginning of the next end step (not exiled — unlike warp, dash has no later recast).
- `conditionalFlash` — flash while condition holds.
- `cantBeCountered` — spell is uncounterable.
- `cantBeCopied` — spell can't be copied (CR 707.10).
- `xManaRestriction = setOf(Color.BLACK, Color.RED)` — "spend only [colors] on X." Restricts which
  mana may pay the `{X}` portion of the cost (the fixed colored/generic portion is unaffected).
  Available in both `spell { }` and `activatedAbility { }` blocks; honored by the mana solver and the
  payment path. Per-color amount spent on X is then readable via `DynamicAmount.ManaSpentOnX(color)`.
  Soul Burn (`spell { xManaRestriction = setOf(Color.BLACK, Color.RED) }`) and Atalya, Samite Master
  (`activatedAbility { xManaRestriction = setOf(Color.WHITE) }`) are the first users.

**`Costs.additional.*`** (wraps `AdditionalCost`) — extra costs paid alongside the mana cost. Card
definitions construct these through the facade, e.g. `Costs.additional.SacrificePermanent(Filters.Creature)`.

- `Costs.additional.ReturnToHand(filter = Filters.Any, count = 1, youControl = true)` — "as an additional cost to cast
  this spell, return [count] permanent(s) you control to its owner's hand" (Fear of Isolation). Paid
  as the spell is cast (CR 601.2f) via `additionalCostPayment.bouncedPermanents`; the enumerator
  surfaces the returnable permanents (a `costType = "ReturnToHand"` cost) and the client picks them
  on the battlefield. The bounce goes through `ZoneTransitionService.moveToZone(…, Zone.HAND)`, so
  attached Auras fall off and tokens cease to exist. Mirrors the sacrifice/tap additional-cost path.
- `Costs.additional.TapForTotalPower(totalPower, filter = GameObjectFilter.Creature)` — "tap any number of
  creatures you control with total power N or more" (Teamwork N, CR 702.194a). A
  `CostAtom.VariablePermanents` with `action = TAP`, `xMeasure = TOTAL_POWER`, `minMeasure = N` and
  `minCount = 0`: the count is free, the power floor is the constraint, and the sum is read from
  **projected** state. The engine advertises the candidates as a `costType = "TapForTotalPower"` cost
  carrying `tapForPowerCreatures` / `tapForPowerRequired` (the same payload crew and saddle use) and
  the client returns the picks in `additionalCostPayment.variableCostPermanents`. Reach for this
  through the `teamwork(n)` DSL helper rather than by hand.
- `Costs.additional.BlightVariable` — "as you cast, you may pay X life" (Blight X); X exposed via
  `DynamicAmount.AdditionalCostBlightAmount`.
- `Costs.additional.PayXLife(minCount = 0)` — "as an additional cost to cast this spell, pay X life."
  The caster declares X at cast time (capped at their current life total) and X is fed to the spell's
  effects through the resolution **X value** — i.e. read it with `DynamicAmount.XValue` and filter with
  `CardPredicate.ManaValueAtMostX` / `manaValueAtMostX()` (Vicious Rivalry: "pay X life; destroy all
  artifacts and creatures with mana value X or less"). A card using this cost must **not** also have an
  `{X}` in its mana cost — both write the same X slot. The client shows a numeric X picker (no target
  step); the AI declares X = 0 by default.
- `Costs.additional.PayLifePerTarget(amountPerTarget)` — "this spell costs N life more to cast for
  each target." Pair with an unbounded `TargetCreature(unlimited = true)` etc.; the engine
  auto-pays `amountPerTarget × action.targets.size` at cast resolution (Phyrexian Purge).
- `Costs.additional.PayLifeEqualToManaValueOfSpell` — auto-pays life equal to the cast spell's own
  mana value. The substitute cost for "pay life equal to its mana value rather than pay its mana cost"
  (Valgavoth, Terror Eater; Bolas's Citadel-style effects). Pair it with a play-from-exile grant whose
  mana cost is waived — `GrantMayCastFromLinkedExile(withoutPayingManaCost = true, additionalCost =
  Costs.additional.PayLifeEqualToManaValueOfSpell)` — so the only cost paid is the life. The amount is
  read from the cast card's mana value, checked at cast time (CR 119.4 — must have at least that much life).
- `Costs.additional.OrPay(cost, alternativeManaCost)` — **cost-vs-mana**: "as an additional cost
  to cast this spell, \<pay this cost\> **or** pay {mana}". One `AdditionalCost.OrPay` covers the
  whole "do X or pay {N}" family, parameterized by the `AdditionalCost` on the non-mana leg; the
  named shapes below are its printed wordings and are one-line facades over it. The enumerator
  offers up to two cast paths: the **leg path** (base cost + the leg cost's ordinary selection
  prompt — the same `costType` that cost emits standing alone, so no new client UI) and the **pay
  path** (base cost + `alternativeManaCost` folded in). The leg path is offered only when the board
  affords the leg's selection, so with nothing to pay it only the pay path is castable — but the
  cost as a whole is always payable. Which leg was taken is recovered at payment time from **which
  `additionalCostPayment` field the client populated**, so `cost` must be a selection-carrying cost
  — a `Behold`, or an atom cost over `Sacrifice` / `Discard` / `ExileFrom` / `TapPermanents` /
  `ReturnToHand` / `RevealFromHand` — and must not share its field with another additional cost on
  the same card.
  `CastSpellHandler.reduceCostAlternatives` then rewrites the whole cost to that plain leg cost (leg
  paid) or drops it (pay path), so validation, payment, LKI snapshots, behold's pipeline storage and
  discard tracking (CR 701.8) reuse the leg's own paths verbatim. `BlightOrPay` stays a separate
  type only because there is no standalone blight `AdditionalCost` for `OrPay` to wrap.
- `Costs.additional.BeholdOrPay(filter = Filters.Any, alternativeManaCost, storeAs = "beheld")` —
  "behold a [filter] or pay {mana}" (Lys Alana Dignitary). `OrPay(AdditionalCost.Behold(filter,
  storeAs = storeAs), …)`; the behold path surfaces as a `costType = "Behold"` cost over one
  candidate pool spanning battlefield *and* hand, and stores the chosen cards under `storeAs` for
  downstream costs/effects exactly as a plain `Behold` does.
- `Costs.additional.RevealFromHand(filter = Filters.Any, count = 1)` — "as an additional cost to
  cast this spell, reveal a [filter] card from your hand". `Atom(CostAtom.RevealFromHand(filter,
  count))`; surfaces as a `costType = "RevealCard"` cost over `validRevealTargets` (the caster's
  hand, minus the spell being cast) and the picks come back as
  `additionalCostPayment.revealedCards`. **Paying moves nothing** — CR 701.20b: revealing a card
  doesn't cause it to leave the zone it's in — so the revealed card is still in hand and still
  castable afterwards; the payment is a `CardsRevealedEvent` and nothing else. As a *mandatory*
  cost it fails closed: with no matching card in hand the spell isn't castable at all.
- `Costs.additional.RevealFromHandOrPay(filter = Filters.Any, alternativeManaCost, count = 1)` —
  "reveal a [filter] card from your hand or pay {mana}" (Lorwyn's tribal cycle: Wren's Run
  Vanquisher, Silvergill Adept, Goldmeadow Stalwart, Squeaking Pie Sneak, Flamekin Bladewhirl).
  `OrPay(RevealFromHand(filter, count), …)`. **Not `BeholdOrPay`**: CR 701.4a defines "behold a
  [quality]" as "reveal a [quality] card from your hand **or** choose a [quality] permanent you
  control", so behold's candidate pool also spans the battlefield and would wrongly let a permanent
  pay a hand-only reveal. That width is also why the reveal has its own `revealedCards` payment
  field rather than sharing `beheldCards` — on an `OrPay` leg the populated field is the only thing
  telling the engine which leg the caster took.
- `Costs.additional.ExileFromGraveyardOrPay(exileCount, alternativeManaCost, filter = Filters.Any)`
  — "exile N cards from your graveyard or pay {mana}" (Soaring Stoneglider: "exile two cards from
  your graveyard or pay {1}{W}"). `OrPay(Atom(CostAtom.ExileFrom(GRAVEYARD, filter, exileCount)), …)`;
  the exile path surfaces as a `costType = "ExileFromGraveyard"` cost and is offered only when the
  graveyard holds at least `exileCount` matching cards. That `costType` pins the client's picker to
  the graveyard, so an `ExileFrom` leg on any other zone is declined (pay path only).
- `Costs.additional.SacrificeOrPay(filter = Filters.Any, alternativeManaCost, count = 1)` —
  "sacrifice a [filter] or pay {mana}" (Louisoix's Sacrifice: "sacrifice a legendary creature or pay
  {2}"). `OrPay(Atom(CostAtom.Sacrifice(filter, count)), …)`; the sacrifice path surfaces as a
  `costType = "SacrificePermanent"` cost (the on-battlefield picker Natural Order uses) and is
  offered only when you control at least `count` matching permanents.
- `Costs.additional.DiscardOrPay(alternativeManaCost, filter = Filters.Any, count = 1)` — "discard a
  [filter] or pay {mana}" (Pumpkin Bombardment: "discard a card or pay {2}").
  `OrPay(Atom(CostAtom.Discard(count, filter)), …)`; the discard path surfaces as a `costType =
  "DiscardCard"` cost (the hand picker Force of Will uses), excludes the spell being cast, and is
  offered only when you hold at least `count` other matching cards. The discard-as-cost still feeds
  the turn's discard tracking (CR 701.8), so it counts toward
  `DynamicAmounts.cardsDiscardedThisTurn()` / `Conditions.YouDiscardedACardThisTurn` /
  `Conditions.YouDiscardedThisCardThisTurn` (Mayhem).
- `Costs.additional.Choice(vararg options)` — **cost-vs-cost**: "as an additional cost to cast this
  spell, pay exactly one of `options`" (Souls of the Lost: *"discard a card **or** sacrifice a
  permanent"*). The general, parameterized form of `Forage` — each option is itself an
  `AdditionalCost` (compose the `Sacrifice` / `Discard` / `ExileFrom` atoms). Distinct from the
  `OrPay` family (`OrPay` and its `SacrificeOrPay` / `DiscardOrPay` / `ExileFromGraveyardOrPay` /
  `BeholdOrPay` wordings, plus `BlightOrPay`): those
  fold a **mana** alternative into the spell's cost, whereas `Choice` is for options that are each
  independently payable **non-mana** costs (no mana-cost change). The enumerator emits **one cast
  action per payable option** (`CastSpellEnumerator.expandChoiceAdditionalCosts` +
  `ChoiceCostResolver`), each carrying that option's existing picker (`SacrificePermanent` /
  `DiscardCard` / `ExileFromGraveyard`) — so the caster picks the sub-cost by choosing which action to
  play, with **no new client UI**. The plain (un-expanded) base action is dropped, since a mandatory
  choice cost can't be skipped. At payment time `CastSpellHandler.reduceCostAlternatives` collapses the
  `Choice` to the single option the caller populated (or, for a server-initiated free/AI cast with no
  payment, the first payable option — mirroring `ForageCostResolver`'s engine-direct fallback), so
  validation, application, and the free-cast selection pause all handle it as a plain atom. Keep the
  options on **distinct** payment fields (sacrifice vs. discard vs. exile) — two options consuming the
  same field can't be told apart by the payment alone.
- `Costs.additional.RemoveCounters(count, counterType = null, filter = Any)` — "as an additional
  cost to cast this spell, remove `count` counters from among permanents matching `filter` you
  control." When `counterType` is set (e.g. `"+1/+1"`), only counters of that type are removed;
  when `null`, counters of any type may be removed in any combination (Eladamri, Korève Domain).

**`Costs.pay.*`** (wraps `PayCost`) — payable costs used by [`PayOrSufferEffect`](#15-replacement-effects) ("do X
unless you Y") and by `morphCost` (non-mana face-up cost). Distinct from `AbilityCost` / `Costs.*`
which model an ability's activation cost; `PayCost` models a single cost the engine prompts the
player to pay against an alternative consequence.

**`PayOrSufferEffect` prompts unconditionally** — it does not check whether its `suffer` effect would
actually do anything. A branch whose suffer reads a collection that may be empty must therefore be
wrapped in a `ConditionalOnCollectionEffect`, or the player is asked to pay for a consequence that
would be a no-op (Wand of Ith splits the revealed card into a land pile and a nonland pile and gates
each ransom on its own pile). The resolving pipeline's collections *are* carried across the
pay-or-decline pause, so a suffer effect can name them on either answer.

`PayOrSufferEffect(cost, suffer, player = EffectTarget.Controller)` defaults to charging the ability's
controller, but **`player` may route the decision *and* the payment to any other player** — most
usefully `EffectTarget.PlayerRef(Player.TriggeringPlayer)` on a death trigger, which resolves to the
dying permanent's last-known controller. The `suffer` consequence still resolves under the **ability's
controller** (not the payer), so `EffectTarget.Controller` inside it means *you*: Meathook Massacre II's
"whenever a creature an opponent controls dies, *they* may pay 3 life. If they don't, return that card
under *your* control" is `PayOrSufferEffect(player = PlayerRef(TriggeringPlayer), cost = Costs.pay.PayLife(3),
suffer = Composite(Move(TriggeringEntity → battlefield, controllerOverride = Controller), AddCounters(finality)))`.

**`consequenceDescription`** is the prompt's words for what happens if the cost isn't paid ("Pay {2}
or **…**?"), and every `Costs.pay` variant's prompt renders it. Null generates the clause from
`suffer`, which is right while the payer is the ability's controller. It stops being right the moment
`player` routes the question elsewhere: an effect description is an imperative fragment addressed to
*the controller*, so `GainControlEffect`'s "gain control of target" asked of an opponent offers them
the theft they are the subject of — Scarwood Bandits asked its victim "Pay {2} or gain control of
target for as long as this creature remains on the battlefield?". The unresolved `target` and `this
creature` are the same fragment's other half: placeholders that read as the card's text rather than
as this game's board. **Write it out whenever `player` isn't the controller**, or whenever the
generated clause would name a placeholder the player can't resolve. (Same defect and same remedy as
an optional trigger's `description` becoming its "may" prompt, above.)

Non-mana `morphCost` payment is routed through the shared engine `CostPaymentService`, so **every
`Costs.pay` variant below works as a morph cost** (including `Tap` / `Choice` / `OwnManaCost`): turning
the creature face up pauses for the cost-specific decision and only flips once the cost is paid.
(Mana morph costs keep their own up-front payment — explicit mana-source selection, X, auto-tap
preview — in the turn-face-up handler.)

- `Costs.pay.Atom(CostAtom)` — the generic lift of any shared payable thing into this context, the
  mirror of `AbilityCost.Atom` / `AdditionalCost.Atom`. Reach for a named factory below where one
  exists; this is what a caller holding a `CostAtom` it did not build itself needs, and it is what
  makes the variable-count costs above payable ("…sacrifice this creature unless you sacrifice any
  number of creatures with total power 12 or greater" — Phyrexian Dreadnought).
- `Costs.pay.Mana(ManaCost)` — pay mana (auto-taps lands via the solver). "...unless you pay {U}{U}"
  (Vaporous Djinn).
- `Costs.pay.OwnManaCost` — pay the mana cost of the permanent the cost applies to (its *own* mana
  cost, read from `CardComponent.manaCost` at payment time). Use for granted abilities like
  Essence Leak ("...sacrifice this permanent unless you pay its mana cost"), where the affected
  permanent — not a fixed cost — owns the mana cost. The engine resolves it into a concrete
  `Costs.pay.Mana` against that permanent before prompting.
- `AbilityCost.AttachedPermanentManaCost` — pay the mana cost of the permanent this Aura/Equipment is
  **attached to**: Merseine (FEM), "Pay enchanted creature's mana cost: Remove a net counter from this
  Aura." The activated-ability sibling of `Costs.pay.OwnManaCost`, and lowered the same way — into a plain
  mana `Atom` against the attached permanent's printed cost before anything prices or pays it, so every
  downstream path (affordability, enumeration, the mana solver, the prompt) sees a uniform shape. An
  unattached source, or one attached to a permanent with no mana cost, prices as {0}.
- `Costs.pay.PayLife(amount)` — pay N life; offered only when the player's life total is at least N
- `Costs.pay.PayDynamicLife(amount: DynamicAmount)` — "pay life equal to **&lt;rule&gt;**", where the
  card names a rule rather than a number (**Wand of Ith**: "…unless they pay life equal to its mana
  value"). Lowered to a concrete `PayLife` inside `PayOrSufferExecutor`, the one place holding the
  `EffectContext` the amount may need — a pipeline-scoped amount such as `ManaValueSumOfCollection`
  is unreadable anywhere else. Consequently it is **PayOrSuffer-only**: used as a spell or ability
  cost it reports unaffordable, because affordability there has to be known before any context
  exists. Same idea as `PayCost.OwnManaCost`, which is likewise resolved at payment time.
  (CR 119.4). "...unless you pay 3 life."
- `Costs.pay.Discard(filter = Any, count = 1, random = false)` — discard cards matching `filter`.
  Random variant prompts a yes/no and the engine picks the discards (Pillaging Horde).
- `Costs.pay.DiscardHand` — discard your **entire** hand. Nothing is selected (every card goes), so
  it prompts a yes/no, and it is **always affordable**: an empty hand discards nothing, and a cost
  of nothing is a cost you can pay (CR 118.3). "Counter target spell unless its controller discards
  their hand" (Perplex) is `PayOrSufferEffect(cost = Costs.pay.DiscardHand, suffer =
  Effects.CounterSpell(), player = EffectTarget.TargetController)`. The shared-vocabulary twin of
  `Costs.DiscardHand`, the activated-ability spelling of the same payable thing.
- `Costs.pay.Sacrifice(filter = Any, count = 1)` — sacrifice permanents you control matching
  `filter`. **The source is included when it matches** — "sacrifice it unless you sacrifice an
  artifact" on an artifact creature may name that creature. "...unless you sacrifice three Forests"
  (Primeval Force).
- `Costs.pay.SacrificeAnother(filter = Any, count = 1)` — the printed-"another" variant; same cost
  with the source excluded.
- `Costs.pay.Exile(filter = Any, zone = HAND, count = 1)` — exile cards from `zone` matching
  `filter`. "...unless you exile a blue card from your hand."
- `Costs.pay.Tap(filter = Any, count = 1)` — tap untapped permanents you control matching `filter`.
  **The source is included when it matches and is untapped** — Public Thoroughfare's and Command
  Bridge's rulings both allow tapping the land itself when something untapped it in response.
  Tapping each emits a `TappedEvent` so "becomes tapped" triggers fire.
  "...unless you tap an untapped permanent you control" (Command Bridge).
- `Costs.pay.TapAnother(filter = Any, count = 1)` — the printed-"another" variant; same cost with
  the source excluded.
- `Costs.pay.Atom(CostAtom.Mill(count))` — mill from the top of your own library. No named factory:
  it arrives through `Costs.pay.Atom`. "...sacrifice this creature unless you mill two cards"
  (Deep Spawn, the only printed instance). Milling from the top selects nothing, so the prompt is a
  plain yes/no. **CR 701.17b** — a player can't pay a cost that mills more cards than their library
  holds, so a library shallower than `count` makes this unpayable and the `suffer` half happens
  with no prompt at all. Mill *replacement* effects apply when the payment is made, not to the
  announced count.

The word **"another"** is the only thing that decides self-exclusion, and it lives on the cost atom
(`excludeSelf`). Every path that asks "which objects could pay this?" reads that flag and nothing
else — `PayOrSufferExecutor`, `AnyPlayerMayPayExecutor` (and the continuation that asks the next
player), `CostHandler`, both enumerators, and `CostPaymentService.selectionCandidates`, the single
candidate-domain helper behind the last one's affordability *and* prompt. None of them applies a
blanket exclusion: a self-inclusive cost stays payable on a board holding only the source, and a
self-exclusive one is never offered the source in the first place.
- `Costs.pay.Choice(options)` — present several `PayCost`s; player picks one (or the suffer effect).
  Unaffordable options are hidden. "...unless they sacrifice a nonland permanent or discard a card."
- `Costs.pay.ReturnToHand(filter, count = 1, youControl = true)` — return permanents to their
  owner's hand. Wired into `PayOrSufferEffect` (Drake Familiar: "sacrifice it unless you return an
  enchantment to its owner's hand") as well as `morphCost`. Set `youControl = false` for the cards
  whose ruling is control-agnostic — Drake Familiar's says *any* enchantment on the battlefield
  qualifies, an opponent's included, and that an untargetable one does too because the ability never
  targets. Selecting nothing is a decline, so the suffer half runs; with no legal permanent at all
  there is no prompt. The source is always out of the pool.
- `Costs.pay.RevealCard(filter, count = 1)` — reveal a card from hand matching `filter`. Currently
  only consumed by `morphCost`; not yet wired into `PayOrSufferEffect`.
- `Costs.pay.RemoveCounters(count, counterType = null, filter = Any)` — remove `count` counters
  from among permanents matching `filter` you control. When `counterType` is set (e.g. `"+1/+1"`),
  only counters of that type are removed; when `null`, counters of any type may be removed in any
  combination.
- `Costs.pay.PutCountersOnPermanent(counterType, count = 1, filter = Permanent)` — put counters on a
  permanent **the payer controls**; the selected-permanent sibling of `PutCountersOnSelf`. Unlike that
  one — always payable, because it needs no selection — this is **unpayable when the payer controls no
  matching permanent**, which is the whole point of the punisher clause it models: Tourach's Chant (FEM)
  deals 3 damage to a player "unless the player puts a -1/-1 counter on a creature they control", and a
  player with an empty board simply takes the damage. The payer picks which of their permanents takes it.
- `Costs.ExileFromSingleGraveyard(count, filter)` is the "from a single graveyard" wording. The two
  flags it sets live on the underlying `CostAtom.ExileFrom`, **not** on `Costs.pay.Exile` — that
  facade is `(filter, zone = HAND, count)` and offers no way to reach them, so a `PayOrSuffer` cost
  cannot express this shape today. `anyPlayersZone = true` widens the pool from the payer's own zone to **every** player's (each
  card leaves from, and is exiled by, its own owner's zone; the payer only chooses). `singleZone = true`
  then adds the other half: all `count` cards must come out of **one** player's zone. Both together are
  Night Soil (FEM), "{1}, Exile two creature cards from a single graveyard" — so a board with one
  creature card in each of two graveyards pays nothing, and `CostHandler` rejects a payment whose cards
  span owners. A graveyard holding fewer than `count` matches is not offered at all.

---

## 4. Effects (`Effects.*`)

Atomic effect factories. For library/zone manipulation, prefer the pipelines in §5.

### Damage

- `DealDamage(amount, target)` — deal fixed/dynamic damage.
- `DealDamageExcessToController(amount, target)` — deal damage to a creature; any amount beyond
  lethal (CR 120.4a) is dealt to that creature's controller instead (the creature is marked only with
  the lethal portion). Backed by `DealDamageEffect.excessToController`. Used by Gandalf's Sanction.
- `DealXDamage(target)` — deal X damage (spell's X).
- `AmplifyNoncombatDamageThisTurn(bonus)` — install an until-end-of-turn replacement (CR 616): every
  source you control deals `bonus` *additional* noncombat damage to any permanent or player this turn.
  Combat damage is unaffected; no opponent restriction. `bonus` (a `DynamicAmount`) is resolved once at
  resolution and baked in (typically `DynamicAmount.XValue` from an `{X}` cost); multiple installs stack
  additively. Read at damage time by the engine's static-amplification path, then cleaned up at end of
  turn. Distinct from the opponent-only, permanent-tied `NoncombatDamageBonus` static. Taii Wakeen,
  Perfect Shot: `{X}, {T}: … it deals that much damage plus X instead.`
- `DoubleDamageToPlayer(target, duration = UntilYourNextTurn)` — install a duration-bounded replacement
  (CR 616) that *doubles* all damage — any source, combat or noncombat — dealt to `target` (a player,
  e.g. `EffectTarget.PlayerRef(Player.TriggeringPlayer)`) and to any permanent that player controls. The
  player is resolved once at resolution and baked into a floating effect scoped to that player, so the
  doubling outlives the source that created it (CR 611.2) and lasts the whole `duration`. Read at damage
  time by the engine's static-amplification path — combat damage is doubled per already-assigned recipient
  (assignment/division happens before doubling) and stays attributed to the original source. Two installs
  on the same player each double once (⇒ ×4). Backs the "Stagger" ability word — Lightning, Army of One:
  "Whenever Lightning deals combat damage to a player, until your next turn, if a source would deal damage
  to that player or a permanent that player controls, it deals double that damage instead." Distinct from
  the permanent-hosted, "you"/"opponent"-relative `DoubleDamage` replacement (Furnace of Rath).
- `Fight(target1, target2, excessDamageVariable?)` — two creatures each deal damage equal to their power
  to each other (CR 701.14). When `excessDamageVariable` is set, the excess damage (CR 120.4a, deathtouch-
  and marked-damage-aware) that `target1` deals **to `target2`** is stored into that pipeline number
  variable for a following effect to read via `DynamicAmount.VariableReference` — e.g. The Last Agni Kai:
  `Fight(yours, theirs, "excess") then AddMana(RED, VariableReference("excess"))`.
- `Effects.DividedDamage(total, minTargets, maxTargets, dynamicTotal?)` — "N damage divided as you
  choose among target ..." The targets come from the ability's target requirement; pair with
  `TargetCreature(count, minCount)` (Forked Lightning, Skirk Volcanist) or, for "any number of target",
  a `TargetObject(unlimited = true, dynamicMaxCount = ..., filter = ...)`. Set `dynamicTotal` (a
  `DynamicAmount`) for totals computed when the ability resolves/goes on the stack — Ureni, the Song
  Unending: `dynamicTotal = DynamicAmounts.landsYouControl()`. Works for creatures and planeswalkers
  (`GameObjectFilter.CreatureOrPlaneswalker`); zero chosen targets ⇒ no-op.

  **Always cap the target count at the total.** Each chosen target must be assigned at least 1 damage
  (CR 601.2d), so a requirement that lets the player pick more targets than there is damage leaves them
  with no legal division to submit. Pass `dynamicMaxCount` alongside `unlimited` — a
  `DynamicAmount.Fixed(total)` for a fixed total (Chandra, Flameshaper: 8 damage ⇒ at most 8 targets),
  or the same `DynamicAmount` that drives `dynamicTotal` when the total is board-derived (Ureni). The
  cap and `unlimited` compose: the client is offered `min(legal targets, cap)`.

  **The division is chosen at announcement, never at resolution** (CR 601.2d). It rides on the action
  (`CastSpell.damageDistribution` / `ActivateAbility.damageDistribution`), is locked onto the stack
  object, and is honored verbatim when the effect resolves — so a target removed in response costs that
  target's share and the survivors keep exactly what they were assigned (the total is *not* re-divided).
  The engine surfaces `requiresDamageDistribution` / `totalDamageToDistribute` / `minDamagePerTarget` on
  the legal action for both spells and activated abilities, and the client collects the division right
  after targeting. When no division is supplied (a single target, or a non-interactive controller such
  as the built-in AI) the executor deals the whole total to a lone target, or asks for the division at
  resolution via a `DistributeDecision`.
- `DamageCantBePreventedThisTurn()` — "Damage can't be prevented this turn." Turn-scoped one-shot that
  sets a `GameState` flag (cleared at the next turn boundary), shutting off all damage prevention for
  the rest of the turn — prevention shields, prevention/replacement-of-damage effects, and protection's
  prevention clause are ignored (CR 615.6). The static, permanent-hosted equivalent is the
  `DamageCantBePrevented` replacement effect (Sunspine Lynx); use this effect when a spell/ability needs
  the shutoff without a permanent on the battlefield (Fear, Fire, Foes!).
- `DamageCantBePrevented(appliesTo = EventPattern.DamageEvent(...))` — the static, permanent-hosted
  replacement, **scoped by its `appliesTo` pattern**. Left at the default (`source` and `recipient` both
  `Any`) it is the printed global "Damage can't be prevented" (Sunspine Lynx, Leyline of Punishment).
  Narrow the pattern for a card that names one end of the damage instance: **Excruciator**'s "damage that
  would be dealt by this creature can't be prevented" is
  `DamageCantBePrevented(EventPattern.DamageEvent(source = SourceFilter.Self))`, which leaves every other
  source's damage preventable. `DamageUtils.isDamagePreventionDisabled(state, recipientId, sourceId)`
  matches the pattern against the concrete damage instance through the same `damageSourceMatches` /
  `damageRecipientMatches` pair every other damage replacement uses, so a filter supported by one is
  supported by all. Callers that don't know both ends of the instance get the *unscoped* answer only —
  a scoped effect never blanks a shield it might not cover.
- `DamageToTargetCantBePreventedThisTurnEffect(target)` — the **per-recipient** form: "Damage that
  would be dealt to that creature this turn can't be prevented **or dealt instead to another
  permanent or player**" (Whippoorwill). Stamps a turn-scoped marker on the recipient, cleared at
  cleanup. One marker covers both halves of the clause: `DamageUtils.isDamagePreventionDisabled(state,
  recipientId)` consults it wherever prevention is applied (shields, prevention replacements, and —
  checked per assignment, not as an early-out — protection's prevention clause), and the redirection
  check is skipped for a marked recipient.
  - Reach for this rather than the global `DamageCantBePreventedThisTurn()` whenever the card names a
    creature: the global form blanks every prevention effect in the game for the turn, which is a
    very different card.
  - It is a marker rather than a replacement effect on purpose — "can't be prevented" is a rules
    modification (CR 615.9), not itself a replacement, so it cannot compete in the replacement-effect
    gather and has to be read where prevention is *applied*.

### Life

- `GainLife(amount, target?)` — target gains life (default: controller).
- `PayDynamicLife(amount: DynamicAmount, payer?)` — pay life equal to a `DynamicAmount` (e.g.
  "pay life equal to its power" via `EntityProperty(Triggering, Power)`), evaluated at resolution.
  The dynamic, payer-parametric twin of the fixed `PayLifeEffect`; use it as the `cost` of an
  `OptionalCostEffect` (`Gate.MayPay`) so the same amount can also feed the `ifPaid` effect. A
  non-positive evaluated amount pays nothing and still counts as paid (CR 119.4).
- `LoseLife(amount, target)` — target loses life.
- `DrainLife(amount, from = EachOpponent, to = Controller)` — each player in `from` loses `amount`
  life, then `to` gains life equal to the total *actually* lost (each loss honors `ModifyLifeLoss`
  replacements) as a single life-gain event — "Each opponent loses X life. You gain life equal to
  the life lost this way." (Exsanguinate). Prefer this over `LoseLife + GainLife` whenever the gain
  is worded "equal to the life lost this way".
- `SetLifeTotal(amount, target)` — set target's life total to N.
- `ExchangeLifeAndStat(target, stat, player)` — swap a player's life total with a creature's power or
  toughness (CR 701.12g). `stat` is `CreatureStat.POWER` (default, Evra, Halcyon Witness) or
  `CreatureStat.TOUGHNESS` (Tree of Perdition); `player` defaults to the controller, pass a
  `ContextTarget` for "target opponent's life total". The creature's *projected* stat is what the
  player receives, while the creature's **base** stat is set at Layer 7b — so counters, Auras, and
  Equipment apply on top of the new value. No-op if the creature has left the battlefield.
- `ExchangeLifeTotals(target, drawEqualToLifeLost)` — swap the controller's life total with `target`
  player's (CR 701.12c): each player gains/loses the life needed to reach the other's former total,
  applied through the shared gain/lose-life primitives so gain prevention/replacements and loss
  modification apply and gain/loss triggers fire. With `drawEqualToLifeLost = true`, the controller
  then draws a card for each point of life they **actually lost** in the swap (Mister Negative). Wrap
  the whole thing in `MayEffect` for "you may exchange".
- `LoseHalfLife(roundUp, target, lifePlayer?)` — lose half of life total (round up/down).
- `LockLifeGain(target?, duration?)` — "target player can't gain life" for `duration` (default
  `Duration.Permanent` = rest of the game; `EndOfTurn` / `UntilYourNextTurn` also honored). A one-shot
  effect that tags the player with `CantGainLifeComponent`, so the lock is independent of any source —
  unlike the `PreventLifeGain` *replacement* (§11), which ends when its permanent leaves play.
  Non-player targets are a no-op, so it composes after a "deal damage to any target" rider (Screaming
  Nemesis). Checked by `DamageUtils.isLifeGainPrevented`.
- `LoseGame(target, message?)` — target loses the game.
- `RemoveMaximumHandSize(target?)` — "target has no maximum hand size for the rest of the game"
  (default target: controller). One-shot resolution effect that confers a permanent, player-scoped
  property via `PlayerNoMaximumHandSizeComponent` — unlike the battlefield-only `NoMaximumHandSize`
  *static ability* (§9, Reliquary Tower / Thought Vessel), it survives the source leaving any zone
  (e.g. Wisdom of Ages exiles itself on resolution). Idempotent. `CleanupPhaseManager` checks both
  this component and the static ability when discarding to hand size.
- `ReduceMaximumHandSize(amount, target?)` — "target's maximum hand size is reduced by `amount` for
  the rest of the game" (Inspired Idea; default target: controller). `amount` is an `Int` (fixed)
  or `DynamicAmount` overload, evaluated once at resolution and *accumulated* into
  `PlayerMaximumHandSizeReductionComponent` — repeat applications stack (two Inspired Ideas → −6).
  A permanent, player-scoped reduction that survives the source leaving the stack, distinct from the
  battlefield-only `SetMaximumHandSize` static (§9). `MaximumHandSize.effective` subtracts the
  accumulated total after the `SetMaximumHandSize` statics pick the most restrictive base, floored
  at 0; a player with no maximum hand size has nothing to reduce (the reduction is inert while that
  holds).
- `WinGame(target, message?)` — target wins the game.
- `TakeExtraTurn(target, loseAtEndStep?, powerUpAbilitiesCantBeActivated?)` — target takes an extra turn after this
  one (Time Walk, Lost Isle Calling). Set `loseAtEndStep = true` for "...you lose the game at the beginning of that
  turn's end step" (Last Chance, Final Fortune). Set `powerUpAbilitiesCantBeActivated = true` for "During that turn,
  power-up abilities can't be activated" (Kang the Conqueror) — a global lockout on every player's power-up abilities
  (§ Power-up, CR 702.193) for the extra turn only, on every permanent, that outlives its source. Both riders are
  scoped to the extra turn *this* effect creates, so neither applies when the `PreventExtraTurns` replacement
  (Ugin's Nexus) stops the extra turn from happening; that is why they are parameters here rather than separate
  effects sequenced after it in a `Composite`.
- `EndTheTurn` — end the current turn (CR 720): Ultima ("Destroy all artifacts and creatures. End the turn."),
  Time Stop, Sundial of the Infinite, Discontinuity. When it resolves the whole stack is exiled (including the
  source and any triggered abilities the resolution queued — even ones that can't be countered — so those never
  reach the stack, CR 720.1c), creatures are removed from combat, and the game skips straight to the cleanup step
  (discard to maximum hand size, marked damage wears off, "this turn" / "until end of turn" effects end) before the
  next turn begins. Takes no target — it always ends the active player's turn. Modeled as a two-step effect: the
  executor records an `EndTheTurnRequestedComponent` on the active player, and `PassPriorityHandler` runs the
  sequence via `TurnManager.performEndTheTurn` once the current resolution finishes (so it can exile the *rest* of
  the stack and drop the pending triggers). Compose after a board wipe with
  `Effects.Composite(listOf(Effects.DestroyAll(...), Effects.EndTheTurn))`.
- `ForceExileMultiZone(count, target)` — exile from hand/battlefield/graveyard combined (Lich's Mastery shape).

### Cards (draw / discard)

- `DrawCards(count, target?)` — draw N (default: controller).
- `DrawUpTo(max, target)` — draw up to N (player picks 0–N).
- "Draw a card and reveal it; if it isn't a [type], discard it" (Sindbad) is a pipeline composition, not
  an effect type: `GatherCards(TopOfLibrary(1), "toDraw")` → `DrawCards(1)` →
  `FilterCollection("toDraw", InZone(HAND), "drawn")` (skips the branch when the draw was replaced or the
  library was empty) → `RevealCollection("drawn", revealToSelf = false)` →
  `FilterCollection(MatchesFilter(Land), storeNonMatching = "notLand")` →
  `MoveCollection("notLand" → graveyard, moveType = Discard)`.
- `Discard(count, target)` — controller-of-target chooses; mandatory. `count` is an `Int` or a
  `DynamicAmount` ("discard X cards, where X is …" — e.g. Converge's Arcane Omens with
  `DynamicAmounts.colorsOfManaSpent()`).
- `EachOpponentDiscards(count)` — each opponent discards N.
- `EachPlayerDiscards(count)` — each player, *including you*, discards N, each choosing from their own hand — Rankle's Prank. Symmetric twin of `EachOpponentDiscards`: same `ForEachPlayer` → Gather → Select → Move pipeline, but iterated over `Player.ActivePlayerFirst` so the choices happen in APNAP order (CR 101.4). Known deviation: iterations run one after another, so a later player chooses after an earlier player's cards have already hit the graveyard, where the rules would have every player choose face down (CR 101.4a) and discard simultaneously.
- `EachPlayerPutsCardsOnTopOfLibrary(count = 1)` — each player, *including you*, puts N cards from their own hand on top of their own library, each choosing their own — Sadistic Augermage. `EachPlayerDiscards` with the destination swapped for `ZonePlacement.Top` of the iterated player's library, so it is a tuck rather than a discard (`MoveType.Default`, feeding no discard trigger). Same APNAP iteration and same sequential-iteration deviation.
- `EachOpponentExilesFromHand(count)` — each opponent exiles N cards from their own hand (each chooses their own). Same `ForEachPlayer(EachOpponent)` → Gather → Select → Move pipeline as `EachOpponentDiscards`, but the destination is exile — Mindleech Ghoul.
- `EachPlayerReturnPermanentToHand()` — each player bounces a permanent.
- `EachPlayerDrawsForDamageDealtToSource()` — each player draws equal to damage source took this turn.
- `ReadTheRunes()` — draw N, then discard N (or sacrifice permanents).
- `ReplaceNextDraw(effect)` — replaces controller's next draw this turn with the given effect (a one-shot
  floating shield with `Duration.NextUse`, consumed by the replacement effect processor when matched, so an
  inner `DrawCards` doesn't re-trigger it). The activation-time `{X}` is captured onto the shield, so the
  replacement effect can read `DynamicAmount.XValue` when it fires at draw time (Aladdin's Lamp: "look at
  the top X cards … then draw a card").

### Destruction & exile

- `Destroy(target, noRegenerate?)` — destroy target (respects indestructible). `noRegenerate = true`
  marks the target so it "can't be regenerated" (composes `CantBeRegeneratedEffect` before the move) —
  the single-target analogue of `DestroyAll(noRegenerate = …)`, for Terror / Smother / Tunnel.
- `RegenerateEffect(target)` (raw — no facade) — drop a regeneration shield on `target`, lasting until end
  of turn. The next time `target` would be destroyed this turn, instead tap it, remove all damage marked on
  it, and remove it from combat. Consumed by the first destruction it intercepts. The tap is a **real tap
  transition** (CR 701.19a, "its controller taps it"): it goes through the `tap()` atom, so it fires
  "becomes tapped" triggers (Deeproot Pilgrimage, Captain America, Living Legend) and stamps the
  per-permanent first-time-tapped window. Regenerating an already-tapped creature taps nothing and emits
  no event (CR 701.26a), while the damage and combat removal still apply.
- `RemoveDamageShieldEffect(target)` (raw — no facade) — Pyramids' second mode. Same shape as regeneration:
  a one-shot destruction shield lasting until end of turn that replaces "destroyed" with "remove all damage
  marked on it". Differs from regeneration in *not* tapping the target and *not* removing it from combat —
  only the marked damage is cleared. The shield isn't a regeneration ability, so a "can't be regenerated"
  marker on the target doesn't disable it. Consumed by the first destruction it intercepts; expires at end
  of turn.
- `DestroyAll(filter, noRegenerate?, storeDestroyedAs?, excludeTriggering?)` — destroy all matching; optionally
  save the ID list for follow-up. `excludeTriggering = true` spares the triggering entity, for "destroy all
  *other* … with it" triggers (Spreading Plague).
- `DestroyAllAndAttached(filter, noRegenerate?)` — also destroys auras/equipment on the matching permanents.
- `SacrificeAll(filter, excludeTriggering?)` — each matching permanent is *sacrificed by its controller*
  (CR 701.21): emits `PermanentsSacrificedEvent` (sacrifice triggers fire), routes each card to its owner's
  graveyard, and ignores regeneration/indestructibility. The "is sacrificed" sibling of `DestroyAll`.
  `excludeTriggering = true` spares the source ("except for ~", Golgothian Sylex).
- `DestroyLeastPowerCreature(noRegenerate?)` — destroy the creature with the least power among **all**
  creatures on the battlefield (global, both players). On a tie for least power the controller chooses which
  one dies (Drop of Honey). Backed by the `GameObjectFilter.Creature.hasLeastPowerAmongAllCreatures()` filter
  (`StatePredicate.HasLeastPowerAmongAllCreatures`) gathered, then a `ChooseExactly(1)` selection that
  auto-resolves when the minimum is unique.
- `DestroyCreaturesBlockingOrBlockedBySource(noRegenerate?)` — destroy the creatures blocking, or blocked by,
  the effect's source (CR 509 combat pairing), using the pairing **last known when the source left the
  battlefield**. For "when ~ dies, destroy all creatures blocking or blocked by it" (Abu Ja'far): the live
  combat cross-references are already torn down by the time a dies trigger resolves, so the pairing is read
  from the leaves-battlefield snapshot (`ZoneChangeEvent.lastKnownBlockingOrBlockedByIds` →
  `EffectContext.triggerLastKnownBlockingOrBlockedByIds`) via `CardSource.LastKnownCombatPairedWithSource`,
  restricted to creatures still on the battlefield.
- `DestroyAllEquipmentOnTarget(target)` — wreck the gear attached to a creature.
- `Exile(target, fromZone?, addCounterType?)` — exile target. `fromZone` skips the exile unless the
  card is still in that zone ("exile it **from their graveyard**" does nothing once the card has moved
  on — by then it's a new object, CR 400.7); `addCounterType` puts one counter of that type on the card
  once it lands ("exile it **with a stash counter on it**" — Tinybones, Bauble Burglar), and is skipped
  along with the move when the `fromZone` gate closes. Both are pass-throughs to `MoveToZoneEffect`
  (also reachable via `Effects.Move`), where `addCounterType` is the single-target counterpart of
  `MoveCollectionEffect.addCounterType`.
- `ExileAndGrantOwnerPlayPermission(target, until?)` — exile + owner may play it (Garth-style).
- `ExileOpponentsGraveyards()` — exile every card in each opponent's graveyard.
- `MoveUntilSourceLeaves(target, destination)` — move an object to a non-battlefield, non-stack zone and return it to its previous zone immediately when the source's battlefield visit ends. The return does not use the stack and survives loss of the source's abilities. An exile also populates the ordinary linked-exile pile for client display and linked abilities. Returning permanents enter under their owner's control; tokens and objects that have since left the destination do not return. If the source has already left (including leaving and returning), the initial move does nothing. Ossification uses `Effects.MoveUntilSourceLeaves(victim, Zone.EXILE)` with no leaves trigger.
- `ExileUntilLeaves(target)` — linked exile; the exiled card returns when the source leaves the battlefield (pair with `ReturnLinkedExile*` on the source's `LeavesBattlefield` trigger). The target is normally a **battlefield** permanent (O-Ring: Liminal Hold, Driftgloom Coyote), but a **graveyard card** is also legal — the executor moves the target to exile from whichever of those two zones it is in, so a cross-zone union `target(...)` ("creature on the battlefield **or** creature card from a graveyard") works directly (Savior of Ollenbock). Other zones are ignored.
- `ExileWithAurasNotingCounters(target = ContextTarget(0))` / `ReturnNotedExileTappedWithAuras()` — the state-preserving "blink that remembers counters and Auras" pair (Tawnos's Coffin). The exile half exiles the target creature **and all Auras attached to it** (all linked to the source via `LinkedExileComponent`) and records the creature's identity + its `kind→count` counter snapshot on the source via `NotedExileComponent` (captures the Auras *before* exiling the creature, so the unattached-Aura SBA can't pre-empt them). The return half (a no-op when nothing is noted, so it's safe to fire from **both** a `LeavesBattlefield` and a `BecomesUntapped` trigger — whichever fires first returns the cards) returns the noted creature **tapped under its owner's control** with the noted counters restored, then returns the linked Auras attached to it; Auras that can't legally re-attach go to their owners' graveyards via the CR 704.5m unattached-Aura SBA (the "If you don't …" fallback). `NotedExileComponent` is preserved across the source's own zone change (like `LinkedExileComponent`) so the leaves-the-battlefield return still reads it, and stripped on battlefield re-entry (Rule 400.7).
- `ExileLinkedToSource(target)` — exile a target **permanently** and record it in the source's linked-exile pile (`LinkedExileComponent`). Unlike `MoveUntilSourceLeaves` there's no automatic return — the link just lets later abilities reference the exiled card (Territory Forge's "this permanent has all activated abilities of the exiled card").
- `RecordChosenLinkedExile(from)` — stamp the source's `ChosenLinkedExileComponent` with the first card in the pipeline collection `from` (its "last chosen card"). Pair after a `SelectFromCollection` over `CardSource.FromLinkedExile()` so a `HasAbilitiesOfChosenLinkedExiledCard` static ability grants the source that card's activated and triggered abilities (Koh, the Face Stealer's "Pay 1 life: Choose a creature card exiled with Koh").
- `ExileGroupAndLink(filter, storeAs?)` — exile all matching permanents into source's linked exile pile.
- `ExileFromTopRepeating(count, repeatCondition)` — keep exiling top cards while a condition holds.
- `ExileLibraryUntilManaValue(manaValue)` — exile from library until mana value ≤ N.
- `Effects.ExileTopCardContest(storeWinnerAs, players = Player.Each, storeExiledAs = "contestExiledCards")`
  (`ExileTopCardContestEffect`) — each player exiles the top card of their library face up; the one who
  exiled the **greatest mana value** is published as the single entry of the pipeline collection
  `storeWinnerAs`, and ties repeat among the **tied players only** until one is alone at the top. Fully
  deterministic — no player ever chooses anything — so it resolves in a single pass with no decision or
  continuation. **Open by design**: it answers "who won" and stops, so the payoff is composed off
  `EffectTarget.PipelineTarget(storeWinnerAs)` rather than being owned by a library primitive. **The
  contest can end with no winner** (a player with an empty library exiles nothing and so can never have
  exiled the greatest mana value; if no contender can exile at all the tie stands), so gate the payoff on
  `Conditions.CompareAmounts(DynamicAmount.DistinctEntitiesInCollections(listOf(storeWinnerAs)), GTE,
  Fixed(1))` — an unresolved `PipelineTarget` otherwise falls back to the ability's controller. Every card
  exiled, in every round, stays in exile and is published under `storeExiledAs`. Used by **Timesifter**
  (`ExileTopCardContest` then a gated `TakeExtraTurn` on the winner).

### Return / placement

- `ReturnToHand(target)` — bounce to hand. Also the right effect for a **targeted** graveyard→hand
  return ("Return target creature card from your graveyard to your hand"): the requirement's own
  `zone = GRAVEYARD` is re-checked at resolution under CR 608.2b, so the clause needs no `fromZone`
  guard, and Argentum Assay's `Graveyard.kt` row builds it without one. Writing the guard on a
  targeted return *creates* a differential divergence.
- `ReturnToHandFromGraveyard(target)` — the *guarded* bounce, `PutOntoBattlefieldFromGraveyard`'s
  sibling one destination over: `MoveToZone(…, Zone.HAND, fromZone = GRAVEYARD)`, so the move is
  skipped if the card has left the graveyard by resolution. Use this for the **self**-return, and only
  that: "Return this card from your graveyard to your hand" (Sanitarium Skeleton, Eternal Dragon,
  Dutiful Griffin) — every caller passes `EffectTarget.Self`. The guard is the *only* check
  there because the clause names no target: `ActivateAbilityHandler` checks an ability's
  `activateFromZone` when the ability is *activated* and nothing re-checks it on resolution, so
  without the guard a card exiled from the graveyard in response to its own ability comes back from
  exile. Assay builds this effect for that sentence (`Recursion.kt`), so a self-return omitting the
  guard shows up in the differential. The asymmetry with `PutOntoBattlefieldFromGraveyard`, whose
  *targeted* row does keep the guard, is empirical — dropping it there broke six cards.
- `PutOnTopOfLibrary(target)` — place target on top of its owner's library.
- `PutOnBottomOfLibrary(target)` — place target on the bottom of its owner's library (forced, no choice).
- `PutOnTopOrBottomOfLibrary(target)` — player chooses top or bottom.
- `PutSecondFromTopOrBottomOfLibrary(target)` — second-from-top or bottom.
- `ShuffleIntoLibrary(target)` — shuffle target into owner's library.
- `PutIntoLibraryNthFromTop(target, positionFromTop)` — place N from the top.
- `PutOntoBattlefield(target, tapped?)` — put target on the battlefield.
- `PutOntoBattlefieldUnderYourControl(target)` — under controller's control.
- `PutOntoBattlefieldFromGraveyard(target, underYourControl = false, tapped = false)` — the *guarded* return:
  `MoveToZone(…, fromZone = GRAVEYARD)`, so the move is skipped if the card has left the graveyard by
  resolution. Use this for "Return target creature card from your graveyard to the battlefield"
  (Zombify, Reya Dawnbringer); plain `PutOntoBattlefield` is for a card whose zone the effect already
  fixed. `underYourControl = true` adds the `controllerOverride` that
  `PutOntoBattlefieldUnderYourControl` sets, for "return that card to the battlefield **under your
  control**" where the guard is wanted too (Scythe of the Wretched) — the two axes are independent, so
  reach for this rather than a raw `MoveToZoneEffect`. `tapped = true` is the third independent axis,
  for "Return this card from your graveyard to the battlefield **tapped**" (Reassembling Skeleton,
  Haunted Dead, Tunnel Rats) — before it existed those cards used plain `PutOntoBattlefield` and
  silently dropped the guard, which is what a frozen facade parameter costs.
- `PutOntoBattlefieldFaceDown(count, target?)` — enter face-down (2/2 morph shape).
- `RevealFaceDownPermanent(target?)` — reveal a face-down permanent (make its hidden card public,
  CR 708.2). Informational only — does **not** turn it face up. Pair with
  `Conditions.TargetIsCreatureCard` + `TurnFaceUpEffect` for "Reveal target face-down permanent. If
  it's a creature card, you may turn it face up." (Hauntwoods Shrieker).
- `PutOntoBattlefieldAttachedToChosen(target, hostFilter?)` — put a targeted Aura or Equipment onto the
  battlefield attached to a permanent the controller chooses at resolution (default host filter: a creature
  you control). Works for both Auras and Equipment; the host is chosen, not targeted. If no legal host exists,
  an Equipment enters unattached while an Aura can't enter (Rule 303.4g). (One Last Job.)
- `ReturnSelfToBattlefieldAttached(target, transformed = false)` — return source attached to target
  (Aura recursion). [target] may resolve to a **player** as well as a permanent, and the two cases
  differ in who controls the returned Aura: a *permanent* host hands control to that permanent's
  controller (the Dragon-aura cycle's "attached to that creature"), a *player* host leaves it under
  the **ability's** controller — the only reading "under your control attached to target opponent"
  allows. `transformed = true` returns it back face up (CR 712.8); the face swap happens while the
  card is still in its old zone, so the entry registers the back face's statics and replacements.
  A single-faced card told to enter transformed doesn't move at all (standing ruling), so it is a
  quiet no-op. Radiant Grace: `ReturnSelfToBattlefieldAttached(target = cursed, transformed = true)`.
- `ReturnSelfFromExileTransformed` — Craft resolution (CR 702.167a). Returns the source from exile to the
  battlefield as its back face, under its owner's control, and re-attaches the source's
  `CraftedFromExiledComponent` recording the exiled materials. Pair with `AbilityCost.Craft`; see the `Craft`
  keyword helper in the keyword catalog.
- `Transform(target = Self)` — turn a double-faced **permanent** over in place (CR 701.27a). The entity id,
  counters, damage, attachments, controller and timestamp all survive; only the identity characteristics
  change, and the new face's static/replacement abilities are re-registered. Emits `TransformedEvent`, so
  "whenever this transforms" triggers fire. A target that isn't a double-faced permanent is a silent no-op
  (CR 701.27c), as is one that can't transform (`AbilityFlag.CANT_TRANSFORM`, daybound/nightbound).
  The front-face tracking a flip needs (`DoubleFacedComponent`, stamped front face up per CR 712.14) is
  installed on **three** entry routes: the cast pipeline (`StackResolver`), every effect-driven entry —
  reanimation, a fetch that puts the card onto the battlefield, a return from exile — via
  `ZoneTransitionService.applyBattlefieldEntry`, and *playing a land*, which is a special action that
  bypasses that service and so calls `stampDoubleFacedFrontFace` from `PlayLandHandler` itself (Balamb
  Garden, SeeD Academy). Face-down entries are excluded (CR 708.2). Still **not** covered: the handful of
  ad-hoc "put it onto the battlefield already attached" placements that call `BattlefieldEntry.place`
  directly (`MoveCollectionExecutor.moveAuraToBattlefield`, `ReturnSelfToBattlefieldAttachedExecutor`,
  `ReturnOneFromLinkedExileExecutor`) — no shipped double-faced Aura or Equipment reaches the battlefield
  that way today, but a `Transform` on one that did would be a silent no-op. To gate the flip on the card
  actually having two faces — "If it's a double-faced card, you may transform it" — wrap it in a
  `ConditionalEffect` over `Filters.DoubleFaced` (§ filters).
- `ExileAndReturnTransformed(target = Self, returnAs = ReturnFace.TRANSFORMED)` — "Exile [this], then return it
  to the battlefield transformed under its owner's control" (FIN Dominant / eikon transform). Exiles a
  double-faced permanent and re-enters it as a **new object** on the chosen face — unlike `Transform`, which
  flips a permanent in place. Because it is a new object: counters/damage drop, attachments fall off, leaves-
  and enters-the-battlefield triggers fire (not transform triggers), and a Saga face re-enters with one lore
  counter (CR 714.2b). The exile and return are atomic (no priority/SBAs between). `returnAs`: `TRANSFORMED`
  (the opposite face — front→back), `FRONT` ("return it front face up" — the eikon Saga's final chapter flips
  back to the legend), or `BACK`. The front face's activated ability is sorcery-speed
  (`timing = TimingRule.SorcerySpeed`); Jecht uses it from a "may" combat-damage trigger instead.
- `ReturnSelfFromGraveyardTransformed(tapped = false)` — "Return this card from your graveyard to the
  battlefield transformed" (Garland, Knight of Cornelia). Returns the *source card* from the graveyard
  to the battlefield with its back face up; pair with `activateFromZone = Zone.GRAVEYARD` on the owning
  activated ability, **or wire it to `Triggers.Dies`** for the "when this dies, return it transformed"
  templating (LCI god cycle — Ojer Axonil/Kaslem/Pakpatiq/Taq // Temples, Aclazotz). No transform
  triggers fire (the card was never turned over on the battlefield); the back face's ETB triggers fire
  normally. No-ops if the source left the graveyard before resolution, or if it isn't a double-faced
  card (a single-faced card instructed to enter transformed doesn't move at all). Pass `tapped = true`
  to return the back-face permanent **tapped** ("...return it to the battlefield tapped and
  transformed"). Because the graveyard→battlefield move preserves the entity id, "...with N counters
  on it" (Ojer Pakpatiq's three time counters) composes as `Composite(ReturnSelfFromGraveyardTransformed(
  tapped = true), AddCounters(counter, n, EffectTarget.Self))` — the following `Self` still resolves to
  the returned permanent. Raw type `ReturnSelfFromZoneTransformedEffect(fromZone, tapped)` generalizes
  to other source zones.
- `ReturnCreaturesPutInGraveyardThisTurn(player)` — Patriarch's Bidding shape.
- `ReturnSameNamedFromGraveyard(target = ContextTarget(0))` — return the target graveyard card and
  **every other card with the same name** in the controller's graveyard to the battlefield **tapped**
  under the controller's control (each moved through `ZoneTransitionService`, so enters-with-counters
  still apply). Rat King, Verminister: "Return target creature card and all other cards with the same
  name as that card from your graveyard to the battlefield tapped."

### Hand reveal

- `Effects.MayRevealCardFromHand(filter, otherwise?)` — atomic "you may reveal a `filter`
  card from your hand" choice. Computes eligible hand cards; if none, runs `otherwise`
  silently; otherwise prompts the controller with a `SelectCardsDecision` (min=0, max=1).
  Revealing emits a `CardsRevealedEvent` and stops; declining (or empty selection) runs
  `otherwise`. Compose with `Effects.Tap`/`Effects.Sacrifice`/etc. via `otherwise` to
  express "if you don't, X" riders — e.g. SOI shadow lands wrap this in
  `OnEnterRunEffect(...)` with `otherwise = Effects.Tap(EffectTarget.Self)` for the
  "this land enters tapped" branch.
- `Effects.Behold(filter, ifBeheld?)` — resolution-time **behold** (`BeholdEffect`): "you may
  behold a `filter`. If you do, `ifBeheld`." The behold itself is optional — the controller may
  choose a matching permanent they control **or** reveal a matching card from hand (revealing emits
  `CardsRevealedEvent`; battlefield permanents are merely chosen). If they decline, or control no
  matching permanent and hold no matching card, `ifBeheld` does not run. Distinct from the cast-time
  `AdditionalCost.Behold` (on its own or as an `AdditionalCost.OrPay` leg — beholding as a casting
  cost). Sarkhan,
  Dragon Ascendant ETB: `Effects.Behold(GameObjectFilter.Any.withSubtype(Subtype.DRAGON),
  ifBeheld = Effects.CreateTreasure())`.

### Library reveal & free cast

- `PlayFromCollectionWithoutPayingCostEffect(from)` (facade `Effects.PlayFromCollectionWithoutPayingCost(from)`) — play the first card in the pipeline collection immediately during the resolving effect. Nonlands use the free-cast machinery; lands use the normal land-play path, consume a land play for the turn, and remain unplayed when no land play is available. Use this for Oracle text that says **play**, such as **Fight Rigging**; it grants no permission that survives resolution.

- `Effects.Cascade` — CR 702.85a (`CascadeEffect`). Exile from the top of the controller's library
  until a nonland card with mana value **strictly less than** the triggering spell's is exiled,
  offer to cast it for free, bottom-randomize every exiled card that isn't cast.
  **`Keyword.CASCADE` alone does nothing** — the engine never reads it. Cascade *is* a "when you cast
  this spell" triggered ability, so a card with cascade carries the keyword (for the printed line)
  **plus** `triggeredAbility { trigger = Triggers.WhenYouCastThisSpell(); effect = Effects.Cascade }`.
  Quandrix, the Proof; Meteoric Mace; Annoyed Altisaur. The card type is irrelevant — it fires on an
  Equipment spell exactly as on a creature spell.
- `Effects.Discover(amount, storeDiscoveredAs?, thenEffect?)` — Discover N, CR 701.57 (`DiscoverEffect`).
  Exile from the top of the controller's library until a nonland card with mana value **≤ N** is exiled
  (the "discovered card"), then present a two-option prompt: **cast it for free** or **put it into your
  hand** (if the cast can't initiate, it falls back to hand); bottom-randomize the rest. `amount` is an
  `Int` (fixed, "Discover 4/5/10") or a `DynamicAmount` ("Discover X, where X is that spell's mana value"
  — **Hurl into History**, pass `EntityProperty(Target(0), ManaValue)`). Differs from `Cascade` on three
  axes: explicit threshold (not the triggering spell's MV), ≤ vs strict <, and the non-cast branch keeps
  the card (hand) rather than bottoming it — hence a distinct primitive. Set `storeDiscoveredAs` to publish
  the discovered card's id to a pipeline collection and `thenEffect` to resolve a follow-up **only when a
  card was discovered** (CR 701.57c); the follow-up runs after the cast/hand step and can read the
  discovered card — e.g. **Hit the Mother Lode** (`Effects.Discover(10, storeDiscoveredAs = "discovered",
  thenEffect = Effects.CreateTreasure(count = IfPositive(Subtract(Fixed(10), StoredCardManaValue("discovered"))), tapped = true))`).
- `RevealAndMayCastFromLibraryEffect(count, maxManaValue, player?)` — Sunbird's Invocation
  shape. Reveal top `count` cards of `player`'s library, present a `SELECT_CARDS` prompt over
  the revealed nonland cards with mana value ≤ `maxManaValue` (player picks 0 or 1), free-cast
  the chosen card if any, bottom-randomize the rest. Pair with `DynamicAmounts.triggeringManaValue()`
  (= `EntityProperty(Triggering, ManaValue)`) when both bounds come from the triggering spell.

### Linked exile & play-from-exile permissions

- `ReturnLinkedExile()` — return all from source's linked exile, under controller.
- `ReturnLinkedExileUnderOwnersControl()` — return under each card's owner. `CardSource.FromLinkedExile()` and `linkToSource` pipeline moves retain the resolving ability's original battlefield source visit: a source that blinks gets a new pile, while its old return trigger reads the departed visit's pile. A token source may cease to exist before that trigger resolves. Leaving exile invalidates the old link even if the same card later returns to exile.
- `ReturnLinkedExileToHand()` — return all from linked exile to hand.
- `ReturnLinkedExileToZoneExiledFrom()` — return each linked-exiled card to **the zone it was exiled from** (CR 610.3 "this second one-shot effect returns the object to its previous zone"), under its owner's control when that zone is the battlefield (CR 610.3c). For an exile-until clause whose exile half can reach more than one zone: **Cloak and Dagger, Entwined** exiles either a nonland card from an opponent's *hand* or the chosen creature from the *battlefield*, and one leaves-the-battlefield trigger puts each back where it belongs. Built on `CardDestination.ToZoneExiledFrom` (below); prefer the fixed-destination siblings when the card names one zone explicitly. Recorded origins are honoured as-is (battlefield, hand, graveyard, library, command zone, sideboard), with two special cases: a card recorded as exiled from the **stack**, or with no recorded origin at all, falls back to the battlefield, and a card exiled **from exile** (CR 406.7) stays put.
- `ReturnOneFromLinkedExile()` — return one chosen card.
- `GrantMayPlayFromExile(from, expiry?, withAnyManaType?, asThoughFlash?, condition?, landEntersTapped?, onPlayRider?, ownerControls?, recipient?, exileAfterResolve?, fixedAlternativeManaCost?, fixedAlternativeCostIsManaValue?, waterbend?, nonLandOnly?, castFaceIndex?, castColorRestriction?)` — controller may play matching cards from exile. `nonLandOnly=true` restricts the permission to *casting* — a land among the granted cards can never be played through it, modeling "you may **cast** that card" wording (**Ragavan, Nimble Pilferer**: "exile the top card of that player's library… you may cast that card") as distinct from "you may **play** those cards" (Light Up the Stage): "cast" never covers a land's play-as-a-special-action (CR 305.1), so the granting effect still exiles the card regardless of type, but the resulting `MayPlayPermission.nonLandOnly` is read by `CastFromZoneEnumerator` (both the exile and graveyard land-play branches) and independently by `PlayLandHandler`'s authoritative `validate`/`execute`, so a client can't bypass the restriction by hand-constructing the action. `castFaceIndex` restricts the permission to the card's **alternative face** at that index (`cardFaces[castFaceIndex]`) instead of its primary characteristics — the face's mana cost, type line, timing, cast restrictions, target requirements, and spell script all apply, and the emitted `CastSpell` carries the same `faceIndex`. Models "you may cast it from your graveyard **as an Adventure**" (**Mosswood Dreadknight**, CR 715.3): index 0 is the Adventure face, so only Dread Whispers becomes castable and the creature half stays locked in the graveyard. Resolving the Adventure then exiles the card and grants the ordinary cast-the-creature-from-exile permission (CR 715.3d), so the two halves chain with no card-specific wiring. Reuses the same enumerator face-swap as a Secrets of Strixhaven prepare-spell copy; `CastSpellHandler` rejects any `faceIndex` (including the unrestricted `null`) that no active permission authorizes. `asThoughFlash=true` lets the granted cards be cast at **instant speed** — "as though they had flash" (CR 702.8) — even when they are sorceries/creatures; the timing rider rides on the `MayPlayPermission` and is honored by both `CastFromZoneEnumerator` (offered actions) and `CastSpellHandler` (authoritative timing check), and waives no cost. Combine with `condition = IsYourTurn` + `withAnyManaType = true` + `expiry = MayPlayExpiry.Permanent` for "During your turn, you may cast cards exiled with this … as though they had flash. Mana of any type can be spent to cast those spells." (**Azula, Cunning Usurper**, whose ETB exiles the two chosen cards *with* it via `MoveCollection(linkToSource = true)`, then grants over `CardSource.FromLinkedExile`). `fixedAlternativeManaCost` (a `ManaCost`, e.g. `{2}`) makes each granted card castable for that *fixed* cost **instead of** its printed mana cost while exiled — it *replaces* the cost, unlike `GrantPlayWithCostIncrease` which adds on top. Stamps `PlayWithFixedAlternativeManaCostComponent(controllerId, fixedCost, waterbend)`, honored by `CastFromZoneEnumerator` + `CastSpellHandler` and stripped on leaving exile by `StackResolver`. `fixedAlternativeCostIsManaValue = true` computes that fixed cost **per card** as `{its mana value}` generic at grant time (a 6-drop → `{6}`, mutually exclusive with a literal `fixedAlternativeManaCost`), and `waterbend = true` marks it a **waterbend** cost (CR 701.67): its whole generic may be paid by tapping untapped artifacts/creatures (each `{1}`) in addition to mana — `CastSpellHandler` reduces the fixed cost by the tapped `AlternativePaymentChoice.tapForGenericPermanents` (cap = the fixed cost's generic) in both validation and payment, and `CastFromZoneEnumerator` surfaces `hasTapForGeneric`/`tapForGenericPermanents` + folds the tap help into affordability. Reached through the `Effects.WaterbendCastFromExile(from, condition?)` facade — backs **Hama, the Bloodbender** ("you may cast the exiled card during your turn by waterbending {X} … where X is its mana value"), whose grant is gated by `AllConditions(IsYourTurn, YouControlSource)` so the exiled card is castable only on your turn and only while you control the granting source (once it leaves the battlefield `YouControlSource` fails and the grant ends). Backs the **Airbend** keyword (`Effects.Airbend`); pair with `ownerControls = true` for "its owner may cast it for {2}". `exileAfterResolve=true` stamps `ExileAfterResolveComponent` on each granted card so a spell cast from the permission is exiled instead of going to a graveyard (on resolution, when countered, or when it fizzles) — the "If that spell would be put into a graveyard, exile it instead" rider on borrow-a-spell-you-don't-own cards (Nita, Forum Conciliator); the same mechanism as `GrantFreeCastTargetFromExile.exileAfterResolve` but for a *paid* cast. `withAnyManaType=true` relaxes the colored pips so mana of any type can pay them (Laughing Jasper Flint, Cruelclaw's Heist); the grant works whether the card stays in exile *or* in a graveyard (Tinybones, the Pickpocket grants over a card the trigger gathered straight from a graveyard via `CardSource.ChosenTargets`), and the relaxation is applied both in the legal-action enumerator and in the cast handler's payment. `landEntersTapped=true` forces a played land tapped regardless of its own ETB script (Lightstall Inquisitor); PlayLandHandler reads the flag off the active `MayPlayPermission` at play time and stamps `TappedComponent` before the card's intrinsic `EntersTapped` branch runs. `onPlayRider` is a "When you play a card this way, …" payoff: the engine registers a linked event-based delayed triggered ability alongside the permission, and casting/playing a granted card emits a `CardPlayedFromPermissionEvent` (link-id-scoped, like `DamagePreventedEvent`) that fires the rider on the stack as a triggered ability of the granting source. Expires with the grant (end of turn). Used by Fires of Mount Doom ("…When you play a card this way, Fires of Mount Doom deals 2 damage to each player."). `ownerControls=true` grants the permission to each exiled card's *owner* instead of the effect controller — the collection is grouped by owner into one permission per owner, and any turn-keyed `expiry` (e.g. `MayPlayExpiry.UntilEndOfNextTurn`) is measured against each owner's own turns. Use for "for each of those cards, its owner may play it until the end of their next turn" wording where the exiled cards may belong to different players (Suspend Aggression: exile a target nonland permanent + your top library card, each owner may replay the one they own). Mirrors `MakePlottedEffect.ownerControls`; prefer it (composed in a gather → exile → grant pipeline) over the monolithic `ExileAndGrantOwnerPlayPermission` when the expiry is turn-bounded or more than one card is exiled. `recipient` (an `EffectTarget`, default `Controller`) names the player who gets the permission when it isn't the effect's controller — resolved against the resolving context, so a trigger can hand the grant to a player named by the trigger rather than by the source. **Gonti, Night Minister** ("Whenever a creature deals combat damage to one of your opponents, **its controller** … may play that card") uses `recipient = EffectTarget.ControllerOfTriggeringEntity`: in a pod the damaging creature's controller may be an opponent of Gonti's controller, a player the ability has no other handle on. Turn-keyed `expiry` windows still follow the *activating* player (the grant's duration is a property of the effect, not of who may use it); for per-card owner routing use `ownerControls`, which takes precedence. `castColorRestriction = Color.RED` restricts the permission to **red spells** — checked against the *face actually being cast*, not the card sitting in exile, in both `CastFromZoneEnumerator` and `CastSpellHandler`. This is the "You may cast red spells from among them this turn" wording (**Chandra, Dressed to Kill** −7), and it is deliberately *not* the same as "if it's red, you may cast it" (her +1), which tests the exiled **card's** characteristics and is expressed upstream as `FilterCollection(MatchesFilter(GameObjectFilter.Any.withColor(RED)))` before the grant. Her rulings pin the difference: a modal double-faced card that is red in exile may have *either* face cast through the +1, but its blue back face is not castable through the −7.
- `GrantPlayWithoutPayingCost(from)` — same, without paying mana costs.
- `GrantPlayWithAdditionalCost(from, additionalCost)` — require a runtime additional cost when casting
  cards in the named collection. Compose with `GrantMayPlayFromExile` and
  `GrantPlayWithoutPayingCost` for "pay [cost] rather than pay its mana cost" permissions. The cost
  remains additive with the spell's printed mandatory additional costs; `PayLifeEqualToManaValueOfSpell`
  computes and displays the life payment per exiled card.
- `GrantPlayWithCostIncrease(from, amount)` — stamp `PlayWithCostIncreaseComponent(controllerId, amount)` on every card in the collection, so the next cast pays `{amount}` extra generic. Pair with `GrantMayPlayFromExile` for "each spell cast this way costs {N} more" clauses (Lightstall Inquisitor); for target-based "exile this permanent, owner may play it, opponents tax" effects use `Effects.ExileAndGrantOwnerPlayPermission` instead.
- `GrantFreeCastTargetFromExile(target)` — cast specific exiled card for free.
- `MakePlotted(from, ownerControls = false)` — make every card in the named collection *plotted* (CR 718). The cards must already be in exile (chain after a `MoveCollection` to `Zone.EXILE`). Each card gets the plotted designation (`PlottedComponent`) + a permanent free-cast-as-a-sorcery-on-a-later-turn permission (`PlayWithoutPayingCostComponent` + `MayPlayPermission` gated by `SourcePlottedOnPriorTurn`) — the Plot keyword's state without a plot cost. Emits a `CardPlottedEvent` per card so "when this card becomes plotted" triggers fire. No-ops on an empty collection (so an optional "you may exile … it becomes plotted" fork is safe). With `ownerControls = true` the free-cast permission goes to each card's **owner** rather than the effect's controller (CR 718.2 — for "exile target spell, it becomes plotted" the spell's owner casts it later, not the plotter); default is controller-controls (you plot a card you own, like Make Your Own Luck).

### Stats & keywords

- `SwitchPowerToughness(target?, duration?)` — switch power and toughness after all other stat changes. Defaults to target 0 and end of turn. Each switch applies; two cancel.

- `ModifyStats(power, toughness, target?, duration?)` — `±P/±T` for `duration` (default: until end of
  turn). Pass `Duration.WhileSourceTapped("…")` for the Antiquities "tap-locked" buffs (Ashnod's Battle
  Gear `+2/-2`, Tawnos's Weaponry `+1/+1`): the bonus persists for as long as the source artifact remains
  tapped and the one-way latch drops it when it untaps.
- `SetBasePower(target, power: DynamicAmount, duration, reevaluateContinuously = false)` — set base power
  to a dynamic value (Layer 7b), leaving toughness unchanged.
- `SetBaseToughness(target, toughness: DynamicAmount, duration, reevaluateContinuously = false)` —
  toughness-only sibling of `SetBasePower`.
- `SetBasePowerAndToughness(power, toughness, target?, duration)` — set base power AND toughness (Layer 7b,
  set values), e.g. "Target creature has base power and toughness 5/5 until end of turn" (Dreadful as the
  Storm). Two overloads: fixed `Int`s, or `DynamicAmount`s (which also take `reevaluateContinuously`).
  - All three facades lower onto the one **`SetBaseStatsEffect(target, power: DynamicAmount?, toughness:
    DynamicAmount?, duration, reevaluateContinuously)`** atom — `null` power or toughness leaves that stat
    unchanged, so the same type covers power-only, toughness-only, and both. (Distinct from
    `ModifyStatsEffect`, a +N/+N *modifier* in layer 7c, and from the `SetBasePowerToughness*Static` CDAs,
    which apply for as long as a static ability printed on a permanent is active rather than as a floating
    effect with a duration.)
  - **`reevaluateContinuously`** picks *when* the `DynamicAmount`s are read — the headline difference
    between two real Magic templates on the same layer, though not the only one (three further limits
    follow):
    - `false` (default) — **snapshot**: evaluated once as the effect resolves and frozen for the duration.
      "Change this creature's base power to target creature's power" keeps the number it saw.
    - `true` — **re-evaluated**: the `DynamicAmount` travels into the floating effect (a nullable-halves
      `SetPowerToughnessDynamic` modification) and is recomputed on every projection pass, so the stat
      tracks the game state. This is what an effect handing out a *quoted static ability* needs —
      **Ms. Marvel, Kamala Khan**'s "Until end of turn, Ms. Marvel gains 'Ms. Marvel's base power is equal
      to the number of cards in your hand.'" =
      `Effects.SetBasePower(EffectTarget.Self, DynamicAmounts.cardsInYourHand(), Duration.EndOfTurn,
      reevaluateContinuously = true)`.
    Either way the *affected set* is locked in at resolution (CR 611.2c); only the number moves. A
    self-granted clause like Ms. Marvel's is **not** a CDA (CR 604.3a criterion 2 — printed on the card it
    affects — and criterion 4 — not an ability an object grants to itself), so it applies in layer 7b per
    CR 613.4b ("effects that refer to the base power and/or toughness of a creature apply in this layer"),
    not 7a. Counters and pump effects are layer 7c and still apply on top.
    Four limits apply to `true` and not to the snapshot mode; the first three because the projector — not
    the resolution — reads the number:
    - **Only projection-scoped `DynamicAmount`s.** The amount is re-evaluated with just the source, its
      controller and the affected entity in scope, so `XValue`/`CastX`, `ContextProperty`, pipeline
      collections, and any `EntityReference`/`Player` naming a target, the triggering object, or something
      sacrificed/tapped as a cost have nothing to resolve against. Those are **rejected at card load** —
      `SetBaseStatsEffect`'s `init` runs `contextScopedReferenceIn` (`mtg-sdk/.../scripting/values/`)
      whenever the flag is set, so a bad amount throws as the `cardDef { }` is built and `CardDiscovery`
      surfaces it, rather than reading 0 forever or blowing up mid-game. (So the snapshot-mode template
      "base power becomes target creature's power" must stay `reevaluateContinuously = false`; CR 611.2d
      also fixes X on resolution.) Counts, battlefield/zone aggregates, life totals, hand size and
      `Source`/`AffectedEntity` properties are all supported.
    - **"Your" is the source's controller**, not the affected creature's. Correct for a self-granted clause
      or a grant to a creature you control; a re-evaluated grant handed to an *opponent's* creature would
      read the granting player's hand. Keep the template to self-grants until an affected-entity-controller
      player reference exists.
    - **It applies only while the permanent is a creature** (CR 208.3a — the effect is created but does
      nothing "unless that permanent becomes a creature"). Re-asked every pass, so a Vehicle crewed later
      in the turn picks it up. Snapshot mode writes unconditionally.
    - **The quoted clause is not an ability the creature has**, so `LoseAllAbilities` can't strip it. Paper
      settles "gains '…'" against Humility by layer-6 timestamp; here it is a layer-7b floating effect with
      nothing for layer 6 to remove, so the two agree whenever the grant is the later effect (the common
      case) and diverge only when ability-removal lands afterwards. Handing out a *whole* quoted static
      instead wants `StaticAbilityHandler.lowerToContinuousEffectData`, the route `BecomeArtifactExecutor`
      already uses — not `GrantStaticAbility`, whose grants are read at points of use and never projected.
- `GrantKeyword(keyword, target, duration, condition = null)` — grant a keyword for a duration. The target may be a battlefield permanent **or a permanent spell still on the stack**: a permanent spell keeps its entity id as it resolves, so a keyword granted to `EffectTarget.TriggeringEntity` inside a "when you next cast a creature spell this turn" delayed trigger carries onto the creature the moment it enters (Summon: Brynhildr's Gestalt Mode = "it gains haste until end of turn"). On a non-permanent spell the floating effect simply never has a permanent to apply to.
  **`condition`** makes the granted keyword *conditionally live* rather than gating whether the grant happens: the grant always happens and still expires with `duration`, but the condition rides along as the floating effect's `sourceCondition` and is re-asked on every projection — the durational sibling of a printed `ConditionalStaticAbility`'s "as long as …" clause. This is how a **quoted conditional ability handed out by an animate effect** is modelled: Restless Spire's "{U}{R}: … becomes a 2/1 blue and red Elemental creature with *'During your turn, this creature has first strike.'*" is `Effects.Composite(Effects.BecomeCreature(...), Effects.GrantKeyword(Keyword.FIRST_STRIKE, EffectTarget.Self, Duration.EndOfTurn, Conditions.IsYourTurn))`. "You" resolves to the **source's projected controller**, and Layer 2 has already run when a later layer's modification is applied, so the clause correctly goes dark if another player gains control of the permanent mid-turn — and comes back if control returns. Do **not** reach for a `Duration.While…` here: those latch off permanently once their gate first fails (CR 611.2b), which is right for a "for as long as" duration and wrong for a conditional clause inside a granted ability. Also distinct from wrapping the grant in a `ConditionalEffect`, which tests the condition **once** at resolution and then grants unconditionally.
- `GrantActivatedAbilityEffect(ability, target, duration)` — one-shot grant of an `ActivatedAbility` to a **target permanent** for a duration (default `Duration.EndOfTurn`). The runtime sibling of the static `GrantActivatedAbility`: records a `GrantedActivatedAbility` keyed to the entity in `GameState.grantedActivatedAbilities`, which both ability enumerators (`ActivatedAbilityEnumerator`, and `ManaAbilityEnumerator` when the granted ability has `isManaAbility = true`) and the activation path (`ActivateAbilityHandler`) read alongside printed abilities; the grant expires in the cleanup step. **Target-general** — works on any battlefield permanent, not just creatures (the *type* of a legal target is constrained by the ability's `TargetRequirement`, so a `TargetPermanent(filter = TargetFilter.Land)` yields "target land gains …"). Glorious Sunrise's mode 2 = grant a land `ActivatedAbility(cost = AbilityCost.Tap, effect = AddMana(GREEN, 3), isManaAbility = true, timing = TimingRule.ManaAbility)` — a temporary "{T}: Add {G}{G}{G}". A granted **mana** ability must set `isManaAbility = true` or the mana enumerator won't surface it, and `timing = TimingRule.ManaAbility` beside it: the `activatedAbility { manaAbility = true }` builder derives the timing for you, but a raw `ActivatedAbility(...)` inside a grant has no builder to do it (Cryptolith Rite, Joiner Adept and Citanul Hierophants each shipped without either flag until Argentum Assay's differential reported them; Abundant Growth, Nature's Embrace, New Horizons, Huatli, Enduring Vitality and Great Divide Guide followed. `CardLinter`'s `UnflaggedManaAbility` rule — gated corpus-wide by `CardLintTest` — now fails the build on an activated ability that adds mana without the flag, so there is no third batch; its mirror `MisflaggedManaAbility` fails on the opposite mistake, an ability flagged as a mana ability that CR 605.1a disqualifies). (The always-on land-grant sibling is the static `ConditionalStaticAbility(GrantActivatedAbility(...), EnchantedPermanentMatches(Land))` — Nature's Embrace.)
- `GrantStaticAbility(ability, target, duration)` — grant a printed-shape `StaticAbility` (e.g. `CantBeBlockedByMoreThan(1)`) to a permanent for a duration. The runtime sibling of a printed static ability: unlike keyword grants (which flow through projected keywords) it is recorded as a `GrantedStaticAbility` keyed to the entity in `GameState.grantedStaticAbilities` and read **at the point of use** — combat blocker validation (`BlockPhaseManager`, CR 509.1b) consults granted `CantBeBlockedByMoreThan` alongside the creature's printed static abilities; the grant expires in the cleanup step (EndOfTurn). Compose inside `ForEachInGroup` with `EffectTarget.Self` for "each creature you control gains ..." (Full Steam Ahead = `ModifyStats(2,2)` + `GrantKeyword(TRAMPLE)` + `GrantStaticAbility(CantBeBlockedByMoreThan(1))`). `CantBeBlockedByMoreThan` (combat), `MayCastFromGraveyard` (graveyard-cast enumerator + `CastZoneResolver`, e.g. Forgotten Cellar's "cast spells from your graveyard this turn"), `PreventActivatedAbilities` (activation legality: `CastPermissionUtils.isActivationPrevented`, consulted by the ability handler and both ability enumerators), `GrantActivatedAbility` (ability legality: `CastPermissionUtils.getStaticGrantedAbilitiesWithGranter` + the `ActivateAbilityHandler` twin, read by both ability enumerators and the activation handler), and `AssignDamageEqualToToughness` (combat damage assignment: `CombatDamageUtils`, read against *final* projected P/T) are wired into read sites today; granting another `StaticAbility` kind compiles and stores but needs its own point-of-use read to take effect. Granting `AssignDamageEqualToToughness` is how an **until-end-of-turn rules modification** is expressed: because the read site re-asks per creature per damage step, using the final projected power and toughness, the affected set stays dynamic the way CR 611.2c requires of an effect that changes no characteristic — The Kingpin of Crime's "until end of turn, creatures you control with toughness greater than their power assign combat damage equal to their toughness" is `GrantStaticAbility(AssignDamageEqualToToughness(GroupFilter.AllCreaturesYouControl, onlyWhenToughnessGreaterThanPower = true), EffectTarget.Self, Duration.EndOfTurn)`, i.e. Bedrock Tortoise's printed static ability for a turn. Do **not** model that sentence as `ForEachInGroup` + `GrantKeyword(AbilityFlag.ASSIGNS_COMBAT_DAMAGE_AS_TOUGHNESS)`: that snapshots the group at resolution (right for an ability grant, wrong for a rules modification), and a floating group-flag variant would resolve its filter in layer 6, before layer 7 has applied, so it could not see a toughness pumped afterwards either. Granting a `GrantActivatedAbility` makes the holder confer the inner activated ability to its filtered group exactly like a printed one — this is how "This permanent gains 'Creatures you control have "&lt;activated ability&gt;"'" is modelled (Roar of the Fifth People, the Saga back of Huatli, Poet of Unity: `GrantStaticAbility(GrantActivatedAbility(ActivatedAbility({T}: AddManaOfChoice(ManaColorSet.Specific(R,G,W))), GroupFilter(Creature.youControl())), EffectTarget.Self, Duration.Permanent)`). A granted `PreventActivatedAbilities` behaves exactly like the printed form anchored to the grant's holder: its filter is evaluated with the holder as source, so the self-scoped `PreventActivatedAbilities(GameObjectFilter.Permanent.sourceItself())` locks the *holder's own* activated abilities — mana abilities included unless `nonManaAbilitiesOnly = true`. Pair it with `Duration.WhileAffectedTapped` ("for as long as it remains tapped", keyed to the granted-to permanent) for the Braided Net shape — "Tap another target nonland permanent. Its activated abilities can't be activated for as long as it remains tapped." = `Effects.Tap(target)` + `Effects.GrantStaticAbility(PreventActivatedAbilities(GameObjectFilter.Permanent.sourceItself()), target, Duration.WhileAffectedTapped)`. Like every "for as long as …" duration it is one-way (CR 611.2b): the read site gates per-frame, and `EndedDurationExpiryCheck` physically removes the grant the moment the permanent untaps (or leaves the battlefield), so a later re-tap does not re-lock it.
- `GrantReplacementEffect(replacement, target, duration)` — grant a printed-shape `ReplacementEffect` (e.g. `RedirectZoneChange`) to a permanent for a duration. The runtime sibling of a printed replacement effect, modelled exactly like `GrantStaticAbility`: recorded as a `GrantedReplacementEffect` (carrying the granting `controllerId`) in `GameState.grantedReplacementEffects` and read **at the point of use** — the zone-change redirect path (`ZoneMovementUtils.checkZoneChangeRedirect`) consults granted `RedirectZoneChange` alongside permanents' printed replacement effects; the grant expires in the cleanup step (EndOfTurn). Used for durational "this turn" riders such as Forgotten Cellar's "if a card would be put into your graveyard from anywhere this turn, exile it instead" (`GrantReplacementEffect(RedirectZoneChange(newDestination = Zone.EXILE, appliesTo = EventPattern.ZoneChangeEvent(filter = GameObjectFilter(controllerPredicate = ControllerPredicate.OwnedByYou), to = Zone.GRAVEYARD)))`). Two families are wired into read sites today: `RedirectZoneChange` (the zone-change redirect path above) and the **token-creation** replacements `MultiplyTokenCreation` / `ModifyTokenCount` / `CreateAdditionalToken`, which `TokenCreationReplacementHelper` reads through `ActiveReplacements.all(state)` — the enumerator that walks battlefield permanents' printed `ReplacementEffectSourceComponent` *and* `GameState.grantedReplacementEffects` together. That is how Kaya, Geist Hunter's −2 (`GrantReplacementEffect(MultiplyTokenCreation(factor = 2, appliesTo = EventPattern.TokenCreationEvent(controller = ControllerFilter.You)))`) doubles tokens for the turn: it stacks multiplicatively with a printed doubler and keeps working after Kaya leaves the battlefield, because the grant lives on `GameState` rather than on her. Granting any *other* `ReplacementEffect` kind compiles and stores but needs its own point-of-use read to take effect — add it to the read site, not a second grant store. A **resolving instant/sorcery** may also grant a *floating, controller-scoped global* replacement — `EffectTarget.Self` then resolves to the spell on the stack (not a battlefield permanent), and the grant is anchored to the caster (`context.controllerId`) so it persists after the spell leaves; the redirect read path uses only the grant's `controllerId` + the replacement's filter. Used by Malicious Eclipse's "if a creature an opponent controls would die this turn, exile it instead" (`GrantReplacementEffect(RedirectZoneChange(newDestination = Zone.EXILE, appliesTo = EventPattern.ZoneChangeEvent(filter = GameObjectFilter.Creature.opponentControls(), from = Zone.BATTLEFIELD, to = Zone.GRAVEYARD)), EffectTarget.Self, Duration.EndOfTurn)`). Note: `MayCastFromGraveyard` granted via `GrantStaticAbility` is now also read at the graveyard-cast enumerator/`CastZoneResolver`, so "you may cast spells from your graveyard this turn" is `GrantStaticAbility(MayCastFromGraveyard(filter), EffectTarget.Self, Duration.EndOfTurn)`.
- `GrantHarmonize(target, cost?, duration)` — grant **Harmonize** (CR 702.180) to a target instant/sorcery card in a graveyard. `cost` defaults to `null` = "equal to the card's mana cost" (Songcrafter Mage); pass a `ManaCost` for a fixed harmonize cost. Records a runtime `GrantedKeywordAbility` keyed to the card entity; the cast-from-graveyard enumerator, the cast handler, the alternative-payment handler (tap-for-power reduction), and the stack resolver (exile on resolution) all read printed-or-granted harmonize through the shared `HarmonizeGrants` resolver, so a granted harmonize behaves identically to a printed one. The grant expires in the cleanup step (EndOfTurn) and surfaces a "Granted Ability" badge on the card.
- `GrantFlashback(target, cost?, duration)` — grant **Flashback** (CR 702.34) to a target instant/sorcery card in a graveyard. The runtime sibling of printed `KeywordAbility.Flashback`, modelled exactly like `GrantHarmonize`. `cost` defaults to `null` = "equal to the card's mana cost" (Archmage's Newt); pass a `ManaCost` for a fixed flashback cost (e.g. `{0}` on a saddled-Mount branch). Records a runtime `GrantedKeywordAbility` keyed to the card entity; the cast-from-graveyard enumerator, the cast handler / `CastZoneResolver`, and the stack resolver (exile on resolution) read printed-**or**-granted flashback through the shared `FlashbackGrants.effectiveFlashback` resolver, so a granted flashback is castable and exiled exactly like a printed one. The grant survives the graveyard → stack move and expires in the cleanup step (EndOfTurn). Pair with `ConditionalEffect(Conditions.SourceIsSaddled, GrantFlashback(t, {0}), elseEffect = GrantFlashback(t))` for the saddled-or-not cost swap.
- `GrantEmbalm(target, cost?, duration)` — grant **Embalm** (CR 702.128) to a target creature card in a graveyard. The runtime sibling of the printed `embalm(cost)` builder helper. `cost` defaults to `null` = "equal to the card's mana cost" (Cursecloth Wrappings: "{T}: Target creature card in your graveyard gains embalm until end of turn. The embalm cost is equal to its mana cost."); pass a `ManaCost` for a fixed embalm cost. Unlike `GrantHarmonize`/`GrantFlashback` — which grant *cast* keywords and so have to reach the cast pipeline through a `GrantedKeywordAbility` record — embalm is an ordinary graveyard-**activated** ability, so this records a plain `GrantedActivatedAbility` (the object `embalmAbility(cost)` builds) keyed to the card entity, and `ZoneActivatedAbilityEnumerator` surfaces it on the card in the graveyard. Rejects a noncreature target or a card not in a graveyard. The grant expires in the cleanup step (EndOfTurn).
- `RemoveKeyword(keyword, target, duration)` — strip a keyword. Target-general, mirroring `GrantKeyword`: **any battlefield permanent, not just creatures**. That matters for the "creatures *and Vehicles*" wording — an uncrewed Vehicle is an artifact and not a creature, so a creature-only guard would silently exempt exactly the permanents such a card names (Spectacular Pileup: "All creatures and Vehicles lose indestructible until end of turn, then destroy all creatures and Vehicles" = `Patterns.Group.removeKeywordFromAll(Keyword.INDESTRUCTIBLE, GroupFilter(GameObjectFilter.CreatureOrVehicle))` then `Effects.DestroyAll(GameObjectFilter.CreatureOrVehicle)`). Removing a keyword the permanent doesn't have is a harmless no-op in projection. Note this is a real characteristic change in layer 6, *not* "destroy, ignoring indestructible" — a permanent that gains indestructible after the removal resolves keeps it.
- `RemoveAllAbilities(target, duration)` — wipe all abilities (including granted keywords).
- `ExileSpellsOnStack(opponentsOnly?, excludeSource?)` — exile matching spells directly from the stack; this is not a counter, so it works on spells that can't be countered. `excludeSource = true` models “all other spells” (Summary Dismissal).
- `CounterAllStackObjects(spells?, abilities?, opponentsOnly?)` — counter the selected kinds of stack objects, with controller scope configurable. Use `spells = false` for “counter all abilities.”
- `LoseAllCreatureTypes(target, duration)` — remove all creature subtypes.
- `SetCreatureSubtypes(subtypes, target, duration)` — replace subtypes outright.
- `AddCreatureType(subtype, target, duration)` — additive subtype.
- `AddColor(color | colors, target, duration?)` — add color(s) in addition to existing ones
  (Layer 5; default duration Permanent). Ability-applied counterpart of the `GrantColor` static.
  Pair with `AddCreatureType`/`AddCardType` for "becomes a [color] [type] in addition to its other
  colors and types" (Possessed Goat).
- `GrantHexproof(target, duration)` / `GrantShroud(target, duration)` — temporary hexproof / shroud.
  Both are facades lowering onto the player-aware `GrantEvasionKeywordEffect(keyword, target, duration)`:
  for player targets it attaches the matching player protection component; for permanents it grants the
  keyword via a Layer-6 floating effect (like `GrantKeyword`).
- `GrantExileOnLeave(target)` — "if it would leave, exile instead".
- `GrantKeywordToAttackersBlockedBy(keyword, target)` — grant keyword to creatures this blocks.

### Counters

- `AddCounters(type, count, target)` — add N counters of `type`.
- `AddDynamicCounters(type, amount, target)` — count is computed at resolution.
- `AddCountersUpTo(type, max, target)` — the effect's controller **chooses** how many (0 up to `max`, a
  `DynamicAmount` so "up to X" works) counters of `type` to put on the target, via one `ChooseNumberDecision`
  at resolution. The additive, single-kind mirror of `RemoveAnyNumberOfCounters` / `RemoveCountersUpTo`;
  placement goes through the normal `AddCounters` chokepoint, so counter-placement replacements (Hardened
  Scales) and downstream triggers (Saga chapter abilities off lore counters) fire. No-op when the target
  can't receive counters or `max` ≤ 0; choosing 0 places none. Esper Terra's "if it's a Saga, put up to three
  lore counters on it" = `ConditionalEffect(CollectionContainsMatch(CREATED_TOKENS, Enchantment.withSubtype(SAGA)),
  AddCountersUpTo(Counters.LORE, 3, PipelineTarget(CREATED_TOKENS)))`.
- **Stat counters and the layer system.** Counters whose `type` is a P/T stat counter modify power/toughness in
  layer 7c (CR 613.4c) via `EffectApplicator.applyCounters`. The symmetric pair is `Counters.PLUS_ONE_PLUS_ONE` /
  `Counters.MINUS_ONE_MINUS_ONE`; the asymmetric counters `Counters.PLUS_ONE_PLUS_ZERO` (`+1/+0`),
  `Counters.PLUS_ZERO_PLUS_ONE` (`+0/+1`), `Counters.MINUS_ONE_MINUS_ZERO` (`-1/-0`) and
  `Counters.MINUS_ZERO_MINUS_ONE` (`-0/-1`) modify only the indicated stat (Clockwork Avian's four `+1/+0`
  counters), and `Counters.PLUS_TWO_PLUS_ZERO` (`+2/+0`) / `Counters.PLUS_ZERO_PLUS_TWO` (`+0/+2`) are the
  two-step versions of those (Frankenstein's Monster). `Counters.PLUS_ONE_PLUS_TWO` (`+1/+2`, Armor Thrull),
  `Counters.PLUS_TWO_PLUS_TWO` (`+2/+2`, Soul Exchange) and `Counters.MINUS_TWO_MINUS_TWO` (`-2/-2`, Ebon
  Praetor) round out the Fallen Empires sizes. CR 122.1a defines a `+X/+Y` counter generally — X to power,
  Y to toughness, applied in layer 7c per CR 613.4c — but the engine enumerates the kinds it can sum, so
  **each printed size needs its own constant**, declared in both `CounterType` and `Counters` *and* listed in
  `CounterType.STAT_COUNTERS` so `fromName` can resolve its printed spelling. Miss that last step and the
  counter works everywhere except by name, silently. `CounterTypeStatCoverageTest` guards it.
  The **six one-step kinds** have a dedicated `CounterTypeFilter` case (`PlusOnePlusZero`, etc.) for
  `EntersWithCounters`, counter-count dynamic amounts, and counter triggers; the asymmetric sizes
  (`+1/+2`, `+2/+2`, `-2/-2`) have none and go through `CounterTypeFilter.Named("+1/+2")`. Only `+1/+1` and `-1/-1` annihilate each other as a
  state-based action (CR 122.3); the asymmetric counters never cancel.
- `DoubleCounters(type?, target?)` — one-shot doubling of the `type` counters (default `+1/+1`) already on the
  target: reads the current count and places that many more (so the total doubles). Distinct from the
  `DoubleCounterPlacement` replacement (which doubles *future* placements); the added counters still trigger
  placement replacements like Hardened Scales. No-op with zero counters. Sage of the Fang.
- `DoubleAllCounters(target?)` — the every-kind twin of `DoubleCounters`: "double the number of each kind of
  counter on target permanent" (`DoubleCountersEffect` with `counterType = null`). Every kind currently on the
  target is snapshotted first, then doubled as its own placement, so per-kind replacements apply and counters
  added by this effect are never re-doubled. No-op when the target has no counters at all. Zimone, Paradox
  Sculptor.
- `GrantCounterPlacementModifier(modifier?, duration?, counterType?, recipient?)` — install a **temporary,
  duration-scoped, controller-scoped** counter-placement modifier: the activated/spell-granted analogue of the
  static `ModifyCounterPlacement` replacement (Hardened Scales). While active, if the *controller* of the effect
  would put `counterType` counters (default `+1/+1`) on a recipient matching `recipient` (default
  `RecipientFilter.CreatureYouControl`, resolved relative to that controller), `modifier` additional counters
  (default `+1`) are placed instead. Recorded in a turn-scoped store on the game state and consulted from the
  single counter-placement chokepoint (so every AddCounters-style effect honors it); expires per `duration`
  (default `Duration.EndOfTurn`) via end-of-turn cleanup. Negative `modifier` reduces (floored at 0). Prairie Dog
  (OTJ): "{4}{W}: Until end of turn, if you would put one or more +1/+1 counters on a creature you control, put
  that many plus one +1/+1 counters on it instead." → `GrantCounterPlacementModifier()` with all defaults.
- `RemoveCounters(type, count, target)` — remove N counters.
- `RemoveAnyNumberOfCounters(target)` — player removes 0 or more (one prompt per counter kind, no total cap).
- `RemoveCountersUpTo(maxCount, target)` — player removes **up to `maxCount` counters total across all
  kinds**. The budget-capped form of `RemoveAnyNumberOfCounters` — the *same* `RemoveAnyNumberOfCountersEffect`
  with `maxTotal` set, not a separate effect: one `ChooseNumber` prompt per kind, each capped at
  `min(kind's count, remaining budget)`; prompting stops once the budget is spent. Used by Heartless Act's
  "Remove up to three counters from target creature."
- `RemoveCounterOfAnyKind(target, count = 1)` — "**remove a counter** from it": the player chooses which
  *kind* comes off, but not *whether*. The floored form of the same effect (`minTotal = maxTotal = count`).
  Each prompt's minimum is the share of the floor the kinds still to come can't cover, so the choice stays
  free while the floor is reachable and is enforced on the last kind that can pay it; a prompt whose min and
  max coincide is applied without asking, so a permanent carrying a single kind raises no prompt at all.
  **Reach for this, not `RemoveCountersUpTo(1, …)`, whenever a payoff hangs off the removal** — `IfYouDo` /
  `ReflexiveTriggerEffect` ("remove a counter … when you do, …", Leatherhead, Swamp Stalker). A bare ceiling
  lets the player answer 0 to every prompt and still report success, firing the payoff for free against
  CR 603.12. Use `RemoveCountersUpTo` only where the card really does say "up to".
- **Player-scoped counters (CR 122.1, 107.14).** Every counter type above lives on a permanent/object. Poison,
  energy, and rad counters instead live directly on a **player entity**, reusing the same `CountersComponent` —
  no separate component or data model. `AddCountersExecutor` already resolves player-shaped targets (`that
  player gets two poison counters`, Virulent Silencer), so a fixed grant needs no new vocabulary at all.
  - `GetEnergy(amount, target = Controller)` — sugar for `AddCounters(Counters.ENERGY, amount, target)`. "You get
    {E}{E}{E}" (three energy counters, CR 107.14) = `GetEnergy(3)`.
  - `PayCounters(counterType, player = Player.You, storeAmountAs)` — a player pays any amount of `counterType`
    counters they currently have (CR 107.14's "pay {E}" generalized to a player-chosen amount and to any
    player-scoped counter kind). One `ChooseNumberDecision` (0..their current total; no prompt at all when they
    have zero); paying is always optional down to 0. The paid amount is removed and stored in the pipeline under
    `storeAmountAs`, readable downstream via `VariableReference(storeAmountAs)` — same convention as
    `DrawUpTo.storeNotDrawnAs`. Galvanic Discharge (MH3): `Composite(GetEnergy(3), PayCounters(Counters.ENERGY,
    storeAmountAs = "paid"), DealDamage(VariableReference("paid"), target))`. Paying 0 is legal and does nothing
    (2024-06-07 ruling: "You may pay zero {E}... won't deal any damage"); if the spell's target becomes illegal
    before resolution the whole spell fizzles per the normal CR 608.2b check, so no energy is gained either.
  - `PayFixedCounters(counterType, amount, player = Player.You)` — the all-or-nothing counterpart to
    `PayCounters`: pays an exact `amount`, not a chosen one. No decision of its own — designed as the `action`
    half of a `ReflexiveTriggerEffect` ("you may pay {E}{E}{E}. **When** you do, ...", CR 603.12 — a fresh
    triggered ability with its own targets, distinct from a same-ability "**If** you do" continuation), where
    the reflexive's own yes/no *is* the payment decision. Fails outright (no partial removal) if the payer has
    fewer than `amount` — per the 2024-06-07 {E} ruling, "you can't pay that amount multiple times to multiply
    the effect... you simply choose whether or not to pay". `ReflexiveTriggerEffectExecutor.isActionFeasible`
    recognizes `PayFixedCountersEffect` and checks the payer's current total before ever offering the "may pay"
    prompt, mirroring how it already gates `SacrificeEffect` — so the prompt never appears when unaffordable,
    it doesn't appear-then-fail. Guide of Souls (MH3): `ReflexiveTriggerEffect(action =
    PayFixedCounters(Counters.ENERGY, 3), reflexiveEffect = AddCounters(PLUS_ONE_PLUS_ONE, 2, ContextTarget(0))
    .then(AddCounters(FLYING, 1, ContextTarget(0))).then(AddCreatureType("Angel", ContextTarget(0))),
    reflexiveTargetRequirements = [Targets.AttackingCreature])`.
  - `DynamicAmount.PlayerCounterCount(counterType, player = Player.You)` / `DynamicAmounts.playerCounterCount(...)`
    — how many counters of `counterType` a player currently has; the player-scoped sibling of
    `EntityProperty(entity, CounterCount(filter))` (which has no case for "a player" — `EntityReference` only
    resolves permanents/objects). `DynamicAmounts.energyCount(player)` is sugar for the energy case — "where X is
    the number of energy counters you have" (Longtusk Cub, Electrostatic Pummeler).
- `ConvertCountersToTokensEffect(counterType = +1/+1, tokenFactory)` — "remove any number of `counterType`
  counters from this permanent; for each removed, create one token." Prompts for `0..(count on source)`,
  removes that many, then mints exactly that many tokens from `tokenFactory` (its own `count` is ignored).
  Set `tokenFactory.stampCreator = true` to make the minted tokens recognizable later. The
  counters→tokens half of Tetravus; the reverse (exile any number of those tokens, add that many counters
  back) **composes** from a pipeline — `gather(CardSource.BattlefieldMatching(filter = ….createdBySource(),
  player = You))` → `chooseAnyNumber` → `exile` → `run(AddDynamicCounters("+1/+1",
  VariableReference("<slot>_count"), Self))` — so no dedicated token→counters effect exists.
- `RemoveAllCounters(target)` — wipe every counter.
- `RemoveAllCountersOfType(type, target)` — wipe one kind.
- `MoveAllLastKnownCounters(target)` — Hooded Hydra / Essence Channeler — move every counter kind from source's
  last-known state. Reads the dies/leaves trigger map (`triggerLastKnownCounters`) first, falling back to the
  cost-sacrifice map (`lastKnownSourceCounters`) so it also works from an activated ability whose source was
  sacrificed/exiled as a cost (Zack Fair).
- `MoveCountersEachKindMissing(source, destination)` — Goldberry, River-Daughter (ability A) — for each counter
  kind on `source` that `destination` does not already have, move one of that kind from `source` onto
  `destination`. Deterministic, no player choice; kinds the destination already has are left untouched.
- `MoveChosenCountersToTarget(source, destination, drawCardOnMove?)` — Goldberry, River-Daughter (ability B) —
  player chooses how many of each kind to move from `source` onto `destination` (one `ChooseNumberDecision` per
  kind). When `drawCardOnMove` is true, the controller draws a card if any counter was moved ("if you do, draw").
- `MoveCounters(counterType, amount, source, destination)` — Tester of the Tangential — deterministic,
  count-carrying move of a single counter kind: moves `amount` (a `DynamicAmount`, e.g. `DynamicAmount.XValue`
  from a may-pay-{X} reflexive) of `counterType` from `source` onto `destination`. The count is capped at the
  number actually on `source`, and adding to `destination` honors counter-placement replacement effects
  (Hardened Scales). No-op when source/destination missing, they're the same permanent, amount ≤ 0, or source has
  none of that kind. The count-fixed counterpart to the interactive `MoveChosenCountersToTarget`.
- `Counters.ANY` — wildcard counter-type string for "counters of any type" triggers/events (e.g.
  `Triggers.countersPlacedOn`); not a real placeable counter, only a matcher sentinel.
- **Passive named counters** — flavor counters with no inherent rule; the card that uses one accumulates
  it (`AddCounters(Counters.X, …)`) and reads the count via `Conditions.SourceCounterCountAtLeast(Counters.X, …)`
  or `DynamicAmounts.countersOnSelf(…)`, and may spend it as a cost (`Costs.RemoveCounterFromSelf(Counters.X, …)`).
  Add a new one to both `enum class CounterType` and `object Counters` (SDK) plus the client's passive-counter
  wiring (`PASSIVE_COUNTER_TYPES`, `passiveCounterBadgeStyle`, `counterManaClass`, `CounterTypeDisplayNames`);
  keep it out of `StateProjector.KEYWORD_COUNTER_MAP` since it grants no keyword. Recent examples:
  `Counters.LANDMARK` (Treasure Map — three flip it into Treasure Cove), `Counters.DREAD` (Grasping Shadows —
  three flip it into Shadows' Lair), `Counters.BORE` (Brass's Tunnel-Grinder — three flip it into Tecutlan),
  `Counters.REVIVAL` (Nine-Lives Familiar — a "lives left" counter: it enters with eight if you cast it and its
  dies trigger reads the last-known count to come back with one fewer),
  `Counters.JUDGMENT` (Faithbound Judge // Sinner's Judgment — both faces count to three, the
  creature face to shed defender and the Aura face to make the enchanted player lose the game),
  `Counters.NET`, `Counters.FIRE`, `Counters.CONQUEROR`, `Counters.POINT` (Contested Game Ball — its
  `{2}, {T}` ability adds one per activation and, when five or more are present, sacrifices the artifact and
  creates a Treasure), `Counters.WISH` (Wishclaw Talisman — see below), `Counters.INGENUITY` (Lady Octopus,
  Inspired Inventor — her first/second-draw triggers each add one and her `{T}` ability caps the mana value of
  the hand artifact she free-casts at the count via `CollectionFilter.ManaValueAtMost(DynamicAmounts.countersOnSelf(
  CounterTypeFilter.Named(Counters.INGENUITY)))`), `Counters.FILM` (Peter Parker's Camera — enters with
  three via `EntersWithCounters(CounterTypeFilter.Named(Counters.FILM), count = 3, selfOnly = true)` and its
  `{2}, {T}` copy ability spends one per activation via `Costs.RemoveCounterFromSelf(Counters.FILM, 1)`),
  `Counters.PLAN` (MSH's Plan enchantment cycle — each Plan's own "whenever …" trigger adds one, and a
  second `Triggers.countersPlacedOn(filter = GameObjectFilter.Any, counterType = Counters.PLAN,
  firstTimeEachTurn = false, binding = TriggerBinding.SELF)` ability gated on
  `triggerRestriction = Conditions.SourceCounterCountAtLeast(Counters.PLAN, N)` models "when the Nth plan
  counter is put on this enchantment"; exact because every payoff sacrifices its own source, so the
  at-least gate can never fire a second time — no dedicated "Nth counter" trigger event is needed),
  `Counters.INVASION` (Alien Invasion — a tally its begin-combat trigger reads via
  `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.INVASION))` to size the +1/+1 counters
  on the Alien token it just made, then increments),
  `Counters.UNLOCK` (MKM — Cryptex: its `{T}`, collect-evidence-3 **mana** ability adds one per activation
  and its `Costs.SacrificeSelf` ability is gated on
  `ActivationRestriction.OnlyIfCondition(Conditions.SourceCounterCountAtLeast(Counters.UNLOCK, 5))`; the
  rider on a mana ability doesn't change its classification — CR 605.1a asks whether the ability could
  add mana, whether it targets, whether it is a loyalty ability, and whether its cost or effect moves a
  card to or from a library; an unlock counter is none of those, so the counter lands at a moment nobody
  can respond to).
- `DistributeCountersFromSelf(type?, count?)` — split source's counters among creatures you control.
- `DistributeCountersAmongTargets(total, type?, minPerTarget?)` — divvy N counters among chosen
  targets. `total` is a `DynamicAmount` (an `Int` overload wraps it in `Fixed`), evaluated once at
  resolution, so the pool can be the spell's X. For "distribute X counters among **any number** of
  target creatures" (Grove's Bounty) pair it with
  `TargetObject(unlimited = true, dynamicMaxCount = DynamicAmount.XValue)` — the dynamic cap
  outranks `unlimited` in `TargetValidator`, enforcing CR 601.2d (no more targets than counters).
- `DistributeCountersAmongFiltered(total, type?, filter, minPerTarget?)` — distribute N **new** counters among permanents matching `filter`, chosen at resolution (not the spell's targets); `minPerTarget = 0` models "among any number of". Unlike `DistributeCountersFromSelf` nothing is removed from a source. Crashing Wave: `DistributeCountersAmongFiltered(3, Counters.STUN, Filters.Creature.tapped().opponentControls())` — "distribute three stun counters among any number of tapped creatures your opponents control."
- `Proliferate(target?)` — give one more counter of each kind already there. With no argument it is
  proliferate proper (CR 701.34): the controller chooses any number of permanents and/or players with
  a counter when the effect *resolves*, nothing is targeted. Pass a `target` for the targeted
  single-object form — "for each kind of counter on target permanent or player, give that permanent or
  player another counter of that kind" (Powerful Broker): the recipient is a real target, so it is
  announced with the spell/ability, respects hexproof/shroud, and the ability is countered if that
  target is illegal on resolution (CR 608.2b). Pair it with `Targets.PermanentOrPlayer`. Both forms
  place counters identically: replacement effects honored, placement attributed to the controller,
  and a recipient that "can't have counters put on it" (Blossombind) skipped.
- `AddCountersToCollection(name, type, count)` — add counters to cards held in a pipeline collection.
  An overload takes a `DynamicAmount` instead of an `Int` count, evaluated once at resolution — "create
  a token, then put X +1/+1 counters on it, where X is …" over the `CREATED_TOKENS` collection (Emil,
  Vastlands Roamer).

### Color & type

- `AddCardType(type, target, duration)` — add a card type (e.g. become an artifact). Also takes a **supertype** — `AddCardType("LEGENDARY", …)` for "it becomes a legendary Spider Hero" (Origin of Spider-Man) — because the layer projection stores supertypes in the same type set; there is no separate `AddSupertype`.
- `AddSubtype(subtype, target, duration)` — add a subtype temporarily.
- `SetLandType(landType, target, duration, fromChosenValueKey)` — target land *becomes* the basic land type, **replacing** its existing land subtypes (Rule 305.7); pass `fromChosenValueKey` to read the type from a preceding `ChooseOption(OptionType.BASIC_LAND_TYPE)`. One-shot counterpart to the `SetEnchantedLandType` aura static ability. (Dream Thrush)
- `ChooseColorForTarget(target)` — target picks a color; stored in context.
- `BecomeChosenManaColor(target)` — adopt the previously chosen color.
- `ChangeColor(colors, target, duration)` — replace colors with the given set.
- `BecomeAllColors(target, duration)` — five-color until end of turn.
- `ChangeColorToChosen(target, duration)` — replace the target's colors with the single color picked
  by a preceding `ChooseColorThen` (read from `EffectContext.chosenColor`). The target may be a
  **spell on the stack** or a permanent — the color projection reads the recolored entry in both
  zones, so a recolored spell's new color drives color-matching checks (e.g. protection) during
  resolution. Compose as `ChooseColorThen(then = ChangeColorToChosen(target))` for "target ...
  becomes the color of your choice" (Blind Seer).
- `ChangeWordInText(target, duration)` — Layer-3 text change: the player picks one **color word**
  or **basic land type** on the target and a replacement of the same category, recorded as a
  `TextReplacement` on the target. A basic-land-type swap flows through the projected type line, so
  the land's mana (via `IntrinsicManaAbilities`), landwalk relevance, and type checks all follow
  automatically (Forest→Island taps for `{U}`); a color-word swap rewrites protection-from-color and
  `HasColor`/`NotColor` filters. `duration = EndOfTurn` is stripped at cleanup; `Permanent` is the
  Artificial-Evolution-style indefinite change. The player picks the FROM and TO words on **one
  screen** (a `ChooseReplacementDecision`), with words **present on the target** surfaced first
  (labeled "On <card>") so a no-op pick is discouraged, and a live `from → to` preview. (Crystal Spray)

### Mana

- `AddMana(color, amount, restriction?, expiry?, riders?)` — add N of one color. `expiry` is a
  `ManaExpiry` (default `END_OF_TURN`); set `END_OF_COMBAT` for firebending-style combat-duration mana
  that the pool keeps through combat and discards when combat ends. Combat-duration mana is stored as
  an `AnySpend` restricted entry (so it spends like any other mana) and cleared by
  `CombatManager.endCombat`. See [ManaExpiry](#manaexpiry). `riders` is a `Set<ManaSpellRider>`
  applied to whatever spell this mana ends up paying for (Pyromancer's Goggles tags its {R} with
  `CopySpellWhenSpent`); as with `AddManaOfChoice`, riders without a `restriction` are stored under
  `ManaRestriction.AnySpend` so the rider survives in the pool while the mana stays spendable on
  anything. See [ManaSpellRider](#manaspellrider).
- `AddColorlessMana(amount, restriction?)` — add colorless.
- `RetainUnspentMana(vararg colors)` — "Until end of turn, you don't lose unspent mana of these colours
  as steps and phases end." The colour-filtered, single-player, turn-scoped one-shot cousin of the
  permanent-static `PreventManaPoolEmptying` (Upwelling, which stops *all* emptying for *everyone*).
  Confers a `RetainUnspentManaComponent` on the resolving controller; at every step/phase-end mana
  emptying (CR 500.5, `CleanupPhaseManager.emptyManaPools` → `ManaPoolComponent.emptyAtBoundary(...)`)
  the kept colours survive while everything else empties, until the marker clears at end-of-turn
  cleanup. The Last Agni Kai (`RetainUnspentMana(Color.RED)`).
- `AddManaOfChoice(colorSet, amount?, restriction?, riders?, recipient?)` — **unified primitive.** Add N mana of one color the controller picks from a resolved [ManaColorSet](#manacolorset). All "any-color from a constrained pool" cards (any color, commander identity, among permanents, lands could produce, source-chosen color) are expressed as this effect plus a different `ManaColorSet`. `riders` is a `Set<ManaSpellRider>` consumed when the mana pays for a spell (e.g. Path of Ancestry tags its mana with `ScryOnSharedTypeWithCommander`); when riders are set without a `restriction`, the engine stores the entries under `ManaRestriction.AnySpend` to preserve the rider through the pool. `recipient` is an `EffectTarget` naming **whose pool** the mana lands in — it defaults to `EffectTarget.Controller` (the only shape a mana ability can have, CR 605.1a) and takes a chosen target for "**target player** adds …" (Radiant Lotus). Only the pool moves: the *colour* is still chosen by the ability's controller, because "Choose a color" names no other chooser. An ability with a non-default `recipient` targets, so by definition it isn't a mana ability — it uses the stack, can be responded to, and the mana arrives in the recipient's pool at resolution (and empties at end of step like any other mana, CR 500.4).
- `AddAnyColorMana(amount?, restriction?)` — sugar for `AddManaOfChoice(ManaColorSet.AnyColor, amount)`. "Add N mana of any **one** color" (Gilded Lotus): one chosen color, N of it. For "any **combination** of colors" use `AddManaInAnyCombination`.
- `AddManaOfChosenColor(amount?)` — sugar for `AddManaOfChoice(ManaColorSet.SourceChosenColor, amount)`.
- `AddManaOfColorAmong(filter)` — sugar for `AddManaOfChoice(ManaColorSet.AmongPermanents(filter))`.
- `AddManaOfColorAmongGraveyard(filter)` — one mana of any color among cards in your graveyard matching
  `filter` (reads each card's base colors; sugar for `ManaColorSet.AmongCardsInGraveyard(filter)`). The
  Grey Havens ("any color among legendary creature cards in your graveyard").
- `AddManaOfColorAmongLinkedExile()` — one mana of any color among the cards currently exiled *with*
  the source permanent (its `LinkedExileComponent`, still in the exile zone; reads each card's base
  colors; sugar for `ManaColorSet.AmongLinkedExiledCards`). Pit of Offerings ("any of the exiled
  cards' colors"). Pair with a `MoveToZoneEffect(linkToSource = true)` that records the exiled pile.
- `AddManaOfColorLandsCouldProduce(scope)` — sugar for `AddManaOfChoice(ManaColorSet.LandsCouldProduce(scope))`. Fellwar Stone / Exotic Orchard / Reflecting Pool shape.
- `AddManaOfColorInCommanderColorIdentity()` — sugar for `AddManaOfChoice(ManaColorSet.CommanderIdentity)`. Arcane Signet / Command Tower shape.
- `AddAnyColorManaSpendOnChosenType(typeName)` — mana that can only pay for a specific card type (kept separate because it derives a runtime [ManaRestriction] from the source's chosen subtype).
- `AddDynamicMana(amount, allowedColors, restriction?)` — split X across a fixed color set, distinct from `AddManaOfChoice` because it distributes the full X total across multiple colors rather than producing X copies of one chosen color.
- `AddManaInAnyCombination(amount, allowedColors?, restriction?)` — "Add N mana in any combination of colors" (Wizard's Rockets, Thornvault Forager, Interdimensional Web Watch). Sugar for `AddDynamicMana`; `allowedColors` defaults to all five. The controller colors **each** pip independently at resolution (3+ colors → pip-by-pip color choice; 2 colors → one "how much of the first" prompt; ≤0 → no mana, no prompt), so the result can mix colors — distinct from `AddAnyColorMana`, where all N share one color.
- `AddOneManaOfEachColorAmong(filter)` — one mana of *each* color found among matching permanents (Bloom Tender shape).
- `AddOneManaOfEachCraftedMaterialColor()` — one mana of *each* printed color among the exiled cards used to craft the source (`AddOneManaOfEachColorAmongEffect(colorSource = ManaColorSource.CraftedMaterials)`; Sunbird Effigy).
- `PayDynamicMana(amount, payer?, color?)` — pay a dynamically-computed amount of mana at resolution; the
  dynamic, payer-parametric twin of the flat `PayManaCostEffect`. `amount` is a [DynamicAmount](#dynamicamount)
  evaluated at resolution (0 pays nothing and succeeds); `payer` is a `Player` reference defaulting to the
  controller (`Player.You`). `color` defaults to null (pay `amount` **generic** mana); set it to a `Color` to pay
  `amount` copies of that **colored** symbol instead — `color = Color.GREEN` → `{G}{G}…`, for "pay {G} for each
  wind counter" (Cyclone). Affordability and the prompt label honor the color. This is the building block for **"pay {N} for each X"** templating — pair it with a
  pipeline selection and read the selection size via `DynamicAmount.Multiply(DynamicAmount.VariableReference("<collection>_count"), N)`
  — and for **"that player pays"** on each-player triggers (`payer = Player.TriggeringPlayer`, the only effect that
  charges a player other than the ability's controller). Affordability is recognized by `Gate.MayPay`, so wrapping
  it in a may-pay gate skips the prompt when the payer can't afford the computed cost, and the gate's "yes" button
  renders the *computed* total ("Pay {8}"), not the formula. When the amount scales with an upstream selection,
  put `SelectionRestriction.MaxAffordablePayment(manaPerSelected, payer)` on the selection so the player can't pick
  a set they can't pay for. Magnetic Mountain ("pay {4} for each tapped blue creature chosen, untap them") composes
  all three: the capped selection, then the dynamic cost in a `Gate.MayPay` whose `decisionMaker` is the same
  `Player.TriggeringPlayer`.
- `PayRepeatedly(cost, upTo?, storeCountAs?)` → `PayManaCostRepeatedlyEffect` — **"pay {1} up to three times"**
  / "pay {2}{R} any number of times", the repeatable optional payment whose payoff scales with the number of
  repetitions (Hawkeye, Master Marksman; Tranquil Frillback; the Adversary cycle). The payer names a count and
  pays `cost * n` in one auto-tapped payment; the count is published to the resolution pipeline under
  `storeCountAs` (default `"times_paid"`), read back with `DynamicAmounts.timesPaid()`.
  - **The offered ceiling is affordability-aware and color-aware.** The cap is the smaller of `upTo` (null =
    "any number of times", bounded only by available mana) and how many repetitions the payer can actually make,
    tested as `cost * n` — `{G}` three times needs three *green*, not three mana. The probe is the *same*
    auto-tap predicate the payment itself uses (floating mana, then `ManaSolver.solve`), **not**
    `ManaSolver.canPay`: `canPay` also counts mana the auto-tapper refuses to spend for you (sacrificing a
    Treasure, Springleaf Drum's tap-a-creature sub-cost), so capping with it would offer a count the payment
    then errors on. Because the two agree, the prompt never offers a payment that fails mid-resolution.
  - **The floor is one repetition, not zero — declining belongs to the wrapper.** Wrap it in
    `ReflexiveTriggerEffect(action = …, optional = true)` for the printed "you **may** pay … **when you do**"
    (CR 603.12; and per CR 603.12a the reflexive half triggers **once**, not once per repetition) or in a
    `Gate.MayPay` for "if you do" — `Gate.MayPay` scores this cost's affordability, so the gate's "yes" is
    absent when even one repetition is unaffordable. A payer who can't afford even one repetition gets a
    *failure*, which is what keeps the reflexive half from firing; `ReflexiveTriggerEffectExecutor.isActionFeasible`
    scores it up front so the may-question isn't raised at all in that case. A cap of exactly 1 pays without a
    prompt — the payer has already consented and there is nothing left to choose.
  - **Why a stored number rather than an X value:** an effect result carries no X channel. A sub-effect hands its
    outputs back up as collections, stored numbers and chosen values — that is the whole list — so there is no
    way for this effect to *write* an X for the reflexive ability to read. (A trigger's own `xValue` does survive
    the CR 603.12 round-trip, riding the carried trigger context; it just isn't settable from here.) Feeding the
    stored count to a modal's `dynamicChooseCount` is the "choose up to **that many** —" payoff (Hawkeye), and it
    is equally at home in "put **that many** +1/+1 counters" (`DynamicAmounts.timesPaid()` into `AddCounters`).
  - Distinct from `Gate.MayPayX`, which is a single *variable-size* generic payment binding X: no cap, no repeat
    unit, no colored pips. **Mind the opposite floor conventions** when picking between them: `MayPayX` prompts
    `0..max` and reads 0 as "decline", while `PayRepeatedly` prompts `1..cap` because the wrapper already asked
    the decline question. Reach for `MayPayX` when the payment *is* X; for `PayRepeatedly` when a fixed unit —
    especially a colored one — repeats.

### Tokens & emblems

- `CreateToken(p, t, colors?, creatureTypes, keywords?, count?, controller?, imageUri?, name?, legendary?, tapped?, artifactToken?, enchantmentToken?, staticAbilities?, exileAtStep?, sacrificeAtStep?)` — make N creature tokens.
  `exileAtStep: Step?` arms the exile counterpart of the delayed trigger described below. Both the fixed
  and dynamic-count overloads expose both delayed zone-change options.
  `sacrificeAtStep: Step?` arms a delayed trigger that sacrifices each created token at the beginning of the next
  step of that kind — the "create …, sacrifice it at the beginning of the next end step" rider (Harried Dronesmith
  passes `Step.END`; because its ability triggers at the beginning of combat on the controller's own turn, "your
  next end step" *is* the plain next END step and needs no controller's-turn narrowing). The plain-token sibling of
  `CreateTokenCopyOfTarget`'s parameter of the same name.
  `name` is for the *named* tokens a card's text calls out — "create The Tiger God, a legendary 4/4 green
  Cat God creature token" (White Tiger, Ava Ayala). Omit it and the token is named after its creature
  types, which is what an ordinary "create a 1/1 white Soldier creature token" wants. Pair it with
  `legendary = true` when the printed token is legendary: the name is what the legend rule keys on, so
  without it two copies would each leave a token on the battlefield.
  `artifactToken = true` makes them **artifact** creatures and `enchantmentToken = true` makes them **enchantment**
  creatures (both may be set at once); the extra card type is unioned onto the token's `Creature` type line (e.g.
  Duskmourn's Glimmer cards create a "1/1 white Glimmer enchantment creature token" via `enchantmentToken = true`).
  `count` accepts an `Int` or a `DynamicAmount` (the latter for "create X tokens" wording — e.g. Verdeloth the
  Ancient passes `count = DynamicAmount.XValue` to make X Saprolings when kicked); both count overloads accept
  `tapped` and `staticAbilities` (The Final Days creates a `DynamicAmount.Conditional` number of **tapped** Horror
  tokens; Song of Totentanz creates `DynamicAmount.XValue` Rats carrying `CantBlock()`). Publishes the created token
  entity IDs to the `CREATED_TOKENS` pipeline collection, so a sibling effect in a `CompositeEffect` can address
  each token via `EffectTarget.PipelineTarget(CREATED_TOKENS, index)` — e.g. Mardu Monument grants menace and haste
  until end of turn to each of its three freshly-created Warriors with one `GrantKeyword` per token. For a *named*
  token (creature or otherwise) with its own abilities — Treasure, Munitions, Cragflame — add a `CardDefinition`
  to `PredefinedTokens.kt` and expose an `Effects.Create<Name>Token()` facade that wraps
  `CreatePredefinedTokenEffect("<Name>", count)`. The predefined-token registry already supports noncreature type
  lines (e.g. Munitions' `typeLine = "Artifact"`) and embedded triggered abilities. For a count computed at
  resolution rather than a fixed integer, pass `dynamicCount = <DynamicAmount>` instead of `count` — the executor
  evaluates it (coerced to ≥ 0) and creates that many tokens (Lobelia Sackville-Baggins, LTR: "create X Treasure
  tokens, where X is the exiled card's power", via `DynamicAmount.EntityProperty(Target(0), Power)`).
  **Colored** predefined tokens (a token has no mana cost, so its printed color is a color indicator, CR 204):
  set `colorIdentity = "<symbols>"` on the token's `CardDefinition` — both predefined-token executors read
  `colorIdentityOverride ?: colors` for the token's color (a bare `colors` is mana-cost-derived and would be
  colorless). Example: the `Frog` token (`PredefinedTokens.Frog`, a 1/1 green Frog created by Quina, Qu Gourmet).
  For an *inline* token (not a registered `CardDefinition`) that has its own abilities, the facade's
  `staticAbilities?` parameter covers the common static-only case (e.g. a token with "This token can't
  block" — Broodrage Mycoid's `CantBlock(GroupFilter.source())`). For `triggeredAbilities` **and**
  `activatedAbilities`, drop to the raw `CreateTokenEffect` constructor, which exposes all three —
  each list is granted to every created token at resolution (permanent
  duration) via `GameState.granted{Static,Triggered,Activated}Abilities`, so the legal-action
  enumerator and `ActivateAbilityHandler` pick them up like any other granted ability. Example:
  Mourner's Surprise's "1/1 red Mercenary creature token with \"{T}: Target creature you control
  gets +1/+0 until end of turn. Activate only as a sorcery.\"" passes a single
  `ActivatedAbility(cost = AbilityCost.Tap, effect = Effects.ModifyStats(1, 0), targetRequirements =
  listOf(Targets.CreatureYouControl), timing = TimingRule.SorcerySpeed)`. (Remember a token is a
  creature, so a `{T}` ability is summoning-sick the turn the token enters.) The raw constructor also
  exposes `stampCreator: Boolean` — when true each minted token records the creating permanent (the
  effect's source) so later abilities can recognize "tokens created with this permanent" via the
  `StatePredicate.CreatedBySource` filter (`.createdBySource()`). Tetravus uses it to reabsorb only the
  Tetravite tokens it minted; off by default. **`Effects.CreateTokenCopyOfTarget(…, stampCreator = …)`
  carries the same flag** — the copy path needs the same provenance for the same reason, and Dance of
  Many's "when this enchantment leaves the battlefield, exile the token" is unimplementable without
  it: two Dances on the battlefield mint indistinguishable copies, and each has to exile only its own.
- `CreateDynamicToken(dynamicPower, dynamicToughness, colors?, creatureTypes, keywords?, count?, controller?, imageUri?)` —
  tokens whose P/T is computed at resolution (e.g. Pure Reflection's X/X Reflection where X = the cast spell's mana
  value, via `DynamicAmounts.triggeringManaValue()`). `controller` directs who gets the token (e.g.
  `EffectTarget.PlayerRef(Player.TriggeringPlayer)` for "that player creates …"); `imageUri` sets custom token art.
- `CreateTokenOfChosenColorAndType(dynamicPower, dynamicToughness, count?)` — a token whose **color and
  creature type are the ones the source locked into its cast-choice slots** (`ChoiceSlot.COLOR` /
  `ChoiceSlot.CREATURE_TYPE`), read off the source's `CastChoicesComponent` at resolution. Riptide
  Replicator: "create an X/X creature token of the chosen color and type." (Replaces the old one-off
  `CreateChosenTokenEffect`; under the hood it sets `CreateTokenEffect.colorsFromChoice` /
  `creatureTypesFromChoice`.)
- `CreateTokenCopyOfSelf(count?, overridePower?, overrideToughness?, removeLegendary?)` — token copies
  of the source. `removeLegendary = true` applies the "except it's not legendary" copy clause (Ran and
  Shaw), mirroring `CreateTokenCopyOfEquippedCreature`.
- `CreateTokenCopyOfTarget(target, count?, overridePower?, overrideToughness?, tapped?, attacking?, triggeredAbilities?, addedKeywords?, addedSupertypes?, removedSupertypes?, overrideColors?, addedColors?, overrideSubtypes?, addedSubtypes?, overrideCardTypes?, activatedAbilities?, addedStaticAbilities?, sacrificeAtStep?, sacrificeOnlyOnControllersTurn?, addCardTypes?, exileAtStep?, exileUnlessSourceIsRingBearer?, controller?, noManaCost?)` —
  token copy of another permanent (or a card in any zone — the executor copies the target's `CardComponent`,
  so a graveyard/exile card works; pass `EffectTarget.PipelineTarget("name")` to copy a card a prior pipeline
  step exiled/stored, as Nexus of Becoming and Mardu Siegebreaker do).
  `overrideColors`/`overrideSubtypes` replace the copy's colors/subtypes
  outright for "a token that's a copy … except it's a 5/5 black Demon" wording (Ardyn, the Usurper).
  `addedColors` *unions* extra colors onto the copy (vs `overrideColors` which replaces; ignored when
  `overrideColors` is set) — e.g. The Jolly Balloon Man's "a 1/1 red Balloon creature in addition to its
  other colors and types".
  `addedSubtypes` *unions* extra subtypes onto the copy (vs `overrideSubtypes` which replaces) — e.g.
  Nexus of Becoming's "a 3/3 Golem … in addition to its other types".
  `noManaCost = true` gives the copy **no mana cost** (mana value 0) instead of the copied card's — the
  "except it … has no mana cost" clause on Embalm (CR 702.128a) and Eternalize; per the Cursecloth Wrappings
  ruling that is itself a copiable value, so anything copying the token also sees mana value 0.
  `addCardTypes` (e.g. `setOf("ARTIFACT")`) *unions* extra card types onto the copy's type line for the
  "except it's a [type] in addition to its other types" clause (the targeted sibling of
  `CreateTokenCopyOfSource`'s `addCardTypes`; Molten Duplication, Nexus of Becoming).
  `overrideCardTypes` replaces the copy's card types outright — "it's a Food artifact … and it loses all
  other card types" → `setOf(CardType.ARTIFACT)`; `activatedAbilities` grants extra activated abilities to
  the copy (the Food "{2}, {T}, Sacrifice this token: You gain 3 life") — together model Shelob, Child of
  Ungoliant's death-trigger Food token.
  `addedStaticAbilities` grants extra static abilities to the copy — the "except it has \"[static ability]\""
  copy clause — via `GameState.grantedStaticAbilities` (the same channel as `activatedAbilities`, since
  tokens have no `CardDefinition`). Firion, Wild Rose Warrior's token copy of an entering Equipment adds
  `ReduceEquipCost(amount = 2, onlyOwnEquip = true)` ("This Equipment's equip abilities cost {2} less to
  activate"). Any static reader that a granted ability must reach has to union `grantedStaticAbilities` with
  printed statics (as the equip-cost reducer and combat managers already do).
  `attacking` only applies to copies whose printed type line is a creature (a copy of a non-creature card
  still enters tapped but never attacking). `sacrificeAtStep` schedules one delayed `SacrificeTargetEffect`
  per created copy at that step (the sacrifice sibling of `CreateTokenEffect.sacrificeAtStep`);
  `sacrificeOnlyOnControllersTurn = true` restricts it to "at the beginning of *your* next end step"
  (Mardu Siegebreaker: a tapped+attacking copy of the linked-exiled card, sacrificed at your next end step).
  `exileAtStep` is the *exile* sibling — it schedules one delayed `MoveToZoneEffect(token, EXILE)` per copy
  at that step (the next matching step of any player's turn, "the next end step"). When
  `exileUnlessSourceIsRingBearer = true` that exile is wrapped in `Gate.WhenCondition(SourceIsRingBearer)`
  so it is skipped while the source is the controller's Ring-bearer at fire time (CR 701.54e) — "create a
  tapped and attacking token that's a copy of that card … at the beginning of the next end step, exile that
  token unless ~ is your Ring-bearer" (Sauron, the Necromancer).
  `controller` (an `EffectTarget` player ref) overrides who creates — and so controls and owns — the token;
  `null` defaults to the effect's controller. Set it for "**Target player** creates a token that's a copy of
  target creature you control" (Echocasting Symposium): the chosen creature is copied but the token enters
  under the named player's control. Mirrors `CreateTokenEffect.controller`.
  **Aura copies (CR 303.4h)** need no extra parameter — the executor handles them. A token copy of an Aura
  is created rather than cast, so it never targets; instead its controller *chooses* what it enchants as it
  enters, restricted to what the copied Aura could legally enchant (its `auraTarget`, with targeting
  restrictions such as hexproof/shroud ignored per CR 303.4f). The choice is raised **before** the token
  exists, so it enters already attached and its enters-the-battlefield triggers see the attachment; a
  `PermanentAttachedEvent` fires so "becomes attached" triggers (Eriette, the Beguiler) work. With no legal
  object to enchant the token isn't created at all (CR 303.4g). An effect making several Aura copies asks
  once per token. Because the choice is a mid-resolution pause, `CREATED_TOKENS` is **not** populated on the
  Aura path — branch a following step on the copied target instead (Yenna, Redtooth Regent's "if the token
  is an Aura, untap Yenna, then scry 2").
  Like `CreateToken`, both `CreateTokenCopyOfTarget` and `CreateTokenCopyOfSource` publish their created token
  entity IDs to the `CREATED_TOKENS` pipeline collection, so a sibling effect in a `CompositeEffect` can address
  the new copy — e.g. Applied Geometry's "Create a token that's a copy … Put six +1/+1 counters on it" composes
  `CreateTokenCopyOfTarget(...)` then `AddCountersToCollection(CREATED_TOKENS, "+1/+1", 6)`.
  **Enters-with replacements (CR 707.2).** A copy has the copied card's abilities, so every token copy runs the
  copied card's "as-enters" replacements as it enters — the same pipeline a minted token (`TokenFromDefinition`)
  and a directly-entering permanent use. This spans all four copy effects (`CreateTokenCopyOfTarget` /
  `-OfSource` / `-OfChosenPermanent` / `-OfEquippedCreature`): the copied card's own **and** global
  `EntersWithCounters`/`EntersWithDynamicCounters` (a token copy of a creature that "enters with a +1/+1
  counter", plus grants like Gev, Scaled Scorch — applied inline via `EntersWithReplacements.applyOnEntry`),
  its printed `EntersWithChoice` (Alloy Golem color, the Siege `MODE`, printed Riot — CR 614.12), and any
  **granted** Riot (Spider-Punk's "Other Spiders you control have riot" — one choice per lord, CR 702.136b).
  The choice half pauses for a player decision; `TokenEntryReplacements.firstEntersWithChoice` selects the choice
  and `PermanentEntryReplacements.pauseForEntersWithChoice` raises it, so the token's ETB triggers fire once
  after the choice resolves. For a multi-token `CreateTokenCopyOfTarget`/`-OfSource` where a token needs a choice,
  the batch resumes token-by-token through a `CreateTokenCopyRemainingContinuation` (each remaining token runs its
  own as-enters pipeline). A token that pauses for a choice is not published to `CREATED_TOKENS`.
- `CreateTokenCopyOfEquippedCreature(count?, tapped?)` — equipment-specific copy.
- `CreateRandomCreatureTokenWithManaValue(manaValue)` — create a token that's a copy of a *randomly
  chosen* creature card whose mana value equals `manaValue` (the Momir Basic Vanguard avatar's payoff —
  "Momir Vig, Simic Visionary": `{X}, Discard a card`). The candidate pool is the active
  `Format.MomirBasic.eligibleCreatureNames` (every creature across all sets, stored pre-sorted for
  replay-stable RNG); the executor filters it to `cmc == manaValue && !hasNoManaCost`, picks
  one with the game's seeded `GameRng`, and mints a token copy via `TokenFromDefinition` (the minting
  path for a bare `CardDefinition`, sibling to the in-zone token-copy path). If no creature has that
  mana value, nothing is created (the cost was still paid). The minted token's own `{X}` reads 0 — it
  never went on the stack. Pass `DynamicAmount.XValue` for "mana value X".
  The `hasNoManaCost` exclusion is what keeps `X = 0` from flipping a meld result (CR 701.42) such as
  Hanweir, the Writhing Township: no mana cost is an *unpayable* cost (CR 202.1b / CR 118.6), so its
  mana value is 0 while it is not a card anyone could cast. A printed `{0}` (Ornithopter) is payable
  and stays eligible.
- `CreateTreasure(count?, tapped?, controller?, imageUri?)` — Treasure tokens. `count` accepts an `Int` or a `DynamicAmount`
  (the latter evaluated at resolution, e.g. `CreateTreasure(DynamicAmounts.sourcePower(), tapped = true)`
  for Goldvein Hydra's "create a number of tapped Treasure tokens equal to its power"). `controller`
  (Int overload) redirects the tokens, e.g. `EffectTarget.TargetController` for "its controller creates
  two Treasure tokens" (An Offer You Can't Refuse). `imageUri` overrides the token art (see the
  set-specific-art note below).
- `CreateFood(count?, controller?)` — Food tokens. `count` accepts an `Int` or a `DynamicAmount` (the latter evaluated at resolution, e.g. `CreateFood(DynamicAmount.Divide(DynamicAmount.CastX, DynamicAmount.Fixed(2), roundUp = true))` for The Goose Mother's "create half X Food tokens, rounded up").
- `CreateEldraziSpawn(count?, controller?, imageUri?)` — 0/1 colorless Eldrazi Spawn creature tokens
  ("Sacrifice this creature: Add {C}."). `count` accepts an `Int` (default 1) or a `DynamicAmount`
  evaluated at resolution, such as `DynamicAmount.XValue` for Kozilek's Command.
- `CreatePest(count?, controller?)` — 1/1 **black and green** Pest creature tokens with "When this
  creature dies, you gain 1 life." (`PredefinedTokens.Pest`) — Strixhaven's Witherbloom token
  (Hunt for Specimens, Pest Summoning, Sedgemoor Witch, …). Predefined rather than inline because
  the token is *named* and carries its own triggered ability, which the inline `CreateToken` facade
  cannot express; its two colors come from a color indicator (CR 204) via `colorIdentity` on the
  token definition, since a token has no mana cost for `colors` to derive from.
- `CreateBlood(count?, controller?)` — Blood tokens (artifact with "{1}, {T}, Discard a card, Sacrifice this artifact: Draw a card."). `count` accepts an `Int` or a `DynamicAmount` (the latter evaluated at resolution, e.g. `CreateBlood(DynamicAmount.EntityProperty(EntityReference.Target(0), EntityNumericProperty.ExcessMarkedDamage))` for Lacerate Flesh's "create a number of Blood tokens equal to the amount of excess damage dealt").
- `CreateClue(count?, controller?)` / `Investigate(count?, controller?)` — Clue tokens (artifact with
  "{2}, Sacrifice this token: Draw a card."). `Investigate` is the keyword-action spelling (CR 701.36) so
  card text "investigate" maps directly; both create the same predefined `Clue` token — Malcolm, the Eyes.
  Both also take a `DynamicAmount` in place of the `Int` for "investigate once for each …" wording
  whose repetition count is only known at resolution (Wojek Investigator:
  `Investigate(DynamicAmount.CountPlayersWith(EachOpponent, …))`). A count of zero investigates not at
  all, which is what the wording means when nothing qualifies.
- `CreateShard(count?, controller?)` — Shard tokens (the Clue token's enchantment cousin: colorless
  "Enchantment — Shard" with "{2}, Sacrifice this enchantment: Scry 1, then draw a card."). Niko, Light of Hope.
- `CreateLander(count?, controller?)` — Lander land tokens.
- `CreateMeteorite(count?, tapped?, controller?)` — Meteorite tokens (Roxanne, Starfall Savant): a
  colorless artifact with "When this token enters, it deals 2 damage to any target." and "{T}: Add
  one mana of any color." Roxanne creates them `tapped = true`.
- `CreateMutavault(count?, tapped?, controller?)` — Mutavault tokens.
- `CreateEverywhere(count?, tapped?, controller?)` — Everywhere land tokens (Overlord of the Hauntwoods):
  a colorless land token with all five basic land subtypes (Plains/Island/Swamp/Mountain/Forest) that
  taps for any color — i.e. the mana ability of each basic land type, without the basic supertype. The
  Overlord of the Hauntwoods trigger creates them `tapped = true`.
- `CreateRoleToken(roleName, target)` — attach a Role aura token.
- `CreateMapToken(count?, imageUri?)` — Map artifact tokens. `count` accepts an `Int` or a `DynamicAmount`
  (the latter evaluated at resolution, e.g. Journey On's `CreateMapToken(Add(Fixed(1),
  CountPlayersWith(Player.EachOpponent, Conditions.ControlArtifact)))` — "X is one plus the number
  of opponents who control an artifact"). `imageUri` overrides the token art, but prefer `MtgSet.tokenArt` (see below).
- **Set-specific token art — declare it on the set, not on the card.** The same token (a 1/1 white Cat,
  a Treasure, a Map) is printed with different art in every set, and a token has no `CardDefinition` and no
  `Printing` row to hang that art on. Sets therefore declare their own token printings via
  **`MtgSet.tokenArt`**, a `List<TokenPrinting>`; the token executors resolve art in three layers:

  1. an explicit `imageUri` on the effect — a deliberate per-card override, always wins;
  2. the `TokenPrinting` contributed by the set the *creating card was printed in*
     (`TokenArtRegistry`, keyed off the entity's `Name#SET-CN` definition id), which is the set's own
     hand-authored `tokenArt` first and then the bulk `TokenArtData` rows synced from Scryfall;
  3. the engine-wide generic fallback — `TokenArt.IMAGES` by creature type for creature tokens, or the
     canonical `PredefinedTokens.kt` printing for Treasure/Map/….

  Layer 2's bulk half is `mtg-sets/core/src/main/resources/tokens.json`, one entry per token printing of every
  set that has a Scryfall token set (`t<code>`), refreshed with **`just token-art-sync`**. You rarely
  touch it: it is machine-owned and regenerated wholesale. The two halves are combined by
  `TokenArtData.forSet(set)` — hand-authored rows plus the synced rows for identities the set doesn't
  declare itself — so declaring a row is how you override synced art or supply art Scryfall doesn't have.
  Register a set's art through that function, never by concatenating the two lists yourself.

  ```kotlin
  object FoundationsSet : MtgSet {
      override val tokenArt = listOf(
          // tfdn #1 — Arahbo, the First Fang's 1/1 white Cat.
          TokenPrinting(name = "Cat", imageUri = "https://cards.scryfall.io/normal/front/2/8/2885d54c-….jpg"),
      )
  }
  ```

  `TokenPrinting` matches on `name`, plus `power` / `toughness` / `colors` when you pin them — only needed
  when one set prints two tokens sharing a name. Use the Scryfall **`normal`** URL — the whole token card,
  the same form `just token-art-sync` writes, rendered by the client as-is. An `art_crop` URL still works
  but takes the legacy path: the client recognises `/art_crop/` and draws the bare art inside a frame it
  generates itself, so a token that has a real printed card comes out looking like a placeholder.

  **Several arts for one token:** a set that printed the same token with different illustrations declares
  **one row per art** — nothing on the row changes, the plurality lives in the list. A batch of tokens
  created at once is dealt out of the matching rows in order and wraps, so Jumpstart's four `Dog` rows put
  four different dogs on the battlefield for Release the Dogs' four tokens. Indexing is by position in the
  batch, so it stays deterministic under replay. A row that pins an identity wins outright over a
  name-only row, so this never bleeds one token's art onto a same-named sibling.

  ```kotlin
  override val tokenArt = listOf(
      TokenPrinting(name = "Dog", imageUri = "/images/tokens/jmp-dog1.jpeg", power = 1, toughness = 1, colors = setOf(Color.WHITE)),
      TokenPrinting(name = "Dog", imageUri = "/images/tokens/jmp-dog2.jpeg", power = 1, toughness = 1, colors = setOf(Color.WHITE)),
      // … two more
  )
  ```

  Prefer this over `imageUri` on the effect. A card that bakes art into its `CreateToken` mints the same
  art from every printing, which is wrong the moment it is reprinted into a set with its own token —
  keying on the minting set is what makes reprints come out right.

  Roughly a third of the sets we implement predate token *cards* (Alpha through Invasion, Tempest,
  Odyssey, Onslaught), so Scryfall has nothing to sync and their tokens would fall back to generic art.
  Those are all self-hosted now, and `backlog/token-art-gaps.md` reports none outstanding — but adding a
  card whose set prints an unsynced token reopens the gap. Run **`just token-art-gaps`** for the work
  list: it names every token with no set-scoped art, the cards that create it, a suggested
  `web-client/public/images/tokens/<set>-<token>.jpeg` path, and a paste-ready `TokenPrinting(...)` row.
  Self-hosted art is served from that directory by relative URI — Invasion's Saproling and Reflection
  are the worked example.

  `TokenArtCoverageTest` walks every registered card and fails the build if any token it can create
  resolves to no image at all; fix a failure by adding the creature type to `TokenArt.IMAGES`, or the
  exact printing to the set's `tokenArt`.
- `CreateDroneToken(count?)` — Drone tokens.
- `CreateMunitionsToken(count?)` — Munitions noncreature artifact tokens (Weapons Manufacturing); the LTB damage
  trigger lives on the predefined `Munitions` `CardDefinition` and is picked up automatically by the engine's
  `TriggerAbilityResolver`.
- `CreateMutagenToken(count?)` / `CreateMutagenToken(amount: DynamicAmount)` — Mutagen noncreature artifact tokens
  (Teenage Mutant Ninja Turtles). The token is `"Artifact — Mutagen"` with the sorcery-speed activated ability
  `"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature."`, defined on the predefined `Mutagen`
  `CardDefinition` (so the ability is resolved automatically). The `DynamicAmount` overload serves X-count makers
  (Mutagen Man, Living Ooze — "create X Mutagen tokens").
- `CreateVehicleToken(count?, controller?)` / `CreateVehicleToken(amount: DynamicAmount, controller?)` —
  Aetherdrift's "3/2 colorless Vehicle artifact token with crew 1" (Mu Yanling, Wind Rider). A *noncreature*
  artifact carrying printed P/T and the ordinary `crew 1` keyword, defined on the predefined `Vehicle`
  `CardDefinition`, so it can't attack or block until something crews it and it crews through the normal path.
- `CreatePermanentEmblem(groupFilter?, powerBonus?, toughnessBonus?, grantedKeywords?, grantedActivatedAbilities?, ownedStaticAbilities?, emblemDescription)` — permanent planeswalker emblem whose dynamically evaluated group receives the listed stats, keywords, and activated abilities. Unlike a one-shot group grant, the emblem also affects matching permanents that enter later. The activated-ability form powers Arlinn Kord's emblem.
  - `ownedStaticAbilities` carries wording the emblem has **itself** rather than grants to a group — "You may cast spells from your hand without paying their mana costs" (Tamiyo, Field Researcher's −7) is `MayCastWithoutPayingManaCost(controllerOnly = true)`, the same static Omniscience prints. Such an emblem leaves `groupFilter` and the group modifications at their defaults. The emblem entity lives outside every zone, so a scan that only walks the battlefield won't see it; the free-cast scan (`CostCalculator.hasFreeCastPermission`) consults emblem statics explicitly. `firstSpellOfTurnOnly` / `oncePerTurn` gates are rejected there rather than approximated, since both key off marking a *battlefield* source used.

### Ability granting

- `GrantTriggeredAbilityEffect(ability, target, duration = Duration.EndOfTurn)` — grant a triggered ability to a battlefield permanent for a duration; `Duration.Permanent` for "gains … " with no end (Carnage, Crimson Chaos). **Target-general — not creature-only.** Nothing in the rules restricts "gains '<triggered ability>'" to creatures, and the printed wording routinely names a noncreature permanent: Down in the Valley's chapter II is "*This Saga* gains 'Landfall — Whenever a land you control enters, create a 1/1 green Elf creature token'", authored as `GrantTriggeredAbilityEffect(ability, EffectTarget.Self, Duration.Permanent)`. Whether a noncreature is a *legal* pick is the `TargetRequirement`'s job; the executor only requires the target to be on the battlefield. Recorded in `GameState.grantedTriggeredAbilities` and merged into the entity's abilities by `TriggerAbilityResolver`, so a granted trigger is detected exactly like a printed one and dies with the permanent. **The conditional "for as long as …" durations work here too**, in the same two halves every such duration gets: `TriggerAbilityResolver` gates the grant per read (so it goes dark the instant the condition fails, even mid-resolution) and `EndedDurationExpiryCheck` physically removes it, one-way per CR 611.2b, so the condition becoming true again does not bring the ability back. Both halves ask the same `GrantDurationGate`. Makeshift Mannequin is the shape: `PutOntoBattlefieldFromGraveyard(target)` + `AddCountersEffect(Counters.MANNEQUIN, 1, target)` + `GrantTriggeredAbilityEffect(sacrificeOnBecomingTarget, target, Duration.WhileAffectedHasCounter(Counters.MANNEQUIN))` — remove the counter (Hex Parasite, Vampire Hexmage) and the drawback really is gone. Wiring that sentence as `Duration.Permanent` reads identically on the card and is wrong in exactly that case.
- `GrantStateTriggeredAbilityEffect(ability, target, duration = Duration.Permanent)` — grant a **state**-triggered ability (CR 603.8) to a battlefield permanent. The sibling of `GrantTriggeredAbilityEffect` for the abilities the `StateTriggerPoller` owns rather than the `TriggerIndex`: use it when the printed rider fires because a condition *becomes true*, with no event to match. **Olivia, Crimson Bride**: the reanimated creature gains `"When you don't control a legendary Vampire, exile this creature."` — nothing *happens* when the last legendary Vampire leaves, so a `GrantTriggeredAbilityEffect` has no event to hang off. Recorded in `GameState.grantedStateTriggeredAbilities`, folded into the per-permanent ability list by `StateTriggerPoller` beside the printed ones, latched per `(entityId, AbilityId)` exactly like a printed state trigger, dropped on battlefield re-entry (CR 400.7) and expired by `CleanupPhaseManager` for `Duration.EndOfTurn`. The default duration is `Permanent`, not `EndOfTurn` — a granted state trigger is a durable rider, where a granted event trigger is usually a one-turn pump. Like `GrantTriggeredAbilityEffect` it is **target-general, not creature-only**; legality is the `TargetRequirement`'s job.
- `CreateGlobalTriggeredAbility(ability, duration = Duration.Permanent, descriptionOverride? = null)` — engine-wide triggered ability with no source permanent. `duration` is a plain parameter, so the one method covers every lifetime: `Duration.EndOfTurn` (False Cure, Death Frenzy), `Duration.UntilYourNextTurn` (Season of the Bold), `Duration.EndOfCombat`, `Duration.Permanent` (Dimensional Breach, planeswalker emblems), etc. `descriptionOverride` sets emblem display text. This is the right shape for a *floating* "until end of turn, whenever …" payoff that must outlive its own source — Mistway Spy's turned-face-up "whenever a creature you control deals combat damage to a player, investigate" keeps triggering even if the Spy is killed in response, which a `GrantTriggeredAbilityEffect` on the Spy would not. Because a global ability is attached to no permanent it lives outside every battlefield trigger index, so each specialized detector has to walk `GameState.globalGrantedTriggeredAbilities` itself; the ANY-bound `DealsDamageEvent` observers do (`DamageTriggerDetector.detectDamageObserverTriggers`), and a new detector that doesn't will silently never fire for a global ability.
- `GrantSpellKeywordEffect` — grant a keyword to a spell on the stack.
- `GrantSpellsCantBeCountered(target, filter, duration)` — target's matching spells become uncounterable (Domri shape).
- `GrantFlashToSpells(target, spellFilter, duration)` — target may cast matching spells as though they had flash (CR 702.8a) for `duration` (default `EndOfTurn`). Resolution-time one-shot that records the grant on the player and survives the source spell leaving the stack. Used by **Borne Upon a Wind** ("You may cast spells this turn as though they had flash."); narrower filters like `GameObjectFilter.Sorcery` cover "you may cast sorcery spells as though they had flash" variants. Sibling of the permanent-static [`GrantFlashToSpellType`](#9-static-abilities) — use the static for "as long as this is on the battlefield" wording (the two Gandalfs); use this Effect for a turn-scoped or duration-bounded grant.

### Control & combat

- `GainControlEffect(target, duration)` — gain control of a permanent; `duration` defaults to
  `Duration.Permanent` (Blatant Thievery). Pair with `Duration.EndOfTurn` for the Threaten pattern
  (Act of Treason), or **`Duration.EndOfYourNextTurn`** for the long Threaten — "gain control of
  that creature until the **end of** your next turn" (Evil's Thrall). That duration is strictly
  longer than `Duration.UntilYourNextTurn`, which ends at the *beginning* of your next turn; it runs
  through that whole turn and ends at its cleanup step (CR 514.2). Only the floating-effect path
  honours it — see the `Duration.EndOfYourNextTurn` KDoc for the mechanism and its limits. For the
  "if <condition>, … **instead**" duration switch, wrap two `GainControlEffect`s that differ only in
  `duration` in one `ConditionalEffect` — never a short steal followed by a second grab; that would
  be two control changes where the card describes one. Pair with
  `Duration.WhileSourceTapped` (Callous Oppressor) or
  `Duration.WhileSourceTappedAndAffectedPowerAtMostSource` (Old Man of the Sea) for the classic
  "for as long as this creature remains tapped [and the stolen creature's power stays ≤ source's
  power]" steal pattern, or `Duration.WhileYouControlSource("<source name>")` for the
  "for as long as you control this [permanent]" pattern (Aladdin, Scroll of Isildur Chapter I,
  Rangers of Ithilien), or `Duration.WhileYouControlSourceAndSourceTapped("<source name>")` when the
  card prints **both halves in one clause** — Seasinger (FEM): "Gain control of target creature whose
  controller controls an Island for as long as you control this creature and this creature remains
  tapped." Neither half alone is the printed duration, and each fails in a direction the other misses:
  an opponent stealing Seasinger hands the borrowed creature back even though Seasinger is still
  tapped, and untapping Seasinger hands it back even though you still control it. `StateProjector`
  gates it in two places, matching where each half can be answered — the battlefield and tapped halves
  when the floating effect is collected, the source-controller half after Layer 2 alongside
  `WhileYouControlSource` — and it is one-way like the rest (below), so re-tapping or regaining control
  never re-steals. Or `Duration.WhileSourceAttachedToAffected` for "gain control … for as
  long as that Aura is attached to it" (Eriette, the Beguiler — the effect is sourced from the
  *Aura*, so the executor swaps the floating effect's source to the triggering attachment, and the
  control ends the instant the Aura leaves, detaches, or re-attaches elsewhere). `StateProjector`
  gates these per-frame for the instantaneous view; the
  one-way half of CR 611.2b ("for as long as" durations don't restart) is enforced by the
  `EndedDurationExpiryCheck` state-based action, which physically removes the effect the moment
  the condition fails — so a pump that wears off, a re-tap, or a re-acquired source never
  re-grabs the creature.
- `ExchangeControlEffect(target1, target2)` — swap control of two permanents.
  Triggered abilities with mandatory single-permanent target slots may compare a later target to an
  earlier one using `EntityReference.Target(index)` (Spawnbroker: `powerAtMostEntity(Target(0))`).
  The engine asks for these targets sequentially, offers only choices that can complete the remaining
  slots, and rechecks the relationship against projected characteristics at resolution. The optional
  exchange remains a resolution-time consent choice. This selection path does not support optional
  or multiple-object slots.

- `GainControlByRankEffect(metric, target?, direction?, tieBreak?)` — rank the players still in the
  game by a `PlayerRankMetric` and hand the target to whoever sits at one end. Three independent
  axes, so a new card in this family usually needs no new effect: **what** is ranked (`metric` —
  `PlayerRankMetric.LifeTotal`, `PlayerRankMetric.CreaturesOfSubtype(subtype)`), **which end** wins
  (`direction` — `PlayerRankDirection.MOST` / `.LEAST`), and **what a tie means** (`tieBreak` —
  `RankTieBreak.NONE`, nothing happens, the "more than each other player" intervening-if wording;
  or `RankTieBreak.CONTROLLER_CHOOSES`, the ability's controller picks one of the tied players).
  Ghazbán Ogre = `LifeTotal` + `MOST` + `NONE`; Thoughtbound Primoc =
  `CreaturesOfSubtype(Wizard)` + `MOST` + `NONE`; Loxodon Peacekeeper = `LifeTotal` + `LEAST` +
  `CONTROLLER_CHOOSES`. Facades: `Effects.GainControlByMostLife()`,
  `Effects.GainControlByMostOfSubtype(subtype)`, `Effects.GainControlByLowestLife(target, tieBreak)`.
  The tie-break prompt is not bespoke plumbing — the executor lowers it into a `ChooseActionEffect`
  with one option per tied player, reusing the ordinary choose-an-option decision. Ranking reads
  `activePlayers`, so a player who has lost the game is not ranked (a 0-life loser would otherwise
  win every `LEAST`), and `AbilityFlag.CANT_GAIN_CONTROL` (Guardian Beast) is checked *before* any
  prompt is raised.
- `GiftGivenEffect(target)` — "gift" temporary control.
- `CantAttackEffect(target, unless?)` — target can't attack.
- `CantBlockEffect(target, duration = EndOfTurn, attacker = null)` — target can't block. With
  `attacker` set the restriction is **pairwise** — "target creature can't block *this creature* this
  turn" (Screeching Griffin: `Effects.CantBlock(attacker = EffectTarget.Self)`), leaving the target
  free to block everything else. The blanket form projects `SetCantBlock`; the pairwise form stores
  `CantBlockSpecificAttacker(attackerId)` (the restriction mirror of provoke's
  `MustBlockSpecificAttacker`) and is enforced by `CantBlockSpecificAttackerRule` at block
  declaration, because a per-blocker projected flag can't express a pair.
- `CantAttackGroupEffect(filter, condition?)` — group-scoped can't-attack.
- `CantBlockGroupEffect(filter, condition?)` — group-scoped can't-block.
- `Effects.Suspect(target, duration = Permanent)` (`SuspectEffect`) — target becomes suspected (MKM,
  CR 701.60): the named designation *plus* the menace and "this creature can't block" it carries
  while suspected. **One** effect and one executor, not a composite of three, because every gate on
  becoming suspected has to suppress all three halves together — CR 701.60d's "already suspected"
  no-op, and `AbilityFlag.CANT_BECOME_SUSPECTED` (Airtight Alibi). Gating only the designation would
  leave a creature that isn't suspected but still has menace and can't block; gating the riders
  separately would wrongly suppress menace or can't-block arriving from an unrelated source.
  `SuspectExecutor` applies the three layer modifications under one shared `(sourceId, timestamp)`
  — `addFloatingEffect` doesn't tick `state.timestamp` — so Rule 613 still orders them as a single
  application and `RemoveSuspectedEffect` still lifts them as one bundle.
- `Effects.NoLongerSuspected(target = ContextTarget(0))` (`RemoveSuspectedEffect`) — "it's no longer
  suspected" (CR 701.60c), the exact inverse of `Effects.Suspect`: it removes the named status
  *together with* the menace grant and the can't-block restriction, since those exist only for as
  long as the creature is suspected. `RemoveSuspectedExecutor` identifies the bundle by the
  `(sourceId, timestamp)` the three floating effects share — `SuspectExecutor` creates all three
  without ticking `state.timestamp`, so Rule 613 treats them as one application and that same shared
  stamp is the bundle's identity here. The match is narrowed to those three modification
  kinds on an affected set of exactly this one creature, so menace or can't-block from any other
  source survives. A no-op on an unsuspected creature; CR 701.60d guarantees a creature never
  carries two bundles at once. Used by Absolving Lammasu (MKM) — `ForEachInGroup` over
  `GameObjectFilter.Creature.suspected()` gives "all suspected creatures are no longer suspected".
- `RemoveFromCombatEffect(target, unblockSoleBlockedAttackers = false)` — yank target out of combat.
  Set `unblockSoleBlockedAttackers = true` for the old-rules behavior (Ydwen Efreet): attackers the
  target was sole blocker of become unblocked (CR 509.1h normally keeps them blocked).
- `Effects.OpponentGuessesTopCardKind(onGuessedRight, onGuessedWrong, chooser = Controller, guesser = Opponent)`
  (`OpponentGuessesTopCardKindEffect`) — "Choose land or nonland. An opponent guesses whether the top
  card of your library is the chosen kind. Reveal that card. If they guessed right, [onGuessedRight];
  otherwise, [onGuessedWrong]." (Gollum, Scheming Guide.) A reusable opponent-guess primitive that
  sequences two `ChooseOptionDecision`s: the `chooser` picks the framing land/nonland kind, then the
  `guesser` guesses the *actual* kind of the top card of the chooser's library; the card is revealed
  and the guess compared to reality (a correct guess = the guesser's call matches the actual top card).
  Both branch effects resolve in the source's original context, so `EffectTarget.Self` inside them
  refers to the ability's source. Empty library → no top card → guess can never be right, so the
  "wrong" branch runs. `chooser`/`guesser` reuse the shared `Chooser` enum (see `ChoosePileEffect`).
- `Effects.PlayerGuessesCondition(condition, prompt, storeGuessedRightAs = "guessedRight", guesser = Opponent, promptNameVariable = null)`
  (`PlayerGuessesConditionEffect`) — "[guesser] guesses whether [condition] is true", storing `1`
  (right) or `0` (wrong) under `storeGuessedRightAs` instead of branching (Liar's Pendulum). The
  **open** sibling of `OpponentGuessesTopCardKind`: that one owns its proposition, its reveal and both
  branches; this one owns none of them, so the card can put its own steps *between* the guess and the
  payoff — Liar's Pendulum's optional hand reveal sits there, and folding it into branches would
  duplicate it and let the two prompts tell the guesser whether they were right. `condition` is any
  resolution-time `Condition`, evaluated only *after* the answer is in and never shown to the guesser;
  nothing is revealed unless the card says so. Gate the payoff with
  `Compare(VariableReference(storeGuessedRightAs), EQ, Fixed(0))` for "guessed wrong" / `Fixed(1)` for
  "guessed right"; a consumer that runs with no guess having happened reads 0, so order the guess
  first. `promptNameVariable` substitutes `{name}` in `prompt` from `chosenValues`, so a guess about a
  card named a step earlier can put that name in the question. `guesser` is the shared `Chooser` enum —
  a printed "target opponent" is `Chooser.TargetPlayer`.
- `Effects.CanAttackDespiteDefenderThisTurn(target = Self)` (`CanAttackDespiteDefenderThisTurnEffect`) — target can attack this
  turn as though it didn't have defender. Adds a transient `CanAttackDespiteDefenderThisTurnComponent`
  honored by the defender attack-restriction rule and cleaned up at end of turn. The
  activated/temporary counterpart to the static `CanAttackDespiteDefender` ability (Krotiq Nestguard).
  Both the static ability and this transient grant are read directly by the attack-restriction rule
  (via the shared `DefenderBypass` helper), never through the layer system — so while a Defender's
  restriction is lifted, `ClientStateTransformer` surfaces a "Can attack despite defender"
  `activeEffects` badge on the card (mirroring how Akawalli's descend-8 block restriction is shown),
  letting the player see the creature can attack the moment the condition is met (e.g. after an
  artifact enters for Shipwreck Sentry / Mechan Shieldmate).
- `Effects.Goad(target = ContextTarget(0))` (`GoadEffect`) — goad target creature (CR 701.15).
  Tags the creature with `GoadedComponent(goaderIds: Set<EntityId>)`; the effect's controller at
  resolution is recorded as the goader. While goaded the creature (a) must attack each combat if able
  and (b) can't attack any player in `goaderIds` if a non-goader player is available to attack (per
  CR 701.15b the alternative is a *player*, not a planeswalker) — both checks
  live inline in `AttackPhaseManager.declareAttackers` alongside the must-attack-this-turn pass. The
  goader set deduplicates, so the same player re-goading is a no-op (CR 701.15d); multiple distinct
  goaders stack (CR 701.15c). After the untap step of each player's turn,
  `CleanupPhaseManager.expireGoadedDesignationFor` drops that player from every goader set and
  removes the component when the set is empty — same hook as the `Duration.UntilYourNextTurn`
  floating-effect path, implementing the "until your next turn" duration (CR 701.15a). Surfaced to
  the client as the `Goaded` badge on the
  card (listing goader names) — there is no separate game-log event. Used by **Glóin, Dwarf
  Emissary**: `Costs.Composite(Costs.Tap, Costs.Sacrifice(Artifact.withSubtype("Treasure"))):
  Goad(target creature)`.
- `Effects.MarkMustAttackThisTurn(target = ContextTarget(0))` (`MarkMustAttackThisTurnEffect`) —
  require one creature to attack this turn if able. The transient marker is enforced by
  `AttackPhaseManager` and removed during cleanup. It composes with the token pipeline for “that
  token attacks this combat if able”: create the token, then target
  `PipelineTarget(CREATED_TOKENS, 0)` with this facade.
- `Effects.MarkMustBlockThisTurn(target = ContextTarget(0))` (`MarkMustBlockThisTurnEffect`) —
  "target creature blocks this turn if able". Adds a `Layer.ABILITY` floating
  `SerializableModification.SetMustBlock` for `Duration.EndOfTurn`, i.e. the same projected
  `mustBlock` the *static* `MustBlock` (Grand Melee) writes, so
  `BlockPhaseManager.validateProjectedMustBlockRequirements` enforces it with no extra wiring and
  the generic end-of-turn cleanup expires it. Distinct from
  `Effects.ForceBlock(target, attacker = EffectTarget.Self)`, which pins the creature to blocking one
  *named* attacker and is only read while that creature is actually attacking (it may be created
  before attackers are declared — Sisters of Stone Death's "{G}: Target creature blocks ~ this turn
  if able" — and binds once the creature attacks) — this one is satisfied by blocking
  anything. `attacker` defaults to the ability's own source ("blocks **it**", Avalanche Tusker); pass
  `EffectTarget.TriggeringEntity` when an ANY-bound trigger pins the blocker to the creature that
  attacked instead (Tolsimir, Midnight's Light: "blocks **that Wolf** this combat if able"). A requirement, not a guarantee (CR 509.1c): a tapped creature, one
  that can't block, or one whose every block would be illegal is excused, and its controller is
  never forced to pay a cost associated with blocking. Used by Culvert Ambusher (MKM).
- `Effects.ForceBlock(blocker, attacker)` also accepts two spell targets (Hunt Down, LRW).
  The blocker is checked using projected creature types, including animated lands and artifacts.
  The end-of-turn requirement is removed when either permanent leaves the battlefield; returning
  that card does not restore the old requirement.
- `Effects.SkipNextTurn(target = Controller, count = Fixed(1))` (`SkipNextTurnEffect`) — target skips their next `count` turns. `count` is a `DynamicAmount`, so it can read a pipeline value (e.g. a coin-flip tally via `DynamicAmount.VariableReference`). Skips accumulate on a `SkipNextTurnComponent(turns)`, decremented one turn per the player's turn-start; a resolved count of 0 is a no-op. Used by Lethal Vapors (one turn) and **Ral Zarek, Guest Lecturer** (skip N turns where N = heads).
- `Effects.FlipCoins(count, storeHeadsAs = "heads")` (`FlipCoinsEffect`) — flip `count` coins and store the number of heads under `storeHeadsAs` in the pipeline (`storedNumbers`) so a later sub-effect in the same composite can scale off it via `DynamicAmount.VariableReference`. The general "flip N coins, count heads" primitive (CR 705); unlike `FlipCoinEffect` (branch on win/lose) and `FlipTwoCoinsEffect` (branch on combined outcome) it only tallies. Each flip emits a `CoinFlipEvent`. **Ral Zarek, Guest Lecturer**'s ultimate composes `FlipCoins(5, "heads")` then `SkipNextTurn(target, count = VariableReference("heads"))`.
- `Effects.FlipCoinsUntilLoss(storeWinsAs = "wins")` (`FlipCoinsUntilLossEffect`) — `FlipCoins`'s open-ended sibling: flip one coin at a time until the flipper *loses* a flip or answers "stop flipping", then store how many flips they won under `storeWinsAs`. The run length is discovered rather than given, and the order within an iteration is flip → check → ask, so the stop question only ever follows a *won* flip ("after each flip, you choose whether to continue flipping"). Losing the first flip stores 0, and since an unread pipeline number reads as 0, a card gating payoffs on "if you win one or more flips" needs no separate "this has no effect" branch — that sentence *is* the absence of every payoff. Deliberately **not** a `RepeatWhile` over `FlipCoinEffect`: a repeat condition is asked unconditionally after each body (so it can't stop *because* a flip was lost) and the repeat loop restarts each iteration from the pristine pre-loop context (so a running tally couldn't survive the prompt). The tally rides `FlipCoinsUntilLossContinuation` instead and is published once, when the run ends. Unlike `FlipCoins`, where the whole batch is one flip event, each coin here is its own flip — so a "the first time you flip one or more coins each turn" replacement (Edgar, King of Figaro) covers only the first coin. Bounded by `GameLimits.MAX_COIN_FLIPS_PER_EFFECT` as a backstop against a forced-win static plus an always-continue automated answer. **Fiery Gambit** composes `FlipCoinsUntilLoss("fieryGambitWins")` with three cumulative `Gate.WhenCondition(Compare(VariableReference("fieryGambitWins"), GTE, Fixed(n)))` tiers.
- `Effects.SkipNextDrawStep(target = Controller)` (`SkipNextDrawStepEffect`) — target skips their next draw step. Adds a one-shot `SkipDrawStepComponent` marker consumed by `DrawPhaseManager.performDrawStep` (Elfhame Sanctuary's "you skip your draw step this turn").
- `Effects.SkipStepOrPhaseThisTurn(part, target)` (`SkipStepOrPhaseThisTurnEffect`, `part` = `TurnPart.DRAW_STEP` |
  `MAIN_PHASE` | `COMBAT_PHASE`) — the target skips **every** instance of that part of the turn for the rest of
  this turn. The **duration** is what separates it from the one-shot `SkipNextDrawStep` / `SkipCombatPhases`
  markers above, which are consumed by the first occurrence: this one stands until end-of-turn cleanup drops
  its `SkippedTurnPartsComponent`, so a second main phase or an additional combat phase created later in the
  turn is skipped too. `TurnPart` is the granularity printed cards use — one value covers both main phases
  (CR 505.1: the precombat and postcombat main phases are individually and collectively "the main phase") and
  one covers all five combat steps. The skip is read in `TurnManager.advanceStepFromEndedStep` *before* the
  step's `StepChangedEvent` is built, which is what makes it faithful to CR 500.11 / 614.10 — the step is
  proceeded past as though it didn't exist, so no player receives priority in it and no "at the beginning of
  ..." ability triggers for it (that event is what `PassPriorityHandler` feeds to `detectPhaseStepTriggers`).
  Skipping the postcombat main phase still runs the deferred end-of-combat bookkeeping that phase owns, or
  creatures would stay in combat for the rest of the turn, and the additional-phase queue
  (`AdditionalPhasesComponent`, drained on a separate path) consults the same marker — so an Aggravated
  Assault combat phase created later in the turn is discarded rather than sailing through while the natural
  one is skipped. Per CR 614.10 a step already under way can no longer be skipped. Used by **Fatespinner**, whose upkeep trigger routes a three-option `ChooseAction` to
  `Player.TriggeringPlayer` and applies the chosen part to that same player.
- `Effects.HijackNextTurn(target)` / `Effects.HijackNextCombatPhase(target)` (`HijackNextTurnEffect(target, scope)`, `scope` = `HijackScope.NextTurn` | `NextCombatPhase`) — Mindslaver-style: you make all decisions for the target player during their next whole turn, or during their next combat phase only. Moves *input authority* only (resource/permanent/spell ownership stays with the affected player); reuses `PlayerTurnHijackedComponent` + `GameState.actorFor`, so hand visibility and legal-action routing follow automatically. A scheduled hijack waits through skipped turns/combat phases and engages on the next one the player actually takes. Turn scope engages at turn start and clears at end-of-turn cleanup (**The Dominion Bracelet**); combat scope engages at beginning of combat and clears when that one combat phase ends — extra combat phases are not controlled (**Secret of Bloodbending**, whose optional waterbend upgrades combat→turn via `ConditionalEffect(Conditions.WaterbendWasPaid, HijackNextTurn, elseEffect = HijackNextCombatPhase)`).
- `GrantCantBeBlockedByChosenColorEffect(target, duration)` — unblockable except by chosen color.
- `Effects.GrantCantBeBlockedExceptBy(target, blockerFilter, duration = EndOfTurn)` (`GrantCantBeBlockedExceptByEffect`) —
  the floating, one-shot grant of "can't be blocked except by creatures matching `blockerFilter`". The dynamic
  counterpart to the static `CantBeBlockedExceptBy` ability (and the filter-based sibling of the color-only
  `GrantCantBeBlockedExceptByColorEffect`). Routes through the same projected `cantBeBlockedExceptByFilters` channel
  the static ability uses, so the existing `CantBeBlockedExceptByRule` enforces it. Used by **Resilient Roadrunner**:
  `{3}: This creature can't be blocked this turn except by creatures with haste` —
  `Effects.GrantCantBeBlockedExceptBy(EffectTarget.Self, GameObjectFilter.Creature.withKeyword(Keyword.HASTE))`.
- `CantBeBlockedByFewerThan(minBlockers, filter = source())` (static ability) — "can't be blocked
  except by N or more creatures," a generalization of menace (the N = 2 case). May be left unblocked;
  the restriction only applies once at least one creature blocks it. Enforced at block declaration in
  `BlockPhaseManager.validateMinBlockersRequirements`, mirroring the menace check. Used by Troll of
  Khazad-dûm (`CantBeBlockedByFewerThan(3)`).
- `CantCastSpellsEffect(target, until?)` — target can't cast spells. Facade: `Effects.CantCastSpells(target, duration)`.
- `CantPlayCardsFromHandEffect(target = Controller, duration = UntilYourNextTurn)` — target can't play cards (cast
  spells **or** play lands) from their **hand** zone for the duration. Hand-scoped: cards in exile/graveyard with a
  may-play permission stay playable. Facade: `Effects.CantPlayCardsFromHand(target, duration)`. Pairs with an impulse
  grant (`ExilePatterns.impulse`) so a player swaps their hand for the top cards of their library for a turn
  (Memory Vessel). Distinct from `CantCastSpells` (every zone, spells only).
- `CantCastSpellsFromNonHandZonesEffect(target, duration = UntilYourNextTurn)` — target can't cast spells from any zone
  **other than their hand** for the duration; ordinary hand casts still resolve, but graveyard (flashback/escape),
  exile (foretell/plot/a may-play permission), library-top, and command-zone casts all become illegal. The **inverse**
  of `CantPlayCardsFromHand` (which restricts *to* the hand): this restricts *away from* every zone except the hand.
  Facade: `Effects.CantCastSpellsFromNonHandZones(target, duration)`. Stamps `CantCastFromNonHandZonesComponent` on the
  player (with an `expiresForPlayerId` keyed to the *casting* player for the `UntilYourNextTurn` window, like
  `PlayerCantPlayFromHandComponent`); enforced authoritatively in `CastSpellHandler` and suppressed at enumeration by the
  non-hand `CastFromZoneEnumerator`. The "your opponents can't cast spells from anywhere other than their hands" clause of
  Avatar's Wrath (`target = EffectTarget.PlayerRef(Player.EachOpponent)`).
- `Effects.CantPlayLandsThisTurn(target = Controller)` (`PreventLandPlaysThisTurnEffect`) — the target player can't
  play lands for the rest of this turn (sets remaining land drops to 0). Defaults to the controller (Rock Jockey);
  pass `EffectTarget.ContextTarget(n)` for "target player can't play lands this turn" cards like Turf Wound.
- `CantActivateLoyaltyAbilitiesEffect(target, duration)` — target can't activate planeswalkers' loyalty abilities.
  Facade: `Effects.CantActivateLoyaltyAbilities(target, duration)`. Sibling of `CantCastSpells`; compose the two for
  cards that forbid both (e.g. Revel in Silence).

### Forced sacrifice / discard

- `SacrificeTargetEffect(target, sacrificedByItsController = false)` — sacrifice a specific permanent. By
  default only fires if the resolving player controls it; set `sacrificedByItsController = true` for
  "[that creature]'s controller sacrifices it" (e.g. The Ring's Ring-bearer ability).
- `Effects.Sacrifice(filter, count, target)` / `ForceSacrificeEffect(target, count)` — edict; target
  sacrifices N permanents matching the filter (target chooses). `count` accepts an `Int` or a
  `DynamicAmount` — pass a `DynamicAmount` (via the `Effects.Sacrifice(filter, count: DynamicAmount, …)`
  overload / `ForceSacrificeEffect.dynamicCount`) for "sacrifices half the creatures they control,
  rounded up" (Rush of Dread): `Divide(AggregateBattlefield(Player.ContextPlayer(0), Creature), Fixed(2),
  roundUp = true)`. The amount is evaluated at resolution against the resolving context, so a per-target
  player reference counts the chosen player's permanents.
- `Effects.SacrificeOwn(filter, count?)` (= `SacrificeEffect(filter, count)`) — the bare imperative,
  "Sacrifice a creature.": the **ability's controller** sacrifices and no player is named. Distinct
  from `Effects.Sacrifice`, which is the edict and names the player who must sacrifice — writing the
  bare form as `Sacrifice(filter, 1, EffectTarget.Controller)` says the same thing the long way round.
- `Effects.SacrificeAnyNumber(filter, excludeSource = false)`
  (= `SacrificeEffect(filter, any = true, excludeSource)`) — the *resolving*
  player chooses 0+ of their own permanents matching `filter` to sacrifice. Distinct from
  `ForceSacrifice` (edict on a target) and from `Costs.pay.Sacrifice` (a cost): this is a
  resolution effect. The sacrificed permanents are recorded in the effect context, so a later
  composite step can read the count via `DynamicAmounts.permanentsSacrificedThisWay()` — e.g.
  "Sacrifice any number of lands. Reveal the top X cards … where X is the number of lands
  sacrificed this way" (Hew the Entwood; same shape as Scapeshift) — or their combined power via
  `DynamicAmounts.totalPowerSacrificedThisWay()` (Kylox, Visionary Inventor). `excludeSource = true`
  keeps the ability's own source off the list, which is how "sacrifice any number of **other**
  creatures" is spelled on a trigger whose source is itself a creature.
- "Each player chooses \<one permanent per category\> they control, then \<does something to\> the
  rest" is a **pipeline composition**, not an effect type — see `chooseOnePerCategory` / `exclude` in
  §5.5. Liliana, Dreadhorde General's −9 is
  `gather(Permanent.opponentControls())` → `chooseOnePerCategory(…, Filters.PermanentTypes)` →
  `sacrifice(exclude(pool, kept))`; swapping the last step gives Consuming Tide's "returns the rest
  to their hands", and swapping the category list gives Cataclysm / Divine Reckoning. For "all
  permanents matching a filter are sacrificed by their controllers" with no choice at all, use
  `Effects.SacrificeAll(filter)` instead.
- "Return a permanent you control [to its owner's hand]" is a pipeline composition, not an effect type:
  `GatherCards(BattlefieldMatching(filter, Player.You, excludeSelf?))` →
  `SelectFromCollection(ChooseExactly(1), useTargetingUI = true)` → `MoveCollection(→ HAND)` (the
  battlefield→hand move routes each card to its owner's hand). See Mistbreath Elder.

### Stack manipulation

- `CounterEffect(target, condition?, destination?)` — counter a spell/ability; optionally send elsewhere. `CounterDestination.Exile(grantFreeCast?)`: `grantFreeCast` lets the counter's *controller* recast the exiled card for free (Kheru Spellsnatcher). (For "exile it; its owner may recast it" wording that is **not** a counter — e.g. airbending a spell — use `ExileTargetSpell(fixedAlternativeManaCost = …)` below, which bypasses can't-be-countered.) `CounterDestination.Hand` (facade `Effects.CounterSpellToHand()`) is Remand's "put it into its owner's hand instead of into that player's graveyard" — still a real counter, so an uncounterable spell is untouched and "whenever a spell is countered" triggers still fire; `ReturnSpellToOwnersHand` is the non-counter sibling.
  - `target = CounterTarget.Spell` / `Ability` / `SpellOrAbility` — `SpellOrAbility` dispatches at resolution by inspecting whether the stack entity has a `SpellOnStackComponent`. Used by Teferi's Response.
  - `condition = CounterCondition.UnlessPaysMana(cost, onPaid?)` / `UnlessPaysDynamic(amount, onPaid?)` — "unless its controller pays …" with an optional `onPaid: Effect` rider that fires **only** when the spell's controller pays (Divert Disaster's "If they do, you create a Lander token"). The rider executes with the counter's controller as `controllerId`, so "you" in the rider resolves to the caster of the counter. The rider does not fire when the spell is countered. Facade: `Effects.CounterUnlessPays(cost, onPaid)` / `Effects.CounterUnlessDynamicPays(amount, exileOnCounter, onPaid)`.
- `CounterAllOnStackEffect(filter?, destination?)` — counter everything matching.
- `ExileTargetSpellEffect(makePlotted = false, fixedAlternativeManaCost = null, linkToSource = false)` (facade `Effects.ExileTargetSpell(makePlotted, fixedAlternativeManaCost, linkToSource)`) — exile target spell (CR 718 "exile target spell"). **Not a counter:** it removes the spell from the stack and exiles the card even if the spell *can't be countered* (so it works where `CounterEffect(destination = Exile())` no-ops), and it fires no "whenever a spell is countered" trigger — but the spell still fails to resolve because it left the stack. With `makePlotted = true` the exiled card becomes *plotted* for its **owner** (gains `PlottedComponent` + a permanent free-cast-on-a-later-turn `MayPlayPermission` gated by `SourcePlottedOnPriorTurn`, granted to the owner per CR 718.2), emitting a `CardPlottedEvent`. With `fixedAlternativeManaCost = {2}` the exiled card's **owner** instead gets a permanent `MayPlayPermission` + `PlayWithFixedAlternativeManaCostComponent`, letting them recast it for that fixed cost rather than its printed cost — the spell-on-stack form of the **Airbend** keyword (Aang, Swift Savior). `makePlotted` and `fixedAlternativeManaCost` are mutually exclusive. With `linkToSource = true` the exiled card is appended to the effect *source's* `LinkedExileComponent` — the stack-side counterpart of `ExileLinkedToSource` / `MoveCollection(linkToSource = true)`. It grants nothing on its own; it is only a handle so a later ability of the same source can say "the exiled card", and it survives the source's own zone change so a leaves-the-battlefield trigger still finds it. **Spell Queller** pairs it with a `LeavesBattlefield` trigger of `ForEachPlayer(Player.OwnersOfLinkedExile, gather(FromLinkedExile()) → MayEffect(CastFromCollectionWithoutPayingCost(...)))` — note that Spell Queller's payoff is a cast *during resolution* (its ruling: "The player can't wait to cast it later in the turn"), not a lingering `GrantMayPlayFromExile` permission. Pair with `Targets.Spell`. Used by **Aven Interrupter** ("…exile target spell. It becomes plotted."), **Spell Queller**, and the airbend stack branch.
- `MarkSpellExileWithCountersEffect(target = TriggeringEntity, counterType, count = 1)` (facade `Effects.MarkSpellExileWithCounters(target, counterType, count)`) — mark a spell on the stack so that, **as it resolves**, it is exiled with `count` counters of `counterType` on it instead of being put into its owner's graveyard. Lets the spell resolve fully, then re-routes only its post-resolution destination via `ExileAfterResolveComponent(onlyIfResolved = true)` — so if the spell is countered or fizzles it goes to the graveyard normally. Used by **Goliath Daydreamer** ("exile that card with a dream counter on it instead of putting it into your graveyard as it resolves").
- `MarkSpellPlotOnResolveEffect(target = TriggeringEntity)` (facade `Effects.MarkSpellPlotOnResolve(target)`) — the plot sibling of `MarkSpellExileWithCounters`: as the spell resolves it is exiled instead of going to the graveyard and **becomes plotted** for its owner (`PlottedComponent` + permanent free-cast-on-a-later-turn `MayPlayPermission` gated by `SourcePlottedOnPriorTurn`, emitting `CardPlottedEvent`). Also `onlyIfResolved` — a countered/fizzled spell is not exiled and doesn't become plotted. Distinct from `ExileTargetSpell(makePlotted = true)`, which removes a *targeted* spell from the stack now (it never resolves); this one only changes a self-cast spell's destination after it resolves. Used by **Lilah, Undefeated Slickshot** ("Whenever you cast a multicolored instant or sorcery spell from your hand, exile that spell instead of putting it into your graveyard as it resolves. If you do, it becomes plotted.").
- `spell { returnTransformedFromGraveyard(vararg counters: CounterType) }` — **not** an effect but a resolution-destination flag on the spell's `CardScript` (`returnTransformedFromGraveyardOnResolve: ReturnTransformedFromGraveyard?`). Marks a double-faced card so that, when it resolves **after being cast from a graveyard**, it is exiled and then put onto the battlefield **transformed** (its back face up) under its owner's control, entering with the given `counters`, instead of going to its owner's graveyard. Models **Esper Origins** ("If this spell was cast from a graveyard, exile it, then put it onto the battlefield transformed under its owner's control with a finality counter on it"): `spell { effect = …; returnTransformedFromGraveyard(CounterType.FINALITY) }` on the sorcery front, joined to a Saga-creature back via `frontFace.copy(backFace = …)`. Like flashback's own graveyard-cast exile, the destination is derived from the spell's `castFromZone` at resolution time (in `StackResolver`), **not** from an effect run during resolution — so it survives a mid-resolution pause (e.g. an earlier Surveil in the same resolution) and is correctly inert when the spell is countered or fizzles. It **takes precedence over the flashback exile**: a graveyard-cast card that both has flashback and this flag returns transformed rather than exiling. Requires a permanent back face; a non-DFC or non-permanent back is a no-op (the card falls through to its normal graveyard/exile destination, per the official ruling on putting a non-double-faced card onto the battlefield transformed). The back-face flip + battlefield entry reuse the shared `returnDfcFaceFromExile` helper (a Saga back enters with a fresh lore counter, CR 714.2b; leaves/enters triggers fire, not transform triggers).
- `OpenLifeBid(onWin, participant = Player.AnOpponent)` — open life-bidding auction between you and `participant` (resolved against the effect context). You open at a bid of 1; the two bidders alternate topping the high bid (yes/no to top, then a number for the amount, capped at the bidder's life) until one passes. The high bidder loses that much life; `onWin` runs **only if you win**, with the original targets in context. If `participant` resolves to you (or to nobody), you're the sole bidder and win at the opening bid. For Mages' Contest, bid against the targeted spell's controller and counter it: `Effects.OpenLifeBid(Effects.CounterSpell(), Player.ControllerOf("target spell"))` — pair with a `TargetSpell` requirement.
- `DestroySourceOfTargetedAbilityEffect` — when the targeted stack object is a permanent's activated/triggered ability, destroy that source permanent. Compose *before* the counter step so the ability component is still readable (Teferi's Response).
- `RemoveAbilitiesFromSourceOfTargetedAbilityEffect(duration = EndOfTurn, sourceCardTypes = emptySet())` (facade `Effects.RemoveAbilitiesFromSourceOfTargetedAbility(duration, sourceCardTypes)`) — the ability-strip sibling of `DestroySourceOfTargetedAbilityEffect`. When the targeted stack object is a permanent's activated/triggered ability whose source is still on the battlefield **and** (when `sourceCardTypes` is non-empty) has one of those projected card types, that source permanent gains a Layer-6 `RemoveAllAbilities` floating effect for `duration`. The floating effect is keyed to *this effect's* source, so `Duration.WhileSourceOnBattlefield` ends when that permanent (e.g. Tishana) leaves. No-op for a spell target, an already-gone source, or a type mismatch. Compose *before* the counter step so the ability's source component is still readable. Used by **Tishana's Tidebinder** ("If an ability of an artifact, creature, or planeswalker is countered this way, that permanent loses all abilities for as long as this creature remains on the battlefield") — pair with `CounterAbility()`, `Duration.WhileSourceOnBattlefield`, and `sourceCardTypes = {ARTIFACT, CREATURE, PLANESWALKER}`, targeting via an optional (`optional = true`) `Targets.ActivatedOrTriggeredAbility` slot for "up to one".
- `CopyTargetSpellEffect(target, keywordsForCopy, removeLegendary, addedTokenKeywords, sacrificeTokenAtStep, sacrificeTokenOnlyOnControllersTurn, copies = DynamicAmount.Fixed(1))` (facade `Effects.CopyTargetSpell(...)`) — copy a spell on the stack. `keywordsForCopy` grants keywords to the copy **while it remains a spell** (wither/lifelink). When the copied spell is a **permanent spell** it becomes a token as it resolves (CR 707.10f); the *token-side* riders bake onto that token for its life on the battlefield: `addedTokenKeywords` (e.g. `HASTE`) are unioned into the token's base keywords, and `sacrificeTokenAtStep: Step?` registers a delayed "sacrifice this token" trigger at the next matching step (`sacrificeTokenOnlyOnControllersTurn` gates it to "your next" step). The spell-copy mirror of `CreateTokenCopyOfTargetEffect.addedKeywords` / `sacrificeAtStep`. Used by **Choreographed Sparks** ("Copy target creature spell you control. The copy gains haste and 'At the beginning of the end step, sacrifice this token.'"). Pair with `Targets.CreatureSpellYouControl`. `copies` (a `DynamicAmount`, default 1) makes *N independent copies*, each retargeted separately (CR 707.10c) — **Thousand-Year Storm** ("copy it for each other instant and sorcery spell you've cast before it this turn") pairs `Effects.CopyTargetSpell(TriggeringEntity, copies = DynamicAmounts.spellsCastThisTurn(filter = InstantOrSorcery, beforeTriggeringSpell = true))` with `Triggers.youCastSpell(InstantOrSorcery)`. A count of zero or less makes no copies at all; a spell with no targets, or one with no legal replacement target, gets its copies without a prompt (inheriting the original's targets).
- `CopyEachTargetSpellEffect()` (facade `Effects.CopyEachTargetSpell(keywordsForCopy, removeLegendary)`) — copy **every** spell targeted by this effect (one copy per `ChosenTarget.Spell` in context), pausing per copy that has targets so the controller may choose new targets (CR 707.10). Pair with an unlimited spell target requirement — `Targets.AnyNumberOfInstantOrSorcerySpells`. Used by Display of Power ("Copy any number of target instant and/or sorcery spells."). Spells flagged `cantBeCopied` are skipped.
- `CopySpellForEachOtherPossibleTargetEffect(spell = EffectTarget.TriggeringEntity, candidates)` (facade `Effects.CopySpellForEachOtherPossibleTarget(candidates, spell)`) — copy a spell once **for each other object it could target**, auto-assigning every copy a distinct one of those objects (CR 707.10d). The Zada family: **Mirrorwing Dragon**, Zada, Hedron Grinder. This is the 707.10d shape, **not** 707.10c — no decision is made, so contrast `CopyTargetSpellEffect(copies = …)`, which makes N copies and pauses to let the controller *choose* new targets for each. The candidate set is every object matching `candidates` that is a legal target for **every** instance of the word "target" on the spell (the per-requirement legal-target sets are intersected, so hexproof/shroud/protection and per-requirement filters are honored — "any creature that couldn't be targeted … is just ignored"), minus the objects the spell already targets (the "each **other** …" of the card text). Each copy fills *all* of the spell's target slots with its one object; a modal spell keeps its chosen modes with its per-mode targets rewritten the same way (700.2g — "a different mode cannot be chosen"). **`candidates` and control of the copies both resolve against the copied spell's controller, not this ability's controller** — which is what makes one effect express both wordings: Zada's "each other creature **you** control" (a trigger only its own controller's casts fire) and Mirrorwing's "each other creature **they** control … **that player** copies" (a trigger watching every seat), so `GameObjectFilter.Creature.youControl()` reads as "creature the caster controls". Cast Murder on an opponent's Mirrorwing Dragon and *your* creatures each get a Murder. The copies aren't cast, so cast triggers (including the Dragon's own) don't refire; a spell flagged `cantBeCopied` yields no copies. Pair with `Triggers.anyPlayerCasts(InstantOrSorcery, requires = setOf(SpellCastPredicate.TargetsOnlySource))`.
- `CopyTargetTriggeredAbilityEffect(target)` — copy a triggered ability on the stack.
- `CopyTargetSpellOrAbilityEffect(target, copies = DynamicAmount.Fixed(1))` (facade `Effects.CopyTargetSpellOrAbility(target, copies)`) — copy whichever kind of stack object the single target resolved to, dispatching at resolution by inspecting the stack entity's component: an instant/sorcery **spell** copies via the spell-copy path, a **triggered ability** via `CopyTargetTriggeredAbilityEffect`'s logic, an **activated ability** by cloning its `ActivatedAbilityOnStackComponent`. You may choose new targets for the copy (CR 707.10c). Pair with `Targets.InstantSorcerySpellOrAbility` (one requirement admitting all four kinds). Generalizes the two single-kind copy effects into the "copy target instant/sorcery spell, activated ability, or triggered ability" clause — **Return the Favor**. `copies` (a `DynamicAmount`, default 1) makes *N independent copies* of an **ability** — pass `DynamicAmount.XValue` for "copy target activated or triggered ability you control X times" (**Gogo, Master of Mimicry**); the executor pauses per copy that has targets so each is retargeted independently, and a no-target ability is copied all the same. `copies` > 1 is honored on both branches — the spell branch forwards it to `CopyTargetSpellEffect.copies`. An ability instance tagged "can't be copied" (`ActivatedAbility.cantBeCopied`, see §9/§11) yields no copies (CR 707.10e).
- `CopyNextSpellCastEffect(copies = 1, spellFilter = InstantOrSorcery)` (facade `Effects.CopyNextSpellCast(copies, spellFilter)`) — when its controller next casts a spell matching `spellFilter` this turn, create `copies` copies of it. `spellFilter` is a `GameObjectFilter` matched against the spell as it's cast, so the default "instant or sorcery" (Howl of the Horde) can be widened — e.g. `GameObjectFilter.Creature` for "copy the next creature spell." The filter is evaluated with the rider's **own source** in the predicate context, so it may be source-relative — Loki Laufeyson's "with mana value less than or equal to Loki's power" is `InstantOrSorcery.manaValueAtMostDynamic(DynamicAmounts.sourcePower())`, resolved as the spell is cast (which is when the delayed trigger's condition is checked), not when the rider was created. Consumed after one matching cast. Non-matching casts leave the entry waiting. A source-relative filter keeps working after the source **leaves the battlefield** (CR 608.2h / 113.7a — the rider exists independently of its source): `ZoneTransitionService` stamps the departing permanent's `EntitySnapshot` onto the pending entry, and `PredicateEvaluator.evaluateDynamicCap` threads it into the reconstructed `EffectContext` so `DynamicAmountEvaluator`'s existing last-known-information branch resolves the cap. The stamp happens at **departure**, not at rider creation, so a source that grew after arming the rider caps on the larger value — Loki armed at 2/1, powered up to 4/3, then killed still copies a mana-value-4 spell.
- `CopyEachSpellCastEffect(copies = 1, spellFilter = InstantOrSorcery)` (facade `Effects.CopyEachSpellCast(copies, spellFilter)`) — the persistent sibling: copies **every** spell matching `spellFilter` the controller casts for the rest of the turn (The Mirari Conjecture Ch. III). Same `spellFilter` parameterization as above.
- `MakeNextSpellUncounterableEffect(spellFilter = Any)` (facade `Effects.MakeNextSpellUncounterable(spellFilter)`) — one-shot rider: the controller's **next** spell matching `spellFilter` cast this turn can't be countered, then the entry is consumed. Stamps `CantBeCounteredComponent` on that spell as it's cast (so it stays uncounterable for as long as it's on the stack); non-matching casts leave the entry waiting, and an unused entry clears at the start of the controller's next turn. Same pending-rider shape as `CopyNextSpellCastEffect`, including the source-relative filter contract (the entry's own `sourceId` goes into the predicate context at cast time). Contrast with the duration-based `GrantSpellsCantBeCountered` (Domri), which protects **every** matching spell cast for a whole duration rather than just the next one. Used by **Mistrise Village** ("{U}, {T}: The next spell you cast this turn can't be countered.").
- `GrantNextSpellAffinityEffect(spellFilter = Noncreature, forType = ARTIFACT)` (facade `Effects.GrantNextSpellAffinity(spellFilter, forType)`) — one-shot rider mirroring `MakeNextSpellUncounterable`, but the controller's **next** matching spell this turn gains **affinity for `forType`**: the cost calculator reduces it by the caster's count of that card type *at cast time* (dynamic), then `CastSpellHandler` consumes the entry. The *consumption* site evaluates `spellFilter` with the entry's own `sourceId` in context like the other two riders, but the *cost-reduction* site (`CostCalculator`) passes no `sourceEntityId` at all, so it answers `false` for dynamic **and** source-relative card predicates — so keep this rider's filter source-independent until that gap is closed. (This is a call-site difference, not a `CostCalculator` limitation: `GrantNextSpellFreeCastEffect` below passes its entry's `sourceId` into the same helper and source-relative predicates do work there.) Used by **Don & Raph, Hard Science** ("the next noncreature spell you cast this turn has affinity for artifacts").
- `GrantNextSpellFreeCastEffect(spellFilter = Any)` (facade `Effects.GrantNextSpellFreeCast(spellFilter)`) — one-shot rider in the same family: the controller's **next** spell matching `spellFilter` cast this turn **can be cast without paying its mana cost**, then the entry is consumed. Stored on `GameState.pendingFreeCastSpells`; `CostCalculator.hasFreeCastPermission` reads it (ahead of the battlefield scan) so the cast surfaces the ordinary `CastSpell.useWithoutPayingManaCost` action variant, and `CastSpellHandler` removes the entry on the matching cast. Per CR 118.9 this is an alternative cost — mandatory additional costs still apply, X is 0 (CR 107.3b, enforced by the enumerator: the `CastWithoutPayingManaCost` action variant carries no X and is not flagged `hasXCost`), and only one alternative cost may apply to a cast (CR 118.9a). **Consumed by the cast, not by the discount**: "the next … spell you cast this turn" names a spell, so a matching spell cast for full price is that spell and spends the rider. Non-matching casts leave the entry waiting, and an unused entry clears at the turn boundary (`TurnManager.startTurn`). Prefer this over the battlefield static `MayCastWithoutPayingManaCost` (§ static abilities) whenever the permission has already *resolved*: the rider lives on the state, so it survives its source leaving the battlefield, applies to a cast from any zone, and carries no first-spell / once-per-turn / active-player gate. A rider-funded free cast deliberately does **not** burn a `MayCastWithoutPayingManaCost(oncePerTurn = true)` source's use. Both the permission site and the consumption site evaluate `spellFilter` with the entry's own `sourceId` in context, so source-relative predicates work on both — but the permission site is `CostCalculator`, which still answers `false` for *dynamic* card predicates (mana-value/power comparisons against another entity). The two sites also read different *characteristic* sources: `CostCalculator` matches the printed `CardDefinition`, while the consumption site matches the spell entity through `PredicateEvaluator` (projected values, falling back to base). They agree today because objects on the stack have no projection entry; see the `hasFreeCastRider` KDoc for what would diverge if that changed. Used by **World War Hulk** chapter I ("The next red or green creature spell you cast this turn can be cast without paying its mana cost.", `GameObjectFilter().withAnyColor(RED, GREEN) and GameObjectFilter.Creature`).
- `ReduceSpellCostsThisTurnEffect(spellFilter, amount)` (facade `Effects.ReduceSpellCostsThisTurn(spellFilter, amount)`) — the **repeating** counterpart of `GrantNextSpellAffinityEffect`: "spells you cast this turn that match `spellFilter` cost {X} less to cast." `amount` (a `DynamicAmount`) is evaluated **once, when this effect resolves**, and the resolved number is stored on `GameState.turnSpellCostReductions`; every matching spell the controller casts for the rest of the turn is discounted by it, and nothing is consumed by a cast. Only generic mana is reduced (CR 601.2f). Two consequences of living on the state rather than on the source: the discount survives the source leaving the battlefield, and it is cleared at the turn boundary by `TurnManager.startTurn`. Resolving `amount` up front is what the Scion cycle's rulings require ("the value of X is determined only once, at the time the ability resolves") — reach for a static `ModifySpellCost` instead when the reduction should track board state continuously. Used by **Will, Scion of Peace** (`DynamicAmounts.lifeGainedThisTurn()`, white and/or blue spells) and **Rowan, Scion of War** (`DynamicAmounts.lifeLostThisTurn()`, black and/or red).
- `CopyCardIntoCollectionEffect(source, storeAs)` (facade `Effects.CopyCardIntoCollection(source, storeAs)`) — copy a **card in a zone** (not a spell on the stack), publishing the copy's entity id to pipeline collection `storeAs`. Per Rule 707.12 the copy is created in the card's current zone under the effect's controller and tagged as a stack-style copy, so once cast it becomes a token if it's a permanent spell and ceases to exist if it's an instant/sorcery (Rule 707.10). Pair with `CastFromCollectionWithoutPayingCostEffect(from)` (facade `Effects.CastFromCollectionWithoutPayingCost(from)`, wrap in `MayEffect` for "you may cast") to express "copy a card, then cast the copy" — e.g. **Shiko, Paragon of the Way**: `Composite(MoveToZoneEffect(target, Zone.EXILE), Effects.CopyCardIntoCollection(target, "copy"), MayEffect(Effects.CastFromCollectionWithoutPayingCost("copy")))`. A copy that is never cast is swept up by the Rule 707.10a state-based action (`PhantomCardCopiesCheck`), so no explicit cleanup step is needed. For the "you may cast it" wording that **doesn't** say "without paying its mana cost", use `Effects.CastFromCollection(from, storeCastTo?)` (`CastFromCollectionWithoutPayingCostEffect(from, payManaCost = true, storeCastTo)`): the controller pays the spell's normal cost (an {X} spell prompts for X) instead of casting for free. Pass `storeCastTo` to publish the cast card's id to that pipeline collection on a successful cast, then gate a follow-up with `IfYouDoEffect(this, then, SuccessCriterion.CollectionNonEmpty(storeCastTo))` — e.g. **Kaervek, the Punisher**: `Composite(Move(target, EXILE), CopyCardIntoCollection(target, "copy"), MayEffect(IfYouDoEffect(CastFromCollection("copy", storeCastTo = "cast"), LoseLife(2, Controller), SuccessCriterion.CollectionNonEmpty("cast"))))` — declining (or being unable to pay) leaves the collection empty, so no life is lost. (`storeCastTo` is reliably published for synchronous casts and target-selection casts; an {X}-cost spell cast with no targets is the one sub-case where the publish doesn't survive the X pause.) **Free-casting still pays the copied spell's non-mana additional costs** (CR 601.2f / 118.9 waive only the mana cost) — when the copy carries a printed sacrifice / discard / exile / tap additional cost, the engine resolves it during the synthesized cast: a forced single option is auto-paid, and a real choice pauses for an on-battlefield (sacrifice/tap) or overlay (discard/exile) selection; if the cost can't be paid the cast doesn't happen (e.g. Roving Actuator copying **Embrace Oblivion**'s "sacrifice an artifact or creature" still makes you sacrifice).
- `CopyCollectionIntoCollectionEffect(from, storeAs)` (facade `Effects.CopyCollectionIntoCollection(from, storeAs)`) — the collection-wide sibling of `CopyCardIntoCollectionEffect`: copy **every** card in pipeline collection `from`, publishing all the copies' entity ids (in `from` order) to `storeAs`. For "copy them" over a set of cards rather than one (`CopyCardIntoCollection` overwrites its collection, so it can't accumulate across a `ForEach`). Each copy is created in its original's current zone (Rule 707.12) and tagged as a stack-style copy, so gather/exile the originals first, then copy. Pair with `Effects.CastAnyNumberFromCollection(storeAs)` for "copy them. You may cast any number of the copies" — e.g. **The Tale of Tamiyo** IV: `Composite(ForEachTargetEffect(Move(ContextTarget(0), EXILE)), GatherCards(ChosenTargets, "exiled"), CopyCollectionIntoCollection("exiled", "copies"), CastAnyNumberFromCollection("copies"))`. Copies never cast are swept by the Rule 707.10a state-based action.
- `CastFromCollectionWithoutPayingCostEffect(from, payManaCost = false, storeCastTo = null, castTransformed = false, insteadOfGraveyard = null, caster = Chooser.Controller)` — `castTransformed = true` casts the card **transformed**, back face up (CR 712.8c), the way disturb casts a card from the graveyard: the back face supplies the spell's card types (hence its timing), its targets and `auraTarget`, its name in the prompt, and the permanent it becomes. It is carried to the cast as `MayPlayPermission.castTransformed`, so the whole ordinary cast pipeline honors it — distinct from `MayPlayPermission.castFaceIndex`, which picks an alternative *face* of a multi-face card (an Adventure, a split half) rather than turning a transforming double-faced card over. A card with **no back face** is not cast at all and stays where it is (the CR 310.12b ruling: a token or non-transforming card that became a copy of a Siege "remains in exile"). Backs `Sieges.defeatAbility` — "exile it, then you may cast it transformed without paying its mana cost".

  `insteadOfGraveyard` is the **cast-this-way destination rider**: an `AfterResolveDestination`
  naming where the spell goes when it would leave the stack for its owner's graveyard — `EXILE`
  ("exile it instead", **Flotsam // Jetsam**'s Jetsam half) or `BOTTOM_OF_LIBRARY` ("put it on the
  bottom of its owner's library instead", **Kylox's Voltstrider**). It is stamped on the card as
  `AfterResolveDestinationComponent` when the cast is granted, so it travels with the card onto the
  stack, and `StackResolver` honors it at all four points a spell can leave the stack for a
  graveyard — resolved, resolved-while-paused, countered (CR 701.5a) and fizzled (CR 608.2b). Two
  properties it is worth relying on: it is scoped to the **one card actually being cast**, so a
  declined or impossible pick leaves nothing behind on cards still in the collection; and it
  outranks the card-intrinsic exile reasons (flashback, rebound, an Adventure face), being the only
  one of them that can name a zone other than exile. Leave it `null` for the ordinary destination.
  The same component, with its default `EXILE`, is what `GrantMayPlayFromExile(exileAfterResolve)`
  and `MayCastFromGraveyard(exileInsteadOfGraveyard)` have always stamped — one mechanism, now with
  a destination.

  `caster` answers the one question a per-player iteration raises. Inside
  `ForEachPlayerEffect(Player.EachOpponent, …)` the context's controller is rebound to the iterated
  player, so a card that casts a spell out of *each opponent's* graveyard while **you** remain the
  caster passes `caster = Chooser.SourceController` — the chooser that reads through the swap to the
  spell's own controller (it resolves via `EffectContext.effectControllerId`, which is captured for
  exactly this purpose and is the only thing that works for a resolving *spell*, whose stack entity
  has a caster but no `ControllerComponent`). Pair it with the same `Chooser` on the upstream
  `SelectFromCollection`, or the opponent would be handed the picker too. The default
  `Chooser.Controller` is every non-iterated card.
- `CastAnyNumberFromCollectionWithoutPayingCostEffect(from, payManaCost = false, maxCasts = null)` (facades `Effects.CastAnyNumberFromCollectionWithoutPayingCost(from)` for free / `Effects.CastAnyNumberFromCollection(from)` for paid / `Effects.CastUpToNFromCollectionWithoutPayingCost(from, maxCasts)` for the capped free form) — the multi-cast sibling of `CastFromCollectionWithoutPayingCostEffect`. **During this effect's resolution**, the controller is offered the cards in pipeline collection `from` (filtered to those still in exile) one at a time and may cast each until they decline; each cast's targets / X / modes flow through the normal cast machinery. With the default `payManaCost = false` each is cast for free; set `payManaCost = true` (facade `Effects.CastAnyNumberFromCollection`) for the "you may cast any number of [them]" wording **without** "without paying their mana costs" — each chosen card is then cast paying its normal cost (an {X} card prompts for X). Because the casts go through the synthesized-cast path (like Cascade), card-type **timing restrictions are ignored** and no lingering "you may play it later" permission is granted — cards left uncast just stay where they are (the controller can't wait until later in the turn). Hand it the eligible set: filter the collection upstream (e.g. nonland + `FilterCollection(ManaValueAtMost(...))`). The free form models "you may cast any number of spells with mana value X or less from among them without paying their mana costs" — e.g. **Kotis, the Fangkeeper**: `GatherCards(TopOfLibrary(damage, TriggeringPlayer)) → MoveCollection(→ exile) → FilterCollection(Nonland) → FilterCollection(ManaValueAtMost(damage)) → CastAnyNumberFromCollectionWithoutPayingCostEffect("castable")` (also **Villainous Wealth**, **Etali, Primal Storm**). The paid form models **The Tale of Tamiyo** IV (cast the copies paying their costs). `maxCasts` bounds the loop for the "you may cast **up to N** spells from among them" wording (**Doom Reigns Supreme**: "target opponent exiles the top five cards of their library. You may cast up to two spells from among the exiled cards without paying their mana costs"); it is a ceiling only — the controller may still stop early, and the loop also ends when the collection runs out. The remaining budget rides on the engine's `CastAnyNumberFromCollectionContinuation`, so the resumer re-enters the loop with `maxCasts - 1` and a budget of 0 makes the effect a no-op before another decision is offered; `null` (the default) is the uncapped "any number" form and leaves every existing caller unchanged. The budget is spent on a cast that **initiates**, not on the pick: a chosen card whose required target has no legal choice can't be cast at all (CR 601.2c), so it stays in exile and the count is untouched (it is still dropped from the pool, so the loop can't re-offer it). Use the facade rather than the raw constructor — it rejects a non-positive `maxCasts`, which would otherwise be a silent no-op, and it only offers `maxCasts` alongside the free form. **`maxCasts` is wired for `payManaCost = false` only**: no printed card pairs "up to N" with "paying their mana costs", and the engine's "did the cast initiate" precondition asks only whether a required target had a legal choice, not whether the controller can afford the cost — so a pick abandoned for want of mana would still spend one of the N. Wire the affordability check before authoring that combination.
- `FilterCollection(from, CollectionFilter.InZone(zone), storeMatching)` — keep only the cards in pipeline collection `from` that are **currently** in `zone`. Pipeline collections track entity refs, not live location, so a card can leave its zone mid-resolution (e.g. an exiled card cast for free moves to the stack). Use this to act on "the ones still there." Models the "you may cast it … if you don't, put that card into your hand" fallback of the **Tarkir: Dragonstorm "…storm" enchantments** (Breaching Dragonstorm): `GatherUntilMatch(Nonland) → MoveCollection(→ exile) → FilterCollection(ManaValueAtMost(8), "castable") → ConditionalOnCollection("castable", ifNotEmpty = MayEffect(CastFromCollectionWithoutPayingCost("castable"))) → FilterCollection("nonland", InZone(EXILE), "uncast") → MoveCollection("uncast" → hand)` — only the nonland still in exile (not the one just cast) goes to hand; the lands stay exiled. The `ConditionalOnCollection` wrapper suppresses the empty "you may cast" prompt when the nonland's mana value is > 8.
- `MoveCollectionEffect(from, destination, filter = null, …)` — move a pipeline collection to a zone.
  `destination = ToZone(zone, player, placement)` or `ToZoneExiledFrom(fallback = BATTLEFIELD)`
  (below); `ZonePlacement.Tapped` enters the battlefield
  tapped, and `player` sets the controller for a battlefield destination (so a card can enter under
  your control even when owned by an opponent — Sméagol). The optional `filter` moves only the cards
  in `from` matching it (the rest stay), letting one revealed pile be split by type in successive
  steps — e.g. revealed lands → battlefield tapped, the rest → graveyard/library (Sméagol, Galadriel
  of Lothlórien, The Ring Goes South). (Equivalent to a `FilterCollection` partition; the inline
  filter just avoids naming an intermediate collection.) `storeMovedAs = "<key>"` captures the
  resulting entity ids under a pipeline collection; `markEnteredViaSourceAbility = true` stamps each
  card that lands on the battlefield with `EnteredViaAbilityComponent(this source)` so a later
  `GatherCards(CardSource.EnteredViaThisResolution)` can re-collect them from live battlefield state.
  `lookableInExile = true` is the "**You may look at that card for as long as it remains exiled**"
  rider: each card this move lands **face down in exile** is stamped `MayLookAtInExileComponent(the
  effect's controller)`, which `Visibility.grantsFaceDownExileAccessTo` honours alongside foretell and
  an active may-play permission. It is a *look* grant and nothing else — CR 708.5 gives exile no
  controller baseline ("You can't look at face-down cards in any other zone"), so before this the only
  ways to see a face-down exiled card were foretell and a play permission, and **Jacob Hauken,
  Inspector**'s front face grants neither ("exile a card from your hand face down. You may look at
  that card…", with the permission to play arriving only on the back face). Ignored for a card that
  ends up face up or outside exile; the grant is read only from the exile branch, so it ends when the
  card leaves exile.
- `CardDestination.ToZoneExiledFrom(fallback = Zone.BATTLEFIELD)` — a **per-card** destination: each
  card goes back to the zone it was exiled from, i.e. CR 610.3's "this second one-shot effect returns
  the object to its previous zone". Backed by `ExiledFromZoneComponent`, which
  `ZoneTransitionService` stamps on every object it moves into the exile zone — that is every
  *effect-driven* exile — and clears again as it leaves. It is **not** a single choke point: the few
  direct-`addToZone` exile sites that matter (exile as a cost in `CostPaymentService` and
  `CastSpellHandler`, `ExileOpponentsGraveyardsExecutor`, the graveyard sweep in
  `SbaZoneMovementHelper`) write the same stamp explicitly, `StackResolver`'s spell-to-exile moves
  record nothing (a stack origin takes `fallback` either way), and anything else that reaches exile
  without a stamp lands on `fallback` — so **`fallback` is load-bearing, not a corner case**. The
  executor groups the collection by recorded zone and runs each group through the ordinary `ToZone`
  path, so owner routing, aura targeting and events behave identically; the battlefield group runs
  last because it is the one that can pause for an Aura's enchant target. The per-group `ToZone`
  carries no `player`/`placement` — owner routing comes from the zone transition itself and from
  `underOwnersControl` — so a card returning to a library lands at the default placement rather than
  the index it left from (CR 610.3 fixes the zone, not a position). A card returning to the
  battlefield is a **new object** (CR 400.7) and should be paired with `underOwnersControl = true`
  (CR 610.3c) — which the `Effects.ReturnLinkedExileToZoneExiledFrom()` facade does. Cards recorded
  as exiled from the **stack**, and cards with no recorded origin, take `fallback`; a card exiled
  **from exile** (CR 406.7, "it doesn't change zones") is left where it is, with no move and no
  event. `SuccessCriterion.Auto` cannot infer success from this destination (there is no single zone
  to snapshot), so an `IfYouDo` over it must state its criterion explicitly.
- `ChangeTargetEffect(spell, newTarget)` — change a spell's target.
- `ChangeSpellTargetEffect(spell, filter)` — same, filtered.
- `ReselectTargetRandomlyEffect(spell)` — re-choose targets at random.
- `Effects.ChangeTriggeringObjectTargets(chooser = RetargetChooser.Controller, spell = EffectTarget.TriggeringEntity)` — the player named by `chooser` may change the target or targets of `spell`, defaulting to the triggering spell/ability (`context.triggeringEntityId`); the player-chosen, multi-target counterpart of `ReselectTargetRandomly`. Pass a `ContextTarget`/`BoundVariable` for "you may choose new targets for target instant or sorcery spell" (**Wild Ricochet**, `Composite(ChangeTriggeringObjectTargets(spell = spell), CopyTargetSpell(spell))` — retarget first so the copy inherits the new targets, and `CopyTargetSpell` carries the copy's own "you may choose new targets" prompt). This is the *all*-targets retarget; `ChangeTarget` swaps one target and no-ops on a spell with more than one, so a card whose text says "targets" wants this even when it names its spell as a target. `RetargetChooser.Controller` = the effect's controller; `RetargetChooser.OwnerOfStored(name)` = the owner of the single card in pipeline collection `name` (≠1 card → no chooser → no-op). Reselection is offered slot-by-slot among the original object's legal targets (legality judged from *its* controller, current target kept as a "keep" option, no target chosen twice). **Psychic Battle** composes from atoms: `Composite(GatherCards(TopOfLibrary(1, Player.Each), revealed=true, storeAs="revealed"), FilterCollection("revealed", GreatestManaValue, storeMatching="w"), ChangeTriggeringObjectTargets(RetargetChooser.OwnerOfStored("w")))` — a tie keeps several greatest cards so `OwnerOfStored` finds no unique owner and the targets stay put.
- `ReturnSpellToOwnersHandEffect` / `Effects.ReturnSpellToOwnersHand()` — return the targeted spell (`ContextTarget`) from the stack to its owner's hand. Not a counter (CR 701.27 / 701.5b), so "can't be countered" doesn't block it. Pair with `Targets.Spell` (Reprieve, Hullbreaker Horror).
- `Effects.ReturnSpellOrPermanentToOwnersHand(target = ContextTarget(0))` (`ReturnSpellOrPermanentToOwnersHandEffect`) — bounce one target to its owner's hand, dispatching on what it resolves to: a **spell on the stack** is removed from the stack to hand (does not resolve; not a counter), a **permanent** is bounced normally (delegates to the standard `MoveToZone(HAND)` path with its full leave-the-battlefield cleanup). The bounce counterpart of `PutOnLibraryPositionOfChoiceEffect`. Pair with `TargetSpellOrPermanent` for "return target spell or nonland permanent…" cards (Press the Enemy); that requirement filters its two halves independently via `permanentFilter` / `spellFilter` (see *Spell-or-permanent targets*). To cap a follow-up free-cast at the bounced object's mana value, capture it first with `Effects.StoreNumber("mv", DynamicAmount.EntityProperty(EntityReference.Target(0), EntityNumericProperty.ManaValue))` before bouncing, then read it via `DynamicAmount.VariableReference("mv")`.

### Combat-shape & misc

- `PreventDamageEffect(target, recipientGroup, amount, direction, scope, sourceFilter, onPrevented, gainLifeFromColors, duration, nextInstanceOnly)` — prevention shield. `amount = null` prevents all. **`recipientGroup: GroupFilter?`** is the recipient-side analogue of `sourceFilter = FromGroup(...)` (which filters the *source* of damage): when set, the shield protects **every** permanent matching the group instead of a single `target` — "prevent all damage that would be dealt to creatures you control this turn" (Summon: Alexander = `Effects.PreventAllDamageToGroup(GroupFilter.AllCreaturesYouControl)`). The group is re-evaluated against projected state at the moment each damage instance would be dealt, with the shield's controller as the "you" reference, so permanents that come under your control later in the turn are protected too; it covers both combat and noncombat damage, and honours `scope` (pass `PreventionScope.CombatOnly` for "prevent all combat damage to creatures you control") and `duration`. Players are not creatures, so a "creatures you control" shield never protects the player — for the "**you and** creatures you control" recipient set (Eerie Interference, Riot Control) set **`recipientGroupIncludesController = true`**, which adds the shield's controller to the recipients (a `GroupFilter` can never match a player), and pass a `sourceFilter = FromGroup(...)` to narrow it to "… by creatures"; both filters are re-evaluated at damage time and an unidentifiable damage source fails **closed** (never prevented). Leaving `recipientGroup` **null** while `recipientGroupIncludesController = true` names *you alone* — "prevent all damage that would be dealt to you this turn by creatures with flying" (Scarecrow = `Effects.PreventAllDamageToYouFrom(GroupFilter(GameObjectFilter.Creature.withKeyword(Keyword.FLYING)))`), so the three recipient-side shapes (permanents / you and permanents / you) are one shield with a different half filled in rather than three effect types. Facades: `Effects.PreventAllDamageToGroup(group, scope = AllDamage, duration = EndOfTurn)`, `Effects.PreventAllDamageToYouAndGroup(group, fromSources = null, scope = AllDamage, duration = EndOfTurn)`, `Effects.PreventAllDamageToYouFrom(fromSources, scope = AllDamage, duration = EndOfTurn)`. The next "prevent all damage to artifacts / to each opponent's creatures" card needs only a different `GroupFilter`, not a new effect. `sourceFilter` can be `ChosenSource` (player picks any source on resolution), `ChosenColoredSource` (player picks a source on resolution, but only colored sources are offered — "a source of your choice that shares a color with the mana spent"; a colorless source qualifies for nothing, so it's never offered — Protective Sphere), or `ChosenSourceMatching(filter)` (player picks a source, but only sources matching the `GameObjectFilter` are offered — the parameterized "a [quality] source of your choice" form; `ChosenSourceMatching(GameObjectFilter.Artifact)` is Circle of Protection: Artifacts, and a future "an enchantment/red/… source of your choice" Circle reuses the same variant with a different filter). `nextInstanceOnly` (default false) is **orthogonal to the eligibility filter**: with `amount = null`, `true` prevents only the *next whole damage instance* from the chosen source then consumes the shield (the Circle of Protection family), while `false` prevents *all* damage from that source for the `duration` (Samite Ministration). Facade `Effects.PreventNextDamageFromChosenArtifactSource(target)` sets `ChosenSourceMatching(Artifact)` + `nextInstanceOnly = true`. **`halvePreventedDamage`** (default false) is a modifier *on* the single-instance shield: with `nextInstanceOnly = true` it prevents only **half** the instance, rounded down, and the rest is still dealt — Dark Sphere. Because it is the same next-instance shield, it is consumed even when it prevents nothing (a 1-damage instance halves to 0). Facade `Effects.PreventHalfNextDamageFromChosenSource(target)`. **`direction` is a third orthogonal axis for chosen sources, and it selects the recipient scope**: the default `ToTarget` shields one recipient against the chosen source (Samite Ministration's "dealt to you … by a source of your choice"), while `FromTarget` with `amount = null` drops the recipient clause entirely — "prevent all damage that would be dealt this turn by a source of your choice", to *anything* (Mourner's Shield). The recipient-free form installs the same `PreventAllDamageDealtBy` silence shield that a **targeted** `PreventionDirection.FromTarget` does, so combat and noncombat damage are covered by the same two read sites, and `effect.target` is not consulted at all. Facade: `Effects.PreventAllDamageFromChosenSourceMatching(filter)`. The eligibility `filter` is evaluated relative to the **ability's source**, so it may name the source or something hanging off it — Mourner's Shield's "that shares a color with the exiled card" is `GameObjectFilter.Any.sharingColorWith(EntityReference.LinkedExiledCard())`. `onPrevented: Effect?` is an **arbitrary follow-up effect** run when a single-instance shield prevents an instance of damage (see below). `gainLifeFromColors: Set<Color>` makes the shield's controller gain that much life whenever it prevents damage from a source of one of those colors (Samite Ministration). Facades: `Effects.PreventNextDamage`, `Effects.PreventAllCombatDamageTo(target, duration = EndOfTurn)` ("prevent all combat damage that would be dealt to it this turn" — Fleeting Flight), `Effects.PreventAllDamageDealtBy(target, duration = EndOfTurn, scope = AllDamage)` (the source-side shield — the target deals no damage at all; pass `Duration.WhileSourceOnBattlefield` for the open-ended "for as long as this Saga remains on the battlefield" wording — Old Fat Spider Can't See Me, and `PreventionScope.CombatOnly` when the printed line says "prevent all **combat** damage that would be dealt by …" — Restrain, Safeguard. The scope is not cosmetic: at the default `AllDamage` such a card also silences the creature's activated damage abilities, which its oracle text does not say), `Effects.PreventNextDamageDealtBy(target, onPrevented)` (the target's next whole damage instance is prevented, then a linked delayed trigger runs against `DynamicAmounts.preventedDamage()` — Awe Strike), `Effects.PreventNextDamageFromChosenSource(amount, target)`, `Effects.PreventNextDamageFromChosenSource(onPrevented)`, `Effects.PreventNextDamageFromChosenArtifactSource(target)`, `Effects.PreventHalfNextDamageFromChosenSource(target)`, `Effects.PreventAllDamageFromChosenSource(target, gainLifeFromColors)`, `Effects.PreventAllDamageFromChosenColoredSource(target)`, `Effects.DeflectNextDamageFromChosenSource()`, `Effects.ReflectNextDamageFromChosenSourceToController()`. The `preventDamage` flag (default true) — when **false**, the chosen-source shield does NOT prevent the damage (it still hits in full) but still fires its `onPrevented` reaction with the captured amount; this is the "instead it still deals that damage to you AND deals that much to its controller" shape (Eye for an Eye), as opposed to the deflect/prevent shape (Deflecting Palm).
  - **Prevent-and-react (`onPrevented`)** — instead of a bespoke reaction type, the chosen-source shield runs **any composed effect** when it fires, as a real triggered ability on the stack ("When damage is prevented this way, …", CR-faithful — opponents get priority and can respond). Mechanically: on resolution the shield is created **and** a linked event-based delayed triggered ability (`CreateDelayedTriggerEffect`-style) whose `effect` is `onPrevented`; when the shield prevents an instance it emits an internal `DamagePreventedEvent` that fires only that delayed trigger (matched by id). Inside the trigger the prevented amount is `DynamicAmounts.preventedDamage()` ("that much"/"that many") and the prevented source's controller is `EffectTarget.ControllerOfTriggeringEntity` ("that source's controller") — the same pair Tephraderm uses. So Deflecting Palm's `onPrevented` = `DealDamage(ControllerOfTriggeringEntity, preventedDamage())`; New Way Forward's = `Composite(DealDamage(ControllerOfTriggeringEntity, preventedDamage()), DrawCards(preventedDamage()))`. Because the payoff is a normal stack ability, it may be interactive (targets, replacements) like any other.
- `RedirectNextDamageEffect(protectedTargets, redirectTo, amount, scope)` — redirection shield (CR 614.9):
  while active, damage that would be dealt to any of `protectedTargets` this turn is dealt to `redirectTo`
  instead. Installed as a `Duration.EndOfTurn` floating effect and checked during damage resolution.
  `amount` caps the redirected damage (`null` = redirect all). **`scope` decides when an unlimited shield is
  used up** (a capacity shield, `amount != null`, is always used up once its capacity hits 0 — CR 615.7):
  - `RedirectScope.NEXT_INSTANCE` (default) — one source's instance, then gone. "The next time a source of
    your choice would deal damage to you…" (Beacon of Destiny).
  - `RedirectScope.NEXT_BATCH` — every instance dealt in the next *simultaneous moment*, then gone. Because
    all combat damage is dealt simultaneously (CR 510.2), this redirects a whole combat damage step — every
    attacker hitting the protected player/creature — not just the first. "The next time damage would be dealt
    to X and/or you…" (Glarecaster). The shield survives the per-assignment apply loop (`inBatch=true`) and is
    consumed once afterwards by `CombatDamageManager.consumeBatchRedirectShields`.
  - `RedirectScope.CONTINUOUS` — never self-consumed; redirects every instance until end of turn.
    "All damage that would be dealt to it this turn…" (Karona's Zealot).
  Distinct from the static `RedirectDamage` replacement (continuous, source-keyed) listed under Replacement
  effects — this one is a one-shot/duration shield generated by a resolving ability.
- `Effects.BecomeCreature(target, power, toughness, keywords, creatureTypes, removeTypes, addTypes, colors, imageUri, duration, dynamicPower = null, dynamicToughness = null)`
  (`BecomeCreatureEffect`) — animate / "becomes a creature." Adds CREATURE (Layer 4, keeping the permanent's
  existing types — so "it's still a land"), *sets* creature subtypes to `creatureTypes` (replacing all others),
  removes `removeTypes`, grants additional card types via `addTypes` (e.g. `setOf("ARTIFACT")` for **Mishra's
  Factory** — "becomes a 2/2 Assembly-Worker artifact creature. It's still a land"), *sets* `colors` (Layer 5,
  replacing all others; `null` = keep), grants `keywords` (Layer 6), and sets base P/T (Layer 7b set
  values). **`power`/`toughness` are `DynamicAmount`** (evaluated once at resolution, CR 613.4c, then
  stamped as a fixed set-P/T floating effect); an `Int` convenience overload wraps them in
  `DynamicAmount.Fixed`. Optional **`imageUri`** is a *display-only* card-art override shown for the
  animated permanent while the effect lasts (e.g. a token's art for "becomes a Fractal") — it changes
  no characteristic (stored as a `SerializableModification.OverrideImage` floating effect that maps to
  `NoOp`, read directly by `ClientStateTransformer` via `GameState.imageOverrideFor`) and reverts with
  the rest of the animate at cleanup; `null` keeps the permanent's own art. The optional
  `dynamicPower`/`dynamicToughness` (`DynamicAmount`, supplied together) instead make the Layer 7b base-P/T a
  *dynamic* `SetPowerToughnessDynamic` recomputed at projection rather than locked in at resolution — the
  single-target companion to `MassAnimateEffect`. The amounts are evaluated with the **animating source's
  controller** as `you`, so a controller-scoped count works: **Beorn's Hospitality**
  ("{5}{G}{G}: This enchantment becomes a Bear creature in addition to its other types and gains 'This
  creature's power and toughness are each equal to the number of lands you control.' (This effect doesn't
  end.)") is `BecomeCreature(EffectTarget.Self, power = Fixed(0), toughness = Fixed(0),
  creatureTypes = {"Bear"}, duration = Duration.Permanent, dynamicPower = dynamicToughness =
  DynamicAmounts.landsYouControl())` — the printed `power`/`toughness` are only the rules-text display
  (rendered `*/*`), and the enchantment keeps its Enchantment type. Facade
  `Effects.BecomeCreatureWithManaValueStats(target, addTypes, keywords, creatureTypes, duration)` wires P/T = the
  animated permanent's own mana value (`EntityProperty(AffectedEntity, ManaValue)`) for **Xenic Poltergeist**
  ("Until your next upkeep, target noncreature artifact becomes an artifact creature with power and toughness
  each equal to its mana value"). Sarkhan's "4/4 Dragon" (fixed); Fractalize's "green and blue Fractal with base
  power and toughness each equal to X plus 1, losing all other colors and creature types"
  (`power = toughness = Add(XValue, Fixed(1))`, `creatureTypes = {"Fractal"}`, `colors = {GREEN, BLUE}`,
  `imageUri =` the Fractal token's Scryfall art).
- `BecomeArtifactEffect(target, cardTypes = {"ARTIFACT"}, subtypes = emptySet(), colors = emptySet(), loseAllAbilities = true, name?, grantedAbility?, grantedStaticAbilities = [], duration = Permanent)` — the general "becomes a Treasure/Food/Clue/artifact" transform: stacks continuous floating effects on `target` — Layer 3 `SetName` when `name` is set, Layer 4 `SetCardTypes` (replaces *all* card types) + `SetAllSubtypes` (replaces *all* subtypes), Layer 5 color (`emptySet()` = colorless), Layer 6 `RemoveAllAbilities` when `loseAllAbilities` — plus an optional single `grantedAbility` recorded durably in `grantedActivatedAbilities` (so it survives the ability wipe; the enumerators read it after the projected `lostAllAbilities` check). That durable record honours source-keyed durations as well as `Permanent`/`EndOfTurn`: it carries the granting permanent's id, and `EndedDurationExpiryCheck` prunes it the moment a `Duration.WhileSourceOnBattlefield` gate closes, so the granted ability dies with its granter instead of outliving it (**Kitesail Larcenist**: the chosen permanents stop being sacrificeable Treasures the instant Kitesail leaves). `name` renames per CR 612.8 ("loses any names it had and has only the specified name"); only supertypes survive, so LEGENDARY stays but the permanent can now collide with a same-named permanent under the legend rule. `grantedStaticAbilities` is the **static** counterpart to `grantedAbility` — a static ability only does anything if it *projects*, so these are lowered to `ContinuousEffectData` and appended to the permanent's own `ContinuousEffectSourceComponent` (the channel a printed static uses), which is also what exempts them from the wipe: `StateProjector` never suppresses a source's own continuous effects with that source's own `RemoveAllAbilities`. Because that channel is the permanent's component, the grant lives exactly as long as the permanent stays on the battlefield — pair it with `Duration.Permanent`. **The Irencrag**: "you may have this become a legendary Equipment artifact named Everflame, Heroes' Legacy. If you do, it gains equip {3} and \"Equipped creature gets +3/+3\" and loses all other abilities" — `name = "Everflame, Heroes' Legacy"`, `subtypes = {"Equipment"}`, `colors = null` (keep colorless without recolouring), `grantedAbility =` the equip {3} `ActivatedAbility`, `grantedStaticAbilities = listOf(ModifyStats(3, 3))` (whose default `GroupFilter.attachedCreature()` scope makes it the equipped creature's bonus). `Duration.Permanent` ends only when the permanent leaves the battlefield. Differs from `BecomeCreatureEffect` (which *adds* CREATURE + sets P/T): this fully replaces types/subtypes so the result is exactly the named artifact. `cardTypes = null` **keeps** the permanent's card types unchanged (only subtypes/color/abilities are touched) — used when a land "loses all land types and abilities" but stays a land and keeps any other card types (Ultima, Origin of Oblivion: `cardTypes = null, subtypes = emptySet(), colors = null, grantedAbility = {T}: Add {C}, duration = Durations.whileAffectedHasCounter(Counters.BLIGHT)`). `subtypes = null` is the same skip one layer down — it **keeps** the permanent's existing subtypes, as distinct from `emptySet()` which strips them all; reach for it when the transform only renames or grants (**Tenth District Hero**: "it becomes a legendary creature named Mileva, the Stalwart … and it gains \"Other creatures you control have indestructible\"" — `cardTypes = null, subtypes = null, colors = null, loseAllAbilities = false, name = "Mileva, the Stalwart", grantedStaticAbilities = listOf(GrantKeyword(INDESTRUCTIBLE, OtherCreaturesYouControl))`, leaving the Human Detective types the card's *first* ability set). Note that this effect — not `Effects.GrantStaticAbility` — is the route for a runtime static that has to **project**: `GrantStaticAbility` writes to the point-of-use `grantedStaticAbilities` store, which combat/cast checks consult but the layer projector never reads, so a `SetName`, `GrantKeyword`, or `ModifyStats` handed over that way is silently inert. (Vraska, the Silencer: a dead opponent's creature returns as a bare colorless Treasure with the sac-for-mana ability.)
- `BecomeSaddledEffect(target = Self)` (facade `Effects.BecomeSaddled()`) — target permanent becomes saddled until end of turn (CR 702.171b). The resolving half of a Saddle ability: stamps the transient `SaddledComponent` marker (cleared at end of turn / on leaving the battlefield; not copiable) and emits `BecameSaddledEvent`. No P/T or type change — read the marker with `Conditions.SourceIsSaddled` / `.saddled()`.
- `BecomeRenownedEffect(target = Self)` (facade `Effects.BecomeRenowned()`) — target permanent gains the **renowned** designation (CR 702.112b), the second half of the renown trigger the engine derives from `Keyword.RENOWN`. Stamps the sticky `RenownedComponent` marker and emits `BecameRenownedEvent` / `ClientEvent.PermanentRenowned`. Like solved it survives cleanup and lasts until the permanent leaves the battlefield, with no inverse effect; not a copiable value, so a copy of a renowned creature is not renowned, and re-renowning is a silent no-op. Read it with `Conditions.SourceIsRenowned` / `.renowned()`. Supplied by the keyword derivation rather than written on a card.
- `BecomeSolvedEffect(target = Self)` (facade `Effects.BecomeSolved()`) — target permanent gains the **solved** designation (CR 719.3b), the resolving half of a Case's "To solve" trigger. Stamps the sticky `SolvedComponent` marker and emits `CaseSolvedEvent` / `ClientEvent.CaseSolved`. Unlike saddled the marker survives cleanup — it lasts until the permanent leaves the battlefield, and there is no inverse effect. Not a copiable value, so a copy of a solved Case enters unsolved; re-solving is a silent no-op. Read it with `Conditions.SourceIsSolved` / `.solved()`. Authored through `toSolve(condition)` rather than called directly.
- `BecomePreparedEffect(target = Self)` (facade `Effects.BecomePrepared()`) — target permanent becomes prepared (Secrets of Strixhaven). The target must be a `CardLayout.PREPARE` permanent on the battlefield; becoming prepared creates a castable copy of its prepare spell in exile (shared `PreparationLogic.makePrepared`, the same path used when a `Keyword.PREPARED` creature enters prepared). A creature already prepared, not on the battlefield, or not a preparation card does nothing. This is the *only* way a card that lacks `Keyword.PREPARED` can become prepared — cards that "enter prepared" carry the keyword on a `PREPARE`-layout card instead, and the two are mutually exclusive (see `Keyword.PREPARED`). Used by Leech Collector ("Whenever you gain life for the first time each turn, this creature becomes prepared"), Joined Researchers (end-step trigger), and Emeritus of Truce (an ETB "Then if …" conditional).
- `UnprepareEffect(target = Self)` (facade `Effects.Unprepare()`) — target permanent **becomes unprepared** (Secrets of Strixhaven), the inverse of `BecomePrepared`. Strips the target's `PreparedComponent` and removes the cast-from-exile permission for its exile prepare-spell copy; the now-orphaned copy is swept by the `PhantomCardCopiesCheck` state-based action (which removes prepare-spell copies whose source is no longer prepared). No-op if the target isn't prepared. Used by Biblioplex Tomekeeper ("Target creature becomes unprepared").
- `EachPermanentBecomesCopyOfTargetEffect(target, filter, duration, excludeTarget, affected, sourceFromAnyZone, exceptions, retainActivatingAbility)` — mass copy (Mirrorform, Naga Fleshcrafter renew). Facade `Effects.EachPermanentBecomesCopyOfTarget(...)`. Copies copiable values only (Rule 707) — counters, tapped state, attached auras/equipment and non-copy modifiers stay put. `duration = Duration.Permanent` (default) bakes the copy into base state for good; `Duration.EndOfTurn` makes a temporary copy reverted by the end-of-turn cleanup; `Duration.UntilNextEndStep` reverts one step earlier — on **entry to the next end step** (`RevertCopyAtNextEndStepComponent`, processed by `CleanupPhaseManager.performNextEndStepExpiry`) — so it lines up with a paired "return it at the beginning of the next end step" delayed trigger (Niko, Light of Hope: "Shards you control become copies of it until the next end step"); `Duration.UntilYourNextTurn` reverts after the **untap step of the effect controller's next turn** (`RevertCopyAtYourNextTurnComponent`, processed by `CleanupPhaseManager.expireUntilYourNextTurnEffects` alongside every other "until your next turn" effect) — Absorbing Man, Taskmaster, Mercenary Mimic. Each temporary copy restores its pre-copy `CardComponent` from its `CopyOfComponent` snapshot. Note what the long duration buys: a copy replaces the permanent's card component wholesale, so the permanent's *own* "at the beginning of your first main phase" trigger is gone while the copy is up and back in time to fire again once it reverts. `sourceFromAnyZone = true` lets the copy **source** (`target`) live off the battlefield — its copiable characteristics are read wherever it is, e.g. a card in exile (Lazav, Familiar Stranger; Niko reads the just-exiled creature) or a creature card in a graveyard (Likeness Looter, Taskmaster). `excludeTarget = true` keeps the copy **source** out of the affected set, for "each **other** … becomes a copy of that …" wordings where the target keeps its own identity (and any counter just placed on it). `affected` (an `EffectTarget`, e.g. a second `ContextTarget` or `EffectTarget.Self`) switches to the single-permanent "target permanent A becomes a copy of target permanent B" shape (Fleeting Reflection) — only that one resolved permanent becomes a copy of `target`, and `filter`/`excludeTarget` are ignored; if `affected` resolves to nothing (optional target omitted) the effect is a no-op. **Copy exceptions** ("except …") all ride one `exceptions: CopyExceptions` — see below. `retainActivatingAbility = true` is the one rider that stays on the effect (it isn't a characteristic): it re-grants the activated ability that created the copy, durably and idempotently, so the permanent can be re-aimed after later copies. **Likeness Looter** uses `CopyExceptions(addedKeywords = {FLYING})` plus the retained ability; **Mimeoplasm, Revered One** uses `powerOverride = 0`, `toughnessOverride = 0` and the retained ability while preserving its entry counters; **Shuri, Wakandan Inventor** uses `removedSupertypes = {LEGENDARY}`.
- `CopyExceptions(nameOverride?, addedKeywords?, addedSupertypes?, removedSupertypes?, addedCardTypes?, overrideCardTypes?, addedSubtypes?, overrideSubtypes?, addedColors?, overrideColors?, powerOverride?, toughnessOverride?, noManaCost?)` — the **"except …" half of a copy effect** (CR 707.9b), one vocabulary shared across the copy paths that carry characteristic exceptions and applied in exactly one place engine-side (`CopyExceptionApplier`). Everything on it is a *copiable* value (CR 707.2), so a later copy of the copy sees it, and it lasts exactly as long as the copy does. Add/override pairs follow Magic's own templating, which the rules make load-bearing: a stated card type or subtype **replaces** by default (CR 205.1a) — `overrideCardTypes` / `overrideSubtypes` / `overrideColors` — unless the clause says "**in addition to** its other types" / "still a [type]", which retains the prior types (CR 205.1b) — `addedCardTypes` / `addedSubtypes` / `addedColors`. A printed clause is only ever one or the other; when both are set anyway the override wins on every axis alike. Supertypes only add and remove — `removedSupertypes` is the "except it isn't legendary" direction, without which a copy of a legendary permanent is binned by the legend rule (CR 704.5j), and it is applied **after** `addedSupertypes`. `powerOverride`/`toughnessOverride` create base P/T from nothing when the copied object had none (Absorbing Man copying a land is "a legendary 4/4 Human Villain creature in addition to his other types"). Carried as an `exceptions` field by **all three** effects that can express one — `EachPermanentBecomesCopyOfTargetEffect`, `CreateTokenCopyOfTargetEffect`, `CreateTokenCopyOfSourceEffect` — and built from its own riders by the `EntersAsCopy` clone path, so all four run identical type-line arithmetic and a new exception added here is immediately expressible on every one of them. The two token effects *additionally* keep their historical flat riders (`addedSupertypes`, `overridePower`, `addCardTypes`, …) because ~20 card definitions and their serialized shape depend on them; `effect.copyExceptions` is `exceptions.over(<the riders projected into a CopyExceptions>)`, so the two combine and neither drops the other (`CopyExceptions.None.over(base) == base`, so a card using only the riders is bit-for-bit unchanged). Those riders are frozen. Still outside it: the two paths whose only exception is the boolean `removeLegendary` and which therefore share no arithmetic — `CreateTokenCopyOfEquippedCreatureEffect` (Helm of the Host) and spell copies (`StormCopyEffectExecutor`, CR 707.10). **New copy exceptions belong here, not on a flat rider.**
- `BecomeCopyOfLinkedExileEffect(affected = AttachedToTriggeringPermanent)` — facade `Effects.BecomeCopyOfLinkedExile(affected)`. The `affected` permanent becomes a copy of the first creature card in the effect's **source's linked exile** (`LinkedExileComponent` — the card the source banished via `Effects.ExileUntilLeaves`), copiable values only (Rule 707.2). Baked into the affected permanent's `CardComponent` like Clone, but tagged with a `CopyWhileAttachedComponent(sourceId)`; the `AttachedCopyExpiryCheck` state-based action reverts the copy (restoring the pre-copy snapshot) the moment the source stops being attached to it — detach, re-attach elsewhere, or source leaving (CR 611.2b, one-way). No-op when the source's linked exile holds no creature card. Used by Assimilation Aegis ("for as long as this Equipment remains attached to it, that creature becomes a copy of a creature card exiled with this Equipment").
- `AnimateLandEffect(target, subtypes, keywords, duration)` — land becomes a creature.
- `MassAnimateEffect(filter, power, toughness, loseAllAbilities = true, duration = EndOfTurn)` — facade `Effects.MassAnimate(filter, power, toughness, loseAllAbilities, duration)`. One-shot: animate **every** permanent matching `filter` into a creature for `duration`, setting each one's base power and toughness to the `power`/`toughness` **`DynamicAmount`s** — resolved per affected permanent (Layer 7b `SetPowerToughnessDynamic`), so `EntityProperty(AffectedEntity, ManaValue)` gives "each equal to its own mana value" — and, when `loseAllAbilities`, stripping all of its abilities (Layer 6 `RemoveAllAbilities`); Layer 4 `AddType("CREATURE")` makes them creatures. The affected set is captured **once** at resolution against the current battlefield (CR 611.2c) and locked in for the duration. This is the fixed-set, one-shot companion to expressing the same effect *continuously* via the `GrantCardType` + `LoseAllAbilities` + `SetBasePowerToughnessDynamicStatic` group statics on a permanent (which take the same `DynamicAmount` P/T) — use the statics for the while-on-battlefield behavior and this effect for the "this effect continues until end of turn" linger when the generating permanent leaves. Used by **Titania's Song** ("Each noncreature artifact loses all abilities and becomes an artifact creature with power and toughness each equal to its mana value. If this enchantment leaves the battlefield, this effect continues until end of turn.") — a `LeavesBattlefield(SELF)` trigger replays the static set as until-EOT floating effects with `power = toughness = EntityProperty(AffectedEntity, ManaValue)`. The dynamic-P/T floating effect resolves its controller from the effect's captured controller (`ContinuousEffect.controllerId`) when the source has already left the battlefield.
- `ExploreEffect(target)` — Explore mechanic (reveal top; land → battlefield, else hand + counter).
- `ConniveEffect(subject, body, replacementsApplied = false)` — the connive keyword action (CR 701.50), wrapping the pipeline that carries it out. Built by `Patterns.Hand.connive` / `Effects.Connive(target)`; never constructed directly by a card. `body` is the ordinary draw → discard → conditional +1/+1 counter pipeline and runs unchanged — the wrapper exists to give the action a **name and a subject**, which is what `ModifyKeywordAction` needs to replace it (`EventPattern.ConnivedEvent`) and what lets `ConniveEffectExecutor` append `EmitConnivedEventEffect` as the pipeline's tail so `PermanentConnivedEvent` fires after the discard resolves (CR 701.50f). `replacementsApplied` is the CR 614.5 recursion guard, set only on the post-replacement re-issue. Mirrors `ExploreEffect`, the other replaceable/observable keyword action.
- `AttachEquipmentEffect(equip, target)` — attach an Equipment. Facade `Effects.AttachEquipment(...)`.
  `Effects.AttachTargetEquipmentToCreature(equipmentTarget, creatureTarget)` force-attaches one
  *targeted* Equipment to one *targeted* creature (both are explicit targets, not the source) — used
  by Stolen Uniform's "Attach it to the chosen creature".
- `UnattachEquipmentEffect(target = Self)` — facade `Effects.UnattachEquipment(target)`. The inverse of
  the attach effects: **unattach** an Aura/Equipment from its host *without moving zones* (CR 701.3d) —
  clears the attachment's `AttachedToComponent` and drops it from the host's attachment list, emitting
  `PermanentUnattachedEvent`. A no-op (no event) when `target` isn't currently attached to anything.
  `target` is usually `EffectTarget.TriggeringEntity` ("that Equipment" inside a delayed trigger) or
  `EffectTarget.Self`. Used by Stolen Uniform's "when you lose control of that Equipment … unattach it".
- `TapUntapEffect(target, isTap)` — tap or untap. Facade: `Effects.Tap` / `Effects.Untap`.
- `Effects.TapEachTarget()` — "tap up to N target creatures": taps every object chosen as a target.
  Composes `ForEachTargetEffect` over `Effects.Tap(ContextTarget(0))`, so the count lives only on the
  spell's `TargetCreature`/`TargetPermanent` (`count`, `unlimited`, or `dynamicMaxCount`) — never
  duplicated on the effect. For "tap X target creatures" use `dynamicMaxCount = DynamicAmount.XValue`
  on the target (Icy Blast); for a fixed cap use `count = N` (Tidal Surge, Choking Tethers, Eddymurk
  Crab). Do **not** pass a magic `count = 20` to mean "any number" — use `unlimited`/`dynamicMaxCount`.
- `Effects.UntapEachTarget()` — the untap twin of `TapEachTarget`: untaps every object chosen as a
  target ("untap each of those creatures"). Composes `ForEachTargetEffect` over
  `Effects.Untap(ContextTarget(0))`, with the count owned by the spell's target requirement.
- `UnlockDoorEffect(target = ContextTarget(0))` — facade `Effects.UnlockDoor(target)`. The resolution-time
  **"unlock a door"** instruction (CR 709.5f): gives a locked half of the targeted Room the "unlocked"
  designation. Distinct from the *unlock cost* special action (CR 709.5e, `ModifyUnlockCost` below) — the
  resolving controller pays nothing. Routes through the same shared `RoomDoorUnlocker` the special action uses,
  so it emits the identical `DoorUnlockedEvent`/`RoomFullyUnlockedEvent` and a face's "When you unlock this
  door" trigger (`Triggers.OnDoorUnlocked`, CR 709.5h) fires either way. Pair with an **up-to-one** target
  Room restricted to one with a locked door —
  `TargetObject(optional = true, filter = TargetFilter(GameObjectFilter.Any.withSubtype(Subtype.ROOM).youControl()).hasLockedDoor())`
  — so a fully-unlocked Room is never a legal target and the controller may choose no target. If the Room has
  more than one locked door (it entered without being cast, CR 709.5d), the controller is prompted for which
  door to unlock (CR 709.5f). Used by Ghostly Keybearer.
- `LockDoorEffect(target = ContextTarget(0))` — facade `Effects.LockDoor(target)`. The resolution-time
  **"lock a door"** instruction (CR 709.5g), the twin of `UnlockDoorEffect`: removes a chosen unlocked half's
  "unlocked" designation, turning that half's name/cost/text off via projection. Unlike unlocking, locking is
  **not a trigger source** (there is no "when you lock this door" ability) and can never *fully unlock* a Room —
  so it emits only a `DoorLockedEvent` (game-log/animation), never the `DoorUnlockedEvent`/`RoomFullyUnlockedEvent`
  family. (That asymmetry is why lock and unlock are two separate effects, not one `lock: Boolean` flag.) When the
  targeted Room has more than one unlocked door (a fully-unlocked Room) the controller is prompted for which door
  to lock; with one it is locked directly; with none it is a harmless no-op. Both door effects share
  `RoomDoorResolution`, which raises the door-choice `ChooseOptionDecision` only when more than one door is eligible.
- `Effects.LockOrUnlockDoor(target = ContextTarget(0))` — **"lock or unlock a door of target Room"**
  (Keys to the House). A resolution-time `ModalEffect.chooseOne(LockDoor, UnlockDoor, countsAsModalSpell = false)`
  (modeled exactly like `Effects.Endure` — no new modal machinery, not a printed modal spell): the controller
  chooses lock or unlock as it resolves, then the chosen door effect runs against the same outer `target`. Pair
  with a single **"target Room you control"** `TargetObject` (no locked/unlocked restriction — either choice can
  always do something on a Room you control).
- `PhaseOutEffect(target = Self)` — phase the target permanent out (Rule 702.26); facade `Effects.PhaseOut(target)`. While phased out it's treated as though it doesn't exist (excluded from `getBattlefield`, so from projection, triggers, combat, targeting, and SBAs) and phases back in before its controller's next untap step. Indirect phasing (attached Auras/Equipment) is handled automatically. Used as the `suffer` branch of a pay-or-phase trigger (Vaporous Djinn: "phases out unless you pay {U}{U}" = `PayOrSufferEffect(Costs.pay.Mana(...), Effects.PhaseOut())`), or as the reaction of a "becomes the target of a spell, it phases out" trigger (King of the Oathbreakers = `Triggers.BecomesTargetOfSpell(...)` + `Effects.PhaseOut(EffectTarget.TriggeringEntity)`). The matching phase-in moment is the `Triggers.PhasesIn(filter?)` trigger (see Triggers below).
- `PhaseOutUntilLeavesEffect(target, tapOnPhaseIn)` / `Effects.PhaseOutUntilLeaves(target, tapOnPhaseIn)` — phase the target out **indefinitely, linked to the effect's source** (the phasing analogue of `ExileUntilLeaves`): it skips its untap-step phase-in and stays out until the source leaves the battlefield. Pair with `Effects.PhaseInLinkedToSource()` on the source's `LeavesBattlefield` trigger, which phases everything the source phased out this way back in (tapping those flagged `tapOnPhaseIn`). The link lives on the phased-out permanent (`PhasedOutComponent.phaseInOnSourceLeaves`); indirect phasing carries Auras/Equipment out with the creature. This is Oubliette (ETB phase-out + leaves-trigger phase-in, tapped).
- `MarkExileOnDeathEffect(target)` — replace next "to graveyard" with "to exile".
- `Effects.AddCombatPhase` / `Effects.AddMainPhase` — the two **atomic extra-phase** effects
  (CR 500.8: extra phases are added after the specified phase). `AddCombatPhase` queues *one combat
  phase and nothing else* — "After this phase, there is an additional combat phase" (Aurelia, the
  Warleader / Combat Celebrant / Fear of Missing Out / Great Train Heist / Raph & Leo / Éomer);
  `AddMainPhase` queues one extra postcombat main phase (CR 505.1a: every main phase after the first
  is a postcombat main phase). **Compose them** —
  `Effects.Composite(listOf(Effects.AddCombatPhase, Effects.AddMainPhase))` — to reproduce "an
  additional combat phase followed by an additional main phase" (Aggravated Assault, All-Out
  Assault); the combat atom alone adds *no* trailing main phase. Implemented as an ordered
  `AdditionalPhasesComponent(phases: List<QueuedPhase>)` queue on the active player (each `QueuedPhase`
  is a `COMBAT` / `MAIN` kind plus, for a combat phase, an optional attacker-restriction filter),
  drained one at a time by `TurnManager.advanceStep` after the postcombat main phase and, for an
  inserted combat phase, again at its end-of-combat step (marked by `InAdditionalCombatPhaseComponent`)
  so a combat-only extra phase proceeds straight to the end step instead of granting an unwanted main
  phase. Engine simplification: all queued phases are inserted after the postcombat main phase
  regardless of when the effect resolved.
- `Effects.AddCombatPhaseRestrictedTo(attackerRestriction: GameObjectFilter)` — the same atomic extra
  combat phase, but **only creatures matching `attackerRestriction` may be declared as attackers
  during that inserted phase** (CR 508.1c; Bumi, Unleashed: "there is an additional combat phase. Only
  land creatures can attack during that combat phase" ⇒
  `AddCombatPhaseRestrictedTo(GameObjectFilter.Creature and GameObjectFilter.Land)`). The filter rides
  on the `QueuedPhase` and is copied onto `InAdditionalCombatPhaseComponent` when the phase begins, so
  it is scoped to exactly that phase (the natural combat phase and any unrestricted extra combat impose
  nothing). Enforced by `AdditionalCombatPhaseAttackerRule` in `defaultAttackRestrictionRules()`,
  matched against projected state so animated lands read as the land *creatures* they are. Reused by
  Aang, Destined Savior and Bumi, King of Three Trials' sibling. (A Kotlin property and function can't
  share a name, so the unrestricted atom stays the `Effects.AddCombatPhase` value and the restricted
  variant is this factory; both build the one `AddCombatPhaseEffect(attackerRestriction: GameObjectFilter?)`.)
- `Effects.AddAdditionalUpkeepSteps(amount)` (`amount: DynamicAmount` or `Int`) — give the
  controller `amount` additional upkeep steps after the current phase (Obeka, Splitter of Seconds:
  "you get that many additional upkeep steps after this phase"). Per CR 500.10, each added upkeep
  step creates the *beginning phase* that normally contains it, with the untap and draw steps
  skipped; per CR 500.8 the phases are inserted after the current phase, and after any additional
  combat phases added to the same point (most-recently-created phase first). "At the beginning of
  [your] upkeep" abilities trigger in each inserted step (CR 503.1a). Steps are always added to the
  controller's own turn (CR 500.10a). Implemented by accumulating an `AdditionalUpkeepStepsComponent`
  count on the active player, drained by `TurnManager.advanceStep` after the postcombat main phase
  (after the additional-combat-phase check), each remaining count redirecting into a fresh upkeep
  step in the beginning phase. Read "that many" from the triggering combat damage with
  `DynamicAmount.ContextProperty(ContextPropertyKey.TRIGGER_DAMAGE_AMOUNT)`.
- `Effects.AddAdditionalEndSteps(amount)` (`amount: DynamicAmount` or `Int`, default `1`) — insert
  `amount` additional end step(s) directly after the current end step (Y'shtola Rhul: "there is an
  additional end step after this step"). Per CR 500.9 a step is inserted directly after the
  specified step; each added step is a full end step (CR 513) where the active player gets priority
  and every "at the beginning of the end step" ability triggers again. Steps are always added to the
  controller's own turn (CR 500.10a). Implemented by accumulating an `AdditionalEndStepsComponent`
  count on the active player, drained by `TurnManager.advanceStep` when leaving the end step — each
  remaining count redirects back into a fresh end step instead of advancing to the cleanup step. A
  rider that adds an end step *and re-triggers in it* must guard against looping with
  `Conditions.IsFirstEndStepOfTurn` (see Conditions below) — Y'shtola only spawns the extra end step
  during the turn's first (natural) end step.
- `Effects.BecomeDay` / `Effects.BecomeNight` (`SetDayNightEffect(DayNight.DAY | NIGHT)`) — "it becomes
  day / night" (CR 731). Sets the game's global `GameState.dayNight` designation. This is the *spell/
  ability* writer of the designation (Into the Night: "It becomes night"), one of three writers routed
  through the single `DayNightService` — the other two are the untap-step turn-based action (CR 502.2 /
  731.2) and the daybound/nightbound designation-start SBA (CR 702.145d/g). Re-declaring the current
  designation is a no-op (emits nothing). A real flip emits a `DayNightChangedEvent` and, because a
  designation change reconciles out-of-step daybound/nightbound permanents (CR 702.145c/f), one
  `TransformedEvent` per permanent the flip cascades through `DayNightService.applyTransformCascade`.
  Read the designation back with `Conditions.IsDay` / `Conditions.IsNight`.
- `GatedEffect(gate, then, otherwise?, decisionMaker?)` — **the unified resolution frame for the
  optional / gated-effect cluster** (phase-rs Lesson 1). A `Gate` decides whether `then` runs; if it
  fails, `otherwise` runs. One executor + one continuation/resumer own the canonical unwind order, so
  targets on `then`/`otherwise` lock at trigger time (CR 603.3d) and the gate is resolved at
  resolution time (CR 608.2c) by `decisionMaker` (defaults to the controller) — the may-vs-target
  timing is correct by construction rather than re-encoded per wrapper. `decisionMaker` is honoured
  on the may-then-target trigger path too, where `TriggerProcessor` asks the question itself before
  the effect ever executes (Farrel's Mantle's "its controller may", with the Aura on an opponent's
  creature); `EffectTarget.ControllerOfTriggeringEntity` is the reference that path resolves, and
  anything it can't resolve falls back to the ability's controller. Gates:
  - `Gate.MayDecide(prompt?, hint?, dynamicHint?, sourceRequiredZone?, inlineOnTrigger?, feasibility?)`
    — pure yes/no
    ("You may [then]."). Replaces `MayEffect` (see the `MayEffect` facade below). `sourceRequiredZone`
    skips the gate silently when the source has left that zone by resolution; `inlineOnTrigger`
    renders the yes/no on the triggering permanent rather than as a modal. `dynamicHint` (a
    `DynamicHint(template, amount)`) is reminder text whose `{n}` is replaced by `amount` evaluated
    against the **resolving** context, and takes precedence over `hint` — see *Dynamic hints* below.
    `feasibility` (a
    `FeasibilityCheck`) — when set and **unmet** at resolution, the may-action is impossible, so the
    player "doesn't": the prompt is skipped and `otherwise` runs directly. Lets "you may sacrifice an
    artifact. If you don't, …" apply its else automatically when the controller has no artifact (the
    no-target analogue of a targeted "may" with no legal targets falling to its else branch).
    The executor skips the prompt on its own in one more case, no authoring needed: a `then` that is
    a `Gate.DoAction` scored by `SuccessCriterion.CollectionNonEmpty(name, min)` whose collection is
    filled by a `SelectFromCollectionEffect(ChooseExactly)` over a `GatherCardsEffect` pool holding
    fewer than `min` cards. No answer could clear that bar, so the option isn't a legal choice
    (CR 608.2d) — **Emeritus of Ideation** with seven cards in the graveyard isn't asked whether to
    exile eight, and the seven aren't spent for nothing. (CR 609.3's do-as-much-as-possible governs
    *mandatory* instructions; it doesn't turn an unavailable option into a partial payment.)
  - `Gate.MayPay(cost)` — "You may [cost]. If you do, [then]." `cost` is a cost **effect**
    (`PayManaCostEffect`, `PayDynamicManaCostEffect`, `PayLifeEffect`, `SacrificeEffect`, or a
    `CompositeEffect` of them). An unaffordable cost (fixed mana, dynamic mana, and life are
    recognized — the dynamic-mana amount is checked against its own `payer`; other shapes are
    assumed payable) skips the prompt straight to `otherwise`. On "yes", the cost is paid then `then`
    runs (`stopOnError`: an unpayable cost aborts the payoff). For a recognized mana cost the "yes"
    button is labeled with the concrete amount — a dynamic cost shows its computed total ("Pay {8}"),
    not the formula. When the cost is a
    `PayDynamicManaCostEffect` with a non-default `payer` (e.g. the "each player's upkeep, that player
    may pay …" shape — Magnetic Mountain), set `decisionMaker` to that same player so the one who is
    charged is the one prompted; affordability is already gauged against the `payer` regardless.
  - `Gate.WhenCondition(condition)` — **not a decision, a state test.** Succeeds iff `condition`
    holds at resolution; no prompt, no pause — `then`/`otherwise` run synchronously in the executor.
    The condition evaluates through the shared `ConditionEvaluationContext` (identical at resolution
    and projection). Replaces `ConditionalEffect` (see "Sequencing & conditional" below).
  - `Gate.DoAction(action, successCriterion?)` — **not a decision, an *action-outcome* test.**
    `action` is performed (it may itself pause for sub-decisions); once it has fully drained,
    `successCriterion` scores it against a pre-action snapshot to decide whether it "happened" —
    success → `then`, failure → `otherwise`. This is "[action]. If you do, [then]" (a declined or
    no-op action runs `otherwise`, not `then`), distinct from `MayDecide` (gates on the yes/no) and
    `MayPay` (gates on paying a cost). `successCriterion` defaults to `SuccessCriterion.Auto`, which
    infers success from the action's terminal zone-move (a pipeline `MoveCollection` to a zone, or a
    single `MoveToZone` of the source itself) growing its destination zone. **Auto is only legal on
    shapes it can infer** (`SuccessCriterion.Auto.canInfer`): card-load validation (`CardValidator`,
    enforced corpus-wide by `SuccessCriterionValidationTest`) rejects an Auto criterion on any other
    action — non-zone-move actions (deal damage, gain life, …) must state `SuccessCriterion.Always`,
    `CollectionNonEmpty(name, min)`, or `DamageDealt(recipient)` explicitly instead of silently
    inheriting a fail-open "it happened". Auto's zone-grew probe is also **wrong for zone-move
    actions that succeed vacuously**: "discard your hand" discards zero cards from an empty hand yet
    still counts as performed (Narset, Jeskai Waymaster ruling 2025-04-04 — "You may choose to
    discard your hand even if your hand contains zero cards"), so gate it with
    `SuccessCriterion.Always`, never Auto (Vaultguard Trooper, Narset, Sauron the Dark Lord). `SuccessCriterion.DamageDealt(recipient = Controller|Any)`
    gates on whether the action **actually dealt damage** from the effect's source (a positive-amount
    `DamageDealtEvent` sourced from it) — damage that was fully prevented or replaced (a Circle of
    Protection, a prevention shield, redirection) emits no such event and counts as "didn't happen".
    `DamageRecipient.Controller` (the default) requires the damage to have reached *you*; `.Any`
    accepts any recipient. Mishra's War Machine: "deals 3 damage to you unless you discard a card. If
    it deals damage to you this way, tap it" → `IfYouDoEffect(PayOrSuffer(discard, DealDamage(3,
    Controller, source=Self)), Tap(Self), successCriterion = DamageDealt(Controller))`.
    `SuccessCriterion.ControlChanged` gates on whether the action **actually changed control** of a
    permanent (a `ControlChangedEvent` whose old and new controllers differ) — a control change that
    never happened (the targeted permanent left the battlefield / is no longer controlled by the donor
    at resolution) emits no such event and counts as "didn't happen". Stiltzkin, Moogle Merchant:
    "{2}, {T}: Target opponent gains control of another target permanent you control. If they do, you
    draw a card" → `IfYouDoEffect(GiveControlToTargetPlayer(permanent, opponent), DrawCards(1),
    successCriterion = ControlChanged)`.
    `SuccessCriterion.CountersRemoved` gates on whether the action **actually took a counter off**
    something (a positive-amount `CountersRemovedEvent`) — a removal against a permanent carrying no
    such counter, or one that has already left the battlefield, emits none and counts as "didn't
    happen" (`RemoveCountersExecutor` reports the amount genuinely removed, clamped to what was
    there). Use for the "Remove a [kind] counter from [permanent]. If you do, …" shape, where
    `Auto` can't infer (no zone move) and `Always` would wrongly fire on an empty permanent.
    Fishing Pole: "Whenever equipped creature becomes untapped, remove a bait counter from this
    Equipment. If you do, create a 1/1 blue Fish creature token" → `IfYouDoEffect(
    RemoveCounters(Counters.BAIT, 1, Self), CreateToken(...), successCriterion = CountersRemoved)`.
    `SuccessCriterion.PermanentsSacrificed` gates on whether the action **actually sacrificed
    something** (a `PermanentsSacrificedEvent` with a non-empty permanent list). A sacrifice *is* a
    zone move, but which permanent — and so whose graveyard — isn't known until the chooser decides at
    resolution, so `Auto` can't infer it, and `Always` would wrongly fire the payoff for a player who
    controls nothing matching the filter. Use for the "Sacrifice a [permanent]. If you do, …" shape.
    **Garruk, the Veil-Cursed**: "−1: Sacrifice a creature. If you do, search your library for a
    creature card, reveal it, put it into your hand, then shuffle" → `IfYouDo(Sacrifice(Creature,
    count = 1, target = PlayerRef(Player.You)), Patterns.Library.searchLibrary(Creature, HAND,
    reveal = true), successCriterion = PermanentsSacrificed)` — the ability doesn't target, and an
    empty board means no sacrifice and no search.
    `SuccessCriterion.TurnedFaceUp` gates on whether the action **actually turned a permanent face
    up** (a `TurnFaceUpEvent`). Turning face up is not a zone move, so `Auto` can't infer it, and
    `Always` would report success for the two cases the rules call failures: a manifested or cloaked
    permanent represented by an instant or sorcery card is revealed and left face down (CR 701.40g /
    701.58g), and an already-face-up permanent has nothing to turn. Use for the "Turn this face up.
    **If you can't**, …" shape, where the fallback rides `ifYouDont` and the primary instruction stays
    the gated action rather than being re-encoded as a condition that would have to re-derive the
    engine's own turn-up legality. **Etrata, Deadly Fugitive** grants face-down creatures
    "{2}{U}{B}: Turn this creature face up. If you can't, exile it, then you may cast the exiled card
    without paying its mana cost" → `IfYouDoEffect(TurnFaceUpEffect(Self), Effects.Composite(emptyList()),
    ifYouDont = <exile + may cast>, successCriterion = TurnedFaceUp)` — the empty `ifYouDo` is the
    house spelling for a gate that only has a failure branch (Kellan, the Kid uses the same shape).
    `CollectionNonEmpty` gates on the action's actual pipeline collection
    (`storedCollections[name].size >= min` after the action runs) — the collections propagate onto
    the gate frame via `exposeCollectionsToNextFrame`, in both the synchronous and the
    paused/continuation-drain paths. The executor pre-pushes a `GatedActionContinuation` so a paused
    action auto-resumes and evaluates after its own continuations drain. Replaces `IfYouDoEffect`
    (see "Sequencing & conditional" below).
  - `Gate.MayPayX` — **not a yes/no, a number chooser.** "You may pay {X}. If you do, [then]." The
    decision-maker is prompted for a number 0..(most generic mana they can produce); paying X > 0
    succeeds → `then` runs with the chosen X bound into the context (read via `DynamicAmount.XValue`),
    X = 0 declines → `otherwise`. An unaffordable gate (no mana) is skipped silently. A parameterless
    `data object` (the {X} cost is implicit). The executor builds a `ChooseNumberDecision` and reuses
    the existing `MayPayXContinuation`/`resumeMayPayX` to auto-tap and bind X. Replaces
    `MayPayXForEffect` (see "Optional & gated" below). For the *repeated fixed* cost — "pay {1} up
    to three times", where the count rather than the size is the variable — use
    `Effects.PayRepeatedly` (see "Mana" above) as the gate's cost or as a reflexive trigger's action.
  - `Gate.OnceEachTurn(abilityId, spend = true)` — **not a decision, a per-turn action budget.** The
    lowered form of `TriggeredAbility.effectOncePerTurn` ("Do this only once each turn", CR 603.2h).
    Succeeds iff the source permanent's controller hasn't yet taken this ability's action this turn;
    with `spend = true` succeeding also marks it taken — check and spend are one atomic step in the
    executor, so two instances resolving back to back can never both pass. `spend = false` is the
    read-only half, used by the lowering to place a check *outside* an optional ability's consent
    gate so an already-used instance resolves silently. The budget lives on the source as a
    `TriggeredAbilityEffectAppliedThisTurnComponent` keyed by ability id and is cleared in cleanup.
    **Cards never author this gate directly** — set `effectOncePerTurn = true` on the triggered
    ability (see §8, and note that it is *not* the `oncePerTurn` trigger cap).
  - The multi-player APNAP `AnyPlayerMayPayEffect` stays a **standalone effect**, not a gate — a
    single `decisionMaker` can't express its turn-order loop (see below).

  **Pipeline storage crosses the gate.** Whatever the taken branch stores — collections, numbers,
  chosen values — is handed to the frame beneath through `exposeCollectionsToNextFrame`, on both the
  synchronous path and the paused/continuation-drain path. That is what lets a gate sit *inside* a
  composite whose later siblings read what the branch produced: "you may discard your hand. Draw X
  cards, where X is the number of cards discarded this way" (**Balin, Loremaster**) gates only the
  discard, and the sibling `DrawCards(VariableReference("discardedHand_count"))` sees the collection
  the `then` branch gathered. Scoping the gate to just the optional clause — rather than wrapping the
  whole rider — is also what makes a decline read X = 0 (an unset `VariableReference` evaluates to 0)
  instead of skipping the mandatory half.
- `MayEffect(effect, descriptionOverride?, sourceRequiredZone?, inlineOnTrigger?, hint?, dynamicHint?, decisionMaker?, otherwise?, feasibility?)`
  — "You may [effect]." Facade preserved for existing cards; it now **lowers to
  `GatedEffect(Gate.MayDecide(...), then = effect, otherwise = otherwise, decisionMaker = decisionMaker)`**
  (compiled form is `Gated`, no distinct `May` type or executor). The may-vs-target trigger reorder —
  for a "may" ability that *also* targets, the yes/no is asked *before* target selection (Invigorating
  Boon) — recognizes the lowered shape via the `Effect.asMayDecide()` matcher (a bare `Gate.MayDecide`
  with no `otherwise`).
  - **The prompt is the ability's authored `description` when it has one.** A generated effect
    description is assembled bottom-up from the effect tree, so a composed effect reads as its own
    plumbing rather than as the card — Safe Haven's `optional = true` upkeep trigger asked "You may
    sacrifice this creature. If you do, look at cards exiled by this permanent. Put those cards onto
    the battlefield" instead of its printed text. Lowering `optional = true` therefore passes the
    `triggeredAbility { }` block's `description` into the gate as `MayEffect(descriptionOverride =
    …)`, which is what both prompt sites render — `GatedEffectExecutor` when the trigger resolves,
    and `TriggerProcessor` for a "may" that is asked *before* target selection. **Write the
    `description` out on any optional trigger whose effect is a composition** — it is player-facing
    text, not just catalog documentation. A trigger with no `description` still falls back to the
    generated "You may …".
  - **Dynamic hints — `dynamicHint = DynamicHint(template, amount)`.** A printed "you may … *that
    much* damage / *that many* cards" renders the same sentence on every instance. When one event
    puts **several instances of the same ability on the stack at once**, the prompts become
    indistinguishable and the player is choosing blind — which silently costs the card its whole
    decision. `DynamicHint` fills `{n}` in `template` from `amount`, evaluated against the resolving
    context, so each prompt names its own number; the oracle text in the prompt itself is untouched
    and the hint renders as a line beneath it. Reach for it whenever a "may" is worth answering
    differently depending on a number the printed text calls "that much".

    ```kotlin
    MayEffect(
        Effects.DealDamage(DynamicAmount.ContextProperty(ContextPropertyKey.TRIGGER_DAMAGE_AMOUNT), victim),
        dynamicHint = DynamicHint(
            "This trigger would deal {n} damage.",
            DynamicAmount.ContextProperty(ContextPropertyKey.TRIGGER_DAMAGE_AMOUNT),
        ),
    )
    ```

    **The Sensational She-Hulk** (the back face of Jennifer Walters) is the motivating case: a
    multi-block puts one mirror trigger on the stack per damaged creature, and "decline down to the
    biggest number" is only a real line of play if the three prompts can be told apart. Pairs
    naturally with `TriggeredAbility.effectOncePerTurn`, which is what makes declining free.
  - **Per-object prompts name their subject automatically.** The sibling problem to dynamic hints:
    a gate nested inside a `ForEachInGroup` / `ForEachInCollection` body raises one prompt per
    entity, and the sentences are identical. `GatedEffectExecutor` stamps every gate prompt's
    `DecisionContext.subjectEntityId` from the loop's `pipeline.iterationTarget` — the same binding
    `EffectTarget.Self` reads inside the body — so the client renders that permanent beside the
    source card and rings it on the battlefield in `--color-decision-subject`. Nothing to declare on
    the card: **Killing Wave** ("for each creature, its controller sacrifices it unless they pay X
    life") gets it from the shape alone. Only the entity id crosses the wire, so the client's
    already-masked card map keeps a face-down subject face-down.
  - **`decisionMaker` routes the yes/no to a non-controller** — pass any player `EffectTarget`
    (`EffectTarget.TargetController` for "that creature's controller may …", or a bound target such
    as `target("target opponent", Targets.Opponent)` for "**target opponent may …**"). Only the
    yes/no prompt is delegated; the `then`/`otherwise` effects still resolve from the controller's
    perspective unless they themselves target a specific player.
  - **`feasibility` suppresses an unanswerable prompt** — a `FeasibilityCheck`
    (`ControlsPermanentMatching(filter, count?)` / `HasCardsInZone(zone, filter?, count?)`, both scoped to
    the decision-maker) evaluated at resolution. Unmet ⇒ the yes/no is skipped and `otherwise` runs
    directly, the no-target analogue of a targeted "may" with no legal targets. Reach for it on
    **recurring** triggers whose action needs a resource the player may not have — Provisions Merchant
    ("whenever this creature attacks, you may sacrifice a Food") would otherwise ask every combat. Only
    for preconditions the engine can decide; never to pre-empt a genuine choice.
  - **`otherwise` is the "if that player doesn't" branch** — runs iff the chooser declines. Combine
    with a delegated `decisionMaker` for "target opponent may [then]; if that player doesn't,
    [otherwise]" (Palantír of Orthanc: "target opponent may have you draw a card; if that player
    doesn't, you mill X cards and that player loses life equal to their total mana value").
- `OptionalCostEffect(cost, ifPaid, ifNotPaid?)` — "You may [cost]. If you do, [ifPaid]." Facade
  preserved for existing cards; it now **lowers to `GatedEffect` with a `Gate.MayPay`** gate (compiled
  form is `Gated`, not a distinct `OptionalCost` type).
- `MayPayManaEffect(cost, effect)` — "You may pay [cost]. If you do, [effect]." Facade preserved for
  existing cards; it now **lowers to `GatedEffect(Gate.MayPay(PayManaCostEffect(cost)), then = effect)`**
  (compiled form is `Gated`, no distinct `MayPayMana` type or executor). The engine recognizes this
  exact shape — a flat mana `Gate.MayPay` with no `otherwise` and the default decision-maker, whether
  authored via `MayPayManaEffect` or `OptionalCostEffect(PayManaCostEffect(...), …)` — and gives it the
  bespoke optional-mana-payment UX rather than the generic gated yes/no: **manual mana-source
  selection** at resolution (a `SelectManaSourcesDecision`, so sources that sacrifice or carry a tap
  sub-cost aren't auto-tapped), and, for a **triggered ability that also requires a target** (the
  Onslaught "Words of …" cycle, Lightning Rift), the deliberate **pay → select-mana → choose-target**
  order so the player isn't asked to pick a target before deciding to pay. Composite-cost, life-gated,
  or `otherwise`-bearing MayPay gates keep the generic auto-tapping path.
- `Effects.UnlessYouWaterbend(amount, otherwise)` — "[otherwise] unless you waterbend {amount}." An
  **in-resolution waterbend payment gate** (Avatar: The Last Airbender). Lowers to
  `GatedEffect(Gate.MayPay(PayManaCostEffect("{amount}", waterbend = true)), then = Composite(), otherwise = otherwise)`.
  The `PayManaCostEffect.waterbend` flag makes the gated executor recognize a waterbend MayPay and,
  instead of the plain "pay?" yes/no + auto-tap, surface a `SelectManaSourcesDecision` that **also lists
  the untapped artifacts/creatures the player may tap to help** (each paying {1}), reusing the shared
  waterbend machinery (`CostEnumerationUtils.findWaterbendPermanents`/`canAffordWithWaterbend`,
  `AlternativePaymentHandler.applyWaterbendForAbility`, `SelectManaSourcesDecision.waterbendPermanents`
  — the same plumbing as Ward—Waterbend). Paying (mana and/or taps) runs `then` (empty — paying is its
  own reward); declining, or being unable to pay, runs `otherwise`. The payment resumes through the
  shared `MayPayManaSelectionContinuation` (now carrying `waterbend`/`otherwise`). Waterbend is
  generic-only, so `amount` carries no colored pips. **Waterbending Lesson**: `Composite(DrawCards(3),
  UnlessYouWaterbend(2, Discard(1)))` — "Draw three cards. Then discard a card unless you waterbend {2}."
- `MayPayXForEffect(effect)` — "You may pay {X}. If you do, [effect]." Facade preserved for existing
  cards; it now **lowers to `GatedEffect(Gate.MayPayX, then = effect)`** (compiled form is `Gated`, no
  distinct `MayPayX` type or executor). Prompts a 0..max-affordable number chooser; paying X auto-taps
  X generic mana and binds the chosen X into `effect`'s context (read via `DynamicAmount.XValue`).
  Decree of Justice's cycling trigger, Hollow Specter's combat-damage trigger.
- `Effects.AnyPlayerMayPay(cost, consequence)` / `Effects.UnlessAnyPlayerPays(cost, effect)` —
  back the single `AnyPlayerMayPayEffect(cost, consequence?, consequenceIfNonePaid?)`, which asks
  each player in APNAP order whether to pay `cost`. The first to pay runs `consequence` and stops
  the loop; if no one pays, `consequenceIfNonePaid` runs. `AnyPlayerMayPay` reads the
  "if a player does, X" direction (Prowling Pangolin); `UnlessAnyPlayerPays` reads the inverse
  "X unless any player pays" direction (Aether Rift: "return it… unless any player pays 5 life").
  Supported costs: `Costs.pay.Sacrifice` (card selection) and `Costs.pay.PayLife` (yes/no). The
  surrounding pipeline's stored collections are carried into whichever consequence fires, so the
  consequence can reference cards gathered earlier in the same resolution (e.g. the discarded card,
  via `MoveCollection(from = "discarded", …)`). The optional `eligiblePlayers: Player` field scopes
  *which* players are offered the choice, relative to the source's controller: `Player.Each`
  (default) asks everyone; `Player.EachOpponent` asks only the controller's opponents — "any
  opponent may sacrifice a creature… if a player does, tap this and put a +1/+1 counter on it"
  (Desecration Demon). APNAP order is preserved within the scoped subset.
- `RepeatWhileEffect(body, repeatCondition)` (facade `Effects.RepeatWhile(body, repeatCondition)`) — do-while loop: run `body` once, then repeat while `repeatCondition` holds. `repeatCondition` is `RepeatCondition.PlayerChooses(decider, prompt)` (a yes/no each iteration) or `RepeatCondition.WhileCondition(condition)` (a game-state `Condition`). A `WhileCondition` is evaluated against the **body's own pipeline outputs from that iteration** (the collections/values it just stored), then the next iteration's body runs from the pristine pre-loop context — so a loop can branch on what it just produced without stale state leaking forward. This holds **whether the body resolves synchronously or pauses for a decision mid-loop**: a body that stops for a player choice (e.g. a `SelectFromCollection` "you may" prompt) has its outputs captured when it resumes and still fed to the condition. Models "do X. Repeat this process [if …]" — e.g. **The Tale of Tamiyo** I–III (synchronous body): `RepeatWhile(body = Composite(Patterns.Library.mill(2), ConditionalEffect(CollectionSharesCardType("milled"), DrawCards(1))), repeatCondition = WhileCondition(CollectionSharesCardType("milled")))` mills two, and while the two milled cards share a card type both draws and repeats. **Cultivator Colossus** (pausing body): `RepeatWhile(body = Composite(Patterns.Hand.putFromHand(Land, count = 1, entersTapped = true), ConditionalEffect(CollectionContainsMatch("putting", Land), DrawCards(1))), repeatCondition = WhileCondition(CollectionContainsMatch("putting", Land)))` — each pass prompts to put up to one land (choosing zero declines), draws only if one was put, and repeats while a land was put this pass.

### Sequencing & conditional

- `CompositeEffect(effects)` — run effects in order. Card definitions use the facade
  `Effects.Composite(e1, e2, ...)` (vararg) or `Effects.Composite(effects, stopOnError?,
  descriptionOverride?, descriptionAmounts?)` (list + render options).
- `ForEachEffect(space, body)` — the **single compiled iteration effect**: run `body` once per item
  of a sealed `IterationSpace`. Five lowering facades keep the pre-unification authoring names
  (same precedent as `IfYouDoEffect` → `GatedEffect`); use the one matching the iteration source:
  - `ForEachTargetEffect(effects)` → `IterationSpace.Targets` — per chosen target; the body sees
    only the current legal target as `ContextTarget(0)`, fresh `storedCollections` (Kaboom!). If one
    target becomes illegal before resolution while another remains legal, the loop still runs for
    each surviving target and rebinds it to slot 0; legality gaps from the outer spell do not leak
    into the iteration body.
  - `ForEachPlayerEffect(players, effects)` → `IterationSpace.Players(players)` — per matching
    player; `controllerId` rebound so `Player.You` is the current player, `opponentId` recomputed,
    fresh `storedCollections` (Winds of Change, Bend or Break, One Ring to Rule Them All).
    Relational single-player references such as `Player.ControllerOf("target creature")` iterate
    exactly that player, using last-known controller information if the permanent has left. If the
    reference is undefined (for example, an optional target was not chosen), the loop is a no-op;
    it never widens to every active player.
  - `ForEachInCollectionEffect(collection, effect)` → `IterationSpace.Collection(name)` — per
    entity of a named pipeline collection; `pipeline.iterationTarget` bound so `EffectTarget.Self`
    is the current entity; outer collections preserved (Fight or Flight).
  - `ForEachInGroupEffect(filter, effect, noRegenerate?)` / facade
    `Effects.ForEachInGroup(...)` → `IterationSpace.Group(filter, noRegenerate)` — per battlefield
    permanent matching a group filter; same `iterationTarget` binding as Collection.
  - `ForEachColorOfEffect(source, effect)` / facade `Effects.ForEachColorOf(...)` →
    `IterationSpace.ColorsOf(source)` — per color of an entity, WUBRG order, bound via the
    chosen-color channel (see the choice section).

  Every space snapshots its items before the first iteration (entities destroyed mid-loop stay in
  the list) and every space is **pause-safe**: a body that pauses for a decision resumes the
  remaining iterations via the shared `ForEachContinuation`. Multi-effect bodies lower to a
  `CompositeEffect`. `ForEachPlayerCollectingEffect(players, effects, collectCollections)` is the
  player-loop facade for a following effect that needs outputs from every fresh per-player
  sub-pipeline. Its map is `body-local collection → aggregate collection`; each iteration's output
  is appended in player order and published only outside the loop. Accumulation is pause-safe and
  does not leak one player's working collections into the next player's body. Syphon Mind maps each
  opponent's `discarded_this_opponent` output into `discarded_by_opponents`, then draws from that
  aggregate.
- `ConditionalEffect(condition, ifTrue, ifFalse?)` / `Branch(...)` — conditional branch. Facade
  preserved for existing cards; it now **lowers to `GatedEffect(Gate.WhenCondition(condition), then =
  ifTrue, otherwise = ifFalse)`** (compiled form is `Gated`, not a distinct `Conditional` type). It is
  a synchronous state test — no decision, no pause. Engine paths that recognize a conditional branch
  (stack-time branch resolution for opponent views, repeat-activation analysis, limited rating) key
  off the lowered `Gate.WhenCondition` shape via the `Effect.asConditional()` matcher.
- `IfYouDoEffect(action, ifYouDo, ifYouDont?, successCriterion?)` — "[action]. If you do, [ifYouDo].
  Otherwise, [ifYouDont]." Gates the payoff on whether `action` actually accomplished its work (a
  declined or no-op action runs `ifYouDont`, not `ifYouDo`) — not on a yes/no decision. Facade
  preserved for existing cards; it now **lowers to `GatedEffect(Gate.DoAction(action,
  successCriterion), then = ifYouDo, otherwise = ifYouDont)`** (compiled form is `Gated`, not a
  distinct `IfYouDo` type or executor). `successCriterion` defaults to `SuccessCriterion.Auto` (infer
  from the action's terminal zone-move); an action shape Auto *can't* infer from is a card-load
  validation error — state `SuccessCriterion.Always` / `CollectionNonEmpty(name, min)` explicitly
  there (and use them whenever the inference is wrong). Wrap with `MayEffect` for the optional
  "You may [action]. If you do, [effect]" shape.
- `ReflexiveTriggerEffect(action, reflexive, optional)` — "You may/do [action]. **When you do**,
  [reflexiveEffect]." **The reflexive half is a genuinely separate CR 603.12 triggered ability — a
  real second stack object**, not something resolved inline as part of the original ability. Once
  `action` completes, `ReflexiveTriggerEffectExecutor` emits a `ReflexiveAbilityTriggeredEvent`;
  `TriggerDetector.detectReflexiveTriggers` turns that into a real `PendingTrigger` and hands it to
  the ordinary `TriggerProcessor` pipeline — the same target-selection/stack-placement code path any
  other triggered ability uses. Concretely this means: the reflexive ability's target(s) are chosen
  as it's placed on the stack (not while the action resolves), players get a genuine priority window
  before it resolves (so an opponent can respond and make its target illegal), and CR 608.2b
  illegal-target fizzle applies automatically — none of which an inline resolution could offer.
  `reflexiveTargetRequirements` targets are found against the resolving ability's live pipeline, so a
  `TargetObject.dynamicMaxCount` on a reflexive requirement can read a count the *action* stored, e.g.
  `dynamicMaxCount = DynamicAmount.VariableReference("discarded_count")` paired with
  `Patterns.Hand.discardAnyNumber()` (Miasma Demon: "you may discard any number of cards. When you do, up
  to that many target creatures each get -2/-2") — this pipeline state is carried across the stack
  round-trip via `PendingTrigger.carriedPipeline`/`TriggeredAbilityOnStackComponent.carriedPipeline`.
  This differs from the cast-time path (`TargetValidator.effectiveMaxCount`), where the pipeline isn't
  live yet — pipeline-linked caps only work on reflexive/resolution-time targets.
  `ReflexiveTriggerEffectExecutor.isActionFeasible` decides up front whether the action can happen at
  all; when it can't, the whole effect is skipped silently. An impossible action never happens, so
  CR 603.12's "when you do" never triggers — for `optional = true` that means the "may [action]?"
  question isn't worth asking (answering yes would no-op the action while still firing the payoff),
  and for `optional = false` it means a vacuous action must not pay out either. Alongside
  `SelectTargetEffect` / `SacrificeEffect` / `ChooseActionEffect` / `PayFixedCountersEffect`, it scores
  the **Gather → Select → Move pipeline** that `Effects.Discard` and the counted `Patterns.Hand`
  discards compile to — a `SelectFromCollection` whose `SelectionMode` carries a minimum
  (`ChooseExactly`, `Random`) drawing from a collection the preceding `GatherCards` will leave empty.
  So `ReflexiveTriggerEffect(action = Effects.Discard(1), …)` on an empty hand never prompts and never
  pays out (Inti, Seneschal of the Sun). Two deliberate gaps, both fail-open: minimum-less modes
  (`ChooseUpTo`, `All`, `ChooseAnyNumber`) stay feasible, because discarding zero cards genuinely
  performs "discard any number of cards" (Miasma Demon); and a non-empty collection smaller than the
  requested count stays feasible, because `SelectFromCollectionExecutor` clamps the count to the
  collection size (with one card in hand, "discard two cards" discards one and succeeds). Gather sizes
  are read off the pre-action state, so the bookkeeping stops at the first composite step that is
  neither a gather nor a select — including inside nested composites, so `Effects.DrawCards(1).then(
  Effects.Discard(1))` is still offered on an empty hand rather than being judged against the pre-draw
  count. Note `Patterns.Hand.discardHand` is a bare Gather → Move with no selection step, so it is
  never gated by this at all.
- **Branching on gathered properties** — "reveal/look, if it's a [type] do X, otherwise Y" needs no
  bespoke effect type; it is the partition + collection-gate composition:
  1. **Partition:** `FilterCollection(from, CollectionFilter.MatchesFilter(filter), storeMatching,
     storeNonMatching)` splits a gathered collection by any `GameObjectFilter` — deterministic, no
     player decision, no continuation.
  2. **Branch:** gate follow-up effects on the partition with
     `GatedEffect(Gate.WhenCondition(CollectionContainsMatch(name, filter?)))` (or `Not(...)` /
     `ConditionalOnCollectionEffect(name, ifNotEmpty, ifEmpty?, minSize?)`). Gate conditions are
     evaluated against the live `EffectContext`, so they see pipeline collections — **including
     collections written before an earlier pause** (a `SelectFromCollection` decision); the resumed
     pipeline context carries them.
  3. Effects that consume an empty collection (`MoveCollection`, `SelectFromCollection`,
     `AddCountersToCollection`) are silent no-ops, so the "nothing matched" leg often needs no gate
     at all — just move the (possibly empty) partition.
  Worked examples: `Patterns.Mechanic.explore()` (CR 701.44), Sindbad ("draw and reveal; if it isn't
  a land, discard it"), Cache Grab (Food gated on `CollectionContainsMatch("selected", Squirrel)`
  after a selection pause).
  - **Collection conditions** (read a pipeline collection from the live `EffectContext`):
    `Conditions.CollectionContainsMatch(name, filter?)` — true if any card in `name` matches `filter`;
    `Conditions.CollectionSharesCardType(name)` — true if two cards in `name` share a card type (CR
    205.2a; false for fewer than two cards). The latter models "if two cards that share a card type
    were milled this way" (The Tale of Tamiyo I–III), typically inside both a `ConditionalEffect` and
    a `RepeatCondition.WhileCondition` over the mill's `"milled"` collection.

### Modal & choice

- `ModalEffect.chooseOne { mode(...) }` / `ModalEffect.chooseN(n) { ... }` — modal effect block.
- `ModalEffect.chooseOneNotYetChosen(*modes)` — "choose one that hasn't been chosen"; source remembers used modes across the game (Gandalf the Grey). Flag: `excludePreviouslyChosenModes` (per-source `ChosenModesEverComponent`, never cleared).
- `ModalEffect.chooseOneNotYetChosenThisTurn(*modes)` — "choose one that hasn't been chosen **this turn**"; the turn-scoped sibling. The source remembers modes chosen during the current turn (per-source `ChosenModesThisTurnComponent`, cleared each cleanup step) and excludes them from later triggers *this turn*, so across all of a turn's triggers each mode is chosen at most once; the memory resets next turn. Once every mode is chosen this turn the ability has no legal mode and resolves as a no-op. Keyed to the source object, so two copies track modes independently. Flag: `excludeModesChosenThisTurn` (mutually exclusive with `excludePreviouslyChosenModes`). Repeatable modal triggered/activated abilities only — not modal spells (Breeches, Eager Pillager).
- `ChooseActionEffect(choices, player = Controller)` — `player` picks from a list of labeled effects; infeasible options (per each `EffectChoice.feasibilityCheck`) are filtered out, and if one remains it auto-runs. `player` may be any `EffectTarget`, including the state-relational `EffectTarget.TargetController` — routing the choice to the controller of the ability's chosen permanent (a "[do X to target permanent] unless its controller [accepts an avoidance]" choice). Combustion Man: "destroy target permanent unless its controller has Combustion Man deal damage to them equal to his power" — `player = TargetController`, with choices `DealDamage(sourcePower(), target = TargetController, damageSource = Self)` and `Destroy(<the permanent>)`.
- `GrantProtectionFromColor(color, target, duration)` — grant protection from a **fixed** color to a target (no player choice); a thin recipe over `GrantKeyword("PROTECTION_FROM_<COLOR>")`. "{W}: Target creature gains protection from red until end of turn." (Crimson Acolyte).
- `GrantProtectionFromCardType(cardType, target, duration)` — the card-type sibling: grant protection from a **fixed** `CardType` (no player choice), a thin recipe over `GrantKeyword("PROTECTION_FROM_CARDTYPE_<TYPE>")` — the same projected keyword the printed `Protection(ProtectionScope.CardType(...))` static and the player-chosen `GrantProtectionFromChosenCardType` produce, so targeting, blocking, and combat damage all read one keyword. Reach for it when the card names the type outright rather than letting the player pick ("gains protection from artifacts" — Razor Barrier).
- `GrantPlayerProtection(scope = ProtectionScope.Everything, duration = Duration.UntilYourNextTurn, target = Controller)` — grant a **player** protection from a `ProtectionScope` (CR 702.16); the player-level counterpart of the creature protection statics. For a player only the **D**amage and **T**argeting parts of DEBT apply: a protected player can't be the target of, nor be dealt damage by, a source matching the scope. Adds/merges a `PlayerProtectionComponent` (multiple grants stack their scopes); the targeting validator, target enumerator, and `DamageUtils` all consult the shared `PlayerProtectionRules`. `Duration.UntilYourNextTurn` clears it after the untap step of the player's next turn. "You gain protection from everything until your next turn." (The One Ring).
- `ChooseColorThenEffect(whenChosen)` — pick a color, then run a function of that color.
- `Effects.ChooseNumberThen(then, minValue=0, maxValue=16, prompt)` — pick a number in `[minValue, maxValue]`,
  then run `then` once with the chosen number exposed via the effect context as **X**. Atomic effects and filters
  under `then` read it through `ManaValueEqualsX` (`.manaValueEqualsX()`). Compose with `CompositeEffect` for
  multi-step cards (Void: destroy all artifacts/creatures with that mana value, then a target player reveals their
  hand and discards all nonland cards with that mana value).
- `Effects.ChooseNumberForSource(minValue=0, maxValue=7, slot=ChoiceSlot.CHOSEN_NUMBER, prompt)` — pick a number in
  `[minValue, maxValue]` and **store it durably on the source permanent** under `slot` (a `ChoiceValue.NumberChoice`
  in its `CastChoicesComponent`, replacing any prior value). Unlike `ChooseNumberThen` (transient X for one inner
  effect), the number persists so a characteristic-defining ability can read it for the permanent's whole life via
  `DynamicAmount.CastChoice(slot)`. Re-callable — the **last** chosen value wins. This is the *on-resolution* form
  (run from a triggered/activated ability, e.g. an upkeep re-choice); for the *as-enters* "As ~ enters, choose a
  number" choice use the replacement `EntersWithChoice(ChoiceType.NUMBER, minValue, maxValue)` (§ replacement effects),
  which writes the same `CHOSEN_NUMBER` slot before the permanent is on the battlefield. For a "you **may** choose"
  clause mark the running triggered ability `optional = true` (declining keeps the prior value). Powers Shapeshifter
  (replacement at entry + this effect each upkeep), whose P/T is a
  `SetBasePowerToughnessDynamicStatic(power = CastChoice(CHOSEN_NUMBER), toughness = Subtract(Fixed(7),
  CastChoice(CHOSEN_NUMBER)))` CDA — power = last chosen number, toughness = 7 − it.
- `Effects.ChooseOpponent(prompt)` — the controller picks an opponent, **stored durably on the
  source entity** under `ChoiceSlot.OPPONENT` (a `ChoiceValue.EntityChoice` in its
  `CastChoicesComponent`) and read back through `Player.ChosenOpponent`. Forced (promptless) with a
  single opponent, so 2-player games see no extra decision. The source may be a spell on the stack
  (the choice lives on the spell entity for its resolution) or a permanent (recorded durably).
  `Patterns.Mechanic.giftSpell` prefixes its gift mode with this automatically — gift recipients
  address `Player.ChosenOpponent`. (Permanent gift cards don't need it: `gift(kind)` records the
  promised opponent in the same slot as part of the cast — see § 11 "Gift".) The *as-enters* analogue
  is `EntersWithChoice(ChoiceType.OPPONENT)`,
  which writes the same slot (Jihad, The Rack).
- `Effects.ChooseCardTypeForSource(allowedCardTypes=null, lookAtOpponentHand=false, slot=ChoiceSlot.CARD_TYPE, prompt)`
  — the controller picks a card type (CR 205.2a), **stored durably on the source entity** under
  `ChoiceSlot.CARD_TYPE` (a `ChoiceValue.TextChoice`) and read back at cost-calculation / projection
  time by `CardPredicate.CardTypeEqualsChosenComponent` (build the filter with
  `GameObjectFilter.…ofChosenCardTypeComponent()`). `allowedCardTypes` restricts the offered set (pass
  the non-creature list for "a card type other than creature"); `lookAtOpponentHand` reveals an
  opponent's hand to the controller first. The on-resolution, durable-slot analogue of an as-enters
  card-type choice — the same relationship `ChooseNumberForSource` has to `EntersWithChoice(NUMBER)`;
  use it in a "when ~ enters" trigger when the chosen type only feeds a continuous tax (Arachne,
  Psionic Weaver — "choose a card type … Spells of the chosen type cost {1} more").
- `GrantHexproofFromChosenColorEffect(target)` — hexproof from chosen color.
- `GrantProtectionFromChosenColorEffect(target)` — protection from chosen color. Must run inside `ChooseColorThen`; wrap in `ForEachInGroup` for the group case (Akroma's Blessing: "Creatures you control gain protection from the chosen color").
- `Effects.GrantProtectionFromChosenCardType(target, duration)` — "gains protection from the card type of your choice" (Pippin, Guard of the Citadel). The card-type analogue of `GrantProtectionFromChosenColor`, but **self-contained**: its executor owns the choice — it presents a `ChooseOptionDecision` over the fixed protectable card-type set (Artifact, Creature, Enchantment, Instant, Land, Planeswalker, Sorcery, Battle) and, on response, grants a floating `PROTECTION_FROM_CARDTYPE_<TYPE>` keyword for `duration`. The targeting validator, `StackResolver` spell-targeting, `DamageUtils`, the combat-damage pipeline/manager, and a `ProtectionFromCardTypeRule` block-evasion rule all match the protected keyword against the source's projected card types. (The "can't be enchanted/equipped by that type" clause is reminder text and unenforced at attach time, mirroring color/subtype protection.)
- `ChooseCreatureTypeEffect(...)` — pause for creature-type pick.
- `SelectTargetEffect(...)` — have a player pick from a valid set.

> **Authoring rule:** prefer composing primitives over adding parameters to an existing effect. Use `CompositeEffect`
> and the gather/select/move pipeline before writing a new executor.

---

## 5. Effect patterns (`Patterns.Library.*` / `Patterns.Hand.*` / `Patterns.Group.*` / `Patterns.Exile.*` / `Patterns.Sideboard.*` / `Patterns.CreatureType.*` / `Patterns.Mechanic.*`)

Composed pipelines (`GatherCards → SelectFromCollection → MoveCollection` shapes and similar).
Named entries here are for named MTG mechanics and shapes with a demonstrated second user — a
one-off pipeline belongs inline in the card file via `Effects.Pipeline { }` (§5.5) instead.

**Library search & reveal**

- `searchLibrary(filter, destination?, tapped?, shuffle?)` — search library, pick matching, move, shuffle.
- `searchMultipleZones(zones, filter, count?, destination?, tapped?, reveal?)` — search several zones (e.g. library and/or graveyard) in one effect; shuffles automatically if `LIBRARY` is among the zones. Pass `reveal = true` for "reveal it" tutors (Delivery Moogle).

**Sideboard / wish (`Patterns.Sideboard.*`)**

- `wish(filter = Any, count = 1, destination = HAND, revealed = true, optional = true)` — the **wish** mechanic (Burning Wish,
  Living Wish, Cunning Wish, Death Wish, Glittering Wish, Wish, …): "you may [reveal] a [type] card
  you own from outside the game and put it into your hand." A player's sideboard is modelled as the
  private per-player `Zone.SIDEBOARD` ("outside the game", CR 100.4 / 400.11a; strictly not a zone
  per CR 400.11, but a pseudo-zone lets the wish reuse the ordinary pipeline). The recipe composes
  `GatherCards(FromZone(SIDEBOARD, You, filter)) → SelectFromCollection(ChooseUpTo(count)) →
  MoveCollection(→ destination, revealed = revealed)` — **no shuffle** (the sideboard is unordered).
  `revealed` defaults on per the cycle's "reveal that card" clause (CR 701.20, Reveal); pass
  `revealed = false` for cards that merely "put a card you own from outside the game into your hand"
  with no reveal (**North Wind Avatar**: `Patterns.Sideboard.wish(GameObjectFilter.Any, revealed = false)`).
  The "may" is the `ChooseUpTo(count)`: declining or having no legal choice simply moves nothing.
  `optional = false` swaps it for `ChooseExactly(count)`, for the wishes printed as a plain
  instruction rather than a "you may" — the controller takes a card if they own one outside the
  game, and an empty or unmatched sideboard still moves nothing rather than stalling (CR 609.3).
  **Ring of Ma'rûf** is the one such card, and the only wish that fetches through a *draw
  replacement* rather than on resolution:
  `Effects.ReplaceNextDraw(Patterns.Sideboard.wish(GameObjectFilter.Any, revealed = false, optional = false))`.
  The varying axis across the cycle is `filter`
  (`Filters.Sorcery` for Burning Wish, `Filters.Instant` for Cunning Wish, creature-or-land for
  Living Wish, `Any` for Death Wish/Wish); `destination` is `HAND` for every printed wish but is
  parameterized for the rare future "from outside the game onto the battlefield"/Karn-style case.
  Pair with `spell { selfExile() }` for the cards (Burning/Cunning/Living Wish) that exile
  themselves on resolution instead of going to the graveyard (CR 608.2g). The sideboard is private
  to its owner — masked from opponents and spectators by `ClientStateTransformer` (like the library
  and hand) — and, consistent with CR 400.11c, no effect other than a wish should ever gather from
  it. Sideboards are populated at deck-build: an explicit ≤15-card list in constructed (CR 100.4a),
  `pool − maindeck` in Limited (CR 100.4b). Example — **Burning Wish**:
  `spell { selfExile(); effect = Patterns.Sideboard.wish(Filters.Sorcery) }`.

**Top-deck manipulation**

- `scry(count)` — look at top N, bottom any, rest on top. Also `Effects.Scry(count)`.
- `scry(count, target)` — **"Target player scries N"** (`Effects.Scry(count, target)`). Player-scoped
  twin of `scry(count)`: when `target` is the controller it is identical (returns the compact
  `ScryEffect` macro); otherwise it expands to a `scryPipeline` whose gather + library moves read the
  **target** player's library and whose top/bottom decision is made by that player
  (`Chooser.TargetPlayer`) — the scry analogue of `mill(count, target)`. Used by modal "• Target
  player scries N" modes (Bumi, King of Three Trials), where `target` is the chosen mode's local
  `EffectTarget.ContextTarget(0)`.
- `surveil(count)` — look at top N, any to graveyard, rest on top. Also `Effects.Surveil(count)`.
  - **Compact macro effect.** `scry`/`surveil` return a single `ScryEffect`/`SurveilEffect` *marker*
    node (`{"type":"Scry","count":N}`), not the unrolled pipeline. The engine's `ScryExecutor` /
    `SurveilExecutor` expand it to the shared `LibraryPatterns.scryPipeline(N)` /
    `surveilPipeline(N)` (Gather → Select → Move → Move → emit `ScriedEvent`/`SurveiledEvent`) at
    resolution and delegate to `CompositeEffectExecutor` — so the SelectCardsDecision pause and the
    "Whenever you scry/surveil" triggers behave exactly as the expanded pipeline. Collapsing to one
    node keeps the per-card snapshot goldens one line and stops them churning when the shared
    pipeline internals change. Any effect-tree walker that needs the inner nodes expands through the
    single `LibraryPatterns.expandMacro(effect)` helper.
  - **Dynamic count.** `surveil(count: DynamicAmount)` / `Effects.Surveil(amount: DynamicAmount)` —
    "surveil X" where X is only known at resolution (e.g. Spider-Man Noir: "surveil X, where X is the
    number of counters on it", `DynamicAmounts.countersOnTriggering()`). There is no compact macro for
    a dynamic surveil (the marker only carries a literal), so this expands straight to
    `surveilPipeline(count)` and always emits `SurveiledEvent` (the real gathered size drives the event,
    handling library-smaller-than-X and X = 0). Twin of the dynamic `lookAtTopAndReorder(count)`.
- `mill(count)` — top N cards into graveyard.
- `exileTop(count, target = Controller)` — top N cards of a player's library into exile (Malboro's
  "exiles the top three cards of their library"). Same Gather → Move pipeline as `mill`, destination
  exile. `count` is an `Int` or `DynamicAmount`. Pass a `target` (e.g. a `Player.You` rebind under
  `Effects.ForEachPlayer(Player.EachOpponent, …)`) to exile another player's library top.
- `lookAtTopAndKeep(count, keepCount, keepDestination?, restDestination?, restOrder?, keepFaceDown?)` —
  Ancestral Memories — keep exactly K to hand. `restOrder = CardOrder.Random` for "on the bottom in a
  random order"; `keepFaceDown = FaceDownMode.CLOAK` (or `MANIFEST`) when the kept cards go to the
  battlefield face down — Hide in Plain Sight's "look at the top five, cloak two of them".
- `lookAtTopAndTakeMatching(count, filter, prompt, selection?, revealed?, keepDestination?,
  keepRevealed?, restDestination?, restOrder?)` — Elvish Rejuvenator / Summoning Trap / Gather the Pack
  shape, and the general form of the two recipes below it: "[Look at|Reveal] the top `count` cards of
  your library. [You may] put [a|any number of|up to N] `filter` from among them `keepDestination`. Put
  the rest `restDestination` `restOrder`." Every printed word that varies is a parameter — `revealed`
  is "Look at" against "Reveal" (the flag on the gather), `selection` is the quantifier
  (`ChooseUpTo(1)` for "you may put a…", `ChooseAnyNumber`, `ChooseUpTo(n)`, `ChooseExactly(1)` for a
  bare mandatory "put a…"), `keepDestination` takes `ZonePlacement.Tapped` /
  `TappedAndAttacking` for "onto the battlefield tapped [and attacking]", and `keepRevealed` turns the
  kept card face up as it moves. Prefer this over hand-rolling the four-step pipeline.
- `lookAtTopRevealMatchingToHand(count, filter, prompt, restDestination?, restOrder?)` — Radagast the
  Brown / Star Charter shape: look at top `count`, **optionally** reveal one card matching `filter` to
  hand, rest to `restDestination` (default bottom of library) in `restOrder` (default
  `CardOrder.Random`). `count` is a `DynamicAmount` (e.g. `DynamicAmounts.triggeringManaValue()`). The
  "reveal it as it goes to hand" point of `lookAtTopAndTakeMatching`'s space; it delegates there.
- `revealTopPutAllMatchingToHand(count, filter, restDestination?, restOrder?)` — Marina Vendrell shape:
  **mandatorily** reveal the top `count`, auto-route *every* card matching `filter` to hand (a
  choice-free `FilterCollection` partition, not a "keep up to one" choice), rest to `restDestination`
  (default bottom of library) in `restOrder` (default `CardOrder.Random`). Use this for "reveal the top
  N, put all [type] cards into your hand and the rest on the bottom" wording.
- `lookAtTopAndReorder(count)` — reorder top N.
- `manifest(count = 1)` — manifest the top N cards (CR 701.40): each is put onto the battlefield
  face down as a 2/2 creature (one at a time). A manifested creature card can be turned face up for
  its mana cost; a manifested non-creature can't.
- `manifestDread(markEntered = false)` — "Manifest dread" (CR 701.60, Duskmourn): look at the top
  two cards of your library, manifest one of them (your choice), and put the other into your
  graveyard. Composes gather → select → move-face-down(MANIFEST) → move-to-graveyard →
  emit-`ManifestedDreadEvent`. Pass `markEntered = true` to stamp each manifested permanent with
  `EnteredViaAbilityComponent(this source)`, so wrapping it in
  `RepeatDynamicTimes(X, manifestDread(markEntered = true))` lets a later
  `GatherCards(CardSource.EnteredViaThisResolution)` re-collect every creature this spell manifested
  (across all X iterations and the per-iteration manifest-dread pick pauses) and reference "each of
  those creatures" — e.g. **Valgavoth's Onslaught**: `RepeatDynamicTimes(XValue, manifestDread(true))`
  → `GatherCards(EnteredViaThisResolution, "manifested")` → `AddCountersToCollection("manifested",
  +1/+1, XValue)`. The trailing `EmitManifestedDreadEventEffect` tail (internal — not for card
  authors) fires `Triggers.WheneverYouManifestDread` (see Triggers) once per manifest-dread,
  carrying the card put into the graveyard this way so a "this way" payoff can pull it back out
  (**Paranormal Analyst**).

**Reveal patterns**

- `revealUntilNonlandDealDamage(target)` — Bonecrusher Giant shape.
- `revealUntilMatchToHand(filter, restDestination?, restOrder?, count?)` — Spinner of Souls / Wirewood
  Herald shape: reveal from the top of your library until you reveal `count` cards matching `filter`
  (default 1); those cards go to hand and the cards revealed alongside them go to `restDestination`
  (default: bottom of library) in `restOrder` (default: random). If the library empties before `count`
  matches, every match found so far still goes to hand and the rest go to the rest destination.
  **Fathom Trawl** is the `count = 3` case, with `restOrder = CardOrder.ControllerChooses` for its
  "in any order".
- `wheelEffect(players)` — each player shuffles hand into library, draws that many.
- `factOrFiction(count = 5, keepZone, otherZone, ...)` — reveal/look at the top `count`, an
  opponent splits them into two piles, then you choose which pile goes to `keepZone` (hand) and
  which to `otherZone` (graveyard). The shared CR 700.3 "divvy" pile-split primitive — also drives
  Sauron's Ransom (`count = 4`, chained `.then(Effects.TheRingTemptsYou())`).

**Hand manipulation**

- `discardCards(count, target)` — controller-of-target chooses (mandatory).
- `discardCardsUnlessMatching(count, unlessFilter, target?, reducedCount?, requiredMatches?)` /
  `Effects.DiscardUnlessMatching(...)` — one-step "discard N cards unless you discard a matching card" selection;
  a lower-count selection is valid only when it includes enough cards matching `unlessFilter`.
- `discardAnyNumber(target?, filter?, storeAs?, prompt?)` — "discard any number of cards": the
  controller chooses any subset of their hand (including none) to discard, via
  `SelectionMode.ChooseAnyNumber`. The selected set is stored under `storeAs` (default `"discarded"`),
  so the count is readable downstream as `DynamicAmount.VariableReference("${storeAs}_count")` — e.g.
  Miasma Demon wires this as the `ReflexiveTriggerEffect` action and reads `discarded_count` as the
  reflexive targets' `dynamicMaxCount` ("up to that many target creatures").
- `discardUpToThenDraw(max, draw?, storeAs?, prompt?)` — "discard up to N cards, then draw that many
  cards" (Tersa Lightshatter, Sokka, Bold Boomeranger, Greasewrench Goblin). Loot backwards: the
  selection is `SelectionMode.ChooseUpTo(max)` so declining entirely is legal and then nothing is drawn,
  and the draw defaults to `DynamicAmount.VariableReference("${storeAs}_count")` — the number *actually*
  discarded, not `max`. `max` takes an `Int` or a `DynamicAmount` ("discard up to X cards"); pass `draw`
  to decouple the payoff from the discard ("discard up to two cards, then draw three").
- `discardRandom(count, target)` — random discards.
- `discardHand(target)` — discard entire hand.
- `eachOpponentDiscards(count, controllerDrawsPerDiscard?)` — each opponent independently chooses
  and discards `count` cards. When `controllerDrawsPerDiscard > 0`, the per-player pipelines collect
  the cards that actually reached graveyards and the controller draws that many times the multiplier
  in one draw instruction (Syphon Mind); this covers every opponent and naturally counts fewer cards
  when an opponent's hand is short.
- `eachPlayerPutsCardsOnTopOfLibrary(count = 1)` — each player *including you* puts N cards from their own hand on top of their own library (facade `Effects.EachPlayerPutsCardsOnTopOfLibrary(count)`). Sadistic Augermage's dies trigger. Identical `ForEachPlayer(Player.ActivePlayerFirst)` → Gather → Select → Move shape as `eachPlayerDiscards`, with `CardDestination.ToZone(Zone.LIBRARY, Player.You, ZonePlacement.Top)` and the default `MoveType` — a tuck is not a discard, so nothing here feeds a discard trigger or a madness cast. Same sequential-iteration deviation from CR 101.4a.
- `eachPlayerDiscards(count)` — each player *including you* discards N, each from their own hand (facade `Effects.EachPlayerDiscards(count)`). Rankle's Prank's first mode, Lore Broker's second half. One `ForEachPlayer(Player.ActivePlayerFirst)` iteration per player so the choices happen in APNAP order (CR 101.4); iterations run sequentially, so a later player chooses after an earlier player's cards have already hit the graveyard, where the rules would have every player choose face down (CR 101.4a) and discard simultaneously. The mtgish emitter renders `EachPlayerAction(AnyPlayer, Discard…)` to this pattern when the discard is the player's sole action.
- `eachPlayerDiscardsDraws(controllerBonusDraw?)` — Windfall / Wheel of Fortune.
- `eachPlayerDrawsX(includeController?, includeOpponents?)` — Howling Mine shape.
- `eachPlayerMayDraw(maxCards, lifePerCardNotDrawn?)` — optional group draw with a tax.
- `exileFromHand(count?, target)` — exile N from hand.
- `revealHandAndExileChosen(target?, filter?, prompt?, storeChosenAs?, storeExiledAs?, revealHand = true, linkToSource = false)` — "Target opponent
  reveals their hand. You choose a nonland card from it. Exile that card." (Cruelclaw's Heist, Soul Search).
  Thoughtseize with exile instead of discard. The chooser is always the **controller**, not the revealing
  player — that asymmetry is the pattern, so it is not derived from `target` the way `exileFromHand` derives
  its chooser. `storeChosenAs` (default `"chosenCard"`) holds the selection *before* the move;
  `storeExiledAs` holds the cards that actually reached exile, and is the key any rider should read
  (`ConditionalEffect(CollectionContainsMatch("exiledCard", Filters.ManaValueAtMost(1)), …)` for Soul
  Search's "if the card's mana value is 1 or less") — it is empty when the hand held no matching card,
  which is exactly when nothing should happen.
  `revealHand = false` drops the leading reveal and keeps only the choose-and-exile tail, for a card
  whose reveal is unconditional while the exile sits behind a "you may" or a mode — bundling the
  reveal in would make it conditional too. `linkToSource = true` files the exiled card in the source
  permanent's linked-exile pile so an "… until this leaves the battlefield" clause can find it again;
  `Effects.ExileUntilLeaves` only accepts battlefield permanents and graveyard cards, so this flag is
  how a **hand** exile joins the same pile. Cloak and Dagger, Entwined uses both.

**Sacrifice / destroy**

- `sacrifice(filter, count, then)` — sacrifice N, then run effect.
- `sacrificeFor(filter, countName, thenEffect)` — sacrifice variable count, store, then effect.
- `destroyAllPipeline(filter, noRegenerate?, storeDestroyedAs?)` — wrath pipeline with storage.
- `destroyAllAndAttachedPipeline(filter, noRegenerate?)` — wrath + attached.
- `destroyAllSharingTypeWithSacrificed(noRegenerate?)` — destroy all creatures sharing type with a sacrificed creature.

**Creature-type choice**

- `chooseCreatureTypeRevealTop()` — pick a type, reveal until matching.
- `chooseCreatureTypeReturnFromGraveyard(count)` — pick a type, return N from graveyard.
- `chooseCreatureTypeModifyStats(...)` — pick a type, buff matching.
- `chooseCreatureTypeUntap()` — pick a type, untap your matching.
- `chooseCreatureTypeGainControl(duration?)` — pick a type, control matching.
- `becomeChosenTypeAllCreatures(...)` — all creatures become the chosen type.

**Misc mechanic shapes**

- `mayPay(cost, effect)` — optionally pay cost to trigger an effect.
- `mayPayOrElse(cost, ifPaid, ifNotPaid)` — pay-or-else fork.
- `blight(amount, player?)` — Blight X additional cost glue.
- `bolster(amount)` — Bolster N (CR 701.36): controller chooses a creature with the least toughness among
  creatures they control and puts N +1/+1 counters on it. Non-targeting; no-op with no creatures. Composes
  Gather → `FilterCollection(CollectionFilter.LeastToughness)` → `SelectFromCollecti…62152 tokens truncated…rd()`.
- `Firebending(n)` — "Whenever this creature attacks, add N {R}. Until end of combat, you don't lose this mana
  as steps and phases end." (CR 702.189, Avatar: The Last Airbender). Display-only; wire the behavior with the
  `card { firebending(n) }` builder helper, which adds this keyword ability plus a "whenever this attacks"
  triggered `AddManaEffect(Color.RED, n, expiry = ManaExpiry.END_OF_COMBAT)` (mirrors `mobilize()` / `rampage()`).
  The mana is ordinary red mana spendable anywhere — it is held as an `AnySpend` restricted-pool entry tagged
  with [ManaExpiry](#manaexpiry).`END_OF_COMBAT` and discarded by `CombatManager.endCombat`. It is a normal
  triggered ability (not a mana ability): it uses the stack and can be responded to. `n` may be any fixed value;
  "firebending X (X = its power)" is not yet expressible by this helper (the keyword carries only a fixed Int).
  To **grant** firebending until end of turn ("target creature gains firebending N until end of turn", Fire Nation
  Palace), use `Effects.GrantFirebending(n, target, duration = EndOfTurn)`. Because firebending has no engine
  handler, the grant reuses the *exact* attack trigger the printed keyword installs (`firebendingAttackTrigger(n)`,
  shared with `firebending(n)`) via `GrantTriggeredAbilityEffect`, so the affected creature adds the same N {R}
  combat-duration mana on attack while the grant is live. The grant rides `GameState.grantedTriggeredAbilities`
  and is dropped in the cleanup step (EndOfTurn).
  For a **conditional static** "this creature has firebending N as long as `<condition>`" (Fire Nation Cadets —
  "… as long as there's a Lesson card in your graveyard"), wrap a *self-scoped* `GrantTriggeredAbility` in a
  `ConditionalStaticAbility`: `staticAbility { ability = ConditionalStaticAbility(GrantTriggeredAbility(
  firebendingAttackTrigger(n), filter = GroupFilter.source()), Conditions.GraveyardContainsSubtype(Subtype.LESSON)) }`.
  `GroupFilter.source()` (i.e. `Scope.Self`) grants the firebending attack trigger to the source itself; the
  `TriggerAbilityResolver` re-evaluates the gating condition each time triggers are computed, so the trigger
  toggles live — it fires on attack while the condition holds and not while it's false. (Self-scoped
  `GrantTriggeredAbility`, plain or conditional, is consulted by `TriggerAbilityResolver.getSelfGrantedTriggeredAbilities`
  alongside the existing battlefield-scope/lord and attached-aura grant paths.)
- `Increment` — "Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power
  or toughness, put a +1/+1 counter on this creature." (Secrets of Strixhaven). Display-only; wire the behavior with
  the `card { increment() }` builder helper, which adds the `KeywordAbility.Increment` display marker (surfacing
  `Keyword.INCREMENT`) plus a `Triggers.YouCastSpell` triggered `AddCounters(+1/+1, 1, Self)` gated by an
  intervening-if (CR 603.4) that compares the triggering spell's mana spent (`EntityProperty(Triggering,
  ManaSpent)`) against the source's power *or* toughness — modelled as an `AnyCondition` of two `Compare(GT)`
  arms, so it fires when the mana exceeds the smaller characteristic. No parameter (mirrors `firebending()` /
  `decayed()`).
- `Opus` — "Opus — Whenever you cast an instant or sorcery spell, [base]. If five or more mana was spent to cast
  that spell, [bonus] [instead]." (Secrets of Strixhaven). **Opus is an ability word** (CR 207.2c — flavor only),
  so it adds *no keyword*; the whole mechanic is one `Triggers.YouCastInstantOrSorcery` triggered ability wired by
  the `card { opus { … } }` builder helper. The 5+ mana tier is a `Compare` of
  `ContextProperty(MANA_SPENT_ON_TRIGGERING_SPELL) >= 5` (the mana spent on the *triggering* spell, not the
  resolving object's own cast). Author the base effect as `effect = …` and pick exactly one bonus setter:
  `insteadIfFiveOrMore = …` lowers to `ConditionalEffect(5+ → bonus, otherwise → base)` and renders "… [bonus]
  instead" (Deluge Virtuoso, Exhibition Tidecaller, Tackle Artist); `alsoIfFiveOrMore = …` lowers to
  `base then ConditionalEffect(5+ → bonus)` and runs the bonus *in addition* (Expressive Firedancer, Colorstorm
  Stallion). Declare a `target(name, requirement)` inside the block and reference the returned handle from both
  `effect` and the bonus so the single chosen target carries across both tiers (Exhibition Tidecaller's "target
  player mills three … mills ten instead"). The rendered ability text is auto-composed from the base/bonus effect
  descriptions unless `description` overrides the whole string.
- `Decayed` — "This creature can't block, and when it attacks, sacrifice it at end of combat" (CR 702.147,
  Innistrad: Midnight Hunt). Display-only; wire the behavior with the `card { decayed() }` builder helper, which adds
  the keyword plus a `CantBlock(GroupFilter.source())` static ability and a "whenever this attacks" triggered
  `CreateDelayedTriggerEffect(step = Step.END_COMBAT, effect = Effects.SacrificeTarget(EffectTarget.Self))` (mirrors
  Mardu Blazebringer's end-of-combat self-sacrifice). No parameter. The **decayed counter** (`Counters.DECAYED`,
  Tarkir: Dragonstorm) grants the same Decayed ability to *any* creature that bears one (CR 702.147a) — put it with
  `AddCounters(Counters.DECAYED, n, target)` (Rot-Curse Rakshasa's Renew). The engine realizes the behavior off the
  counter directly: `StateProjector` projects the `DECAYED` keyword + `cantBlock = true`, and `TriggerDetector`
  schedules the end-of-combat self-sacrifice when a decayed-countered creature is declared as an attacker — no
  per-card static/trigger needed for the counter form.
- `Riot` — "Riot (This creature enters with your choice of a +1/+1 counter or haste.)" (CR 702.136). Display-only
  keyword; wire it with the `card { riot() }` builder helper, which composes the Khans-Siege
  `EntersWithChoice(ChoiceType.MODE, [counter, haste])` + a mode-gated `EntersWithCounters(count = 1, selfOnly = true,
  condition = SourceChosenModeIs("counter"))` + a mode-gated `ConditionalStaticAbility(GrantKeyword(HASTE,
  GroupFilter.source()), SourceChosenModeIs("haste"))`. **Grant-aware:** when Riot is *granted* to other permanents
  (`GrantKeyword(Keyword.RIOT, <group>)`, e.g. Spider-Punk's "Other Spiders you control have riot"), the engine
  synthesizes one enters-with choice per granting lord (`RiotSynthesis.grantedRiotInstanceCount`, honoring each lord's
  `excludeSelf` and its *projected* controller — one instance per grant, CR 702.136b), wired into the spell-resolution
  + token/land entry seams; the choice resumer applies each chosen counter/haste branch directly and re-pauses for the
  next instance (a granted permanent has none of the printed replacement/static abilities to fall back on).
  `GrantCantBeCountered` gained an `includesAbilities` flag (default false) so "spells **and abilities** can't be
  countered" (Spider-Punk) also makes matching abilities uncounterable (e.g. Stifle fizzles); `DamageCantBePrevented`
  is a battlefield replacement scoped by its own `appliesTo` pattern — while one is on the battlefield (or the "damage
  can't be prevented this turn" one-shot is active) `DamageUtils.applyDamagePreventionShields` applies no prevention
  shields to the damage instances that pattern names (CR 615.12).
- `Champion an [object]` — "Champion a Goblin (When this enters, sacrifice it unless you exile another
  Goblin you control. When this leaves the battlefield, that card returns to the battlefield.)"
  (CR 702.72, Lorwyn). Display-only keyword (`Keyword.CHAMPION`); wire the behavior with the
  `card { champion(Subtype.GOBLIN) }` builder helper — or `champion(quality: GameObjectFilter,
  qualityDescription: String)` for a non-tribal quality, of which `championCreature()` ("champion a
  creature", the three Changelings) is the only printed one. It adds the keyword plus **two linked
  triggered abilities** (CR 702.72b / 607.2k), both composed from existing primitives with no new
  executor:
  - **enters** — an `IfYouDoEffect` over a Gather → Select → Move pipeline. The quality is chosen,
    **not targeted** (the printed text has no "target"): `CardSource.BattlefieldMatching(filter =
    quality.notSourceItself(), player = Player.You)`, then `SelectionMode.ChooseUpTo(1)` with
    `useTargetingUI = true`, then a move to exile carrying `linkToSource = true` and
    `storeMovedAs = CHAMPIONED_CARDS`. `ChooseUpTo(1)` **is** the "unless": picking nothing is always
    legal, and with no eligible permanent the selection resolves with no prompt at all. The gate's
    criterion is `SuccessCriterion.CollectionNonEmpty(CHAMPIONED_CARDS)` — the cards that actually
    reached exile, not merely the ones picked — with `otherwise = SacrificeSelfEffect` and
    `then = EmitChampionedEventEffect()`.
  - **leaves** — `Triggers.LeavesBattlefield` running `Effects.ReturnLinkedExileUnderOwnersControl()`,
    which reads the linked-exile pile of the **originating battlefield visit**, so a champion that
    blinks never returns the other visit's card and never sacrifices for the other visit's obligation.
  - **the quality is a permanent filter.** "Champion a Goblin" is a bare tribal noun, so per CR 109.2
    it means a Goblin *permanent* — a Kindred noncreature Goblin is a legal choice. The `Subtype`
    overload builds `GameObjectFilter.Permanent.withSubtype(subtype)`; narrowing it to `Creature`
    would silently drop those. `notSourceItself()` supplies "another" and is *visit-aware*.
  - **two triggers, not an "exile until" replacement.** This is what CR 702.72a says, and it
    reproduces the printed interaction Fiend Hunter documents: a champion removed before its enters
    trigger resolves has already run its leaves trigger against an empty pile, and then exiles a
    permanent that never comes back. The sacrifice is a genuine no-op when the champion is already
    gone, exactly as the printed instruction behaves.
  - **CR 702.72c payoff** — `Triggers.championedWith()` (`EventPattern.ChampionedEvent`) is
    "when a [quality] is championed with this creature" (Mistbind Clique). It fires only when a
    permanent actually reached exile, so declining the choice taps nothing. The quality is not
    restated on the trigger: the champion clause above it can only ever exile a matching permanent.
  Cards: Boggart Mob, Changeling Berserker/Hero/Titan, Mistbind Clique, Nova Chaser, Thoughtweft
  Trio, Wanderwine Prophets, Wren's Run Packmaster. Pinned by `ChampionKeywordTest` (17 scenarios
  covering accept/decline/no-candidate, "another", the tribal-vs-creature quality, projected types,
  owner's-control return, linkage, tokens, trigger ordering, and blinking).
- `Exploit` — "Exploit (When this creature enters, you may sacrifice a creature.)" (CR 702.110, Dragons of Tarkir;
  reprinted MH1/MH2/VOW/PIP/MH3). Display-only keyword; wire the behavior with the `card { exploit(onExploit, onExploitTargets) }`
  builder helper. It adds the keyword plus one `EntersBattlefield` triggered ability whose effect is a
  `ReflexiveTriggerEffect(optional = true)`:
  - **action** = `CompositeEffect(SacrificeEffect(GameObjectFilter.Creature, count = 1), EmitExploitedEventEffect)` — an
    optional "you may sacrifice a creature" (any one creature the controller owns, **including this creature itself** —
    CR 701.17a scopes sacrifice to the controller, and there is no self-exclusion), immediately followed by
    `EmitExploitedEventEffect`, which fires an observable `EventPattern.ExploitedEvent` (CR 702.110b). Declining the
    optional sacrifice sacrifices nothing, so no `ExploitedEvent` is emitted and no payoff fires (satisfies CR 702.110a's "may").
  - **reflexiveEffect** = the self-bound "when this creature exploits a creature, …" payoff (`onExploit`), or a no-op
    `CompositeEffect(emptyList())` when `onExploit = null`. Prefer baking a *self-bound* payoff into the reflexive
    (established while the source is on the battlefield, run "when you do") rather than a separate SELF-bound
    `ExploitedEvent` trigger: the reflexive path needs no gone-source detection, whereas a SELF-bound `ExploitedEvent`
    watcher would rely on the sacrifice look-back below.
  - `onExploitTargets` supplies `reflexiveTargetRequirements` for a **targeted** payoff (Fell Stinger's "target player
    draws two cards and loses 2 life"), chosen *after* the sacrifice resolves.
  Examples: Stitched Assistant `exploit(onExploit = scry(1) then draw(1))` (untargeted self-payoff); Fell Stinger
  `exploit(onExploit = <target player draws 2, loses 2>, onExploitTargets = listOf(Targets.Player))`; Skull Skaab
  `exploit()` (no self-payoff) **plus** a hand-written broadcast watcher `triggeredAbility { trigger =
  EventPattern.ExploitedEvent(player = Player.You, requireNontokenExploited = true); effect = <create a 2/2 black Zombie> }`.
  **Self-exploit look-back (CR 603.10a):** sacrifice triggers "look back in time", so an exploiter's own
  `ExploitedEvent` watcher (SELF or ANY binding — e.g. Skull Skaab exploiting itself) still fires even though the
  exploiter is in the graveyard when the event resolves. `TriggerDetector.detectExploitedSelfSacrificeTriggers` supplies
  this pass by resolving the gone exploiter's last-known abilities from `event.exploiterId`; the main battlefield index
  scan handles the exploiter-still-present case, and a battlefield-presence guard keeps the two from double-firing.
  `EmitExploitedEventEffect` is an internal `data object` (no player-facing text) and should not be used directly — it is
  wired into `exploit()`. See `EventPattern.ExploitedEvent` under Sacrifice triggers for the watcher form.
- `Soulbond` — "Soulbond (You may pair this creature with another unpaired creature when either enters. They remain
  paired for as long as you control both of them.)" (CR 702.95, Avacyn Restored; reprinted MM3/INR). Display-only keyword;
  wire the behavior with the `card { soulbond() }` builder helper. It adds the keyword plus the **two** triggered
  abilities CR 702.95a defines, both composed from existing primitives — the only soulbond-specific vocabulary is
  `PairWithSourceEffect`:
  - **"When this creature enters, … you may pair this creature with another unpaired creature you control"** — a
    `Effects.Pipeline { }` of `gather(GameObjectFilter.Creature.unpaired(), player = Player.You, excludeSelf = true)` →
    `chooseUpTo(1, useTargetingUI = true)` → `pairWithSource(…)`. The `upTo` *is* the "you may" (selecting nothing
    declines), and an empty gather prompts nothing, which is also how the intervening-if's "if you control … another
    creature" comes out right. `useTargetingUI` puts the choice on the battlefield rather than in an overlay.
  - **"Whenever another creature you control enters, … you may pair that creature with this creature"** —
    `Triggers.OtherCreatureEnters` (which already carries the "another creature **you control**" clause) with
    `optional = true` for a plain yes/no, `triggerRestriction = Conditions.SourceIsUnpaired` for the rest of the
    intervening-if (a creature that just entered can never already be paired), and a
    `Pipeline { gather(CardSource.TriggeringEntity); pairWithSource(…) }` body.
  The **payoff** clause is an ordinary static ability whose `GroupFilter` is `GroupFilter.soulbondPair()`
  (`Scope.SoulbondPair`) — "both creatures" / "each of those creatures". That scope resolves to `{source, partner}` while
  paired and to the **empty set** while unpaired, so "as long as this creature is paired with another creature" is
  self-enforcing and needs no `condition =` gate: Lightning Mauler is just
  `GrantKeyword(Keyword.HASTE, GroupFilter.soulbondPair())`, Spectral Gateguards is the same with `VIGILANCE`,
  Tandem Lookout is `GrantTriggeredAbility(<whenever this creature deals damage to an opponent, draw a card>,
  GroupFilter.soulbondPair())` — hosted on *each* half, so `TriggerBinding.SELF` makes "this creature" mean whichever
  one dealt the damage — and Deadeye Navigator is
  `GrantActivatedAbility(<{1}{U}: blink self>, GroupFilter.soulbondPair())` — a granted ability's `EffectTarget.Self`
  binds to the permanent that *has* it (CR 113.7), so activating it on the partner blinks the partner.
  Pairing state lives in the engine, not the SDK: `PairWithSourceExecutor` stamps a symmetric `PairedComponent` on both
  halves (enforcing CR 702.95c — either half no longer a creature, off the battlefield, or under another controller and
  *neither* becomes paired — and CR 702.95d's one-partner limit), `SoulbondPairingCheck` (`SbaOrder.SOULBOND_UNPAIRING`)
  breaks the pair the moment CR 702.95e applies, `ZoneMovementUtils.stripBattlefieldComponents` clears the leaving half,
  and `CreaturesPairedEvent` / `CreaturesUnpairedEvent` surface it to the client as `pairedWithId` (which the
  `SoulbondBonds` overlay draws as a bond between the two battlefield slots). Query pairing from a filter or condition
  with `GameObjectFilter.paired()` / `.unpaired()` (`StatePredicate.IsPaired`) and `Conditions.SourceIsPaired` /
  `SourceIsUnpaired`.
- `Training` — "Training (Whenever this creature and at least one other creature with power greater than this creature's
  power attack, put a +1/+1 counter on this creature.)" (CR 702.149, Innistrad: Midnight Hunt; also WHO, SLD). Display-only
  keyword; wire the behavior with the `card { training() }` builder helper. It adds the keyword plus one attack-triggered
  ability — `Triggers.attacks(requires = setOf(AttackPredicate.AttackedAlongsideGreaterPower))` (SELF) → a two-step
  `CompositeEffect(Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self), EmitTrainedEventEffect)`. The predicate
  reads **projected** power for every attacker (§8 `AttackPredicate`), so an anthem/aura on the *other* attacker can flip the
  trigger on; the comparison is strict. Multiple instances trigger separately (CR 702.149b) — call `training()` twice, or add a
  second `trainingTriggeredAbility()`, for two independent counters. The standalone `trainingTriggeredAbility()` factory (same
  file) exposes just the ability so an **intrinsically-training token** can carry it: `Effects.CreateToken(…, keywords =
  setOf(Keyword.TRAINING))` sets the display badge and `CreateTokenEffect(triggeredAbilities = listOf(trainingTriggeredAbility()))`
  supplies the behavior (Torens, Fist of the Angels' "1/1 Human Soldier creature token with training" — the token trains when it
  later attacks alongside greater power). **CR 702.149c "when this creature trains"** (a resolving training ability placing ≥1
  +1/+1 counter) is realized by the `EmitTrainedEventEffect` tail: after the sibling `AddCounters` resolves it recomputes the
  placement decision on the post-placement state (still on battlefield, `canReceiveCounters`, ≥1 after counter-placement
  replacements) and, only if a counter actually landed, fires an observable `EventPattern.TrainedEvent` — so a Solemnity-type
  "can't have counters" prohibition trains nothing and no payoff fires. This is a *dedicated* signal, never a generic
  counter-placed watcher (which would fire for non-training counters too). `EmitTrainedEventEffect` is an internal `data object`
  (no player-facing text) wired into `training()`; do not use it directly. See `EventPattern.TrainedEvent` under counter triggers
  for the watcher form (Savior of Ollenbock).
- `Job select` — "Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach
  this to it.)" (Final Fantasy). Equipment keyword; display-only. Wire it with the `card { jobSelect() }` builder
  helper, which adds the keyword plus an `EntersBattlefield` triggered ability composing two existing primitives
  through the token pipeline: `Effects.CreateToken(power = 1, toughness = 1, creatureTypes = setOf("Hero"))` (no
  colors → colorless) publishes the new token's id to the `createdTokens` slot, then
  `Effects.AttachEquipment(EffectTarget.PipelineTarget(CREATED_TOKENS, 0))` attaches the source Equipment to that
  freshly-made token. No new effect/executor — it reuses the same create-then-attach-on-ETB chain as Auxiliary
  Boosters. Author the per-card equip cost and equipped-creature bonus alongside the `jobSelect()` call (e.g. Monk's
  Fist: `jobSelect()` + `ModifyStats(1, 0)` + `GrantSubtype("Monk", Filters.EquippedCreature)` + `equipAbility("{2}")`).
- `Toxic(n)` — adds poison counters on combat damage.
- `Cycling(cost)` — pay cost, discard, draw a card. The cost may contain `{X}`
  (`KeywordAbility.cycling("{X}{G}{G}")`, Webstrike Elite): cycling is an activated ability (CR 702.29a), so X is
  announced as it's activated (CR 107.3a). The legal action carries `hasXCost` / `maxAffordableX` so the client's
  xSelection phase asks for X and submits it on `CycleCard.xValue`; a bare submission instead pauses on an engine
  `ChooseNumberDecision` (`CycleCardChooseXContinuation`) rather than silently defaulting X to 0. The announced X
  rides `CardCycledEvent.xValue` into the cycling trigger's context, so a `Triggers.YouCycleThis` payoff reads it as
  `DynamicAmount.XValue` (Valor's Flagship's "create X Pilot tokens") or via `manaValueEqualsX()` /
  `manaValueAtMostX()` in a target filter (Webstrike Elite's "artifact or enchantment with mana value X").
  X-cost **typecycling** is not wired — no such card is printed.
- `BasicLandcycling(cost)` — cycling that fetches a basic land type.
- `Typecycling(type, cost)` — cycling that fetches a card type.
- `Plot(cost)` — `KeywordAbility.plot(cost)`. Special action available during your main phase while the stack is empty: pay [cost] and exile the card from your hand. It becomes plotted (stamped with a `PlottedComponent`). On a later turn you may cast it from exile without paying its mana cost, as a sorcery (CR 718). Cast permission is granted via the engine's standard `MayPlayPermission` + `PlayWithoutPayingCostComponent`, gated by `Conditions.SourcePlottedOnPriorTurn`. No card-side wiring needed — declare the keyword ability on the card and the engine handles the rest.
- `Foretell(cost)` — `KeywordAbility.foretell(cost)` (Kaldheim, CR 702.143). A sorcery-speed **special action** available while you have priority during your own turn: pay the fixed **{2}** setup cost and exile the card from your hand **face down** (`ForetoldComponent` + `FaceDownComponent` — hidden from opponents in exile, visible to its owner). On a **later** turn (not the turn it was foretold) you may cast it from exile by paying its **foretell cost** `[cost]` rather than its mana cost. Cast permission is a standard permanent `MayPlayPermission` gated by `Conditions.SourceForetoldOnPriorTurn`, plus a `PlayWithFixedAlternativeManaCostComponent` carrying `[cost]` — the same fixed-alternative-cast machinery **Airbend** uses, honored by `CastFromZoneEnumerator` + `CastSpellHandler` and stripped on leaving exile by `StackResolver` (which also strips the `FaceDownComponent` as the card is cast face up). Structurally Plot's paid cousin (`ForetellCardHandler` / `ForetellEnumerator` mirror `PlotCardHandler` / `PlotEnumerator`): plot is free to set up and free to cast later, foretell costs {2} to exile and has a distinct foretell cost to cast. No card-side wiring needed — declare the keyword ability and the engine handles the rest.
- `Suspend(cost, timeCounters)` — `KeywordAbility.suspend(cost, n)` (Time Spiral, CR 702.62). Unlike every other
  entry in this list, exiling this way is **not a cast** — CR 702.62a: "this action doesn't use the stack" — so it's
  wired as its own special action (`SuspendCardFromHand` / `SuspendCardFromHandHandler` / `SuspendEnumerator`),
  structurally mirroring Plot/Foretell (pay `[cost]`, move hand → exile) but never touching `CastSpellHandler` or
  `AlternativeCostType`. Legal any time the player could *begin* to cast the card (instant speed for an
  instant/flash, sorcery speed otherwise), independent of whether the card's mana cost could ever actually be paid.
  On exiling, the handler puts `timeCounters` time counters on the card and stamps it exactly like
  `GrantSuspendExecutor` (`SuspendedComponent` + dormant haste), handing off into the pre-existing
  `Suspend.countdownAbility` — the same countdown-and-free-cast machinery a runtime-granted suspend (Taigam, Master
  Opportunist) uses. See the full mechanic writeup under §11 "Keywords" → `Suspend` above. **Ancestral Vision** is the reference
  card — a Sorcery with **no printed mana cost** (`hasNoManaCost = true`), playable only through Suspend.
- `Hideaway(n)` — `KeywordAbility.hideaway(n)`; display tag rendered "Hideaway N". Mechanic is composed manually via `MoveCollectionEffect(faceDown = FaceDownMode.HIDDEN, linkToSource = true)` + `CardSource.FromLinkedExile()` — the keyword itself carries no engine behavior.
- `Harmonize(cost)` — `KeywordAbility.harmonize(cost)` (Tarkir: Dragonstorm). An alternative cost to cast an instant/sorcery **from your graveyard**, like Flashback, then exile it as it resolves. As you cast it you may tap **a single** untapped creature you control to reduce the **generic** portion of the harmonize cost by that creature's (projected) power — a Convoke-style reduction, but one creature paying generic-equal-to-power instead of one mana per creature. No card-side wiring: declare the keyword ability and the engine handles graveyard-cast enumeration (`CastWithHarmonize`), the per-creature reduction (routed through `AlternativePaymentChoice.harmonizeCreature`), and the exile-on-resolution. The chosen creature and its power are surfaced to the client via `LegalAction.harmonizeCreatures` / `hasHarmonize`; the client offers an on-battlefield single-creature tap step (the `harmonize` pipeline phase + `HarmonizeSelector` HUD, mirroring Convoke). **Harmonize {X}** (e.g. Nature's Rhythm `{X}{G}{G}{G}{G}`): the `CastWithHarmonize` action surfaces `hasXCost`/`maxAffordableX` (max X folds in the best single-creature tap reduction) so the client prompts for X. {X} is generic mana, so the tap reduces the mana paid *for X* — `CastSpellHandler.harmonizePaymentXValue` lowers the X mana once `reduceGeneric` has consumed any printed generic — while the chosen X stamped onto `SpellOnStackComponent.xValue` (and read by the effect, e.g. "mana value X or less") is unchanged. Colored pips are never reduced. **Granting harmonize at runtime:** harmonize can also be granted to a graveyard card that doesn't print it via `Effects.GrantHarmonize(target, cost?, duration)` (Songcrafter Mage). The grant is a `GrantedKeywordAbility` record keyed to the card entity; every harmonize read site consults printed-**or**-granted harmonize through the `HarmonizeGrants.effectiveHarmonize` resolver, so a granted harmonize is castable, reducible, and exiled exactly like a printed one. The grant survives the graveyard → stack move (so exile-on-resolution still fires) and is cleared in the cleanup step.
- **Tap-for-generic payments (the shared rail: Improvise, Waterbend).** Every mechanic shaped as *"you may tap an untapped permanent you control rather than pay {1} generic"* runs on **one** rail, not a field each. The chosen permanents travel in `AlternativePaymentChoice.tapForGenericPermanents` (a `Set<EntityId>`); the engine applies them through `AlternativePaymentHandler.applyTapForGeneric`, which takes a `TapForGeneric` eligibility value (`rules-engine/.../mechanics/mana/TapForGeneric.kt`) plus an optional cap. Each tap removes {1} of **generic** — never a colored pip, and never more taps than there is generic to pay. Enumeration surfaces it once, as `LegalAction.hasTapForGeneric` / `tapForGenericPermanents` / `tapForGenericAmount` (the cap, null when the bound is just the cost's generic) / `tapForGenericLabel` (the player-facing verb), built by `CostEnumerationUtils.findTapForGenericPermanents(state, playerId, eligibility)` + `canAffordWithTapForGeneric`. The client has one HUD for all of them: the `tapForGeneric` pipeline phase + `TapForGenericSelector`, which reads the label so the bar says "improvise" or "waterbend". **Adding another mechanic of this shape is one `TapForGeneric` entry, not a new payment field, handler branch, or UI.**
  - `Keyword.IMPROVISE` — **Improvise** (CR 702.126). *"For each generic mana in this spell's total cost, you may tap an untapped artifact you control rather than pay that mana."* A plain `keywords(Keyword.IMPROVISE)` on the card; no other wiring. Artifacts only, spells only, and it is neither an additional nor an alternative cost (CR 702.126b) — it applies *after* the total cost is determined, so it never changes mana value and has no cap beyond the generic in that cost. Multiple instances are redundant (CR 702.126c). Grantable to other spells with `GrantKeywordToOwnSpells(Keyword.IMPROVISE, filter)` — **Ironheart, Clever Champion** (`GameObjectFilter.Noncreature`) — resolved through the same `GrantedKeywordResolver` every cost keyword uses, so granted improvise behaves exactly like printed. Cards: Ironheart, Clever Champion; Arc Reactor. It also composes with another alternative payment on the same spell: because the grant is by card type, improvise routinely lands on delve and convoke spells (which are noncreature almost to a card), and `CastSpellHandler` applies delve/convoke first and improvise second. `canAffordWithDelve` / `canAffordWithConvoke` take an optional `tapForGenericPermanents` pool so enumeration agrees with that — an unaffordable cast is *dropped* from the legal actions, not greyed out, so a missed combination makes a legal play unreachable. **Two scope limits to know before picking up an improvise card.** (a) *Spells cast from hand or a cast-from-zone permission are covered; the `{X}` generic is not yet.* Per CR 601.2b/601.2f the value of X is locked in before the total cost is determined, so CR 702.126a's "generic mana in this spell's total cost" **includes** the X-derived generic — the Whir of Invention ruling: choose X=3 on `{X}{U}{U}{U}` and two taps leave you paying `{1}{U}{U}{U}`. The engine currently credits taps only against the *printed* generic, and the enumerator's `maxAffordableX` deliberately ignores improvise to stay on the under-offering side; see the TODO in `CastSpellEnumerator`'s `maxAffordableX` block. Four printed cards need it — Whir of Invention, Universal Surveillance, Saheeli's Directive, Battle at the Bridge — and none is implemented yet, so implement that fix with the first of them. (b) *`CastFromZoneEnumerator` doesn't offer improvise* (graveyard/exile casts): no implemented card needs it, and closing it is the same metadata post-pass `CastSpellEnumerator.applyImproviseMetadata` already runs.
  - **Waterbend** (Avatar: The Last Airbender) — *not a keyword ability*; a cost flag on an activated ability. Set `hasWaterbend = true` in the `activatedAbility { }` block (alongside a `cost = Costs.Mana("{N}")`). It means "Waterbend {N}: pay {N}, but for each generic mana in that cost you may tap an untapped **artifact or creature** you control instead." Improvise widened to creatures (or Convoke widened to artifacts and restricted to generic-only). You can tap a permanent that just came under your control — no summoning-sickness gate. The activated-ability handler applies it via `AlternativePaymentHandler.applyWaterbendForAbility`. The ability's `description` auto-prefixes "Waterbend " before the cost.
- **Spell-level waterbend additional cost** (Avatar: The Last Airbender) — *"As an additional cost to cast this spell, [you may] waterbend {N}."* Declared in the card builder with `waterbendCost(amount, optional = false, isX = false)`, which sets `CardScript.spellWaterbend: SpellWaterbendCost`. It adds {N} generic to the spell's cost; the same `AlternativePaymentChoice.tapForGenericPermanents` taps pay it, **bounded by N** so taps never cover the spell's own generic. `optional = true` models "you may waterbend {N}" — the enumerator offers a second, *paid* cast variant, and paying it sets `ChoiceSlot.WATERBEND_PAID` so the effect branches via `Conditions.WaterbendWasPaid` (the waterbend analogue of `BlightWasPaid`, e.g. `ConditionalEffect(Conditions.WaterbendWasPaid, paidEffect, elseEffect = baseEffect)`); a mandatory cost always adds {N}. Wiring: `CastSpellHandler` adds {N} and applies `AlternativePaymentHandler.applyWaterbendForSpell` (capped at N); `CastSpellEnumerator` surfaces `hasTapForGeneric`/`tapForGenericPermanents`/`tapForGenericAmount = N` on the cast action, reusing the same client `tapForGeneric` pipeline phase + `TapForGenericSelector`. Cards: Benevolent River Spirit (mandatory {5}), Ruinous Waterbending (optional {4}), Spirit Water Revival (optional {6}). The **`isX` "waterbend {X}" shape** (`waterbendCost(isX = true)`) is fully wired: the enumerator folds a literal `{X}` into the cost so the spell reads as X-carrying (`maxAffordableX` bounded by available mana **plus** tappable permanents), the client prompts for X then runs the waterbend tap step (capped at the chosen X), and the resolver charges X as the waterbend generic — so X also feeds the effect via `DynamicAmount.XValue`. *(The two `isX` cards Crashing Wave and Foggy Swamp Visions each still need a card-specific effect beyond the cost — "distribute N counters among a filtered group chosen at resolution", and "token copy of each exiled card" + delayed sacrifice — before they can ship.)* The **in-resolution "unless you waterbend {N}" shape** (Waterbending Lesson) is wired separately as `Effects.UnlessYouWaterbend(amount, otherwise)` — a `Gate.MayPay` over a waterbend-flagged `PayManaCostEffect`, resolved during the spell's resolution rather than as a cast-time cost (see the gated-effects section).
- `OptionalAdditionalCost(manaCost?, additionalCost?, multi, displayPrefix, branchesEffect, grantsFlashTiming, declaredSlot)` — generalised "pay an optional extra cost while casting" primitive. `declaredSlot` (default `ChoiceSlot.KICKED`) is the durable slot the declaration stamps — i.e. *which mechanic* is riding the rail, so `ChoiceSlot.BARGAINED` makes the same machinery Bargain (CR 702.166) and `ChoiceSlot.TEAMWORK` makes it Teamwork N (CR 702.194), without either reading as kicked. Backs printed Kicker / Multikicker / Offspring / Bargain **and** the pre-kicker "pay {N} more to cast as though it had flash" pattern (Ghitu Fire). When `branchesEffect = true` (default) paying the cost marks the spell so `WasKicked` fires for the card's own effect/triggers; when `false` the payment is invisible to `WasKicked` (used by `flashKicker`). When `grantsFlashTiming = true` paying the cost unlocks instant-speed casting in addition to whatever else it does — the optional cost may be mana (Ghitu Fire: `KeywordAbility.flashKicker("{2}")`) **or** a non-mana `additionalCost` such as Behold (Molten Exhale: "you may cast this as though it had flash if you behold a Dragon", `KeywordAbility.flashKicker(Costs.additional.Behold(filter = Filters.WithSubtype("Dragon")))`). Prefer the factories: `KeywordAbility.kicker(cost)`, `KeywordAbility.kicker(additionalCost)`, `KeywordAbility.multikicker(cost)`, `KeywordAbility.offspring(cost)`, `KeywordAbility.flashKicker(cost)`, `KeywordAbility.flashKicker(additionalCost)`. Serial name is `Kicker` for wire compatibility. **Kicker {X}** (variable kicker, e.g. `KeywordAbility.kicker("{X}")` on Verdeloth the Ancient): the kicked cast surfaces `hasXCost`/`maxAffordableX` so the client prompts for X exactly like a base-cost X spell; the chosen X is paid as part of the kicker and stamped onto `SpellOnStackComponent.xValue`, so the card's ETB trigger reads it via `DynamicAmount.XValue` ("create X tokens").
- `Impending(time, cost)` — `card { impending(n, cost) }` builder helper (CR 702.176, Duskmourn). A self-alternative
  cost: pay [cost] instead of the mana cost and the permanent enters with N **time counters**, isn't a creature until
  the last is removed, and loses one at the beginning of your end step. The helper wires everything from one call — the
  `KeywordAbility.Impending` alt-cost (display + cast enumeration), a `ConditionalStaticAbility(RemoveCardType("CREATURE"),
  Conditions.SourceHasCounter(TIME))` "isn't a creature while it has a time counter" static, and a `YourEndStep`
  triggered ability (gated by the same intervening-if) that removes a time counter. The engine places the N TIME counters
  when a spell cast for its impending cost resolves; casting for the normal mana cost adds no counters, so neither wiring
  fires (mirrors `prowess()` / `rampage()`).
- `Sneak(cost)` — `card { sneak("{cost}") }` builder helper (CR 702.190, Teenage Mutant Ninja Turtles). An
  alternative cost with a built-in **timing permission**: *"Any time you could cast an instant during your declare
  blockers step, you may cast this spell by paying [cost] and returning an unblocked creature you control to its owner's
  hand rather than paying this spell's mana cost."* A permanent spell whose sneak cost was paid enters **tapped and
  attacking** the same defender the returned creature was attacking (CR 702.190b). The helper just attaches the
  `KeywordAbility.Sneak` display marker; all behavior is in the engine: the dedicated `SneakCastEnumerator` surfaces a
  `CastWithAlternativeCost` (`AlternativeCostType.SNEAK`) only during the active player's declare blockers step while they
  control an unblocked attacker, with a `BouncePermanent` additional cost listing the returnable attackers; `CastSpellHandler`
  charges the sneak mana, returns the chosen attacker to hand, and stamps the sneak-was-paid flag; `StackResolver` enters a
  resolving permanent tapped and attacking. Read "its sneak cost was paid" via `Conditions.SneakCostWasPaid`.
- `Ninjutsu(cost)` — `card { ninjutsu("{cost}") }` builder helper (CR 702.49). *"[cost], Return an unblocked attacker you
  control to hand: Put this card onto the battlefield from your hand tapped and attacking."* **Mechanically identical to
  `Sneak`** — Ninjutsu is the canonical rules keyword, `Sneak` its reflavor in the custom TMNT set — so both share the
  engine's declare-blockers alternative-cost pipeline. The two keyword abilities expose their cost through one property,
  `KeywordAbility.ninjutsuStyleCost`, which `SneakWindow`/`SneakCastEnumerator`/`CastSpellHandler` read; a new reflavor of
  the mechanic only overrides that property. A card put onto the battlefield this way enters **tapped and attacking** the
  same defender the returned creature was attacking (CR 506.3a); a card that isn't a creature as it enters (e.g. an
  un-animated planeswalker) just enters tapped. Used by *Kaito, Bane of Nightmares* (DSK) — a planeswalker with ninjutsu
  whose own static makes it a creature on your turn, so it can enter attacking.
- `WebSlinging(cost)` — `card { webSlinging("{cost}") }` builder helper (CR 702.188, Marvel's Spider-Man). An
  alternative cost bundling a non-mana portion: *"You may cast this spell by paying [cost] and returning a **tapped**
  creature you control to its owner's hand rather than paying its mana cost."* Unlike `Sneak`/`Ninjutsu` it grants **no
  timing permission** — the spell is web-slung at its **normal timing** (sorcery speed for creatures; any priority for the
  instant *Spider-Sense*), so it is *not* routed through `ninjutsuStyleCost`. The mana value is unchanged (CR 118.9c) and
  cost increases/reductions still apply on top (CR 118.9d). The helper attaches the `KeywordAbility.WebSlinging` display
  marker; all behavior is in the engine: the dedicated `WebSlingingCastEnumerator` surfaces a `CastWithAlternativeCost`
  (`AlternativeCostType.WEB_SLINGING`) at the card's normal timing while the player controls a tapped creature, with a
  `BouncePermanent` additional cost listing the returnable tapped creatures; `CastSpellHandler` charges the web-slinging
  mana, returns the chosen creature to hand, and stamps two durable facts on the resolving permanent — the flag
  `ChoiceSlot.WEB_SLUNG` (read via `Conditions.WebSlungCostWasPaid`, e.g. *Spiders-Man, Heroic Horde*'s enters trigger) and
  the returned creature's mana value under `ChoiceSlot.WEB_SLUNG_RETURNED_MV` (read via `DynamicAmount.CastChoice`, e.g.
  *Scarlet Spider, Ben Reilly* enters with that many +1/+1 counters).
- `GrantWebSlingingToSpells(cost, spellFilter)` — a static ability that **grants web-slinging `cost`** to spells the
  controller casts matching `spellFilter` (web-slinging carries a `ManaCost`, which the generic `GrantKeywordToOwnSpells`
  can't express). Read through `WebSlinging.effectiveWebSlinging` (printed → this battlefield grant), which the
  `WebSlingingCastEnumerator` and `CastSpellHandler` consult, so a granted web-slinging behaves exactly like a printed
  one. Amazing Spider-Man (back of Peter Parker): "Each legendary spell you cast that's one or more colors has web-slinging
  {G}{W}{U}" → `GrantWebSlingingToSpells({G}{W}{U}, GameObjectFilter(cardPredicates = [IsLegendary, IsColored]))`.
  **Gating this with a condition is silently inert today.** `staticAbility { condition = … }`
  wraps the ability in a `ConditionalStaticAbility`, and `WebSlinging` matches the bare type
  without unwrapping it — so the grant never applies rather than applying conditionally. Teach
  that read site to unwrap first; `FlashTypeGrants.activeGrant` is the worked example.
- `Emerge(cost)` — `card { emerge("{cost}") }` builder helper (CR 702.119, Eldritch Moon). A **hand** alternative
  cost that bundles a sacrifice *and* a cost reduction derived from it: *"You may cast this spell by paying [cost] and
  sacrificing a creature rather than paying its mana cost"* plus *"if you chose to pay this spell's emerge cost, its
  total cost is reduced by an amount of **generic** mana equal to the sacrificed creature's mana value."* Generic-only,
  so a colored pip is never reduced and mana value beyond the generic portion is wasted (a mana-value-7 creature turns
  Elder Deep-Fiend's emerge {5}{U}{U} into {U}{U}, not into {0}). Grants **no timing permission** — the spell is cast at
  its normal timing, which is why Elder Deep-Fiend needs its own flash. All behavior is in the engine, keyed off the
  `KeywordAbility.Emerge` entry and centralised in `EmergeCasts`: `EmergeCastEnumerator` surfaces a
  `CastWithAlternativeCost` (`AlternativeCostType.EMERGE`) whose `additionalCostInfo` is an ordinary
  `SacrificePermanent` selection, filtered to **only the creatures that leave the reduced cost payable** — affordability
  is per-candidate, so offering an unpayable one would hand the client (and the AI, which takes the first candidate) an
  action that errors on submission. `CastSpellHandler` prices the cast against the creature actually chosen and
  sacrifices it **after** the mana payment: CR 601.2f–g activate mana abilities before CR 601.2h pays the total cost, so
  the creature may legally be tapped for mana toward its own emerge cost before it dies. The chosen creature rides
  `CastSpell.additionalCostPayment.sacrificedPermanents`, exactly as Sneak's bounce rides `bouncedPermanents`. Printed
  only — no card grants emerge. Because emerge is the one cost whose *mana* half depends on which permanent pays its
  *non-mana* half, the enumerator also sends `AdditionalCostData.costAfterSacrifice` — the surviving mana cost per
  candidate — so the client can show `{5}{U} → {2}{U}` live as the player picks and price manual mana-source selection
  off the chosen entry. The client never re-derives the reduction: the generic-only clamp is a rule, and rules stay
  server-side.
- `Mayhem(cost)` — `card { mayhem("{cost}") }` builder helper (CR 702.187, Marvel's Spider-Man). A **graveyard**
  alternative cost: *"As long as you discarded this card this turn, you may cast it from your graveyard by paying [cost]
  rather than paying its mana cost."* Grants **no timing permission** (normal timing — sorcery speed unless the card is an
  instant or has flash). Crucially, unlike `Flashback`/`Harmonize` the spell is **not exiled on resolution** — a permanent
  simply enters the battlefield and an instant/sorcery goes to the graveyard as normal. All behavior is in the engine:
  `CastFromZoneEnumerator.enumerateMayhem` surfaces a `CastWithMayhem` (`AlternativeCostType.MAYHEM`) for a graveyard card
  gated on the "you discarded this card this turn" tracker (`Conditions.YouDiscardedThisCardThisTurn`, backed by
  `CardsDiscardedThisTurnComponent`); `CastZoneResolver.hasMayhemPermission` enforces the same gate in `CastSpellHandler`.
  A resolving spell carries the durable "mayhem cost was paid" fact — `ChoiceSlot.MAYHEM_CAST` on a permanent, the resolution
  context otherwise — read via `Conditions.MayhemCostWasPaid` (e.g. *Sandman's Quicksand*'s opponents-only rider). Pass `""`
  for the CR 702.187c no-cost land form. Printed or granted per-entity, resolved through `MayhemGrants.effectiveMayhem`.
- `Disturb(cost)` — `card { disturb("{cost}") }` builder helper (CR 702.146, Innistrad: Midnight Hunt / Crimson Vow).
  A **graveyard** alternative cost printed on the **front** face of a transforming double-faced card:
  *"You may cast this card transformed from your graveyard by paying [cost] rather than its mana cost."* The resulting
  spell goes on the stack **back face up**, so per CR 712.8c it has **only the back face's characteristics** — its card
  types decide the timing, its `targetRequirements` / `auraTarget` decide what is chosen as the spell is cast (the
  Innistrad cycle has both creature and Aura back faces), and its name/colors/P/T are the back face's. Its **mana value
  still comes from the front face's mana cost**. Grants no timing permission of its own. Unlike `Flashback`/`Harmonize`
  the card is **not exiled on resolution**; every printed disturb back face instead carries its own
  `RedirectZoneChange(EXILE, to = GRAVEYARD, selfOnly = true)` — *"if this would be put into a graveyard from anywhere,
  exile it instead"* — which functions in every zone (CR 614.12), so a **countered** disturb spell is exiled too and the
  card can never be disturbed twice. All behavior is in the engine, keyed off the `KeywordAbility.Disturb` entry:
  `DisturbCasts.castFace` resolves the printed keyword plus a permanent back face into the face being cast;
  `CastFromZoneEnumerator.enumerateDisturb` surfaces a `CastWithDisturb` (`AlternativeCostType.DISTURB`) labelled with the
  back face's name; `CastZoneResolver.disturbCastFace` re-derives it authoritatively in `CastSpellHandler`; and
  `StackResolver.castSpell(castTransformed = true)` flips the card — swapping its `CardComponent`, stamping a
  `DoubleFacedComponent` on the back face, and re-registering the back face's statics, replacements and self-redirects —
  before it becomes a spell, so resolution, targeting and the client view need no special case. Rule 712.8a still turns
  the card back over as it leaves the battlefield or stack for any other zone.
- `Splice(cost, onto = Subtype.ARCANE)` — `card { splice("{2}{R}{R}") }` builder helper (CR 702.47, Champions of
  Kamigawa). A static ability functioning while the card is **in hand**: *"You may reveal this card from your hand as you
  cast a [quality] spell. If you do, that spell gains the text of this card's rules text and you pay [cost] as an
  additional cost to cast that spell."* Every printed splice card reads "splice onto Arcane", hence the `onto` default;
  it is matched against the spell's **subtypes**. Unique among the cost-carrying keywords in that **the card never moves**
  — it is only *revealed*, so it stays castable later, splice-able onto a later spell, and (per the CR's own example) can
  even be discarded to pay a discard cost of the very spell it was spliced onto.
  **The card needs no extra authoring**: its ordinary `spell { }` script *is* the text that gets spliced, targets
  included. All behavior is in the engine, keyed off the `KeywordAbility.Splice` entry and centralized in
  `SpliceCasts` (candidates in hand, quality match, cost addition, target-requirement tail):
  `CastSpellEnumerator.enumerateSplice` surfaces one `CastWithSplice` per (eligible spell, splice card in hand) pair,
  priced at the spell's cost **plus** the splice cost (an *additional* cost — CR 601.2b/f–h, so it survives a free cast or
  an alternative cost) and target-checked against the union of both cards' requirements, which is how CR 702.47b's
  *"you can't choose to use a splice ability if you can't make the required choices"* is enforced; `CastSpellHandler`
  re-validates every leg (card in **hand**, has splice, quality matches, no card spliced twice), appends the spliced
  cards' `targetRequirements` after the main spell's, and reveals them via `CardsRevealedEvent`; `StackResolver` records
  the choice on `SpellOnStackComponent.splicedCardNames` / `splicedTargetsOrdered` and, at resolution, runs the main
  spell's effect first and then each spliced card's as a `PreTargetedEffectEntry` queue (CR 702.47b) — the same
  `processPreTargetedEffectQueue` drain a choose-N modal spell uses, so each spliced card resolves against **its own**
  target slice and its `ContextTarget(0)` means its own first target.
  Two consequences worth knowing: the spliced effect's `sourceId` is the **spell**, never the spliced card, so the spell
  keeps all its own characteristics (CR 702.47c) and a red splice card's damage on a blue Arcane spell can still be dealt
  to a creature with protection from red; and because the whole choice rides the stack object, *"the spell loses any
  splice changes once it leaves the stack"* (CR 702.47e) needs no cleanup code. The engine handles **arbitrarily many**
  spliced cards (`CastSpell.splicedCardIds` is an ordered list), but the enumerator deliberately surfaces one splice card
  per action — every subset would be exponential. *Through the Breach* (CHK, reprinted in INR).
- `Madness(cost)` — `card { madness("{cost}") }` builder helper (CR 702.35). One keyword, **two abilities**
  (CR 702.35a): a *static* one functioning in **hand** — *"if a player would discard this card, that player discards it,
  but exiles it instead of putting it into their graveyard"* — and a *triggered* one functioning on that exile —
  *"when this card is exiled this way, its owner may cast it by paying [cost] rather than paying its mana cost. If that
  player doesn't, they put this card into their graveyard."* Both live in the engine, keyed off the
  `KeywordAbility.Madness` entry. The static half is a card-intrinsic zone-change replacement in
  `ZoneMovementUtils.checkZoneChangeRedirect` (`ZoneMovementUtils.madnessDiscardExile`), which is why it holds for
  **every** discard route — an opponent's Mind Rot, a cost payment, cycling, the CR 514.1 cleanup-step hand-size
  discard. The card is still *discarded*, so "whenever you discard" payoffs and `CardsDiscardedThisTurnComponent`
  still see it. As it lands in exile it is stamped `MadnessExiledComponent` plus a
  `PlayWithFixedAlternativeManaCostComponent` carrying the madness cost, and `ZoneTransitionService.moveToZone` emits
  `CardExiledWithMadnessEvent`; `TriggerDetector.detectMadnessCastTriggers` turns that into `Madness.castAbility`, an
  **owner-controlled** synthesized trigger (`activeZone = EXILE`) composing `MayEffect(GatherCardsEffect(CardSource.Self)
  → CastFromCollectionWithoutPayingCostEffect(payManaCost = true))` with a trailing
  `MoveToZoneEffect(Self, GRAVEYARD, fromZone = EXILE)`. That `fromZone` gate is the whole "if that player doesn't"
  clause: it is a no-op when the card is on the stack (cast) and puts it in the graveyard when it isn't (declined, or
  the cost couldn't be paid). Because the cast happens while the trigger resolves, **timing restrictions don't apply** —
  a discarded madness *sorcery* can be cast on an opponent's turn (CR 702.35b). Both markers are stripped the moment the
  card leaves exile, so a lingering fixed cost can never re-price a later graveyard cast.
  *Fiery Temper*, *Gisa's Bidding*, *Bloodmad Vampire*.
  - **Granting madness** — `GrantMadnessToOwnedCards(filter)` is the static half of Falkenrath Gorger:
    *"Each Vampire creature card you own that isn't on the battlefield has madness. The madness cost is equal to its
    mana cost."* It carries no cost field — "equal to its mana cost" is the only printed shape, so the cost is derived
    per card. `StaticAbilityHandler` bakes it into a `GrantsMadnessToOwnedCardsComponent` on the permanent (the discard
    replacement walks the battlefield with no `CardRegistry` in hand), and `MadnessGrants.effectiveMadnessCost` is the
    single source of truth `ZoneMovementUtils.madnessDiscardExile` consults: printed `MadnessComponent` wins, else the
    first matching grant controlled by the card's **owner**. Everything downstream is shared with printed madness —
    same exile redirect, same stamped `PlayWithFixedAlternativeManaCostComponent`, same CR 702.35a cast offer (which
    reads its cost off that stamp, so it works for both sources and survives the granter leaving the battlefield).
    **Known simplification:** CR 616.1 lets the player choose among applicable replacements, so a discarded Vampire
    with *printed* madness should be offered the choice of which madness exiles it; we take the printed cost without
    asking, which is the cheaper one for every card in the pool.
- `Suspend` (CR 702.62) — an **exile-zone** mechanic, unlike Impending/Vanishing which live on the battlefield.
  A suspended card sits in exile with **time counters**; at the beginning of its **owner's** upkeep one is removed,
  and when the last is gone its owner **may play it for free**, with **haste** if it's a creature. The lifecycle is
  **component-driven**, not definition-driven: the engine grants `Suspend.countdownAbility` (a synthesized
  `activeZone = EXILE` upkeep trigger — remove a counter, then a `MayEffect` that gathers the card via
  `CardSource.Self` and casts it with `CastFromCollectionWithoutPayingCostEffect`) to **any** exiled card carrying the
  `SuspendedComponent` marker. So an arbitrary card with no printed suspend can be suspended.
  - **Putting a card into suspend** is a chain you compose; `Effects.Suspend(target, timeCounters)` is the reusable
    two-step tail (`AddCounters(TIME, n)` + `GrantSuspendEffect` — the latter sets the marker **and** arms a dormant
    haste effect on the card with duration `WhileControlledByController`, so the haste ends the moment the player who
    played it loses control of the permanent — CR 702.62g). The caller supplies the exile step first, because it differs by source zone:
    a spell on the stack uses `CounterSpellToExile` / `CounterEffect(counterDestination = Exile())` (it can't be lifted
    off the stack with a zone-move).
  - **Taigam, Master Opportunist** is the first user: `Composite(CopyTargetSpell(TriggeringEntity),
    CounterEffect(TriggeringEntity → Exile), Suspend(TriggeringEntity, 4))`.
  - **A printed `Suspend N—[cost]`** (`KeywordAbility.Suspend(cost, timeCounters)`, built via
    `KeywordAbility.suspend("{U}", 4)`) is a **different code path from every other alternative-cost keyword**
    (Flashback/Warp/Mayhem/Plot/Foretell): CR 702.62a is explicit that exiling this way "doesn't use the stack" —
    it's a **special action** (CR 116.2f), not a cast, so it never touches `CastSpellHandler`/`AlternativeCostType`.
    Modelled with the same shape as Plot/Foretell (`SuspendCardFromHand` action, `SuspendCardFromHandHandler`,
    `SuspendEnumerator`): pay the printed cost, move hand → exile, put N time counters on it, then stamp
    `SuspendedComponent` + the dormant haste effect exactly like `GrantSuspendExecutor` — handing off into the same
    `Suspend.countdownAbility` machinery a granted suspend uses. Legal any time the player could *begin* to cast the
    card (instant speed for an instant/flash, sorcery speed otherwise — `SuspendEnumerator` checks this, ignoring
    whether the card's own mana cost could ever actually be paid, per CR 702.62c). **Ancestral Vision** (Time
    Spiral) is the canonical user — a Sorcery with **no printed mana cost**, playable only through Suspend.
  - **`CardDefinition.hasNoManaCost`** (set by the `card { }` DSL exactly when its `manaCost` string is blank, for
    non-land cards) is CR 202.1b/118.6's "no mana cost is an unpayable cost — can't be cast normally, only through
    an alternative cost or a free-cast effect" gate, read by `CastSpellEnumerator` (never offers a normal `CastSpell`
    legal action) and `CastSpellHandler` (rejects one anyway, defense in depth). It is **not** derived from
    `manaCost == ManaCost.ZERO` — a printed `{0}` parses to a non-empty one-symbol cost and stays normally castable,
    and plenty of engine test fixtures construct `ManaCost.ZERO` directly as shorthand for "free," which must not
    trip this gate.
- `selfShuffleIntoLibrary()` — `spell { effect = …; selfShuffleIntoLibrary() }` for a card that prints
  "Shuffle <card name> into its owner's library." (the Mirrodin Besieged Zenith cycle: Green Sun's Zenith,
  Blue/White/Black/Red Sun's Zenith). Sets `CardScript.selfShuffleIntoLibraryOnResolve`, the sibling of
  `selfExileOnResolve`: both replace the destination of **CR 608.2n** ("as the final part of an instant or
  sorcery spell's resolution, the spell is put into its owner's graveyard"), and `StackResolver` reads them
  at the same seam on both the full-resolve and paused-resolve paths. The card is added to its owner's
  library and the library is then shuffled, emitting `LibraryShuffledEvent` — the same tail the Omen face
  uses. A card prints one clause or the other, and setting both is **rejected at card-construction time**
  (in the DSL, with a message naming the card, and again in `CardScript`'s own `init` for scripts built
  directly). Deliberately **not** a zone-change replacement (`RedirectZoneChange` is that, and applies to any
  card heading to a graveyard from anywhere), and **not** `AfterResolveDestination.BOTTOM_OF_LIBRARY` (which
  also lands in `Zone.LIBRARY` but does not shuffle). It is also **not** the cast-this-way rider
  `AfterResolveDestination` — and it *outranks* one: those riders read "if that spell would be put into a
  graveyard, [somewhere] instead" (Kylox's Voltstrider), and a spell that shuffles itself in never would be,
  so the rider has nothing to replace. On the countered and fizzled paths, where the card really is put into
  a graveyard, the rider still wins. Because it is read at resolution-destination
  time it is correctly inert when the spell is countered or fizzles — those paths never reach CR 608.2n, so
  the card goes to its owner's graveyard as usual.
  **It does not outrank flashback (CR 702.34a) or harmonize (CR 702.180a)**, printed or granted. Every other
  clause at this seam — the rider, rebound, Adventure, Omen — is worded "instead of putting it into its
  owner's *graveyard*", which is why the printed clause beats them; those two are worded "exile this card
  instead of putting it *anywhere else* any time it would leave the stack", which covers the library move,
  so they still apply. A Blue Sun's Zenith flashbacked off Snapcaster Mage is exiled, not shuffled in.
- `Paradigm` (Secrets of Strixhaven) — `spell { effect = …; paradigm() }` on a Lesson spell. An **exile-zone
  recurrence** mechanic, modelled exactly like Suspend (a marker the engine reads off an exiled card), differing
  only in that it casts a **copy** rather than the card itself, so the original recurs forever. Oracle: "[effect]
  Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile
  without paying its mana cost at the beginning of each of your first main phases." `paradigm()` implies
  `selfExile()`: the spell exiles itself on resolution (reusing the `selfExileOnResolve` → `StackResolver` exile
  path) and is tagged with the `ParadigmComponent` marker as it lands in exile; the `Keyword.PARADIGM` display
  keyword is added automatically. The engine then grants `Paradigm.recastAbility` — a synthesized
  `activeZone = EXILE`, `StepEvent(PRECOMBAT_MAIN, You)` trigger whose `MayEffect` copies the card via
  `CopyCardIntoCollectionEffect(Self)` and casts it with `CastFromCollectionWithoutPayingCostEffect` — to **any**
  exiled card carrying the marker (the marker is the gate: a Lesson exiled by some other path never recurs). The
  original stays in exile; each cast copy is a phantom that ceases to exist (CR 707.10a / 112.3b), so there is no
  exponential growth. The `Lesson` spell subtype (`Subtype.LESSON`) is a plain, non-functional subtype (no Learn
  mechanic in the set), but the type line must parse it.
- `Craft(filter, cost)` — `card { craft(filter, cost, materialDescription?, minCount = 1, maxCount = null) }`
  builder helper (CR 702.167, The Lost Caverns of
  Ixalan). On the front face of a transforming DFC: "Craft with [filter] [cost] ([cost], Exile this permanent,
  Exile [filter] you control and/or [filter] cards from your graveyard: Return this card to the battlefield
  transformed under its owner's control. Activate only as a sorcery.)" `minCount`/`maxCount` bound the material
  count: exact-count wordings ("Craft with artifact" = exactly one, "Craft with two creatures" = exactly two)
  pass `maxCount = minCount`; "one or more [filter]s" leaves `maxCount = null`. Composes entirely from existing primitives
  — `AbilityCost.Composite(Mana(cost), AbilityCost.Craft(filter, minCount, maxCount))` (the atomic `Craft` sub-cost handles both the
  self-exile and the materials-exile because CR 702.167a defines them as one paired clause), plus
  `Effects.ReturnSelfFromExileTransformed` as the resolution effect, and `timing = TimingRule.SorcerySpeed`.
  Records the exiled materials on the source's `CraftedFromExiledComponent` so the back face's CDA
  ("Mastercraft Raptor's power is equal to the total power of the exiled cards used to craft it", CR 702.167c)
  can read them via `DynamicAmount.CraftedMaterialsTotalPower`. Declares `Keyword.CRAFT` for display.

  Material selection: the engine surfaces the combined BF + GY candidate pool on each Craft activation as
  `AdditionalCostData.validCraftMaterials` / `craftMinCount` / `craftMaxCount`. The web client renders both zones side-by-side
  via the dedicated `CraftMaterialOverlay` (routed by the `Craft` cost-type branch in `pipelinePhases`) and
  submits the picked IDs back as `ActivateAbility.costPayment.exiledCards`. Headless / game-server callers can
  supply the chosen IDs directly. The cost handler validates that every chosen entity is either a permanent
  the activator controls or a card in their graveyard matching `filter`, and rejects activation when no
  choices are supplied (no silent auto-pick).

- `Renew(cost)` — `card { renew(cost) { effect = … } }` builder helper (Tarkir: Dragonstorm, Sultai clan keyword).
  A graveyard-activated ability: "Renew — [cost], Exile this card from your graveyard: [effect]. Activate only as a
  sorcery." The helper composes it entirely from existing primitives — `AbilityCost.Composite(Mana(cost), ExileSelf)`,
  `activateFromZone = Zone.GRAVEYARD`, and `timing = TimingRule.SorcerySpeed` — so no new engine subsystem is involved.
  The `renew { }` lambda configures the effect (and any targets via `target(name, requirement)`) exactly like
  `activatedAbility { }`; its `cost`/`timing`/`activateFromZone` fields are ignored (fixed by Renew). The
  `GraveyardAbilityEnumerator` surfaces the ability while the card is in the graveyard and only at sorcery speed; the
  `ActivateAbilityHandler` pays the mana and exiles the card from the graveyard. Declares `Keyword.RENEW` for display.
- `Embalm(cost)` — `card { embalm(cost) }` builder helper (CR 702.128, Amonkhet). "[cost], Exile this card from your
  graveyard: Create a token that's a copy of it, except it's a white Zombie in addition to its other types and it has no
  mana cost. Activate only as a sorcery." Like `renew`, composed entirely from existing primitives — the shared
  `embalmAbility(cost)` factory builds `AbilityCost.Composite(Mana(cost), ExileSelf)` +
  `activateFromZone = Zone.GRAVEYARD` + `timing = SorcerySpeed`, whose effect is
  `CreateTokenCopyOfTarget(EffectTarget.Self, overrideColors = {WHITE}, addedSubtypes = {Zombie}, noManaCost = true)` —
  the three printed exceptions, no new engine subsystem. The card is exiled as part of the *cost*, which is why per the
  Cursecloth Wrappings ruling an opponent can't respond by exiling it. Declares `Keyword.EMBALM` for display.
  **Granting embalm at runtime:** `Effects.GrantEmbalm(target, cost?, duration)` hands the very same ability to a
  creature card already in a graveyard (Cursecloth Wrappings — "target creature card in your graveyard gains embalm
  until end of turn. The embalm cost is equal to its mana cost"). Because embalm is an ordinary *activated* ability
  rather than an alternative way to cast, the grant rides the plain `GrantedActivatedAbility` channel — not the
  `GrantedKeywordAbility` record `GrantHarmonize`/`GrantFlashback` need — and `ZoneActivatedAbilityEnumerator` surfaces
  printed **and** granted zone abilities alike.
- `station()` — `card { station() }` builder helper (CR 702.184, Edge of Eternities; Spacecraft and Planet cards).
  Emits the fixed station keyword ability (CR 702.184a): "Tap another untapped creature you control: Put a number of
  charge counters on this permanent equal to the tapped creature's power. Activate only as a sorcery." The ability is
  fully fixed by the rules, so the helper takes no arguments — it builds
  `AbilityCost.TapPermanents(count = 1, filter = Creature, excludeSelf = true)` →
  `Effects.AddDynamicCounters(Counters.CHARGE, DynamicAmount.StationCharge, Self)` at `TimingRule.SorcerySpeed`. The
  charge amount is the dedicated `DynamicAmount.StationCharge` node (see §13), *not* a plain
  `EntityProperty(TappedAsCost, Power)` read, so the CR 702.184c "station using toughness" substitution
  (`StationUsingToughness`, Tapestry Warden) stays scoped to station abilities. What the card gains at each charge
  threshold (the `{N+}` station symbols, CR 721.2a) is authored separately per card — `staticAbility { }` rows for
  Spacecraft that grant `GrantKeyword(...)` / `GrantCardType("CREATURE", …)`, or threshold-gated activated abilities
  for Planets — each gated on `Conditions.SourceCounterCountAtLeast(Counters.CHARGE, N)` (see §12). No dedicated
  `Keyword.STATION`: the layout/symbols are display-only and the ability is the whole mechanic.
  - **Multi-select activation shortcut.** Because the station ability has no chosen targets and its
    effect stacks, the player may station with several creatures in one gesture: the cost-selection
    UI lets them pick 1..N distinct untapped creatures and the engine queues one activation per
    creature on the stack (each taps exactly its creature and charges by *that* creature's power).
    This is a pure UX convenience over activating station repeatedly — the resulting state is
    identical to doing it one creature at a time — and is wired generically, not just for station:
    any single-creature (`count == 1`) `TapPermanents`-cost activated ability with no target
    requirements, a repeat-stacking effect, and no once-/max-per-turn restriction is offered the
    same batch. It rides the existing `ActivateAbility.repeatCount` batch-activation path; the
    server advertises the cap as `AdditionalCostInfo.tapBatchMaxActivations` (the count of legal tap
    targets) and `ActivateAbilityHandler` slices `costPayment.tappedPermanents` one creature per
    activation. Selecting a single creature is the unchanged single-station behaviour. (Saddle/Mount,
    by contrast, already taps any number of creatures within one activation — a different shape —
    so it is unaffected.)
- `Morph(cost)` — cast face-down for `{3}`, flip for cost.
- `Disguise(cost)` — morph plus ward {2} (CR 702.168). Same `{3}` sorcery-speed face-down cast and
  the same turn-face-up special action; the ward is a *characteristic* of the face-down permanent
  (see `FaceDownMode.DISGUISE`), not part of this ability. Takes a full `PayCost` because
  CR 702.168e contemplates X in a disguise cost.
- `Unmorph(cost, effect)` — turn-face-up cost + bonus effect.
- `Equip(cost)` — Equipment attach cost. The `equipAbility(cost, genericCostReduction = …)` DSL
  form optionally reduces the generic portion of the equip cost by a `DynamicAmount` evaluated at
  activation. Reductions that read the chosen equip target (e.g. `DynamicAmounts.targetColorCount()`
  for "costs {1} less to activate for each color of the creature it targets" — Dragonfire Blade)
  resolve against the picked target. The lowering itself — CR 702.6a's "attach to target creature you
  control, sorcery speed" ability — is `ActivatedAbility.equip(cost, quality?, targetFilter?,
  genericCostReduction?, id?)`, which `equipAbility` calls and which Argentum Assay also calls when it
  reads a printed "Equip {1}" line back into a model. Cards keep writing `equipAbility`; the factory
  exists so there is exactly one definition of what that line lowers to, and so the differential gate
  is comparing a shared lowering rather than two copies that agree today. Backed by
  `ActivatedAbility.genericCostReduction`: the
  `ActivateAbilityHandler` locks the per-target reduction in before paying; the legal-action
  enumerator gates affordability on the cheapest reachable cost (largest reduction over the
  currently-legal targets) since the target isn't chosen until activation. The synthesized ability
  carries `ActivatedAbility.isEquipAbility = true`, which the engine keys off for the equip-timing
  and free-first-equip permissions below. For a **non-mana equip cost** ("Equip—Sacrifice a
  creature" — Dissection Tools) the `equipAbility(cost)` helper doesn't apply (it only parses a mana
  cost); author the ability by hand as `activatedAbility { cost = Costs.Sacrifice(...); isEquipAbility
  = true; timing = TimingRule.SorcerySpeed; … }`. The `activatedAbility { }` builder exposes
  `isEquipAbility` so a hand-rolled equip ability still participates in equip-specific rules
  (sorcery-speed default, equip-cost reductions, instant-speed-equip permissions).
  **"Equip [quality]" variants** (CR 702.6c — "Equip Human {1}", "Equip legendary creature {3}",
  "Equip worthy {1}") pass `quality` + `targetFilter` to the same helper rather than being
  hand-rolled: `equipAbility("{1}", quality = "Human", targetFilter =
  TargetFilter.CreatureYouControl.withSubtype(Subtype.HUMAN))`. `quality` supplies the wording only,
  landing in two places: `ActivatedAbility.equipQuality`, which makes the ability *render* as its
  printed line, and the target requirement's id/label ("Human creature you control"), which is the
  targeting prompt and what `LegalActionInfo.targetDescription` carries. `targetFilter` is the rules
  half and must stay controller-scoped, since CR 702.6c allows targeting
  "only a creature that's controlled by the player activating the ability and that has the chosen
  quality". Build it off `TargetFilter.CreatureYouControl` or a `GameObjectFilter` ending in
  `.youControl()`. The quality restricts *targeting* only: per CR 702.6c it "[doesn't] restrict what
  the Equipment may be attached to", so a host that stops matching stays equipped — an Equipment
  unattaches only under CR 704.5n. Mjölnir, Hammer of Thor's "worthy" (a legendary non-Villain that's
  red and/or white) is spelled out as `GameObjectFilter.Creature.legendary().notSubtype(Subtype.VILLAIN)
  .withAnyColor(Color.RED, Color.WHITE).youControl()` — it is one card's defined term, not an SDK
  concept.

  **Menu text.** `ActivatedAbility.describeWithCost` renders any `isEquipAbility` ability as its
  printed keyword line — `"Equip {3}"` (CR 702.6a) or `"Equip Human {1}"` (CR 702.6c) — rather than
  the attach effect's generated `"{1}: Attach this equipment to Human creature you control"`. A
  non-mana equip cost takes the printed em dash instead: `"Equip—Pay 3 life"`. Because the line is
  derived rather than a per-card `descriptionOverride`, the cost stays live: the legal-action
  enumerator re-renders against the *effective* cost, so Éowyn's discount and Forge Anew's free first
  equip show as the `{0}`/`{2}` the player actually pays. **Never give a mana-cost equip ability a
  `descriptionOverride`** — it freezes exactly the number those effects rewrite. Only a non-mana equip
  cost with card-specific naming needs one (Dark Knight's Greatsword: "Chaosbringer — Equip—Pay 3
  life. Activate only once each turn."). `EquipQualityVariantTest` (mtg-sets) pins all three
  invariants catalog-wide, as properties rather than a card list: every `isEquipAbility` ability is
  sorcery-speed and targets a single creature you control, renders as its printed `Equip …` line with
  no frozen cost, and — when its target label is narrower than the plain `"creature you control"` —
  declares the matching `equipQuality`.
- `Fortify(cost)` — Aura-like attach cost on lands.

```kotlin
keywords(Keyword.FLYING, Keyword.VIGILANCE)
keywordAbility(KeywordAbility.Ward(2))
keywordAbilities(KeywordAbility.Protection(Color.BLUE), KeywordAbility.Annihilator(2))
```

---

## 12. Conditions (`Conditions.*`)

**One "entity matches a filter" primitive.** "Does *some entity* match a `GameObjectFilter`" is a
single condition — `EntityMatches(entity: EffectTarget, filter)` — that names *which* entity via the
shared `EffectTarget` vocabulary. It subsumes the four former near-clones; each is now a facade
helper over it:

| Helper | Desugars to |
|---|---|
| `Conditions.SourceMatches(f)` (and every `SourceIs*` / `SourceHas*`) | `EntityMatches(EffectTarget.Self, f)` |
| `Conditions.EnchantedPermanentMatches(f)` | `EntityMatches(EffectTarget.EnchantedPermanent, f)` |
| `Conditions.TargetMatchesFilter(f, i)` | `EntityMatches(EffectTarget.ContextTarget(i), f)` |
| `Conditions.TriggeringSpellMatches(f)` | `EntityMatches(EffectTarget.TriggeringEntity, f)` |
| `Conditions.DiscardedCardMatches(f, i = 0)` | `EntityMatches(EffectTarget.DiscardedAsCost(i), f)` |

The entity role fixes *when* the condition can be answered: `Self` and the enchanted/equipped
attachment roles evaluate in both resolution and static-ability projection; `ContextTarget`,
`TriggeringEntity`, and `DiscardedAsCost` are resolution-only (false under projection). Use `Conditions.EntityMatches`
directly only for a role the helpers don't name (e.g. the equipped creature). It is deliberately
*not* a player check (`TargetIsPlayer`) nor a numeric/tracker check (`Compare`). Any other
`EffectTarget` role is rejected by the `CardLinter` at card load (§21) — the evaluator can't
answer it and would silently return `false`.

**Two anti-patterns to avoid when adding conditions:**

- **Tracker-shaped conditions route through `Compare` + a tracked amount**, not a new condition
  class. "You gained 3+ life this turn" is `Compare(TurnTracking(You, LIFE_GAINED), GTE, 3)`. When
  the tracker the comparison needs doesn't exist yet, add the *tracker enum value* (data) and reach
  for `Compare` — don't mint a bespoke `You…ThisTurn` condition.
- **Set-mechanic conditions are quarantined** in mechanic-named files (next to the mechanic's other
  SDK surface), never added to the general condition files. The `add-feature` checklist asks this
  placement question explicitly.

### Battlefield state

- `YouControl(filter, negate = false, excludeSelf = false)` — you control ≥1 matching permanent.
  Set `excludeSelf = true` for "another …" wording, which excludes the resolving source from the
  search (e.g. Splitskin Doll's "another creature with power 2 or less").
- `YouControlAtLeast(count, filter)` — you control `count` or more matching permanents (the
  filtered-count generalization of `ControlCreaturesAtLeast`/`ControlLandsAtLeast`; e.g.
  `YouControlAtLeast(3, GameObjectFilter.Creature.attacking())` for Stormbeacon Blade).
- `YouControlOtherAtLeast(count, filter)` / `YouControlOtherAtMost(count, filter)` — the same tally
  with the source itself left out, i.e. `AggregateBattlefield(..., excludeSelf = true)`. These are
  the "other" of the slow lands ("enters tapped unless you control two or more **other** lands" →
  `YouControlOtherAtLeast(2, GameObjectFilter.Land)`) and the fast lands ("two or fewer other lands"
  → `YouControlOtherAtMost(2, …)`). **Write these rather than counting the whole group against one
  more:** the two agree only while the source itself matches `filter` and is already on the
  battlefield when the condition is checked — true of a land testing lands, and of nothing the
  signature promises. Self-exclusion is a property of the *count*, not a predicate on the filter,
  which is why it is a separate entry and not a `GameObjectFilter` modifier.
- `ControlCreature` — you control any creature.
- `AnyPlayerControls(filter, negate = false, excludeSelf = false)` — at least one permanent matching
  `filter` is on the battlefield **under anyone's control**; the controller-blind sibling of
  `YouControl` / `OpponentControls` (`Exists(Player.Each, Zone.BATTLEFIELD, …)`). This is what "are
  on the battlefield" means on a card that never says whose. `excludeSelf` supplies the printed
  "other" — City in a Bottle's "whenever one or more **other** nontoken permanents with a name
  originally printed in the Arabian Nights expansion are on the battlefield", where without it the
  artifact's own ARN-printed name would hold its condition true forever. `negate` gives the two
  named shorthands below.
- `NoCreaturesOnBattlefield` — there are no creatures anywhere on the battlefield (either player;
  `AnyPlayerControls(Creature, negate = true)`). Used by Drop of Honey's "when there are no creatures
  on the battlefield, sacrifice this enchantment" state trigger.
- `NoLandsOnBattlefield` — the land sibling of `NoCreaturesOnBattlefield`, same global shape. Used by
  Mana Vortex's "when there are no lands on the battlefield, sacrifice this enchantment" state trigger.
- `EntityNumericProperty.ValueChosenAsEntered` (via `DynamicAmount.EntityProperty(EntityReference.Source, …)`)
  — the number the controller chose as this permanent entered, kept for the permanent's whole life.
  Written by `PayAnyAmountOfLifeAsEntersEffect`, which is the ETB half of **Nameless Race**
  (`replacementEffect(OnEnterRunEffect(PayAnyAmountOfLifeAsEntersEffect(maxAmount)))`), and read back
  by its characteristic-defining power and toughness via `dynamicStats(...)`.
  **Why not a pipeline variable or a counter:** `VariableReference` dies with the resolution that set
  it, and a CDA is consulted during layer projection long afterwards; a counter is visible, removable
  game state, while this is a fixed fact about how the permanent entered. It is stored as its own
  `EnteredWithValueComponent`, not cleared at cleanup, and stripped on a zone change — a permanent
  that leaves and returns chooses afresh (CR 400.7). The effect bounds the choice by the controller's
  life total as well as by `maxAmount`, and a ceiling of 0 records 0 without prompting.
- `CouldNotHaveAttackedThisTurn` (filter builders `couldNotHaveAttackedThisTurn()` /
  `couldHaveAttackedThisTurn()`, and the plain negation `didntAttackThisTurn()`) — the "except for
  creatures that couldn't attack" exemption of **Season of the Witch**
  (`DestroyAll(Creature.untapped().didntAttackThisTurn().couldHaveAttackedThisTurn())`). Covers the
  reasons a creature had no say in staying home. First, **its controller wasn't the one attacking**:
  only the active player declares attackers (CR 508.1a), so every creature an opponent controls is
  exempt and the sweep only ever hits creatures that skipped this turn's Declare Attackers Step. (In
  a shared-team-turns format the whole active team counts — CR 805.10b.) Second, **no Declare
  Attackers Step happened at all** — a distinct question from whose turn it is, and the one that
  keeps an effect skipping the combat phase (False Peace, Fatespinner) from making the sweep punish
  a choice nobody was offered; backed by `AttackersDeclaredThisTurnComponent`, the turn-scoped
  sibling of `AttackersDeclaredThisCombatComponent`, stamped even for an empty declaration. Then the
  per-creature reasons, read from projection where they can be: it is **summoning sick** (entered
  this turn without haste, CR 508.1a), or it is under an attack restriction (CR 508.1c) — it has
  **defender** (CR 702.3b) or it **can't attack** (Pacifism). It is deliberately *not* the full
  declare-attackers legality check — that needs a chosen defending player and a card registry,
  neither of which predicate evaluation has — so a creature kept home only by a card-specific "can't
  attack unless …" restriction is not exempt, and neither is one that came under its controller's
  control this turn without entering the battlefield. Implemented identically in
  `PredicateEvaluator` and `AffectsFilterResolver` so resolution and projection agree.
- `SourceBlockedThisTurn` — this permanent was declared as a blocker at least once **this turn**
  (CR 509.1). The turn-scoped sibling of `SourceBlockedThisCombat`: backed by
  `BlockedThisTurnComponent`, stamped beside the per-combat marker at blocker declaration but
  cleared at cleanup, so it survives into the postcombat main phase and across a second combat in
  the same turn. `SourceAttackedOrBlockedThisTurn` is the pair (the attack half needs no new
  marker — `AttackedThisTurn` already reads the controller's per-turn attacker set).
- `SourceAttackedLastTurn` — this permanent was declared as an attacker during its controller's
  **most recent own turn**. The one-turn-back sibling of `SourceAttackedThisTurn`: false on the turn
  it attacked, true on the next. Backed by `PlayerAttackersLastTurnComponent`, rolled over in the
  cleanup step of that player's *own* turn only, so an intervening opponent's turn can't blank it.
  Gates the untap step for **Goblin Rock Sled** ("doesn't untap during your untap step if it
  attacked during your last turn") as `ConditionalStaticAbility(GrantKeyword(DOESNT_UNTAP,
  GroupFilter.source()), SourceAttackedLastTurn)` — a *conditional* static rather than the bare
  flag, so the permanent untaps normally on a turn it didn't attack. An Aura granting the same
  clause to its host (**Tangle Kelp**) must use
  `EnchantedPermanentMatches(GameObjectFilter.Any.attackedLastTurn())` instead: the Aura is the
  source and an Aura never attacks, so a source-scoped condition would always read false.
  The filter-side helper is `GameObjectFilter.attackedLastTurn()`.
- `ControlMoreCreatures` — you control more creatures than each opponent.
- `OpponentControlsCreature` — at least one opponent has a creature.
- `OpponentControls(filter, negate = false)` — at least one opponent controls a permanent matching
  `filter`; the opponent-side mirror of `YouControl(filter, …)` and the general form of
  `OpponentControlsCreature`. Existential across opponents, so it holds when *any single* opponent
  has a match (Syr Ginger, the Meal Ender's "as long as an opponent controls a planeswalker").
- `OpponentControlsMoreCreatures` — an opponent outpaces you.
- `OpponentControlsMoreLands` — an opponent has more lands.
- `OpponentHasMoreCardsInHand` — an opponent has more cards in hand than you (compares opponents' hand size to yours). Used by Beza, the Bounding Spring and Joined Researchers.
- `DefendingPlayerControlsLandType(type)` — the defending player controls a land of a type (CantAttackUnless template; defender-relative, not any-opponent).
- `CompareAmounts(left, operator, right)` — generic numeric comparison of two `DynamicAmount`s with a
  `ComparisonOperator.{LT,LTE,EQ,NEQ,GT,GTE}` (composes the underlying `Compare` condition). The facade
  entry point for any "if amount X (relation) amount Y" intervening-if or static gate. Used by Taii
  Wakeen, Perfect Shot's intervening-if: `CompareAmounts(ContextProperty(TRIGGER_DAMAGE_AMOUNT), EQ,
  ContextProperty(TRIGGER_RECIPIENT_TOUGHNESS))`.
- `PlayerHasMostLife(player)` — `player` has the most life, or is tied for the most, among all
  players (their life ≥ every player's). The "most life" check a binary `CompareAmounts` can't express
  (it needs the max over every player). Resolve `player` to the controller (`Player.You`), the
  attacked player (`Player.DefendingPlayer` — read from the source's attack assignment, so it works as
  an attack-trigger intervening-if), etc. Preacher of the Schism gates its two attack triggers with
  `PlayerHasMostLife(Player.DefendingPlayer)` ("attacks the player with the most life") and
  `PlayerHasMostLife(Player.You)` ("attacks while you have the most life").
- `PlayerControlsMostPermanents(player, filter = GameObjectFilter.Creature)` — `player` controls the
  most permanents matching `filter`, or is tied for the most, among all players. The board-count
  sibling of `PlayerHasMostLife`: the same "max over every player" shape a binary `CompareAmounts`
  can't express. Counts are read off the **projected** battlefield, so an animated land counts as a
  creature. Wrap it in a per-player loop for "each player who controls the most X" —
  `ForEachPlayerEffect(Player.Each, ConditionalEffect(PlayerControlsMostPermanents(Player.You,
  GameObjectFilter.Creature), …))`, where `Player.You` inside the loop is the iterated player
  (No Witnesses: "Each player who controls the most creatures investigates").
- `AmountIsPrime(amount)` / `AmountIsEven(amount)` / `AmountIsOdd(amount)` /
  `AmountIsMultipleOf(amount, divisor)` — the **unary** numeric-predicate family, the counterpart to
  `CompareAmounts` for properties a two-sided threshold can't express (primality, parity,
  divisibility). Each desugars to `NumberMatches(amount, NumberProperty.{Prime,Even,Odd,MultipleOf})`;
  the arithmetic lives in the engine's `ConditionEvaluator` (the SDK `NumberProperty` is pure data,
  exactly like `ComparisonOperator`). Dual-mode (resolution + projection), so it gates either a
  triggered ability's intervening-if or an "as long as" static. `0` and `1` are not prime; `0` is
  even and a multiple of every nonzero divisor. Used by Zimone, All-Questioning ("if … you control a
  prime number of lands": `AmountIsPrime(AggregateBattlefield(You, Land))`).
- `YouHaveUnspentManaAtLeast(amount)` — true while your mana pool holds at least `amount` unspent
  mana. Desugars to `CompareAmounts(UnspentMana(You), GTE, Fixed(amount))`; dual-mode, so it gates an
  "as long as you have six or more unspent mana" conditional static (Ozai, the Phoenix King).
- `DifferentCounterKindsAtLeast(count, filter = Creature)` — true when `count` or more *different
  kinds* of counters are among permanents you control matching `filter` (default: creatures). A
  +1/+1 and a finality counter is two kinds; the same kind on several permanents counts once.
  Board-derived only, so it gates a `ConditionalStaticAbility` (evaluates identically in resolution
  and projection). Desugars to `Compare(AggregateBattlefield(You, filter, DISTINCT_COUNTER_TYPES),
  GTE, count)`. Used by Hundred-Battle Veteran ("three or more different kinds of counters among
  creatures you control").
- `CounterKindAmongYouControlAtLeast(count, counterType, filter)` — true when the *total* number of
  `counterType` counters (a `CounterTypeFilter`, e.g. `CounterTypeFilter.Named("lore")`, or `.Any`
  to total every kind) among permanents you control matching `filter` is at least `count`. Sums the
  kind across the whole group — three Sagas with one, two, and one lore counter total four.
  Board-derived only (gates a `ConditionalStaticAbility`; evaluates identically in resolution and
  projection). Desugars to `Compare(AggregateBattlefield(You, filter, SUM, counterType = …), GTE,
  count)`. Used by Tom Bombadil ("As long as there are four or more lore counters among Sagas you
  control, … hexproof and indestructible").
- `TriggeringEntityHadCounters` — intervening-if for dies/leaves triggers: true when the triggering
  entity had ≥1 counter of *any* kind on it the moment it left the battlefield (reads the last-known
  total counter count, CR 603.10 / 603.6c). Resolution-only. Pair with `Triggers.YourCreatureDies` +
  `Effects.MoveAllLastKnownCounters` for "whenever this or another creature you control dies, if it
  had counters on it, move its counters" (Host of the Hereafter). Companion to the existing
  `TriggeringEntityHadMinusOneMinusOneCounter` (which checks only -1/-1 counters, e.g. Retched Wretch).
- `TriggeringEntityHadSubtype(subtype)` — intervening-if for dies/leaves triggers: true when the
  triggering entity had `subtype` among its **projected** subtypes the moment it left the battlefield
  (CR 603.10), so continuous-effect-granted types count and not just printed ones. Resolution-only.
  Wrap in `Conditions.Not(...)` for the "if it wasn't a X" wording — Infernal Vessel's
  `interveningIf = Conditions.Not(Conditions.TriggeringEntityHadSubtype(Subtype.DEMON.value))`,
  where the Demon type the card grants itself on return (`Effects.AddCreatureType(..., Duration.Permanent)`)
  is what stops the second death from returning it again. Reads `TriggerContext.lastKnownSubtypes`,
  populated from the `ZoneChangeEvent`'s `EntitySnapshot.subtypes`.
- `TriggeringEntityHadCardType(cardType)` — the card-type sibling of `TriggeringEntityHadSubtype`:
  intervening-if for dies/leaves triggers, true when the triggering entity had `cardType` among its
  **projected** card types the moment it left the battlefield (CR 603.10), so a type set by a
  continuous effect counts and not just the printed line. Resolution-only; pass `CardType.X.name`
  (matched case-insensitively). Tom, Bert, and William's `interveningIf =
  Conditions.TriggeringEntityHadCardType(CardType.CREATURE.name)` is the self-recursion guard for
  "if they were a creature, return them … They're an artifact" — the second death is of the artifact
  they came back as, so the guard fails and the loop stops. Reads
  `TriggerContext.lastKnownCardTypes`, populated from the `ZoneChangeEvent`'s
  `EntitySnapshot.typeLine`.
- `YouWonTheClash` — the "if you won" rider inside a `Triggers.WheneverYouClash` effect (CR 701.30d).
  True when the clash that fired this trigger was won by the ability's controller; false on a tie,
  on revealing nothing from an empty library, and on any trigger a clash did not fire.
  Resolution-only. Reads `TriggerContext.clashWon`, populated from `ClashedEvent.won` — the clash
  has already ended when the ability resolves, so there is no pipeline collection to read and the
  outcome has to travel on the trigger. Entangling Trap ("tap target creature an opponent controls.
  If you won, that creature doesn't untap …") and Rebellion of the Flamekin. See
  [Clash](#clash) for which of the three "if you win" spellings a given wording wants.
- `TargetControlsCreature(target)` — target player has a creature.
- `TargetControlsLand(target)` — target player has a land.
- `TargetMatchesFilter(filter, targetIndex = 0)` — the context target matches a `GameObjectFilter`.
  Resolution-only; backed by `EntityMatches(EffectTarget.ContextTarget(targetIndex), filter)`.
- `TargetIsPlayer(targetIndex = 0)` — the context target is a player (not a permanent/spell/card).
  `TargetMatchesFilter` matches only game objects and returns false for a player target, so this is
  the dedicated check for "any target" effects with a player-only follow-up. Used by Sonic Shrieker
  ("If a player is dealt damage this way, they discard a card"); pair with
  `EffectTarget.ContextTarget(index)` to make that same player the subject of the follow-up.
- `TriggeringPlayerIs(player)` — the player who triggered this ability equals another resolved
  `Player` reference. Narrows a broad "whenever a player …" trigger to one player without a bespoke
  event filter; both sides resolve through the shared player resolver. Shinryu, Transcendent Rival
  gates "When the chosen player loses the game, you win the game" with
  `triggerRestriction = TriggeringPlayerIs(Player.ChosenOpponent)` on `Triggers.AnyPlayerLosesGame`.
- `TargetIsCreatureCard(targetIndex = 0)` — the context target is a creature *card*, tested by the
  underlying card's printed types rather than projected state. Unlike `TargetMatchesFilter(Creature)`
  (which reads projection, where a face-down permanent always projects as a typeless 2/2 Creature),
  this reads the hidden card itself (CR 708.2) — the correct test for "...if it's a creature card"
  over a face-down permanent. Resolution-only. Used by Hauntwoods Shrieker ("Reveal target face-down
  permanent. If it's a creature card, you may turn it face up.").
- `TargetIsSpellOnStack(targetIndex = 0)` — the context target is a **spell on the stack** (a
  `ChosenTarget.Spell`) rather than a permanent. The zone-aware test needed to branch a single "target
  creature **or spell**" target — a creature *spell* on the stack still satisfies a `Creature`
  `GameObjectFilter`, so `TargetMatchesFilter` can't distinguish it. Resolution-only. Used by Aang,
  Swift Savior (the airbend stack branch).
- `TargetIsTapped(targetIndex = 0)` — the context target resolves to a tapped battlefield permanent.
  Non-permanent targets and permanents no longer on the battlefield return false. Branch on a target's
  tapped state at resolution via `ConditionalEffect` — used by Shackle Slinger ("If it's tapped, put a
  stun counter on it. Otherwise, tap it.").
- `TargetIsSource(targetIndex = 0)` — the context target resolves to the ability's own source
  permanent. Wrap in `Conditions.Not(...)` for "another"/"a different permanent" wordings — used by
  Arid Archway ("If another Desert was returned this way, surveil 1": `All(TargetMatchesFilter(Desert
  land), Not(TargetIsSource()))` checks the chosen land *before* the bounce, so returning the Archway
  itself doesn't count).
- `IfTargetTookExcessDamage(targetIndex = 0)` — true post-damage when the target creature's marked
  damage strictly exceeds its (projected) toughness. Chain after `Effects.DealDamage` in a composite
  so the marked-damage update applies before the condition reads it. Used by Orbital Plunge ("If
  excess damage was dealt this way, create a Lander token"). Semantics caveat: the read is
  `marked > toughness` regardless of which preceding step dealt the damage — Composite doesn't
  interleave SBA or fire triggers mid-chain, so for the canonical "deal N, then check" pipeline
  this is equivalent to "did the preceding step deal excess". A chain that deals damage in
  multiple steps within the same composite would see cumulative damage; reach for a different
  condition there. Defensive guards return false for non-creature targets and targets no longer
  on the battlefield (unreachable under `Targets.Creature` + Composite, retained for future
  callers).
- `TargetSharesMostCommonColor(targetIndex = 0)` — the context target shares a color with the
  most common color among all permanents, or a color tied for most common. Tallies each of the
  five colors across every battlefield permanent (multicolored permanents count once per color,
  using projected colors), takes the highest tally, and checks whether the target has any color
  in that (possibly tied) most-common set. A board with no colored permanents is `false`. Used by
  Tsabo's Assassin.
- `ColorIsMostCommon(color)` — the self-gating sibling of the above: true when `color` is the most
  common color among all permanents, or tied for most common (same tally rules). Board-derived
  only — no targets/triggering/kicker — so it evaluates identically in resolution and in
  projection, which lets it gate a `ConditionalStaticAbility`. Used by the Invasion djinn cycle
  ("as long as [color] is the most common color among all permanents…" — Goham/Halam/Ruham/Sulam/Zanam).
- `AnotherPermanentWithSameNameAsTarget(targetIndex = 0)` — true when at least one *other*
  battlefield permanent shares the exact card name of the context target at `targetIndex`. The
  target itself is excluded, so a lone copy never satisfies its own check; tokens compare by name
  like any other permanent. Resolution-only (reads a chosen target). Used by Winnow ("Destroy
  target nonland permanent if another permanent with the same name is on the battlefield").
- `EnchantedPermanentMatches(filter)` — true when the permanent the source Aura is attached to
  matches a `GameObjectFilter` (color, type, etc.), evaluated in projected state via the Aura's
  `AttachedToComponent`. General-purpose counterpart to the narrow `EnchantedCreatureIsLegendary` /
  `EnchantedCreatureHasSubtype` conditions. Backed by
  `EntityMatches(EffectTarget.EnchantedPermanent, filter)`; works as a `ConditionalStaticAbility`
  gate (also in the trigger resolver for conditionally-granted abilities). Used by Essence Leak ("as
  long as enchanted permanent is red or green…", `GameObjectFilter.Permanent.withAnyColor(Color.RED,
  Color.GREEN)`).
- `YouHaveCitysBlessing` — you have City's Blessing (10+ permanents).
- `YouHaveEnduringStory` — you have an enduring story (storied: 3+ artifacts/Sagas/legendaries).
- `SourceIsRingBearer` — the source permanent is your Ring-bearer (CR 701.54e).
- `YouChoseOtherCreatureAsRingBearer` — intervening-if for `Triggers.RingTemptsYou` payoffs that fire
  only when the controller chose a Ring-bearer other than the source (CR 701.54a). True iff the
  controller currently has a Ring-bearer designated AND that bearer isn't the source — so it's false
  both when the source itself was chosen and when the controller had no creature to choose. Used by
  Aragorn (Company Leader), Faramir (Field Commander), Gandalf (Friend of the Shire), and Galadriel
  of Lothlórien.
- `RingHasTemptedYouAtLeast(times)` — the Ring has tempted you `times` or more times this game
  (CR 701.54). Reads the cumulative `temptCount` on your The Ring emblem; a player never tempted
  counts as 0. Works at resolution and as an intervening-if. Used by Frodo, Sauron's Bane's granted
  Rogue ability ("that player loses the game if the Ring has tempted you four or more times this
  game"). Backed by `RingHasTemptedPlayerAtLeast(times, Player.You)`.

### Life & damage

- `LifeAtLeast(n, player?)` — player has ≥N life.
- `LifeAtMost(n, player?)` — player has ≤N life.
- `LifeAboveStartingBy(n)` — your life total is at least N greater than your **starting** life total,
  reading the seat's real starting total (20 / 30 / 40 / 2HG) rather than a hardcoded 20. `n = 1` is the
  plain "greater than your starting life total" reading. Elenda, Saint of Dusk gates her two stat tiers on
  `LifeAboveStartingBy(1)` and `LifeAboveStartingBy(10)`.
- `APlayerLifeAtMost(n)` — *some* player in the game has ≤N life (existential over `state.turnOrder`; distinct from `LifeAtMost`, which is `Player.You`). Used by enters-tapped-unless lands like Razortrap Gorge.
- `EachPlayerLifeAtMost(n)` — every player in the game has ≤N life (universal over `state.turnOrder`). Used by Cryptolith Fragment's intervening-if upkeep trigger.
- `AnOpponentLifeAtMost(n)` — at least one opponent of the ability's controller has ≤N life. Unlike
  `APlayerLifeAtMost`, the controller's own life total never satisfies it; this is the conditional
  static-ability gate for Bloodghast's haste.
- `YouLostLife` — you lost life this turn.
- `OpponentLostLife` — an opponent lost life this turn.
- `PlayerLostLifeThisTurn(player)` — a specific player lost life this turn. Use when the wording
  binds the check to a particular player rather than "an opponent" — Thought-Stalker Warlock's
  "choose target opponent. If THEY lost life this turn, …" is
  `PlayerLostLifeThisTurn(Player.ContextPlayer(0))` (the chosen target, not any opponent).
- `YouGainedLifeThisTurn` — you gained ≥1 life this turn (intervening-if / static gate; backed by the
  `LIFE_GAINED` turn tracker). Used by Ulna Alley Shopkeep's Infusion buff.
- `YouGainedLifeThisTurnAtLeast(n)` — you gained ≥`n` life this turn. The threshold form of
  `YouGainedLifeThisTurn` (`Compare(TurnTracking(You, LIFE_GAINED), GTE, n)`). Used by Scheming
  Silvertongue's "if you gained 2 or more life this turn" prepared trigger.
- `YouDiscardedACardThisTurn` — you discarded ≥1 card this turn (`Compare(TurnTracking(You,
  CARDS_DISCARDED), GTE, 1)`, the same per-player record `DynamicAmounts.cardsDiscardedThisTurn()`
  counts). Used by Ragged Recluse's end-step flip. Counts **cards**, not discard events — one discard
  of two cards satisfies it exactly as two discards of one do. Not `YouDiscardedThisCardThisTurn`,
  which is Mayhem's per-*card* question and reads a different record.
- `PutCounterOnCreatureThisTurn` — you put ≥1 counter of *any* kind on a creature this turn (Lasting
  Tarfire), read through the `COUNTERS_PUT_ON_CREATURE` turn tracker.
- `PutCounterKindOnCreatureThisTurn(counterType, player = Player.You)` — the **kind-scoped** reading
  of the same per-player record: "as long as you've put one or more +1/+1 counters on a creature this
  turn" (Sigardian Paladin). This is turn *history*, not a board scan — the counter may be gone, the
  creature may have left the battlefield, and it may have stopped being a creature, and all three
  still count (Sigardian Paladin's first ruling). For the per-*permanent* wording ("on **~** this
  turn" — Kid Loki, Beast, Erudite Aerialist) use `StatePredicate.ReceivedCounterThisTurn` on a
  filter instead; that one names a single permanent, this one means any creature.
- `CardsPutIntoExileThisTurn(atLeast = 1)` — `atLeast` or more cards were put into exile this turn,
  game-wide (summed across every player via `Player.Each`, backed by the `CARDS_PUT_INTO_EXILE`
  turn tracker), not just yours. Used by Ennis, Debate Moderator's end-step "if one or more cards
  were put into exile this turn".

### Cast / cost

- `WasCast` — source was cast (not put onto the stack).
- `TriggeringEntityWasCast` — the *triggering* entity (not the ability's source) was cast — i.e. it
  carries a cast-origin marker (`CastFromHandComponent` / `CastFromGraveyardComponent`). The
  cast-subject sibling of `WasCast`, for "whenever a creature you control enters, **if you cast it**,
  …" intervening-if triggers where the source is a separate permanent and the cast subject is the
  entering creature. Tokens, reanimated permanents, and "put onto the battlefield" permanents lack
  the markers and are correctly excluded. Used by **The Sibsig Ceremony** (a plain `WasCast` there
  would test the enchantment, not the entering creature). Resolution-only.
- `WasCastFromHand` — cast specifically from hand.
- `WasCastFromZone(zone)` — cast from a specific zone. For resolving spells it reads the spell's
  cast-origin; for a permanent already on the battlefield it falls back to the cast-origin marker
  stamped as it entered (`HAND` → `CastFromHandComponent`, `GRAVEYARD` → `CastFromGraveyardComponent`,
  `LIBRARY` → `CastFromLibraryComponent`), so it can gate an entering permanent's own replacement
  effect (Hundred-Battle Veteran: "enters with a finality counter if cast from your graveyard") or a
  *non-self* `EntersWithCounters` (`selfOnly = false`), whose condition is evaluated against the
  entering creature — Leonardo, Sewer Samurai ("creatures you cast from your graveyard enter with a
  finality counter") and Mikey & Don ("creatures you cast from the top of your library enter with an
  extra +1/+1 counter", `WasCastFromZone(Zone.LIBRARY)`).
- `SourceInZone(vararg zones)` — where the source object is **right now**. A live zone-membership
  lookup, so unlike `WasCastFromZone` (frozen at cast time) it answers differently once the source
  moves; it reads identically at resolution and under projection. Its job is CR 603.4's
  resolution-time re-check for an ability whose printed intervening-"if" is a zone test — *eminence*
  is `SourceInZone(Zone.BATTLEFIELD, Zone.COMMAND)` gating the effect, so an Edgar Markov killed
  after the trigger fires makes no token. See §8's `triggerZones`.
- `WasKicked` — cast with kicker / multikicker / offspring (i.e. an `OptionalAdditionalCost` with `branchesEffect = true` whose extra cost was paid). FlashKicker payments are intentionally invisible to this condition.
- `WasBargained` — the spell's **bargain** additional cost was declared as it was cast (CR 702.166b, Wilds of Eldraine). A facade over `CastChoiceMade(ChoiceSlot.BARGAINED)`, so bargain needs no condition type of its own. Reads the durable flag on a resolved permanent (an "if it was bargained" enters trigger) *and* the declaration carried on a still-on-the-stack spell (an "if this spell was bargained" rider), and is also the condition a `CostGating.OnlyIf` cost reduction gates on. Never true for a merely kicked spell.
- `TeamworkWasPaid` — the spell was cast **using teamwork** (CR 702.194b, Marvel Super Heroes): its optional "tap any number of creatures you control with total power N or more" additional cost was declared as it was cast. A facade over `CastChoiceMade(ChoiceSlot.TEAMWORK)`, so teamwork needs no condition type of its own. Reads the declaration carried on a still-on-the-stack spell (an "if this spell was cast using teamwork" rider) *and* the durable flag on a resolved permanent, and is the condition `teamworkModal { }` gates both ends of the mode count on for "choose one; if cast using teamwork, choose both instead". Never true for a merely kicked or bargained spell.
- `SneakCostWasPaid` — the source was cast for its `Sneak` cost (CR 702.190 — mana + returning an unblocked attacker). Reads the durable `ChoiceSlot.SNEAK` flag on a resolved permanent, falling back to the resolution context for a non-permanent spell's own effect. Backs riders like Leonardo, Leader in Blue and The Last Ronin's Technique.
- `WebSlungCostWasPaid` — the source was cast using web-slinging (CR 702.188 — mana + returning a tapped creature you control). Reads the durable `ChoiceSlot.WEB_SLUNG` flag on a resolved permanent, falling back to the resolution context for a non-permanent spell's own effect. Backs riders like *Spiders-Man, Heroic Horde* and *Scarlet Spider, Ben Reilly*; the latter also reads the returned creature's mana value via `DynamicAmount.CastChoice(ChoiceSlot.WEB_SLUNG_RETURNED_MV)`.
- `MayhemCostWasPaid` — the source was cast from the graveyard for its Mayhem cost (CR 702.187). Reads the durable `ChoiceSlot.MAYHEM_CAST` flag on a resolved permanent, falling back to the resolution context (`wasMayhem`) for a non-permanent spell's own effect. Backs riders like *Sandman's Quicksand* ("if this spell's mayhem cost was paid, creatures your opponents control get -2/-2 instead").
- `YouDiscardedThisCardThisTurn` — the source card in a graveyard was discarded by its owner this turn (CR 702.187b). Reads the per-player `CardsDiscardedThisTurnComponent` id list (entity ids are stable across the hand→graveyard move). Engine-internal — the Mayhem enumerator and cast-permission check wire it up; cards don't reference it directly.
- `GiftWasPromised` — the spell's **gift** additional cost was paid, i.e. "if the gift was promised"
  (CR 702.174a/b, Bloomburrow). A facade over `CastChoiceMade(ChoiceSlot.GIFT_PROMISED)` — the flag the
  engine stamps (together with the promised opponent in `ChoiceSlot.OPPONENT`) on a permanent whose
  gift was promised while casting. Gate a permanent's printed enters-abilities with it (Scrapshooter's
  destroy, Starforged Sword's attach) and with `Conditions.Not(GiftWasPromised)` for the "if the gift
  wasn't promised" riders (Kitnap's stun counters). See `gift(kind)` in § 11.
- `WaterbendWasPaid` — the spell's optional spell-level **waterbend** additional cost (`waterbendCost(..., optional = true)`, Avatar: The Last Airbender) was paid. Reads the durable `ChoiceSlot.WATERBEND_PAID` flag on a resolved permanent, falling back to the resolution context for a non-permanent spell's own effect. The waterbend analogue of `BlightWasPaid`; backs "if this spell's additional cost was paid" on Ruinous Waterbending and Spirit Water Revival.
- `NoManaSpentToCast` — "it wasn't cast or no mana was spent to cast it": the standard free-cast
  payoff clause (**Freestrider Commando**, **Satoru, the Infiltrator**). True when the *total* mana
  spent to put the source onto the battlefield was zero — it was put onto the battlefield without
  being cast (reanimation, token, "put onto the battlefield"), **or** it was cast for free / for
  `{0}` (e.g. a plotted card cast from exile). False if any mana was spent, including mana paid for
  additional costs or cost increases on an otherwise-free cast (per the Freestrider Commando ruling:
  a plotted spell taxed by Aven Interrupter had mana spent, so it does not qualify). Reads the
  source's cast-mana record (`CastRecordComponent`), which the engine only stamps when mana > 0 was
  spent, so its absence (or a zero total) is exactly "no mana was spent." This single condition
  covers the whole oracle clause; compose `All(WasCast, NoManaSpentToCast)` for the narrower
  "cast, but for free" sense that excludes uncast permanents. Pairs naturally with the conditional
  `EntersWithCounters(..., condition = Conditions.NoManaSpentToCast)`. Resolution-only.
- `ManaSpentToCastIncludes(requiredWhite, requiredBlue, requiredBlack, requiredRed, requiredGreen)` —
  "if {U} was spent to cast this spell" / "if {R}{R} was spent to cast it": each pip named must have
  been among the mana actually paid. A *payment* question, not a colour requirement — casting a
  mono-black **Ribbons of Night** off a Dimir land turns its blue rider on. Reads through
  `ManaSpentReader`, so it answers for a **spell still on the stack** (the Ravnica "if {X} was spent"
  riders, where the spell's own resolution asks the question) as well as for a permanent that has
  already resolved (the Lorwyn Incarnation cycle's ETB `interveningIf`). A copy of a spell was never
  cast and so had no mana spent for it — it always reads false, which is the printed ruling.
  Resolution-only.
- `NoManaSpentToCastEntered` — the batch-enters variant of `NoManaSpentToCast`: "if none of them were
  cast or no mana was spent to cast them." Evaluated at resolution over the permanents a batch-enters
  trigger captured (the `Triggers.OneOrMorePermanentsEnter` batch, exposed as the `trigger.captured`
  pipeline collection), it's true iff **every** captured permanent had no mana spent to cast it (an empty
  capture is vacuously true). Use as a resolution-time `ConditionalEffect` gate on the payoff — **Satoru,
  the Infiltrator**: `ConditionalEffect(Conditions.NoManaSpentToCastEntered, Effects.DrawCards(1))` under a
  `Triggers.OneOrMorePermanentsEnter(GameObjectFilter.Creature.nontoken())` trigger. Resolution-only.
- `AnyEnteredOrWasCastFromExile` — the batch-enters *any*-of exile condition: "if one or more of them
  entered from exile or was cast from exile." The exile twin of
  `TriggeringEntityEnteredOrWasCastFromGraveyard`, evaluated over the same `trigger.captured` collection
  as `NoManaSpentToCastEntered` — note the opposite quantifier (that one is "if **none** of them …", this
  one is "if **one or more** of them …"; an empty capture is false here). Reads the two exile-provenance
  markers the engine now stamps alongside their graveyard siblings: `CastFromExileComponent` (a spell
  that resolved with `castFromZone == EXILE` — impulse draws, plot/foretell, an adventurer's permanent
  half, linked-exile grants) and `EnteredFromExileComponent` (a direct exile → battlefield entry, e.g. a
  blink). **Usable as a real intervening-"if" (`interveningIf`), not just a resolution gate** — batch
  captures are now seeded into the condition context at trigger-detection time as well as at resolution.
  That distinction is load-bearing whenever the ability also carries `oncePerTurn`: CR 603.4 says an
  ability whose intervening-"if" is false never triggers, so it must not consume the turn's single
  firing. **Extraordinary Journey**: `Triggers.OneOrMorePermanentsEnter(Creature.nontoken().anyController())`
  + `interveningIf = Conditions.AnyEnteredOrWasCastFromExile` + `oncePerTurn = true`. The same
  provenance marker also backs `WasCastFromZone(Zone.EXILE)` for a permanent already on the battlefield.
- `TriggeringSpellCastWithoutPayingMana` — triggering-entity counterpart of `NoManaSpentToCast`: "if no
  mana was spent to cast it" about the *triggering* spell (reads its `CastRecordComponent`). Used as a
  triggered-ability intervening-if (Boromir, Warden of the Tower: "Whenever an opponent casts a spell,
  if no mana was spent to cast it, counter that spell" → pair with `Effects.CounterTriggeringSpell()`).
- `TriggeringSpellManaSpentAtLeast(amount)` — threshold counterpart of
  `TriggeringSpellCastWithoutPayingMana`: "if at least `amount` mana was spent to cast it" about the
  *triggering* spell (sums the mana paid recorded on its `SpellOnStackComponent`). Triggered-ability
  intervening-if (Sahagin: "Whenever you cast a noncreature spell, if at least four mana was spent to
  cast it, …"). Counts actual mana paid, so an {X} spell that paid four or more qualifies.
- `BlightWasPaid(amount)` — the Blight X additional cost was paid.

### Source state

All "source matches X" conditions desugar to `Conditions.SourceMatches(filter)`, the facade over
`EntityMatches(EffectTarget.Self, filter)` — a generic predicate check against the source entity
that works in both resolution and static-ability (projection) contexts.

- `SourceMatches(filter)` — source entity matches a `GameObjectFilter`
  (`EntityMatches(EffectTarget.Self, filter)`).
- `SourceIsAttacking` — source is attacking.
- `SourceIsBlocking` — source is blocking.
- `SourceIsBlockingOrBlockedBySubtype(listOf(subtype, …))` — the source is currently in a combat
  pairing with a creature of one of the given subtypes — i.e. it is blocking, or being blocked by,
  at least one creature whose projected subtypes match any of them. "It" is resolved through the
  source: an Equipment/Aura reads its attached creature (so the condition gates a static ability
  granted to the equipped creature); a creature source uses itself. Checks both combat directions
  (`BlockingComponent` + `BlockedComponent`); partner subtypes use projected state. Works under
  static-ability projection, so a conditionally-granted keyword (e.g. first strike) is honored when
  combat damage is assigned. Used by Sting, the Glinting Dagger (LTR): "Equipped creature has first
  strike as long as it's blocking or blocked by a Goblin or Orc."
- `SourceIsTapped` — source is tapped.
- `SourceIsUntapped` — source is untapped.
- `SourceEnteredThisTurn` — source entered the battlefield this turn.
- `SourceIsSaddled` — source is saddled (CR 702.171b). Gates Mount payoffs on "while saddled" /
  "as long as it's saddled"; evaluates identically at resolution and during projection.
- `SourceIsRenowned` — source has the renowned designation (CR 702.112b). The gate behind every
  renown payoff — "as long as this creature is renowned, it has menace" (Goblin Glory Chaser),
  "if it's renowned" (Consul's Lieutenant, Enshrouding Mist). Wrapped in `Conditions.Not(…)` it is
  renown's own intervening-`if`, CR 702.112a's "if it isn't renowned".
- `SourceIsSolved` — source has the solved designation (CR 719.3b). The gate behind every
  "Solved —" ability (CR 702.169); a Case author writes `solvedStaticAbility` /
  `solvedTriggeredAbility` / `solvedActivatedAbility` instead of applying it by hand. Wrapped in
  `Conditions.Not(…)` it is the second half of the "To solve" intervening-if.
- `SourceIsSuspected` — source is suspected (CR 701.60a). Wrap in `Conditions.Not(…)` for the
  "if it's **not** suspected" intervening-if on MKM's self-suspecting attack triggers
  (**Rubblebelt Braggart**) — because a suspected permanent can't become suspected again
  (CR 701.60d), the check is what stops the trigger going on the stack, rather than letting the
  player click through a "may" that could never do anything.
- `SourceAttackedThisTurn` — source was declared as an attacker at least once during the
  current turn (per-creature, derived from the controller's `PlayerAttackersThisTurnComponent`).
  Negate via `Conditions.Not(...)` for Erg Raiders-style "if it didn't attack this turn".
- `SourceAttackedThisCombat` / `SourceBlockedThisCombat` — source was declared as an attacker /
  blocker at least once during the current combat (per-creature `AttackedThisCombatComponent` /
  `BlockedThisCombatComponent`, stamped at declaration time, cleared when the combat phase ends;
  filter helpers `.attackedThisCombat()` / `.blockedThisCombat()`). Unlike the live
  `AttackingComponent`/`BlockingComponent` these survive the creature (or its blocked attacker)
  leaving combat, so they still read true at end of combat; unlike `SourceAttackedThisTurn` they
  reset between multiple combats in one turn.
- `SourceAttackedOrBlockedThisCombat` — `Any(SourceAttackedThisCombat, SourceBlockedThisCombat)`.
  Used as the intervening-if on Clockwork Avian's `EachEndOfCombat` counter-shed trigger.
- `SourceHasDealtDamage` — source has dealt damage since entering the battlefield.
  `SourceMatches(GameObjectFilter.Any.hasDealtDamage())`, i.e. the source-scoped view of
  `StatePredicate.HasDealtDamage()`. For the per-turn window of the same predicate, put
  `.hasDealtDamageThisTurn()` on the filter directly.
- `SourceHasDealtCombatDamageToPlayer` — saboteur-style payoff gate.
- `SourceIsModified` — has counters, attached Equipment, or controller-owned Aura
  attached (CR 700.4). Kept as a dedicated condition because the controller-of-Aura
  match isn't expressible via the generic `EntityMatches` filter machinery.
- `SourceReceivedCounterThisTurn(counterType = null, placedByYou = false)` — "if you put a counter on this creature
  this turn." `SourceMatches(GameObjectFilter.Any.receivedCounterThisTurn(...))`, i.e. the source-scoped view of
  `StatePredicate.ReceivedCounterThisTurn`; the filter-level form covers "each creature you control that you've
  put …" (Kid Loki). True while the source carries the per-turn `ReceivedCountersThisTurnComponent` marker
  (stamped by the counter-placement path, cleared at cleanup). Distinct from `SourceHasCounter` (which checks
  current counters): this fires even if the counter was later removed, and stays false if the source merely
  entered with counters on a *prior* turn. Narrow with `counterType` ("+1/+1 counters") and `placedByYou`
  ("**you've** put"). Used as the end-step intervening-if of Secrets of Strixhaven's Fractal Tender and the
  flying gate of Beast, Erudite Aerialist.
- `SourceHasSubtype(subtype)` — `SourceMatches(GameObjectFilter.Any.withSubtype(...))`;
  Changeling is honored.
- `SourceHasKeyword(keyword)` — `SourceMatches(GameObjectFilter.Any.withKeyword(...))`.
- `SourceHasCounter(counterType)` — `SourceMatches(GameObjectFilter.Any` with the
  corresponding `StatePredicate.HasCounter` / `HasAnyCounter`).
- `SourceCounterCountAtLeast(counterType, count)` — the threshold form of `SourceHasCounter`: the source has `count`+
  counters of `counterType` (a `Compare` on `EntityProperty(Source, CounterCount(filter))`). This is the gate
  behind a Station card's `{N+}` symbol (CR 721.2a — "As long as this permanent has N or more charge counters on it,
  it has [abilities]"): use it as the `condition` of a `staticAbility { }` or inside
  `ActivationRestriction.OnlyIfCondition(...)`, with `Counters.CHARGE`. Generic over counter type, reads counters live.
  Takes either a counter-type name (`Counters.CHARGE`) or a `CounterTypeFilter`; pass `CounterTypeFilter.Any` for
  "N or more counters **of any kind**" gates (Warden of the Inner Sky), which sums every counter kind on the source.
- `SourceCounterCountAtMost(counterType, count)` — the downward-facing twin: a countdown gate rather than a
  threshold (an `LTE` `Compare` on the same `EntityProperty(Source, CounterCount(filter))`). `count = 0` is the
  "**if it has no [kind] counters on it**" clause that follows a remove-a-counter step — Thing in the Ice's
  "remove an ice counter from this creature. Then if it has no ice counters on it, transform it" is
  `Composite(RemoveCounters(Counters.ICE, 1, Self), ConditionalEffect(SourceCounterCountAtMost(Counters.ICE, 0),
  TransformEffect(Self)))`. Reads the source live, so it sees the counter the same resolution just removed. Same
  two overloads (name / `CounterTypeFilter`) as its `AtLeast` sibling.

### Turn / phase

- `IsYourTurn` — it's your turn.
- `IsNotYourTurn` — it's an opponent's turn.
- `IsPlayersTurn(player)` — it's `player`'s turn; the `Player`-parametric form of `IsYourTurn`, for a
  turn check relative to a player other than the controller. Wrap in `Not` for "if it's not their
  turn" where "their" is a non-controller player — Scytheclaw Raptor's "whenever a player casts a
  spell, if it's not their turn" is `Not(IsPlayersTurn(Player.TriggeringPlayer))` (the intervening-if
  carries the casting player as `Player.TriggeringPlayer`).
- `IsInPhase(phase)` — currently in `BEGINNING | MAIN | COMBAT | …`.
- `IsInStep(steps, yoursOnly = true)` — current step is one of `steps` (e.g. `Step.END`). Board-derived
  (reads `state.step` + active player), so it evaluates identically at resolution and under projection,
  making it usable as a `ConditionalStaticAbility` gate. `yoursOnly` requires it to be the controller's
  turn ("during your end step"). Used by Zurgo, Thunder's Decree.
- `IsFirstEndStepOfTurn` — it's the turn's first (natural) end step, i.e. *not* an extra end step
  inserted by `Effects.AddAdditionalEndSteps`. Board-derived (reads `state.step` + the active
  player's "in an inserted end step" marker), so it evaluates identically at resolution and under
  projection. The loop guard for "there is an additional end step after this step" riders: gate the
  `Effects.AddAdditionalEndSteps` call on it so the spawned end step doesn't spawn another (Y'shtola
  Rhul).
- `IsFirstCombatPhaseOfTurn` — the combat analog of `IsFirstEndStepOfTurn`: it's the turn's first
  (natural) combat phase, i.e. *not* an extra combat phase inserted by `Effects.AddCombatPhase`.
  Board-derived (reads `state.phase == COMBAT` + the active player's "in an inserted combat phase"
  marker), so it evaluates identically at resolution and under projection. The intervening-if / loop
  guard for "after this phase, there is an additional combat phase" riders: use it as
  `triggerRestriction` so the spawned combat phase doesn't spawn another (Balthier and Fran; also the
  faithful replacement for the `oncePerTurn = true` approximation on Genji Glove / Raph & Leo).
- `ControllerTurnsTakenAtMost(n)` — the controller has taken at most N turns so far
  (1-indexed once they're partway through their first turn). Reads
  `PlayerTurnsTakenComponent` set by `TurnManager.startTurn`. Used by Starting Town
  ("your first, second, or third turn of the game" → `n = 3`).
- `IsDay` / `IsNight` — the game's day/night designation is day (resp. night) (CR 731). A global fact
  read straight off `GameState.dayNight`; a game that is **neither** day nor night (its starting state,
  CR 731.1) satisfies *neither* condition. Board-derived, so it evaluates identically at resolution and
  under projection — usable both as a resolution-time gate (`ConditionalEffect(Conditions.IsNight, …)`,
  Wolf Strike's "+2/+0 … if it's night") and as a `ConditionalStaticAbility` gate ("as long as it's
  day/night"). Mirror pair; see also the `Effects.BecomeDay` / `Effects.BecomeNight` effects and the
  `daybound()` / `nightbound()` keywords.

### Per-turn counts

All three are parameterised by a `Player` reference (default `Player.You`), so they
work in both resolution and static-ability (projection) contexts. The DSL helpers
default to "you" so card authors don't need to pass it explicitly.

- `YouAttackedWithCreaturesThisTurn(filter, atLeast)` — Raid/Battalion shape. Backed by
  `PlayerAttackedWithCreaturesThisTurn(Player.You, filter, atLeast)`.
- `CreaturesAttackedThisTurn(atLeast, filter = Creature)` — the **player-agnostic** count: at least
  `atLeast` creatures matching `filter` attacked this turn, whoever declared them
  (`PlayerAttackedWithCreaturesThisTurn(Player.Each, …)`, which unions every player's attack record
  so a creature counts once). For printed text that names no player — Case of the Gateway Express's
  "three or more creatures attacked this turn" — rather than the controller-scoped
  `YouAttackedWithCreaturesThisTurn`. The record is keyed to the player who *declared* the attacker,
  so a creature whose controller changed after attacking still counts.
- `PlayerAttackedPlayerThisTurn(attacker, defender = Player.You)` — whether `attacker` "attacked"
  `defender` this turn (CR 508.6): they declared one or more attackers whose defending player was
  `defender` (the player directly, or the controller of a planeswalker / protector of a battle the
  attacker was attacking, CR 508.5). Reads the attacker's per-turn `PlayerAttackedPlayersThisTurnComponent`
  (stamped at declare-attackers, cleared at end of turn). Negate with `Not(...)` for "didn't attack you
  that turn" (Faramir, Prince of Ithilien: at the chosen opponent's next end step, draw if they didn't
  attack you, else make three Human Soldier tokens). `attacker` is typically `Player.TriggeringPlayer`
  (the player a delayed trigger fired on via `CreateDelayedTriggerEffect.fireOnPlayer`).
- `YouCastSpellsThisTurn(atLeast, filter, fromZone?, fromZoneOtherThan?)` — Prowess/Magecraft shape.
  Backed by `PlayerCastSpellsThisTurn(Player.You, filter, atLeast, fromZone, fromZoneOtherThan)`. `fromZone`
  (default any) restricts the count to spells cast from that zone, matched independently of `filter` (a
  face-down/morph spell cast from hand still counts, CR 708.2). With `fromZone = Zone.HAND`, negating gives
  the Prairie Dog cycle's "you haven't cast a spell from your hand this turn":
  `Not(YouCastSpellsThisTurn(1, fromZone = Zone.HAND))` (Inventive Wingsmith, Prairie Dog, Canyon Crab,
  Emergent Haunting, Wrangler of the Damned). `fromZoneOtherThan = Zone.HAND` is the inverse — "cast a spell
  this turn from anywhere **other than** your hand" (Spider-Man 2099). The origin zone is captured on each
  `CastSpellRecord` (`castFromZone`) at cast time, so flashback/forage (GRAVEYARD), plot/foretell (EXILE),
  and commander (COMMAND) casts are all distinguished from hand casts.
  `filter` is matched against the `CastSpellRecord`'s snapshot of the spell's characteristics, so
  only card-level predicates mean anything there (state and controller predicates are skipped, and a
  face-down spell matches nothing but `GameObjectFilter.Any`). **`namedFromVariable(key)` works**:
  the record matcher receives the resolving pipeline's chosen values, so a name captured earlier in
  the same resolution can be matched against cast history — Grim Reminder's "each opponent who cast
  a spell this turn with the same name as that card", where `storeCardName` supplies the key. In a
  static/projection evaluation there is no pipeline and so no captured name, and the predicate
  matches nothing.
- `YouPlayedLandThisTurn(fromZone?, fromZoneOtherThan?)` — "as long as you've **played a land** this turn"
  (CR 305.1 special land-play action), the land mirror of `YouCastSpellsThisTurn`. No qualifier = any land;
  `fromZone` requires that specific origin; `fromZoneOtherThan` excludes it (mutually exclusive). Backed by
  `PlayerPlayedLandThisTurn(Player.You, …)`, reading the per-player `LandsPlayedThisTurnComponent` — the
  zone each land was played from this turn, appended by `PlayLandHandler` (HAND for a normal drop,
  GRAVEYARD/EXILE/LIBRARY for a play permission).
  - `YouPlayedLandFromNonHandThisTurn` — convenience for `YouPlayedLandThisTurn(fromZoneOtherThan = Zone.HAND)`.
    The land half of Spider-Man 2099's end-step intervening-if; compose with
    `YouCastSpellsThisTurn(1, fromZoneOtherThan = Zone.HAND)` via `Any(...)` for the full "played a land or
    cast a spell this turn from anywhere other than your hand".
- `YouDrewCardsThisTurn(atLeast = 1)` — "as long as you've drawn N or more cards this turn".
  Backed by `PlayerDrewCardsThisTurn(Player.You, atLeast)`, which reads the per-player
  `CardsDrawnThisTurnComponent` (reset for all players at turn start). Works in resolution and
  cost-reduction (projection) contexts. Used by Gwaihir the Windlord ("costs {2} less … as long as
  you've drawn two or more cards this turn") via `ModifySpellCost(..., gating = CostGating.OnlyIf(...))`.
- `YouActivatedExhaustAbilitiesThisTurn(atLeast = 1)` / `YouHaventActivatedAnExhaustAbilityThisTurn` —
  "as long as you've activated N or more exhaust abilities this turn" and its negation. Backed by
  `PlayerActivatedExhaustAbilitiesThisTurn(Player.You, atLeast)`, reading the per-player
  `ExhaustAbilitiesActivatedThisTurnComponent` (bumped at activation time, so a countered exhaust ability
  still counts; reset for all players at turn start). Gates Elvish Refueler's
  `ExtraOnceOnlyActivations`.
- `TriggeringSpellMatches(filter)` — intervening-if guard: the spell that triggered this ability
  matches `filter`. Reads the triggering entity's static card characteristics (so it stays correct
  after the spell leaves the stack). General "whenever you cast a spell, if it's a/an X ..." gate.
  Backed by `EntityMatches(EffectTarget.TriggeringEntity, filter)`.
- `DiscardedCardMatches(filter, index = 0)` — the card discarded to pay this spell's additional
  discard cost (`Costs.additional.DiscardCards(...)`) matches `filter`. The discarded card is in its
  owner's graveyard by resolution (CR 608.2), so the filter checks the graveyard card's
  characteristics. Resolution-only; backed by `EntityMatches(EffectTarget.DiscardedAsCost(index),
  filter)`. Wrap in `Not` for "wasn't a [type]" — e.g. Grab the Prize: "if the discarded card wasn't
  a land card, ~ deals 2 damage to each opponent" = `Not(DiscardedCardMatches(GameObjectFilter.Land))`.
- `YouCastFirstSpellOfTypeThisTurn(filter)` — true when the triggering spell is the *first* spell
  matching `filter` you've cast this turn. Pure composition, no bespoke counting:
  `All(TriggeringSpellMatches(filter), Not(YouCastSpellsThisTurn(atLeast = 2, filter)))`. The
  `TriggeringSpellMatches` half is load-bearing — it stops a later non-matching cast from satisfying
  the count once one matching spell exists. Used by Alania, Divergent Storm (first instant / first
  sorcery / first Otter).
- `YouCommittedCrimeThisTurn` — Outlaws of Thunder Junction crime gate: true once you've cast a
  spell, activated an ability, or put a triggered ability on the stack this turn that targets an
  opponent, anything an opponent controls, and/or a card in an opponent's graveyard. Backed by
  `PlayerCommittedCrimeThisTurn(Player.You)`, which reads `GameState.playersWhoCommittedCrimeThisTurn`
  — a turn-scoped set populated at every `CommitCrimeEvent` emit site (crime detection is the engine's
  `CrimeDetector`) and cleared at each turn boundary. Stays true for the rest of the turn even if the
  crime-committing spell/ability is countered. Resolves identically in resolution and projection (e.g.
  cost-reduction) contexts. Pairs with `CostGating.OnlyIf(...)` for "costs {N} less if you've committed
  a crime this turn" (Seize the Secrets).
- `PermanentEnteredFaceDownThisTurn` / `YouTurnedPermanentFaceUpThisTurn` — Duskmourn (Oblivious
  Bookworm) gates: true when a permanent entered the battlefield face down under your control this turn,
  or you turned a permanent face up this turn. Backed by per-player `PermanentEnteredFaceDownThisTurnComponent`
  (stamped in `ZoneTransitionService` on any face-down battlefield entry) and `TurnedPermanentFaceUpThisTurnComponent`
  (stamped in the turn-face-up handler), both cleared at the turn boundary. Compose with `Conditions.Not` /
  `Conditions.Any` for the "unless A or B" rider.
- `YouHaveCitysBlessing` — Ascend gate. Backed by `PlayerHasCitysBlessing(Player.You)`.
- `YouHaveEnduringStory` — storied gate (CR 702.195). Backed by `PlayerHasEnduringStory(Player.You)`.
- `YouHaveMaxSpeed` / `HasMaxSpeed(player)` — "your speed is 4" (CR 702.179e), the gate the
  `maxSpeed { }` block applies. `SpeedBelowMax(player)` is the complement used by the inherent speed
  trigger's intervening-if. All three are plain `Compare` over `DynamicAmount.Speed`, so they need no
  new condition type and evaluate identically at resolution and in projection. Wrap `HasMaxSpeed` in
  `Conditions.Not` for "doesn't have max speed" (Outpace Oblivion, Hazoret, Godseeker).
- `IsFirstSpellPaidWithTreasureManaCastThisTurn` — gates a triggered ability to fire only
  on the first spell each turn that mana from a Treasure was spent to cast (Rain of
  Riches). Reads `CastSpellRecord.paidWithTreasureMana` on the per-player spell history.
- `PermanentTypeEnteredBattlefieldThisTurn(cardType, player = Player.You)` — true if a
  permanent of `cardType` entered the battlefield under `player`'s control at any point
  this turn. Pure ETB tracker: the permanent need not still be on the battlefield, still
  be of that type, or still be under the same controller — only the entry event matters
  (so Mechan Shieldmate's "as long as an artifact entered ... this turn" stays satisfied
  even if the artifact is destroyed before combat). Captured types are read from the
  *projected* state at the moment of entry, so a permanent that's an artifact via a
  continuous effect at ETB (Mycosynth Lattice, etc.) also counts. Backed by the per-player
  `PermanentsEnteredUnderControlThisTurnComponent` entry log, cleared by `CleanupPhaseManager`
  at end of turn. Every battlefield entry must go through `BattlefieldEntry.place` for this
  tracker to stay in sync. Shortcut: `Conditions.ArtifactEnteredBattlefieldThisTurn`.
- `Celebration` / `NonlandPermanentsEnteredThisTurn(atLeast = 1, player = Player.You)` — the
  **Celebration** ability word (Wilds of Eldraine; CR 207.2c — italic flavor with no rules
  meaning, so there is no keyword, only this condition): "if two or more nonland permanents
  entered the battlefield under your control this turn". Composes through
  `Compare(TurnTracking(player, TurnTracker.NONLAND_PERMANENTS_ENTERED), GTE, Fixed(atLeast))`
  over the same per-player entry log as `PermanentTypeEnteredBattlefieldThisTurn`, so it is a
  pure past-event check: the permanents need not still be on the battlefield or still be yours
  (WOE release notes). Tokens count; lands — including land creatures — never do. It's a
  threshold, not a count: a third entry adds nothing. Dual-mode, which is what the mechanic
  needs — the printed cards use it both as an intervening-'if' `interveningIf` (CR 603.4;
  Pests of Honor, Lady of Laughter, Ash, Party Crasher) and as a `ConditionalStaticAbility` gate
  (Armory Mice, Grand Ball Guest, Gallant Pie-Wielder). Use
  `DynamicAmounts.nonlandPermanentsEnteredUnderControlThisTurn(player)` for the raw count.
- `CreaturesEnteredThisTurn(atLeast = 1, player = Player.You)` — the creature-typed counterpart of
  `NonlandPermanentsEnteredThisTurn`: "if two or more creatures entered the battlefield under your
  control this turn" (Spider-UK's end step). Composes through
  `Compare(TurnTracking(player, TurnTracker.CREATURES_ENTERED_UNDER_CONTROL), GTE, Fixed(atLeast))`
  over the same per-player entry log (an entry counts if it was a creature at the moment it
  entered, read from projected state), so it is the same pure past-event check — the creatures need
  not still be on the battlefield, and each entry is counted per entry event (a creature that
  leaves and re-enters counts twice, CR 400.7).
- `YouDescendedThisTurn(atLeast = 1)` — CR 700.11 gate: at least `atLeast` nontoken
  permanent cards were put into your graveyard from *any* zone this turn (battlefield,
  hand, library, stack, exile). Tokens do not count, even though they briefly enter the
  graveyard before ceasing to exist; instants and sorceries do not count. The cards
  themselves need not still be in the graveyard when the gate evaluates — the count is a
  pure event tracker. Composes through `Compare(DynamicAmount.TurnTracking(Player.You,
  TurnTracker.DESCENDED), GTE, Fixed(atLeast))`, so the same plumbing supports the bare
  descend gate (`atLeast = 1`, Ruin-Lurker Bat: "At the beginning of your end step, if
  you descended this turn, scry 1") and the descend N / fathomless descent ability words
  (`atLeast = 4`, `atLeast = 8`). Backed by the per-player
  `PlayerDescendedThisTurnComponent`, incremented in `ZoneTransitionService` whenever a
  permanent (nontoken) card lands in a player's graveyard, and cleared by
  `CleanupPhaseManager` at end of turn.
- `CreatureCardPutIntoYourGraveyardThisTurn(atLeast = 1)` — at least `atLeast` creature cards were put
  into your graveyard from *any* zone this turn (battlefield, hand, library, stack). The creature-typed
  sibling of `YouDescendedThisTurn`, composing through
  `Compare(DynamicAmount.TurnTracking(Player.You, TurnTracker.CREATURE_CARDS_PUT_INTO_GRAVEYARD), GTE,
  Fixed(atLeast))`. Tokens never count (a token isn't a card, CR 111.6), and it's owner-scoped — a
  creature card hitting an *opponent's* graveyard never sets yours. Turn history, not a graveyard scan:
  reanimating the creature later in the turn doesn't clear it. Backed by the per-player
  `CreatureCardsPutIntoGraveyardThisTurnComponent`, incremented in `ZoneTransitionService` alongside the
  descend counter and cleared by `CleanupPhaseManager`. Gates Macabre Reconstruction's cost reduction
  ("This spell costs {2} less to cast if a creature card was put into your graveyard from anywhere this
  turn") via `ModifySpellCost(SelfCast, ReduceGeneric(2), CostGating.OnlyIf(...))`.
- `YouSacrificedPermanentsThisTurn(atLeast = 1)` — at least `atLeast` permanents (any type) were
  sacrificed by you this turn. Composes through `Compare(DynamicAmount.TurnTracking(Player.You,
  TurnTracker.PERMANENTS_SACRIFICED), GTE, Fixed(atLeast))`. Backed by the per-player
  `PermanentsSacrificedThisTurnComponent` (controller-scoped; distinct from the game-wide
  `GameState.permanentsSacrificedThisTurn` cost-reduction counter), incremented in
  `ZoneTransitionService.trackPermanentSacrifice` and cleared by `CleanupPhaseManager`. Pair with the
  `DynamicAmounts.permanentsSacrificedThisTurn()` amount for "that much" damage (Sawblade Skinripper:
  "At the beginning of your end step, if you sacrificed one or more permanents this turn, this creature
  deals that much damage to any target").
- `SacrificedArtifactThisTurn` — you sacrificed at least one artifact this turn. Composes through
  `Compare(DynamicAmount.TurnTracking(Player.You, TurnTracker.ARTIFACT_SACRIFICED), GTE, Fixed(1))`,
  the card-type sibling of `SacrificedFoodThisTurn`. Backed by the per-player marker
  `SacrificedArtifactThisTurnComponent`, set in `ZoneTransitionService.trackPermanentSacrifice` off
  the *projected* type line (an animated artifact counts) and cleared by `CleanupPhaseManager`. Turn
  history, not a graveyard scan — the artifact having since left the graveyard doesn't clear it, and
  it's controller-scoped, so an opponent cracking their own Clue never sets yours. Both MKM readings
  of "if you've sacrificed an artifact this turn" ride it: the cost reduction via
  `ModifySpellCost(SelfCast, ReduceGeneric(3), CostGating.OnlyIf(...))` (Suspicious Detonation) and
  the "as long as" evasion via `ConditionalStaticAbility` (Furtive Courier).
- `DynamicAmounts.cardsDiscardedThisTurn(player)` / `TurnTracker.CARDS_DISCARDED` — the number of cards
  `player` has discarded this turn (CR 701.8). Backed by the per-player `CardsDiscardedThisTurnComponent`
  id list, recorded at **every** discard site (cost, effect, cycling, CR 514.1 hand-size cleanup) via
  `ZoneTransitionService.trackDiscard` and reset to empty for every player at the start of each turn by
  `TurnManager`. Powers "draw a card for each card you've discarded this turn" (Green Goblin, Revenant).
  The same component's membership check backs the Mayhem gate (`Conditions.YouDiscardedThisCardThisTurn`).
- `CreatureCardsPutIntoGraveyardsThisTurn(atLeast = 1)` — the **game-wide** sibling of
  `CreatureCardPutIntoYourGraveyardThisTurn`: at least `atLeast` creature cards reached *any* player's
  graveyard this turn (summed via `Player.Each`). Case of the Gorgon's Kiss's "three or more creature
  cards were put into graveyards from anywhere this turn". Same tracker, so the same two rulings apply:
  it reads the card's own type line (what the card *is in the graveyard*, so a creature card that was a
  noncreature permanent counts and an animated noncreature card doesn't), and tokens never count.
- `SourcesYouControlledDealtDamageThisTurn(atLeast)` — at least `atLeast` **distinct sources** you
  controlled dealt damage this turn (`TurnTracker.DAMAGE_SOURCES`). Case of the Burning Masks. Counts
  source *objects* at the moment they dealt damage, which is what the printed rulings require: a source
  that pings twice counts once; a source that left the battlefield and returned is a new object and
  counts again; a source that dies or changes controller afterwards still counts; and an ability is not
  a source (the source is the object the ability came from).
- `YouDealtRedNoncombatDamageThisTurn(atLeast = 1)` — red sources you controlled dealt at least
  `atLeast` noncombat damage this turn. `Compare(TurnTracking(Player.You, TurnTracker.RED_NONCOMBAT_DAMAGE_DEALT),
  GTE, Fixed(atLeast))`, backed by the per-player `RedNoncombatDamageDealtThisTurnComponent`. Gates
  Temple of Power's transform-back (back of Ojer Axonil, Deepest Might).
- `GraveyardContains(filter)` — "there is at least one card matching `filter` in your graveyard"
  (`Exists(Player.You, Zone.GRAVEYARD, filter)`). Compose with `Conditions.All`/`Any` for multi-type
  checks, e.g. `All(GraveyardContains(Filters.Instant), GraveyardContains(Filters.Sorcery))` =
  "an instant card and a sorcery card in your graveyard" (Flow State). `GraveyardContainsSubtype(subtype)`
  is the subtype-filtered sibling.
- `CardsInGraveyardMatchingAtLeast(count, filter)` — "there are `count` or more cards matching `filter`
  in your graveyard" (`Compare(Count(Player.You, Zone.GRAVEYARD, filter), GTE, count)`). The general
  form behind `CreatureCardsInGraveyardAtLeast(count)`; use for "N or more <kind> cards", e.g. Ran and
  Shaw's "three or more Dragon and/or Lesson cards" with
  `GameObjectFilter.Any.withAnySubtype("Dragon", "Lesson")` (a card matching multiple ways is counted
  once). `CardsInGraveyardAtLeast(count)` is the unfiltered total.
- `Delirium(count = 4)` — the Delirium ability word: "there are `count` or more card types among
  cards in your graveyard." Composes through `Compare(DynamicAmount.AggregateZone(Player.You,
  Zone.GRAVEYARD, GameObjectFilter.Any, Aggregation.DISTINCT_TYPES), GTE, Fixed(count))` — the
  distinct-card-type count over your graveyard (artifact, battle, creature, enchantment, instant,
  land, planeswalker, sorcery); a single card with several types contributes each type once. The
  printed threshold is always four, but `count` is parameterized. Works as a static "as long as …"
  gate (`staticAbility { ability = ModifyStats(...); condition = Conditions.Delirium() }` —
  Spineseeker Centipede) and as an activated-ability `ActivationRestriction.OnlyIfCondition`
  ("Activate only if there are four or more card types …" — Balustrade Wurm).
- `DistinctPermanentTypesInGraveyard(count)` — Delirium's permanent-only sibling: "there are `count`
  or more distinct **permanent** types (CR 110.4: artifact, battle, creature, enchantment, land,
  planeswalker) among cards in your graveyard" (Matzalantli, the Great Door's transform gate).
  Composes through `Compare(AggregateZone(Player.You, Zone.GRAVEYARD, Aggregation.DISTINCT_PERMANENT_TYPES),
  GTE, Fixed(count))`. Non-permanent card types never count: instants and sorceries have no permanent
  type, and a **kindred** card contributes only its *other* (permanent) type, not "kindred" itself
  (CR 300.2b) — so this is not the same as counting distinct card types among a graveyard filtered to
  permanent cards, which would over-count a kindred permanent.
- `CreatureDiedThisTurn` — intervening-if "if a creature died this turn", **global** (any player's
  control; sums every player's `CreaturesDiedThisTurnComponent`).
- `ControlledCreatureDiedThisTurn` — intervening-if "if a creature died **under your control** this
  turn", scoped to the source's controller (reads only that player's `CreaturesDiedThisTurnComponent`).
  Used by Barrensteppe Siege (Mardu). Dual-mode.
- `SubtypeCreatureDiedThisTurn(subtype)` / `NonSubtypeCreatureDiedThisTurn(subtype)` — **global**,
  subtype-filtered death gates. Backed by `CreatureSubtypesDiedThisTurnComponent`, which records one
  entry per death holding the dying creature's **last-known subtypes** (captured from projected state
  at the moment it left the battlefield, CR 603.10), so a creature whose types change after death is
  still recorded by the subtypes it had as it died. `SubtypeCreatureDiedThisTurn(Subtype.GOBLIN)` is
  true iff some dead creature *had* Goblin; `NonSubtypeCreatureDiedThisTurn(Subtype.ZOMBIE)` is true
  iff some dead creature *lacked* Zombie (note the asymmetry — a turn in which only Zombies died does
  not satisfy the non-Zombie form; a Zombie + a Human both dying satisfies both forms). Used by Undead
  Sprinter (DSK): "if a non-Zombie creature died this turn". Dual-mode. The underlying SDK condition
  is `CreatureWithSubtypeDiedThisTurn(subtype, present)`. Cleared at end of turn by `CleanupPhaseManager`.
- `YouHadPermanentLeaveBattlefieldThisTurn` — intervening-if "if a permanent you controlled left
  the battlefield this turn". Per-player, scoped to the source's controller. Counts every permanent
  type (creatures, lands, artifacts, enchantments, planeswalkers) and includes tokens — broader
  than `ControlledCreatureDiedThisTurn`. Backed by `PermanentLeftBattlefieldThisTurnComponent`,
  incremented by `ZoneTransitionService` whenever a permanent leaves the battlefield, credited to
  its last-known controller. Used by Shortcut to Mushrooms (LTR). Dual-mode.
- `SacrificedHadSubtype(subtype)` — intervening-if "if an X was sacrificed this way". Reads
  `EffectContext.sacrificedPermanents` snapshots captured at cost/effect-time (cost-payment or
  edict-sacrifice both populate the same list). Used by Thallid Omnivore (DOM).
- `ExiledAsCostHadSubtype(subtype)` — intervening-if "if the exiled creature was a X"; the exile
  counterpart of `SacrificedHadSubtype`, reading what an **exile additional cost**
  (`Costs.additional.ExileCards`) just ate, for a spell or an activated ability. The cost is paid on
  announcement (CR 601.2h), long before resolution, so the answer is recorded at payment time. Prefers the cost-time
  last-known-information snapshot (CR 113.7a) over the card now sitting in exile — that is the only
  reading that survives an exiled **token**, and the only one that sees continuous effects that had
  changed the permanent's subtypes on the battlefield; it falls back to the card's printed subtypes for
  a cost paid from a non-battlefield zone. Subtype comparison is case-insensitive on both paths. Used by
  Soul Exchange (FEM): "exile a creature you control … put a +2/+2 counter on that creature if the exiled
  creature was a Thrull."
- `ThisAbilityActivatedThisTurnAtLeast(count)` — intervening-if "if this ability has been activated N or
  more times this turn", counting the activation resolving right now (the fourth activation reads four).
  Scoped to the resolving ability on its own source, so two copies of the same creature burn out
  independently. **Requires the ability to opt in with `trackActivations = true`** — the engine otherwise
  bookkeeps activations only for abilities gated by `OncePerTurn` / `MaxPerTurn`, and this reads zero. See
  `ActivatedAbility.trackActivations` below. Used by Fallen Empires' burnout mana creatures, Farrelite
  Priest ("{1}: Add {W}. If this ability has been activated four or more times this turn, sacrifice this
  creature at the beginning of the next end step.") and Initiates of the Ebon Hand (the same clause on
  {B}) — the clause *reads* a tally rather than imposing a limit, which is exactly why the opt-in exists.
- `SacrificedWasLegendary` — intervening-if "if the sacrificed creature was legendary". Same
  snapshot path as `SacrificedHadSubtype`, but reads `supertypes` instead of subtypes. Used by
  Nasty End and Gríma Wormtongue (LTR).
- `SacrificedWasSuspected` — intervening-if "if the sacrificed creature was suspected" (CR 701.60a).
  Same snapshot path again, reading `EntitySnapshot.wasSuspected`, which `captureEntitySnapshots`
  freezes from `ProjectedState.isSuspected` at cost-payment time. It *has* to be last-known
  information (CR 608.2h): the designation is a floating effect keyed on the entity, so it is gone
  with the creature by the time the ability resolves. Asks "was it suspected", not "did it have
  menace". Used by Agency Coroner (MKM).
- `YouSacrificedThisWay` — intervening-if "if you sacrificed a creature this way". Filters
  `EffectContext.sacrificedPermanents` for snapshots whose last-known controller is the source's
  controller — the gate on the personal half of a symmetric edict. Used by Rise of the Witch-king
  (LTR). The companion `CardSource.FromZone(..., excludeSacrificedThisWay = true)` drops those same
  snapshotted entities from a later gather, so "return **another** permanent card …" can't offer the
  permanent you just sacrificed to the same spell.

### Composition

- `All(c1, c2, ...)` — AND.
- `Any(c1, c2, ...)` — OR.
- `Not(c)` — negate.
- `Compare(v1, op, v2)` — numeric comparison between `DynamicAmount`s. Its `description` is
  player-facing (a `CantAttackUnless` restriction renders it into the "can't attack" message), so it
  reads as a sentence: `ComparisonOperator.phrase` supplies the English comparative and the whole
  condition comes out as "the number of other Wolf creatures you control **is at least** 2". Use
  `ComparisonOperator.symbol` (`>=`) only for diagnostics — never in text a player sees.
- `NumberMatches(amount, NumberProperty.{Prime,Even,Odd,MultipleOf(n)})` — unary numeric predicate
  over one `DynamicAmount` (primality/parity/divisibility); facades `AmountIsPrime/Even/Odd/MultipleOf`.
- `Exists(player, zone, filter)` — at least one matching object exists.

To gate a spell-cost reduction on a condition, use `CostGating.OnlyIf(condition)` on the
`ModifySpellCost` ability (see **Spell cost statics**) rather than baking the condition into the
reduction amount.

### Static-ability vs resolution-time evaluation

Every `Condition` works in both contexts: at spell/trigger resolution (full
`EffectContext` — targets, kicker, triggering entity, etc.) and during state projection
inside a `ConditionalStaticAbility` (only the source entity and projected values are
known). The engine dispatches via a `ConditionEvaluationContext.Resolution` /
`Projection` sealed type — there is **no** separate `SourceProjectionCondition` arm.

Conditions that need resolution-only facts (e.g. `TargetMatchesFilter`, `TargetSharesMostCommonColor`, `TriggeringEntity*`,
`WasKicked`, `ManaSpentToCastIncludes`, `CollectionContainsMatch`) silently evaluate to
`false` under projection — a static-ability gate is never "in the middle of casting a spell".

Other gates available in both contexts:

- `ColorIsMostCommon(color)` — board-derived, so it gates a `ConditionalStaticAbility` directly
  (the Invasion djinns rely on this).
- `SourceChosenModeIs("id")` — gate on the chosen mode (Sieges / `EntersWithChoice`). Works at both
  resolution and projection.
- `CastChoiceMade(slot)` — generic "was a value locked into this `ChoiceSlot`" guard over the durable
  cast-choices bag (mtgish's `AColorWasChosen`): `CastChoiceMade(ChoiceSlot.COLOR)`,
  `CastChoiceMade(ChoiceSlot.KICKED)`, `CastChoiceMade(ChoiceSlot.BARGAINED)`. Works at resolution and
  projection; for the optional-additional-cost slots it also answers from the declaration a spell carries
  while it is still on the stack (and from the branch a cost gate is pricing), before any durable bag exists.
- `CastChoiceIs(slot, "value")` — the slot's value equals `value` (text compare; color compares against
  the enum name): `CastChoiceIs(ChoiceSlot.MODE, "Khans")`, `CastChoiceIs(ChoiceSlot.COLOR, "RED")`. The
  generic slot reader new cards should prefer over per-slot conditions; the §8 emitter target for
  mtgish's `TheChosenColor`/`TheChosenCreatureType` guards.
- `CapturedAtCast("flag")` — the named **"as you cast this spell"** condition capture (CR 601.2i) was
  true the moment the spell was cast. Pairs with the spell DSL `captureAtCast("flag", condition)`: the
  engine evaluates `condition` (caster as controller) as the spell finishes being cast and freezes the
  names whose condition held onto `SpellOnStackComponent.castTimeFlags`; this reads the frozen answer at
  resolution, so a later board change can't flip the branch. Distinct from the player-choice slot guards
  above — those read what the player *chose*, this reads whether a game condition *held*. Used by Steer
  Clear ("deals 4 damage instead if you controlled a Mount as you cast this spell"):

  ```kotlin
  spell {
      captureAtCast("controlledMount", Conditions.ControlCreatureOfType(Subtype("Mount")))
      val creature = target("target", TargetPermanent(filter = TargetFilter.AttackingOrBlockingCreature))
      effect = ConditionalEffect(
          condition = Conditions.CapturedAtCast("controlledMount"),
          effect = Effects.DealDamage(4, creature),
          elseEffect = Effects.DealDamage(2, creature),
      )
  }
  ```

**Cast-choice slots (`ChoiceSlot`).** The choices an object locks in *as it is cast / as it enters*
(CR 601.2b) — color, creature type, land type, mode, chosen creature, kicked-ness, bargained-ness,
blight amount, `CHOSEN_NUMBER` — all ride one durable `CastChoicesComponent` on the stable entity (the immutable-ECS
analogue of Forge's SVar bag). `{X}` has its own dedicated reader (`DynamicAmount.CastX`); the other
slots are read generically via `DynamicAmount.CastChoice(slot)` (numeric), `CastChoiceMade(slot)` /
`CastChoiceIs(slot, value)` (conditions), or consumed directly by effects (e.g.
`Effects.CreateTokenOfChosenColorAndType`). `ChoiceSlot.CHOSEN_NUMBER` is the general-purpose,
re-settable numeric slot written by `Effects.ChooseNumberForSource` and read by `DynamicAmount.CastChoice`
— distinct from `BLIGHT_AMOUNT` (a one-time cast additional-cost X); it backs a free-standing repeatable
number choice not tied to casting (Shapeshifter's 0–7 P/T choice).

---

## 13. Dynamic amounts (`DynamicAmount.*`)

Numbers computed at resolution time.

### Math

- `Fixed(n)` — literal constant.
- `XValue` — the X chosen for the spell/ability, read from the transient resolution context. Populated
  only while the spell/ability itself is resolving — an ETB trigger or a later activated ability can't
  see it. Use `CastX` for the durable, object-scoped reading.
- `CastX` — the `{X}` this object was cast with, read off the *current object* regardless of zone, so it
  survives onto the permanent. The same X feeds a "when you cast this spell" trigger, an enters-the-
  battlefield trigger, the enters-with-counters replacement, and a later activated ability — the analogue
  of mtgish's `ValueX` / `Trigger_ValueXOfThatSpell`. Backed by a durable `CastChoicesComponent` that
  rides the spell's stable entity onto the battlefield (and `SpellOnStackComponent.xValue` while still on
  the stack); preserved as last-known information for dies/leaves triggers. A copy of a *permanent*
  (Clone) does not inherit it (CR 707.2); a copy of a *spell* on the stack does. Hydroid Krasis reads
  `CastX` for both its cast trigger ("draw half X") and its enters-with-X-counters replacement.
- `CastChoice(slot)` — the *numeric* value locked into a `ChoiceSlot` as this object was cast, read off
  the same durable `CastChoicesComponent` as `CastX` (falling back to the resolution context, so an
  instant/sorcery that never becomes a permanent still resolves it). The only numeric slot today is
  `ChoiceSlot.BLIGHT_AMOUNT` — the X declared for a `blight X` additional cost (Soul Immolation "deals X
  damage…"). Non-numeric slots (color, creature type, mode) are read by the `CastChoiceMade` /
  `CastChoiceIs` conditions or consumed directly by effects, not by `DynamicAmount`. (Replaces the old
  `ContextProperty(ADDITIONAL_COST_BLIGHT_AMOUNT)`.)
- `TotalManaSpent` — total mana paid from the pool to cast the current spell (sum of every per-color
  bucket; for X spells the X portion is included). E.g. Memory Deluge "where X is the mana spent."
- `ManaSpentOnX(color)` — the amount of `{color}` mana spent on the `{X}` portion specifically, broken
  down by color. Used by payoffs that scale with how much of a color went into X — Soul Burn ("you gain
  life equal to the amount of black mana spent on X"). Pair with `xManaRestriction` (see below) so the X
  can only be paid with the relevant colors.
- `DistinctColorsManaSpent` — the number of distinct *colors* of mana spent to cast the source spell
  (0–5), counting how many of the W/U/B/R/G payment buckets are non-zero. Colorless is not a color
  (CR 105.1) and never counts; mana spent on `{X}` and on generic costs still has its color counted.
  Backs the **Converge** ability word and the classic **Sunburst** rule. Resolves off the source
  entity's recorded payment (live `SpellOnStackComponent` while on the stack, the resolved permanent's
  `CastRecordComponent` afterward), so it reads correctly both at resolution and as the permanent enters
  (the common use: feeding `EntersWithDynamicCounters`). Facade: `DynamicAmounts.colorsOfManaSpent()`.
  A permanent put onto the battlefield without being cast spent no mana, so this is 0 for it.
- `ManaSpentFromSubtype(subtype)` — how many mana units produced by a source with `subtype` were spent
  to cast the current spell. Bat Colony's "create a 1/1 black Bat with flying **for each mana from a
  Cave spent to cast it**" is `ManaSpentFromSubtype(Subtype.CAVE)`. Like `DistinctColorsManaSpent`, it
  resolves off the source entity's recorded provenance (live `SpellOnStackComponent.manaSpentBySubtype`
  on the stack, the resolved permanent's `CastRecordComponent.manaSpentBySubtype` as it enters), so an
  enters-the-battlefield payoff reads it correctly. The subtype is snapshotted at production. 0 for a
  permanent that wasn't cast. See `SpellCastPredicate.PaidWithManaFromSubtype` for the boolean form.
- `DevotionTo(colors, player = You)` — a player's **devotion** to one or more colors (CR 700.5):
  the number of mana symbols of those colors among the mana costs of permanents the player controls.
  One color = "devotion to red"; several = devotion to that combination ("white and black"), where a
  symbol matching more than one listed color is counted once. Every colored symbol contributes —
  plain colored, both halves of a two-color hybrid ({W/U} counts for white *and* blue), monocolored
  twobrid ({2/B} is black), and Phyrexian ({B/P} is black); generic/colorless/{X} never count.
  Face-down permanents have no mana cost and contribute 0. Controller read via projected state.
  Facade: `DynamicAmounts.devotionTo(color, …)`. Used by "draw cards equal to your devotion to red"
  (Clive, Ifrit's Dominant). For the pips inside **one object's** cost ("a spell with one or more
  blue mana symbols in its mana cost") use `DynamicAmounts.coloredManaSymbolsOf(entity, …)` — same
  counting rule (`ManaCost.coloredSymbolCount`), different scope.
- `UnlockedDoors(player = You, distinctNames = false)` — the number of unlocked doors among Rooms
  `player` controls (CR 709.5). Reads per-face door state, so a single Room with **both** doors
  unlocked counts as **two** — an entity-level `AggregateBattlefield`/`Count` cannot see this. With
  `distinctNames = true`, counts the distinct printed names among those unlocked door faces instead
  (two Rooms sharing an unlocked face name count once). Controller read via projected state.
  Facades: `DynamicAmounts.unlockedDoors(player)` and `DynamicAmounts.distinctUnlockedDoorNames(player)`;
  condition facade `Conditions.UnlockedDoorsAtLeast(count, player)`. Feeds the standard `Compare`
  machinery — Rampaging Soulrager ("+3/+0 while two or more unlocked doors"), Misty Salon's X/X token,
  Promising Stairs' "eight or more different names among unlocked doors" alt-win.
- `Add(a, b)` — `a + b`.
- `Subtract(a, b)` — `a − b`.
- `Multiply(a, b)` — `a × b`.
- `Power(base, exponent)` — `base^exponent` with a fixed integer `base` and a dynamic `exponent`; saturates at the global quantity cap. A non-positive exponent yields `1` (`x⁰ = 1`). Used for "draws 2ˣ cards" (Mathemagics: `Power(2, XValue)`).
- `Divide(a, b, roundUp?)` — division with rounding rule.
- `Min(a, b)` — minimum.
- `Max(a, b)` — maximum.
- `Absolute(a)` — `|a|`.

### Player counting

- `PlayerCount(scope = Player.EachOpponent)` — how many players the `scope` names, counting only
  players still in the game (a player who has lost drops out, so a shrinking pod reports the live
  number). `Player.EachOpponent` is "for each opponent" and, outside team play, also "for each other
  player"; `Player.Each` counts the whole table including you. The unconditional sibling of
  `CountPlayersWith` — reach for that one when the count is qualified ("each opponent **who has one
  or fewer cards in hand**"). Its other home is `TargetObject.dynamicMaxCount`, where paired with
  `optional = true` and `differentControllers = true` it spells "for each other player, exile up to
  one target creature that player controls" (Kaya, Spirits' Justice) — see `differentControllers`
  in the targeting section.

### Battlefield aggregation

- `AggregateBattlefield(player, filter, aggregation?, property?, counterType?)` — aggregate over
  matching permanents. `aggregation` defaults to `COUNT`; other modes: `MAX`/`MIN`/`SUM` over a
  `property` (`POWER`/`TOUGHNESS`/`MANA_VALUE`), and the distinct-set counters
  `DISTINCT_TYPES`, `DISTINCT_PERMANENT_TYPES`, `DISTINCT_COLORS`, `DISTINCT_COLOR_PAIRS`,
  `DISTINCT_NAMES`, `DISTINCT_BASIC_LAND_SUBTYPES`
  (Domain), `DISTINCT_COUNTER_TYPES` (the number of different kinds of counters present
  across the group — same kind on several permanents counts once), and `DISTINCT_VALUES`
  (the number of *distinct values* of the configured `property` — Selvala, Eager Trailblazer's
  "the number of different powers among creatures you control" via
  `aggregation = DISTINCT_VALUES, property = POWER`; two permanents sharing a value count once).
  Builder shortcut: `DynamicAmounts.battlefield(player, filter).distinctValues(CardNumericProperty.POWER)`.
  `DISTINCT_NAMES` counts *differently named* matched permanents (two sharing a name count once) —
  "the number of differently named lands you control" (Emil, Vastlands Roamer) via
  `DynamicAmounts.battlefield(Player.You, GameObjectFilter.Land).distinctNames()`.
  `DISTINCT_COLOR_PAIRS` counts the *color pairs* the group contributes: one unordered pair per
  matched permanent that is exactly two colors (CR 105.2c), the same pair on several permanents
  counting once, so the value is bounded by the ten pairs in Magic. Mono-colored, three-or-more
  colored, and colorless permanents contribute nothing — "exactly two colors" is part of the
  aggregation rather than something the filter spells — which is what makes it distinct from
  `DISTINCT_COLORS` (a set of *colors*, bounded by five). Niv-Mizzet, Guildpact's "the number of
  different color pairs among permanents you control that are exactly two colors" via
  `DynamicAmounts.colorPairsAmongPermanents()`.
  `excludeSelf = true` drops the aggregate's own source/affected entity ("among *other* …"), e.g.
  Loot, the Key to Everything's "the number of card types among other nonland permanents you control"
  (`filter = GameObjectFilter.NonlandPermanent, aggregation = DISTINCT_TYPES, excludeSelf = true`).
  `DISTINCT_TYPES` counts only true **card types** (CR 205.2a: Artifact/Creature/Enchantment/…), never
  supertypes or subtypes, while still honoring projection-changed types (an animated land that became a
  Creature counts as a Creature). `DISTINCT_PERMANENT_TYPES` is the same but restricted to the six
  **permanent** types (CR 110.4: artifact, battle, creature, enchantment, land, planeswalker) — instant,
  sorcery, and kindred never count (a kindred permanent contributes only its *other* type). Used for
  "N or more permanent types among …" (Matzalantli, the Great Door, via
  `Conditions.DistinctPermanentTypesInGraveyard`).
  When `counterType` (a `CounterTypeFilter`) is set with `SUM`/`MAX`/`MIN`, the per-permanent value
  aggregated is the count of *that kind* of counter on it — i.e. "the total <kind> counters among
  <filter>" (Tom Bombadil's lore-counter total; reach for it via
  `Conditions.CounterKindAmongYouControlAtLeast`). `CounterTypeFilter.Any` totals every kind. Counters
  are read from base state (layer-independent).
  The `player` accepts the same references as elsewhere, including `Player.ControllerOf(desc)` /
  `Player.OwnerOf(desc)` — "the creatures **that [target]'s controller** controls" — which resolve
  against the effect's chosen target. Skulking Killer's "target creature an opponent controls gets
  -2/-2 … if that opponent controls no other creatures" is
  `AggregateBattlefield(Player.ControllerOf("target creature an opponent controls"), Creature) == 1`
  (the target's controller controls exactly one creature — the target itself).
- `GreatestAmongPlayers(players, inner)` — the largest value `inner` takes when measured **once per
  player** in `players`: Oracle's "the greatest number of X a player controls / an opponent controls /
  has". Every iteration rebinds the resolution context's controller, so `Player.You` inside `inner`
  means the player being measured (the same rebinding `Effects.ForEachPlayer` does) — write `inner`
  from that player's side and never with an outward reference such as `Player.AnOpponent`. Empty
  player set evaluates to 0.
  This is **not** an `Aggregation` on `AggregateBattlefield`: that primitive fans its player reference
  out and then flattens every player's permanents into one list, so
  `AggregateBattlefield(Player.Each, Creature)` is the table's *total* creature count and there is no
  per-player boundary left to maximize across. Investigator's Journal's "a number of suspect counters
  … equal to the greatest number of creatures a player controls" is
  `DynamicAmounts.greatestControlledBySinglePlayer(GameObjectFilter.Creature)`; pass
  `players = Player.EachOpponent` for the "an opponent controls" wording (Cavern-Hoard Dragon). The
  wrapper takes any `DynamicAmount`, so the off-battlefield siblings ("the greatest number of cards an
  opponent has drawn this turn") are the same shape around a `TurnTracking`.
- `AggregateZone(player, zone, filter?, aggregation?)` — count cards in a zone.
- `CountPermanentsOfType(player, subtype)` — count by creature type.
- `CountCreaturesYouControl` — shorthand for "your creatures".

#### A bare tribal noun means *permanents*, not creatures

Oracle spells two different scopes and means two different things, and the SDK has a facade for each.
**"the number of Slivers on the battlefield"** — the bare noun — counts every Sliver *permanent*;
**"the number of Sliver creatures"** — the adjectival form — counts only the creatures. Read the
printed text and pick accordingly; they coincide for almost every card, which is exactly why picking
by habit went unnoticed for a long time (Argentum Assay's differential gate found it, and the
migration that followed touched 103 cards).

| Printed | Facade |
|---|---|
| "the number of **Zombies**" | `DynamicAmounts.permanentsWithSubtype(subtype)` |
| "the number of **Zombie creatures**" | `DynamicAmounts.creaturesWithSubtype(subtype)` |
| "if you control a **Zombie**" | `Conditions.ControlPermanentOfType(subtype)` |
| "if you control a **Zombie creature**" | `Conditions.ControlCreatureOfType(subtype)` |
| "target **Zombie card** in your graveyard" | `TargetFilter.PermanentInYourGraveyard.withSubtype(…)` |
| "target **Zombie creature card** in your graveyard" | `TargetFilter.CreatureInYourGraveyard.withSubtype(…)` |
| "other **Zombies** you control" | `GameObjectFilter.Permanent.withSubtype(…)` |
| "other **Zombie creatures** you control" | `GameObjectFilter.Creature.withSubtype(…)` |

Zombie Master is the card that shows the distinction is deliberate rather than stylistic: it prints
both spellings, and the ability its bare-noun line grants says "Regenerate this **permanent**".
- Facades: `DynamicAmounts.equipmentYouControl(player = You)` — Equipment you control (counts
  permanents whose projected subtypes include Equipment), and `equippedCreaturesYouControl(player = You)`
  — creatures with at least one Equipment attached (`GameObjectFilter.Creature.equipped()`). Used by
  Adelbert Steiner (+1/+1 per Equipment), Barret Wallace.
- Facades: `DynamicAmounts.cardsInYourGraveyard(player = You)` /
  `creatureCardsInYourGraveyard(player = You)` (graveyard counts) — pass `Player.Each` for the
  "in **all** graveyards" wording (Undergrowth Scavenger's entry counters), and `DynamicAmounts.cardsInYourHand()` — cards in your hand,
  e.g. Stingerback Terror's "-1/-1 for each card in your hand" (multiply by `-1` and feed
  both bonuses of a `GrantDynamicStatsEffect(GroupFilter.source(), …)`). Greatest power among
  creatures you control is `DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature).maxPower()`
  (Tumbleweed Rising's X/X token, paired with `Effects.CreateDynamicToken`).

### Player & game

- `LifeTotal(player)` — current life total.
- `UnspentMana(player)` — total unspent mana in that player's mana pool (all colours + colorless +
  restricted entries, i.e. the pool's `total`). Powers "as long as you have six or more unspent mana"
  (Ozai, the Phoenix King) via `Conditions.YouHaveUnspentManaAtLeast(n)` /
  `CompareAmounts(UnspentMana(You), GTE, Fixed(n))`.
- `HandSize(player)` — cards in hand.
- `Speed(player)` — that player's speed, 0–4 (Aetherdrift, CR 702.179; facade
  `DynamicAmounts.speed(player)`). Powers "where X is your speed" (Point the Way, The Speed Demon,
  Samut, the Driving Force) and, via `Compare`, the whole max-speed gate. A player with no speed reads
  as 0 (CR 702.179f), so it never needs a has-speed guard. Not pooled in team games, unlike life and
  poison.
- `TurnCount(player)` — turn number for that player.
- `TurnTracking(player, TurnTracker)` — value of a per-turn counter (see below).
- `SpellsCastThisTurn(player, filter?, excludeSelf?, fromZone?, countDistinctCardTypes?, beforeTriggeringSpell?)` — count of spells `player` has cast
  this turn, read from the per-player cast history (`GameState.spellsCastThisTurnByPlayer`). `filter`
  matches a spell characteristic captured at cast time — type/color/mana value (face-down casts
  never match a non-empty filter); defaults to `GameObjectFilter.Any`. `excludeSelf` (default
  `false`) drops the resolving spell's *own* record, matched by its stack entity id, for "the
  number of **other** spells you've cast this turn". `fromZone` (default any) restricts to spells cast
  from that zone (`CastSpellRecord.castFromZone`), matched independently of `filter`. The triggering
  spell is already recorded and counts unless `excludeSelf`. DSL:
  `DynamicAmounts.spellsCastThisTurn(player, filter, excludeSelf, fromZone, beforeTriggeringSpell)`.
  - Thunder Salvo ("2 plus the number of other spells you've cast this turn"):
    `Add(Fixed(2), SpellsCastThisTurn(Player.You, excludeSelf = true))`.
  - Magebane Lizard ("the number of noncreature spells they've cast this turn"):
    `SpellsCastThisTurn(Player.TriggeringPlayer, GameObjectFilter.Noncreature)`.
  - `countDistinctCardTypes` (default `false`) switches the aggregation from *count of spells* to
    *count of distinct card types among them* — April O'Neil, Hacktivist ("draw a card for each card
    type among spells you've cast this turn"): `SpellsCastThisTurn(Player.You, countDistinctCardTypes
    = true)`. An artifact creature spell contributes both Artifact and Creature; types are unioned
    across the matching records (and across every player the ref resolves to).
  - `beforeTriggeringSpell` (default `false`) truncates each player's history at the **triggering
    spell's own cast record** — the storm-style "each other spell you've cast **before it** this
    turn" clause (CR 702.40a). The triggering spell doesn't count itself, and neither does anything
    cast in *response* to the trigger while it waits on the stack, so the value is the spell's
    position in the turn's cast history rather than a resolution-time total. Unlike `excludeSelf`
    (which keys off the resolving *source*, and is therefore inert for a permanent's triggered
    ability) this keys off the triggering entity; a player with no record for it contributes zero.
    **Thousand-Year Storm** ("copy it for each other instant and sorcery spell you've cast before it
    this turn"): `SpellsCastThisTurn(filter = GameObjectFilter.InstantOrSorcery,
    beforeTriggeringSpell = true)`.
  - Pairs with the `YouCastSpellsThisTurn` **condition** (§ conditions) — that gates a yes/no
    threshold, this yields the count.
- `SpellsCastLastTurn` (facade `DynamicAmounts.spellsCastLastTurn()`) — total spells cast during the
  immediately preceding turn. Reads the active-player/shared-team snapshot captured at the turn
  boundary before current-turn counters reset. Compose with `Conditions.CompareAmounts`: `== 0`
  models the original Innistrad werewolf front-face trigger, while `>= 2` models its back-face
  trigger. It is deliberately turn-global rather than controller-scoped, matching "no spells were
  cast last turn" and "a player cast two or more spells last turn."
- `CraftedMaterialsTotalPower` — total printed power of the cards exiled to craft the source
  permanent (CR 702.167c). Reads the source's `CraftedFromExiledComponent`. Used for the
  `*`-power CDA on Mastercraft Raptor (Saheeli's Lattice back face). Evaluates to 0 when the
  source has no recorded materials.
- `CraftedMaterialsTotalManaValue` — mana-value sibling of `CraftedMaterialsTotalPower`: total
  printed mana value of the crafted materials. Exact-one crafts read it as the single material's
  mana value (Jadeheart Attendant's "gain life equal to the mana value of the exiled card used to
  craft it"). 0 when not crafted.
- `CraftedMaterialsColorCount` — number of distinct printed colors (0–5) among the crafted
  materials (Sunbird Effigy's `*/*` P/T CDA). Pairs with the
  `Effects.AddOneManaOfEachCraftedMaterialColor()` mana effect (§4 mana effects) —
  `AddOneManaOfEachColorAmongEffect(colorSource = ManaColorSource.CraftedMaterials)` — for
  "for each color among the exiled cards used to craft this creature, add one mana of that
  color". 0 when not crafted.
- `CreaturesThatCrewedOrSaddledThisTurn` (facade `DynamicAmounts.creaturesThatCrewedOrSaddledThisTurn()`)
  — number of distinct creatures that crewed (CR 702.122) or saddled (CR 702.171) the source
  permanent this turn. Source-relative: reads the source's `CrewSaddleContributorsComponent` and
  returns its size. Retains contributors that have since left the battlefield, so the count
  includes creatures no longer present as the ability resolves (Luxurious Locomotive ruling) — a
  plain `Count` over `crewedOrSaddledSourceThisTurn()` can't express that. Evaluates to 0 with no
  source / no component. For *which* creatures (targeting/gathering) use the
  `CrewedOrSaddledSourceThisTurn` state predicate instead.
- `PermanentsSacrificedThisWay` (facade `DynamicAmounts.permanentsSacrificedThisWay()`) — number of
  permanents sacrificed by the current resolving effect ("this way"); reads the effect context's
  `sacrificedPermanents` snapshot list (populated when an edict resolves earlier in the same
  composite — the sibling-rider wiring from the sacrifice-snapshot work). Used by "each opponent
  sacrifices a creature … create a Food token for each creature sacrificed this way" (Voracious Fell
  Beast). Evaluates to 0 when nothing was sacrificed.
- `TotalPowerSacrificedThisWay` (facade `DynamicAmounts.totalPowerSacrificedThisWay()`) — the sibling
  of `PermanentsSacrificedThisWay` over the same `sacrificedPermanents` snapshots, summing each
  snapshot's power instead of counting the entries: "sacrifice any number of other creatures, then
  exile the top X cards of your library, where X is **their total power**" (Kylox, Visionary
  Inventor). The snapshots are last-known information taken as each permanent was sacrificed
  (Rule 608.2h) — which is what the wording has to mean, since they are all in the graveyard by the
  time a later sibling effect reads them. A sacrificed noncreature contributes 0 rather than
  erroring. Evaluates to 0 when nothing was sacrificed.
- `LargestSharedCreatureTypeCount(player = You)` — the size of the largest creature-type tribe among
  the creatures `player` controls, i.e. "the greatest number of creatures you control that have a
  creature type in common." For every creature type present, tally how many of the player's creatures
  have it, then take the max. A creature with several creature types feeds each of its tribes (a Bird
  Soldier adds to both the Bird and the Soldier tally); a Changeling — projected to all creature types
  — feeds every tribe. Reads projected creature subtypes (type-changing effects and Changeling are
  honored), restricted to actual creature types so artifact/land subtypes never inflate the count.
  Evaluates to 0 when no creature shares a type. Used by White Lotus Tile ("Add X mana of any one
  color, where X is …") — pair with `AddManaOfChoiceEffect(ManaColorSet.AnyColor, amount = …)`.

### An object's characteristics

`EntityProperty(entity, property)` reads one number off one object, and the `DynamicAmounts` facades
name the grid its two axes cross — the entity (`Source` / `Target(index)` / `Triggering` /
`Sacrificed(index)` / `EnchantedCreature` / …) against the property (`Power` / `Toughness` /
`ManaValue` / `CounterCount` / …). The three that carry nearly every printed "…equal to its ⟨noun⟩":

| | power | toughness | mana value |
|---|---|---|---|
| the source | `sourcePower()` | `sourceToughness()` | `sourceManaValue()` |
| a chosen target | `targetPower(i)` | `targetToughness(i)` | `targetManaValue(i)` |
| the triggering object | `triggeringPower()` | `triggeringToughness()` | `triggeringManaValue()` |

Which entity a card's printed "its" means is decided by the sentence, not by the word: "Destroy
target artifact. You gain life equal to **its** mana value." is `targetManaValue()`, "Whenever
another creature you control dies, you gain life equal to **its** toughness." is
`triggeringToughness()`, and "{2}, {T}, Sacrifice this artifact: You gain life equal to **its** mana
value." is `sourceManaValue()`.

Also on `Sacrificed`: `sacrificedPower(i)` / `sacrificedToughness(i)`, for a cost that sacrifices
something other than the source.

### Counters

- `CountersOnSource(type)` — counters of `type` on the source permanent.
- `LastKnownSourceCounters(type)` — counters the source had as it last existed on the battlefield (dies/leaves
  triggers, and self-exile/self-sacrifice costs) — see "Last-known source counters" below.
- `CountersOnTarget(target, type)` — counters on a target permanent.
- `CountersOnContext(path, type)` — counters stored in an `EffectContext` path.

### Last-known source counters (self-exile / self-sacrifice cost, dies/leaves triggers)

- `LastKnownSourceCounters(CounterTypeFilter)` — the number of matching counters the *source* had as it last existed
  on the battlefield (CR 113.7a / 608.2h). Counters cease to exist on a zone change (CR 122.2), so the value comes
  from whichever snapshot the resolution context carries — the two never both apply to one resolution:
  - the **cost-payment** snapshot, taken by `ActivateAbilityHandler` when an activated ability's cost exiles or
    sacrifices its own source. Lost Isle Calling: "{4}{U}{U}, Exile this enchantment: Draw a card for each verse
    counter on this enchantment. If it had seven or more verse counters on it, take an extra turn." Both the draw
    amount (`DrawCards(lastKnownSourceCounters(Named(Counters.VERSE)))`) and the seven-or-more gate
    (`Compare(lastKnownSourceCounters(Named(Counters.VERSE)), GTE, Fixed(7))`) read this node.
  - the **leaves-the-battlefield** snapshot carried on a dies/leaves trigger (`TriggerContext.lastKnownCounters`),
    available both to the intervening-`if` (`interveningIf`) and to the resolving effect. Nine-Lives Familiar:
    "When this creature dies, if it had a revival counter on it, return it to the battlefield with one fewer revival
    counter on it at the beginning of the next end step."

  `CounterTypeFilter.Any` sums all counter types; otherwise it reads the named/typed counter — naming the kind is
  what stops an unrelated +1/+1 counter from satisfying "if it had a revival counter on it". This is the
  parameterized sibling of `ContextPropertyKey.LAST_KNOWN_PLUS_ONE_COUNTER_COUNT` (fixed to +1/+1) and
  `LAST_KNOWN_TOTAL_COUNTER_COUNT` (sums every kind). Facade: `DynamicAmounts.lastKnownSourceCounters(filter)`.
  Contrast `EntityProperty(Source, CounterCount(filter))`, which reads counters on the still-present source (zero
  once it has left the battlefield).

  **Scheduling it into a delayed trigger:** a value read from the dies-trigger context is gone by the time a
  `CreateDelayedTriggerEffect` fires, so `CreateDelayedTriggerExecutor` snapshots the `amount` of a nested
  `AddDynamicCountersEffect` into a `Fixed` literal at scheduling time (the same treatment `AddManaEffect` gets).
  That is how Nine-Lives Familiar's "with one fewer revival counter" —
  `Subtract(lastKnownSourceCounters(Named(Counters.REVIVAL)), Fixed(1))` — survives to the end step.

  **Pipeline-scoped counts get the same treatment, board-state counts deliberately do not.** A
  `CreateTokenEffect` scheduled into a delayed trigger has its `count` frozen at scheduling time *only
  if* the amount reads the pipeline that produced it — `DistinctEntitiesInCollections`,
  `DistinctCardTypesInCollections`, `ManaValueSumOfCollection`, `StoredCardManaValue`,
  `VariableReference`, or any arithmetic tree containing one. Those live in the resolving
  `EffectContext` and would silently read 0 an upkeep later. A *board-state* count
  (`AggregateBattlefield`, …) is left lazy, because "at the beginning of your end step, create a token
  for each creature you control" is supposed to count when the trigger fires. The Eagles Are Coming!
  is the pipeline case: `moveTracked` records what actually reached the hand and
  `DistinctEntitiesInCollections` counts those ids without looking them up in state, so a *token*
  creature returned to hand still counts even though it ceased to exist (CR 111.7).

### Last-known damage dealt to the source (dies/leaves triggers)

- `LastKnownDamageDealtToSource` — the total damage dealt to the source *this turn*, read as last-known
  information. Facade: `DynamicAmounts.lastKnownDamageDealtToSource()`. Tangled Colony: "When this
  creature dies, create X 1/1 black Rat creature tokens …, where X is the amount of damage dealt to it
  this turn." The engine already tallies damage per source-controller on every permanent it is dealt to
  and captures that map onto the `ZoneChangeEvent` when the permanent leaves the battlefield, so the
  value survives into the dies trigger; this node simply sums it across all controllers. The per-player
  split is what `EachPlayerDrawsForDamageDealtToSource` (Grothama, All-Devouring) reads instead.
  Evaluates to `0` when no snapshot is present — including for a source still on the battlefield, since
  the tally is only captured on the way out. Lethal damage is not a cap: excess damage, and non-lethal
  damage the creature survived earlier in the turn, both count.

- **Last-known source P/T (self-exile / self-sacrifice cost)** — the P/T analogue of
  `LastKnownSourceCounters`, applied automatically to `EntityProperty(EntityReference.Source, Power|Toughness)`
  (i.e. `DynamicAmounts.sourcePower()` / `sourceToughness()`). When an activated ability's cost sacrifices or
  exiles its own source, the source is off the battlefield by resolution, so `ActivateAbilityHandler` snapshots
  its projected P/T at cost-payment time (CR 113.7a / 608.2h, mirroring the counter snapshot) and
  `DynamicAmountEvaluator` reads the snapshot back when the source is no longer on the battlefield. This makes
  "{T}, Sacrifice this creature: it deals damage equal to its power" (Ghitu Fire-Eater, Cinder Shade, Blazing
  Bomb's Blow Up) read the pre-sacrifice power including counters/buffs rather than zero. No DSL change: existing
  `sourcePower()` reads simply become correct after a self-sacrifice. Live on-battlefield `Source` reads are
  unaffected (the snapshot is only consulted when the source has left).

### Station

- `StationCharge` — the number of charge counters a Station ability puts on its permanent: the power of the creature
  tapped to pay the station cost (CR 702.184a). Emitted by the `station()` builder (§11); do not hand-author. It is a
  dedicated node rather than `EntityProperty(TappedAsCost(0), Power)` so the CR 702.184c characteristic substitution
  (Tapestry Warden's `StationUsingToughness` → use toughness when toughness > power) is confined to station abilities
  and never rewrites an unrelated "tap a creature: do X equal to its power" read. Resolves with last-known information
  (CR 113.7a) if the tapped creature has left the battlefield before the station ability resolves.

### Card properties

- `TargetPower(target)` — target's current power.
- `TargetToughness(target)` — target's current toughness.
- `TargetManaValue(target)` — target's mana value.
- `DynamicAmounts.targetManaSpent(index)` — sum of all `manaSpent{Color}` buckets on
  the targeted spell's `SpellOnStackComponent` (i.e. what was actually paid, after
  cost reductions/increases). Pair with `targetManaValue()` for "if the amount of
  mana spent to cast that spell was less than its mana value" gates (Unravel).
  Desugars to `EntityProperty(EntityReference.Target(index), EntityNumericProperty.ManaSpent)`.
  Returns 0 if the target isn't a spell on the stack.
- `DynamicAmounts.targetColorCount(index)` / `DynamicAmounts.colorCountOf(entity)` — number of
  distinct colors of the indexed cast-time target / any `EntityReference`. Desugars to
  `EntityProperty(entity, EntityNumericProperty.ColorCount)`. Read from projected state for
  battlefield permanents (honors layer-5 color-changing — a creature turned colorless counts 0).
  Powers "for each color of [it]" amounts, e.g. Dragonfire Blade's equip cost reduction.
- `DynamicAmounts.coloredManaSymbolsOf(entity, vararg colors)` — the number of mana symbols of
  `colors` in **that one entity's printed mana cost**. Desugars to
  `EntityProperty(entity, EntityNumericProperty.ColoredManaSymbolCount(colors))`. Namor the
  Sub-Mariner's "…with one or more blue mana symbols in its mana cost, create **that many** 1/1 blue
  Merfolk creature tokens" is `coloredManaSymbolsOf(EntityReference.Triggering, Color.BLUE)`, paired
  with the `.coloredManaSymbolsAtLeast(Color.BLUE)` filter on the trigger. Hybrid and Phyrexian
  pips count for their colour(s) (CR 107.4e/f); generic, `{C}` and `{X}` count for none, whatever
  value was announced for X (`{X}` is a generic symbol, CR 107.4b). The *printed* cost is what is
  read — a card's mana cost is the symbols printed on it (CR 202.1/202.1a), while alternative
  costs, additional costs and cost reductions only build the spell's **total cost** (CR 601.2f) —
  so none of them change the count. Face-down objects have no mana cost and count 0
  (CR 708.2a); a missing entity counts 0.
  **Not `DevotionTo`** — devotion (CR 700.5) counts the same symbols across every permanent a
  player controls, this counts them inside one object's cost. The two share one counting rule
  (`ManaCost.coloredSymbolCount`), so they agree symbol-for-symbol and differ only in scope.
  Being `Triggering`-scoped, it is rejected by `SetBaseStatsEffect(reevaluateContinuously = true)`
  like every other context-scoped amount; read off `EntityReference.Source` it is projector-safe.
- `DynamicAmount.EntityProperty(entity, EntityNumericProperty.DamageDealtThisTurn)` — actual damage
  dealt by a battlefield permanent or resolving spell this turn, including combat and noncombat
  damage to any recipient. The tally is bound to the source’s object identity. Prevention
  and damage replacement effects modify the tally; damage replaced entirely with counters contributes
  zero. Turn changes make the value read zero and zone changes clear it. For “damage dealt this way”,
  store the value before the damage and subtract it afterward (Brightflame). The stored baseline and
  damage history survive resolution decisions, including optional damage redirection.
- `EntityProperty(entity, EntityNumericProperty.ExcessMarkedDamage)` — the excess damage (CR 120.4a)
  marked on a creature: `max(0, marked − toughness)`, read from post-damage state. Amount-valued twin of
  the `TargetMarkedDamageExceedsToughness` condition. Read it AFTER a deal-damage step in the same
  composite/pipeline resolution so the marked damage in scope is the damage that step just dealt — e.g.
  Hell to Pay: "deals X damage to target creature. Create a number of tapped Treasure tokens equal to the
  amount of excess damage dealt to that creature this way." (`EntityProperty(EntityReference.Target(0),
  ExcessMarkedDamage)`). CompositeEffect resolves sub-effects sequentially with no interleaved SBA pass, so
  the creature is still present mid-composite with its just-marked damage. Returns 0 off the battlefield or
  for a non-creature.
- `EntityProperty(entity, EntityNumericProperty.BasePower)` / `EntityNumericProperty.BaseToughness` — the
  entity's printed **base** power/toughness (its P/T before counters, Auras, Equipment, anthems, and any other
  continuous modification): the Fixed `CardComponent.baseStats.basePower`/`baseToughness` the card was printed
  with, `0` for `*`/CDA stats. Uses the *same* base as the `CardPredicate.PowerGreaterThanBase` filter, so
  `Subtract(EntityProperty(e, Power), EntityProperty(e, BasePower))` gives exactly the "difference" (current
  power − base power) that filter's qualifying creatures have — the per-creature +1/+1 counter amount for
  **Sovereign Okinec Ahau** ("Whenever ~ attacks, for each creature you control with power greater than that
  creature's base power, put a number of +1/+1 counters on that creature equal to the difference."), composed
  as a `ForEachInGroup` over `Creature.youControl().powerGreaterThanBase()` reading the amount off
  `EntityReference.IterationEntity`. Not projected — always the printed base.
- `CardNumericProperty(card, property)` — generic numeric property accessor.

### Board-count shortcuts (`DynamicAmounts.*` facades)

`DynamicAmounts.creaturesYouControl()`, `.landsYouControl()`, `.otherCreaturesYouControl()`,
`.attackingCreaturesYouControl()`, `.equipmentYouControl()` — each a named `battlefield(…).count()`.

`DynamicAmounts.legendaryCreaturesYouControl()` is the same shape over
`GameObjectFilter.Creature.legendary()`: the Kamigawa channel lands' "costs {1} less to activate for
each legendary creature you control", fed to `ActivatedAbility.genericCostReduction`. Counts creatures
only — a legendary land or artifact does not qualify.

### Triggering-entity shortcuts (`DynamicAmounts.*` facades)

For triggered abilities whose effect reads a property of the entity that caused the trigger
(rather than the source of the ability):

- `DynamicAmounts.triggeringPower()` — power of the triggering entity (e.g. Warstorm Surge:
  "it deals damage equal to its power").
- `DynamicAmounts.triggeringToughness()` — toughness of the triggering entity.
- `DynamicAmounts.triggeringManaValue()` — mana value of the triggering entity.
- `DynamicAmounts.countersOnTriggering(type = CounterTypeFilter.Any)` — number of counters (of `type`,
  default every kind) on the triggering permanent (e.g. Spider-Man Noir: "surveil X, where X is the
  number of counters on it").

All desugar to `EntityProperty(EntityReference.Triggering, …)`.

### Death-batch total-power shortcut (`DynamicAmounts.*` facade)

For "one or more creatures you control die" **batch** triggers
(`Triggers.OneOrMoreCreaturesYouControlDie`, CR 603.2c) whose payoff scales by the combined power of
the creatures that died:

- `DynamicAmounts.diedBatchTotalPower()` — the summed **last-known** power of the creatures that died
  in the batch that fired this trigger, counting only the deaths that match the trigger's filter
  (so a `.nontoken()` trigger ignores dying tokens). Desugars to
  `ContextProperty(ContextPropertyKey.DIED_BATCH_TOTAL_POWER)`. The value is captured at trigger
  detection (a graveyard card would report only printed power, dropping counters and buffs — CR
  603.10 last-known information), so it survives to resolution. Returns 0 outside a creatures-died
  batch trigger. Individual powers may be negative, so the sum can be too. Used by The Skullspore
  Nexus — "create a green Fungus Dinosaur creature token with base power and toughness each equal to
  the total power of those creatures" (`Effects.CreateDynamicToken(dynamicPower = …, dynamicToughness
  = …)`).

### Attached-creature shortcut (`DynamicAmounts.*` facade)

For Aura/Equipment abilities that read a property of the creature the source is attached to (rather
than the source permanent itself — for an Aura, `EntityReference.Source` is the Aura, not the creature):

- `DynamicAmounts.enchantedCreaturePower()` — power of the attached creature (e.g. Pain for All:
  "enchanted creature deals damage equal to its power"). Desugars to
  `EntityProperty(EntityReference.EnchantedCreature, EntityNumericProperty.Power)`. The
  `EnchantedCreature` reference resolves through the source's `AttachedToComponent` (state-aware), so it
  needs an effect context with a `sourceId`; it returns 0 in predicate/filter-only contexts that don't
  thread state. When read in a **triggered ability** and the attached creature has already left the
  battlefield by resolution (e.g. removed in response to the aura's ETB trigger), it falls back to the
  creature's last-known power — captured when the trigger fired — per CR 608.2g, rather than 0.

### Ring-bearer's power (`EntityReference.RingBearer`)

- `EntityProperty(EntityReference.RingBearer(player = Player.You), EntityNumericProperty.Power)` —
  the power of the referenced player's designated **Ring-bearer** (CR 701.54: the creature carrying
  `RingBearerComponent` and controlled by its designating owner), or 0 when that player has no
  Ring-bearer. The reference always reads the *referenced* player's bearer independent of any later
  player-context rebinding — so inside a `ForEachPlayerEffect` / mill-each-player, "each player mills
  cards equal to **your** Ring-bearer's power" (One Ring to Rule Them All) measures the spell
  controller's Ring-bearer for every player. `player` defaults to `Player.You` ("your Ring-bearer");
  opponent variants resolve through the effect context's `opponentId`.

### Attachment-count shortcuts (`DynamicAmounts.*` facades)

For "X = the number of [things] attached to this permanent":

- `DynamicAmounts.attachmentsOnSelf()` — every Aura/Equipment/Fortification attached to the source
  (Champion of the Flame, Valduk). Desugars to `EntityProperty(Source, AttachmentCount())`
  (`AttachmentKind.ANY`).
- `DynamicAmounts.equipmentAttachedToSelf()` — only the Equipment attached to the source (Shagrat,
  Loot Bearer: "amass Orcs X, where X is the number of Equipment attached to Shagrat"). Desugars to
  `EntityProperty(Source, AttachmentCount(AttachmentKind.EQUIPMENT))`.
- `DynamicAmounts.attachmentsOnEnchantedCreature(kind = AttachmentKind.ANY)` — attachments of `kind`
  on the creature the *source* is attached to (the enchanted creature for an Aura, the equipped
  creature for an Equipment — both read the same attachment link), for an Aura or Equipment that
  buffs its host by that host's own attachment count. Desugars to
  `EntityProperty(EnchantedCreature, AttachmentCount(kind))`. Default `ANY` counts every Aura and
  Equipment (With Great Power…: "enchanted creature gets +2/+2 for each Aura and Equipment attached
  to it"); `AttachmentKind.EQUIPMENT` counts only Equipment (Golem-Skin Gauntlets: "equipped creature
  gets +1/+0 for each Equipment attached to it", which includes the Gauntlets themselves). Distinct
  from `attachmentsOnSelf()`, which counts attachments on the source itself.

`AttachmentCount(kind)` takes an `AttachmentKind` (`ANY` / `EQUIPMENT` / `AURA`); the evaluator
counts the source's `attachedIds` whose card type matches the kind.

### Just-amassed Army (`EntityReference.AmassedArmy`)

For composite "Amass [subtype] N. Then [effect using the amassed Army's …]" shapes — Foray of
Orcs, Surrounded by Orcs, Grishnákh Brash Instigator. Compose `Effects.Amass(...)` with a
sibling effect that reads `DynamicAmount.EntityProperty(EntityReference.AmassedArmy, …)`:

- `EntityReference.AmassedArmy` — the Army that received the +1/+1 counters from the most
  recent Amass step in the current resolution pipeline (CR 701.47). Written by `AmassExecutor`
  into `EffectContext.pipeline.storedCollections[AmassedArmy.STORAGE_KEY]` after Amass
  resolves; the slot survives the multi-Army choice continuation, so a follow-up sibling
  reads the chosen Army even when Amass paused for a decision.
- Pair with `EntityNumericProperty.{Power,Toughness}` for "deals damage equal to the amassed
  Army's power" (Foray of Orcs) or "mills X cards, where X is the amassed Army's power"
  (Surrounded by Orcs).
- It also resolves inside **target / affected-entity filters** via the pipeline-threaded
  predicate path (below), so comparison-based targeting like Grishnákh's "with power ≤ the
  amassed Army's power" works — `TargetFilter.…powerAtMostEntity(EntityReference.AmassedArmy)`.
- When a sibling effect needs the Army as an **`EffectTarget`** rather than as an amount or a
  filter comparand, read the same slot back with
  `EffectTarget.PipelineTarget(EntityReference.AmassedArmy.STORAGE_KEY)` — Goblin Plate Mail's
  "amass Goblins 1, then attach this Equipment to the amassed Army". The Army is not a target
  (nothing is chosen on the stack); this just addresses what Amass already picked. `CardLinter`
  knows `Amass` writes that slot, so the read does not trip the unwired-collection rule.

### The imprinted card (`EntityReference.LinkedExiledCard`)

- `EntityReference.LinkedExiledCard(index = 0)` — a card **exiled with the ability's source**: its
  `LinkedExileComponent` pile, which the Mirrodin *Imprint* keyword (CR 702.15) fills with exactly one
  card, and which any `linkToSource = true` exile writes (`Effects.ExileLinkedToSource`,
  `Patterns.Hand.revealHandAndExileChosen(linkToSource = true)`, `MoveToZone`/`MoveCollection`'s flag).
  Resolution walks the pile in exile order and **skips ids that have since left exile**, so index `0`
  is the oldest card still exiled and a pile whose card has moved on resolves to null.
- It is the read-side companion to that flag, and it exists so an imprint payoff needs no
  characteristic-specific vocabulary of its own: every `…With(entity)` predicate and every
  `DynamicAmount.EntityProperty` already works against it. Thought Prison's "shares a color or mana
  value with the exiled card" is
  `GameObjectFilter.Any.sharingColorWith(EntityReference.LinkedExiledCard()) or GameObjectFilter.Any.sharingManaValueWith(EntityReference.LinkedExiledCard())`;
  Mourner's Shield's "a source of your choice that shares a color with the exiled card" is the same
  colour half used as a `PreventionSourceFilter.ChosenSourceMatching` filter.
- Reachable wherever the evaluating context knows the source permanent — predicate evaluation
  (including a cast trigger's `spellFilter`, where the trigger's own source is supplied), dynamic
  amounts, and target resolution. A **null** resolution is the fail-closed reading of a declined
  imprint: a `sharing…With` predicate matches nothing and an `EntityProperty` reads 0, so a permanent
  that exiled no card grants and punishes nothing. Classified `LkiPolicy.LIVE_ONLY` — an exiled card
  is never on the battlefield, so its characteristics are the printed ones and there is no snapshot
  to fall back to.
- For *counting* a pile rather than reading one card's characteristics, prefer the existing
  `ContextPropertyKey.LINKED_EXILE_CARD_COUNT` / `LINKED_EXILE_DISTINCT_CARD_TYPE_COUNT`; for the
  pile's colours as a mana choice, `ManaColorSet.AmongLinkedExiledCards`; for its abilities,
  `HasAllActivatedAbilitiesOfCards(DonorCards.LINKED_EXILE)`.

#### Pipeline values inside target filters (`powerAtMostEntity`/`powerLessThanEntity` + `AmassedArmy`)

A target filter can compare each candidate against a **resolution-time pipeline value** — the
Army just amassed by a sibling/action effect, or any cost-chosen entity. The plumbing:

- `PredicateContext` carries `storedCollections` (the pipeline's `storedCollections`, threaded
  by `PredicateContext.fromEffectContext`). `PredicateEvaluator.resolveEntityReference` resolves
  `EntityReference.AmassedArmy` / `FromCostStorage` from it (mirroring
  `TargetResolutionUtils.resolveEntityReference`), instead of returning null.
- `PredicateContext` also carries the spell/ability's chosen `targets`, and
  `resolveEntityReference` resolves `EntityReference.Target(i)` from them — so a **resolution-time
  group filter** can be relative to a cast-time target. This is the Radiance shape: `Effects.X(target)
  then Patterns.Group.xAll(GroupFilter(Creature.sharingColorWith(EntityReference.Target(0))).otherThanTarget())`
  (Cleansing Beam, Surge of Zeal, Wojek Siren, Rally the Righteous, Leave No Trace). The target is
  acted on directly rather than folded into the group because a colorless target shares a color with
  nothing, not even itself. During target *enumeration* nothing has been chosen yet, so a target
  filter that names `Target(i)` still sees null there.
- `TargetFinder.findLegalTargets(..., pipelineContext = …)` accepts the resolving effect's
  `PredicateContext` and folds it into the per-candidate context, so **target enumeration** sees
  the pipeline. `ReflexiveTriggerEffectExecutor` passes it for deferred ("when you do, … target …")
  triggers, which is how Grishnákh filters its steal target.
- Pair with `.powerAtMostEntity(ref)` / `.powerLessThanEntity(ref)` / `.powerGreaterThanEntity(ref)`
  on the `TargetFilter`. With `ref = EntityReference.AmassedArmy`, after "amass Orcs 2" the legal
  targets exclude any creature with power > 2. This same plumbing unblocks Ent-Draught Basin's
  "target creature with power X"-style references that need a pipeline-known bound.

### Context-plumbed

- `ContextProperty(key)` — value plumbed via `EffectContext`. Keys include:
  - `TRIGGER_DAMAGE_AMOUNT` — damage in the current trigger payload (Tephraderm).
  - `TRIGGER_LIFE_GAINED` / `TRIGGER_LIFE_LOST` — life delta from a `LifeChangedEvent`.
  - `TRIGGER_COUNTERS_PLACED_AMOUNT` — counters placed in the triggering event (Simic Ascendancy).
  - `LAST_KNOWN_PLUS_ONE_COUNTER_COUNT` / `LAST_KNOWN_TOTAL_COUNTER_COUNT` — counters on the
    source as it last existed on the battlefield (Hooded Hydra / Shadow Urchin).
  - `ADDITIONAL_COST_EXILED_COUNT` — cost-step accumulator. (The blight-X amount moved to
    `DynamicAmount.CastChoice(ChoiceSlot.BLIGHT_AMOUNT)`.)
  - `TARGET_COUNT` — still-legal targets in the current effect context.
  - `TARGETS_TOTAL_MANA_VALUE` — the summed mana value of the objects targeted (CR 202.3); the
    summing sibling of `TARGET_COUNT`, read from the same target list. Player targets have no mana
    value and contribute nothing. Unusually for a context key it is also readable **while a spell is
    being cast**, which is what it exists for: an additional cost priced off the targets is
    determined at CR 601.2f, after the targets are announced at 601.2c and before it is paid at
    601.2h, so a cost carrying this key reads `CastSpell.targets`. See
    `Costs.additional.CollectEvidenceForTargetsTotalManaValue` (§ additional costs) — Urgent
    Necropsy. In a context with no targets it reads `0`.
  - `LINKED_EXILE_CARD_COUNT` / `LINKED_EXILE_DISTINCT_CARD_TYPE_COUNT` — cards / distinct
    types in the source's linked exile pile (Veteran Survivor / Keen-Eyed Curator).
  - `MODES_CHOSEN_ON_TRIGGERING_SPELL` — number of mode picks recorded on the cast that fired
    the trigger (Riku of Many Paths). Counts selections, not distinct modes, so Spree with
    the same mode twice reads as `2`.
  - `MANA_SPENT_ON_TRIGGERING_SPELL` — total mana spent to cast the spell that fired the
    trigger (Aberrant Manawurm's "+X/+0 ... where X is the amount of mana spent to cast that
    spell", Expressive Firedancer's "if five or more mana was spent"). Distinct from
    `DynamicAmount.TotalManaSpent`, which reads the *current resolving object's own* cast — this
    reads the **triggering** spell's cast (the payoff lives on a separate permanent). Populated
    from `SpellCastEvent.totalManaSpent`; `0` for non-cast triggers.
  - `COLORS_SPENT_ON_TRIGGERING_SPELL` — number of distinct *colors* of mana spent to cast the
    spell that fired the trigger (0–5; colorless is not a color, CR 105.1). The triggering-spell
    analogue of `DistinctColorsManaSpent` (which reads the resolving object's own cast, i.e.
    Converge): this reads the **triggering** spell's payment for a payoff on a separate permanent
    (Magmablood Archaic's "creatures you control get +1/+0 ... for each color of mana spent to cast
    that spell"). Populated from `SpellCastEvent.distinctColorsSpent`; `0` for non-cast triggers.
    Facade: `DynamicAmounts.colorsSpentOnTriggeringSpell()`.
  - `TRIGGERING_SPELL_MANA_VALUE` — mana value (CR 202.3) of the spell that fired the trigger
    (Kellan, the Kid — "a permanent spell with equal or lesser mana value"). Distinct from
    `MANA_SPENT_ON_TRIGGERING_SPELL` (mana actually paid): this is the spell's printed mana
    value, unaffected by cost reductions / alternative costs / X. Populated from
    `SpellCastEvent.manaValue`; `0` for non-cast triggers. Pair with
    `CollectionFilter.ManaValueAtMost(ContextProperty(TRIGGERING_SPELL_MANA_VALUE))` to bound a
    gathered collection by the triggering spell's mana value.
  - `X_VALUE_OF_TRIGGERING_SPELL` — value chosen for `{X}` on the spell that fired the trigger
    (CR 601.2b) — Geometer's Arthropod's "look at the top X cards of your library." Distinct from
    `MANA_SPENT_ON_TRIGGERING_SPELL` (total mana paid) and `TRIGGERING_SPELL_MANA_VALUE` (printed
    mana value, where {X} counts as 0). Populated from `SpellCastEvent.xValue`; `0` for non-cast /
    no-{X} triggers. Pair with `SpellCastPredicate.HasXInCost`. Facade:
    `DynamicAmounts.xValueOfTriggeringSpell()`.
  - `TRIGGER_SCRY_COUNT` — cards looked at by the scry **or surveil** that fired the trigger
    (Celeborn the Wise, Elrond Master of Healing). Equals the scry/surveil N parameter unless the
    library held fewer cards.
  - `TRIGGER_DISCARD_COUNT` — cards discarded in the batch that fired the trigger (CR 603.2c) —
    Magmakin Artillerist's "whenever you discard one or more cards, this creature deals **that much**
    damage to each opponent." Populated from `CardsDiscardedEvent.cardIds.size`, so one event of
    three cards reports `3` while three sequential single discards fire three triggers reporting `1`.
    Pair with `Triggers.YouDiscardOneOrMore`; `0` for non-discard triggers.
  - `TRIGGER_DISCOVER_VALUE` — the discover value N (mana-value threshold) of the discover that
    fired the trigger (CR 701.57) — Curator of Sun's Creation's "discover again for the same value."
    Pair with `Triggers.WheneverYouDiscover`; `0` for non-discover triggers.
  - `TRIGGER_EXCESS_DAMAGE_AMOUNT` — damage past lethal in the trigger payload (CR 120.4a).
    Set from `DamageDealtEvent.excessAmount`; non-zero only for `DealsDamageEvent(requireExcess = true)`
    triggers — Fall of Cair Andros' "amass Orcs X, where X is the excess damage."
  - `TRIGGER_RECIPIENT_TOUGHNESS` — the damage recipient creature's toughness at the instant the
    triggering damage was dealt (CR 603.10 last-known information — survives a lethal hit). Set from
    `DamageDealtEvent.targetToughnessAtDamage`; `0` for non-creature recipients. Compare against
    `TRIGGER_DAMAGE_AMOUNT` for "deals damage to a creature equal to that creature's toughness"
    (Taii Wakeen, Perfect Shot).
- `AdditionalCostBlightAmount` — X paid via the Blight additional cost.
- `ChosenNumber` — number a player chose via a Choose action.
- `VariableReference(name)` — named count variable stored earlier in the same resolution (e.g. a pipeline `storeCountAs`).
- `ColorsAmongPermanents(player)` — count of distinct colors among player's permanents.
- `DistinctEntitiesInCollections(collections)` — number of *distinct* entities across the named
  pipeline collections (union, de-duplicated by entity id). Facade: `DynamicAmounts.distinctEntitiesIn(vararg)`.
  For "you affected N *different* objects" payoffs spread over several resolution-time selections —
  e.g. Call the Spirit Dragons puts a +1/+1 counter on a chosen Dragon of each color (one `SelectTarget`
  per color, each stored under its own key) and wins if five *different* Dragons received counters, so a
  multicolored Dragon chosen for two colors counts once.
- `DistinctCardTypesInCollections(collections)` — number of *distinct card types* among the cards in
  the named pipeline collections (union, de-duplicated by card type; an artifact creature counts for
  two). Facade: `DynamicAmounts.distinctCardTypesIn(vararg)`. Cards are read by entity id, so counting
  stays correct after the cards move zones (e.g. after being discarded into a graveyard). For "draw a
  card for each card type among cards discarded this way" (Kefka, Court Mage) — collection-scoped
  sibling of the `LINKED_EXILE_DISTINCT_CARD_TYPE_COUNT` context property and of
  `SpellsCastThisTurn(countDistinctCardTypes = true)`.
- `StoredCardManaValue(collectionName)` — mana value of the **first** card in a named pipeline
  collection (Erratic Explosion-style "that card's mana value").
- `ManaValueSumOfCollection(collectionName)` — **total** mana value of *every* card in a named
  pipeline collection. Facade: `DynamicAmounts.manaValueSumOf(collectionName)`. Cards are read by
  entity id, so the value stays correct after the collection has moved zones (e.g. milled into the
  graveyard). For "you mill X cards … that player loses life equal to the total mana value of those
  cards" — Palantír of Orthanc mills into the default `"milled"` collection
  (`Patterns.Library.mill(X)`), then `Effects.LoseLife(DynamicAmounts.manaValueSumOf("milled"), opponent)`.

### `ManaColorSet`<a id="manacolorset"></a>

Color analogue of `DynamicAmount` — pure data resolved at the moment a mana effect fires.
Used by `AddManaOfChoice(colorSet, amount)`; the engine's `ManaColorSetResolver` materializes
a `Set<Color>` from the source/controller/projected state, the player picks one (or the
solver picks if there's only one), and that color is added to the pool.

- `ManaColorSet.AnyColor` — all five colors. The "any-color" default.
- `ManaColorSet.Specific(colors)` — hand-authored fixed set (e.g., `{R, G}` for a Gruul producer).
- `ManaColorSet.CommanderIdentity` — union of color identities of every commander the controller has registered. Empty (no mana produced) in non-Commander formats.
- `ManaColorSet.AmongPermanents(filter)` — colors of permanents matching `filter`, read via projected state so type/color-changing effects are honored. Mox Amber shape.
- `ManaColorSet.LandsCouldProduce(scope)` — colors any land in `scope` could produce; tapped state and activation costs are ignored (CR 106.7). `scope` is `LandControllerScope.{YOU, OPPONENTS, ANY}`. Fellwar Stone / Exotic Orchard / Reflecting Pool shape.
- `ManaColorSet.SourceChosenColor` — the single color stored on the source's `ChosenColorComponent` (set via `EntersWithChoice(ChoiceType.COLOR)`). Uncharted Haven / Ashling Rekindled shape.
- `ManaColorSet.AmongLinkedExiledCards` — union of the base colors of the cards currently exiled *with* the source permanent — the ids in its `LinkedExileComponent` (set by `MoveToZoneEffect(linkToSource = true)`) that are still in the exile zone. A card that has since left exile drops out of the pool; colorless-only or empty piles produce no mana. Pit of Offerings shape ("any of the exiled cards' colors").

### `ManaRestriction`

Spending restrictions attached to a unit of mana when it is added to the pool. Used by
`AddMana`, `AddColorlessMana`, and `AddManaOfChoice` (via the `restriction` parameter).
When the engine pays a spell's cost, restricted mana is consumed preferentially when its
restriction matches the spell context.

- `ManaRestriction.AnySpend` — no restriction; satisfies any spend. Used internally when
  `AddManaOfChoice(riders = ...)` is provided without an explicit restriction, so the rider
  set survives in the pool without limiting where the mana can be spent (Path of Ancestry).
- `ManaRestriction.InstantOrSorceryOnly` — only instants and sorceries.
- `ManaRestriction.KickedSpellsOnly` — only kicked spells.
- `ManaRestriction.CreatureSpellsOnly` — only creature spells.
- `ManaRestriction.SpellsWithManaValueAtLeast(minManaValue, orXInCost?, creatureOnly?)` — the
  mana-value gate, parameterized over threshold and the two printed qualifiers.
  `orXInCost = true` adds "or spells with {X} in their mana costs" (such a spell qualifies whatever
  its mana value, and the mana may pay any part of its cost); `creatureOnly = true` narrows both
  clauses to creature spells. Ability activations never satisfy it.
  `SpellsWithManaValueAtLeast(4)` — Ashling, Rimebound;
  `SpellsWithManaValueAtLeast(4, orXInCost = true, creatureOnly = true)` — Helga, Skittish Seer;
  `SpellsWithManaValueAtLeast(5, orXInCost = true)` — Troyan, Gutsy Explorer.
- `ManaRestriction.LegendarySpellsOnly` — only legendary spells (matches `SpellPaymentContext.isLegendary`,
  populated from the cast card's `typeLine.isLegendary`). Great Hall of the Citadel
  (`AddManaInAnyCombination(2, restriction = LegendarySpellsOnly)`); Delighted Halfling pairs it with
  the `ManaSpellRider.MakesSpellUncounterable` rider on a one-mana any-color ability.
- `ManaRestriction.SubtypeSpellsOrAbilitiesOnly(subtype, creatureOnly?)` — Cavern of Souls /
  Unclaimed Territory: only spells of a baked subtype, optionally creature-only.
- `ManaRestriction.SubtypeSpellsOnly(subtypes)` — multi-subtype spend restriction: only spells
  whose type line carries **any** of the given subtypes (OR-joined). Spell-only (no ability
  variant). Maelstrom of the Spirit Dragon: `SubtypeSpellsOnly(setOf("Dragon", "Omen"))`
  ("a Dragon spell or an Omen spell").
- `ManaRestriction.CastFromExileOnly` — only spells cast from exile.
- `ManaRestriction.CastFromNonHandOnly` — only spells cast from anywhere other than
  hand (exile, graveyard, top of library, command zone, …). Mm'menon, the Right Hand's
  granted artifact mana ability. Generalizes `CastFromExileOnly` by allowing all non-hand
  origins instead of exile alone; rejects ability activations.
- `ManaRestriction.CardTypeSpellsOrAbilitiesOnly(cardType, allowSpells?, allowAbilities?, negated?)` —
  Steelswarm Operator shape. Use `cardType = CardType.ENCHANTMENT, allowSpells = true` for
  "spend only to cast an enchantment spell." `negated = true` flips the type test for the
  "non[type]" wordings — The Emperor of Palamecia's "Spend this mana only to cast a noncreature
  spell" is `CardTypeSpellsOrAbilitiesOnly(CardType.CREATURE, negated = true)`; only the type
  membership is negated, the allowSpells/allowAbilities gating is unchanged.
- `ManaRestriction.AbilityActivationOnly` — only ability activations (any activated ability of
  any source). Satisfied by `SpellPaymentContext.isAbilityActivation`; unlike
  `CardTypeSpellsOrAbilitiesOnly`, the ability's source card type doesn't matter. Compose with
  `AnyOf` for "... or to activate an ability" clauses — Purple Dragon Punks:
  `AnyOf(CardTypeSpellsOrAbilitiesOnly(ARTIFACT, allowSpells = true, allowAbilities = false), AbilityActivationOnly)`
  ("spend only to cast an artifact spell or to activate an ability").
- `ManaRestriction.EquipAbilityActivationOnly` — only **equip** ability activations (CR 702.6): the
  strict narrowing of `AbilityActivationOnly` to abilities the engine flags
  `ActivatedAbility.isEquipAbility`, which covers "Equip [quality]" (CR 702.6c), "Equip planeswalker"
  (CR 702.6e) and non-mana "Equip—[cost]" variants. Satisfied by
  `SpellPaymentContext.isEquipAbilityActivation`, set by `buildAbilityPaymentContext` — the one
  builder every ability-activation path funnels through. Do **not** reach for
  `SubtypeSpellsOrAbilitiesOnly("Equipment")` for this wording: that admits *every* activated ability
  of an Equipment source, so it also pays Iron Man Armor's "{2}: … it becomes a 0/0 Construct Hero
  artifact creature" (or Batterskull's "{3}: Return this Equipment to its owner's hand"). A card type
  can't separate them either — the equip ability and the other ability share one source. Ronin,
  Shadow Stalker's "Spend this mana only to cast Equipment spells or activate equip abilities" is
  `AnyOf(SubtypeSpellsOnly(setOf("Equipment")), EquipAbilityActivationOnly)`, and Freya Crescent's
  identically-meant "…an Equipment spell or activate an equip ability" is the same expression — it
  shipped on the over-broad spelling before this atom existed and was converged onto it.
- `ManaRestriction.TurnPermanentsFaceUpOnly` — only the turn-face-up special action (disguise/
  morph face-up). Satisfied by `SpellPaymentContext.isTurnFaceUpAction`; the turn-face-up handler/
  enumerator pass that context so restricted mana in the pool is consumed. Overgrown Zealot,
  Creeping Peeper.
- `ManaRestriction.UnlockDoorOnly` — only the unlock-a-door special action (CR 709.5e).
  Satisfied by `SpellPaymentContext.isUnlockDoorAction`; the unlock-room handler/enumerator pass
  that context. Creeping Peeper (inside `AnyOf`).
- `ManaRestriction.FaceDownSpellsOnly` — only spells **cast face down** for a morph (CR 702.37a)
  or disguise (CR 702.168a) cost. Satisfied by `SpellPaymentContext.isFaceDownCast`, which every
  face-down cast path sets via `SpellPaymentContext.faceDownCast()` — the CR 708.2 shape (nameless
  colorless 2/2 creature spell, mana value 0), *not* the printed card's characteristics. Does not
  cover cloak or manifest: nothing there is cast. The turn-face-up half of "cast face-down spells
  or turn creatures face up" is the separate `TurnPermanentsFaceUpOnly` atom (a face-down permanent
  is always a creature, so "permanents" and "creatures" coincide). Tin Street Gossip:
  `AnyOf(FaceDownSpellsOnly, TurnPermanentsFaceUpOnly)`.
- `ManaRestriction.CannotCastSpellsOtherThan(cardTypes)` — the family's only **negative**
  restriction: "This mana can't be spent to cast a non[type] spell" (Hydraulic Helper —
  `CannotCastSpellsOtherThan(setOf(CardType.ARTIFACT))`). Every other variant is a whitelist
  ("spend this mana *only* to …") and therefore blocks anything not named; this one blocks exactly
  one thing — casting a spell lacking one of `cardTypes` — and leaves every other spend legal:
  activating an ability, a ward cost, an "unless that player pays {2}" tax, a cost demanded during
  resolution, turning a permanent face up. Reach for it whenever the printed text says "can't be
  spent to" rather than "spend only to"; the positive composition
  `AnyOf(CardTypeSpellsOrAbilitiesOnly(X), AbilityActivationOnly)` is strictly narrower and
  silently rejects the non-cast spends. Backed by `SpellPaymentContext.isSpellCast`.
- `ManaRestriction.AnyOf(restrictions)` — disjunction; the mana is spendable in any context that
  satisfies *any* listed restriction. Compose atomic restrictions for multi-option mana — e.g.
  Creeping Peeper's "cast an enchantment spell, unlock a door, or turn a permanent face up" is
  `AnyOf(CardTypeSpellsOrAbilitiesOnly(ENCHANTMENT), UnlockDoorOnly, TurnPermanentsFaceUpOnly)`.

### `ManaSpellRider`<a id="manaspellrider"></a>

Side-effects attached to mana that fire when the mana is spent on a spell. Orthogonal to
`ManaRestriction`: the restriction controls *where* the mana may be spent; the rider
controls *what happens to the spell* when it is spent. The cast pipeline either mutates the
spell directly (e.g. stamps a component) or queues a triggered ability onto the stack above
the spell when the rider needs the stack (typically because it requires a player decision).

Attach riders via the `riders` parameter of `AddMana`, `AddManaOfChoice` or
`AddManaOfSourceChosenSubtype`. The set of riders consumed by a payment is collected as a **list**,
not a set — multiplicity is load-bearing, since two rider-carrying mana spent on one spell must fire
the rider twice (Pyromancer's Goggles: "That many copies will be created").

- `ManaSpellRider.MakesSpellUncounterable` — Cavern of Souls: stamps `CantBeCounteredComponent`
  on the spell at cast time.
- `ManaSpellRider.ScryOnSharedTypeWithCommander(amount)` — Path of Ancestry: if the spell is
  a creature spell that shares a creature type with any of the controller's commanders,
  queues a `scry amount` triggered ability above the spell.
- `ManaSpellRider.CopySpellWhenSpent(spellFilter)` — Pyromancer's Goggles
  (`GameObjectFilter.InstantOrSorcery.withColor(Color.RED)`): if the cast spell matches
  `spellFilter`, queues a `CopyTargetSpellEffect(TriggeringEntity)` triggered ability **above** the
  spell, so the copy resolves first (CR 707.10) and its controller may choose new targets. Matching
  happens at payment time against the spell's cast characteristics; a non-matching spell is a silent
  no-op. Because the queued trigger's source is the *spell*, it still fires if the mana's producer
  has already left the battlefield.
- `ManaSpellRider.GrantsKeywordWhenSpent(keyword, spellFilter)` — Carnelian Orb of Dragonkind
  (`Keyword.HASTE`, `GameObjectFilter.Creature.withSubtype("Dragon")`): "If that mana is spent on a
  [filter] spell, it gains [keyword] until end of turn." The one rider that puts **nothing** on the
  stack — the printed effect is continuous, so it floats a `Layer.ABILITY` `GrantKeyword`
  `Duration.EndOfTurn` modification keyed to the spell's entity id instead of queuing a trigger. A
  permanent spell keeps that id as it resolves, so the keyword is live the moment the permanent
  exists (which is what haste needs); on a non-permanent spell the grant never finds a permanent to
  apply to. Matching happens at payment time against the spell's cast characteristics, per the
  printed rulings: mana spent on a non-Dragon spell that *becomes* a Dragon later in the turn grants
  nothing, and an instant or sorcery that makes Dragon tokens is not a Dragon creature spell.

### `ManaExpiry`<a id="manaexpiry"></a>

The *duration* axis of mana — when it leaves the pool — orthogonal to `ManaRestriction` (where it
may be spent) and `ManaSpellRider` (what happens to the spell). Passed via the `expiry` parameter
of `AddMana`. The engine empties pools at end of turn, so:

- `ManaExpiry.END_OF_TURN` — the default; ordinary mana cleared by the end-of-turn pool emptying.
- `ManaExpiry.END_OF_COMBAT` — firebending-style mana (CR 702.189): kept through combat, discarded
  by `CombatManager.endCombat` when the combat phase ends ("Any of this mana you still have as combat
  ends will be lost"). Stored as an `AnySpend` restricted-pool entry tagged with the expiry, so it
  spends like any other mana and the tag survives partial spends.

### `TurnTracker` keys (used with `TurnTracking`)

- `CREATURES_DIED` — creatures that died this turn.
- `ARTIFACTS_DIED` — artifacts (incl. tokens) put into a graveyard from the battlefield this turn,
  credited to each one's **last-known controller** (so a stolen artifact destroyed after the theft
  counts for the thief). Type is read off the last-known *projected* type line, so an animated
  artifact creature counts and a permanent that was only an artifact through a continuous effect
  counts while that effect applied. Read it with `Player.Each` for the **game-wide** total —
  `DynamicAmounts.artifactsDiedThisTurn()` defaults to exactly that, and is Anzrag's Rampage's X
  ("the number of artifacts that were put into graveyards from the battlefield this turn"). Every
  such artifact had exactly one controller, so the sum double-counts nothing; there is deliberately
  no separate game-scoped component.
- `NONTOKEN_CREATURES_DIED` — nontoken creatures that died this turn.
- `CREATURES_LEFT_BATTLEFIELD` — creatures (incl. tokens) that left the battlefield under the
  player's control this turn, regardless of destination (death, exile, bounce, …). The creature-scoped
  sibling of `PermanentLeftBattlefieldThisTurn`; broader than `CREATURES_DIED` (which is
  battlefield→graveyard only). Powers Kutzil's Flanker ("for each creature that left the battlefield
  under your control this turn"). Backed by `CreatureLeftBattlefieldThisTurnComponent`, credited to
  the last-known controller and cleared at end of turn.
- `OPPONENT_CREATURES_EXILED` — opponent creatures you exiled.
- `OPPONENTS_WHO_LOST_LIFE` — count of opponents who lost life.
- `DAMAGE_RECEIVED` — damage received by player.
- `DAMAGE_RECEIVED_FROM_ARTIFACTS` — damage dealt to the player this turn by artifact sources
  (a source that is an artifact when it deals the damage). Combat and non-combat both count;
  prevented damage does not. Powers Reverse Polarity ("twice the damage dealt to you so far this
  turn by artifacts") via `Multiply(TurnTracking(You, DAMAGE_RECEIVED_FROM_ARTIFACTS), 2)`.
- `LIFE_GAINED` — life gained this turn (Bre of Clan Stoutarm). Facade `DynamicAmounts.lifeGainedThisTurn(player)`.
- `LIFE_LOST` — indicator (0/1) that the player lost life this turn.
- `LIFE_LOST_AMOUNT` — *how much* life the player lost this turn: damage taken, life-loss effects
  and life paid as a cost all accumulate on `LifeLostAmountThisTurnComponent`. Life gained never
  nets against it (Rowan's ruling: gain 3 and lose 3 and the total is still 3). Facade
  `DynamicAmounts.lifeLostThisTurn(player)`; powers Rowan, Scion of War.
- `PLAYER_ATTACKED` — whether/how many times you attacked.
- `DEALT_COMBAT_DAMAGE` — combat damage dealt.
- `DEALT_COMBAT_DAMAGE_BY_LEGENDARY_CREATURE` — indicator (0/1) that the player was dealt combat
  damage by a legendary creature this turn (recorded in `CombatDamageManager`, cleared at end of
  turn). Powers "an opponent was dealt combat damage by a legendary creature this turn" — Blitzball —
  via `Compare(TurnTracking(EachOpponent, DEALT_COMBAT_DAMAGE_BY_LEGENDARY_CREATURE), GTE, 1)`
  (facade `Conditions.AnOpponentWasDealtCombatDamageByLegendaryCreatureThisTurn`).

For a *combat-damage-amount threshold* that is existential over players — "a player was dealt N or
more combat damage this turn" — use the dedicated condition
`Conditions.aPlayerWasDealtCombatDamageThisTurnAtLeast(n)`
(`AnyPlayerDealtCombatDamageThisTurnAtLeast`), NOT a `TurnTracking` tracker. It reads a per-player
running total (`CombatDamageReceivedThisTurnComponent`, accumulated at the two combat-damage-to-a-
player sites in `CombatDamageManager` and cleared at the turn boundary) and returns true when *some
single* player crossed the threshold. The tracker path would instead sum every player's combat
damage together (`TurnTracking(Player.Each, …)` sums), which would falsely satisfy the threshold on
the combined total. Backs Sidequest: Play Blitzball ("if a player was dealt 6 or more combat damage
this turn").
- `COUNTERS_PUT_ON_CREATURE` — counters placed.
- `LANDS_PLAYED` — lands the player explicitly played this turn (from-hand land drops only,
  derived from `LandDropsComponent`).
- `LANDS_ENTERED_UNDER_CONTROL` — lands that entered the battlefield under the player's
  control this turn. Counts *every* land ETB regardless of how it arrived (land drops,
  Lander-token search, Cultivate-style "put a land onto the battlefield" effects,
  opponent-gift effects), so it differs from `LANDS_PLAYED`. Backs
  `DynamicAmounts.landsEnteredUnderControlThisTurn(player)` — e.g. Bioengineered Future's
  "for each land that entered the battlefield under your control this turn."
- `NONLAND_PERMANENTS_ENTERED` — the complement of `LANDS_ENTERED_UNDER_CONTROL` over the same
  per-player entry log: nonland permanents that entered the battlefield under the player's control
  this turn. Tokens count; a permanent that is both a land and a creature does not (it's a land).
  One count per *entry event*, so a permanent that leaves and re-enters counts twice (CR 400.7 — a
  new object each time), and an entry stays counted after the permanent leaves or changes
  controller. Backs `DynamicAmounts.nonlandPermanentsEnteredUnderControlThisTurn(player)` and,
  at a threshold of two, the **Celebration** ability word — see `Conditions.Celebration`.
- `CREATURES_ENTERED_UNDER_CONTROL` — the creature-typed slice of the same per-player entry log
  (an entry counts if it was a creature at the moment it entered). Same per-*entry-event* counting
  and post-departure persistence as `NONLAND_PERMANENTS_ENTERED`. At a threshold of two it backs
  `Conditions.CreaturesEnteredThisTurn` — Spider-UK's "two or more creatures entered the
  battlefield under your control this turn."
- `TOKENS_CREATED` — tokens the player created this turn, recorded only after replacement effects
  produce an actual token. Creator-scoped rather than controller-scoped: a token you create under
  another player's control increments your tally. The history remains after the token leaves play.
  `Conditions.PlayerCreatedTokensThisTurn(player, atLeast)` is the generic threshold facade;
  `Conditions.YouCreatedTokensThisTurn` is its common one-token form.
- `FOOD_SACRIFICED` — Food tokens sacrificed.
- `ARTIFACT_SACRIFICED` — indicator (0 or 1) that the player sacrificed an artifact this turn, read
  off the projected type line at sacrifice time. Backs `Conditions.SacrificedArtifactThisTurn`
  (Suspicious Detonation, Furtive Courier).
- `CARDS_LEFT_GRAVEYARD` — cards leaving your graveyard.
- `DESCENDED` — number of times a player has descended this turn (CR 700.11) — i.e.
  count of nontoken permanent cards put into that player's graveyard from any zone.
  Backs `Conditions.YouDescendedThisTurn(atLeast)` and `DynamicAmounts.descendedThisTurn`
  (descend N / fathomless descent ability words).
- `CREATURE_CARDS_PUT_INTO_GRAVEYARD` — number of creature cards put into a player's graveyard from
  any zone this turn; the creature-typed sibling of `DESCENDED`, recorded by the same
  `ZoneTransitionService` hook, keyed on the card's owner, tokens excluded. Backs
  `Conditions.CreatureCardPutIntoYourGraveyardThisTurn(atLeast)` (Macabre Reconstruction).
- `CARDS_DRAWN` — number of cards a player has drawn this turn (backed by
  `CardsDrawnThisTurnComponent`, reset to 0 for every player at turn start). Powers
  characteristic-defining stats like Duelist of the Mind's "power is equal to the number of
  cards you've drawn this turn" via `dynamicPower = CharacteristicValue.dynamic(TurnTracking(You, CARDS_DRAWN))`.
- `CARDS_PUT_INTO_EXILE` — number of cards put into exile this turn, keyed on each card's owner
  (backed by `CardsPutIntoExileThisTurnComponent`, incremented at the central zone-transition for
  any non-token card entering exile from another zone, reset to 0 for every player at turn start).
  Summed across all players (via `Player.Each`) it gives the game-wide count of cards put into
  exile this turn. Powers Ennis, Debate Moderator's "if one or more cards were put into exile this
  turn" — see the `Conditions.CardsPutIntoExileThisTurn(atLeast)` wrapper.
- `PERMANENTS_SACRIFICED` — number of permanents a player has sacrificed this turn (controller-scoped,
  any permanent type). Backed by the per-player `PermanentsSacrificedThisTurnComponent`, incremented at
  the central sacrifice hook (`ZoneTransitionService.trackPermanentSacrifice`) and reset to 0 for every
  player at turn start. Distinct from the game-wide `GameState.permanentsSacrificedThisTurn` cost-reduction
  counter (which sums every player's sacrifices). Backs `Conditions.YouSacrificedPermanentsThisTurn(atLeast)`
  and `DynamicAmounts.permanentsSacrificedThisTurn(player)` — e.g. Sawblade Skinripper's "if you sacrificed
  one or more permanents this turn, ... deals that much damage".
- `DAMAGE_SOURCES` — the number of distinct sources a player controlled that dealt damage this turn.
  Backed by the per-player `DamageSourcesThisTurnComponent`, a set of
  `DamageSourceIdentity(entityId, incarnation)` recorded on the source's controller *at damage time*
  (from `DamageUtils.trackDamageSourceForController`, called from the noncombat damage chokepoint and
  from each combat-damage path) and cleared at end of turn. `incarnation` is the source's
  battlefield-entry timestamp, which is what makes a blinked permanent a second source (CR 400.7)
  while a permanent that deals damage repeatedly stays one. Backs
  `Conditions.SourcesYouControlledDealtDamageThisTurn(atLeast)` — Case of the Burning Masks.
- `CARDS_IN_HAND_AT_TURN_START` — how many cards the player had in hand **at the beginning of the
  current turn**. The one entry here that is a *snapshot* rather than an accumulator: it is written
  for every player in `BeginningPhaseManager.performUntapStep` (CR 502, the turn's first turn-based
  action) and overwritten there each turn, backed by `CardsInHandAtTurnStartComponent`. The untap
  step rather than `TurnManager.startTurn` because no player gets priority during untap (CR 502.3),
  so the value is still literally "at the beginning of this turn" when an upkeep ability reads it —
  and because untap also runs on the game's first turn, so the snapshot is never missing. Wrapped by
  `Conditions.YouHadNoCardsInHandAtTurnStart`, which backs **Mindstorm Crown**. Do not reach for
  `Conditions.EmptyHand` for these wordings: that reads the hand *now*, and resolves differently on
  any turn where something touched the hand before the upkeep.
- `RED_NONCOMBAT_DAMAGE_DEALT` — total noncombat damage red sources a player controlled dealt this turn
  (controller-scoped). Backed by the per-player `RedNoncombatDamageDealtThisTurnComponent`, incremented in
  `DamageUtils.dealDamageToTarget` on the source's controller whenever a red source deals positive noncombat
  damage, and reset to 0 at end of turn. Backs `Conditions.YouDealtRedNoncombatDamageThisTurn(atLeast)` —
  Temple of Power's transform gate (back of Ojer Axonil, Deepest Might).

`SubtypeEnteredUnderControlThisTurn(player, subtypes, excludeTriggeringEntity?)` /
`DynamicAmounts.subtypeEnteredUnderControlThisTurn(subtype, player?, excludeTriggeringEntity?)` /
`DynamicAmounts.subtypesEnteredUnderControlThisTurn(subtypes, player?, excludeTriggeringEntity?)` —
"the number of [other] [subtype]s that entered the battlefield under [player]'s control this turn"
(Geralf, the Fleshwright — "each other Zombie that entered the battlefield under your control this
turn"). It's a **turn-history** count: backed by `PermanentsEnteredUnderControlThisTurnComponent`,
which records each entrant's subtypes (from projected state) at entry, so a permanent that has since
left the battlefield or lost the type still counts. `excludeTriggeringEntity = true` drops the
permanent whose entry triggered the ability (giving "each *other*"); because every simultaneous
entrant is recorded before the triggers resolve, each one sees the others (2024-04-12 ruling).
`subtypes` is a **set with any-of semantics**, so the printed "Mounts and/or Vehicles" wording
(Cloudspire Coordinator, via the plural facade) counts each qualifying entry exactly once — summing
two single-subtype amounts would double-count a permanent carrying both. The singular facade is the
ordinary one-tribe case.

---

## 14. Modal & choice

### Modal spells

```kotlin
spell {
    modal(chooseCount = 1) {
        mode("Destroy a creature") {
            val c = target("creature", Targets.Creature)
            effect = Effects.Destroy(c)
        }
        mode("Draw a card") {
            effect = Effects.DrawCards(1)
        }
    }
}
```

- `modal(chooseCount = N) { ... }` — N modes picked at cast time (or resolution for Commands).
- `mode(description) { ... }` — one option with its own targets/effect.
- `.requiresTarget(filter)` — mode needs a target matching filter.
- `.optional()` — mode can be skipped.
- `Mode.noTarget(...)` — explicit target-less mode (outer targets are preserved).

`ModalEffect.chooseOne { mode(...) }` and `ModalEffect.chooseN(n) { ... }` for explicit modal effects.

**Dynamic "choose up to X"** — `ModalEffect.chooseUpToDynamic(dynamicMax, *modes, allowRepeat = false)`
caps the pick count by a `DynamicAmount`, evaluated as the ability goes onto the stack for a
triggered ability and at resolution for an activated one. This factory sets no
`dynamicMinChooseCount`, so the floor stays `0` (the player may always decline); `chooseCount`
becomes `min(eval, modes.size)` — or just `eval`, uncapped by the mode list, when
`allowRepeat = true` lets one mode fill every pick.
If the evaluated cap is `0` no mode is chosen and nothing happens. Used by Riku of Many Paths,
where the cap is `ContextProperty(MODES_CHOSEN_ON_TRIGGERING_SPELL)`. Equivalent raw shape:
`ModalEffect(modes, chooseCount = modes.size, minChooseCount = 0, dynamicChooseCount = …)`.
The cap is any `DynamicAmount` — e.g. **Bumi, King of Three Trials** uses
`DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.Any.withSubtype(Subtype.LESSON))`
("choose up to X, where X is the number of Lesson cards in your graveyard"). Modes may carry their
own per-mode targets here just as in a fixed modal — each chosen mode's target (referenced as its
mode-local `EffectTarget.ContextTarget(0)`) is only demanded when that mode is picked (Bumi's scry
mode targets a player, its Earthbend mode targets a land).
The cap may also come from something the *same resolution* just did rather than from board state:
**Hawkeye, Master Marksman** feeds it `DynamicAmounts.timesPaid()`, the repetition count of the
`Effects.PayRepeatedly` that formed the action half of its reflexive trigger ("you may pay {1} up to
three times. When you do, choose up to **that many** —"). That works because the count rides across
the CR 603.12 stack round-trip in the reflexive ability's carried pipeline, and the trigger-time
evaluation site reads the pipeline (see `EffectContext.forTriggeredAbility`).

**Cast-time mode-selection UX (Spree / "choose one or more").** A choose-N modal *spell* cast
by a human is presented as a **single mode-selection panel** (web client), not a sequential
one-mode-at-a-time prompt. The enumerator emits one `CastSpellModal` legal action carrying a
`modalEnumeration` payload (each mode's description, `+ {cost}`, availability, and target
requirements); the client's cast pipeline opens the panel from that payload, lets the player
toggle the mode subset (respecting `minChooseCount`/`chooseCount`, and a count stepper when
`allowRepeat`), shows the live combined additional/total mana cost, and submits a `CastSpell`
with `chosenModes` populated but **targets deferred**. The engine then drives per-mode
on-battlefield target selection (`CastSpellHandler` pauses via the existing
`CastModalTargetSelectionContinuation`) before cost payment — so `validate()`/`execute()` accept
"modes chosen, targets deferred" as a legitimate intermediate cast state. Server-synthesized
free casts (Cascade, Sunbird's Invocation) and the AI still use the sequential server-side
mode-selection pause; choose-1 modal spells remain client-local `CastSpellMode` actions. No SDK
change is needed to author a Spree card — it is a plain `ModalEffect` with per-mode
`additionalManaCost` (see Trash the Town).

**Escalate (CR 702.120a).** Set `additionalManaCostPerExtraMode` on `ModalEffect`, or pass it to the `modal(...)`
builder, for “Pay this cost for each mode chosen beyond the first.” Unlike Spree's per-mode
`additionalManaCost`, no printed mode carries the cost: selecting one mode adds nothing, selecting two
adds the cost once, and selecting three adds it twice. The server includes the field in the modal legal
action so the existing single-panel selector previews the combined additional and total mana costs.

```kotlin
spell {
    modal(chooseCount = 3, minChooseCount = 1, additionalManaCostPerExtraMode = "{1}") {
        mode("First mode") { effect = firstEffect }
        mode("Second mode") { effect = secondEffect }
        mode("Third mode") { effect = thirdEffect }
    }
}
```

**Escalate with a non-mana cost.** *"Escalate—Discard a card"* (Collective Brutality),
*"Escalate—Tap an untapped creature you control"* (Collective Effort). Pass the payable thing as a
`CostAtom` to `additionalCostPerExtraMode` instead:

```kotlin
spell {
    modal(chooseCount = 3, minChooseCount = 1, additionalCostPerExtraMode = CostAtom.Discard(1)) {
        mode("First mode") { effect = firstEffect }
        …
    }
}
```

The engine charges it as **one scaled cost** — `atom.repeated(chosenModes.size - 1)`, so three modes
owe `Discard(2)` — because every additional-cost payment channel is a flat list and two `Discard(1)`
entries would both be satisfied by the same card. `CostAtom.repeated(times)` is the general
"pay this cost N times over" operation (`scripting/costs/CostAtoms.kt`); it is exhaustive over the
atom vocabulary, so a new atom is a compile error rather than a silent no-op.

The cast surface follows: `CastSpellEnumerator` caps the offered `chooseCount` at
`1 + (candidates / per-mode selection)` — one spare card in hand means at most two modes — and puts
the per-extra-mode cost on `modalEnumeration.additionalCostPerExtraMode`. The client's mode panel
names it, then an injected `escalateCost` pipeline phase opens the ordinary picker for that cost
type with the count scaled by the modes just chosen. Supported cost shapes are the selection-bearing
ones (`SelectionCostPresentation`): sacrifice, discard, tap, bounce, exile from graveyard; any other
atom caps the spell at one mode rather than offering a mode that can never be paid for.

**Tiered (CR 702.183) — `spell { tiered { } }`.** *"Tiered (Choose one additional cost.)"* is a
choose-**one** modal spell where each tier carries its own additional mana cost, paid as you cast
the spell (702.183a: *"Choose one. As an additional cost to cast this spell, pay the cost associated
with that mode."*). Exactly one tier is chosen and only that tier's (usually scaled) effect resolves.
Mechanically it is Spree constrained to a single mode: a `ModalEffect` with `chooseCount = 1`,
`minChooseCount = 1`, and per-mode `additionalManaCost` — **no engine change** beyond Spree, because
the choose-1 enumerator path (`CastSpellEnumerator.computeModeEnumeration`) already folds the chosen
tier's cost into each `CastSpellMode` action's effective cost, and `CastSpellHandler` already adds it
on execute. So the cast surface is the standard choose-1 modal flow: one `CastSpellMode` legal action
per *affordable* tier (unpayable tiers aren't offered), each showing its full mana cost. The
`tiered { }` builder is authoring sugar only:

```kotlin
spell {
    tiered {
        tier("Fire", "{0}", "Fire Magic deals 1 damage to each creature.") {
            effect = Patterns.Group.dealDamageToAll(1, GroupFilter.AllCreatures)
        }
        tier("Fira", "{2}", "Fire Magic deals 2 damage to each creature.") {
            effect = Patterns.Group.dealDamageToAll(2, GroupFilter.AllCreatures)
        }
        tier("Firaga", "{5}", "Fire Magic deals 3 damage to each creature.") {
            effect = Patterns.Group.dealDamageToAll(3, GroupFilter.AllCreatures)
        }
    }
}
```

`tier(name, cost, text) { … }` builds one `Mode` with `additionalManaCost = cost` and a button label
of `"<name> — <text>"`; inside the block, set `effect` and (for targeted tiers) `target`, exactly
like `mode { }`. Use `"{0}"` for a free base tier. Tiered grants no behavior of its own beyond the
modal-with-additional-cost shape, so there is **no `Keyword.TIERED`** (mirroring Spree); print the
reminder via the card's `oracleText` (the `TIERED_REMINDER` constant holds the canonical string). See
Fire / Ice / Thunder / Restoration Magic, Tifa's / Vincent's Limit Break (FIN).

**Cast-time conditional choose count** — for modal *spells* the same `dynamicChooseCount`
field is evaluated against the battlefield **at cast time**, and — unlike `chooseUpToDynamic` — it
keeps a floor instead of forcing `0`. Both ends are clamped to `[minChooseCount, modes.size]`. This
models "Choose one. If [condition] as you cast this spell, you **may** choose two instead."
(Flame of Anor): pass it via the DSL with
`modal(chooseCount = 2, minChooseCount = 1, dynamicChooseCount = …) { … }`. Combine a
`DynamicAmount.Conditional` with a control condition, e.g.

```kotlin
modal(
    chooseCount = 2,
    minChooseCount = 1,
    dynamicChooseCount = DynamicAmount.Conditional(
        condition = Conditions.YouControlAtLeast(1, GameObjectFilter.Creature.withSubtype("Wizard")),
        ifTrue = DynamicAmount.Fixed(2),
        ifFalse = DynamicAmount.Fixed(1)
    )
) { /* modes */ }
```

**…and the mandatory variant.** `dynamicMinChooseCount` is the same thing for the *lower* bound,
because the printed wording splits. "You **may** choose two instead" leaves the floor at one — set
`dynamicChooseCount` alone. "Choose both **instead**" does not — set both to the same
`DynamicAmount`, or the player pays the extra cost and then takes a single mode, which no printed
card allows. `teamworkModal { }` is the recipe for the Marvel Super Heroes teamwork modals and does
exactly that, deriving "both" from the modes declared.

> **One authority for the count.** `ModalChooseCounts.forCast` computes the `min..max` range, and
> both `CastSpellHandler` (validating a submitted cast) and `CastSpellEnumerator` (advertising one)
> call it. Keep it that way: when the enumerator had its own copy, it drifted — it knew nothing
> about the blight path or the floor, so it offered counts the handler rejected and dropped
> variants the handler would have taken. A mode with no legal target can't be chosen (CR 700.2a),
> so the enumerator drops a variant when fewer than `min` modes are available, unless `allowRepeat`
> lets one mode fill every pick (CR 700.2d).

> **`allowRepeat` applies at resolution time too.** The resolution-time picker (a modal reached with
> `chosenModes` empty — e.g. one inside an `OnEnterRunEffect`) narrows its option list after each
> pick so "choose two" means two *different* modes. With `allowRepeat` it does not: every mode stays
> on the menu for every pick, which is what "for each card exiled this way, put a +2/+0, +1/+1, or
> +0/+2 counter on it" (Frankenstein's Monster) needs — X independent choices among the same three.
> Without it the picks silently collapse once they outnumber the distinct modes.

**Modal triggered abilities (CR 603.3c / 700.2b).** *Every* modal triggered ability picks its
mode — and each chosen mode's targets, CR 603.3d — as the ability is put onto the stack, the same
moment a modal spell's caster does, never while it resolves. Two things depend on that timing:

- The opponent responds to a **known** mode. The ability reaches the stack with `chosenModes`
  populated, so its chosen mode text and per-mode targets are already in the client view
  (`ClientCard.chosenModeDescriptions` / `perModeTargets`) while there is still priority to act on.
- The chosen targets only "become targets" once the ability is on the stack, which is what ward
  (CR 702.21), "whenever this becomes the target of a spell or ability", and shroud/hexproof key on.

Consequences for authoring and testing:

- A mode whose mandatory target has no legal choice **isn't offered at all** — e.g. Hullbreaker
  Horror's "target spell you don't control" is absent when the only spell on the stack is your own,
  and Silent Hallcreeper's "another target creature you control" is absent when it is your only
  creature. If no mode is chosen, the ability is removed from the stack (CR 603.3c).
- A `chooseUpToDynamic` cap is evaluated **once**, against the state the ability goes onto the stack
  in, and then can't drift between picks (CR 601.2c via 603.3d). A cap of `0` means no mode is
  chosen, so the ability never reaches the stack.
- The per-source memory of `chooseOneNotYetChosen` / `chooseOneNotYetChosenThisTurn` is written when
  the ability goes onto the stack, not when it resolves. That is what makes two simultaneous triggers
  of the same source pick *different* modes (Breeches, Eager Pillager: both attack triggers announce
  their modes before either resolves).
- In a scenario test the mode / target decisions surface *before* the ability resolves, so the chosen
  mode's effect needs a further `resolveStack()`. The decision carries
  `DecisionPhase.TRIGGER`, not `RESOLUTION`.

`ModalEffectExecutor`'s resolution-time picker still serves modal **activated** abilities and a
`ModalEffect` **nested inside** another effect (a gated effect, a reflexive trigger, a pipeline step),
where the mode question isn't the ability's own. See `ModalTriggeredAbilityOnStackTest`.

**"Choose one that hasn't been chosen"** — `ModalEffect.chooseOneNotYetChosen(*modes)` for a
repeatable modal *ability* whose source remembers which modes it has already chosen across the
game and never offers them again (Gandalf the Grey). Equivalent raw shape:
`ModalEffect(modes, chooseCount = 1, excludePreviouslyChosenModes = true, countsAsModalSpell =
false)`. The engine records each chosen mode index in a per-source
`ChosenModesEverComponent` and excludes it from every later presentation of the effect; once all
modes have been chosen the ability has no legal mode (a triggered ability is removed from the stack,
CR 603.3c; an activated one resolves as a no-op). The memory is keyed
to the source object and persists while it remains the same object on the battlefield (CR 700.4) —
it resets if the permanent leaves and returns as a new object. Intended for triggered/activated
abilities on a persistent source, not one-shot modal spells.

```kotlin
triggeredAbility {
    trigger = Triggers.YouCastInstantOrSorcery
    effect = ModalEffect.chooseOneNotYetChosen(
        Mode.withTarget(/* tap or untap */, Targets.Permanent, "You may tap or untap target permanent"),
        Mode.noTarget(Effects.DealDamage(3, EffectTarget.PlayerRef(Player.EachOpponent)), "…"),
        Mode.withTarget(Effects.CopyTargetSpell(), Targets.InstantOrSorcerySpellYouControl, "…"),
        Mode.noTarget(Effects.PutOnTopOfLibrary(EffectTarget.Self), "…"),
    )
}
```

### Permanent enters-with-choice (Sieges)

```kotlin
EntersWithChoice(
    ChoiceType.MODE,
    modeOptions = listOf(
        ModeOption(id = "khans", label = "Khans", description = "...", iconKey = "khans"),
        ModeOption(id = "dragons", label = "Dragons", description = "...", iconKey = "dragons"),
    ),
)
```

- Writes `ChosenModeComponent(modeId)` on the permanent.
- Downstream triggers/conditions gate via `SourceChosenModeIs("khans")`.
- Icons live in `web-client/src/assets/icons/options/`.

**Other `ChoiceType`s** — `ChoiceType.COLOR` writes `ChosenColorComponent` (read by
`GrantChosenColor`), `ChoiceType.CREATURE_TYPE` writes `ChosenCreatureTypeComponent`,
`ChoiceType.CREATURE_ON_BATTLEFIELD` writes `ChosenCreatureComponent`,
`ChoiceType.BASIC_LAND_TYPE` writes `ChosenLandTypeComponent` (read by
`SetEnchantedLandTypeFromChosen` and `GrantLandwalkOfChosenType`), and
`ChoiceType.OPPONENT` writes an entity-id choice into the `CastChoicesComponent` under
`ChoiceSlot.OPPONENT` — read back via the `Player.ChosenOpponent` reference (e.g. Jihad's
anthem + state-trigger condition: `Exists(Player.ChosenOpponent, Zone.BATTLEFIELD, …)`), and
`ChoiceType.CARD_NAME` writes a chosen **card name** (presented as a searchable option list) into
the `CastChoicesComponent` under `ChoiceSlot.CARD_NAME` as a `ChoiceValue.TextChoice` — read back via
`chosenCardName()` or, for name-keyed static-ability filters,
`GameObjectFilter.namedFromChosenComponent()` (→ `CardPredicate.NameEqualsChosenComponent`, see §7).
The offered pool is controlled by `cardNamePool: CardNamePool` — `CardNamePool.LAND` (default) offers
every registered land name (Petrified Hamlet's "choose a land card name"); `CardNamePool.NONLAND`
offers every registered nonland name (Skyseer's Chariot's "choose a nonland card name");
`CardNamePool.ANY` offers every registered card name (Sorcerous Spyglass / Pithing Needle's "choose
any card name"). Set
`lookAtOpponentHand = true` to first reveal an opponent's hand to the controller as the permanent
enters, immediately before the choice (durable reveal via `RevealedToComponent`, correctly masked to
show only to the controller; purely informational — it never restricts the name chosen, so an empty
opposing hand still lets you name any card). Used by Petrified Hamlet ("When this land enters, choose
a land card name", then two statics — `PreventActivatedAbilities(nonManaAbilitiesOnly = true)` and
`GrantActivatedAbility` of a `{T}: Add {C}` mana ability — both filtered by
`namedFromChosenComponent()`) and Sorcerous Spyglass (`EntersWithChoice(ChoiceType.CARD_NAME,
cardNamePool = CardNamePool.ANY, lookAtOpponentHand = true)` +
`PreventActivatedAbilities(GameObjectFilter.Any.namedFromChosenComponent(), nonManaAbilitiesOnly = true)`), and
`ChoiceType.NUMBER` (set `minValue` / `maxValue`) writes a chosen number into the
`CastChoicesComponent` under `ChoiceSlot.CHOSEN_NUMBER` as a `ChoiceValue.NumberChoice` — read back
by a CDA via `DynamicAmount.CastChoice(CHOSEN_NUMBER)`. This is the *as-enters replacement* (CR
614.1c) form of a number choice — chosen before the permanent is on the battlefield, no priority
window at the default — versus `Effects.ChooseNumberForSource` (on resolution, e.g. from an upkeep
trigger) writing the same slot. Shapeshifter uses the replacement at entry and the effect each
upkeep: `replacementEffect(EntersWithChoice(ChoiceType.NUMBER, minValue = 0, maxValue = 7))` +
`SetBasePowerToughnessDynamicStatic(power = CastChoice(CHOSEN_NUMBER), toughness = Subtract(Fixed(7),
CastChoice(CHOSEN_NUMBER)))`. Example — Phantasmal Terrain
("As this Aura enters, choose a basic land type. Enchanted land is the chosen type."):

```kotlin
auraTarget = Targets.Land
replacementEffect(EntersWithChoice(ChoiceType.BASIC_LAND_TYPE))
staticAbility { ability = SetEnchantedLandTypeFromChosen }
```

Traveler's Cloak grants landwalk of the chosen type to the enchanted creature instead:

```kotlin
auraTarget = Targets.Creature
replacementEffect(EntersWithChoice(ChoiceType.BASIC_LAND_TYPE))
staticAbility { ability = GrantLandwalkOfChosenType() }
```

### Other choice effects

- `ChooseActionEffect(choices)` — pick one effect from a list.
- `ChooseColorThenEffect(whenChosen)` — pick a color, then apply a function of the color.
- `GrantHexproofFromChosenColorEffect(target)` / `GrantProtectionFromChosenColorEffect(target)` — atoms that run inside `ChooseColorThen` and read the chosen color from context (hexproof / protection from that color). Wrap in `ForEachInGroup` for "creatures you control gain protection from the chosen color" (Akroma's Blessing).
- `Effects.ForEachColorOf(source, effect)` — the **non-interactive sibling of `ChooseColorThen`**:
  runs `effect` once per color of the entity referenced by `source`, with that color set as the
  context's chosen color, so the same per-color atoms (`GrantProtectionFromChosenColor`,
  `GrantHexproofFromChosenColor`, `GrantCantBeBlockedByChosenColor`, …) compose inside it. Source
  colors come from projected state while the source is on the battlefield (Layer-5 / Devoid honored),
  else its base `CardComponent.colors` (LKI); a colorless source runs zero times (CR 105.2). For
  "[group] gain protection from each of `source`'s colors", wrap a group iteration in it —
  `Effects.ForEachColorOf(source, ForEachInGroupEffect(group, GrantProtectionFromChosenColor(Self)))`
  — and, when `source` is the about-to-leave permanent, place it before the exile/destroy step
  (`Composite(ForEachColorOf(…), Exile(…))`) so its colors are still readable (Éowyn, Fearless Knight).
- `ChooseCreatureTypeEffect(...)` — pause for creature-type selection.
- `Effects.NoteCreatureType(storeAs = "notedType", prompt?)` — "note a creature type that hasn't been noted for this <source>" (LTR — Long List of the Ents). Same decision shape as `ChooseOption(OptionType.CREATURE_TYPE)`, but the source's *current* `NotedCreatureTypesComponent.types` are excluded from the option list (so the player can't pick a duplicate), and on resolution the chosen type is appended to that component on the source AND stored in `chosenValues[storeAs]` for any downstream pipeline step. The component lives on the source permanent's container, so it disappears when the source leaves play (CR 400.7 — a permanent that changes zones becomes a new object with no memory of its previous existence). Use this whenever a card's text says "note … for this permanent"; use plain `ChooseOption(OptionType.CREATURE_TYPE)` when the choice is one-shot and doesn't need to accumulate.
- `Effects.SecretlyChooseCreatureType(options = emptyList(), storeAs = "notedType", prompt?)` — "Then secretly choose Human, Merfolk, or Goblin." (MKM — A Killer Among Us). The hidden-information sibling of `NoteCreatureType`, and the same `NoteCreatureTypeEffect` under the hood with `secret = true`: the type is noted on the source permanent exactly as above, but `NotedCreatureTypesComponent.secretTo` records *who* chose it, and two things key off that — the client view shows the note only to that player (badged "Chosen (secret)"; spectators never see it), and only that player can pay `Costs.RevealNotedCreatureType` (§ costs). This is CR 702.106a-b's hidden agenda — the piece of paper kept with the object — applied to a permanent, so a change of control neither hands the new controller the answer nor lets them reveal it. Pass `options` to narrow the choice to a named handful; the source's already-noted types are excluded from whichever set that is. Leave `options` empty for "secretly choose a creature type".
- `Effects.ChooseCardName(storeAs, prompt?, excludeBasicLandNames?)` — name a card (`ChooseOptionEffect(OptionType.CARD_NAME)`); the chosen name is stored in `chosenValues[storeAs]`. Options are every registry card name (searchable list, not free text); `excludeBasicLandNames` drops the five basics. Match cards by it with `GameObjectFilter.namedFromVariable(storeAs)`. (Desperate Research)
- `Effects.StoreCardName(from, storeAs)` — capture the name of the first card in collection `from` into `chosenValues[storeAs]`. The "choose a card, then act on cards of that name" counterpart to `ChooseCardName`. (Lobotomy)
- `SelectTargetEffect(...)` — pick from a valid target set.

---

## 15. Replacement effects

```kotlin
replacementEffect {
    condition = Conditions.YouControl(Filters.Swamp)
    effect = ReplacementEffect.PreventDamage(1)
}
```

All `ReplacementEffect` subtypes inherit the following virtual properties from the sealed interface:

- `restrictions: List<Condition>` (default empty) — when non-empty, the replacement only applies if every condition passes; a uniform gating mechanism used by `PreventDamage`, `DoubleDamage`, `ModifyLifeLoss`, `LifeLossFloor`, and others. In the `ReplacementEffectProcessor` (the draw domain today) each condition is evaluated with the **player the event affects** as `EffectContext.controllerId` — so `Player.You` inside a restriction reads as the drawing player, not the source's controller. The two coincide for a `Player.You` `appliesTo`; for `Player.EachOpponent` they don't, and such a card needs a source-relative condition instead.
- `optional: Boolean` (default `false`) — when `true`, the player affected by the event may decline the replacement (e.g. "you may draw a card instead").
- `activeZones: Set<Zone>` (default `{BATTLEFIELD}`) — the zones the effect functions from (CR 113.6), the replacement-effect twin of `TriggeredAbility.activeZones`. Declaring another zone is a *move*, not an addition: `EntersWithReplacements` sweeps the battlefield for `BATTLEFIELD` sources and every graveyard for `GRAVEYARD` ones, so `{GRAVEYARD}` switches the effect **on** in the graveyard and **off** on the battlefield. That is the whole of "as long as this creature is in your graveyard, …" (Dearly Departed) — no condition, no duration. Currently a constructor parameter on `EntersWithDynamicCounters` only; other subtypes take the interface default until a card needs otherwise. "You" for a graveyard source resolves to the graveyard's **owner** (a card outside the battlefield has no controller, CR 108.3), and the sweep visits every card, so two copies in one graveyard stack.
- `priorityGroup: ReplacementPriorityGroup` (default `ReplacementPriorityGroup.ANY`) — the CR 616.1a–f priority tier. Declared as an *override on the subtype*, never as a card-facing constructor parameter, so the engine processor never pattern-matches on SDK types and a card can't accidentally promote itself out of the affected player's 616.1e choice. Today only `EntersAsCopy` overrides it (`COPY`).

The priority groups are (CR 616.1a–f):

| Group | CR | Description |
|-------|-----|-------------|
| `SELF_REPLACEMENT` | 616.1a (→ 614.15) | An effect of a resolving spell or ability that replaces that same spell or ability's own effect. **Not** "affects its own source" — an as-it-enters modifier on a permanent is an ordinary CR 614.12 replacement and belongs in `ANY` |
| `CONTROL_CHANGE` | 616.1b | Control-changing effects |
| `COPY` | 616.1c | Copy effects |
| `TRANSFORM` | 616.1d | Replacements that cause entering with back face up |
| `ANY` | 616.1e | All others — affected player chooses freely |
| _(repeat)_ | 616.1f | After applying one effect, repeat until no more apply |

- `ReplacementEffect.PreventDamage(amount?, restrictions?, appliesTo)` — prevent damage matching the
  `EventPattern.DamageEvent` shape. `amount = null` prevents all; a number prevents up to that much.
  `restrictions: List<Condition>` (default empty) gates the prevention on extra conditions evaluated
  against the source's controller — the same pattern as `ModifyLifeLoss.restrictions`. Use it for
  "as long as …, prevent …" statics (Spirit of Resistance: a five-distinct-colors `Compare` gate).
- `ReplacementEffect.PreventDamageByRemovingCounter(counterType = PlusOnePlusOne, removalAmount = CounterRemovalAmount.One, requiresCounter = false, appliesTo = DamageEvent(recipient = Self))`
  — "If this creature would be dealt damage, prevent that damage and remove a +1/+1 counter from it"
  (Unbreathing Horde). The printed twin of the shield counter's prevention half (CR 122.1c), wired at
  the same two chokepoints (`DamageUtils.dealDamageToTarget` and
  `CombatDamageManager.applyShieldCountersToCombatDamage`) and inheriting its scoping: exactly **one**
  counter per damage *event*, however large the damage and however many counters are on the permanent —
  a creature blocking two attackers is dealt damage once (CR 510.2), and the first-strike and regular
  damage steps are two events. One deliberate difference from the shield counter: the prevention does
  **not** depend on having a counter to spend, because it is a printed ability rather than a rule made
  of counters. A separate type rather than a flag on `PreventDamage` — the counter removal is what the
  ability *is*, and it needs a state-returning application path where plain prevention is pure
  arithmetic. Only self-recipient patterns are honoured; a card shielding *other* permanents this way
  would need a battlefield-wide scan. A permanent carrying both this and a shield counter spends only
  the shield counter (once the shield prevents the damage there is nothing left to replace).
  Both printed rules above are **defaults**, and `removalAmount` / `requiresCounter` are the axes that
  invert them for **Magma Pummeler** ("If damage would be dealt to this creature *while it has a +1/+1
  counter on it*, prevent that damage and remove *that many* +1/+1 counters from it"):
  `CounterRemovalAmount.EqualToDamage` spends as many counters as the damage would have dealt, bounded
  by the counters present — damage above the count is still prevented in full, it simply has nothing
  left to remove — and `requiresCounter = true` makes the ability not apply at all with no counter, so
  the damage is dealt normally. In combat the amount read is the **per-target total** of that step's
  assignments, because CR 510.2 makes all combat damage one event: a creature double-blocked for 2 and
  3 loses five counters, not two then three.
  The removal emits its `CountersRemovedEvent` with `byDamagePrevention = true`, which is the "**this
  way**" scope `Triggers.countersRemovedFrom(byDamagePrevention = true)` matches — the Pummeler's
  reflexive "When one or more counters are removed from this creature this way, it deals that much
  damage to any target" must not fire for a counter paid as a cost or removed by an opponent. The
  payoff amount is `ContextPropertyKey.TRIGGER_COUNTERS_REMOVED_AMOUNT` (the removal mirror of
  `TRIGGER_COUNTERS_PLACED_AMOUNT`), i.e. the counters **removed** — not the damage that was prevented.
- `CapDamage(maxAmount, appliesTo)` — clamp matching damage to `maxAmount` (a *replacement* distinct
  from prevent/modify; applied after all amplification). Divine Presence: `CapDamage(3, DamageEvent(recipient = Any))`.
- `SetMinimumDamage(minAmount = 0, dynamicMinimum?, appliesTo)` — the **floor** mirror of `CapDamage`:
  raise matching damage *up to* a minimum (larger amounts unchanged; a zero would-be amount is not
  raised — a source only "deals damage" once it deals a positive amount). `dynamicMinimum` (a
  `DynamicAmount`, else the flat `minAmount`) is evaluated against the **replacement's source**
  permanent, like `ModifyDamageAmount.dynamicModifier`. Applied after all amplification/capping. Ojer
  Axonil, Deepest Might: `SetMinimumDamage(dynamicMinimum = DynamicAmounts.sourcePower(), appliesTo =
  DamageEvent(recipient = Opponent, source = SourceFilter.Matching(GameObjectFilter.Any.withColor(RED).youControl()),
  damageType = NonCombat))`.
- `DoubleDamage(restrictions?, appliesTo)` — double matching damage (Gratuitous Violence, Furnace of
  Rath). `restrictions: List<Condition>` (default empty) gates the doubling on extra conditions
  evaluated against the source's controller — the same pattern as `PreventDamage.restrictions`. The
  doubling also honours `appliesTo.damageType` (`Combat` / `NonCombat` / `Any`). The Rollercrusher
  Ride: `DoubleDamage(restrictions = listOf(Conditions.Delirium(4)), appliesTo = DamageEvent(source =
  SourceFilter.Matching(GameObjectFilter.Any.youControl()), damageType = DamageType.NonCombat))` — a
  delirium-gated "double all noncombat damage from sources you control". The doubled damage stays
  attributed to the original source (the engine scales the amount in place). Source and recipient
  filters are matched by the shared `DamageUtils` matchers, so every `SourceFilter` /
  `RecipientFilter` the prevention and `ModifyDamageAmount` paths understand works here too —
  including `RecipientFilter.OpponentOrPermanentTheyControl` (Twinflame Tyrant). Each hosting
  permanent is its own replacement and applies once (CR 616.1): two Twinflame Tyrants quadruple.
- `HalveDamage(restrictions?, appliesTo)` — the dividing mirror of `DoubleDamage`: matching damage is
  halved, **rounded down**. Ghosts of the Innocent: `HalveDamage(appliesTo = DamageEvent(recipient =
  RecipientFilter.Any))` — "a permanent or player" is the unscoped recipient, so combat and burn,
  creatures and players, the host's own controller included, are all halved. Its own type rather than
  a negative `ModifyDamageAmount` because the reduction is *multiplicative* and no `DynamicAmount` can
  read the incoming amount. Half of 1 rounded down is 0, so a 1-damage source deals nothing; each
  hosting permanent applies once (CR 616.1), so three copies take 14 to 7, 3, then 1. It runs after
  the `DoubleDamage` pass and shares its `restrictions` / `damageType` / source / recipient handling.
  It is **not** a prevention effect, so `DamageCantBePrevented` (Excruciator) does not switch it off.
  A player who is a legal recipient sees a "Damage Doubled" badge on their life orb — **except** when
  the source filter is attachment-scoped (`SourceFilter.EquippedCreature` / `EnchantedCreature`), which
  badges the attached creature's card instead, and only while it is attached. That case is a property
  of one creature's *outgoing* damage rather than of whoever might be damaged, so a player badge would
  read as "all damage dealt to you doubles" and would show even for an Equipment attached to nothing.
- `ModifyDamageAmount(modifier = 0, dynamicModifier = null, restrictions = emptyList(), appliesTo)` —
  add an amount to matching
  damage. Pass a flat `modifier` (Valley Flamecaller: "deals that much damage plus 1") or a
  `dynamicModifier: DynamicAmount?` evaluated at damage time against the replacement's **source**
  permanent (so `DynamicAmount.EntityProperty(Source, …)` / `DynamicAmounts.countersOnSelf(…)` reads
  the source's own characteristics/counters). Fated Firepower: `dynamicModifier =
  DynamicAmounts.countersOnSelf(CounterTypeFilter.Named("fire"))` with `appliesTo = DamageEvent(source =
  SourceFilter.YouControl, recipient = RecipientFilter.OpponentOrPermanentTheyControl)` — "a source you
  control deals that much damage plus the number of fire counters on this enchantment to an opponent or
  a permanent an opponent controls". Applied in `DamageUtils.applyStaticDamageAmplification` (both the
  general and combat damage paths), once per damage event, after `DoubleDamage`. `restrictions` (a
  `List<Condition>`, ALL must hold) gates *when* the bonus applies; like the rest of the damage family
  — and unlike the draw / life-total replacements, whose restrictions read the *affected* player — each
  entry is evaluated against the **replacement source's controller**. That asymmetry is what lets Far
  Fortune, End Boss's `maxSpeed { replacementEffect(ModifyDamageAmount(modifier = 1, …)) }` gate on
  *your* speed while the damage lands on an opponent.
- `RedirectDamage(redirectTo, appliesTo, condition = null)` — redirect matching damage to another
  recipient. Now wired as a continuous static replacement (each source applies at most once per damage
  event). `redirectTo` supports `EffectTarget.ControllerOfDamageSource` (the controller of the damaging
  source), `Controller`/`Self` (the replacement's owner/controller), and `TargetController`. Harsh
  Judgment: redirect chosen-color instant/sorcery damage dealt to you back to the spell's controller.
  The optional `condition: Condition?` gates the redirect on the *replacement source* at the moment
  damage would be redirected (mirrors `PreventDamage.restrictions`); a `null` condition always applies.
  Martyrs of Korlis uses `Conditions.SourceIsUntapped` for "As long as this creature is untapped, all
  damage that would be dealt to you by artifacts is dealt to this creature instead" (`redirectTo =
  EffectTarget.Self`, `source = SourceFilter.Matching(GameObjectFilter.Artifact)`).
- `ReplaceDamageWithCounters(counterType, sacrificeThreshold = null, appliesTo = DamageEvent(recipient =
  You), counterRecipient = DamageCounterRecipient.ReplacementHost)` — replace matching damage
  (CR 614.1a, an "instead" effect: the damage is never dealt, so nothing that keys on damage being
  dealt sees it) with `counterType` counters. Not a prevention effect, so it still applies to damage
  that can't be prevented. `appliesTo` filters recipient, source, damage type and amount;
  `counterRecipient` says where the counters land:
  - `ReplacementHost` (default) — on the permanent that has the ability. **Force Bubble**: "If damage
    would be dealt to you, put that many depletion counters on this enchantment instead"
    (`DamageEvent(recipient = You)`, `sacrificeThreshold = 4` for the paired "sacrifice it when it has
    four or more"). **Anti-Venom, Horrifying Healer** is the self-damage case where host and damaged
    permanent coincide (`recipient = RecipientFilter.Self`).
  - `DamagedPermanent` — on the permanent that would have been dealt the damage. **Soul-Scar Mage**:
    "If a source you control would deal noncombat damage to a creature an opponent controls, put that
    many -1/-1 counters on that creature instead" — `DamageEvent(recipient =
    RecipientFilter.CreatureOpponentControls, source = SourceFilter.YouControl, damageType =
    DamageType.NonCombat)`. A player recipient has nowhere to put counters, so the replacement
    declines rather than eating the damage.

  Wired in both damage paths (`DamageUtils.applyReplaceDamageWithCounters` for the general path,
  `CombatDamageManager` for combat); combat callers pass `isCombatDamage = true` so a noncombat-only
  pattern isn't applied to combat damage. Supported source filters are `Any`, `Self` and `YouControl`
  — any other `SourceFilter` declines rather than guessing.
- `ReplaceDamageWithMill(appliesTo = DamageEvent(recipient = Opponent))` — replace matching damage
  (CR 615, neither dealt nor prevented): each opponent of the replacement's controller mills that many
  cards instead. The Mindskinner: `DamageEvent(recipient = RecipientFilter.Opponent, source =
  SourceFilter.Matching(GameObjectFilter.Any.youControl()))` covers both the unblockable creature's
  combat damage and noncombat damage from any source you control. Mirrors `ReplaceDamageWithCounters`;
  wired in both damage paths (`DamageUtils.applyReplaceDamageWithMill` for the general path,
  `CombatDamageManager` for combat). Damage-type filtering is not applied (matches any type).
- `HealOtherDamage(appliesTo = DamageEvent(recipient = Self))` — the damage is dealt **in full**, but
  as part of the same replacement all *other* damage already marked on the recipient is **healed**
  (CR 701.69a: "If an effect states that damage already dealt to a permanent 'is healed,' that
  permanent's controller removes all marked damage from that permanent"). **Wolverine, Fierce
  Fighter**: "If damage would be dealt to Wolverine, instead that damage is dealt, but all other
  damage already dealt to him is healed." The only member of the damage family that leaves the
  *amount* alone — it can't be expressed as `PreventDamage` (subtracts), `CapDamage` (clamps) or
  `ReplaceDamageWithCounters` (swaps the damage for something else), because its whole job is the
  side effect on already-marked damage. Observable result: marked damage never accumulates across
  separate damage *events*, so the recipient only ever carries the most recent event's damage.
  Applied **once per damage event, not per instance**: all combat damage in a step is dealt
  simultaneously (CR 510.2), so a double-blocked Wolverine heals what was marked before the step and
  then takes both blockers' damage; the first-strike and regular combat damage steps are separate
  events and each heal in turn. Fires *after* every prevention/redirection/replacement has had its
  say, so fully prevented or redirected damage heals nothing; a deathtouch source still kills, and a
  wither source still triggers the heal (wither only changes the *form* of the damage, CR 702.80a).
  Players, planeswalkers and battles have no marked damage, so it is a no-op on them. The threading
  that enforces once-per-event and the engine entry points (`DamageUtils.applyHealOtherDamage`,
  `DamageUtils.healMarkedDamage`) are documented on the `HealOtherDamage` KDoc and
  `CombatDamageManager.applyCombatDamage`.
- **DamageEvent filters (gap #7):** `EventPattern.DamageEvent(recipient, source, damageType, amount)`.
  `amount: AmountFilter` (`Any` / `AtMost(n)` / `AtLeast(n)` / `Exactly(n)`) gates on the would-be
  amount (Callous Giant: `AtMost(3)`). `source = SourceFilter.Matching(filter)` can carry relational
  predicates: `GameObjectFilter.sharingColorWithRecipient()` (`CardPredicate.SharesColorWithRecipient`,
  Well-Laid Plans — "another creature that shares a color") and `sharingChosenColorWithSource()`
  (`CardPredicate.SharesChosenColorWithSource`, reads the replacement source's `ChosenColorComponent`).
  `source = SourceFilter.YouControl` matches any source (permanent, spell, ability) controlled by the
  replacement's controller — "a source you control" (Fated Firepower) — without enumerating a
  `GameObjectFilter`. `recipient = RecipientFilter.OpponentOrPermanentTheyControl` matches an opponent
  player **or** any permanent an opponent controls — "an opponent or a permanent an opponent controls".
  `recipient = RecipientFilter.Self` / `source = SourceFilter.Self` match the permanent that owns the
  replacement — "damage dealt *to* / *by* this permanent" — for source-relative static foggers like
  Fog Bank (`DamageEvent(recipient = RecipientFilter.Self, damageType = Combat)` +
  `DamageEvent(source = SourceFilter.Self, damageType = Combat)` = "prevent all combat damage that would
  be dealt to and dealt by this creature").
  `source = SourceFilter.EnchantedCreature` / `SourceFilter.EquippedCreature` match damage dealt **by**
  the permanent the replacement's host Aura/Equipment is attached to — the source-side mirror of the
  `RecipientFilter` pair, resolved from the host's `AttachedToComponent`. Mjölnir, Hammer of Thor
  ("Double all damage equipped creature would deal") is `DoubleDamage(appliesTo = DamageEvent(source =
  SourceFilter.EquippedCreature))`; with the default `recipient = Any` and `damageType = Any` that
  covers combat and noncombat damage to players and permanents alike. The two constants behave
  identically (both read the host's attachment) and exist so an Equipment's definition reads in
  Equipment vocabulary — pick the one matching the host's card type.
- `EntersTapped(unlessCondition?, payLifeCost?)` — "this permanent enters tapped" (`unlessCondition = null`),
  or "enters tapped unless `<condition>`" when an `unlessCondition` is supplied. The "slow land" cycle
  (Deathcap Glade, Dreamroot Cascade, Sundown Pass — "enters tapped unless you control two or more other
  lands") uses `unlessCondition = Conditions.YouControlOtherAtLeast(2, GameObjectFilter.Land)`, and the
  parallel "fast land" cycle (Blooming Marsh — "two or fewer other lands")
  `Conditions.YouControlOtherAtMost(2, GameObjectFilter.Land)`. Both spell the printed "other" as
  `AggregateBattlefield.excludeSelf`; counting the whole group against one more agrees arithmetically
  *only* because the source is itself a land already on the battlefield when the condition is checked,
  which is a fact about these twenty cards rather than about the shape. `payLifeCost` renders
  the "you may pay N life; if you don't, it enters tapped" variant. Assay reads every shape of this
  line — the condition is a slot over the whole condition vocabulary, not a rule per land cycle.
  The clause applies **however the permanent enters** (CR 614.1d / 614.12), so three disjoint paths
  read it: `PlayLandHandler` (playing a land), `StackResolver` (a resolving permanent spell), and
  `ZoneTransitionService.moveToZone` (everything else — reanimation, a return from exile, a
  search-library or collection move). A card put onto the battlefield by an effect that says nothing
  about tapped therefore needs no `ZonePlacement.Tapped` of its own; spelling one anyway is a
  divergence Assay's differential reports, because the printed line says nothing about it. Only
  `PlayLandHandler` and `StackResolver` can pause for the `payLifeCost` prompt; `moveToZone` is a pure
  state transition, so a reanimated or fetched shock land resolves fail-closed to **tapped** without
  being asked.
- `EntersUntapped(appliesTo = ZoneChangeEvent(filter, to = Zone.BATTLEFIELD))` — the inverse of
  `EntersTapped`: "[filter] enter the battlefield untapped" (The Wandering Minstrel — "Lands you
  control enter untapped", `filter = GameObjectFilter.Land.youControl()`). Unlike `EntersTapped`,
  which is a self-replacement consumed once as the source enters, this is a *runtime* replacement
  stamped into the source's `ReplacementEffectSourceComponent` (`StaticAbilityHandler.isRuntimeReplacementEffect`)
  and consulted from the battlefield against OTHER permanents as they enter — so `appliesTo.filter`
  describes the *affected* permanents. The entry-tap paths (`PlayLandHandler`, `ZoneTransitionService`,
  `StackResolver`, and `CreateTokenExecutor` for created tokens)
  ask `EnterUntappedReplacements.entersUntapped(...)` before marking a permanent tapped and skip the
  tap when it matches. Per CR 614 ordering this collapses "would enter tapped via another replacement"
  (controller chooses untapped) and "simply put onto the battlefield tapped" (no replacement → untapped)
  to the same outcome; a shock land's "pay N life or enter tapped" prompt is elided (moot when it enters
  untapped regardless). Edge not covered: a same-event simultaneous mass-entry where the source itself is
  among the entering permanents (the source isn't yet consulted), and a land tapped via a generic
  `OnEnterRunEffect` self-tap (e.g. Game Trail) is not overridden.
- `PermanentsEnterTapped(appliesTo = ZoneChangeEvent(filter, to = Zone.BATTLEFIELD), condition = null)` — the global/group
  counterpart of the self-only `EntersTapped`: "[filter] enter the battlefield tapped" (Zhao, the Moon
  Slayer — "Nonbasic lands enter tapped", `filter = GameObjectFilter.NonbasicLand`; also expresses
  Imposing Sovereign / Authority of the Consuls "creatures your opponents control enter tapped"). Like
  `EntersUntapped`, it is a *runtime* replacement stamped into the source's `ReplacementEffectSourceComponent`
  and consulted from the battlefield against OTHER permanents as they enter, so `appliesTo.filter` describes
  the *affected* permanents. The entry paths (`PlayLandHandler`, `ZoneTransitionService`, `StackResolver`,
  and `CreateTokenExecutor` for created tokens) call
  `EnterTappedReplacements.entersTapped(...)` and mark the permanent tapped — **after** consulting
  `EnterUntappedReplacements`, so per CR 614 an applicable `EntersUntapped` still wins. Created tokens are
  covered too: e.g. Dauntless Dismantler's "Artifacts your opponents control enter tapped" taps an
  opponent's Map/Treasure/Clue token, and Authority of the Consuls taps opponents' creature tokens (a token
  entering attacking keeps its tapped state and is not overridden). The optional `condition` is a gate
  evaluated against the replacement *source* when a permanent would enter — the same axis `RedirectDamage`
  and `EntersWithCounters` carry — for a source whose tap clause depends on state it can't put in the
  filter: Ashling's Prerogative gates its even-mana-value half on `SourceChosenModeIs("odd")` and its
  odd-mana-value half on `SourceChosenModeIs("even")`. Reach for it whenever the clause would otherwise
  want a `ConditionalStaticAbility` wrapper — a runtime replacement can't be wrapped in one, because it is
  stamped into the replacement component rather than projected through the layer system.
- `RedirectZoneChange(newDestination, appliesTo, linkToSource = false, selfOnly = false, shuffleIntoLibrary = false, reveal = false, requiredCause = ZoneChangeCause.Any)`
  — redirect a zone change to a different destination (Rest in Peace / Leyline of the Void: graveyard →
  exile). `appliesTo` is an `EventPattern.ZoneChangeEvent(filter, from?, to?)`; the `filter`'s
  `controllerPredicate` scopes it (e.g. `OwnedByOpponent` for Leyline). When `linkToSource = true` and
  `newDestination = Zone.EXILE`, each redirected card is added to the source permanent's
  `LinkedExileComponent`, so the source can later reference — and grant playing of — the cards it exiled.
  Valgavoth, Terror Eater pairs it with `GrantMayCastFromLinkedExile`: "If a card you didn't control
  would be put into an opponent's graveyard from anywhere, exile it instead" is
  `RedirectZoneChange(newDestination = Zone.EXILE, linkToSource = true, appliesTo = ZoneChangeEvent(to =
  Zone.GRAVEYARD, filter = GameObjectFilter(cardPredicates = listOf(CardPredicate.IsNontoken),
  controllerPredicate = ControllerPredicate.And(listOf(OwnedByOpponent, Not(ControlledByYou))))))`. The
  redirect (and link) is honored across every graveyard path: SBA deaths, mill/discard/destroy
  (`ZoneTransitionService`), spell resolution, counters, and fizzles (`StackResolver`).
  **Card-intrinsic "from anywhere" self-replacements:** set `selfOnly = true` when the redirect is the
  moving card's *own* ability referring to itself ("If ~ would be put into a graveyard from anywhere,
  …"). It then functions in **every** zone (CR 614.12), carried on the card entity via
  `SelfZoneRedirectComponent` rather than scanned off the battlefield, so the card is redirected whether
  it dies, is milled, is discarded, or is countered on the stack — and it stops applying only while the
  source is on the battlefield with all abilities removed. `shuffleIntoLibrary = true` (with
  `newDestination = Zone.LIBRARY`) shuffles the card in rather than placing it on top; `reveal = true`
  is the flavor "reveal it and …" (informational — a public-zone card is already known). Darksteel
  Colossus / Progenitus: `RedirectZoneChange(newDestination = Zone.LIBRARY, appliesTo =
  ZoneChangeEvent(to = Zone.GRAVEYARD), selfOnly = true, shuffleIntoLibrary = true, reveal = true)`.
  **Cause qualifier:** `requiredCause` narrows the replacement to a particular *reason* for the move, on
  top of the from/to zones — the zones alone can't tell "hand → graveyard because you discarded to hand
  size" from "…because an opponent's Mind Rot made you". `ZoneChangeCause.Any` (default) is unqualified;
  `ZoneChangeCause.DiscardedByOpponentEffect` fires only when a spell or ability an **opponent** of the
  discarding player controls caused the discard — excluding the cleanup-step hand-size discard (a
  turn-based action) and discards paid as a cost of your own spell. Wilt-Leaf Liege / Loxodon Smiter:
  `RedirectZoneChange(newDestination = Zone.BATTLEFIELD, appliesTo = ZoneChangeEvent(filter =
  GameObjectFilter.Any, from = Zone.HAND, to = Zone.GRAVEYARD), selfOnly = true, requiredCause =
  ZoneChangeCause.DiscardedByOpponentEffect)`. Engine-side, the discard sites record the causing
  object's controller in the transient `GameState.pendingDiscardCauseControllers` marker (the same idiom
  as `pendingSacrificeIds`), which the redirect path reads and `moveToZone` consumes. The card still
  counts as discarded either way — `CardsDiscardedEvent` fires regardless of where it lands.
- `RedirectZoneChangeWithEffect(newDestination, additionalEffect, selfOnly = false, linkToSource = false,
  appliesTo)` — like `RedirectZoneChange` but also runs `additionalEffect` when the replacement fires.
  The additional effect is applied through a small executor whitelist (not the full pipeline) —
  `TakeExtraTurnEffect` (Ugin's Nexus), `AddCountersEffect` on the redirected card (Darigaaz
  Reincarnated), `GainLifeEffect` (fixed amount, gained by the replacement source's controller;
  emits `LifeChangedEvent` so life-gain triggers fire), and `CreateTokenEffect` (Head of the Hunt's
  "When you do, create a 2/2 green Wolf creature token" — minted for the replacement source's
  controller through the real `CreateTokenExecutor`, so the token gets the minting set's art, its
  keywords and static abilities, and the enters-the-battlefield events ETB triggers read).
  `selfOnly = true` restricts it to the source
  permanent itself; `linkToSource = true` (exile destination only) adds the redirected card to the
  source's `LinkedExileComponent` exactly like `RedirectZoneChange.linkToSource`. The Darkness Crystal's
  "If a nontoken creature an opponent controls would die, instead exile it and you gain 2 life" is
  `RedirectZoneChangeWithEffect(newDestination = Zone.EXILE, additionalEffect = GainLifeEffect(2),
  linkToSource = true, appliesTo = ZoneChangeEvent(filter = GameObjectFilter.Creature.nontoken().opponentControls(),
  from = Zone.BATTLEFIELD, to = Zone.GRAVEYARD))` — the linked cards are then retrieved by a
  `Creature.exiledWithSource()` target (see §7 state predicates). Honored across the same graveyard
  paths as `RedirectZoneChange`.
- `ReplacementEffect.IfYouDoBranchEffect(...)` — branch on "if you do" replacement.
- `OnEnterRunEffect(effect)` — generic "as ~ enters the battlefield, run [effect]". The wrapped effect
  executes via the normal effect-executor pipeline at entry time (so `EffectTarget.Self` resolves to
  the entering permanent) and may pause for player input. Compose with atomic pausable effects like
  `Effects.MayRevealCardFromHand` to build SOI shadow lands or other "as ~ enters" choices.
  **Scope today:** the land-play path (`PlayLandHandler`) and the single-card `MoveToZoneEffect`
  path (`MoveToZoneEffectExecutor` → `PermanentEntryReplacements.runOnEnterRunEffect`), which covers
  reanimation, blink, and an earthbend/"return it to the battlefield" trigger. Not yet wired into:
  - `MoveCollectionExecutor` — the batch move. This is **not** only multi-card: a library search that
    puts a *single* card onto the battlefield (`SearchDestination.BATTLEFIELD`, e.g. Wayfarer's
    Bauble) routes here too, so tutoring up one of these lands still skips the clause. Closing it
    needs a continuation, because the replacement can pause mid-loop.
  - `StackResolver.enterPermanentOnBattlefield` — the spell-resolution path; needed when the first
    non-land permanent uses this replacement.
  - Auras, on any path — `MoveToZoneEffectExecutor` hands an entering Aura to its choose-a-host
    continuation before reaching the replacement.

  **Entry order caveat.** CR 614.12a says an as-enters choice is made *before* the permanent enters;
  the engine places the permanent first and then runs the effect, so `EffectTarget.Self` resolves.
  The observable difference is that the entry's triggers are matched against the pre-choice
  characteristics — a "whenever a Mountain enters" trigger won't see the type a Multiversal Passage
  just chose. Both entry paths behave the same way here, so it is consistent, not path-dependent.
- `EntersWithCounters(counterType?, count, selfOnly?, condition?, otherOnly?, appliesTo?)` /
  `EntersWithDynamicCounters(counterType?, count, otherOnly?, appliesTo?, activeZones?)` — "[permanent] enters with
  N counters." `EntersWithCounters` takes a fixed `count: Int` (Master Biomancer, Metallic Mimic);
  `EntersWithDynamicCounters` takes a `count: DynamicAmount` (Stag Beetle; the SOS Converge "Archaic"
  cycle via `convergeEntersWithCounters()` → `count = DistinctColorsManaSpent`). `appliesTo` defaults
  to "creatures you control entering the battlefield." Two scopes:
  - **Self** (default) — applies to the permanent that owns the replacement. Reserve a *dynamic* count
    for "this creature enters with a counter for each color of mana spent to cast **it**" / "for each X
    it has".
  - **`activeZones = {GRAVEYARD}`** (dynamic variant) — the source hands out its counters from the
    *graveyard* instead of the battlefield (Dearly Departed: "as long as this creature is in your
    graveyard, each Human creature you control enters with an additional +1/+1 counter on it"). Pair it
    with `otherOnly = true`, which is what routes the effect through the global sweep; the source can
    never be live for its own entry anyway, since entering the battlefield is leaving the graveyard.
  - **`otherOnly = true`** (both variants) — applies to *other* matching creatures entering (Metallic
    Mimic: "each **other** creature you control of the chosen type enters with an additional +1/+1
    counter"; Gev, Scaled Scorch:
    "Other creatures you control enter with additional counters"; Wildgrowth Archaic: "whenever you
    cast a creature spell, that creature enters with X additional +1/+1 counters … where X is the
    number of colors of mana spent to cast **it**"). The `count` is always evaluated against the
    **entering object**, not the replacement source — so an entering-object amount like
    `DistinctColorsManaSpent` reads the new creature's own cast (and a token / reanimated creature that
    wasn't cast spent no mana → 0). Player-scoped counts (`TurnTracking(Player.You)`, Gev) still read
    the replacement source's controller. **Set `otherOnly` on any "each *other* …" wording** — the
    entering permanent's own entry path applies its printed enters-with effects regardless of
    `appliesTo` (that's how plain self-counter cards work), so without the flag a group effect also
    counters its own source as it enters. Source-relative predicates in `appliesTo` (notably
    `withChosenSubtype()`) resolve against the **replacement source**, so Metallic Mimic's filter reads
    the type chosen as the Mimic entered, not anything on the entering creature.
- `EntersWithKeywords(keywords, condition?, selfOnly?, appliesTo?)` — "[permanent] enters with
  [keywords]" (CR 614.1c), the keyword counterpart of `EntersWithCounters`. The grant happens as the
  permanent enters — no trigger, no stack, no response window — as a permanent, entry-timestamped
  Layer-6 floating effect: a later "loses all abilities" removes it, it does not re-apply if stripped,
  and it is cleaned up when the permanent leaves the battlefield (new object, CR 400.7). Kicker riders
  are the canonical use (Kavu Titan "If this creature was kicked, it enters with three +1/+1 counters
  on it and with trample" = an `EntersWithCounters` **plus** an `EntersWithKeywords`, both
  `selfOnly = true, condition = WasKicked`; also Benalish Lancer / Duskwalker / Faerie Squadron /
  Pouncing Kavu). `condition` is evaluated at the moment of entry against the entering permanent
  (cast-choice conditions like `WasKicked` read the durable cast-choices bag, so a token copy or
  reanimated body — never kicked — correctly gets nothing). Like `EntersWithCounters`, a
  non-`selfOnly` instance stamped on a battlefield permanent applies to *other* permanents matching
  `appliesTo` as they enter.
- `EntersWithDevour(multiplier, sacrificeFilter, counterType, variant)` — Devour (CR 702.82) and its
  printed variants. As the permanent resolves from the stack, the controller is prompted to pick any
  number of their own permanents matching `sacrificeFilter`. Those permanents are sacrificed and the
  entering permanent gains `multiplier × count` counters of `counterType` (default `+1/+1`). Pair
  with `KeywordAbility.Devour(multiplier, sacrificeFilter, variant)` so the rules text renders. The
  `variant` parameter is a textual tag only — `""` for plain Devour, `"land"` for the EOE
  "Devour land N" wording. **Scope today:** only the stack-spell entry path is wired; reanimation and
  token entries skip Devour (which is fine for printed cards — Devour creatures all cost real mana to
  cast).
- `EntersWithExileCounters(filter, sourceZone, maxCards, counterType, countersPerCard)` — as the
  permanent resolves from the stack, its controller may select up to the dynamic `maxCards` matching
  cards from their `sourceZone` (graveyard by default). The selected cards move to exile linked to the
  entering permanent's `LinkedExileComponent`, and the permanent enters with `countersPerCard` counters
  for each card that actually reached exile. `maxCards = DynamicAmount.XValue` models X-bounded entry
  choices; a zero maximum or no matching cards skips the prompt. **Mimeoplasm, Revered One** uses this
  with creature cards, three +1/+1 counters per card, and later targets the linked exile pile.
- `ModifyCounterPlacement(modifier, appliesTo, placedByYou?)` / `DoubleCounterPlacement(placedByYou?, appliesTo)` —
  **static** counter-placement modifiers living on a battlefield permanent for as long as it remains
  (Hardened Scales `+1`, Winding Constrictor `+1`, Doubling Season doubles). `appliesTo` is an
  `EventPattern.CounterPlacementEvent(counterType, recipient)`; `recipient = CreatureYouControl` is
  resolved relative to the *source permanent's* controller. **`placedByYou`** (both types, default
  `false`) is the *placer* axis, and it is what separates the two printed wordings: leave it `false`
  for "**if counters would be put** on …" (Hardened Scales, Winding Constrictor, Doubling Season),
  where an opponent's proliferate feeds the effect too and the recipient filter is the only
  "you control" gate; set it `true` for "**if you would put** one or more counters on …" (Doc
  Samson, Super Psychiatrist; Innkeeper's Talent), which applies only when the effect's own
  controller is the one placing them. For the **activated/spell-granted,
  duration-scoped** version of this (Prairie Dog), use the effect
  `Effects.GrantCounterPlacementModifier(...)` (§4 Counters) instead — it records a controller-scoped
  modifier in a turn-scoped game-state store consulted from the same counter-placement chokepoint,
  and expires at end of turn.
- `MultiplyTokenCreation(factor = 2, appliesTo)` / `ModifyTokenCount(modifier, appliesTo)` —
  **static** token-count replacements living on a battlefield permanent. `MultiplyTokenCreation`
  multiplies the number of tokens created by `factor` (Doubling Season / Anointed Procession /
  Exalted Sunborn — `factor = 2`; **Ojer Taq, Deepest Foundation** — `factor = 3`); several stack
  multiplicatively. `ModifyTokenCount` shifts the count by a fixed amount. `appliesTo` defaults to
  `EventPattern.TokenCreationEvent(controller = You)`; the multiplier runs in `CreateTokenExecutor`,
  the executor for **creature** tokens (it always builds a "… Creature" type line — Treasure/Clue/Map
  and other predefined tokens go through a separate executor that isn't multiplied), so an
  unfiltered `MultiplyTokenCreation` scopes to creature tokens in practice. `tokenFilter` on the
  event is not yet honored by the count path (filtered events are skipped), so express "creature
  tokens" via the default rather than `tokenFilter = Creature`.
- `ReplaceTokenCreationWithAttachedCopy(optional, oncePerTurn, attachmentVerb, appliesTo)` —
  "the first time you would create one or more tokens each turn, you may instead create that
  many tokens that are copies of [attached] permanent." Works for both Equipment and Auras —
  the engine reads the source's `AttachedToComponent` to find the permanent to copy.
  `optional = true` surfaces a yes/no during resolution; `oncePerTurn = true` adds
  `TokenReplacementOfferedThisTurnComponent` after the first offer (cleared at end of turn).
  `attachmentVerb` is a display-only label ("equipped", "enchanted", "fortified") — the
  attachment-type validation already happens at cast/attach time via `equipmentTarget` /
  `auraTarget`. Token copies are summoning-sick only when the copy is a creature (CR 302.6).
  Mirrormind Crown: `attachmentVerb = "equipped"`; Moonlit Meditation: `attachmentVerb = "enchanted"`.
- `CreateAdditionalToken(additionalTokenType, additionalTokenCount = 1, inheritTapped = false, appliesTo, restrictions = [])` —
  token-creation replacement that keeps the original tokens and appends one or more predefined tokens of
  another type. `appliesTo = EventPattern.TokenCreationEvent(controller, tokenFilter)` gates the original
  creation event, and the extra tokens are added once per qualifying event, not once per token. The added
  tokens bypass the same replacement pass so a Map added for an artifact-token event does not recursively
  trigger itself. Used by Worldwalker Helm (`TokenCreationEvent(You, Artifact)`, add `Map`, `inheritTapped = true`)
  and Peregrin Took (`additionalTokenType = "Food"`, "those tokens plus an additional Food token are created instead")
  and Quina, Qu Gourmet (`additionalTokenType = "Frog"`, default `appliesTo` = any token you create, adds a 1/1 green Frog).
  `restrictions` are extra `Condition` gates, evaluated by `TokenCreationReplacementHelper` with the
  *creating* player as the controller and the rider's own permanent as the source — so a source-relative
  gate resolves. That is how a **Solved replacement effect** carries its gate: a "Solved —" static
  ability written as a replacement effect can't go through `solvedStaticAbility { }` (replacement effects
  are declared outside the static-ability builder), so it takes `restrictions = listOf(Conditions.SourceIsSolved)`
  instead — Case of the Pilfered Proof's "Solved — If one or more tokens would be created under your
  control, those tokens plus a Clue token are created instead".
- `EntersAsCopy(optional, copyFilter, copyFromZone, filterByTotalManaSpent, additionalSubtypes, additionalKeywords, nameOverride, powerOverride, toughnessOverride, exileCopiedCard, tappedIfCopied, additionalCounters)` —
  "enter as a copy of …". As the permanent enters, the controller picks an object matching
  `copyFilter` and the permanent enters as a copy (Rule 707 copiable values), with any overrides
  applied. `copyFromZone` selects the candidate pool: `Zone.BATTLEFIELD` (default — Clone, Clever
  Impersonator, Mockingbird) copies a permanent in play; `Zone.GRAVEYARD` copies a *card*
  from any graveyard (Superior Spider-Man; Echoing Deeps copies a land card) via the modal card-list
  overlay. `additionalSubtypes` /
  `additionalKeywords` are added "in addition to its other types"; `nameOverride` keeps a fixed name;
  `powerOverride` / `toughnessOverride` force base P/T; `exileCopiedCard` exiles the copied card after
  the copy ("When you do, exile that card"). `filterByTotalManaSpent` restricts copy targets to mana
  value ≤ total mana spent (Mockingbird). `tappedIfCopied` makes the permanent enter **tapped** only
  when it actually enters as a copy — the "enter tapped as a copy" rider on the land-copy cycle
  (Echoing Deeps; Vesuva / Thespian's Stage copying a land on the battlefield); declining the copy
  enters it untapped as its printed self. `additionalCounters: DynamicAmount?` is the sibling rider
  "except it enters with N additional +1/+1 counters on it" — `DynamicAmount.XValue` for Altered
  Ego, `Fixed(1)` for a Spark Double shape. It lives on the copy effect rather than on a separate
  `EntersWithCounters`, because copying replaces the permanent's own copiable text: a self-targeted
  enters-with-counters replacement would be gone by the time the copy applies. It follows the same
  "only if a copy was actually made" rule as `tappedIfCopied`, which is the printed ruling
  ("You can choose not to copy anything. … It won't have +1/+1 counters placed on it by its
  ability."), and routes through `EntersWithReplacements.placeEntryCounters` so Hardened Scales-style
  placement modifiers apply exactly as for printed enters-with counters. The copy snapshots a
  `CopyOfComponent` so it reverts to its
  printed identity when it leaves the battlefield (CR 400.7 / 707.2). Works both when the source is
  cast as a spell (resolved off the stack) **and** when it enters the battlefield directly — a land
  played (Echoing Deeps) pauses via `PermanentEntryReplacements.pauseForEntersAsCopy`, its resumer
  `CloneEntersOnBattlefieldContinuation` copying onto the already-placed permanent in place.
- `ModifyDrawAmount(modifier, multiplier, restrictions, appliesTo)` — modify the number of cards a draw
  instruction announces to `(count * multiplier) + modifier`, clamped to ≥ 0, optionally gated by extra
  `restrictions: List<Condition>`
  evaluated against the drawing player as controller. Applied **once** per draw instruction at the
  announcement site — `DrawCardsExecutor.execute` for spell/ability draws and
  `DrawPhaseManager.performDrawStep` for the draw step (CR 121.2a: "An instruction to draw multiple
  cards can be modified by replacement effects that refer to the number of cards drawn. This
  modification occurs before considering any of the individual card draws.") — so a paused-and-
  resumed per-card loop doesn't double-modify. CR 616.1g is what makes the two-level split legal:
  the announced draw *contains* the individual draws, and an effect applying to a contained event
  can't be chosen until the containing one has been. `appliesTo` is typed as
  `EventPattern.DrawCardsEvent`, not the general `EventPattern`, so pointing one at the per-card
  `DrawEvent` is a **compile error** rather than a hang — a count modification that draws no card
  leaves the game state unchanged, so the per-card loop would re-match and re-apply it forever.
  Reach for `ReplaceDrawWithEffect` when you genuinely need a per-card replacement. Note that "you"
  in restriction text reads as the drawing player, not the source's controller; for
  `DrawCardsEvent(player = Player.You)` they coincide, but `DrawCardsEvent(player = Player.EachOpponent)`
  cards needing "you" = source controller would have to use a source-relative condition instead. Use
  `modifier` for the additive wording — "if you would draw one or more cards, you draw that many
  cards plus N instead" (Quantum Riddler:
  `ModifyDrawAmount(modifier = 1, restrictions = listOf(Conditions.CardsInHandAtMost(1)), appliesTo = DrawCardsEvent(player = Player.You))`)
  — and `multiplier` for a doubling that genuinely refers to the announced quantity. **Pick by the
  oracle wording, not by the outcome.** "If you would draw *one or more cards*, …" refers to the
  number drawn and belongs here; "If you would draw *a card*, draw two cards instead" (Vnwxt,
  Verbose Host) does not, and belongs in a per-card `ReplaceDrawWithEffect(DrawCardsEffect(2))` on
  `EventPattern.DrawEvent`. Both make Harmonize draw six on their own, so the difference only shows
  when the two levels meet: CR 616.1g orders the containing event before the contained one, so
  Quantum Riddler plus Vnwxt is `(3 + 1)` announced and then each of the four draws doubled = 8.
  Modelling Vnwxt here instead would drop both into one CR 616.1e pool and let the player choose an
  order giving 7. Several applicable announcement effects are cumulative — two doublers quadruple
  the draw. `restrictions` is also the seam a "Max speed —" gate folds into — declare the replacement
  inside `maxSpeed { replacementEffect(…) }` and the builder fills the slot.
- `ModifyMillAmount(modifier, restrictions, appliesTo)` — modify the number of cards a *mill* announces
  by a fixed amount (the mill twin of `ModifyDrawAmount`): a player who would mill N instead mills
  `N + modifier`, clamped to ≥ 0. `appliesTo` is an `EventPattern.MillEvent` whose `player` filter
  (`Player.You` / `Player.EachOpponent` / `Player.Each`) gates which players' mills are affected,
  relative to the source's controller. `restrictions` (a `List<Condition>`, ALL must hold, evaluated
  against the milling player as controller) gates *when* it applies. Applied **once** per mill
  instruction at the announcement site (`GatherCardsExecutor`'s `CardSource.TopOfLibrary(isMill = true)`
  branch, which only the `Patterns.Library.mill(...)` pipeline sets — scry / surveil / exile-top /
  look-at-top gathers leave `isMill = false` and are never affected), so a paused-and-resumed mill
  never double-modifies. A base mill of 0 is left untouched ("would mill one or more cards"). Multiple
  instances sum. Use for "if an opponent would mill one or more cards, they mill that many cards plus
  four instead" (The Water Crystal:
  `ModifyMillAmount(modifier = 4, appliesTo = EventPattern.MillEvent(player = Player.EachOpponent))`).
- `ModifyKeywordAction(prefixEffect, appliesTo)` — insert an extra effect *in front of* a keyword
  action (CR 614): replaces "[a matching permanent] <acts>" with "[prefixEffect], then that permanent
  <acts>". One type across keyword actions rather than one per action — `appliesTo` carries which
  action (and its subject filter), `prefixEffect` carries the rest. Supported patterns:
  `EventPattern.ExploredEvent` (CR 701.44) and `EventPattern.ConnivedEvent` (CR 701.50); any other
  pattern never matches. The pattern's `filter` scopes which actions are modified, matched against
  the acting permanent with the **source's controller** as "you" (so
  `ConnivedEvent(GameObjectFilter.Creature.youControl())` = "if a creature you control would
  connive"); `ExploredEvent.revealedType` is irrelevant (the replacement runs before the reveal).
  Like `ReplaceDrawWithEffect`, neither action is a generic replaceable event —
  `ExploreEffectExecutor` / `ConniveEffectExecutor` consult printed `ModifyKeywordAction` on the
  battlefield directly (via `KeywordActionReplacements`) and, on a match, re-issue the action as
  `Composite(prefixEffect, <action>(sameCreature, replacementsApplied = true))` through the registry
  recursion, so a pausing prefix (Scry's top/bottom decision) and the action's own decision
  (connive's discard) sequence in the printed order. The `replacementsApplied` guard on the inner
  action stops the same replacement applying twice (CR 614.5). Multiple applicable sources chain
  their prefixes in battlefield order (a faithful APNAP order per CR 616 is unmodeled — no printed
  card stacks two modifiers on one action). Note the prefix runs in the *replaced action's* context,
  so an opponent's effect making your creature act would run the prefix as the opponent; no printed
  card distinguishes this today.
  - Twists and Turns:
    `ModifyKeywordAction(Effects.Scry(1), EventPattern.ExploredEvent(GameObjectFilter.Creature.youControl()))`
    ("If a creature you control would explore, instead you scry 1, then that creature explores").
  - Leader, Super-Genius:
    `ModifyKeywordAction(Effects.DrawCards(1), EventPattern.ConnivedEvent(GameObjectFilter.Creature.youControl()))`
    ("If a creature you control would connive, instead you draw a card, then that creature
    connives") — the extra card is in hand *before* the discard is chosen, which a
    "whenever … connives, draw a card" trigger could not do.
- `ModifyLifeGain(multiplier, modifier, appliesTo, restrictions)` — modify life gain by a multiplicative *and/or*
  additive factor: `gained = (original * multiplier) + modifier`, clamped to ≥ 0. `appliesTo` is a `LifeGainEvent`
  whose `player` filter (default `Player.Each`) gates which players the replacement applies to. `restrictions`
  (a `List<Condition>`, ALL must hold, evaluated against the gaining player as controller) gates *when* it applies
  — e.g. Phial of Galadriel `restrictions = listOf(Conditions.LifeAtMost(5))` ("while you have 5 or less life").
  Used by Alhammarret's Archive (`multiplier = 2`), Leyline of Hope (`multiplier = 1, modifier = 1, player =
  Player.You`). Multiple instances stack (×s multiply, +s sum) — two Leylines of Hope add 2 to every life-gain event.
- `ModifyLifeLoss(multiplier, modifier, restrictions, appliesTo)` — same shape as `ModifyLifeGain` for life loss
  events (`LifeLossEvent`), plus a `restrictions: List<Condition>` list that further gates the replacement.
- `LifeLossFloor(floor, restrictions, appliesTo)` — cap damage-induced life loss so the resulting life total
  is ≥ `floor`. `appliesTo` is a `LifeLossEvent` whose `player` filter gates who is protected (default
  `Player.Each`); `restrictions: List<Condition>` (evaluated against the source's controller) further
  gates the floor — same shape as `ModifyLifeLoss.restrictions`. **Scope:** damage-as-life-loss only
  (CR 120.3a); `LoseLifeExecutor` deliberately skips this step so pay-life costs and direct life-loss
  effects bypass the floor (matching the Ali from Cairo ruling "does not apply to effects which reduce
  your life without doing damage"). The damage event still fires at the original amount, so lifelink
  and damage-dealt triggers see the full damage. Multiple instances pick the strictest floor. Used by
  Ali from Cairo (`LifeLossFloor(floor = 1, appliesTo = LifeLossEvent(Player.You))`); Worship adds a
  `restrictions = listOf(YouControlACreature)` gate.
- `ReplaceLifePaymentWithLibraryExile(appliesTo)` — a life **payment** becomes an exile of that many cards
  off the top of the payer's library, when the library is at least that deep (Ashiok, Wicked Manipulator).
  `appliesTo` is a `LifePaymentEvent` whose `player` filter picks whose payments are replaced (default
  `Player.You`). **Scope:** payments only (CR 118.8) — life *loss* from damage or a "you lose N life"
  effect keeps its own path, which is exactly the printed reminder text "Damage and unpayable costs still
  cause you to lose life". Mandatory and unsplittable, and a library shallower than the payment simply
  falls through to paying life normally. It does not raise what you're allowed to pay: CR 118.5 still
  requires a life total at least equal to the payment, so cost legality is unchanged.
  Applied by `LifePaymentService`, the engine choke point every life payment funnels through — cost
  atoms, additional casting costs, ward and Phyrexian-style payments, pain-cost mana abilities and the
  `PayLife` / `PayDynamicLife` resolution effects alike.
- `PreventLifeGain(appliesTo)` — life gain matching the event is fully prevented (Sulfuric Vortex, Erebos).
  The `LifeGainEvent.player` scope can be `You` / `EachOpponent` / `Each` (resolved relative to the
  source's controller) or `EnchantedPlayer` for an "enchant player" Aura whose locked player is its
  attachment target (Grievous Wound). For a *source-independent, rest-of-game* lock on a specific player
  instead, use the one-shot effect `Effects.LockLifeGain` (§4).
- Custom — implement the `ReplacementEffect` interface directly.

Amount-modifying replacements expose **both** `multiplier` (×) and `modifier` (±) on the same type — do not split into
`DoubleX` + `ModifyXAmount`.

---

## 16. Counters

String-keyed counter types — resolve via the central `resolveCounterType` helper rather than per-executor character
substitution.

- `+1/+1`, `-1/-1` — power/toughness counters.
- `loyalty` — planeswalker loyalty.
- `charge`, `time`, `level`, `quest`, `fade`, `vanishing`, `experience`, `age`, `velocity`, `awakening`,
  `blood`, `cage`, `doom`, `storage`, `divinity` (`Counters.DIVINITY`, a passive counter used by the Myojin
  cycle), `charm`, `music`, `crumble`, `corpse`, `germ`, `ink`, `growth`,
  `hour`, `energy`, `scry`, `aura`, `chapter`, `citation`, `rune`, `scar`, `crux`, `omen`, `secret`, `feather`,
  `hourglass`, `hope`, `verse`, `influence`, `burden`, `loot`, `soul`, `bait` — assorted printed counter kinds. (`hourglass`: Temporal Distortion
  — a permanent with one doesn't untap during its controller's untap step; model the restriction with
  `GrantKeyword(AbilityFlag.DOESNT_UNTAP.name, GroupFilter(... .withCounter(Counters.HOURGLASS)))` so it stays
  projection-scoped.) (`hope` / `verse` / `influence` / `burden`: LTR — Dawn of a New Age / Lost Isle Calling /
  Palantír of Orthanc / The One Ring. `loot`: OTJ — Bandit's Haul. `wind`: ARN — Cyclone (accrued one-per-upkeep,
  scales a pay-or-sacrifice cost + damage). `nest` (`Counters.NEST`): DSK — Twitching Doll,
  whose mana ability accumulates one per activation and whose sacrifice ability reads the count to scale a token
  payoff. `page` (`Counters.PAGE`): SOS — Diary of Dreams, whose cast-an-instant-or-sorcery trigger accumulates one
  and whose `{5},{T}: draw` ability reads the count via `genericCostReduction` to cost `{1}` less per counter.
  `hoofprint` (`Counters.HOOFPRINT`): LRW — Hoofprints of the Stag, whose "whenever you draw a card, you **may**"
  trigger accumulates one and whose `{2}{W}, Remove four hoofprint counters` ability
  (`Costs.RemoveCounterFromSelf(Counters.HOOFPRINT, 4)`) spends them for a 4/4 flying Elemental.
  `mannequin` (`Counters.MANNEQUIN`): LRW — Makeshift Mannequin, a pure marker that exists only so the reanimated
  creature's granted "when this becomes the target of a spell or ability, sacrifice it" ability has something to be
  keyed to (`Duration.WhileAffectedHasCounter(Counters.MANNEQUIN)`); remove the counter and the drawback goes too.
  `doom`: ATQ — Armageddon Clock (accrued one-per-upkeep, scales the damage dealt to each player in the draw step;
  a {4} ability removes one). `omen` (`Counters.OMEN`): VOW — Soulcipher Board, a *countdown* counter — the artifact
  enters with three and a per-card "whenever a creature card is put into your graveyard from anywhere" trigger removes
  one, transforming the artifact once a `Compare(countersOnSelf(Named(OMEN)), EQ, 0)` intervening check passes.
  `suspect` (`Counters.SUSPECT`): VOW — Investigator's Journal, a passive store — the artifact enters with one per
  creature the most-creatured player controls (`EntersWithDynamicCounters` over
  `DynamicAmounts.greatestControlledBySinglePlayer(...)`) and a `{2}, {T}, Remove a suspect counter` ability spends
  them one at a time. Unrelated to the *suspected* keyword action (CR 701.58), which places no counter at all.
  Pure passive counters with no inherent rule; the cards that use them accumulate/spend them via their own
  abilities and read the count via `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.X))` — or, when a
  self-sacrifice/exile cost wipes them first, `DynamicAmounts.lastKnownSourceCounters(...)` (CR 113.7a; see §13).
  `rev` (`Counters.REV`): DSK — Chainsaw, whose "whenever one or more creatures die" batched trigger accumulates one
  per death batch and whose `+X/+0` static reads the count via `DynamicAmounts.countersOnSelf(...)` applied to the
  equipped creature — another pure passive counter with no inherent rule.
  `bloodstain` (`Counters.BLOODSTAIN`): MKM — Blood Spatter Analysis, whose "whenever one or more creatures die"
  batched trigger accumulates one per death batch and then, *in the same resolution*, tests
  `Conditions.SourceCounterCountAtLeast(Counters.BLOODSTAIN, 5)` to decide whether to sacrifice itself. The threshold
  deliberately lives inside the trigger rather than in a state trigger/SBA: per the card's ruling, a fifth counter
  arriving by any other route (proliferate, a doubler) does *not* sacrifice it — another pure passive counter with no
  inherent rule.
  `blood` (`Counters.BLOOD`): RAV — Bloodletter Quill, whose `{2},{T}, put a blood counter on this artifact: draw a
  card` ability accrues one per activation as part of the *cost* (`Costs.PutCounterOnSelf`, always payable) and then
  reads the running count on resolution via `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.BLOOD))`
  to size the life lost, while a second `{U}{B}` ability removes one as its *effect*. Note it is a counter, entirely
  unrelated to the MID Blood *token* — another pure passive counter with no inherent rule.
  `soul` (`Counters.SOUL`): FDN — Ravenous Amulet, whose `{1},{T}, sacrifice a creature: draw` ability accumulates
  one per activation and whose `{4},{T}, sacrifice this: each opponent loses life` ability reads the count via
  `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.SOUL))` — another pure passive counter with no
  inherent rule.
  `possession` (`Counters.POSSESSION`): DSK — Unwilling Vessel, whose Eerie triggers (an enchantment you control
  entering / fully unlocking a Room) each accumulate one and whose dies trigger reads the total counter count via
  `DynamicAmount.ContextProperty(ContextPropertyKey.LAST_KNOWN_TOTAL_COUNTER_COUNT)` to size the X/X Spirit token it
  leaves behind — another pure passive counter with no inherent rule.)
  `fire` (`Counters.FIRE`): TLA — War Balloon (a `{1}` ability accumulates one; a `ConditionalStaticAbility`
  gated on `Conditions.SourceCounterCountAtLeast(Counters.FIRE, 3)` grants `GrantCardType("CREATURE")` so the
  Vehicle is an artifact creature at 3+); reused by later Fated/Fated-Firepower cards — another pure passive
  counter with no inherent rule.
  `conqueror` (`Counters.CONQUEROR`): TLA — Zhao, the Moon Slayer (a `{7}` ability accumulates one; a
  `ConditionalStaticAbility` gated on `Conditions.SourceCounterCountAtLeast(Counters.CONQUEROR, 1)` switches on a
  `SetLandTypesForGroup` making all nonbasic lands Mountains) — another pure passive counter with no inherent rule.
  `net` (`Counters.NET`): LCI — Braided Net (enters with three via an `EntersWithCounters` replacement; its tap
  ability spends them via `Costs.RemoveCounterFromSelf(Counters.NET, 1)`) — another pure passive counter with no
  inherent rule.
  `incubation` (`Counters.INCUBATION`): FDN — Drake Hatcher (a `DealsCombatDamageToPlayer` trigger accumulates
  "that many" via `AddDynamicCounters(Counters.INCUBATION, DynamicAmount.ContextProperty(TRIGGER_DAMAGE_AMOUNT), Self)`;
  an activated ability spends three via `Costs.RemoveCounterFromSelf(Counters.INCUBATION, 3)` to hatch a Drake token) —
  a pure passive resource counter with no inherent rule. Not MTG's Incubate/incubator-token mechanic.
  `bait` (`Counters.BAIT`): FDN — Fishing Pole (the Equipment's *granted* ability accrues one via
  `Costs.PutCounterOnSelf(Counters.BAIT)` + `AddCountersEffect(..., EffectTarget.GrantingSource)`;
  its "equipped creature becomes untapped" trigger spends one through an `IfYouDoEffect` gated on
  `SuccessCriterion.CountersRemoved` to make a Fish token) — another pure passive resource counter
  with no inherent rule.
  `fellowship` (`Counters.FELLOWSHIP`): FDN — Banner of Kinship (enters with one per creature you control of
  the as-enters chosen type via `EntersWithDynamicCounters(CounterTypeFilter.Named(Counters.FELLOWSHIP),
  DynamicAmount.AggregateBattlefield(Player.You, GameObjectFilter.Creature.withChosenSubtype()))`; a
  `GrantDynamicStatsEffect` sized by `DynamicAmounts.countersOnSelf(...)` reads the count back) — a pure
  passive resource counter with no inherent rule.
  `ingenuity` (`Counters.INGENUITY`): SPM — Lady Octopus, Inspired Inventor (two `Triggers.NthCardDrawn`
  triggers — first and second draw each turn — each add one via `AddCounters(Counters.INGENUITY, 1, EffectTarget.Self)`;
  her `{T}` ability reads the count via `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.INGENUITY))`
  inside a `CollectionFilter.ManaValueAtMost` to gate which hand artifact she can free-cast) — a pure passive
  resource counter with no inherent rule.
  `film` (`Counters.FILM`): SPM — Peter Parker's Camera (enters with three via an `EntersWithCounters(
  CounterTypeFilter.Named(Counters.FILM), count = 3, selfOnly = true)` replacement; each activation of its
  `{2}, {T}` copy ability spends one via `Costs.RemoveCounterFromSelf(Counters.FILM, 1)`). A pure "uses left"
  counter with no inherent rule — when it hits zero the activation cost is simply unpayable.
  `wish` (`Counters.WISH`): ELD — Wishclaw Talisman (enters with three via an `EntersWithCounters(
  CounterTypeFilter.Named(Counters.WISH), count = 3, selfOnly = true)` replacement; each activation of its
  tutor ability spends one via `Costs.RemoveCounterFromSelf(Counters.WISH, 1)`). A pure "uses left" counter
  with no inherent rule — when it hits zero the activation cost is simply unpayable, which is exactly the
  printed ruling that the Talisman then sits inert on the battlefield.
  `skewer` (`Counters.SKEWER`): WOE — Rotisserie Elemental (its combat-damage trigger adds one via
  `AddCounters(Counters.SKEWER, 1, EffectTarget.Self)`, and the optional self-sacrifice cashes the tally in
  for an impulse-exile sized by `DynamicAmounts.countersOnSelf(CounterTypeFilter.Named(Counters.SKEWER))`).
  A pure tally counter with no inherent rule.
  `ice` (`Counters.ICE`): SOI — Thing in the Ice (enters with four via an `EntersWithCounters(
  CounterTypeFilter.Named(Counters.ICE), count = 4, selfOnly = true)` replacement; its
  `Triggers.YouCastInstantOrSorcery` trigger removes one and then flips the permanent through a
  `ConditionalEffect(Conditions.SourceCounterCountAtMost(Counters.ICE, 0), TransformEffect(Self))`).
  A "countdown to zero" counter with no inherent rule — the inverse of the `wish`/`film` "uses left" shape:
  read down rather than spent as a cost. Gating the flip on the live count *inside the ability's resolution*
  is what makes the printed ruling hold — removing the last counter any other way never transforms it.
- `stun` — CR 122.1d, a built-in replacement: "If a permanent with a stun counter on it would become untapped,
  instead remove a stun counter from it." Engine-wired through `untapOrConsumeStun` (`rules-engine/core/UntapHelpers.kt`),
  which is invoked from the untap step (`BeginningPhaseManager`), from `TapUntapExecutor`'s untap branch, and from the
  sacrifice/pay continuation resumer. Adding stun counters is done by `AddCounters(Counters.STUN, n, target)`.
- `shield` — CR 122.1c, a built-in replacement **and** prevention effect: "If this permanent would be destroyed
  as the result of an effect, instead remove a shield counter from it" and "If damage would be dealt to this
  permanent, prevent that damage and remove a shield counter from it." One or more counters create a *single*
  effect of each kind, so exactly **one** counter is consumed per damage or destruction event however many are on
  the permanent and however large the damage. Add via `AddCounters(Counters.SHIELD, n, target)` or an
  `EntersWithCounters` replacement (Captain America, Super-Soldier); read the presence back with
  `Conditions.SourceHasCounter` / `.withCounter(Counters.SHIELD)`.
  Engine-wired at the four chokepoints in `rules-engine/core/ShieldCounterHelpers.kt`'s KDoc:
  `DamageUtils.dealDamageToTarget` and `CombatDamageManager` (prevention; combat damage applies it once for the
  whole simultaneous batch per CR 510.2, so a creature blocked by three creatures still spends one counter), and
  `ZoneMovementUtils.destroyPermanent` + `MoveCollectionExecutor`'s destroy branch (replacement).
  What it deliberately does **not** stop, per the official rulings: sacrifice; the lethal-damage/deathtouch
  state-based action (CR 122.1c replaces destruction "as the result of an **effect**"); 0-toughness death. It is
  not regeneration (no tap, no removal from combat, marked damage untouched) and it is not a keyword counter, so
  losing all abilities doesn't switch it off. Unpreventable damage (Leyline of Punishment) is still dealt — but
  still removes a counter. An indestructible permanent never "would be destroyed", so its counter stays unspent.
- `storage` — a passive counter with no inherent rule, like `loot` and `nest`: the card that places
  them is the only thing that reads them. City of Shadows exiles a creature to add one
  (`AddCounters(Counters.STORAGE, 1, Self)`) and taps to add {C} for each
  (`AddColorlessMana(EntityProperty(Source, CounterCount(Named(Counters.STORAGE))))`).
- `hunger` — a pure bookkeeping counter, same no-inherent-rule shape as `storage`: the card counts
  its own pile and acts on the total. Fasting adds one each upkeep
  (`AddCounters(Counters.HUNGER, 1, Self)`) and destroys itself at five, reading the count back
  through `Conditions.SourceCounterCountAtLeast(Counters.HUNGER, 5)`.
- `javelin`, `credit`, `cube`, `tide` — the Fallen Empires named counters, all in the no-inherent-rule
  family above. `javelin` (Icatian Javelineers) is a one-shot resource: the creature enters with one and
  removing it is part of the cost of its ping. `credit` (Icatian Moneychanger) accrues one per upkeep and is
  cashed in for life when the creature sacrifices itself. `cube` (Delif's Cube) is charged by the artifact's
  first ability and spent by its second — the same store-and-spend shape as `storage`. `tide` (Homarid,
  Tidal Influence) is the odd one out: its *exact* count is what matters, not a threshold, because the
  permanent's static effect switches on at exactly one and again at exactly three and sheds all of them on
  reaching four — so read it with an equality condition, not `SourceCounterCountAtLeast`.
- `hone` — CR 122.1j, a built-in Layer 7c pump aimed at a *different* object: "A hone counter on an Equipment
  gives +1/+0 to any creature that Equipment is attached to." Add via `AddCounters(Counters.HONE, n, target)`
  or `AddDynamicCounters(Counters.HONE, amount, target)` — and that is **all** a hone card does; the bonus is
  never a `ModifyStats`/`GrantDynamicStats` on the Equipment. Like `shield` and `stun` the behavior belongs to
  the counter, which is what makes Dwalin, Weaponmaster ("put a hone counter on each Equipment you control")
  work: a Mirrodin Bonesplitter that has never heard of hone still pumps its equipped creature. Engine-wired in
  `StateProjector.collectContinuousEffects`, which synthesizes one `Modification.ModifyPowerToughness(n, 0)` per
  honed Equipment scoped to `AffectsFilter.AttachedPermanent` — so it stacks additively with the Equipment's own
  printed bonus (CR 613.4c covers "effects **and** counters that modify power and/or toughness"), contributes
  nothing while the Equipment is unattached, and is inert on a non-Equipment permanent. Not a keyword counter,
  so it stays out of `KEYWORD_COUNTER_MAP`. Used by Sting, Bilbo's Sword and Dwalin, Weaponmaster (HOB).
- **Keyword counters** (Rule 122.1b) — `flying`, `first strike`, `double strike`, `vigilance`, `lifelink`,
  `indestructible`, `deathtouch`, `trample`, `hexproof`, `reach`, `haste`, `menace`. `StateProjector` grants the matching `Keyword`
  to any permanent carrying one (mapped in `KEYWORD_COUNTER_MAP`, re-applied after Layer 6 so "loses all abilities"
  can't wipe a counter-granted keyword). Add via `AddCounters(Counters.DEATHTOUCH, ...)` etc.; no static ability needed.
  (`reach`: Sagu Pummeler's renew payoff puts a reach counter on a creature. `vigilance`: Aragorn, Company Leader.
  `double strike`: Mai, Jaded Edge's exhaust ability. `haste` / `menace`: Super-Adaptoid, which copies keywords
  off another creature as counters.)
- **Ability counters beyond single keywords** — `decayed` (`Counters.DECAYED`, CR 702.147a, Tarkir: Dragonstorm) grants
  the whole **Decayed** ability (a "can't block" static **and** an attack-triggered end-of-combat sacrifice) to any
  creature that bears one. `StateProjector` projects the `DECAYED` keyword + `cantBlock = true` (initial pass and the
  post-Layer-6 re-apply), and `TriggerDetector.detectDecayedCounterAttackTriggers` schedules the self-sacrifice when a
  decayed-countered creature attacks. Add via `AddCounters(Counters.DECAYED, n, target)` (Rot-Curse Rakshasa's Renew).

Counter effects live in §4 (`AddCounters`, `RemoveCounters`, `Proliferate`, `MoveAllLastKnownCounters`, etc.).

---

## 17. Zones & movement

**Zones** — `BATTLEFIELD`, `HAND`, `LIBRARY`, `GRAVEYARD`, `EXILE`, `STACK`.

**Primitives**

- `MoveToZoneEffect(target, zone, faceDown?, byDestruction?, linked?)` — single-target move. Card
  definitions construct it via the facade `Effects.Move(target, destination, …)` (or the named
  shortcuts `Effects.Destroy/Exile/ReturnToHand/PutOnTopOfLibrary/ShuffleIntoLibrary/…`).
- `MoveTrackedBattlefieldObjectEffect(target, destination, enteredBattlefieldTimestamp?)` — moves
  only the battlefield object identified by both entity ID and entry timestamp. When nested in a
  delayed trigger, target resolution snapshots the timestamp automatically. Use for delayed moves
  that must ignore a permanent that left and returned as a new object (CR 603.7c / 400.7).
- `MoveCollectionEffect(collectionName, zone, faceDown?, linkToSource?, asOwner?, likelyPosition?)` — pipeline move of a
  stored collection.
- `faceDown` (on both move effects) is a nullable **`FaceDownMode`** — `null` = enter face up;
  `MORPH` = face-down with the card's morph cost as its turn-up cost; `MANIFEST` = face-down with
  the card's mana cost as its turn-up cost (only if it's a creature card, CR 701.40b);
  `DISGUISE` = morph plus ward {2} (CR 702.168); `CLOAK` = manifest plus ward {2} (CR 701.58a);
  `HIDDEN` = face down with no turn-up (e.g. exiled face down for Hideaway). The engine derives the
  turn-up data at entry, so a manifested, cloaked or disguised creature reuses the whole morph
  turn-up machinery (special action, payment, flip).
  - **The ward is data on the mode** (`FaceDownMode.faceDownWard`), not an ability of the card
    underneath. Per CR 708.2 a face-down permanent has only the characteristics the rules that made
    it face down list, and disguise/cloak list ward {2} among theirs — so `StateProjector` puts
    `WARD` in the face-down keyword set and `TriggerAbilityResolver` builds the ward trigger from the
    mode instead of from `cardDef.keywordAbilities`. It ends the instant the permanent is turned face
    up. A face-down permanent contributes no *other* triggered ability of its own; ward granted from
    outside (a `GrantWard` static elsewhere) still applies to it.
  - **Turn-up procedures.** `FaceDownTurnUp` is the single place that maps (card, mode) →
    `MorphDataComponent.procedures`. Manifest and cloak contribute both their own "pay the card's
    mana cost" procedure *and* any morph/disguise procedure the card prints, because CR 701.40c/d and
    701.58c/d let the controller pick either — that permanent then offers two `TurnFaceUp` legal
    actions, selected by `TurnFaceUp.procedureIndex`. Megamorph's `faceUpEffect` rides its own
    procedure, so a cloaked megamorph creature flipped for its mana cost correctly gets no counter
    (CR 702.37b).
  - **A cloaked/manifested instant or sorcery that would turn face up** is revealed and left face
    down, firing no turned-face-up trigger (CR 701.40g / 701.58g) — handled in `TurnFaceUpExecutor`.
  - `FaceDownModeComponent(mode)` carries the mode on the permanent while it is face down. It is
    public information (CR 708.6) and reaches the client as `ClientCard.faceDownMode`, which picks
    the face-down helper-card art (morph token / manifest token / "A Mysterious Creature" for both
    disguise and cloak, matching paper).
- `GatherCardsEffect(source, filter, into)` — pipeline gather from a zone into a named collection. `CardSource`
  variants include zones (`FromZone`, `FromMultipleZones`), battlefield queries (`BattlefieldMatching`,
  `ControlledPermanents`), linked exile (`FromLinkedExile`), tapped-as-cost (`TappedAsCost`), and the resolved
  spell/ability targets (`ChosenTargets`). The zone/library sources (`FromZone`, `FromMultipleZones`,
  `TopOfLibrary`) accept a multi-player `player` reference (`Player.Each`, `Player.ActivePlayerFirst`,
  `Player.EachOpponent`) and fan out across every relevant player's copy of the zone in a single gather —
  e.g. "all creature cards in each player's graveyard" (Bringer of the Last Gift). Pair with
  `MoveCollectionEffect(underOwnersControl = true)` to return each card to its owner.
  `revealed = true` makes a public reveal (every player sees the cards while they stay in a hidden
  zone, persisted via `RevealedToComponent` and emitting a reveal event). For a non-public library
  *look* (`revealed = false`), `lookAudience` chooses who privately sees the cards:
  `LookAudience.Controller` (default — Scry / Surveil / look-at-top-N), `LookAudience.Opponent`
  ("an opponent looks at the top N of your library"), or `LookAudience.None` (no one is auto-shown;
  a downstream decision is the only window — used by **Sauron's Ransom**, where the opponent who
  partitions sees the cards through their own `SelectFromCollection` decision but the caster does
  not). `lookAudience` is ignored when `revealed = true` or for non-library sources. To then turn a
  single pile face up for everyone — including the caster, before a `ChoosePileEffect` — re-gather
  that pile via `GatherCards(FromVariable("pile"), revealed = true)`; any pile never revealed
  renders to the caster as opaque card backs (Sauron's Ransom's concealed face-down pile).
- `CaptureControllersEffect(from, storeAs)` — snapshot each entity's current controller into a parallel
  `List<EntityId>` under `storedCollections[storeAs]`. Required when a later step needs "who controlled
  this card before it left the battlefield" — `ControllerComponent` is stripped on move-out.
  Also captures a spell's stack controller before countering it, even when its caster is not its owner
  (Broken Ambitions). Battlefield permanents use projected control. Pair `captureControllers` with
  `forEachCaptured` over the original collection when the rider applies regardless of whether a move
  succeeded; retain the snapshot through intervening decisions such as counter payments and clashes.
- `ForEachCapturedControllerEffect(collection, originalCollection, controllerSnapshot, countVariable?, effects)` —
  cross-references a post-move `collection` against an `originalCollection` + parallel `controllerSnapshot` to
  build per-controller tallies, then runs `effects` once per controller (turn order from the active player). Each
  iteration sets `context.controllerId` to the controller (so `Player.You` / `EffectTarget.Controller` resolve to
  them) and writes the tally into `storedNumbers[countVariable]` (default `"iterationCount"`) for
  `DynamicAmount.VariableReference` to read. Outer `storedCollections` are preserved (unlike
  `ForEachPlayerEffect`). Used by Builder's Bane via the
  `GatherCards(ChosenTargets) → CaptureControllers → MoveCollection(Destroy, storeMovedAs) → ForEachCapturedController`
  shape.
- `ForEachInCollectionEffect(collection, effect)` — run `effect` once per entity in a named pipeline collection
  (snapshotted at resolution), with `pipeline.iterationTarget` set to that entity. Lowers to
  `ForEachEffect(IterationSpace.Collection(...))` — see the unified ForEach entry under "Sequencing &
  conditional". Collection-based sibling of
  `ForEachInGroupEffect` (which iterates a battlefield filter): use it to apply a per-entity effect to a *chosen*
  set rather than a re-evaluated filter. Pair with a single-target effect on `EffectTarget.Self` — e.g.
  `ForEachInCollection(nonChosenPile, Effects.CantAttack(EffectTarget.Self))` gives each creature in a chosen pile
  its own snapshot can't-attack floating effect (Fight or Flight / Stand or Fall; creatures entering after the
  split are unaffected).
- `SelectFromCollectionEffect(from, into, selectCount?, allowZero?, alwaysPrompt?, restrictions?)` — let a player pick
  from a collection. `restrictions` (`List<SelectionRestriction>`) cap and trim the picks server-side: `OnePerCardType`,
  `OnePerColor(matchControllerPermanentColors?)`, `OnePerCardName`, `OnePerPower`, `TotalManaValueAtMost(max)` /
  `TotalManaValueAtMost(maxAmount = <DynamicAmount>)` (the dynamic overload caps the sum at a resolved amount — e.g.
  `DynamicAmount.XValue` for "with total mana value X or less"; the executor resolves it to a fixed cap up front so every
  downstream consumer sees an integer — The Rise of Sozin // Fire Lord Sozin),
  `TotalPowerAtMost(max)`, `OnePerBasicLandType`, `ReducedMinimumIfMatches(reducedMinimum, filter, requiredMatches?)`, and
  `MaxAffordablePayment(manaPerSelected, payer?)`. `TotalPowerAtMost(max)` caps the sum of selected creatures'
  **projected** power at `max` (a creature with undefined power contributes 0); it is the power analogue of
  `TotalManaValueAtMost` and surfaces `maxTotalPower` on `SelectCardsDecision` so the UI shows a running "Total power: X / N"
  and disables over-cap picks while the server trims oversubmits in response order — used for "choose any number of
  creatures you control with total power N or less, then sacrifice the rest" (Destined Confrontation). `OnePerPower` keeps at most one card of each *printed* power
  (a card with no fixed power — no printed P/T, or a characteristic-defining `*` — can't be kept and bottoms out,
  like a typeless land under `OnePerBasicLandType`); pair it with the `CreatureOrVehicle` filter for "any number of
  creature and/or Vehicle cards with different powers" (Rip, Spawn Hunter). `OnePerBasicLandType` keeps at most one
  land of each basic land type (a kept land claims
  *every* basic type it has) and — unlike `OnePerColor`, where a colourless card is unconstrained — a land with no
  basic land type can't be kept at all (Global Ruin: "chooses a land of each basic land type, then sacrifices the
  rest"). Each restriction also exposes a boolean flag on `SelectCardsDecision` (`onePerBasicLandType`, …) so the UI
  can disable redundant picks. `ReducedMinimumIfMatches` exposes `conditionalMinimums` on `SelectCardsDecision` so the
  UI and server can accept one matching card for "discard two unless you discard a creature card" while rejecting one
  nonmatching card. `MaxAffordablePayment` caps the selection at
  `floor(payer's available mana / manaPerSelected)` (floating + untapped sources) — pair it with a downstream
  `Gate.MayPay` over `PayDynamicMana` at the same rate so a player can never select a set whose total cost is
  unpayable and silently forfeit the payoff; a cap of zero (under `ChooseAnyNumber`) skips the selection prompt
  entirely (Magnetic Mountain: "choose any number … and pay {4} for each creature chosen this way").
  - `chooser` (`Chooser`, default `Controller`) — who makes the selection: `Controller`, `Opponent`, `TargetPlayer`
    (`context.targets[0]` treated as the player), `TriggeringPlayer`, `SourceController` (the source's controller,
    ignoring per-iteration swaps), `ControllerOfSelection` (the controller of the cards in `from` — resolved from the
    first card's projected controller), `DefendingPlayer` (the player the source is attacking, CR 508.1), or
    `ControllerOfTarget` (the controller of the targeted *permanent*,
    `context.targets[0]`, falling back to its owner once it has left the battlefield). Use `ControllerOfSelection` for
    "their controller chooses…" where the deciding player is whoever controls the gathered cards and may be you or an
    opponent (Barrin's Spite: gather the two targeted creatures, their controller sacrifices one, the other is returned
    to hand). Use `ControllerOfTarget` for "destroy target permanent. Its controller searches/chooses…" where the
    targeted permanent's controller performs a follow-up (Magmatic Hellkite: destroy target nonbasic land, *its
    controller* searches for a basic). Use `DefendingPlayer` for an attack trigger whose payoff is the defending
    player's own choice — "defending player discards three cards" (Mindstab Thrull) is picked from *their* hand, not
    the attacker's; it reads combat off the ability's source and keeps answering after a self-sacrifice has taken that
    source off the battlefield (CR 508.1 / 608.2h), the same last-known leg `Player.DefendingPlayer` uses. The same
    `chooser` set is accepted by `ChoosePileEffect`.
  - **`Chooser.Opponent` in multiplayer.** "An opponent" is *one* opponent, and the controller of the spell or
    ability picks which one (CR 601.7a / 602.3a for cast/activation-time choices; resolution-time choices follow the
    same principle and cards say so in their rulings — Curator of Destinies: "You decide which opponent chooses the
    pile"). The engine handles that for you: with several opponents the step first pauses on a `ChooseOptionDecision`
    for the controller listing the opponents by name, then re-runs itself and presents the real choice to the named
    opponent. With a sole opponent the choice is forced and nothing extra is prompted, so two-player games are
    unaffected. Each "an opponent chooses" step in one resolution gets its own pick — the choice is
    resolution-scoped, not recorded on the source. All of this lives in the engine's `ChooserResolution`, so any
    effect carrying a `Chooser` inherits it; card definitions just say `Chooser.Opponent`. (For the durable,
    cast-time "you may promise **an opponent** a gift"-style recipient choice, use
    `Effects.ChooseOpponentForSource` + `Player.ChosenOpponent` instead — that one is stored on the source and
    persists past the resolution.)
  - **`Chooser.ChosenOpponent`** is the decision-side twin of `Player.ChosenOpponent`: the opponent a
    preceding `Effects.ChooseOpponentForSource` already named for this source makes the choice. Use it,
    not `Chooser.Opponent`, whenever the *same* opponent has to appear in two steps of one mechanic —
    `Chooser.Opponent` re-picks per step, so in a multiplayer game a mechanic that reads one opponent's
    library and then asks that opponent to decide could split across two different players. Clash
    (CR 701.30b) is the case in hand. Unresolvable if no choice has been made, so a card using it must
    run `Effects.ChooseOpponentForSource` first.

**Linked exile**

- `Effects.ExileGroupAndLink(filter, storeAs?)` — exile matching permanents linked to source.
- `Effects.ReturnLinkedExile` — return all to controller.
- `ReturnLinkedExileUnderOwnersControl` — return to owners.
- `ReturnLinkedExileToHand` — return to hand.
- `ReturnLinkedExileToZoneExiledFrom` — return each card to the zone it was exiled from (CR 610.3).
- `ReturnOneFromLinkedExile` — return one chosen card.
- `CardSource.FromLinkedExile()` — play permission targeting linked-exile pile.
- `CardSource.FromExile(name)` — play permission for a named exile zone.

**Face-down**

- `PutOntoBattlefieldFaceDown(count, target?)` — enter face-down (morph shape).
- `Triggers.TurnedFaceUp` — fires when source flips face-up.
- UI label: `"Turn Face-Up"` (used by E2E `selectAction("Turn Face-Up")`).

---

## 18. Components (set indirectly by effects)

### Permanent

- `ChosenModeComponent` — chosen entry mode (Sieges, modal permanents).
- `TypeLineOverrideComponent` — temporary type-line edits.
- `CountersComponent` — all counters on the permanent.
- `EnchantedCreatureComponent` — reference to attached creature (Auras).
- `EquippedCreatureComponent` — reference to equipped creature.
- `LinkedExileComponent` — linked exile pile attached to source.
- `ExileOnLeaveComponent` — replace next zone change with exile.
- `MayPlayFromExileComponent` — owner may play this from exile.
- `TappedStateComponent` — tap state.
- `FaceDownComponent` — face-down state.
- `ControllerComponent` — current controller.
- `ProtectionComponent` — protection from colors/types.
- `CantAttackComponent` / `CantBlockComponent` — combat restrictions.

### Player

- `PlayerCitysBlessingComponent` — you have City's Blessing.
- `TheRingComponent` — you have the Ring emblem; `temptCount` gates its four abilities (CR 701.54).
- `RingBearerComponent` — designates a creature as a player's Ring-bearer (on the creature, not the player).
- `SpellsCantBeCounteredComponent` — your matching spells can't be countered.
- `LifeGainedAmountThisTurnComponent` — accumulator for life gained.
- `LifeLostThisTurnComponent` — marker that you've lost life this turn.
- `PlayerAttackedThisTurnComponent` — marker that you've attacked this turn.
- `PlayerAttackersThisTurnComponent` — list of attackers declared this turn.
- `PlayerAttackedPlayersThisTurnComponent` — set of defending players you "attacked" this turn (CR
  508.6); read by `PlayerAttackedPlayerThisTurn`.
- `LandDropsComponent` — lands played this turn.
- `FoodSacrificeThisTurnComponent` — marker that you sacrificed a Food this turn.
- `SpellsCastThisTurnByPlayer` — count of spells you cast this turn.

Card authors rarely reference these directly; they are created/updated by the matching effect or trigger.

---

## 19. Named-mechanic composites

- **Cycling / Typecycling / Basic landcycling** — `KeywordAbility.Cycling(cost)`, `Typecycling(type, cost)`,
  `BasicLandcycling(cost)`; unified via `TypecyclingVariant(cost, searchFilter, description)` in `TypecycleCardHandler`.
  A plain cycling cost may contain `{X}`; `CycleCardHandler` announces it (CR 107.3a), resolves the cost via
  `ManaCost.withXAs(x)` so the ordinary payment path sees no X, and stamps it on `CardCycledEvent.xValue` for the
  cycling trigger. See the `Cycling(cost)` entry in §Keyword abilities for the full flow.
- **Cases (CR 719)** — `typeLine = "Enchantment — Case"` (`Subtype.CASE`, `TypeLine.isCase`) plus the
  four DSL helpers in `dsl/mechanics/CaseDsl.kt`. No new ability kind: a Case's two special lines lower
  onto vocabulary that already existed.
  - `toSolve(condition)` — the **"To solve — [condition]"** ability (CR 719.3a) = "At the beginning of
    your end step, if [condition] and this Case is not solved, this Case becomes solved". Emits a
    `Triggers.YourEndStep` triggered ability whose `interveningIf` is
    `All(condition, Not(SourceIsSolved))` and whose effect is `Effects.BecomeSolved()`. Both halves are
    re-checked on resolution (CR 603.4), so a Case whose condition is undone in response stays unsolved,
    and the `not solved` half is what stops a solved Case re-triggering every turn.
  - `solvedStaticAbility { }` / `solvedTriggeredAbility { }` / `solvedActivatedAbility { }` — the
    **"Solved — [ability]"** keyword (CR 702.169) in its three shapes. Each is the ordinary builder with
    the gate pre-applied, matching the rule exactly: a static ability gets `condition = SourceIsSolved`
    ("as long as this Case is solved", 702.169b), a triggered ability gets
    `triggerRestriction = SourceIsSolved` ("triggers only if this Case is solved", 702.169c — a trigger
    restriction, *not* an intervening-if, so an ability that triggered while solved still resolves), and
    an activated ability gets `ActivationRestriction.OnlyIfCondition(SourceIsSolved)` ("activate only if
    this Case is solved", 702.169d). Each helper ANDs its gate with whatever condition/restriction the
    block sets itself, so a Solved ability that is also sorcery-speed-only keeps both.
  - The **solved designation** itself (CR 719.3b) is `SolvedComponent` — engine state, neither an ability
    nor a copiable value. `Effects.BecomeSolved` stamps it, `Conditions.SourceIsSolved` / `.solved()` /
    `StatePredicate.IsSolved` read it, `ClientCard.isSolved` surfaces it as a card badge, and
    `ZoneMovementUtils.stripBattlefieldComponents` drops it when the Case leaves the battlefield.
  - The **moment** it flips is `Triggers.WheneverYouSolveACase` (§ Cases (CR 719) under triggers) —
    "when this creature enters **and whenever you solve a Case**" (Case File Auditor). That is the
    event, not the standing state; `SourceIsSolved` is the state.
  - An **unsolved** Case shows how close its criterion is as a `current/required` badge — Case of the
    Burning Masks counts 0/3 up to 3/3 as sources deal damage — and drops it once solved. That falls
    out of the generic intervening-if badge (§ Triggered abilities) reading inside the
    `All(condition, Not(SourceIsSolved))` composite that `toSolve` emits; no Case-specific plumbing.
  - A Case's remaining lines — the "When this Case enters" ability, or an always-on static like Case of
    the Ransacked Lab's cost reduction — are plain `triggeredAbility { }` / `staticAbility { }` blocks;
    they function whether or not the Case is solved.
- **Plot (CR 718)** — `KeywordAbility.plot(cost)`. Engine wires a sorcery-speed `PlotEnumerator` + `PlotCardHandler`
  that pays the plot cost, exiles the card face-up from hand, stamps `PlottedComponent(controllerId, turnPlotted)` +
  `PlayWithoutPayingCostComponent`, and adds a permanent `MayPlayPermission` gated by `SourcePlottedOnPriorTurn`.
  The cast-from-exile path is the standard `MayPlayPermission` flow in `CastFromZoneEnumerator` — `permanent = true`
  keeps the grant alive across end-of-turn cleanup. Emits `CardPlottedEvent` / `ClientEvent.CardPlotted`.
- **Adventure (CR 715)** — `layout = ADVENTURE` + `cardFaces[0]` Adventure spell; DSL:
  `card { adventure("Name") { spell { … } } }`. The primary face may be a **land** (`Land — Town`) instead of a
  creature — FIN's "Town land // spell" DFCs (Ishgard, the Holy See // Faith & Grief, …). No new layout: resolving
  the Adventure exiles the card with the same generic `MayPlayPermission`, which `CastFromZoneEnumerator` /
  `PlayLandHandler` already honor as a *play-the-land-from-exile* permission. The only seam beyond the creature case
  is `CastSpellEnumerator` — it now enumerates the Adventure spell face for a land-primary card (the land itself is
  played via `PlayLandEnumerator`). First land users: Ishgard, the Holy See; Jidoor, Aristocratic Capital; Lindblum,
  Industrial Regency; Midgar, City of Mako; Zanarkand, Ancient Metropolis.
- **Omen (Tarkir: Dragonstorm)** — `layout = OMEN` + `cardFaces[0]` Omen spell; DSL:
  `card { omen("Name") { spell { … } } }`. Reuses the Adventure cast/enumeration path (`enumerateSecondaryFace`,
  cast via `CastSpell.faceIndex = 0`), but `StackResolver` routes the resolving Omen to `Zone.LIBRARY` and shuffles
  the owner's library (`shuffleOwnerLibrary` + `LibraryShuffledEvent`) instead of exiling with a `MayPlayPermission`.
  No new effect/component — the layout enum drives the resolution fork. First user: Dirgur Island Dragon //
  Skimming Strike.
- **Modal DFC, spell back (CR 712)** — `layout = MODAL_DFC` + `cardFaces[0]` back face; DSL:
  `card { modalBack("Name") { imageUri = …; spell { selfExile(); … } } }`. Cast either face from hand (back via
  `CastSpell.faceIndex = 0`); reuses the Adventure cast/enumeration path (`enumerateSecondaryFace`) but with no
  exile-then-recast linkage at resolution. `StackResolver` reads the cast face's `selfExileOnResolve`, and the back
  art rides on `CardFace.imageUri` → `CardComponent.backFaceImageUri`. First user: Flamescroll Celebrant.
- **Modal DFC, permanent back (CR 712.3)** — `layout = MODAL_DFC` + a full `backFace`, via
  `CardDefinition.modalDoubleFacedPermanent(front, back)`. Reuses the **disturb** path rather than the Adventure
  one, because the card goes on the stack transformed: `ModalDfcCasts.castFace` is the single
  can-I-and-as-which-face policy (mirroring `DisturbCasts`), `CastZoneResolver.modalBackCastFace` is its hand-side
  permission check, `CastSpellEnumerator.enumerateModalBackFace` surfaces the offer, and `CastSpellHandler` reads
  every characteristic off that face (`transformedFace`) and passes `castTransformed = true` to `StackResolver`.
  Cost is the back's own mana cost (`AlternativeCostType.MODAL_BACK_FACE` — the enum entry is plumbing, not a real
  alternative cost; CR 712.11b calls it choosing a face). Because the back is a real `backFace`, transform and the
  client's flip preview work with no extra wiring. The one place the shared disturb path is *not* shareable is
  **mana value**, which is the only characteristic the CR treats differently for the two layouts — see the entry
  below. Timing comes off the face being cast, not the front (CR 712.11c), so a permanent back is sorcery-speed
  unless *it* has flash. First users: the MSH hero cycle.
- **Mana value across a transform (CR 712.8c / 712.8e / 712.8f)** — the one characteristic where nonmodal and modal
  DFCs diverge, so it is the one thing the shared face-swap machinery has to fork on. A **nonmodal** DFC computes
  its mana value from the **front** face's mana cost while the back is up — on the stack (CR 712.8c, a disturb
  cast) and on the battlefield (CR 712.8e) — which matters because a transform back prints no mana cost at all, so
  reading the face directly would make a transformed Delver of Secrets mana value 0. A **modal** DFC has no such
  exception (CR 712.8f): its back keeps its own printed cost. `dfcBackFaceManaValue(frontDef, frontManaValue)` is
  the single place that decides, and it feeds `CardComponent.manaValueOverride` (which `CardComponent.manaValue`
  prefers over `manaCost.cmc`) through `buildCardComponentForDfcFace` — so all three flip routes
  (`flipDfcInPlace`, `returnDfcFace`, and `StackResolver`'s cast-transformed swap) agree, and every reader of
  `manaValue` (predicates, `EntityNumericProperty.ManaValue`, emerge) sees the right number with no per-call-site
  handling. `StackResolver` reuses the same value for `SpellCastEvent.manaValue` (hence
  `ContextPropertyKey.TRIGGERING_SPELL_MANA_VALUE`) and `CastSpellHandler` mirrors it for its `CastSpellRecord`.
  Not modelled: CR 712.8e's other clause, that a permanent *copying* a nonmodal back face has mana value 0
  (CR 202.3b) — that is a property of the copy, not of the flip. Pinned by `DfcManaValueTest` and `DisturbKeywordTest`.
- **Prepare / Prepared (Secrets of Strixhaven)** — `layout = PREPARE` + `cardFaces[0]` prepare spell; DSL:
  `card { prepare("Name") { spell { … } } }`. The creature is only cast as itself. A creature that carries
  `Keyword.PREPARED` ("This creature enters prepared") becomes prepared on enter
  (`StackResolver.enterPermanentOnBattlefield`, gated on the keyword). A PREPARE-layout creature *without* the
  keyword (Leech Collector, Joined Researchers) only becomes prepared via `Effects.BecomePrepared(target)`
  (`BecomePreparedExecutor`). Both paths call the shared `PreparationLogic.makePrepared`, which
  creates a stack-style copy of the prepare spell in the controller's exile carrying
  `PreparedSpellCopyComponent(sourceId)`, stamps `PreparedComponent(exileCopyId)` on the creature, and grants a
  permanent `MayPlayPermission` for the copy. `CastFromZoneEnumerator` recognizes the copy and offers it as
  `CastSpell(..., faceIndex = 0)` from `EXILE` using the prepare face's cost/targets. Casting the copy
  (`StackResolver.castSpell`) strips the source's `PreparedComponent` and consumes the permission; the copy resolves
  via the face script and ceases to exist (`CopyOfComponent`). The exiled copy is exempt from the 707.10a
  phantom-copy SBA (`PhantomCardCopiesCheck`) while linked, and that same check removes it once the source leaves
  the battlefield or stops being prepared. First users: Adventurous Eater // Have a Bite, Landscape Painter //
  Vibrant Idea; becomes-prepared-via-trigger: Leech Collector // Bloodletting, Joined Researchers // Secret
  Rendezvous (end-step trigger gated on `Conditions.OpponentHasMoreCardsInHand`).
- **Hideaway N** — `KeywordAbility.hideaway(n)` (display, "Hideaway N") + `MoveCollectionEffect(faceDown = FaceDownMode.HIDDEN,
  linkToSource = true)` + `CardSource.FromLinkedExile()`; no special engine plumbing needed.
- **Ascend / City's Blessing** (CR 702.131) — on a **permanent**, `keywords(Keyword.ASCEND)` is the whole
  implementation: ascend there is a *static* ability (702.131b, "**any time** you control ten or more
  permanents…"), and the engine's `AscendCitysBlessingCheck` state-based action grants the designation to
  any player controlling an ascend permanent once they control ten permanents. Do **not** write it as an
  enters-the-battlefield trigger — that samples the count once, on the turn a cheap creature is least
  likely to meet it, and never looks again. On an **instant or sorcery** ascend is a spell ability
  (702.131a), so those cards spell it out with `Effects.GainCitysBlessing()` in the spell's effect, as does
  any card that just says "you get the city's blessing".
  Read it back with `Conditions.YouHaveCitysBlessing` / `SourceProjectionCondition.ControllerHasCitysBlessing`;
  the marker is `PlayerCitysBlessingComponent` and is never removed (702.131b/c, "for the rest of the game").
  Both the read and the two writers go through `CitysBlessingService`, which also evaluates the ascend
  condition *live* — that is what makes "create a token, then if you have the city's blessing…" come out
  right when the token itself is your tenth permanent (Ocelot Pride), since state-based actions aren't
  polled mid-resolution.
- **Storied / Enduring Story** (The Hobbit, CR 702.195) — the same shape as ascend-on-a-permanent, with a
  different threshold, and authored the same way: `storied()` adds `Keyword.STORIED` and nothing else,
  because 702.195a is a *static* ability ("**any time** you control three or more permanents that are
  artifacts, Sagas, and/or legendary…") handled by the engine's `StoriedEnduringStoryCheck` state-based
  action. There is no spell-ability form — storied only ever appears on permanents — so unlike the city's
  blessing there is no grant *effect*, only the SBA.

  ```kotlin
  storied()
  staticAbility {                                       // "As long as you have an enduring story, …"
      ability = ConditionalStaticAbility(
          ability = GrantKeyword(Keyword.VIGILANCE, GroupFilter.source()),
          condition = Conditions.YouHaveEnduringStory
      )
  }
  ```

  Read it back with `Conditions.YouHaveEnduringStory` (backed by `PlayerHasEnduringStory(Player.You)`);
  the marker is `PlayerEnduringStoryComponent` and is never removed (702.195a, "for the rest of the
  game"). Read and write both go through `EnduringStoryService`, which evaluates the storied condition
  *live* for the same mid-resolution reason as `CitysBlessingService`. Two things the count gets right and
  a hand-rolled one usually doesn't: the three categories are a **union over permanents**, so a legendary
  artifact is one qualifying permanent rather than two; and the whole scan reads *projected* state, so a
  granted storied, an animated artifact, or a stolen permanent all count for the right player. First
  users: Ori, Keeper of Songs; Óin the Brave; Thorin Oakenshield.
- **Speed / Start your engines! / Max speed** (Aetherdrift, CR 702.178–702.179) — a player's speed is
  an `Int` 0–4 (`Speed.NONE` / `Speed.STARTING` / `Speed.MAX` in `core/Speed.kt`) held by
  `PlayerSpeedComponent`. It only ever rises, is clamped at 4, and is never removed — like the city's
  blessing. Author it with two `CardBuilder` helpers and add nothing else:

  ```kotlin
  startYourEngines()                                     // CR 702.179a — just the keyword
  maxSpeed { keywords(Keyword.DOUBLE_STRIKE) }            // "Max speed — This creature has double strike."
  maxSpeed {                                              // any ability kind works inside the block
      activatedAbility { cost = Costs.Tap; effect = Effects.AddMana("{R}{R}"); manaAbility = true }
      triggeredAbility { trigger = Triggers.BeginningOfYourEndStep; effect = … }
      staticAbility { ability = ModifyStats(1, 2, GroupFilter.source()) }
  }
  ```

  - `startYourEngines()` adds only `Keyword.START_YOUR_ENGINES`. Setting a controller's speed to 1 is a
    *state-based action* (CR 704.5aa, `StartYourEnginesCheck`), not a trigger, so nothing is authored on
    the card. Because the check reads projected keywords and projected controllers, gaining control of
    a permanent with the keyword or having the keyword granted to one both start speed for free.
  - `maxSpeed { }` adds display-only `Keyword.MAX_SPEED` and gates each ability it declares on
    `Conditions.YouHaveMaxSpeed` using that ability kind's existing vocabulary — statics via
    `ConditionalStaticAbility`, activated via `ActivationRestriction.OnlyIfCondition`, triggered via
    `interveningIf` (CR 603.4). No new ability type; activated/triggered labels get the printed
    "Max speed — " prefix. Several abilities may share one block (Tsagan, Raider Warlord).
    Two static kinds are exceptions to the wrapper, both for the same reason — their read site scans
    the *raw* static list with `filterIsInstance` and never unwraps a conditional, so a
    `ConditionalStaticAbility` would hide them entirely. Both carry their own condition slot, and the
    builder folds the gate into it. Authoring is unchanged — declare them in the block and the builder
    picks the right seam:
    - `ModifySpellCost` → its `CostGating.OnlyIf` slot (cost calculation; Racers' Scoreboard,
      "Max speed — Spells you cast cost {1} less to cast").
    - `MayCastSelfFromZones` → its `condition` slot (`CastFromZoneEnumerator.enumerateIntrinsicZoneCast`
      / `CastZoneResolver.findMayCastSelfFromZoneAbility`; Lightwheel Enhancements, "Max speed — You
      may cast this card from your graveyard"). The condition is evaluated in the *casting player's*
      context at both read sites, which is what lets a max-speed ability function from a zone where
      the card isn't a permanent at all — per the CR ruling that *"if the granted ability functions in
      a zone other than the battlefield, the max speed ability does too."*
  - Raising speed is `Effects.IncreaseSpeed(amount, target)`. The inherent CR 702.179d trigger
    ("Whenever one or more opponents lose life during your turn, if your speed is less than 4, your
    speed increases by 1. This ability triggers only once each turn.") is synthesized per player by
    `SpeedAbilities.inherentSpeedIncrease` and handed out by `TriggerDetector.detectInherentSpeedTriggers`
    with the *player entity* as its source — which is what makes the generic `oncePerTurn` tracker and
    its cleanup reset enforce the once-each-turn clause with no new machinery.
  - Read speed with `DynamicAmounts.speed(player)` ("where X is your speed"); a player with no speed
    reads as 0 (CR 702.179f), so no has-speed guard is ever needed. `SpeedService` is the single writer,
    holding the clamp and the CR 702.179c "no speed + N ⇒ N" rule.
  - Client: `ClientPlayer.speed` (public info, unmasked) plus `ClientEvent.SpeedChanged`; the UI renders
    a four-bar `SpeedGauge` that redlines at max speed.
  - **Replacement effects** use a third seam, `maxSpeed { replacementEffect(…) }`, for the same reason
    as the two static exceptions: they're read straight off `ReplacementEffectSourceComponent` at ~20
    independent interception sites, so a `ConditionalStaticAbility`-style wrapper would be invisible to
    every one of them. The builder folds the gate into the effect's own `restrictions` list, which only
    some replacement types have — anything else throws rather than silently emitting an ungated effect.
    Vnwxt, Verbose Host ("Max speed — If you would draw a card, draw two cards instead") is a
    `ReplaceDrawWithEffect`; Far Fortune, End Boss's damage rider is a `ModifyDamageAmount`. Know which
    player the restrictions read before reaching for it: the damage family evaluates them against the
    *source's controller* (so Far Fortune taxes opponents while gating on your speed), while the draw /
    life-total ones read the *affected* player — fine for a `Player.You` pattern like Vnwxt's own draws,
    wrong for a `Player.EachOpponent` one.
- **Siege (named-mode entry)** — `EntersWithChoice(ChoiceType.MODE, modeOptions = ...)` + `SourceChosenModeIs("id")`.
- **Morph** — `morph = "{2}{U}"` (top-level) + `morphFaceUpEffect` for "as it turns face up".
- **Disguise** (CR 702.168) — `disguise = "{1}{W}"` (top-level), or `disguiseCost` for a non-mana
  cost. Morph plus ward {2}, and that is the whole difference: the same sorcery-speed `{3}`
  face-down cast (`MorphCastEnumerator`), the same turn-face-up special action, and the ward carried
  as a face-down characteristic by `FaceDownMode.DISGUISE` rather than as an ability of the card
  (see the `FaceDownMode` notes under the move effects). Pair with `Triggers.TurnedFaceUp` for the
  common "when this creature is turned face up, …" payoff, or
  `Triggers.or(Triggers.EntersBattlefield, Triggers.TurnedFaceUp)` for the "enters **or** is turned
  face up" wording (Rakish Scoundrel) — one ability with two conditions, which must fire once on
  either route, not twice. For the **replacement** wording "As this creature is turned face up, …"
  (Bubble Smuggler) reach for `disguiseFaceUpEffect` instead: it applies inside the special action,
  so it can't be responded to, where the `Triggers.TurnedFaceUp` form goes on the stack first.
- **Cloak** (CR 701.58) — no keyword to author: it is `FaceDownMode.CLOAK` on whichever move puts
  the card onto the battlefield, exactly as manifest is `FaceDownMode.MANIFEST`. For the common
  "look at the top N, cloak M" shape use
  `Patterns.Library.lookAtTopAndKeep(keepDestination = ToZone(BATTLEFIELD), keepFaceDown = CLOAK)`
  (Hide in Plain Sight).
- **Warp** — `warp = "{1}{R}"`; alt-cost that exiles end of turn. Like morph and cycle, a warp card
  always surfaces *both* cast options — its normal cost and its warp cost — in the action window, even
  when only one (or neither) is payable; the unpayable side appears grayed out (CR 118.9a, the caster
  chooses which cost to use). The warp action is enumerated by `CastFromZoneEnumerator`, which also
  emits the grayed-out normal-cast placeholder when the normal cost is unaffordable (mirroring
  `MorphCastEnumerator`). The end-step exile is a delayed trigger whose `WarpExileEffect` snapshots
  the permanent's battlefield-entry timestamp (`enteredBattlefieldTimestamp`); at resolution it only
  exiles the *same object* — a warped permanent that left the battlefield and returned before the
  end step (blink, e.g. Daydream) is a new object (CR 603.7c / 400.7) and stays permanently.
- **Dash** — `dash = "{1}{R}"`; hand-only alt-cost (CR 702.109). Mirrors warp's cast-window and
  timestamp-guarded delayed-trigger shape, but simpler: no graveyard variant, no recast-from-exile
  permission. The cast is tracked with a `DashedComponent` marker (not a floating continuous
  effect — an EntityId can be reused across a later fresh cast of the same card, so a
  `Duration.Permanent` effect keyed to the id could misfire) that `StateProjector` reads live to
  grant haste, and a delayed trigger fires `MoveTrackedBattlefieldObjectEffect(..., HAND)` at
  `Step.END`, snapshotting
  `enteredBattlefieldTimestamp` the same way warp's exile does — a dashed permanent blinked before
  the end step is a new object and stays on the battlefield; one that already left (died, was
  bounced) is left where it is, per the official ruling.
- **Evoke** — `evoke = "{U}"`; pay alt cost, sacrifice on ETB.
- **Sneak** — `sneak("{1}{U}")`; declare-blockers-step alt cost (pay mana + return an unblocked attacker you control to hand); a resolving permanent enters tapped and attacking the same defender. `Conditions.SneakCostWasPaid` reads the rider flag.
- **Ninjutsu** — `ninjutsu("{1}{U}{B}")`; the canonical CR 702.49 keyword that **Sneak** reflavors. Same declare-blockers alt cost and tapped-and-attacking entry, shared via `KeywordAbility.ninjutsuStyleCost`. *Kaito, Bane of Nightmares* (DSK).
- **Splice** — `splice("{2}{R}{R}")` (CR 702.47); reveal from hand as you cast an Arcane spell, pay the splice cost as an *additional* cost, and that spell gains this card's rules text — the card itself stays in hand. The spell keeps its own characteristics (702.47c); the spliced text resolves after the main spell's (702.47b) with its own targets. *Through the Breach* (CHK / INR).
- **Earthbend** — `Effects.Earthbend(amount, target)` composes AnimateLand + GrantKeyword + AddCounters + granted
  self-triggers (no fake keyword). `amount` is an `Int` for "Earthbend N" (Earthbending Lesson) or a `DynamicAmount`
  for "Earthbend X, where X is …" (Rockalanche — X = the number of Forests you control), which counts X at resolution
  via `AddDynamicCounters`.
- **Airbend** (Avatar: The Last Airbender) — `Effects.Airbend(cost = {2})` / `Effects.AirbendAll(filter, excludeSelf, excludeChosenTargets, cost = {2})`.
  *"Airbend target permanent"* = "Exile it. While it's exiled, its owner may cast it for {2} rather than its mana
  cost." Composes a pipeline (no fake keyword): `GatherCards(ChosenTargets)` → `MoveCollection(→ EXILE, storeMovedAs)`
  → `GrantMayPlayFromExile(ownerControls = true, expiry = Permanent, fixedAlternativeManaCost = {2})`. **Target-agnostic
  by design:** the *card* declares the targeting shape via its `TargetRequirement` ("up to one", "any number of",
  "another", "you control", "target nonland permanent"), and `Effects.Airbend()` airbends whatever was chosen — so one
  effect serves every airbend card. `AirbendAll(filter)` swaps the gather to `CardSource.BattlefieldMatching` for "airbend
  all other creatures" — pass `excludeChosenTargets = true` (and `excludeSelf = false` for a sorcery) so the spared "other"
  is the spell's chosen target, backing **Avatar's Wrath** ("Choose up to one target creature, then airbend all other
  creatures."); `CardSource.BattlefieldMatching.excludeChosenTargets` drops `EffectContext.targets` from the gather, the
  chosen-target sibling of `excludeSelf`/`excludeTriggering`. The new piece is **`fixedAlternativeManaCost`** on `GrantMayPlayFromExile`: it
  stamps `PlayWithFixedAlternativeManaCostComponent(controllerId, fixedCost)` on each exiled card, which the legal-action
  enumerator (`CastFromZoneEnumerator`) and the cast handler (`CastSpellHandler`) read to *replace* the printed mana cost
  entirely (a 6-drop and a 2-drop both become {2}) — unlike `GrantPlayWithCostIncrease`, which adds on top. The component
  is stripped when the card leaves exile (`StackResolver`), so a recast Airbended permanent doesn't carry a stale cost.
- **Airbend a spell** (the stack branch — Aang, Swift Savior: "airbend up to one other target creature **or spell**").
  The single target is a cross-zone union — `TargetFilter.anyOf(TargetFilter.Creature, TargetFilter.SpellOnStack)` (the
  same union machinery as Sorceress's Schemes). Branch on whether the chosen target is a spell with
  `Conditions.TargetIsSpellOnStack(0)`: the spell branch is `Effects.AirbendSpell(cost = {2})` — airbend's reminder
  says "**exile it**", not "counter it", so it reuses the Aven Interrupter `exileSpell` primitive: it removes the spell
  from the stack to its *owner's* exile **even if the spell can't be countered**, fires **no** `SpellCounteredEvent`, and
  grants the **owner** the same fixed-{2} may-play (reusing `PlayWithFixedAlternativeManaCostComponent`). The permanent
  branch is the normal `Effects.Airbend()`. Both branches fire the "whenever you airbend" trigger below once an object is
  actually exiled (CR 701.65b). (`Effects.AirbendSpell` is `Effects.ExileTargetSpell` with `emitAirbend = true`; use the
  plain `ExileTargetSpell` — no bend — for a non-airbend exile like Aven Interrupter.)
- **"Whenever you waterbend, earthbend, firebend, or airbend" (the four-bend event) + "all four this turn"** —
  `Triggers.YouBend(types = BendType.ALL)` fires once per bend of any element in `types` the controller performs
  (Avatar Aang uses all four; pass a subset like `setOf(BendType.EARTH)` for a single-element variant). Backed by a
  `BendPerformedEvent(playerId, bendType)` emitted at each of the four keyword actions, per CR 701.65b / 701.66b /
  701.67c / 702.189b:
  - **earthbend** and **airbend** compose `Effects.EmitBend(BendType.EARTH/AIR)` into their pipelines
    (`Effects.Earthbend`, `Effects.Airbend`/`AirbendAll`); airbend emits only when ≥1 object was exiled (gated on the
    `airbendExiled` collection, CR 701.65b). Airbending a **spell** (`Effects.AirbendSpell`, the stack branch) emits the
    same `BendType.AIR` from `ExileTargetSpellExecutor` once the spell is exiled.
  - **firebending** emits `BendType.FIRE` when its attack trigger resolves (folded into `firebendingAttackTrigger`), so
    both printed `firebending(n)` and `Effects.GrantFirebending` fire it.
  - **waterbend** emits `BendType.WATER` engine-side when the waterbend cost is *paid* — in `CastSpellHandler` /
    `ActivateAbilityHandler`, ungated on how it was paid (CR 701.67c), so paying entirely with mana still fires it.
  Each emit also folds the element into the player's `BendsThisTurnComponent` (a `Set<BendType>`, reset for every player
  at the start of each turn). Read the count of *distinct* bends this turn via
  `DynamicAmount.TurnTracking(Player.You, TurnTracker.DISTINCT_BENDS)` (0–4); "if you've done all four this turn" is
  `Conditions.CompareAmounts(TurnTracking(You, DISTINCT_BENDS), ComparisonOperator.GTE, DynamicAmount.Fixed(4))`.
  `Effects.EmitBend(bendType)` is the internal marker effect (executor: `EmitBendEventExecutor`); card authors reach a
  bend through the keyword-action facades above, not this effect. `BendPerformedEvent` is internal (dropped from the
  client log).
- **Endure N** — `Effects.Endure(amount, target = EffectTarget.Self)` composes a `ModalEffect.chooseOne` of
  AddDynamicCounters (N +1/+1 counters on the enduring permanent) and a single N/N white Spirit `CreateTokenEffect`
  (no fake keyword — endure is always the effect of a triggered/activated ability, resolved at resolution time). `amount`
  is `DynamicAmount.Fixed` for "endure 2" or any dynamic value for "endure X" (e.g. Warden of the Grove reads
  `EntityProperty(Source, CounterCount(...))`); `target` defaults to `Self` ("it endures") but takes
  `EffectTarget.TriggeringEntity` when a card endures the creature that triggered it.
- **Forage** — effect form is `Patterns.Mechanic.forage` (`ChooseActionEffect`). All *cost* forms
  (`Costs.Forage()`, `Costs.additional.Forage`, and the cast-from-graveyard permission) route their
  payment, candidate-finding, and per-mode legal-action cost-info through the single
  `ForageCostResolver`, so the player chooses exile-vs-sacrifice and which cards/Food everywhere
  (CR 701.59a). The payoff is `Triggers.WheneverYouForage` (`EventPattern.ForagedEvent`), emitted
  from `ForageCostResolver.pay` for the cost forms and from the `Effects.Foraged()` marker inside
  each effect-form mode — the same cost/effect split waterbend uses. The foraging player is whoever
  *paid*, which need not be the source's controller.
- **Blight X** — `Costs.additional.BlightVariable` + `DynamicAmount.AdditionalCostBlightAmount` +
  `Conditions.BlightWasPaid(n)`.
- **Divvy (Fact-or-Fiction)** — `Patterns.Library.factOrFiction(...)`; `SplitPilesDecision` stays dormant until N > 2.
- **Astral Slide / delayed return** — `ExileUntilEndStepEffect` + `DelayedTriggeredAbility`.
- **Lord effects** — multiple `staticAbility { }` blocks + `ModifyStatsForCreatureGroup` /
  `AffectsFilter.OtherCreaturesWithSubtype`.
- **Player-scoped uncounterable grant** — `Effects.GrantSpellsCantBeCountered(target, filter, duration)` +
  `SpellsCantBeCounteredComponent`.
- **Static emblems** — `Effects.CreatePermanentEmblem(...)` for planeswalker emblems with static abilities.
- **The Ring / the Ring tempts you (CR 701.54)** — `Effects.TheRingTemptsYou(target = Controller)`: the player gets
  the Ring emblem (`TheRingComponent`, tempt-count tracked) and chooses a creature they control to become their
  Ring-bearer (`RingBearerComponent` designation). The emblem's four cumulative abilities are resolved by the engine,
  not card data: the bearer is made legendary in `StateProjector` and can't be blocked by greater power via
  `RingBearerCantBeBlockedByGreaterPowerRule`; the ≥2/≥3/≥4 triggered abilities are appended to the bearer by
  `TriggerAbilityResolver` (see `TheRingAbilities`). For card triggers/checks use `Triggers.RingTemptsYou`
  ("Whenever the Ring tempts you"), `Conditions.SourceIsRingBearer` ("if this is your Ring-bearer"), and
  `Conditions.YouChoseOtherCreatureAsRingBearer` ("if you chose a creature other than this as your
  Ring-bearer" — pairs with `Triggers.RingTemptsYou` for the Aragorn/Faramir/Gandalf/Galadriel cycle).
  CR 701.54a: the designation ends permanently when another player gains control of the bearer —
  every control-change executor strips `RingBearerComponent` via `clearRingBearerOnControlChange`, so a
  temporary steal (Threaten) does not silently restore the designation when control reverts.
- **Amass [subtype] N (CR 701.47)** — `Effects.Amass(count, subtype)` (fixed) or
  `Effects.Amass(amount, subtype)` (a `DynamicAmount`, for "amass Orcs X"). `subtype` is required (no default) —
  the amassed Army's type is printed on each card (Orcs for the LTR cards). If the controller controls no Army
  creature, a 0/0 black `[subtype]` Army token is created first (composing `CreateTokenEffect`); then they put N
  +1/+1 counters on an Army they control (a `SelectCardsDecision` resolved by `AmassContinuation` picks which one
  when they control several) and that Army becomes the subtype if it isn't already. The counter/subtype back half
  lives in `AmassResolution`; counters route through `AddCountersEffect`, so placement replacements still apply.

## 20. Miscellaneous author-facing knobs

- `triggeredAbility { controlledByTriggeringEntityController = true }` — the triggered ability is controlled by the
  triggering entity's controller (not source's). Useful for ETB-on-creature triggers and Death Match-style shapes.
- `metadata.oracleTextOverride` — bypass auto-generated oracle text when needed.
- `metadata.inBooster = false` — Special Guests, starter exclusives, bonus sheets.
- `colorIdentity` override is authoritative — never run `:mtg-sets:syncColorIdentityFromDump`.
- Layer dependencies (CR 613.8) — same-layer effects sort by dependency (trial application) before falling back to
  timestamp.
- Server is authoritative; never compute legal actions in the client. Every state change emits a `GameEvent` so triggers
  and animations can react.

### Ability identity (engine-internal, not authored)

`AbilityIdentity(cardDefinitionId, abilityId)` (`mtg-sdk` `scripting/AbilityIdentity.kt`) is the stable, **definition-scoped**
identity of a *kind* of ability — independent of the stack object or source entity instance. Two permanents printed from the
same card (and every future instance) share one identity for a given ability, because both halves are definition-scoped.
Cards never author it. Activated-ability lookup records whether the concrete ability came from the current card definition:
printed abilities and generated Class level-up abilities receive the corresponding identity, while runtime-, static-, and
emblem-granted abilities and intrinsic subtype abilities retain their concrete `ActivatedAbility` without claiming definition
ownership. The activation snapshot travels through stack copies and resolution; its `activatedAbilityId` is derived from the
snapshot, so retaining “this ability” never depends on a grant still existing when the effect resolves. The engine threads proven
identities onto `ActivatedAbilityOnStackComponent` and `DecisionContext`, so persistent yields can remember a per-ability
answer across all copies. The triggered-ability path still derives a provisional key from the current source card definition;
typed ownership provenance for granted and synthesized triggers remains an explicit follow-up in
`backlog/stack-collapse-and-batch-decisions.md` §4. See that backlog's §C.2 for the original identity contract.

### Batched may-question (engine-internal, not authored)

When a run of structurally identical **optional, targeted** triggers ("Whenever …, you may … *target* …") fires off one
event, the engine asks the controller a single `BatchYesNoDecision` instead of one `YesNoDecision` per trigger — Magic
Online's "auto-stack identical triggers" affordance (`backlog/stack-collapse-and-batch-decisions.md` §B). Cards author
nothing: `TriggerProcessor` groups contiguous `liveTriggers` sharing one (controller, `AbilityIdentity`) key (and that would
actually raise the may-question rather than fizzle for lack of targets) into one decision carrying a `count`. The reply,
`BatchYesNoResponse(choice, applyToAll)`, is fanned back out by `BatchMayTriggerContinuation`:

- `applyToAll = true` resolves the whole run (`no` drops it; `yes` unwraps each may-gate and routes every instance through
  ordinary per-trigger target selection — only the yes/no is shared, never the target).
- `applyToAll = false` peels one instance off (answered with `choice`) and re-raises the batch for the remainder.

Only same-controller, same-identity, targeted-may triggers batch; targetless "may" triggers still decide at resolution, and a
lone trigger uses the plain per-trigger yes/no. The guard guarantees the engine never makes a meaningful target/ordering
choice on the player's behalf.

## 21. Structural lint (`CardLinter`)

Every registered card is structurally validated at build time: `CardValidator.validate` runs
`CardLinter` (mtg-sdk `serialization/CardLinter.kt`), and the corpus-wide gate is
`CardLintTest` in mtg-sets (beside `CardDefinitionSnapshotTest`). The linter walks the card's
serialized JSON tree, so every container — composites, gates, modes, granted abilities, class
levels, saga chapters, faces — is covered automatically. What it checks:

- **Pipeline dataflow** — every read of a named pipeline variable (`MoveCollection.from`,
  `CardSource.FromVariable`, `VariableReference`, `CollectionContainsMatch`, `chosenSubtypeKey`,
  …) must have a writer (`storeAs` / `storeSelected` / `storeMatching` / `StoreNumber` /
  `ChooseOption` / a cast-time additional cost, …) in the same resolution scope. A read written
  *nowhere* on the card is an **error** (typo → silent no-op); read-before-write and
  cross-resolution reads are warnings, as are stores nothing reads. A collection write `x`
  also satisfies the numeric read `x_count`. Macro effects that the engine expands into a
  pipeline count as writers of the collections that expansion seeds: `Scry` writes `toTop` /
  `toBottom` and `Surveil` writes `toTop` / `toGraveyard`, so a sibling effect may read the
  kept-on-top cards (e.g. Starving Revenant's `DistinctEntitiesInCollections("toTop")`).
- **Target bindings per owning ability** — `ContextTarget(i)` must fit the owning ability's
  flattened target slots (a `count = 2` requirement spans two indices); `BoundVariable(name)`
  must match a requirement `id` (indexed form `id[i]` allowed). Modes inherit the card-level
  requirements unless they declare their own; `ReflexiveTriggerEffect.reflexiveEffect` resolves
  against `reflexiveTargetRequirements`; `CreateDelayedTriggerEffect.effect` against its
  `targetRequirement`; granted/token abilities against their own requirements only.
- **Choice slots** — a `ChoiceSlot` read (`CastChoiceMade`, `DynamicAmount.CastChoice`,
  `HasChosenColor`, `SourceChosenModeIs`, …) needs a declarer on the card (`EntersWithChoice`,
  kicker, blight, sneak, `ChooseColorThen`/`ChooseColorForTarget`, or `ChooseNumberForSource`,
  which declares the slot named in its `slot` field); `SourceChosenModeIs` ids must match a
  declared `modeOptions` id.
- **Registry hygiene** — a string field whose name follows the dataflow conventions (`store*`,
  `from`, `collectionName`, `variableName`, …) on a node type the linter doesn't know is itself
  an error: **when you add an SDK type that reads or writes a named pipeline variable, classify
  it in `CardLinter.dataflowFields` in the same change** (and name the field conventionally so
  the hygiene net sees it).
- **`EntityMatches` entity roles** — the condition's `entity` must be a role the
  `ConditionEvaluator` dispatches (`Self`, `EnchantedPermanent`, `EnchantedCreature`,
  `EquippedCreature`, `ContextTarget`, `TriggeringEntity`); any other `EffectTarget` would be a
  silent constant `false` and is an **error**. Extending the evaluator to a new role must extend
  `CardLinter.supportedEntityMatchesRoles` in the same change.
- **Mana-ability classification (CR 605.1a)** — the `isManaAbility` flag is a *consequence* of the
  ability, not an authoring choice, so it is checked in **both** directions and each mismatch is an
  **error**. An activated ability that could add mana (`AddMana`, `AddColorlessMana`,
  `AddManaOfChoice`, `AddDynamicMana`, …) *is* a mana ability unless it requires a target, is a
  loyalty ability, or its **cost or effect** moves a card to or from a library — `UnflaggedManaAbility`
  fires when such an ability isn't flagged (the shape that shipped Cryptolith Rite, Joiner Adept and
  Citanul Hierophants unflagged, because a raw `ActivatedAbility(...)` inside a `GrantActivatedAbility`
  has no builder to derive the flag), and `MisflaggedManaAbility` fires when a disqualified ability
  *is* flagged. The library clause entered 605.1a in the August 7, 2026 update, which is why the
  second direction exists: Chromatic Sphere, the five Odyssey Eggs and Deranged Assistant were all
  correctly flagged when written and became ordinary activated abilities on that date. A library
  *reorder* is not a disqualifier — it moves cards **within** a library, not to or from one — so
  `Scry`, the pipeline it expands to, and `Patterns.Library.lookAtTopAndReorder` all leave a mana
  ability a mana ability (Path of Ancestry). What the check reads is whether a card crosses the
  library boundary: gathered from a library and put anywhere else, or put into a library without
  having come from one. `Surveil` does cross it (library → graveyard) and disqualifies.
- **Attach-scope on a card that can't be attached** — a **printed** static ability
  (`script.staticAbilities` or a `classLevels` entry, on any face) whose `GroupFilter` carries
  `Scope.AttachedTo` ("enchanted/equipped creature"), on a card that is not an Aura, Equipment, or
  Fortification and carries no `auraTarget` / `equipCost`, is an **error**. The engine resolves
  attach-scope only by walking a host's attachments, so with nothing attached the filter matches no
  permanent and the ability does nothing. This is the shape that shipped Harmonious Grovestrider
  with no ward and Myojin of Night's Reach with no indestructible: attach scope is the *default*
  filter on the `Grant*` static abilities, so simply omitting the filter argument on a creature
  produces it — and because it's a default it appears in neither the card source nor the snapshot.
  (The check therefore re-encodes each ability with defaults materialized.) Use
  `keywordAbility(KeywordAbility.ward("{N}"))` for a card's own printed keyword, or pass an explicit
  filter — `Filters.Self` for "this permanent", a battlefield-scoped `GroupFilter` for a lord-style
  grant. Attach scope *inside an effect* is not flagged: an effect can change what the card is
  first (The Irencrag becomes an Equipment and then grants "equipped creature gets +3/+3"), and an
  effect can hand abilities to another object entirely (an Aura token). Attachability is likewise
  judged across the whole physical card, so a creature that transforms into an Aura isn't flagged
  for its other face.

Intentional exceptions go in `mtg-sets/src/test/resources/lint-allowlist.txt`
(`ErrorType|Card Name`, stale entries fail). Inside `ForEachInGroup` / `ForEachInCollection`,
address the iterated entity with `EffectTarget.Self` — `ContextTarget(0)` reads the cast-time
target list, which is unrelated to the iteration (this exact bug shipped on a real card before
the linter).

---

## Authoritative source files

| Area               | Path                                                            |
|--------------------|-----------------------------------------------------------------|
| Card DSL           | `mtg-sdk/src/main/kotlin/.../dsl/CardBuilder.kt`                |
| Effects            | `mtg-sdk/src/main/kotlin/.../dsl/Effects.kt`                    |
| Effect patterns    | `mtg-sdk/src/main/kotlin/.../dsl/{Library,Hand,Group,Exile,CreatureType,Misc}Patterns.kt` |
| Inline pipelines   | `mtg-sdk/src/main/kotlin/.../dsl/PipelineBuilder.kt`            |
| Triggers           | `mtg-sdk/src/main/kotlin/.../dsl/Triggers.kt`                   |
| Costs              | `mtg-sdk/src/main/kotlin/.../dsl/Costs.kt`                      |
| Conditions         | `mtg-sdk/src/main/kotlin/.../dsl/Conditions.kt`                 |
| Filters            | `mtg-sdk/src/main/kotlin/.../dsl/Filters.kt`                    |
| Targets            | `mtg-sdk/src/main/kotlin/.../dsl/Targets.kt`                    |
| Keywords           | `mtg-sdk/src/main/kotlin/.../core/Keyword.kt`                   |
| Card model         | `mtg-sdk/src/main/kotlin/.../model/CardDefinition.kt`           |
| Dynamic amounts    | `mtg-sdk/src/main/kotlin/.../scripting/values/DynamicAmount.kt` |
| Real card examples | `just where BLB` → `mtg-sets/<era>/.../definitions/blb/cards/`   |

For step-by-step authoring workflow see [`api-guide.md`](api-guide.md) (and use the `add-card` skill);
for hard cases see [`managing-complex-and-rare-abilities.md`](managing-complex-and-rare-abilities.md).

### Transmute

`transmute("{1}{U}{U}")` composes a hand-zone activated ability: pay the mana,
discard the source as a cost, and search for one card with the same mana value.
The chosen card is revealed and put into hand, then the library is shuffled; the
search may find nothing. Activation is restricted to sorcery timing. It uses
`Costs.DiscardSelf` and the existing library search pipeline. Declare `manaCost`
before `transmute`: the helper compiles the card's hand-zone mana value into the
search filter, so reanimating and copying the discarded card in response does not
change the search. Mana paid and later characteristics of a new incarnation are
irrelevant. Callers: Drift of Phantasms and Dimir Infiltrator.

`Effects.Regenerate(target)` creates the existing regeneration shield through the
DSL facade. Dimir House Guard composes it with a creature sacrifice cost. The
default target is `ContextTarget(0)`; self-regeneration passes `EffectTarget.Self`.

### Dredge

`keywordAbility(KeywordAbility.dredge(N))` declares dredge N. The optional replacement
functions only in the card owner's graveyard and only when that player has at least
N cards in their library. It replaces one draw with milling N cards and returning
the source to hand. Each draw of a multi-card instruction rechecks the graveyard,
so cards milled by the first replacement may be available for the next draw.

The engine keeps the intrinsic amounts in `DredgeComponent` and supplies matching
graveyard sources to the ordinary draw-replacement processor with `CardZoneIdentity`.
The effect recipe composes library milling and return-to-hand; no new decision or
resolution executor is introduced. The existing Yes/No decision belongs to the
drawing player, and its source identifies the public graveyard card. The client
keyword label is `DREDGE`. The mtgish emitter preserves the numeric argument through
`KeywordAbility.dredge(N)`; unsupported numeric shapes remain scaffolded.

### Live library-top references

`EntityReference.LibraryTop(player = Player.You)` reads the current top card of the named
player's library for relational filters and dynamic amounts. `EffectTarget.LibraryTop(player)`
is the matching effect/condition target: compose `Conditions.EntityMatches` with any card filter,
or pass it to an ordinary zone-moving effect. Both resolve at evaluation time and return no entity
for an empty library; they never use last-known information or reveal the card by themselves.
Single-player `Player` references use the normal player resolver; group references resolve to no entity.
During filter and condition projection, the source controller comes from the intermediate projection.

Crown of Convergence combines `RevealTopOfLibrary`, a creature-card condition, `ModifyStats` with
`sharingColorWith(EntityReference.LibraryTop())`, and `PutOnBottomOfLibrary`. The bonus follows
changes to the top card, control, and projected creature colors, and never grants more than +1/+1
for sharing multiple colors. A colorless card shares no colors.

Continuous group filters also support `SharesColorWith(reference)`, including inside `And`, `Or`,
and `Not`. They delegate that relational comparison to the shared predicate evaluator with an
intermediate projection, preserving colorless results instead of falling back to printed colors.
Public library-reveal statics follow projected control, so stealing a reveal source switches which
player's top card is visible.
