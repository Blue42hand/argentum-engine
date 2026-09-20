package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.grn.cards.GoblinCratermaker
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GoblinCratermakerScenarioTest : ScenarioTestBase() {
    init {
        test("sacrificing it can destroy a colorless nonland permanent") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Goblin Cratermaker")
                .withCardOnBattlefield(2, "Sol Ring")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val cratermaker = game.findPermanent("Goblin Cratermaker")!!
            val ring = game.findPermanent("Sol Ring")!!
            val ability = GoblinCratermaker.activatedAbilities.single()

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = cratermaker,
                    abilityId = ability.id,
                    paymentStrategy = PaymentStrategy.AutoPay,
                ),
            ).error shouldBe null
            game.resolveStack()
            val choice = game.getPendingDecision() as ChooseOptionDecision
            game.submitDecision(OptionChosenResponse(choice.id, 1))
            game.selectTargets(listOf(ring)).error shouldBe null
            game.resolveStack()

            game.findPermanent("Goblin Cratermaker") shouldBe null
            game.findPermanent("Sol Ring") shouldBe null
        }
    }
}
