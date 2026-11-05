package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.Outcome
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ecl.cards.UnexpectedAssistance
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * A convoke spell is paid with mana first and taps creatures only for the shortfall, cheapest
 * creatures first ([AiProfile.convokeLandsFirst]).
 *
 * Production game `ecbad62a` (2026-10-15) turn 14: Unexpected Assistance convoked with all three of
 * the AI's would-be blockers while its lands sat untapped, and 11 damage came in unblocked the next
 * turn. Unexpected Assistance ({3}{U}{U}, convoke) is the same card here.
 */
class ConvokeLandsFirstTest : FunSpec({
    val withFlag = AiProfile.CURRENT.copy(id = "convoke-lands-first", convokeLandsFirst = true)

    fun driver(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(UnexpectedAssistance))
        initMirrorMatch(Deck.of("Island" to 20))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    fun GameTestDriver.castChosenBy(profile: AiProfile, spell: EntityId): CastSpell {
        val player = activePlayer!!
        val legal = LegalActionEnumerator.create(cardRegistry).enumerate(state, player)
            .single { (it.action as? CastSpell)?.cardId == spell }
        return AIPlayer.create(cardRegistry, player, profile).chooseFrom(state, listOf(legal)).action as CastSpell
    }

    test("enough lands untapped: the lands pay and every creature stays untapped") {
        val driver = driver()
        val player = driver.activePlayer!!
        repeat(5) { driver.putLandOnBattlefield(player, "Island") }
        repeat(3) { driver.putCreatureOnBattlefield(player, "Grizzly Bears") }
        val spell = driver.putCardInHand(player, "Unexpected Assistance")

        val chosen = driver.castChosenBy(withFlag, spell)
        withClue("convoked ${chosen.alternativePayment?.convokedCreatures}") {
            chosen.alternativePayment?.convokedCreatures.orEmpty().size shouldBe 0
        }
        driver.submit(chosen).outcome shouldBe Outcome.Done
    }

    test("without the flag the same board taps all three creatures — the production misplay") {
        val driver = driver()
        val player = driver.activePlayer!!
        repeat(5) { driver.putLandOnBattlefield(player, "Island") }
        repeat(3) { driver.putCreatureOnBattlefield(player, "Grizzly Bears") }
        val spell = driver.putCardInHand(player, "Unexpected Assistance")

        driver.castChosenBy(AiProfile.CURRENT, spell).alternativePayment?.convokedCreatures.orEmpty().size shouldBe 3
    }

    test("lands short by two: convokes exactly two, the cheapest bodies, and keeps the big one") {
        val driver = driver()
        val player = driver.activePlayer!!
        repeat(3) { driver.putLandOnBattlefield(player, "Island") }
        val force = driver.putCreatureOnBattlefield(player, "Force of Nature")
        val bears = List(2) { driver.putCreatureOnBattlefield(player, "Grizzly Bears") }
        val spell = driver.putCardInHand(player, "Unexpected Assistance")

        val chosen = driver.castChosenBy(withFlag, spell)
        val convoked = chosen.alternativePayment?.convokedCreatures.orEmpty().keys
        withClue("convoked $convoked; Force of Nature is $force") {
            convoked shouldBe bears.toSet()
        }
        driver.submit(chosen).outcome shouldBe Outcome.Done
    }
})
