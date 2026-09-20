package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class MoggAlarmScenarioTest : ScenarioTestBase() {

    init {
        test("sacrifices two Mountains instead of paying mana and creates two Goblins") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Mogg Alarm")
                .withLandsOnBattlefield(1, "Mountain", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val alarm = game.findCardsInHand(1, "Mogg Alarm").single()
            val mountains = game.findPermanents("Mountain")

            game.execute(
                CastSpell(
                    playerId = game.player1Id,
                    cardId = alarm,
                    useAlternativeCost = true,
                    additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = mountains),
                )
            ).error shouldBe null
            game.resolveStack()

            withClue("both Mountains paid the alternative cost") {
                mountains.all { it in game.state.getGraveyard(game.player1Id) } shouldBe true
            }
            withClue("the spell made exactly two 1/1 red Goblins") {
                game.findAllPermanents("Goblin Token").size shouldBe 2
            }
        }
    }
}
