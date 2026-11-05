package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.FlipOntoBattlefieldDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.FlipThrowResponse
import com.wingedsheep.engine.mechanics.dexterity.FlipPhysics
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan as intShouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The AI's Chaos Orb flip ([FlipAim]): it throws at what it most wants destroyed, never at its own
 * board, and with a clean release.
 */
class FlipAimTest : ScenarioTestBase() {

    private val orbAbility = cardRegistry.getCard("Chaos Orb")!!.activatedAbilities.first().id

    private fun TestGame.flipDecision(): FlipOntoBattlefieldDecision {
        execute(ActivateAbility(playerId = player1Id, sourceId = findPermanent("Chaos Orb")!!, abilityId = orbAbility))
            .error shouldBe null
        resolveStack()
        return getPendingDecision().shouldBeInstanceOf<FlipOntoBattlefieldDecision>()
    }

    init {
        context("FlipAim") {

            test("aims at the opponent's most valuable permanent") {
                val game = scenario().withRngSeed(1993L)
                    .withPlayers("AI", "Opponent")
                    .withCardOnBattlefield(1, "Chaos Orb")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Shivan Dragon")
                    .withCardOnBattlefield(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val decision = game.flipDecision()
                val dragon = decision.table.cards.single { it.entityId == game.findPermanent("Shivan Dragon") }

                val response = AIPlayer.create(cardRegistry, game.player1Id)
                    .respondToDecision(game.state, decision)
                    .shouldBeInstanceOf<FlipThrowResponse>()
                // At the dragon, or at the seam between it and the Mountain behind it to take both.
                withClue("aimed within reach of the dragon") {
                    kotlin.math.hypot(response.aimX - dragon.x, response.aimY - dragon.y) shouldBeLessThan 60.0
                }
                // The same decision always gets the same throw.
                FlipAim.respond(game.state, decision, game.player1Id) shouldBe response
                game.execute(SubmitDecision(game.player1Id, response)).error shouldBe null
                game.isOnBattlefield("Grizzly Bears").shouldBeTrue()
            }

            test("its hand is a human's: it usually hits its goal, but not every time") {
                val game = scenario().withRngSeed(1993L)
                    .withPlayers("AI", "Opponent")
                    .withCardOnBattlefield(1, "Chaos Orb")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardOnBattlefield(2, "Shivan Dragon")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val decision = game.flipDecision()
                val dragon = decision.table.cards.single { it.entityId == game.findPermanent("Shivan Dragon") }

                val throws = (0 until 300).map { FlipAim.respond(game.state, decision, game.player1Id, hand = kotlin.random.Random(it)) }
                withClue("it lets go at different moments") { throws.map { it.strength }.distinct().size shouldBeGreaterThan 250 }
                withClue("it swings both ways") {
                    throws.count { it.sway < 0 } shouldBeGreaterThan 100
                    throws.count { it.sway > 0 } shouldBeGreaterThan 100
                }
                val hits = throws.withIndex().count { (i, t) ->
                    val landing = FlipPhysics.land(decision.table, t.aimX, t.aimY, t.strength, GameRng.seeded(i.toLong()), t.sway).first
                    landing.turnedOver && FlipPhysics.overlaps(landing, dragon)
                }
                withClue("hits $hits of 300") {
                    hits shouldBeGreaterThan 210
                    hits intShouldBeLessThan 295
                }
            }

            test("with nothing of the opponent's worth hitting, it throws the Orb off the table") {
                val game = scenario().withRngSeed(1993L)
                    .withPlayers("AI", "Opponent")
                    .withCardOnBattlefield(1, "Chaos Orb")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Grizzly Bears", isToken = true)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val decision = game.flipDecision()

                val response = FlipAim.respond(game.state, decision, game.player1Id)
                withClue("aimed past the far edge") { response.aimY shouldBeLessThan 0.0 }

                game.execute(SubmitDecision(game.player1Id, response)).error shouldBe null
                game.findPermanents("Grizzly Bears").size shouldBe 2
            }
        }
    }
}
