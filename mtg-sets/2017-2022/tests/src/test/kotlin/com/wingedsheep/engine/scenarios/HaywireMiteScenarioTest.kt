package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class HaywireMiteScenarioTest : ScenarioTestBase() {
    init {
        test("sacrificing Haywire Mite exiles a noncreature artifact and gains two life") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardOnBattlefield(1, "Haywire Mite")
                .withCardOnBattlefield(2, "Mind Stone")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withLifeTotal(1, 20)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val mite = game.findPermanent("Haywire Mite")!!
            val target = game.findPermanent("Mind Stone")!!
            val ability = cardRegistry.getCard("Haywire Mite")!!.script.activatedAbilities.single()
            game.execute(
                ActivateAbility(
                    playerId = game.player1Id,
                    sourceId = mite,
                    abilityId = ability.id,
                    targets = listOf(ChosenTarget.Permanent(target)),
                )
            ).error shouldBe null
            game.resolveStack()

            game.isOnBattlefield("Haywire Mite") shouldBe false
            game.isInGraveyard(1, "Haywire Mite") shouldBe true
            game.isOnBattlefield("Mind Stone") shouldBe false
            game.isInExile(2, "Mind Stone") shouldBe true
            game.getLifeTotal(1) shouldBe 22
        }
    }
}
