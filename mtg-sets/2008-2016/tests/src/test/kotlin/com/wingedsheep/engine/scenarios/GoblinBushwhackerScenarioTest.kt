package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.zen.cards.GoblinBushwhacker
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GoblinBushwhackerScenarioTest : FunSpec({
    test("kicked entry pumps the existing team and grants haste") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + GoblinBushwhacker)
        driver.initMirrorMatch(Deck.of("Mountain" to 40), skipMulligans = true, startingPlayer = 0)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = driver.activePlayer!!
        val bears = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        repeat(2) { driver.putLandOnBattlefield(player, "Mountain") }
        val bushwhacker = driver.putCardInHand(player, "Goblin Bushwhacker")

        driver.submit(
            CastSpell(
                playerId = player,
                cardId = bushwhacker,
                declaredCostSlot = ChoiceSlot.KICKED,
                paymentStrategy = PaymentStrategy.AutoPay,
            ),
        ).isSuccess shouldBe true
        repeat(4) { driver.bothPass() }

        driver.state.projectedState.getPower(bears) shouldBe 3
        driver.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe true
    }
})
