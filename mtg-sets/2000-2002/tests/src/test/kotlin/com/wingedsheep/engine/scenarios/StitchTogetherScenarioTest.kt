package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Stitch Together chooses its threshold branch when the spell resolves. */
class StitchTogetherScenarioTest : ScenarioTestBase() {
    private fun board(
        ownGraveyardCards: Int,
        opponentGraveyardCards: Int = 0,
        extraHand1: String? = null,
        extraHand2: String? = null,
        ownSwamps: Int = 2,
        opponentSwamps: Int = 0,
    ): TestGame = scenario().withPlayers()
        .withCardInHand(1, "Stitch Together")
        .withCardInGraveyard(1, "Grizzly Bears")
        .apply {
            repeat(ownGraveyardCards - 1) { withCardInGraveyard(1, "Swamp") }
            repeat(opponentGraveyardCards) { withCardInGraveyard(2, "Swamp") }
            if (extraHand1 != null) withCardInHand(1, extraHand1)
            if (extraHand2 != null) withCardInHand(2, extraHand2)
        }
        .withLandsOnBattlefield(1, "Swamp", ownSwamps)
        .withLandsOnBattlefield(2, "Swamp", opponentSwamps)
        .withActivePlayer(1)
        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        .build()

    private fun TestGame.castStitch() =
        castSpellTargetingGraveyardCard(1, "Stitch Together", 1, "Grizzly Bears")

    init {
        test("six own graveyard cards at resolution return the creature to hand and pay both black mana") {
            val game = board(ownGraveyardCards = 6)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val lands = game.findPermanents("Swamp")

            game.castStitch().error shouldBe null
            game.state.getGraveyard(game.player1Id).size shouldBe 6
            lands.count { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe 2
            game.resolveStack()

            (target in game.state.getHand(game.player1Id)) shouldBe true
            (target in game.state.getBattlefield(game.player1Id)) shouldBe false
            game.findCardsInGraveyard(1, "Stitch Together").size shouldBe 1
        }

        test("exactly seven own graveyard cards at resolution return the creature untapped to battlefield") {
            val game = board(ownGraveyardCards = 7)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()

            game.castStitch().error shouldBe null
            game.state.getGraveyard(game.player1Id).size shouldBe 7 // resolving spell is still on stack
            game.resolveStack()

            (target in game.state.getBattlefield(game.player1Id)) shouldBe true
            (target in game.state.getHand(game.player1Id)) shouldBe false
            game.state.getEntity(target)!!.has<TappedComponent>() shouldBe false
            game.findCardsInGraveyard(1, "Stitch Together").size shouldBe 1
        }

        test("opponent's graveyard cannot supply the seven-card threshold") {
            val game = board(ownGraveyardCards = 6, opponentGraveyardCards = 7)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()

            game.castStitch().error shouldBe null
            game.resolveStack()

            (target in game.state.getHand(game.player1Id)) shouldBe true
            (target in game.state.getBattlefield(game.player1Id)) shouldBe false
            game.state.getGraveyard(game.player2Id).size shouldBe 7
        }

        test("a responding instant raises the graveyard from six to seven before resolution") {
            val game = board(ownGraveyardCards = 6, extraHand1 = "Dark Ritual", ownSwamps = 3)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()

            game.castStitch().error shouldBe null
            game.state.getGraveyard(game.player1Id).size shouldBe 6
            game.castSpell(1, "Dark Ritual").error shouldBe null
            game.resolveStack()

            (target in game.state.getBattlefield(game.player1Id)) shouldBe true
            (target in game.state.getHand(game.player1Id)) shouldBe false
            game.findCardsInGraveyard(1, "Dark Ritual").size shouldBe 1
        }

        test("opponent's response lowers the graveyard from seven to six before resolution") {
            val game = board(ownGraveyardCards = 7, extraHand2 = "Coffin Purge", opponentSwamps = 1)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val filler = game.findCardsInGraveyard(1, "Swamp").first()

            game.castStitch().error shouldBe null
            game.passPriority().error shouldBe null // give the opponent the response window
            game.castSpellTargetingGraveyardCard(2, "Coffin Purge", 1, "Swamp").error shouldBe null
            game.resolveStack()

            (filler in game.state.getExile(game.player1Id)) shouldBe true
            (target in game.state.getHand(game.player1Id)) shouldBe true
            (target in game.state.getBattlefield(game.player1Id)) shouldBe false
        }

        test("an opponent's creature card is not a legal target") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Stitch Together")
                .withCardInGraveyard(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val theirs = game.findCardsInGraveyard(2, "Grizzly Bears").single()
            val spell = game.findCardsInHand(1, "Stitch Together").single()

            game.execute(CastSpell(game.player1Id, spell,
                targets = listOf(ChosenTarget.Card(theirs, game.player2Id, Zone.GRAVEYARD))
            )).error shouldNotBe null
            game.isInHand(1, "Stitch Together") shouldBe true
        }

        test("a noncreature card in your graveyard is not a legal target") {
            val game = board(ownGraveyardCards = 2)
            val swamp = game.findCardsInGraveyard(1, "Swamp").single()
            val spell = game.findCardsInHand(1, "Stitch Together").single()

            game.execute(CastSpell(game.player1Id, spell,
                targets = listOf(ChosenTarget.Card(swamp, game.player1Id, Zone.GRAVEYARD))
            )).error shouldNotBe null
            game.isInHand(1, "Stitch Together") shouldBe true
        }

        test("target exiled in response makes Stitch Together fail without moving a different card") {
            val game = board(ownGraveyardCards = 7, extraHand2 = "Coffin Purge", opponentSwamps = 1)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()

            game.castStitch().error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingGraveyardCard(2, "Coffin Purge", 1, "Grizzly Bears").error shouldBe null
            game.resolveStack()

            (target in game.state.getExile(game.player1Id)) shouldBe true
            (target in game.state.getHand(game.player1Id)) shouldBe false
            (target in game.state.getBattlefield(game.player1Id)) shouldBe false
            game.findCardsInGraveyard(1, "Stitch Together").size shouldBe 1
        }

        test("target leaving and returning to the graveyard is a new object and cannot be recovered") {
            val game = board(ownGraveyardCards = 7)
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()

            game.castStitch().error shouldBe null
            val original = game.state.objectRef(target)
            val exile = zones.moveToZone(game.state, target, Zone.EXILE)
            game.state = exile.state
            val returnToGraveyard = zones.moveToZone(game.state, target, Zone.GRAVEYARD)
            game.state = returnToGraveyard.state
            (game.state.objectRef(target) == original) shouldBe false
            game.resolveStack()

            (target in game.state.getGraveyard(game.player1Id)) shouldBe true
            (target in game.state.getBattlefield(game.player1Id)) shouldBe false
            (target in game.state.getHand(game.player1Id)) shouldBe false
        }
    }
}
