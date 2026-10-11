package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.SpellCopiedEvent
import com.wingedsheep.engine.core.SpellFizzledEvent
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.stack.ResolutionTargetValidator
import com.wingedsheep.engine.mechanics.stack.StackPlacement
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** CR 400.7 / 608.2b: a graveyard card that leaves and returns is a new target object. */
class GraveyardTargetObjectIdentityTest : ScenarioTestBase() {
    private fun TestGame.exileAndReturn(cardId: com.wingedsheep.sdk.model.EntityId) {
        val oldObject = state.objectRef(cardId)
        state = zones.moveToZone(state, cardId, Zone.EXILE).state
        state = zones.moveToZone(state, cardId, Zone.GRAVEYARD).state
        (state.objectRef(cardId) == oldObject) shouldBe false
    }

    init {
        for (roundTrip in listOf(false, true)) {
            test("Apprentice Necromancer target ${if (roundTrip) "leaves and returns" else "stays"} before ability resolves") {
                val game = scenario().withPlayers()
                    .withCardOnBattlefield(1, "Apprentice Necromancer", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                val source = game.findPermanent("Apprentice Necromancer")!!
                val abilityId = cardRegistry.getCard("Apprentice Necromancer")!!.activatedAbilities.single().id
                game.execute(ActivateAbility(game.player1Id, source, abilityId,
                    targets = listOf(ChosenTarget.Card(target, game.player1Id, Zone.GRAVEYARD))
                )).error shouldBe null
                if (game.getPendingDecision() is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().error shouldBe null
                }
                game.isInGraveyard(1, "Apprentice Necromancer") shouldBe true
                if (roundTrip) game.exileAndReturn(target)

                game.resolveStack()

                (target in game.state.getBattlefield(game.player1Id)) shouldBe !roundTrip
                (target in game.state.getGraveyard(game.player1Id)) shouldBe roundTrip
            }
        }

        test("Gravedigger's triggered ability loses a creature card that left and returned") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Gravedigger")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInGraveyard(1, "Grizzly Bears")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val target = game.findCardsInGraveyard(1, "Grizzly Bears").single()

            game.castSpell(1, "Gravedigger").error shouldBe null
            if (game.getPendingDecision() is SelectManaSourcesDecision) {
                game.submitManaSourcesAutoPay().error shouldBe null
            }
            game.resolveStack()
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
            game.selectTargets(listOf(target)).error shouldBe null
            game.exileAndReturn(target)
            game.resolveStack()

            (target in game.state.getGraveyard(game.player1Id)) shouldBe true
            (target in game.state.getHand(game.player1Id)) shouldBe false
            game.isOnBattlefield("Gravedigger") shouldBe true
        }

        for (chooseNewTarget in listOf(false, true)) {
            test("Raise Dead copy ${if (chooseNewTarget) "retargets the returned object" else "inherits the old object's target"}") {
                val game = scenario().withPlayers()
                    .withCardInHand(1, "Raise Dead")
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val cardId = game.findCardsInGraveyard(1, "Grizzly Bears").single()
                game.castSpellTargetingGraveyardCard(1, "Raise Dead", 1, "Grizzly Bears").error shouldBe null
                if (game.getPendingDecision() is SelectManaSourcesDecision) {
                    game.submitManaSourcesAutoPay().error shouldBe null
                }
                val originalSpell = game.state.stack.single()
                val originalTargetRef = game.state.getEntity(originalSpell)!!
                    .get<TargetsComponent>()!!.targetObjectRefs[cardId]
                game.exileAndReturn(cardId)

                val selectedTargets = if (chooseNewTarget)
                    listOf(ChosenTarget.Card(cardId, game.player1Id, Zone.GRAVEYARD)) else emptyList()
                val copy = StackPlacement.putSpellCopy(game.state, originalSpell, targets = selectedTargets)
                copy.error shouldBe null
                val copyId = copy.events.filterIsInstance<SpellCopiedEvent>().single().copyEntityId
                game.state = copy.state
                val copyTargetRef = game.state.getEntity(copyId)!!.get<TargetsComponent>()!!
                    .targetObjectRefs[cardId]
                copyTargetRef shouldBe if (chooseNewTarget) game.state.objectRef(cardId) else originalTargetRef

                val events = game.resolveStack().flatMap { it.events }
                val fizzles = events.filterIsInstance<SpellFizzledEvent>().map { it.spellEntityId }
                if (chooseNewTarget) {
                    (cardId in game.state.getHand(game.player1Id)) shouldBe true
                    fizzles shouldBe listOf(originalSpell)
                } else {
                    (cardId in game.state.getGraveyard(game.player1Id)) shouldBe true
                    fizzles.toSet() shouldBe setOf(copyId, originalSpell)
                }
            }
        }

        test("captured card targets distinguish stale and current objects in the same graveyard") {
            val game = scenario().withPlayers()
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(1, "Hill Giant")
                .build()
            val staleId = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val currentId = game.findCardsInGraveyard(1, "Hill Giant").single()
            val stale = ChosenTarget.Card(staleId, game.player1Id, Zone.GRAVEYARD)
            val current = ChosenTarget.Card(currentId, game.player1Id, Zone.GRAVEYARD)
            val chosen = listOf(stale, current)
            val original = TargetsComponent.capture(game.state, chosen)
            original.targetObjectRefs.keys shouldBe setOf(staleId, currentId)
            game.exileAndReturn(staleId)
            val validator = ResolutionTargetValidator(PredicateEvaluator(cardRegistry))

            validator.validateTargets(game.state, chosen, controllerId = game.player1Id,
                targetObjectRefs = original.targetObjectRefs) shouldBe listOf(current)
            // A copied stack object carries the original target identity, too.
            val copiedTargets = original.copy()
            validator.validateTargets(game.state, copiedTargets.targets, controllerId = game.player1Id,
                targetObjectRefs = copiedTargets.targetObjectRefs) shouldBe listOf(current)
            // A genuinely new target choice after the zone change captures the current object.
            val retargeted = TargetsComponent.capture(game.state, listOf(stale))
            validator.validateTargets(game.state, retargeted.targets, controllerId = game.player1Id,
                targetObjectRefs = retargeted.targetObjectRefs) shouldBe listOf(stale)
        }

        test("legacy targets without captured card refs retain zone-based validation") {
            val game = scenario().withPlayers().withCardInGraveyard(1, "Grizzly Bears").build()
            val cardId = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val target = ChosenTarget.Card(cardId, game.player1Id, Zone.GRAVEYARD)
            game.exileAndReturn(cardId)
            val validator = ResolutionTargetValidator(PredicateEvaluator(cardRegistry))

            validator.validateTargets(game.state, listOf(target), controllerId = game.player1Id,
                targetObjectRefs = emptyMap()) shouldBe listOf(target)
        }
    }
}
