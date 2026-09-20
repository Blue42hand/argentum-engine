package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutorRegistry
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.chk.cards.KokushoTheEveningStar
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KokushoTheEveningStarScenarioTest : FunSpec({
    test("dies ability drains every opponent and gains the aggregate life in a four-seat game") {
        val driver = GameTestDriver().apply { registerCards(TestCards.all) }
        val players = driver.initMultiplayer(
            decks = List(4) { Deck.of("Swamp" to 40) },
            startingLife = 20,
        )
        val kokusho = driver.putCreatureOnBattlefield(players.first(), "Kokusho, the Evening Star")
        val effect = KokushoTheEveningStar.triggeredAbilities.single().effect

        val result = EffectExecutorRegistry(cardRegistry = driver.cardRegistry).execute(
            driver.state,
            effect,
            EffectContext(sourceId = kokusho, controllerId = players.first()),
        )

        withClue("all three opponents lose 5 life") {
            players.drop(1).forEach { opponent ->
                result.state.getEntity(opponent)
                    ?.get<com.wingedsheep.engine.state.components.identity.LifeTotalComponent>()
                    ?.life shouldBe 15
            }
        }
        withClue("Kokusho's controller gains the total 15 life lost") {
            result.state.getEntity(players.first())
                ?.get<com.wingedsheep.engine.state.components.identity.LifeTotalComponent>()
                ?.life shouldBe 35
        }
    }
})
