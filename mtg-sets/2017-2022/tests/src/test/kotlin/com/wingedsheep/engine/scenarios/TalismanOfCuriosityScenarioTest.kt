package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.mh1.cards.TalismanOfCuriosity
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class TalismanOfCuriosityScenarioTest : ScenarioTestBase() {
    init {
        test("colored activation adds green mana and deals one damage to you") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Talisman of Curiosity")
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val talisman = game.findPermanent("Talisman of Curiosity")!!
            val greenAbility = TalismanOfCuriosity.activatedAbilities[1]

            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = talisman,
                    abilityId = greenAbility.id,
                ),
            ).error shouldBe null

            game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()?.green shouldBe 1
            game.getLifeTotal(1) shouldBe 19
        }
    }
}
