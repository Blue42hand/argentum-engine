package com.wingedsheep.engine.mechanics.dexterity

import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The release meter the flipping player plays against, sent with the decision so the client draws
 * exactly the zones and scatter the engine scores. Strength runs 0..1; a release at [sweetSpot]
 * carries exactly to the aim, and every bit of strength either side of it drifts the landing
 * [depthPerStrength] mm short of the aim or past it, and the hand's sideways sway at release pulls
 * it up to [sidePerSway] mm to either side. Inside [cleanFrom]..[cleanTo] the hand is as
 * steady as it gets; below [tooSoftBelow] the card is unlikely to turn over. The scatter terms are
 * [FlipPhysics.scatter]'s, for the aim preview.
 */
@Serializable
data class FlipReleaseProfile(
    val sweetSpot: Double = FlipPhysics.SWEET_SPOT,
    val depthPerStrength: Double = FlipPhysics.DEPTH_PER_STRENGTH,
    val sidePerSway: Double = FlipPhysics.SIDE_PER_SWAY,
    val cleanFrom: Double = FlipPhysics.CLEAN_FROM,
    val cleanTo: Double = FlipPhysics.CLEAN_TO,
    val tooSoftBelow: Double = FlipPhysics.TOO_SOFT_BELOW,
    val baseScatter: Double = FlipPhysics.BASE_SCATTER,
    val scatterPerMm: Double = FlipPhysics.SCATTER_PER_MM,
    val sloppyScatter: Double = FlipPhysics.SLOPPY_SCATTER,
    val slopRange: Double = FlipPhysics.SLOP_RANGE,
)

/**
 * How a flipped card flies and where it lands, given the player's intent.
 *
 * The player supplies an aim point and a release strength; the engine supplies the hand's
 * unsteadiness. The release decides how far the card carries: early falls short along the line
 * from the hand (often onto the thrower's own permanents), late sails long, so a mistimed flip
 * comes down on some other card rather than just "a bit off". The hand also sways from side to
 * side while it swings, and wherever the sway is at release pulls the card that far to the
 * thrower's left or right of the line. The aim is the player's to get
 * right: a clean release lands within a few millimetres of it, so random scatter stays small —
 * growing a little with distance from the hand and more with a sloppy release — and the number
 * of half-turns grows with strength. All randomness comes from the game's seeded RNG, so a flip
 * replays identically.
 */
object FlipPhysics {
    const val SWEET_SPOT = 0.74
    const val CLEAN_FROM = 0.70
    const val CLEAN_TO = 0.78
    const val TOO_SOFT_BELOW = 0.2

    /** How far (mm) a release carries short or long per unit of strength off the sweet spot. */
    const val DEPTH_PER_STRENGTH = 800.0
    /** How far (mm) a release at the very edge of the hand's sway pulls to that side. */
    const val SIDE_PER_SWAY = 110.0

    /** Scatter (one standard deviation, mm) of a clean release, before distance. */
    const val BASE_SCATTER = 3.0
    /** Extra scatter per mm between the hand and the aim point. */
    const val SCATTER_PER_MM = 0.016
    /** Extra scatter of the worst possible release. */
    const val SLOPPY_SCATTER = 35.0
    /** How far outside the clean zone a release must stray to be as sloppy as it gets. */
    const val SLOP_RANGE = 0.4

    /** Spread (degrees, one standard deviation) of the resting angle of a clean / the sloppiest release. */
    const val CLEAN_SKEW = 7.0
    const val SLOPPY_SKEW = 40.0

    /** Half-turns a release of strength 0 / 1 averages, and the spread around that. */
    private const val HALF_TURNS_AT_ZERO = 0.9
    private const val HALF_TURNS_PER_STRENGTH = 7.5
    private const val HALF_TURN_NOISE = 0.55

    /** Aim is clamped to this far beyond the table's edges; past it, a throw is still a miss. */
    private const val AIM_SLACK = 400.0

    /** 1 for a clean release, falling to 0 as it strays [SLOP_RANGE] outside the clean zone. */
    fun releaseQuality(strength: Double): Double {
        val s = strength.coerceIn(0.0, 1.0)
        val miss = when {
            s < CLEAN_FROM -> CLEAN_FROM - s
            s > CLEAN_TO -> s - CLEAN_TO
            else -> 0.0
        }
        return 1.0 - min(1.0, miss / SLOP_RANGE)
    }

    /**
     * Where a release of [strength] aimed at ([aimX], [aimY]) carries to before scatter: the aim
     * point, pushed along the line from the hand by the release's distance from the sweet spot,
     * and across it by the hand's [sway] (positive is the thrower's right).
     */
    fun carry(table: FlipTable, aimX: Double, aimY: Double, strength: Double, sway: Double = 0.0): Pair<Double, Double> {
        val dx = aimX - table.handX
        val dy = aimY - table.handY
        val length = hypot(dx, dy)
        // Aiming at the hand itself: the throw still goes away from the thrower.
        val (ux, uy) = if (length < 1.0) 0.0 to -1.0 else dx / length to dy / length
        val drift = drift(strength)
        // The thrower faces along (ux, uy); their right-hand side is (-uy, ux).
        val pull = pull(sway)
        return (aimX + ux * drift - uy * pull) to (aimY + uy * drift + ux * pull)
    }

    /** Sideways carry error (mm) of a release: positive pulls to the thrower's right. */
    fun pull(sway: Double): Double = sway.coerceIn(-1.0, 1.0) * SIDE_PER_SWAY

    /** Signed carry error (mm) of a release: negative falls short, positive sails long. */
    fun drift(strength: Double): Double = (strength.coerceIn(0.0, 1.0) - SWEET_SPOT) * DEPTH_PER_STRENGTH

    /** Standard deviation (mm) of where a throw at ([aimX], [aimY]) with [strength] lands. */
    fun scatter(table: FlipTable, aimX: Double, aimY: Double, strength: Double): Double {
        val distance = hypot(aimX - table.handX, aimY - table.handY)
        return BASE_SCATTER + SCATTER_PER_MM * distance + SLOPPY_SCATTER * (1.0 - releaseQuality(strength)).pow(1.6)
    }

    /** Roll where the flip lands. Returns the landing and the advanced RNG. */
    fun land(
        table: FlipTable,
        aimX: Double,
        aimY: Double,
        strength: Double,
        rng: GameRng,
        sway: Double = 0.0,
    ): Pair<FlipLanding, GameRng> {
        val ax = aimX.coerceIn(-AIM_SLACK, table.width + AIM_SLACK)
        val ay = aimY.coerceIn(-AIM_SLACK, table.height + AIM_SLACK)
        val s = strength.coerceIn(0.0, 1.0)
        val sigma = scatter(table, ax, ay, s)
        val w = if (sway.isFinite()) sway.coerceIn(-1.0, 1.0) else 0.0
        val (cx, cy) = carry(table, ax, ay, s, w)

        var r = rng
        fun uniform(): Double { val (d, next) = r.nextDouble(); r = next; return d }
        fun gaussian(): Double {
            // Box–Muller; 1 - u keeps the log argument away from zero.
            val u1 = 1.0 - uniform()
            val u2 = uniform()
            return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
        }

        val x = cx + gaussian() * sigma
        val y = cy + gaussian() * sigma
        // A clean release keeps the card nearly square to the thrower, so the aim ghost tells the
        // truth about what it covers; a sloppy one can come down at any skew.
        val skew = CLEAN_SKEW + SLOPPY_SKEW * (1.0 - releaseQuality(s))
        val rotation = ((gaussian() * skew) % 360.0 + 360.0) % 360.0
        val halfTurns = floor(max(0.0, HALF_TURNS_AT_ZERO + HALF_TURNS_PER_STRENGTH * s + gaussian() * HALF_TURN_NOISE)).toInt()
        return FlipLanding(x = x, y = y, rotation = rotation, halfTurns = halfTurns, drift = drift(s), pull = pull(w), aimX = ax, aimY = ay) to r
    }

    /**
     * Every card on [table] the flipped card, resting at [landing], overlaps — the touchable part of
     * it (an attachment only where it shows from under its host). Rotated-rectangle overlap by the
     * separating-axis test.
     */
    fun touched(table: FlipTable, landing: FlipLanding): List<EntityId> =
        table.cards.filter { overlaps(landing, it) }.map { it.entityId }

    fun overlaps(landing: FlipLanding, card: FlipTableCard): Boolean {
        val theta = Math.toRadians(landing.rotation)
        val c = cos(theta)
        val s = sin(theta)
        val hw = FlipTableLayout.CARD_WIDTH / 2
        val hh = FlipTableLayout.CARD_HEIGHT / 2
        // The landed card's corners.
        val orb = listOf(-hw to -hh, hw to -hh, hw to hh, -hw to hh).map { (dx, dy) ->
            (landing.x + dx * c - dy * s) to (landing.y + dx * s + dy * c)
        }
        val left = card.x - card.width / 2
        val right = card.x + card.width / 2
        val rect = listOf(left to card.hitTop, right to card.hitTop, right to card.hitBottom, left to card.hitBottom)
        val axes = listOf(1.0 to 0.0, 0.0 to 1.0, c to s, -s to c)
        return axes.all { (axisX, axisY) ->
            val a = orb.map { (px, py) -> px * axisX + py * axisY }
            val b = rect.map { (px, py) -> px * axisX + py * axisY }
            // Touching edges count as touching; strict separation is a miss.
            a.max() >= b.min() && b.max() >= a.min()
        }
    }
}
