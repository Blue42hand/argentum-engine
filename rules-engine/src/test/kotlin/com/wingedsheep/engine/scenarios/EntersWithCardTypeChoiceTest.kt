package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** Pins the generic CARD_TYPE as-enters contract independently of Cloud Key. */
class EntersWithCardTypeChoiceTest : FunSpec({
    val restrictedTypes = listOf(CardType.ARTIFACT, CardType.CREATURE, CardType.SORCERY)

    val CastTypeChooser = card("Cast Type Chooser") {
        manaCost = "{0}"
        typeLine = "Artifact"
        oracleText = "As this artifact enters, choose artifact, creature, or sorcery."
        replacementEffect(EntersWithChoice(choiceType = ChoiceType.CARD_TYPE, allowedCardTypes = restrictedTypes))
    }

    val LandTypeChooser = card("Land Type Chooser") {
        typeLine = "Land"
        oracleText = "As this land enters, choose artifact, creature, or sorcery."
        replacementEffect(EntersWithChoice(choiceType = ChoiceType.CARD_TYPE, allowedCardTypes = restrictedTypes))
    }

    val UnrestrictedLandTypeChooser = card("Unrestricted Land Type Chooser") {
        typeLine = "Land"
        oracleText = "As this land enters, choose a card type."
        replacementEffect(EntersWithChoice(choiceType = ChoiceType.CARD_TYPE))
    }

    fun newDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(CastTypeChooser, LandTypeChooser, UnrestrictedLandTypeChooser))
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    fun chosenType(driver: GameTestDriver, entityId: EntityId): String? =
        (driver.state.getEntity(entityId)
            ?.get<CastChoicesComponent>()
            ?.chosen
            ?.get(ChoiceSlot.CARD_TYPE) as? ChoiceValue.TextChoice)?.text

    test("cast permanent offers the restricted card types and persists the chosen type") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val card = driver.putCardInHand(player, "Cast Type Chooser")

        driver.castSpell(player, card).isSuccess shouldBe true
        driver.bothPass()

        val decision = driver.pendingDecision as ChooseOptionDecision
        decision.prompt shouldBe "Choose a card type"
        decision.options shouldContainExactly listOf("Artifact", "Creature", "Sorcery")
        driver.submitDecision(player, OptionChosenResponse(decision.id, decision.options.indexOf("Creature")))

        val permanent = driver.findPermanent(player, "Cast Type Chooser")!!
        chosenType(driver, permanent) shouldBe "Creature"
    }

    test("direct land entry uses the same restricted menu and persists the chosen type") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val land = driver.putCardInHand(player, "Land Type Chooser")

        driver.playLand(player, land)

        val decision = driver.pendingDecision as ChooseOptionDecision
        decision.prompt shouldBe "Choose a card type"
        decision.options shouldContainExactly listOf("Artifact", "Creature", "Sorcery")
        driver.submitDecision(player, OptionChosenResponse(decision.id, decision.options.indexOf("Sorcery")))

        chosenType(driver, land) shouldBe "Sorcery"
    }

    test("unrestricted entry reuses the canonical default card-type universe") {
        val driver = newDriver()
        val player = driver.activePlayer!!
        val land = driver.putCardInHand(player, "Unrestricted Land Type Chooser")

        driver.playLand(player, land)

        val decision = driver.pendingDecision as ChooseOptionDecision
        decision.prompt shouldBe "Choose a card type"
        decision.options shouldContainExactly CardType.DEFAULT_CHOOSABLE_TYPES.map { it.displayName }
        driver.submitDecision(player, OptionChosenResponse(decision.id, decision.options.indexOf("Instant")))

        chosenType(driver, land) shouldBe "Instant"
    }
})
