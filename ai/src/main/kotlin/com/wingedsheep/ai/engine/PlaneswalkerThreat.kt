package com.wingedsheep.ai.engine

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.core.doesntUntapDuringUntapStep
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityCost

/**
 * What a planeswalker on the battlefield is worth as an **ongoing engine**, and what loyalty damage
 * to it takes away — one primitive for every combat question about walkers, whichever side of the
 * table they are on.
 *
 * A walker is not a pile of counters: each turn it survives it fires an ability, so its worth is
 * roughly what those activations will still produce. Read off its printed loyalty abilities:
 *
 * - **A walker with a + ability** is a perpetual engine — it refuels itself and keeps acting for as
 *   long as nobody attacks it. It carries [ENGINE_VALUE] on top of its loyalty.
 * - **A walker whose every ability costs loyalty** (the empower-Jace token) is a fixed budget of
 *   activations; it is worth little beyond the loyalty it has left ([FUEL_VALUE]).
 * - **Its biggest ability in reach** — loyalty at least the largest minus it prints — is the next
 *   activation its controller most wants; a walker standing there carries [TOP_ABILITY_THREAT], and
 *   damage that knocks it back out of reach takes that away even when it does not kill.
 *
 * Killing a walker outright takes all of [value]; a hit that leaves it alive takes the loyalty it
 * removes plus, when it pushes the walker below its biggest ability, that ability's threat. The
 * units are the evaluator's board units (one loyalty counter is [LOYALTY_VALUE], as in
 * `BoardPresence.permanentValue`), so callers weigh them against life at the board-presence weight.
 *
 * Printed abilities only, like `IntentCatalog.loyaltyIsOnlyFuel`; without a registry (or for a
 * card it cannot read) a walker is assumed to be an engine — the dangerous reading.
 *
 * **The defending side** ([AiProfile.defendPlaneswalkers]) asks two more questions of it: what a
 * walker is worth on the board *before its controller acts again* ([boardValue], read by the
 * evaluator with [expectedDamage]), and what blocking an attacker aimed at it saves (`CombatAdvisor`
 * prices a block on a walker attacker by [damageValue]).
 */
object PlaneswalkerThreat {

    /** Board worth of a walker that refuels itself, beyond its loyalty. The evaluator's walker floor. */
    const val ENGINE_VALUE = 4.0

    /** Board worth of a walker that can only spend loyalty, beyond the loyalty it has left. */
    const val FUEL_VALUE = 1.0

    /** Board value of one loyalty counter — the evaluator's `LOYALTY_VALUE`. */
    const val LOYALTY_VALUE = 0.8

    /** The threat of a walker that can fire its biggest ability next turn. */
    const val TOP_ABILITY_THREAT = 2.0

    /** The shape of a walker's printed loyalty abilities. */
    data class Shape(
        /** Whether any printed loyalty ability adds loyalty (+N or +0 counts as refuelling). */
        val refuels: Boolean,
        /** The largest loyalty a printed minus ability costs; 0 when it prints none. */
        val topCost: Int,
    ) {
        /** Whether a walker at [loyalty] can fire its biggest minus ability. */
        fun topInReach(loyalty: Int): Boolean = topCost > 0 && loyalty >= topCost

        companion object {
            /** What an unreadable walker is assumed to be: an engine with nothing big in reach. */
            val UNKNOWN = Shape(refuels = true, topCost = 0)
        }
    }

    /** Loyalty counters on [walkerId]; 0 when it has none or is gone. */
    fun loyalty(state: GameState, walkerId: EntityId): Int =
        state.getEntity(walkerId)?.get<CountersComponent>()?.getCount(CounterType.LOYALTY) ?: 0

    /** The printed shape of [walkerId]'s loyalty abilities, [Shape.UNKNOWN] when it cannot be read. */
    fun shape(state: GameState, walkerId: EntityId, cardRegistry: CardRegistry?): Shape {
        val name = state.getEntity(walkerId)?.get<CardComponent>()?.name ?: return Shape.UNKNOWN
        val definition = cardRegistry?.getCard(name) ?: return Shape.UNKNOWN
        val costs = definition.script.activatedAbilities
            .filter { it.isPlaneswalkerAbility }
            .mapNotNull { (it.cost as? AbilityCost.Loyalty)?.change }
        if (costs.isEmpty()) return Shape.UNKNOWN
        return Shape(
            refuels = costs.any { it >= 0 },
            topCost = costs.filter { it < 0 }.maxOfOrNull { -it } ?: 0,
        )
    }

    /** What removing [walkerId] from the battlefield takes away from its controller. */
    fun value(state: GameState, walkerId: EntityId, cardRegistry: CardRegistry?): Double {
        val shape = shape(state, walkerId, cardRegistry)
        val loyalty = loyalty(state, walkerId)
        return (if (shape.refuels) ENGINE_VALUE else FUEL_VALUE) +
            loyalty * LOYALTY_VALUE +
            if (shape.topInReach(loyalty)) TOP_ABILITY_THREAT else 0.0
    }

    /**
     * What [damage] loyalty damage to [walkerId] takes away: all of [value] when it kills, otherwise
     * the counters it removes plus [TOP_ABILITY_THREAT] when it knocks the walker's biggest ability
     * out of reach.
     */
    fun damageValue(state: GameState, walkerId: EntityId, damage: Int, cardRegistry: CardRegistry?): Double {
        if (damage <= 0) return 0.0
        val loyalty = loyalty(state, walkerId)
        if (damage >= loyalty) return value(state, walkerId, cardRegistry)
        val shape = shape(state, walkerId, cardRegistry)
        val deniesTop = shape.topInReach(loyalty) && !shape.topInReach(loyalty - damage)
        return damage * LOYALTY_VALUE + if (deniesTop) TOP_ABILITY_THREAT else 0.0
    }

    /**
     * The evaluator's base for a walker that refuels, beyond its loyalty, when its card prior is
     * lower. 1.6 + 0.8 × 3 is the old flat 4.0 floor at a typical starting loyalty — so a fresh
     * engine is priced as before, and only a walker worn below it is priced lower than before.
     */
    const val ENGINE_BOARD_BASE = 1.6

    /**
     * What a walker is worth on the board — continuous in loyalty, so every counter gained or lost
     * moves the score — once [expectedDamage] has been taken off its loyalty.
     *
     * A walker that refuels is worth its card prior (what its abilities do) or [ENGINE_BOARD_BASE],
     * whichever is more; one that can only spend loyalty is worth [FUEL_VALUE] — its prior reads
     * "repeatable draw" off a −3 that fires once, which priced a one-loyalty Jace token like a
     * four-loyalty one. On top, [LOYALTY_VALUE] per counter it will still have. A walker the
     * damage kills is worth nothing: whatever it has left is about to be lost, which is what makes
     * spending it now ("use it or lose it") and blocking for it visible to the search.
     */
    fun boardValue(refuels: Boolean, prior: Double, loyalty: Int, expectedDamage: Int): Double {
        val left = loyalty - expectedDamage
        if (left <= 0) return 0.0
        return (if (refuels) maxOf(prior, ENGINE_BOARD_BASE) else FUEL_VALUE) + left * LOYALTY_VALUE
    }

    /**
     * Loyalty damage [controllerId]'s planeswalkers should expect before [controllerId] acts again:
     * on its own turn, what the strongest opposing side's next attack pushes through its blockers;
     * on anyone else's turn, nothing.
     *
     * Only the side whose turn it is has a next attack still to face: the other side's walkers are
     * facing *this* turn's attack, which the search plays out rather than predicts. The attackers
     * are every opposing creature that will untap for it — summoning sickness is gone by then — and
     * the blockers every untapped creature of ours that can block, chumps included
     * ([CombatMath.calculateDamageThroughOptimalBlocking]): a creature tapped to attack this turn is
     * no defence of a walker next turn, which is what lets the attack search see the walker it
     * leaves open. The whole push-through is charged to every walker on its own; whichever one the
     * attack picks, that is the damage it takes.
     */
    fun expectedDamage(state: GameState, projected: ProjectedState, controllerId: EntityId): Int {
        if (state.activePlayerId != controllerId) return 0
        val sides = state.sidesFor(controllerId) ?: return 0
        val blockers = sides.mine.flatMap { projected.getBattlefieldControlledBy(it) }.filter {
            projected.isCreature(it) && !projected.cantBlock(it) && state.getEntity(it)?.has<TappedComponent>() != true
        }
        return sides.opponents.maxOfOrNull { team ->
            val attackers = team.flatMap { projected.getBattlefieldControlledBy(it) }.filter {
                projected.isCreature(it) && !projected.cantAttack(it) &&
                    Keyword.DEFENDER.name !in projected.getKeywords(it) &&
                    (state.getEntity(it)?.has<TappedComponent>() != true || !projected.doesntUntapDuringUntapStep(it))
            }
            if (attackers.isEmpty()) 0
            else CombatMath.calculateDamageThroughOptimalBlocking(state, projected, attackers, blockers)
        } ?: 0
    }

    /** Whether [walkerId] will not survive [expectedDamage] — anything it still has is spend-or-lose. */
    fun doomed(state: GameState, projected: ProjectedState, walkerId: EntityId): Boolean {
        val controller = projected.getController(walkerId) ?: return false
        val loyalty = loyalty(state, walkerId)
        return loyalty > 0 && expectedDamage(state, projected, controller) >= loyalty
    }
}
