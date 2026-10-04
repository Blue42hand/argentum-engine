package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

class PatrollingPeacemakerScenarioTest : ScenarioTestBase() {
    init {
        test("enters with two +1/+1 counters") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Patrolling Peacemaker")
                .withLandsOnBattlefield(1, "Plains", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Patrolling Peacemaker").error shouldBe null
            game.resolveStack()

            val peacemaker = game.findPermanent("Patrolling Peacemaker")!!
            counterCount(game, peacemaker) shouldBe 2
        }

        test("opponent committing a crime offers proliferate before the crime spell resolves") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Patrolling Peacemaker")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val peacemaker = game.findPermanent("Patrolling Peacemaker")!!
            game.state = game.state.updateEntity(peacemaker) { c ->
                c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 2))
            }

            game.castSpellTargetingPlayer(2, "Lightning Bolt", 1).error shouldBe null
            game.resolveStack()
            game.hasPendingDecision() shouldBe true
            game.getLifeTotal(1) shouldBe 20
            game.selectCards(listOf(peacemaker)).error shouldBe null

            counterCount(game, peacemaker) shouldBe 3
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 17
        }

        test("your crime does not trigger your Patrolling Peacemaker") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Patrolling Peacemaker")
                .withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
            game.resolveStack()
            game.hasPendingDecision() shouldBe false
        }

        test("opponent targeting themselves does not commit a crime") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Patrolling Peacemaker")
                .withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpellTargetingPlayer(2, "Lightning Bolt", 2).error shouldBe null
            game.resolveStack()
            game.hasPendingDecision() shouldBe false
        }
    }

    private fun counterCount(game: TestGame, entity: EntityId): Int =
        game.state.getEntity(entity)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
}
