package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class AlibouAncientWitnessScenarioTest : ScenarioTestBase() {
    private fun board(ringTapped: Boolean = true) = scenario().withPlayers("Pilot", "Opponent")
        .withCardOnBattlefield(1, "Alibou, Ancient Witness", summoningSickness = false)
        .withCardOnBattlefield(1, "Memnite", summoningSickness = true)
        .withCardOnBattlefield(1, "Ornithopter", summoningSickness = true)
        .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
        .withCardOnBattlefield(1, "Sol Ring", tapped = ringTapped)
        .withCardOnBattlefield(1, "Forest", tapped = true)
        .withCardOnBattlefield(1, "Island")
        .withCardOnBattlefield(2, "Sol Ring", tapped = true)
        .withCardOnBattlefield(2, "Memnite", summoningSickness = true)
        .withCardInHand(1, "Unsummon")
        .withCardInLibrary(1, "Plains").withCardInLibrary(1, "Mountain")
        .withCardInLibrary(1, "Swamp").withCardInLibrary(1, "Forest")
        .withCardInLibrary(2, "Island")
        .withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

    private fun target(game: TestGame, entity: com.wingedsheep.sdk.model.EntityId = game.player2Id) {
        val decision = game.getPendingDecision() as ChooseTargetsDecision
        game.submitDecision(TargetsResponse(decision.id, mapOf(0 to listOf(entity)))).error shouldBe null
    }

    private fun scrySize(game: TestGame, expected: Int) {
        val decision = game.getPendingDecision() as SelectCardsDecision
        decision.options.size shouldBe expected
        game.skipSelection().error shouldBe null
        while (game.getPendingDecision() is ReorderLibraryDecision) game.keepLibraryOrder().error shouldBe null
    }

    init {
        test("other controlled artifact creatures gain haste and multiple attackers create one damage and scry trigger") {
            val game = board().build()
            val projected = game.state.projectedState
            projected.hasKeyword(game.findPermanent("Alibou, Ancient Witness")!!, Keyword.HASTE) shouldBe false
            projected.hasKeyword(game.findPermanent("Memnite")!!, Keyword.HASTE) shouldBe true
            projected.hasKeyword(game.findPermanent("Ornithopter")!!, Keyword.HASTE) shouldBe true
            projected.hasKeyword(game.findPermanent("Grizzly Bears")!!, Keyword.HASTE) shouldBe false
            val enemyMemnite = game.findAllPermanents("Memnite").single { projected.getController(it) == game.player2Id }
            projected.hasKeyword(enemyMemnite, Keyword.HASTE) shouldBe false
            game.declareAttackers(mapOf("Memnite" to 2, "Ornithopter" to 2)).error shouldBe null
            target(game)
            game.state.stack.size shouldBe 1
            game.resolveStack()
            // Two tapped attackers + our tapped noncreature artifact; neither a land nor an enemy artifact counts.
            game.getLifeTotal(2) shouldBe 17
            scrySize(game, 3)
            game.state.stack.size shouldBe 0
        }

        test("a nonartifact attacker does not trigger Alibou") {
            val game = board().build()
            game.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
            game.getPendingDecision() shouldBe null
            game.state.stack.size shouldBe 0
        }

        test("Alibou itself can be the qualifying artifact attacker") {
            val game = board().build()
            game.declareAttackers(mapOf("Alibou, Ancient Witness" to 2)).error shouldBe null
            target(game)
            game.resolveStack()
            game.getLifeTotal(2) shouldBe 18
            scrySize(game, 2)
        }

        test("X includes an artifact tapped in response and is determined at resolution") {
            val game = board(ringTapped = false).build()
            game.declareAttackers(mapOf("Ornithopter" to 2)).error shouldBe null
            target(game)
            val ring = game.findAllPermanents("Sol Ring").single { game.state.projectedState.getController(it) == game.player1Id }
            val ability = cardRegistry.getCard("Sol Ring")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player1Id, ring, ability)).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(2) shouldBe 18
            scrySize(game, 2)
        }

        test("no tapped artifacts at resolution means no damage and no scry decision") {
            val game = board(ringTapped = false).build()
            game.declareAttackers(mapOf("Ornithopter" to 2)).error shouldBe null
            target(game)
            game.castSpell(1, "Unsummon", game.findPermanent("Ornithopter")!!).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.getLifeTotal(2) shouldBe 20
            game.getPendingDecision() shouldBe null
            game.isInHand(1, "Ornithopter") shouldBe true
        }

        test("an illegal sole damage target prevents the entire trigger including scry") {
            val game = board().build()
            game.declareAttackers(mapOf("Ornithopter" to 2)).error shouldBe null
            val bears = game.findPermanent("Grizzly Bears")!!
            target(game, bears)
            game.castSpell(1, "Unsummon", bears).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.getPendingDecision() shouldBe null
            game.getLifeTotal(2) shouldBe 20
            game.isInHand(1, "Grizzly Bears") shouldBe true
        }

        test("the attack trigger survives source departure while its continuous haste grant disappears") {
            val game = board().build()
            game.declareAttackers(mapOf("Ornithopter" to 2)).error shouldBe null
            target(game)
            game.castSpell(1, "Unsummon", game.findPermanent("Alibou, Ancient Witness")!!).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.isInHand(1, "Alibou, Ancient Witness") shouldBe true
            game.state.projectedState.hasKeyword(game.findPermanent("Ornithopter")!!, Keyword.HASTE) shouldBe false
            game.getLifeTotal(2) shouldBe 18
            scrySize(game, 2)
        }

        test("lethal damage to an artifact creature waits for the scry decisions to finish") {
            val game = board().build()
            game.declareAttackers(mapOf("Ornithopter" to 2)).error shouldBe null
            target(game, game.findPermanent("Ornithopter")!!)
            game.resolveStack()
            game.isOnBattlefield("Ornithopter") shouldBe true
            scrySize(game, 2)
            game.isOnBattlefield("Ornithopter") shouldBe false
            game.isInGraveyard(1, "Ornithopter") shouldBe true
        }
    }
}
