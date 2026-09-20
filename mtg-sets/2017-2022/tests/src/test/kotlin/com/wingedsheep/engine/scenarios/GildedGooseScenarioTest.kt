package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class GildedGooseScenarioTest : ScenarioTestBase() {

    init {
        test("entering creates a Food token") {
            val game = scenario()
                .withPlayers()
                .withCardInHand(1, "Gilded Goose")
                .withCardOnBattlefield(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Gilded Goose").error shouldBe null
            game.resolveStack()

            game.findAllPermanents("Food").size shouldBe 1
        }

        test("tapping and sacrificing Food adds the chosen color") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Gilded Goose", summoningSickness = false)
                .withCardOnBattlefield(1, "Food", isToken = true)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val goose = game.findPermanent("Gilded Goose")!!
            val food = game.findPermanent("Food")!!
            val manaAbility = cardRegistry.requireCard("Gilded Goose").activatedAbilities[1].id

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = goose,
                    abilityId = manaAbility,
                    costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(food)),
                    manaColorChoice = Color.GREEN,
                )
            ).error shouldBe null

            withClue("Food was consumed as the non-mana cost") {
                game.isOnBattlefield("Food") shouldBe false
            }
            withClue("the mana ability resolves immediately and makes the selected color") {
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.green shouldBe 1
            }
        }
    }
}
