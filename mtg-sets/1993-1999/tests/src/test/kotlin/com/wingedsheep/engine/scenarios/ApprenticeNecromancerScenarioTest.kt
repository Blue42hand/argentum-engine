package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ApprenticeNecromancerScenarioTest : ScenarioTestBase() {
    private val abilityId get() = cardRegistry.getCard("Apprentice Necromancer")!!.activatedAbilities.single().id

    init {
        for (active in listOf(1, 2)) {
            test("activation sacrifices its source immediately and returns a hasty creature until player $active end step") {
                val game = scenario().withPlayers("Pilot", "Opponent")
                    .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
                    .withActivePlayer(active).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                if (active == 2) game.passPriority()
                val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                game.execute(ActivateAbility(game.player1Id, game.findPermanent("Apprentice Necromancer")!!,
                    abilityId, targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD)))).error shouldBe null
                if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
                game.isInGraveyard(1, "Apprentice Necromancer") shouldBe true
                game.isOnBattlefield("Grizzly Bears") shouldBe false
                game.resolveStack()
                val returned = game.findPermanent("Grizzly Bears")!!
                game.state.projectedState.hasKeyword(returned, Keyword.HASTE) shouldBe true
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()
                game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                game.isOnBattlefield("Grizzly Bears") shouldBe false
            }
        }

        test("activation after end step begins survives cleanup and sacrifices at the next player's end step") {
            val game = scenario().withPlayers("Pilot", "Opponent")
                .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                .withLandsOnBattlefield(1, "Swamp", 1).withCardInGraveyard(1, "Grizzly Bears")
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.ENDING, Step.END).build()
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Apprentice Necromancer")!!,
                abilityId, targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD)))).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            game.state.activePlayerId shouldBe game.player2Id
            game.state.projectedState.hasKeyword(game.findPermanent("Grizzly Bears")!!, Keyword.HASTE) shouldBe true
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack()
            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
        }

        for (invalid in listOf("self", "opponent creature", "own land")) {
            test("rejects $invalid before paying the sacrifice cost") {
                val game = scenario().withPlayers("Pilot", "Opponent")
                    .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInGraveyard(1, "Forest").withCardInGraveyard(2, "Grizzly Bears")
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val source = game.findPermanent("Apprentice Necromancer")!!
                val target = when (invalid) {
                    "self" -> ChosenTarget.Card(source, game.player1Id, Zone.GRAVEYARD)
                    "opponent creature" -> ChosenTarget.Card(game.findCardsInGraveyard(2, "Grizzly Bears").single(), game.player2Id, Zone.GRAVEYARD)
                    else -> ChosenTarget.Card(game.findCardsInGraveyard(1, "Forest").single(), game.player1Id, Zone.GRAVEYARD)
                }
                game.execute(ActivateAbility(game.player1Id, source, abilityId, targets = listOf(target))).error shouldNotBe null
                game.isOnBattlefield("Apprentice Necromancer") shouldBe true
                game.isInGraveyard(1, "Apprentice Necromancer") shouldBe false
            }
        }

        test("the tap cost prevents activating a summoning-sick necromancer") {
            val game = scenario().withPlayers("Pilot", "Opponent")
                .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = true)
                .withLandsOnBattlefield(1, "Swamp", 1).withCardInGraveyard(1, "Grizzly Bears")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Apprentice Necromancer")!!,
                abilityId, targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD)))).error shouldNotBe null
            game.isOnBattlefield("Apprentice Necromancer") shouldBe true
        }

        test("a graveyard target exiled in response does not return or create a delayed sacrifice") {
            val game = scenario().withPlayers("Pilot", "Opponent")
                .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                .withCardOnBattlefield(2, "Tormod's Crypt")
                .withLandsOnBattlefield(1, "Swamp", 1).withCardInGraveyard(1, "Grizzly Bears")
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Apprentice Necromancer")!!,
                abilityId, targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD)))).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.isInGraveyard(1, "Apprentice Necromancer") shouldBe true
            game.passPriority()
            val cryptId = cardRegistry.getCard("Tormod's Crypt")!!.activatedAbilities.single().id
            game.execute(ActivateAbility(game.player2Id, game.findPermanent("Tormod's Crypt")!!,
                cryptId, targets = listOf(ChosenTarget.Player(game.player1Id)))).error shouldBe null
            game.resolveStack()
            game.isInExile(1, "Grizzly Bears") shouldBe true
            game.isInExile(1, "Apprentice Necromancer") shouldBe true
            game.isOnBattlefield("Grizzly Bears") shouldBe false
            game.state.delayedTriggers.size shouldBe 0
        }

        test("the delayed sacrifice cannot sacrifice a returned creature now controlled by an opponent") {
            val game = scenario().withPlayers("Pilot", "Opponent")
                .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                .withLandsOnBattlefield(1, "Swamp", 1).withCardInGraveyard(1, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Island", 4).withCardInHand(2, "Control Magic")
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.passPriority()
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Apprentice Necromancer")!!,
                abilityId, targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD)))).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            val returned = game.findPermanent("Grizzly Bears")!!
            if (game.state.priorityPlayerId != game.player2Id) game.passPriority()
            game.castSpell(2, "Control Magic", returned).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.state.projectedState.getController(returned) shouldBe game.player2Id
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
        }

        test("a bounced and recast creature is a new object and escapes the delayed sacrifice") {
            val game = scenario().withPlayers("Pilot", "Opponent")
                .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                .withLandsOnBattlefield(1, "Swamp", 1).withLandsOnBattlefield(1, "Island", 1)
                .withLandsOnBattlefield(1, "Forest", 2).withCardInHand(1, "Unsummon")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            game.execute(ActivateAbility(game.player1Id, game.findPermanent("Apprentice Necromancer")!!,
                abilityId, targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD)))).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.castSpell(1, "Unsummon", game.findPermanent("Grizzly Bears")!!).error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.castSpell(1, "Grizzly Bears").error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) game.submitManaSourcesAutoPay()
            game.resolveStack()
            game.state.projectedState.hasKeyword(game.findPermanent("Grizzly Bears")!!, Keyword.HASTE) shouldBe false
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.resolveStack()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
        }
    }
}
