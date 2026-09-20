package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class KamiOfWhisperedHopesScenarioTest : ScenarioTestBase() {

    init {
        test("adds one extra +1/+1 counter to a permanent you control") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Kami of Whispered Hopes")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInHand(1, "Stony Strength")
                .withCardOnBattlefield(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSpell(1, "Stony Strength", targetId = bears).error shouldBe null
            game.resolveStack()

            game.state.getEntity(bears)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
        }

        test("mana ability adds mana equal to current power") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Kami of Whispered Hopes", summoningSickness = false)
                .withCardInHand(1, "Stony Strength")
                .withCardOnBattlefield(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val kami = game.findPermanent("Kami of Whispered Hopes")!!
            game.castSpell(1, "Stony Strength", targetId = kami).error shouldBe null
            game.resolveStack()
            game.state.projectedState.getPower(kami) shouldBe 3

            val ability = cardRegistry.requireCard("Kami of Whispered Hopes").activatedAbilities.single().id
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = kami,
                    abilityId = ability,
                    manaColorChoice = Color.BLUE,
                )
            ).error shouldBe null

            withClue("base power 1 plus two replacement-modified counters produces three mana") {
                game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.blue shouldBe 3
            }
        }
    }
}
