package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.UtterInsignificance
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class UtterInsignificanceScenarioTest : FunSpec({
    test("the Aura blanks and shrinks its creature, then exiles that creature") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + UtterInsignificance)
            initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val creature = driver.putCreatureOnBattlefield(opponent, "Air Elemental")
        val auraCard = driver.putCardInHand(player, "Utter Insignificance")
        driver.giveMana(player, Color.BLUE, 2)
        driver.castSpell(player, auraCard, listOf(creature)).error shouldBe null
        driver.bothPass()

        val projected = StateProjector().project(driver.state)
        projected.getPower(creature) shouldBe 1
        projected.getToughness(creature) shouldBe 1
        projected.hasKeyword(creature, Keyword.FLYING) shouldBe false

        val aura = driver.findPermanent(player, "Utter Insignificance")!!
        driver.giveColorlessMana(player, 3)
        driver.submit(
            ActivateAbility(
                playerId = player,
                sourceId = aura,
                abilityId = UtterInsignificance.activatedAbilities.single().id,
            )
        ).error shouldBe null
        driver.bothPass()

        driver.getExileCardNames(opponent) shouldContain "Air Elemental"
    }
})
