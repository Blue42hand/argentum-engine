package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class LivingDeathScenarioTest : ScenarioTestBase() {
    init {
        test("all three instructions are global batches; only the original graveyard cards return") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Living Death").withLandsOnBattlefield(1, "Swamp", 5)
                .withCardOnBattlefield(1, "Zulaport Cutthroat").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardInGraveyard(1, "Serra Angel").withCardInGraveyard(2, "Air Elemental")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Living Death").error shouldBe null; game.resolveStack()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(1, "Zulaport Cutthroat") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe true
            game.state.projectedState.getController(game.findPermanent("Serra Angel")!!) shouldBe game.player1Id
            game.state.projectedState.getController(game.findPermanent("Air Elemental")!!) shouldBe game.player2Id
            game.getLifeTotal(1) shouldBe 22; game.getLifeTotal(2) shouldBe 18
        }
        test("a sacrificed creature redirected into exile is not returned with the original exiled cards") {
            val game = scenario().withPlayers("Player", "Opponent")
                .withCardInHand(1, "Living Death").withLandsOnBattlefield(1, "Swamp", 5)
                .withCardOnBattlefield(1, "Grizzly Bears").withCardOnBattlefield(2, "Leyline of the Void")
                .withCardInGraveyard(1, "Serra Angel")
                .withCardInLibrary(1, "Swamp").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val bear = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Living Death").error shouldBe null; game.resolveStack()
            game.state.getExile(game.player1Id).contains(bear) shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
            game.isOnBattlefield("Serra Angel") shouldBe true
        }
    }
}

/**
 * Scenario tests for Living Death — "Each player exiles all creature cards from their graveyard,
 * then sacrifices all creatures they control, then puts all cards they exiled this way onto the
 * battlefield."
 *
 * The ordering is the whole card: the creatures sacrificed in the middle step land in the
 * graveyard *after* the exile, so they must stay there, while only the cards exiled "this way"
 * come back — each under its owner's control.
 */
class LivingDeathUpstreamScenarioTest : ScenarioTestBase() {

    init {
        context("Living Death") {

            test("graveyard creatures swap places with battlefield creatures for each player") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Living Death")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInGraveyard(1, "Savannah Lions")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInGraveyard(2, "Squire")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Living Death").error shouldBe null
                game.resolveStack()

                withClue("the previously-dead creatures are back on the battlefield") {
                    game.isOnBattlefield("Savannah Lions") shouldBe true
                    game.isOnBattlefield("Squire") shouldBe true
                }
                withClue("each returns under its owner's control") {
                    val lions = game.findPermanent("Savannah Lions")!!
                    val squire = game.findPermanent("Squire")!!
                    game.state.getEntity(lions)?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
                    game.state.getEntity(squire)?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
                }
                withClue("the sacrificed creatures stay in the graveyard — they weren't exiled this way") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("nothing is left in exile") {
                    game.isInExile(1, "Savannah Lions") shouldBe false
                    game.isInExile(2, "Squire") shouldBe false
                }
                withClue("the sorcery itself goes to the graveyard, not the battlefield") {
                    game.isInGraveyard(1, "Living Death") shouldBe true
                }
            }

            test("with empty graveyards every creature is sacrificed and nothing returns") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Living Death")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Living Death").error shouldBe null
                game.resolveStack()

                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.isOnBattlefield("Hill Giant") shouldBe false
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                game.isInGraveyard(2, "Hill Giant") shouldBe true
            }
        }
    }
}
