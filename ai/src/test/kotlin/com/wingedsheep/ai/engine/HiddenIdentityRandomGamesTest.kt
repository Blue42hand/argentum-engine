package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayCardDecision
import com.wingedsheep.engine.core.PlayCardResponse
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.support.HiddenIdentityForkChecker
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import kotlin.random.Random

/**
 * The fuzz net for [HiddenIdentityForkChecker]'s two properties: nothing a player is sent may
 * depend on a card they may not see (#2780), and whether a player is asked a question may depend
 * only on information every player has (#3340).
 *
 * The **enforcement** is the deterministic per-site cases, which fork a scripted state just before
 * a site's question (`HiddenIdentityForkCasesTest` and each #3340 group's own case). Random games
 * reach a given site only by luck, so a green run here says nothing about any one site; it finds the leak
 * nobody thought to script. `HiddenIdentityInvarianceTest` (rules-engine) checks the data property
 * on scripted scenarios as well.
 *
 * Each game is played from a seed over a set built around face-down mechanics — morph, manifest
 * dread, disguise, cloak — and its actions and events recorded. At fork points the state is forked
 * and the recorded actions replayed through the checker. Three kinds of fork are made:
 *
 * - **A face-down object** against a player who may not look under it, made into another of the
 *   set's creatures — on arrival, then every so often while it stays hidden.
 * - **A card in a hand or library** against a player it is hidden from, made into any other card
 *   of the set through `HiddenSlotRewrite`. A library card is forked only against the owner's
 *   opponents, because a player knows their own decklist.
 * - **An order fork** for the owner of a library: two of its cards exchange identities, so its
 *   composition stays as the owner knows it and only the order differs.
 *
 * A slot the rewrite refuses is never forked, and neither is a card a player already saw through
 * an event-only reveal, a look, or a question that displayed it earlier in the game: [Visibility]
 * keeps no trace of those, so the swapped world would contradict what the viewer was shown. Hand
 * and library forks are made only where no question is pending, since a question raised before the
 * fork would otherwise be inherited by the swapped world unchanged.
 */
class HiddenIdentityRandomGamesTest : FunSpec({

    for ((setCode, seed) in SETS) {
        test("a hidden identity never reaches a player, nor decides whether a question is asked ($setCode)") {
            val set = MtgSetCatalog.requireByCode(setCode)
            // An expansion like Legions printed no basics of its own.
            val basics = set.basicLands.ifEmpty { MtgSetCatalog.requireByCode("ONS").basicLands }
            val registry = CardRegistry().apply {
                register(set.cards)
                register(basics)
            }
            val rng = Random(seed)
            // Hand and library forks draw from a stream of their own, so the games and decks are
            // exactly those of the face-down-only version of this test.
            val zoneRng = Random(seed.inv())
            val harness = Harness(
                registry,
                creatures = set.cards.filter { it.typeLine.isCreature },
                // A transforming card's back face can't sit in a hand or library.
                cards = (set.cards + basics).filter { registry.getFrontFace(it.name) == null },
            )
            val leaks = mutableListOf<String>()
            var faceDownChecked = 0
            var zoneChecked = 0
            repeat(GAMES_PER_SET) { game ->
                val deck1 = buildRandomSealedDeck(faceDownHeavyPool(set.cards), rng)
                val deck2 = buildRandomSealedDeck(faceDownHeavyPool(set.cards), rng)
                val trajectory = harness.playRandomGame(deck1, deck2, rng)
                val outcome = harness.checkForks(trajectory, rng, zoneRng, label = "$setCode seed=$seed game=$game")
                faceDownChecked += outcome.faceDownChecked
                zoneChecked += outcome.zoneChecked
                leaks += outcome.leaks
            }
            if (leaks.isNotEmpty()) {
                fail("Hidden identity reached a player, or decided whether one was asked a question:\n\n" + leaks.distinct().joinToString("\n\n"))
            }
            // A vacuous pass proves nothing: each path must have forked a few times and checked at
            // least one action after the fork. Counting forks rather than compared steps keeps the
            // guard from tripping when replay windows legitimately get shorter.
            faceDownChecked shouldBeGreaterThanOrEqual MIN_CHECKED_FORKS
            zoneChecked shouldBeGreaterThanOrEqual MIN_CHECKED_FORKS
        }
    }
}) {
    /** A recorded game: the state before each action, the action applied to it, and its events. */
    class Trajectory(val states: List<GameState>, val actions: List<GameAction>, val events: List<List<GameEvent>>)

    /** How many forks of each path checked at least one action after the fork, and the leaks found. */
    class ForkOutcome(val faceDownChecked: Int, val zoneChecked: Int, val leaks: List<String>)

    /** Where a forked card is hidden. */
    enum class SlotKind { FACE_DOWN, HAND, LIBRARY, LIBRARY_ORDER }

    /** A card whose identity [viewer] may not see. */
    data class HiddenPair(val hidden: EntityId, val viewer: EntityId, val kind: SlotKind)

    class Harness(
        private val registry: CardRegistry,
        private val creatures: List<CardDefinition>,
        private val cards: List<CardDefinition>,
    ) {
        private val processor = ActionProcessor(registry)
        private val enumerator = LegalActionEnumerator.create(registry)
        private val checker = HiddenIdentityForkChecker(registry)
        private val visibility = checker.visibility

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
            val events = mutableListOf<List<GameEvent>>()
            while (!state.gameOver && state.turnNumber < MAX_TURNS && actions.size < MAX_ACTIONS) {
                val action = chooseAction(state, rng) ?: break
                val applied = processor.process(state, action).result.takeIf { it.error == null }
                    ?.let { action to it }
                    ?: PassPriority(action.playerId).let { pass ->
                        processor.process(state, pass).result.takeIf { it.error == null }?.let { pass to it }
                    }
                    ?: break
                states += state
                actions += applied.first
                events += applied.second.events
                state = applied.second.state
            }
            return Trajectory(states, actions, events)
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

        fun checkForks(trajectory: Trajectory, rng: Random, zoneRng: Random, label: String): ForkOutcome {
            val shownAt = shownAt(trajectory)
            val (faceDownChecked, faceDownLeaks) = checkFaceDownForks(trajectory, shownAt, rng, label)
            val (zoneChecked, zoneLeaks) = checkZoneForks(trajectory, shownAt, zoneRng, label)
            return ForkOutcome(faceDownChecked, zoneChecked, faceDownLeaks + zoneLeaks)
        }

        /**
         * For each (card, player) shown in passing — by an event-only reveal or look, or by a
         * question that displayed it — the first state index at which the player has seen it.
         */
        private fun shownAt(trajectory: Trajectory): Map<Pair<EntityId, EntityId>, Int> {
            val shown = mutableMapOf<Pair<EntityId, EntityId>, Int>()
            for ((index, state) in trajectory.states.withIndex()) {
                // A question pending at this state is on the decider's screen now; an action's
                // events are seen only after it, from the next state on.
                checker.displayedByQuestion(state)?.let { (decider, ids) ->
                    for (id in ids) shown.putIfAbsent(id to decider, index)
                }
                val events = trajectory.events[index]
                for (player in state.turnOrder) {
                    for (id in revealedIds(events)) {
                        if (checker.revealedBy(events, id, player)) shown.putIfAbsent(id to player, index + 1)
                    }
                }
            }
            return shown
        }

        private fun revealedIds(events: List<GameEvent>): Set<EntityId> = events.flatMap { event ->
            when (event) {
                is com.wingedsheep.engine.core.CardsRevealedEvent -> event.cardIds
                is com.wingedsheep.engine.core.HandRevealedEvent -> event.cardIds
                is com.wingedsheep.engine.core.HandLookedAtEvent -> event.cardIds
                is com.wingedsheep.engine.core.LookedAtCardsEvent -> event.cardIds
                else -> emptyList()
            }
        }.toSet()

        private fun checkFaceDownForks(
            trajectory: Trajectory,
            shownAt: Map<Pair<EntityId, EntityId>, Int>,
            rng: Random,
            label: String,
        ): Pair<Int, List<String>> {
            val leaks = mutableListOf<String>()
            var checked = 0
            var forks = 0
            var nextForkAt = 0
            val seen = mutableSetOf<Pair<EntityId, EntityId>>()
            for ((index, state) in trajectory.states.withIndex()) {
                if (forks >= MAX_FORKS_PER_GAME) break
                val pairs = hiddenPairs(state, setOf(SlotKind.FACE_DOWN))
                    .map { it.hidden to it.viewer }
                    .filter { (shownAt[it] ?: Int.MAX_VALUE) > index }
                // Fork as soon as an object becomes hidden from someone, then every so often while
                // it stays hidden, so both its arrival and its later life on the board are covered.
                val fresh = pairs.filter { it !in seen }
                seen += pairs
                val pair = fresh.firstOrNull() ?: pairs.takeIf { index >= nextForkAt }?.randomOrNull(rng) ?: continue
                nextForkAt = index + REFORK_INTERVAL
                forks++
                val (hidden, viewer) = pair
                val identity = otherIdentity(state, hidden, creatures, rng) ?: continue
                val outcome = checker.replay(state, window(trajectory, index), checker.faceDownFork(state, hidden, identity, viewer, knownTo(shownAt, state, listOf(hidden), index)))
                if (outcome.checked > 0) checked++
                outcome.leak?.let { leaks += "$label fork@$index hidden=$hidden viewer=$viewer as ${identity.name}: $it" }
            }
            return checked to leaks
        }

        /**
         * Forks a hand or library card every [ZONE_FORK_INTERVAL] states, cycling through hand,
         * opponent's library and owner's library order. Every card in a hand or library is hidden
         * from someone from the first state on, so forking on arrival, as the face-down path does,
         * would spend the whole budget on the opening hand.
         */
        private fun checkZoneForks(
            trajectory: Trajectory,
            shownAt: Map<Pair<EntityId, EntityId>, Int>,
            rng: Random,
            label: String,
        ): Pair<Int, List<String>> {
            val leaks = mutableListOf<String>()
            var checked = 0
            var forks = 0
            for (index in ZONE_FORK_START until trajectory.states.size step ZONE_FORK_INTERVAL) {
                if (forks >= MAX_ZONE_FORKS_PER_GAME) break
                val state = trajectory.states[index]
                if (state.pendingDecision != null) continue
                val candidates = hiddenPairs(state, setOf(SlotKind.HAND, SlotKind.LIBRARY, SlotKind.LIBRARY_ORDER))
                    .filter { it.kind == SlotKind.HAND || libraryDepth(state, it.hidden) < LIBRARY_FORK_DEPTH }
                    .filter { (shownAt[it.hidden to it.viewer] ?: Int.MAX_VALUE) > index }
                    .filter { checker.rewriteRefusal(state, it.hidden) == null }
                val preferred = ZONE_KINDS[forks % ZONE_KINDS.size]
                val pair = candidates.filter { it.kind == preferred }.ifEmpty { candidates }.randomOrNull(rng) ?: continue
                val fork = when (pair.kind) {
                    SlotKind.LIBRARY_ORDER -> {
                        val partner = state.getLibrary(pair.viewer).filter { id ->
                            id != pair.hidden && name(state, id) != name(state, pair.hidden) &&
                                (shownAt[id to pair.viewer] ?: Int.MAX_VALUE) > index &&
                                checker.hiddenFrom(state, id, pair.viewer, faceDownFork = false) &&
                                checker.rewriteRefusal(state, id) == null
                        }.randomOrNull(rng) ?: continue
                        val fork = checker.orderFork(state, pair.hidden, partner, pair.viewer, knownTo(shownAt, state, listOf(pair.hidden, partner), index))
                        fork to "exchanged with $partner (${name(state, partner)})"
                    }
                    else -> {
                        val identity = otherIdentity(state, pair.hidden, cards, rng) ?: continue
                        checker.zoneFork(state, pair.hidden, identity, pair.viewer, knownTo(shownAt, state, listOf(pair.hidden), index)) to "as ${identity.name}"
                    }
                }
                forks++
                val outcome = checker.replay(state, window(trajectory, index), fork.first)
                if (outcome.checked > 0) checked++
                outcome.leak?.let {
                    leaks += "$label fork@$index hidden=${pair.hidden} (${name(state, pair.hidden)}, " +
                        "${pair.kind.name.lowercase()}) viewer=${pair.viewer} ${fork.second}: $it"
                }
            }
            return checked to leaks
        }

        /** The seats that already saw one of [cards] by state [index]. */
        private fun knownTo(
            shownAt: Map<Pair<EntityId, EntityId>, Int>,
            state: GameState,
            cards: List<EntityId>,
            index: Int,
        ): Set<EntityId> = state.turnOrder.filter { seat -> cards.any { (shownAt[it to seat] ?: Int.MAX_VALUE) <= index } }.toSet()

        private fun window(trajectory: Trajectory, from: Int): List<GameAction> =
            trajectory.actions.subList(from, minOf(trajectory.actions.size, from + REPLAY_WINDOW))

        /**
         * Every (hidden card, player it is hidden from) pair of the given [kinds] in [state]: a
         * face-down object; a card in its owner's hand; a library card against the owner's
         * opponents; and a library card against its owner, for an order fork.
         */
        private fun hiddenPairs(state: GameState, kinds: Set<SlotKind>): List<HiddenPair> {
            val faceDown = if (SlotKind.FACE_DOWN !in kinds) emptyList() else state.entities.keys
                .filter { state.getEntity(it)?.has<FaceDownComponent>() == true }
                .sortedBy { it.value }
                .flatMap { id ->
                    state.turnOrder.filter { visibility.isCardIdentityHiddenFrom(state, id, it) }
                        .map { HiddenPair(id, it, SlotKind.FACE_DOWN) }
                }
            val zones = state.turnOrder.flatMap { owner ->
                val hand = state.getHand(owner).flatMap { id ->
                    state.turnOrder.filter { checker.hiddenFrom(state, id, it, faceDownFork = false) }
                        .map { HiddenPair(id, it, SlotKind.HAND) }
                }
                val library = state.getLibrary(owner).flatMap { id ->
                    state.turnOrder.filter { checker.hiddenFrom(state, id, it, faceDownFork = false) }
                        .map { viewer ->
                            val owns = viewer == owner || state.actorFor(owner) == viewer
                            HiddenPair(id, viewer, if (owns) SlotKind.LIBRARY_ORDER else SlotKind.LIBRARY)
                        }
                }
                hand + library
            }.filter { it.kind in kinds }
            return faceDown + zones
        }

        private fun libraryDepth(state: GameState, id: EntityId): Int =
            state.turnOrder.firstNotNullOfOrNull { state.getLibrary(it).indexOf(id).takeIf { i -> i >= 0 } } ?: Int.MAX_VALUE

        private fun name(state: GameState, id: EntityId): String? = state.getEntity(id)?.get<CardComponent>()?.name

        private fun otherIdentity(
            state: GameState,
            hidden: EntityId,
            pool: List<CardDefinition>,
            rng: Random,
        ): CardDefinition? {
            val actual = name(state, hidden)
            return pool.filter { it.name != actual }.randomOrNull(rng)
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
        private const val MAX_ZONE_FORKS_PER_GAME = 3
        private const val ZONE_FORK_START = 20
        private const val ZONE_FORK_INTERVAL = 150
        private val ZONE_KINDS = listOf(SlotKind.HAND, SlotKind.LIBRARY, SlotKind.LIBRARY_ORDER)
        /** A library fork takes one of the top cards, which draws and looks in the window reach. */
        private const val LIBRARY_FORK_DEPTH = 7
        private const val REPLAY_WINDOW = 80
        private const val FACE_DOWN_BIAS = 0.75
        private const val MIN_CHECKED_FORKS = 3

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
