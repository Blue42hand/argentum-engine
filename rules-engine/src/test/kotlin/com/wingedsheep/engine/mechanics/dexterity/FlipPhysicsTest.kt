package com.wingedsheep.engine.mechanics.dexterity

import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan as intShouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Engine coverage for the dexterity flip's physics ([FlipPhysics]): what counts as touching, and how
 * the player's release shapes where the card lands and whether it turns over. Chaos Orb's own text
 * is the rule here — the Comprehensive Rules say nothing about dexterity.
 */
class FlipPhysicsTest : FunSpec({

    val host = EntityId("host")
    val aura = EntityId("aura")

    fun card(id: EntityId, x: Double, y: Double, w: Double = 63.0, h: Double = 88.0) =
        FlipTableCard(entityId = id, controllerId = EntityId("p2"), x = x, y = y, width = w, height = h, tapped = false, eligible = true)

    fun table(vararg cards: FlipTableCard) = FlipTable(
        width = 700.0, height = 600.0,
        bands = emptyList(), cards = cards.toList(),
        handX = 350.0, handY = 670.0,
    )

    context("touching") {
        val target = card(host, x = 300.0, y = 200.0)

        test("a card resting squarely on another touches it") {
            FlipPhysics.overlaps(FlipLanding(300.0, 200.0, 0.0, halfTurns = 2), target).shouldBeTrue()
        }

        test("edges that just meet still touch") {
            // 63 mm wide each, centres 63 mm apart: the long edges lie on each other.
            FlipPhysics.overlaps(FlipLanding(363.0, 200.0, 0.0, halfTurns = 2), target).shouldBeTrue()
            FlipPhysics.overlaps(FlipLanding(363.5, 200.0, 0.0, halfTurns = 2), target).shouldBeFalse()
        }

        test("a turned card reaches further than an upright one") {
            // Upright, its half-width is 31.5 mm; turned 90° it's 44 mm — so 72 mm away only the turned one touches.
            FlipPhysics.overlaps(FlipLanding(372.0, 200.0, 0.0, halfTurns = 2), target).shouldBeFalse()
            FlipPhysics.overlaps(FlipLanding(372.0, 200.0, 90.0, halfTurns = 2), target).shouldBeTrue()
        }

        test("a diagonal landing whose corner clears the card is a miss, by the separating-axis test") {
            // Bounding boxes overlap, but at 45° the corner points away from the card.
            FlipPhysics.overlaps(FlipLanding(300.0 + 31.5 + 45.0, 200.0 - 44.0 - 45.0, 45.0, halfTurns = 2), target).shouldBeFalse()
        }

        test("an Aura tucked under its host is touchable only where it shows") {
            // The strip behind the host: the aura's back 22 mm.
            val tucked = card(aura, x = 300.0, y = 178.0).copy(hitTop = 134.0, hitBottom = 156.0)
            // Resting on the host's lower half covers the aura's hidden part, not its strip.
            FlipPhysics.overlaps(FlipLanding(300.0, 260.0, 0.0, halfTurns = 2), tucked).shouldBeFalse()
            FlipPhysics.overlaps(FlipLanding(300.0, 120.0, 0.0, halfTurns = 2), tucked).shouldBeTrue()
        }

        test("touched lists every card the landing overlaps") {
            val left = card(EntityId("left"), x = 200.0, y = 200.0)
            val right = card(EntityId("right"), x = 273.0, y = 200.0)
            val far = card(EntityId("far"), x = 600.0, y = 500.0)
            FlipPhysics.touched(table(left, right, far), FlipLanding(236.5, 200.0, 0.0, halfTurns = 2)) shouldBe
                listOf(EntityId("left"), EntityId("right"))
        }
    }

    context("the release") {
        val t = table()

        test("a release in the clean zone is as steady as a hand gets; straying outside it is sloppier") {
            FlipPhysics.releaseQuality(0.74) shouldBe 1.0
            FlipPhysics.releaseQuality(FlipPhysics.CLEAN_FROM) shouldBe 1.0
            FlipPhysics.releaseQuality(0.0) shouldBe 0.0
            FlipPhysics.scatter(t, 350.0, 200.0, 0.95) shouldBeGreaterThan FlipPhysics.scatter(t, 350.0, 200.0, 0.74)
        }

        test("throwing further scatters more") {
            FlipPhysics.scatter(t, 350.0, 100.0, 0.74) shouldBeGreaterThan FlipPhysics.scatter(t, 350.0, 500.0, 0.74)
        }

        test("the same throw from the same game state lands in the same place") {
            val rng = GameRng.seeded(42)
            FlipPhysics.land(t, 350.0, 200.0, 0.74, rng).first shouldBe FlipPhysics.land(t, 350.0, 200.0, 0.74, rng).first
            FlipPhysics.land(t, 350.0, 200.0, 0.74, rng).second shouldNotBe rng
        }

        test("a clean release always turns over; a limp one rarely does") {
            val seeds = 0L until 400L
            val cleanTurned = seeds.count { FlipPhysics.land(t, 350.0, 200.0, 0.74, GameRng.seeded(it)).first.turnedOver }
            val limpTurned = seeds.count { FlipPhysics.land(t, 350.0, 200.0, 0.0, GameRng.seeded(it)).first.turnedOver }
            cleanTurned shouldBe 400
            limpTurned intShouldBeLessThan 40
        }

        test("a clean release lands where it was aimed: the aim, not luck, decides what it hits") {
            val misses = (0L until 400L).map {
                val landing = FlipPhysics.land(t, 350.0, 200.0, 0.74, GameRng.seeded(it)).first
                kotlin.math.hypot(landing.x - 350.0, landing.y - 200.0)
            }
            misses.average() shouldBeLessThan 15.0
            // Within a card gap of the aim almost every time.
            misses.count { it > 30.0 } intShouldBeLessThan 16
        }

        test("letting go early falls short towards the thrower; late sails long") {
            // The hand is at y = 670, straight below the aim: short means a larger y.
            fun meanY(strength: Double) = (0L until 200L).map {
                FlipPhysics.land(t, 350.0, 200.0, strength, GameRng.seeded(it)).first.y
            }.average()
            meanY(0.60) shouldBeGreaterThan 200.0 + 90.0
            meanY(0.90) shouldBeLessThan 200.0 - 90.0
            FlipPhysics.drift(FlipPhysics.SWEET_SPOT) shouldBe 0.0
            FlipPhysics.land(t, 350.0, 200.0, 0.60, GameRng.seeded(1)).first.drift shouldBeLessThan 0.0
        }

        test("the carry runs along the line from the hand, whichever way the throw goes") {
            // Aimed up and to the right: a late release carries further up and further right.
            val (x, y) = FlipPhysics.carry(t, 650.0, 370.0, 0.90)
            x shouldBeGreaterThan 650.0
            y shouldBeLessThan 370.0
            (x - 650.0) shouldBe ((370.0 - y) plusOrMinus 1e-9)
        }

        test("the hand's sway pulls it to the thrower's left or right of the line, never along it") {
            // Hand straight below the aim: the thrower's right is +x.
            val (rx, ry) = FlipPhysics.carry(t, 350.0, 200.0, FlipPhysics.SWEET_SPOT, sway = 1.0)
            rx shouldBe (350.0 + FlipPhysics.SIDE_PER_SWAY plusOrMinus 1e-9)
            ry shouldBe (200.0 plusOrMinus 1e-9)
            FlipPhysics.carry(t, 350.0, 200.0, FlipPhysics.SWEET_SPOT, sway = -0.5).first shouldBe
                (350.0 - FlipPhysics.SIDE_PER_SWAY / 2 plusOrMinus 1e-9)
            // Out-of-range sway is clamped to the edge of the swing.
            FlipPhysics.pull(7.0) shouldBe FlipPhysics.SIDE_PER_SWAY
            val landing = FlipPhysics.land(t, 350.0, 200.0, FlipPhysics.SWEET_SPOT, GameRng.seeded(3), sway = -1.0).first
            landing.pull shouldBe -FlipPhysics.SIDE_PER_SWAY
            landing.x shouldBeLessThan 350.0 - 60.0
        }

        test("a clean release lands nearly square, so the aim ghost shows what it covers; a sloppy one skews") {
            fun skew(strength: Double) = (0L until 200L).map {
                val r = FlipPhysics.land(t, 350.0, 200.0, strength, GameRng.seeded(it)).first.rotation
                kotlin.math.abs(((r + 180.0) % 360.0) - 180.0)
            }.average()
            skew(0.74) shouldBeLessThan 12.0
            skew(0.30) shouldBeGreaterThan skew(0.74) * 2
        }

        test("aim is clamped near the table, so no throw can reach an absurd point") {
            val landing = FlipPhysics.land(t, 1e9, -1e9, 0.74, GameRng.seeded(7)).first
            landing.x shouldBeLessThan 700.0 + 600.0
            landing.y shouldBeGreaterThan -600.0
            landing.halfTurns intShouldBeGreaterThan 1
        }
    }
})
