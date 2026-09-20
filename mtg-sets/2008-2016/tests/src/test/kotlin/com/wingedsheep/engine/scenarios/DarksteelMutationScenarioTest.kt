package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c13.cards.DarksteelMutation
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DarksteelMutationScenarioTest : FunSpec({

    test("turns the enchanted creature into a 0/1 indestructible Insect artifact creature") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + DarksteelMutation)
            initMirrorMatch(deck = Deck.of("Plains" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val creature = driver.putCreatureOnBattlefield(player, "Serra Angel")
        val aura = driver.putCardInHand(player, "Darksteel Mutation")

        driver.giveMana(player, Color.WHITE, 1)
        driver.giveColorlessMana(player, 1)
        driver.castSpell(player, aura, listOf(creature)).error shouldBe null
        driver.bothPass()

        val projected = driver.state.projectedState
        projected.hasType(creature, "ARTIFACT") shouldBe true
        projected.isCreature(creature) shouldBe true
        projected.hasSubtype(creature, "Insect") shouldBe true
        projected.hasSubtype(creature, "Angel") shouldBe false
        projected.getPower(creature) shouldBe 0
        projected.getToughness(creature) shouldBe 1
        projected.hasKeyword(creature, Keyword.INDESTRUCTIBLE) shouldBe true
        projected.hasKeyword(creature, Keyword.FLYING) shouldBe false
        projected.hasLostAllAbilities(creature) shouldBe true
    }
})
