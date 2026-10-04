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
import com.wingedsheep.mtg.sets.definitions.ktk.cards.TreasureCruise
import com.wingedsheep.mtg.sets.definitions.ktk.cards.EmptyThePits
import com.wingedsheep.mtg.sets.definitions.mh3.cards.Nethergoyf
import com.wingedsheep.mtg.sets.definitions.mkm.cards.UrgentNecropsy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NativeCostObservationTest : FunSpec({
    test("Nethergoyf native view carries each exile candidate's card types") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(Nethergoyf))
        driver.initMirrorMatch(Deck.of("Swamp" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val goyf = driver.putCardInGraveyard(player, "Nethergoyf")
        val artifactCreature = driver.putCardInGraveyard(player, "Ornithopter")
        driver.putCardInGraveyard(player, "Lightning Bolt")
        driver.putCardInGraveyard(player, "Swamp")
        val offered = driver.legalActions(player)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, player, offered)
            .observation as TrainingObservation).legalActions.first { action ->
            val cast = offered[action.actionId].action as? CastSpell
            cast?.cardId == goyf && cast.alternativeCostType == AlternativeCostType.ESCAPE
        }
        val cost = view.costChoices!!
        (artifactCreature in cost.validExileTargets) shouldBe true
        cost.exileCardTypes[artifactCreature]?.size shouldBe 2
        cost.exileMinTotalWeight shouldBe 4
    }

    test("Urgent Necropsy native view carries evidence weight per possible target") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(UrgentNecropsy))
        driver.initMirrorMatch(Deck.of("Swamp" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val necropsy = driver.putCardInHand(player, "Urgent Necropsy")
        val creature = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        driver.putCardInGraveyard(player, "Air Elemental")
        driver.giveMana(player, Color.BLACK, 2)
        driver.giveMana(player, Color.GREEN, 2)
        val offered = driver.legalActions(player)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, player, offered)
            .observation as TrainingObservation).legalActions.first { action ->
            (offered[action.actionId].action as? CastSpell)?.cardId == necropsy
        }
        view.costChoices!!.exileWeightPerTarget[creature] shouldBe 2
    }

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

    test("Treasure Cruise native view offers delve selections and their minimum") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + TreasureCruise)
        driver.initMirrorMatch(Deck.of("Island" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val cruise = driver.putCardInHand(player, "Treasure Cruise")
        repeat(3) { driver.putLandOnBattlefield(player, "Island") }
        val graveyard = (1..7).map { driver.putCardInGraveyard(player, "Grizzly Bears") }
        val offered = driver.legalActions(player)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, player, offered)
            .observation as TrainingObservation).legalActions.first { action ->
            (offered[action.actionId].action as? CastSpell)?.cardId == cruise
        }

        ("delvedCards" in view.parameterSpec.allowedFields) shouldBe true
        view.validDelveCards.toSet() shouldBe graveyard.toSet()
        view.minDelveNeeded shouldBe 5
    }
    test("X delve view does not advertise an allocation choice it cannot express") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + EmptyThePits)
        driver.initMirrorMatch(Deck.of("Swamp" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val pits = driver.putCardInHand(player, "Empty the Pits")
        repeat(5) { driver.putLandOnBattlefield(player, "Swamp") }
        driver.putCardInGraveyard(player, "Grizzly Bears")
        val offered = driver.legalActions(player)
        val view = (ObservationBuilder(driver.cardRegistry).build(driver.state, player, offered)
            .observation as TrainingObservation).legalActions.first { action ->
            (offered[action.actionId].action as? CastSpell)?.cardId == pits
        }
        view.hasXCost shouldBe true
        ("delvedCards" in view.parameterSpec.allowedFields) shouldBe false
        view.validDelveCards shouldBe emptyList()
        view.minDelveNeeded shouldBe null
    }
})
