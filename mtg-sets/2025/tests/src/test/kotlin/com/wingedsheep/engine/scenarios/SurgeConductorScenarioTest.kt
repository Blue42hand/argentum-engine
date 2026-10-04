package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe

class SurgeConductorScenarioTest : ScenarioTestBase() {
    init {
        cardRegistry.register(CardDefinition.artifact("Iron Trinket", ManaCost.parse("{1}")))

        test("another nontoken artifact you control entering offers a proliferate choice") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Surge Conductor")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Iron Trinket")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(bears) { c ->
                c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
            }

            game.castSpell(1, "Iron Trinket").error shouldBe null
            game.resolveStack()
            game.hasPendingDecision() shouldBe true
            game.selectCards(listOf(bears)).error shouldBe null
            counterCount(game, bears) shouldBe 2
        }

        test("Surge Conductor entering does not trigger itself") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Surge Conductor")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(bears) { c ->
                c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
            }

            game.castSpell(1, "Surge Conductor").error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Surge Conductor") shouldBe true
            game.hasPendingDecision() shouldBe false
            counterCount(game, bears) shouldBe 1
        }

        test("an opponent's artifact entering does not trigger Surge Conductor") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Surge Conductor")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(2, "Iron Trinket")
                .withLandsOnBattlefield(2, "Forest", 1)
                .withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(bears) { c ->
                c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
            }

            game.castSpell(2, "Iron Trinket").error shouldBe null
            game.resolveStack()
            game.hasPendingDecision() shouldBe false
            counterCount(game, bears) shouldBe 1
        }

        test("an artifact token entering does not trigger Surge Conductor") {
            val game = scenario().withPlayers()
                .withCardOnBattlefield(1, "Surge Conductor")
                .withCardOnBattlefield(1, "Nesting Bot")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Disfigure")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val bot = game.findPermanent("Nesting Bot")!!
            val bears = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(bears) { c ->
                c.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 1))
            }

            game.castSpell(1, "Disfigure", targetId = bot).error shouldBe null
            game.resolveStack() // Nesting Bot dies and creates a Servo artifact creature token.
            game.isOnBattlefield("Servo Token") shouldBe true
            game.hasPendingDecision() shouldBe false
            counterCount(game, bears) shouldBe 1
        }
    }

    private fun counterCount(game: TestGame, entity: EntityId): Int =
        game.state.getEntity(entity)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
}
