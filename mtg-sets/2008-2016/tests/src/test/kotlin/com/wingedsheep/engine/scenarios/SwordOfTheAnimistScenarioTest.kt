package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Sword of the Animist gives +1/+1 and lets its controller search when the equipped creature attacks. */
class SwordOfTheAnimistScenarioTest : ScenarioTestBase() {
    private val swordName = "Sword of the Animist"

    private fun TestGame.attack(attacker: Int) {
        declareAttackers(mapOf("Grizzly Bears" to if (attacker == 1) 2 else 1)).error shouldBe null
        resolveStack()
    }

    init {
        test("equip costs two mana and grants the equipped creature +1/+1") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, swordName)
                .withLandsOnBattlefield(1, "Plains", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val sword = game.findPermanent(swordName)!!
            val bears = game.findPermanent("Grizzly Bears")!!
            val plains = game.findPermanents("Plains")
            val equip = cardRegistry.requireCard(swordName).activatedAbilities.first { it.isEquipAbility }.id

            game.state.projectedState.getPower(bears) shouldBe 2
            game.execute(ActivateAbility(
                playerId = game.player1Id, sourceId = sword, abilityId = equip,
                targets = listOf(ChosenTarget.Permanent(bears))
            )).error shouldBe null
            if (game.hasPendingDecision()) game.submitManaSourcesAutoPay().error shouldBe null
            game.resolveStack()

            game.state.getEntity(sword)!!.get<AttachedToComponent>()?.targetId shouldBe bears
            plains.count { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe 2
            game.state.projectedState.getPower(bears) shouldBe 3
            game.state.projectedState.getToughness(bears) shouldBe 3
        }

        test("attack searches only basic lands and puts the selected land onto the battlefield tapped") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, swordName, "Grizzly Bears")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Temple Garden")
                .withCardInLibrary(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val forest = game.findCardsInLibrary(1, "Forest").single()
            val island = game.findCardsInLibrary(1, "Island").single()
            val nonbasicLand = game.findCardsInLibrary(1, "Temple Garden").single()
            val nonbasic = game.findCardsInLibrary(1, "Grizzly Bears").single()

            game.attack(1)
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
            game.answerYesNo(true).error shouldBe null
            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            search.playerId shouldBe game.player1Id
            search.options.toSet() shouldBe setOf(forest, island)
            (nonbasicLand in search.options) shouldBe false
            (nonbasic in search.options) shouldBe false
            search.maxSelections shouldBe 1
            val selected = game.selectCards(listOf(forest))
            selected.error shouldBe null
            val settled = game.resolveStack()
            (selected.events + settled.flatMap { it.events })
                .filterIsInstance<LibraryShuffledEvent>().count { it.playerId == game.player1Id } shouldBe 1

            game.findPermanent("Forest") shouldBe forest
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
            game.findCardsInLibrary(1, "Forest").size shouldBe 0
            game.findCardsInLibrary(1, "Island").size shouldBe 1
        }

        test("when an opponent's creature carries Sword the Equipment controller searches their own library") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withCardAttachedTo(1, swordName, "Grizzly Bears")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(2, "Island")
                .withActivePlayer(2)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val forest = game.findCardsInLibrary(1, "Forest").single()
            val island = game.findCardsInLibrary(2, "Island").single()

            game.attack(2)
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
            game.answerYesNo(true).error shouldBe null
            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            search.playerId shouldBe game.player1Id
            search.options shouldBe listOf(forest)
            (island in search.options) shouldBe false
            game.selectCards(listOf(forest)).error shouldBe null
            game.resolveStack()

            (forest in game.state.getBattlefield(game.player1Id)) shouldBe true
            (forest in game.state.getBattlefield(game.player2Id)) shouldBe false
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
        }

        test("Sword's controller can decline the attack search") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, swordName, "Grizzly Bears")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val forest = game.findCardsInLibrary(1, "Forest").single()

            game.attack(1)
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            val declined = game.answerYesNo(false)
            declined.error shouldBe null
            val settled = game.resolveStack()
            (declined.events + settled.flatMap { it.events })
                .filterIsInstance<LibraryShuffledEvent>().count { it.playerId == game.player1Id } shouldBe 0
            game.hasPendingDecision() shouldBe false
            (forest in game.state.getLibrary(game.player1Id)) shouldBe true
        }

        test("accepted search can fail to find a basic land") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, swordName, "Grizzly Bears")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val forest = game.findCardsInLibrary(1, "Forest").single()

            game.attack(1)
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>().options shouldBe listOf(forest)
            val skipped = game.skipSelection()
            skipped.error shouldBe null
            val settled = game.resolveStack()
            (skipped.events + settled.flatMap { it.events })
                .filterIsInstance<LibraryShuffledEvent>().count { it.playerId == game.player1Id } shouldBe 1
            game.hasPendingDecision() shouldBe false
            (forest in game.state.getLibrary(game.player1Id)) shouldBe true
            (forest in game.state.getBattlefield(game.player1Id)) shouldBe false
        }

        test("destroying Sword before attack removes its search trigger and stat bonus") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, swordName, "Grizzly Bears")
                .withCardInHand(1, "Disenchant")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            val sword = game.findPermanent(swordName)!!
            val forest = game.findCardsInLibrary(1, "Forest").single()

            game.castSpell(1, "Disenchant", sword).error shouldBe null
            game.resolveStack()
            game.findPermanent(swordName) shouldBe null
            game.state.projectedState.getPower(bears) shouldBe 2
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
            game.attack(1)
            game.hasPendingDecision() shouldBe false
            (forest in game.state.getLibrary(game.player1Id)) shouldBe true
        }

        test("destroying Sword after its attack trigger is queued does not stop the search") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardAttachedTo(1, swordName, "Grizzly Bears")
                .withCardInHand(1, "Disenchant")
                .withLandsOnBattlefield(1, "Plains", 2)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            val sword = game.findPermanent(swordName)!!
            val forest = game.findCardsInLibrary(1, "Forest").single()

            game.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
            game.state.stack.size shouldBe 1
            game.castSpell(1, "Disenchant", sword).error shouldBe null
            game.resolveStack()
            game.findPermanent(swordName) shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>().options shouldBe listOf(forest)
            game.selectCards(listOf(forest)).error shouldBe null
            game.resolveStack()

            game.findPermanent("Forest") shouldBe forest
            game.state.getEntity(forest)!!.has<TappedComponent>() shouldBe true
        }
    }
}
