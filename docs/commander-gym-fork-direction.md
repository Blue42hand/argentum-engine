# Commander Gym fork direction

This fork of Argentum exists as a staging area for making Argentum the best practical game environment for the Commander Gym project and contributing those improvements back upstream.

The desired long-term state is that Commander Gym plugs into **vanilla upstream Argentum**. A permanent Commander-Gym-specific Argentum distribution is not the goal; the less fork-only engine code this project owns, the better.

A useful shorthand is:

> **Argentum is the game. Commander Gym is the player.**

## Boundary

Argentum remains authoritative for:

- Magic rules and card behavior;
- game state and state transitions;
- format rules, including Commander;
- multiplayer lifecycle;
- legal actions and structured decisions;
- hidden-information projection and player-visible observations;
- game/session hosting;
- player/controller connection seams;
- Gym reset/step/observe APIs;
- snapshots, forks, batching, replay, and environment-level performance.

Commander Gym owns:

- artificial-player reasoning and policy;
- deck construction and deck/pilot optimization;
- LLM/RL/other ML systems;
- prompts, context construction, learned models, and planning;
- training loops and curricula;
- self-play policy and experiment design;
- benchmarks and player evaluation;
- data/replay pipelines used to improve pilots;
- orchestration around Argentum;
- adapters that let the same pilot train through Gym and play humans through game-server.

## Development rule for this fork

Development in this fork should usually answer one of these questions:

1. Does Commander Gym need a rule, card, multiplayer behavior, or format capability that Argentum does not yet model correctly?
2. Does an external artificial player need a cleaner, safer, more stable, or more efficient way to observe or act?
3. Does training need better environment performance, batching, snapshots, forks, determinism, provenance, or diagnostics?
4. Does the game-server need a better generic controller/player seam so a program can occupy the same kind of seat as a human?
5. Is there a generally useful Argentum capability currently being reimplemented awkwardly in Commander Gym?

If yes, the work likely belongs in Argentum. In this fork, that normally means: implement it generically, validate it here, and prepare it for upstream contribution.

Every fork delta should be treated as one of:

- **upstream candidate** — the default for generic game/environment work;
- **downstream extension** — exceptional work that truly should not be part of Argentum;
- **transitional infrastructure** — temporary integration machinery with a clear retirement path.

Once upstream contains the required capability, Commander Gym should consume upstream and remove the corresponding fork-only dependency. The target required fork delta is zero.

Strategic intelligence should not move into Argentum merely because Commander Gym needs it. Built-in Argentum AI remains useful as a baseline, opponent, simulator, and fallback, but Commander Gym is responsible for producing the strongest artificial player.

## Upstream contribution discipline

Follow upstream Argentum's own contribution and architecture guidance for work developed here rather than treating this fork as a private patch stack.

For card-database work, in particular:

- use Scryfall Oracle text and rulings as the implementation source;
- compose existing `Effects.*` / `Patterns.*` primitives before introducing new SDK/engine vocabulary;
- keep one scenario-test file per card;
- manually exercise the card and player-facing interaction;
- batch cards only when they reuse existing primitives;
- isolate cards that require a new effect, condition, keyword, decision flow, or other engine primitive into focused changes with primitive-level tests.

Commander Gym's active deck roster is the first card-coverage priority; after roster coverage, Commander usage/popularity data such as EDHREC can prioritize broadly useful cards. Mechanic leverage may move a card earlier when implementing its generic capability unlocks many other cards.

Non-card work should likewise be written as generally useful Argentum capability whenever the need is about the game/environment: external-player APIs, Gym semantics, multiplayer, server/controller seams, provenance, replay, transport, diagnostics, performance, and rules correctness are all natural upstream candidates.

This policy does **not** blur the boundary: player reasoning, learning, deck construction, strategy, model policy, and training remain Commander Gym work.

## Two interfaces, one player

Commander Gym should ultimately be able to use the same conceptual pilot through two Argentum surfaces:

```text
training:
Commander Gym pilot → Argentum Gym / gym-server → rules engine

human play:
Commander Gym pilot → Argentum game-server → human table
```

The training interface can expose machine-oriented affordances such as batch stepping and snapshot/fork. The human-play interface should preserve normal seat information and game-server authority.

## Near-term priority

The near-term objective is broad player competence rather than separate casual and competitive AI modes.

Argentum work should therefore prioritize the environment qualities needed to measure and improve competence, while keeping those improvements suitable for upstream Argentum:

- correct Commander and multiplayer rules;
- sufficient card coverage for representative decks;
- complete and stable legal-action/decision surfaces;
- seat-safe observations;
- reliable external-controller integration;
- high-throughput Gym execution;
- reproducible replay/provenance;
- useful diagnostics when a pilot or environment fails.

Behavior-profile distinctions such as casual pacing, politics style, risk appetite, or competitive maximization can be layered on later when Commander Gym pilots are strong enough for those objectives to diverge meaningfully.
