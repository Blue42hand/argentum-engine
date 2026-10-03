package com.wingedsheep.gym

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.mtg.sets.definitions.lrw.cards.SpringleafDrum
import com.wingedsheep.mtg.sets.definitions.mh1.cards.ForceOfNegation
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NativeCostObservationTest : FunSpec({
    test("Drum native view identifies tap cost field and eligible creature") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(SpringleafDrum))
        driver.initMirrorMatch(Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val drum = driver.putPermanentOnBattlefield(player, "Springleaf Drum")
        val creature = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        val offered = driver.legalActions(player)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, player, offered)
            .observation as TrainingObservation).legalActions.first { action ->
            val legal = offered[action.actionId].action as? ActivateAbility
            legal?.sourceId == drum && offered[action.actionId].isManaAbility
        }
        ("tappedPermanents" in view.parameterSpec.allowedFields) shouldBe true
        ("discardedCards" in view.parameterSpec.allowedFields) shouldBe false
        view.costChoices?.validTapTargets shouldBe listOf(creature)
        view.costChoices?.tapCount shouldBe 1
    }

    test("Force native view identifies exile cost field and eligible blue card") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(ForceOfNegation))
        driver.initMirrorMatch(Deck.of("Island" to 40), startingPlayer = 1)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val opponent = driver.activePlayer!!
        val player = driver.getOpponent(opponent)
        val bolt = driver.putCardInHand(opponent, "Lightning Bolt")
        driver.giveMana(opponent, Color.RED)
        driver.castSpell(opponent, bolt, targets = listOf(player)).error shouldBe null
        driver.passPriority(opponent)
        val blue = driver.putCardInHand(player, "Counterspell")
        val nonblue = driver.putCardInHand(player, "Grizzly Bears")
        val force = driver.putCardInHand(player, "Force of Negation")
        val offered = driver.legalActions(player)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, player, offered)
            .observation as TrainingObservation).legalActions.first { action ->
            val legal = offered[action.actionId].action as? CastSpell
            legal?.cardId == force && legal.useAlternativeCost &&
                legal.alternativeCostType == AlternativeCostType.SELF_ALTERNATIVE
        }
        ("exiledCards" in view.parameterSpec.allowedFields) shouldBe true
        val cost = view.costChoices!!
        (blue in cost.validExileTargets) shouldBe true
        (nonblue in cost.validExileTargets) shouldBe false
        cost.exileMinCount shouldBe 1
    }
})
