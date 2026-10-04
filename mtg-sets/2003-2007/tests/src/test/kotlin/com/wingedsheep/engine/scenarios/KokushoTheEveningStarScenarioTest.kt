package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.mtg.sets.definitions.chk.cards.KokushoTheEveningStar
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Kokusho, the Evening Star (CHK #122) — "When Kokusho dies, each opponent loses 5 life. You gain
 * life equal to the life lost this way."
 */
class KokushoTheEveningStarScenarioTest : ScenarioTestBase() {

    init {
        context("Kokusho, the Evening Star") {

            test("dying drains each opponent for 5 and gains the life lost") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kokusho, the Evening Star")
                    .withCardInHand(1, "Murder")
                    .withLandsOnBattlefield(1, "Swamp", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val kokusho = game.findPermanent("Kokusho, the Evening Star")!!
                val p1Before = game.getLifeTotal(1)
                val p2Before = game.getLifeTotal(2)

                game.castSpell(1, "Murder", kokusho).error shouldBe null
                game.resolveStack()

                withClue("Kokusho died") { game.isInGraveyard(1, "Kokusho, the Evening Star") shouldBe true }
                withClue("the opponent lost 5") { game.getLifeTotal(2) shouldBe p2Before - 5 }
                withClue("its controller gained the 5 lost") { game.getLifeTotal(1) shouldBe p1Before + 5 }
            }

            test("gains the aggregate life lost by three opponents") {
                val driver = GameTestDriver().apply { registerCards(TestCards.all) }
                val players = driver.initMultiplayer(
                    decks = List(4) { Deck.of("Swamp" to 40) },
                    startingLife = 20,
                )
                val kokusho = driver.putCreatureOnBattlefield(players.first(), "Kokusho, the Evening Star")
                val result = driver.services.effectExecutorRegistry.execute(
                    driver.state,
                    KokushoTheEveningStar.triggeredAbilities.single().effect,
                    EffectContext(sourceId = kokusho, controllerId = players.first()),
                )

                players.drop(1).forEach { opponent ->
                    result.state.getEntity(opponent)
                        ?.get<com.wingedsheep.engine.state.components.identity.LifeTotalComponent>()
                        ?.life shouldBe 15
                }
                result.state.getEntity(players.first())
                    ?.get<com.wingedsheep.engine.state.components.identity.LifeTotalComponent>()
                    ?.life shouldBe 35
            }
        }
    }
}
