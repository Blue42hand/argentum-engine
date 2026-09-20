package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.afr.cards.BattleCryGoblin
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BattleCryGoblinScenarioTest : FunSpec({
    test("the activated ability pumps Goblins and gives them haste") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + BattleCryGoblin)
            initMirrorMatch(deck = Deck.of("Mountain" to 40), skipMulligans = true)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val battleCry = driver.putCreatureOnBattlefield(player, "Battle Cry Goblin")
        val otherGoblin = driver.putCreatureOnBattlefield(player, "Goblin Guide")
        val bear = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        driver.giveMana(player, Color.RED, 2)
        driver.submit(
            ActivateAbility(
                playerId = player,
                sourceId = battleCry,
                abilityId = BattleCryGoblin.activatedAbilities.single().id,
            )
        ).error shouldBe null
        driver.bothPass()

        val projected = StateProjector().project(driver.state)
        projected.getPower(otherGoblin) shouldBe 3
        projected.hasKeyword(otherGoblin, Keyword.HASTE) shouldBe true
        projected.getPower(bear) shouldBe 2
    }

    test("pack tactics creates one tapped and attacking Goblin") {
        val driver = GameTestDriver().apply {
            registerCards(TestCards.all + BattleCryGoblin)
            initMirrorMatch(deck = Deck.of("Mountain" to 40), skipMulligans = true)
        }
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val battleCry = driver.putCreatureOnBattlefield(player, "Battle Cry Goblin")
        val giant = driver.putCreatureOnBattlefield(player, "Hill Giant")
        val bear = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        listOf(battleCry, giant, bear).forEach(driver::removeSummoningSickness)
        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)

        driver.declareAttackers(player, listOf(battleCry, giant, bear), opponent).error shouldBe null
        driver.bothPass()

        driver.getPermanents(player).count { driver.getCardName(it) == "Goblin Token" } shouldBe 1
    }
})
