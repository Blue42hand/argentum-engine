# Commander Gym fork direction

This fork of Argentum exists primarily to make Argentum the best practical game environment for the Commander Gym project while keeping improvements generally useful to Argentum itself.

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

If yes, the work likely belongs here.

Strategic intelligence should not move into Argentum merely because Commander Gym needs it. Built-in Argentum AI remains useful as a baseline, opponent, simulator, and fallback, but Commander Gym is responsible for producing the strongest artificial player.

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

Argentum work should therefore prioritize the environment qualities needed to measure and improve competence:

- correct Commander and multiplayer rules;
- sufficient card coverage for representative decks;
- complete and stable legal-action/decision surfaces;
- seat-safe observations;
- reliable external-controller integration;
- high-throughput Gym execution;
- reproducible replay/provenance;
- useful diagnostics when a pilot or environment fails.

Behavior-profile distinctions such as casual pacing, politics style, risk appetite, or competitive maximization can be layered on later when Commander Gym pilots are strong enough for those objectives to diverge meaningfully.
