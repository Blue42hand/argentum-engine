package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.evaluation.BoardPresence
import com.wingedsheep.engine.core.FlipOntoBattlefieldDecision
import com.wingedsheep.engine.core.FlipThrowResponse
import com.wingedsheep.engine.mechanics.dexterity.FlipLanding
import com.wingedsheep.engine.mechanics.dexterity.FlipPhysics
import com.wingedsheep.engine.mechanics.dexterity.FlipTableCard
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Where the AI throws a dexterity flip (Chaos Orb).
 *
 * The AI plays the mini-game with a human's hands: it picks a goal, then lets go a little early
 * or late and with its hand a little off-centre, like a decent player does — so it usually hits
 * what it aims at, sometimes clips the card beside it, and now and then misses outright. It
 * scores each candidate aim point by its *expected* haul under that unsteadiness plus the
 * engine's own scatter: the value of the opponents' permanents it would destroy minus a little
 * more than the value of its own. Candidates are each counted permanent and the midpoints of
 * nearby pairs (an Orb on the seam between two cards takes both). When nothing scores above
 * zero it throws the Orb off the table rather than hurt itself.
 */
object FlipAim {
    /** The AI's timing error (one standard deviation of release strength) — about a human's. */
    const val RELEASE_SPREAD = 0.06
    /** The AI's sideways error (one standard deviation of hand sway at release). */
    const val SWAY_SPREAD = 0.3
    private const val OWN_LOSS_WEIGHT = 1.25
    private const val PAIR_REACH = 140.0
    private val ROTATIONS = listOf(-FlipPhysics.CLEAN_SKEW, 0.0, FlipPhysics.CLEAN_SKEW)

    /**
     * [samples] landings per candidate per rotation; fewer for playouts, more for the real move.
     * [hand] draws the release error; by default it is seeded from the decision, so the same
     * decision always gets the same throw.
     */
    fun respond(
        state: GameState,
        decision: FlipOntoBattlefieldDecision,
        playerId: EntityId,
        samples: Int = 16,
        hand: Random = Random(decision.id.hashCode()),
    ): FlipThrowResponse {
        val table = decision.table
        val projected = state.projectedState
        val worth = table.cards.filter { it.eligible }.associate { card ->
            val value = 1.0 + (state.getEntity(card.entityId)?.get<CardComponent>()
                ?.let { BoardPresence.permanentValue(state, projected, card.entityId, it) } ?: 0.0)
            val mine = card.controllerId == playerId || !state.isOpponentTo(card.controllerId, playerId)
            card.entityId to if (mine) -OWN_LOSS_WEIGHT * value else value
        }
        val targets = table.cards.filter { (worth[it.entityId] ?: 0.0) > 0.0 }
        val candidates = targets.map { centre(it) } + targets.flatMapIndexed { i, a ->
            targets.drop(i + 1).mapNotNull { b ->
                val (ax, ay) = centre(a)
                val (bx, by) = centre(b)
                if (hypot(ax - bx, ay - by) <= PAIR_REACH) (ax + bx) / 2 to (ay + by) / 2 else null
            }
        }

        val offsets = spread(samples)
        val releaseSigma = hypot(RELEASE_SPREAD * FlipPhysics.DEPTH_PER_STRENGTH, SWAY_SPREAD * FlipPhysics.SIDE_PER_SWAY)
        fun expected(x: Double, y: Double): Double {
            val sigma = hypot(FlipPhysics.scatter(table, x, y, FlipPhysics.SWEET_SPOT), releaseSigma)
            var total = 0.0
            for ((dx, dy) in offsets) for (rotation in ROTATIONS) {
                val landing = FlipLanding(x + dx * sigma, y + dy * sigma, rotation, halfTurns = 2)
                for (card in table.cards) {
                    val w = worth[card.entityId] ?: continue
                    if (FlipPhysics.overlaps(landing, card)) total += w
                }
            }
            return total / (offsets.size * ROTATIONS.size)
        }

        val best = candidates.map { it to expected(it.first, it.second) }.maxByOrNull { it.second }
        val (x, y) = if (best != null && best.second > 0.0) best.first else table.width / 2 to -2 * table.height
        val strength = (FlipPhysics.SWEET_SPOT + gaussian(hand) * RELEASE_SPREAD).coerceIn(0.0, 1.0)
        val sway = (gaussian(hand) * SWAY_SPREAD).coerceIn(-1.0, 1.0)
        return FlipThrowResponse(decision.id, x, y, strength, sway)
    }

    private fun gaussian(random: Random): Double =
        sqrt(-2.0 * kotlin.math.ln(1.0 - random.nextDouble())) * cos(2.0 * PI * random.nextDouble())

    private fun centre(card: FlipTableCard) = card.x to (card.hitTop + card.hitBottom) / 2

    /** A deterministic, roughly unit-normal spread of [n] points (a sunflower over the normal's CDF). */
    private fun spread(n: Int): List<Pair<Double, Double>> = List(n) { i ->
        val u = (i + 0.5) / n
        val r = sqrt(-2.0 * kotlin.math.ln(1.0 - u))
        val angle = i * PI * (3.0 - sqrt(5.0))
        r * cos(angle) to r * sin(angle)
    }
}
