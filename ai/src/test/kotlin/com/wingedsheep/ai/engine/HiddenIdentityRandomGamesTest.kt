package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.PlayCardDecision
import com.wingedsheep.engine.core.PlayCardResponse
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.view.ClientEvent
import com.wingedsheep.engine.view.ClientEventTransformer
import com.wingedsheep.engine.view.ClientGameState
import com.wingedsheep.engine.view.ClientStateTransformer
import com.wingedsheep.engine.view.DecisionMasker
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.view.LegalActionInfo
import com.wingedsheep.engine.view.Visibility
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.random.Random

/**
 * Issue #2780's property over random games: nothing a player is sent may depend on the identity of
 * a face-down object that player may not look under.
 *
 * `HiddenIdentityInvarianceTest` (rules-engine) checks the property on scripted scenarios. This
 * test drives it from seeded random games over sets built around face-down mechanics — morph,
 * manifest dread, disguise, cloak — so the leak it catches is one through a
 * field, event or decision nobody thought to script.
 *
 * Each game is played once and its actions recorded. At fork points where a face-down object is
 * hidden from a player, the state is forked into a second world in which that object really is a
 * different card, and the recorded actions are replayed in both. After every action the hidden
 * player's view in the two worlds must be identical. The view is everything the game server sends a
 * seat: the client state, the events, the legal actions, and either the decision (when the seat
 * answers it) or the opponent-decision status (when it does not).
 *
 * A replay window ends where the worlds may legitimately differ: the object is turned face up,
 * leaves its face-down zone or becomes visible to the viewer by any other rule, or an action
 * that worked in the real world is refused in the other — the object's controller knows what it
 * is and acted on it, so the rest of the script no longer describes the swapped world.
 *
 * The engine mints entity ids from a counter in [GameState], so both worlds allocate the same ids
 * and the comparison needs no normalisation.
 */
class HiddenIdentityRandomGamesTest : FunSpec({

    for ((setCode, seed) in SETS) {
        test("a face-down identity never reaches a player who may not see it ($setCode)") {
            val set = MtgSetCatalog.requireByCode(setCode)
            val registry = CardRegistry().apply {
                register(set.cards)
                // An expansion like Legions printed no basics of its own.
                register(set.basicLands.ifEmpty { MtgSetCatalog.requireByCode("ONS").basicLands })
            }
            val rng = Random(seed)
            val harness = Harness(registry, set.cards.filter { it.typeLine.isCreature })
            val leaks = mutableListOf<String>()
            var compared = 0
            repeat(GAMES_PER_SET) { game ->
                val deck1 = buildRandomSealedDeck(faceDownHeavyPool(set.cards), rng)
                val deck2 = buildRandomSealedDeck(faceDownHeavyPool(set.cards), rng)
                val trajectory = harness.playRandomGame(deck1, deck2, rng)
                val outcome = harness.checkForks(trajectory, rng, label = "$setCode seed=$seed game=$game")
                compared += outcome.compared
                leaks += outcome.leaks
            }
            if (leaks.isNotEmpty()) fail("Hidden identity reached a player:\n\n" + leaks.distinct().joinToString("\n\n"))
            // A vacuous pass — no face-down object ever hidden from anyone — proves nothing.
            compared shouldBeGreaterThan MIN_COMPARISONS
        }
    }
}) {
    /** What one seat is sent after one action; the two worlds must agree on every field. */
    data class SeatView(
        val state: ClientGameState,
        val events: List<ClientEvent>,
        val legalActions: List<LegalActionInfo>,
        val decision: PendingDecision?,
        val opponentDecision: OpponentDecisionView?,
    ) {
        /** The first field on which [other] differs, with the text around where they part; null when equal. */
        fun diff(other: SeatView): String? {
            val fields = listOf(
                "state" to (state to other.state),
                "events" to (events to other.events),
                "legalActions" to (legalActions to other.legalActions),
                "decision" to (decision to other.decision),
                "opponentDecision" to (opponentDecision to other.opponentDecision),
            )
            val (field, values) = fields.firstOrNull { (_, v) -> v.first != v.second } ?: return null
            // Name the card when the difference is one card's view, which is the usual case.
            val card = (state.cards.keys + other.state.cards.keys)
                .firstOrNull { field == "state" && state.cards[it] != other.state.cards[it] }
            val name = if (card != null) "state.cards[$card]" else field
            val a = (if (card != null) state.cards[card] else values.first).toString()
            val b = (if (card != null) other.state.cards[card] else values.second).toString()
            val at = a.indices.firstOrNull { it >= b.length || a[it] != b[it] } ?: a.length
            val from = maxOf(0, at - CONTEXT)
            return "$name differs\n  real:    …${a.substring(from, minOf(a.length, at + CONTEXT))}…" +
                "\n  swapped: …${b.substring(from, minOf(b.length, at + CONTEXT))}…"
        }

        private companion object {
            const val CONTEXT = 160
        }
    }

    /** The fields of the server's `OpponentDecisionStatus` that depend on the game. */
    data class OpponentDecisionView(val type: String, val sourceName: String?, val sourceId: EntityId?)

    /** A recorded game: the state before each action and the action that was applied to it. */
    class Trajectory(val states: List<GameState>, val actions: List<GameAction>)

    class ForkOutcome(val compared: Int, val leaks: List<String>)

    class Harness(private val registry: CardRegistry, private val creatures: List<CardDefinition>) {
        private val processor = ActionProcessor(registry)
        private val enumerator = LegalActionEnumerator.create(registry)
        private val predicateEvaluator = PredicateEvaluator(cardRegistry = registry)
        private val visibility = Visibility(registry, conditionEvaluator = predicateEvaluator.conditions)
        private val masker = DecisionMasker(visibility)
        private val stateTransformer = ClientStateTransformer(registry, predicateEvaluator = predicateEvaluator)
        private val enricher = LegalActionEnricher(ManaSolver(registry, predicateEvaluator), registry)

        fun playRandomGame(
            deck1: com.wingedsheep.sdk.model.Deck,
            deck2: com.wingedsheep.sdk.model.Deck,
            rng: Random,
        ): Trajectory {
            var state: GameState = GameInitializer(registry).initializeGame(
                GameConfig(
                    players = listOf(PlayerConfig("P1", deck1), PlayerConfig("P2", deck2)),
                    skipMulligans = true,
                    startingPlayerIndex = 0,
                    seed = rng.nextLong(),
                )
            ).state
            val states = mutableListOf<GameState>()
            val actions = mutableListOf<GameAction>()
            while (!state.gameOver && state.turnNumber < MAX_TURNS && actions.size < MAX_ACTIONS) {
                val action = chooseAction(state, rng) ?: break
                val applied = processor.process(state, action).result.takeIf { it.error == null }
                    ?.let { action to it.state }
                    ?: PassPriority(action.playerId).let { pass ->
                        processor.process(state, pass).result.takeIf { it.error == null }?.let { pass to it.state }
                    }
                    ?: break
                states += state
                actions += applied.first
                state = applied.second
            }
            return Trajectory(states, actions)
        }

        /** A random legal action, preferring face-down casts so hidden identities are common. */
        private fun chooseAction(state: GameState, rng: Random): GameAction? {
            val pending = state.pendingDecision
            if (pending != null && pending !is PlayCardDecision) {
                return SubmitDecision(pending.playerId, randomDecisionResponse(pending, rng))
            }
            val player = pending?.playerId ?: state.priorityPlayerId ?: return null
            val offers = enumerator.enumerate(state, player).filter { it.affordable }
            if (offers.isEmpty()) return null
            val faceDown = offers.filter { (it.action as? CastSpell)?.castFaceDown == true }
            val offer = if (faceDown.isNotEmpty() && rng.nextDouble() < FACE_DOWN_BIAS) {
                faceDown.random(rng)
            } else {
                offers.random(rng)
            }
            val filled = TargetSelection.fillHeuristically(state, offer, player, fillPartialRequirements = true)
            return if (pending is PlayCardDecision) SubmitDecision(player, PlayCardResponse(pending.id, filled)) else filled
        }

        fun checkForks(trajectory: Trajectory, rng: Random, label: String): ForkOutcome {
            val leaks = mutableListOf<String>()
            var compared = 0
            var forks = 0
            var nextForkAt = 0
            val seen = mutableSetOf<Pair<EntityId, EntityId>>()
            for ((index, state) in trajectory.states.withIndex()) {
                if (forks >= MAX_FORKS_PER_GAME) break
                val pairs = hiddenPairs(state)
                // Fork as soon as an object becomes hidden from someone, then every so often while
                // it stays hidden, so both its arrival and its later life on the board are covered.
                val fresh = pairs.filter { it !in seen }
                seen += pairs
                val pair = fresh.firstOrNull() ?: pairs.takeIf { index >= nextForkAt }?.randomOrNull(rng) ?: continue
                nextForkAt = index + REFORK_INTERVAL
                forks++
                val (hidden, viewer) = pair
                val identity = otherIdentity(state, hidden, rng) ?: continue
                val result = replay(trajectory, index, hidden, identity, viewer)
                compared += result.first
                result.second?.let { leaks += "$label fork@$index hidden=$hidden viewer=$viewer as ${identity.name}: $it" }
            }
            return ForkOutcome(compared, leaks)
        }

        /** Every (face-down object, player it is hidden from) pair in [state]. */
        private fun hiddenPairs(state: GameState): List<Pair<EntityId, EntityId>> =
            state.entities.keys
                .filter { state.getEntity(it)?.has<FaceDownComponent>() == true }
                .sortedBy { it.value }
                .flatMap { id ->
                    state.turnOrder.filter { visibility.isCardIdentityHiddenFrom(state, id, it) }.map { id to it }
                }

        private fun otherIdentity(state: GameState, hidden: EntityId, rng: Random): CardDefinition? {
            val actual = state.getEntity(hidden)?.get<CardComponent>()?.name
            return creatures.filter { it.name != actual }.randomOrNull(rng)
        }

        /**
         * Replays from [from] in the real world and in one where [hidden] is [identity], comparing
         * [viewer]'s view after each action. Returns how many views were compared, and the first
         * difference, if any.
         */
        private fun replay(
            trajectory: Trajectory,
            from: Int,
            hidden: EntityId,
            identity: CardDefinition,
            viewer: EntityId,
        ): Pair<Int, String?> {
            var real = trajectory.states[from]
            var swapped = withIdentity(real, hidden, identity)
            view(real, emptyList(), viewer).diff(view(swapped, emptyList(), viewer))?.let {
                return 0 to "at the fork itself: $it"
            }
            var compared = 1
            val end = minOf(trajectory.actions.size, from + REPLAY_WINDOW)
            for (step in from until end) {
                val action = trajectory.actions[step]
                val actorSees = !visibility.isCardIdentityHiddenFrom(real, hidden, action.playerId)
                val a = processor.process(real, action).result
                val b = processor.process(swapped, action).result
                if (a.error != null || b.error != null) break
                real = a.state
                swapped = b.state
                if (!stillHidden(real, hidden, viewer) || !stillHidden(swapped, hidden, viewer)) break
                // The game itself went differently, not just its rendering: a choice the engine made
                // for the acting player (which creature pays a tap cost, which lands auto-pay taps)
                // depended on what the object is. That is fair when the actor may look under it —
                // the rest of the script then no longer describes the swapped world — and a leak
                // when the actor may not.
                val diverged = withIdentity(real, hidden, identity) != swapped
                if (diverged && actorSees) break
                view(real, a.events, viewer).diff(view(swapped, b.events, viewer))?.let {
                    return compared to "after action ${step - from + 1} ($action): $it"
                }
                if (diverged) {
                    return compared to "after action ${step - from + 1} ($action): the game diverged on an action " +
                        "by a player who may not see the object"
                }
                compared++
            }
            return compared to null
        }

        private fun stillHidden(state: GameState, hidden: EntityId, viewer: EntityId): Boolean =
            state.getEntity(hidden)?.has<FaceDownComponent>() == true &&
                visibility.isCardIdentityHiddenFrom(state, hidden, viewer)

        private fun view(state: GameState, events: List<GameEvent>, viewer: EntityId): SeatView {
            val decision = state.pendingDecision
            val decides = decision != null && state.actorFor(decision.playerId) == viewer
            val actor = (decision as? PlayCardDecision)?.playerId ?: state.priorityPlayerId
            val legalActions = if (actor == viewer && (decision == null || decision is PlayCardDecision)) {
                enricher.enrich(enumerator.enumerate(state, viewer), state, viewer)
            } else {
                emptyList()
            }
            return SeatView(
                state = stateTransformer.transform(state, viewer),
                events = ClientEventTransformer.transform(events, viewer, state, visibility),
                legalActions = legalActions,
                decision = decision?.takeIf { decides }?.let { masker.maskFor(it, state, viewer) },
                opponentDecision = decision?.takeIf { !decides }?.let {
                    OpponentDecisionView(
                        type = it::class.simpleName ?: "",
                        sourceName = masker.maskFor(it, state, viewer).context.sourceName,
                        sourceId = it.context.sourceId,
                    )
                },
            )
        }

        /** [state] with [entityId] made into [def] underneath — same id, owner and components otherwise. */
        private fun withIdentity(state: GameState, entityId: EntityId, def: CardDefinition): GameState =
            state.updateEntity(entityId) { container ->
                val card = container.get<CardComponent>()!!
                container.with(
                    card.copy(
                        cardDefinitionId = def.name,
                        name = def.name,
                        manaCost = def.manaCost,
                        typeLine = def.typeLine,
                        oracleText = def.oracleText,
                        baseStats = def.creatureStats,
                        baseKeywords = def.keywords,
                        baseFlags = def.flags,
                        colors = def.colors,
                        imageUri = def.metadata.imageUri,
                    )
                )
            }
    }

    companion object {
        /** Sets whose limited decks put face-down objects on the board. */
        private val SETS = listOf(
            "ONS" to 11L, // morph
            "LGN" to 12L, // morph, and cards that care about face-down creatures
            "KTK" to 13L, // morph
            "MKM" to 14L, // disguise, cloak
            "DSK" to 15L, // manifest dread
        )
        private const val GAMES_PER_SET = 3
        private const val MAX_TURNS = 24
        private const val MAX_ACTIONS = 1_200
        private const val MAX_FORKS_PER_GAME = 8
        private const val REFORK_INTERVAL = 40
        private const val REPLAY_WINDOW = 80
        private const val FACE_DOWN_BIAS = 0.75
        private const val MIN_COMPARISONS = 50

        /**
         * The set's cards with its face-down cards weighted up, so a random sealed pool reliably
         * contains some. Cards are chosen by name, so duplicates only change the odds.
         */
        private fun faceDownHeavyPool(cards: List<CardDefinition>): List<CardDefinition> {
            val faceDown = cards.filter { card ->
                val text = card.oracleText.lowercase()
                FACE_DOWN_WORDS.any { it in text }
            }
            return cards + List(3) { faceDown }.flatten()
        }

        private val FACE_DOWN_WORDS = listOf("morph", "manifest", "disguise", "cloak", "face down")
    }
}
