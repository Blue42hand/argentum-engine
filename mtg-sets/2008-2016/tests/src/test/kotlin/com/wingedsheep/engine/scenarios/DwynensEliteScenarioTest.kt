package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class DwynensEliteScenarioTest : ScenarioTestBase() {
    init {
        for(other in listOf("none","own Elf","own non-Elf","opponent Elf")) {
            test("another Elf is required - $other") {
                val builder=scenario().withPlayers("Alice","Bob").withCardInHand(1,"Dwynen's Elite")
                    .withLandsOnBattlefield(1,"Forest",2)
                    .withActivePlayer(1).withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN)
                when(other) {
                    "own Elf" -> builder.withCardOnBattlefield(1,"Llanowar Elves")
                    "own non-Elf" -> builder.withCardOnBattlefield(1,"Grizzly Bears")
                    "opponent Elf" -> builder.withCardOnBattlefield(2,"Llanowar Elves")
                }
                val game=builder.build()
                game.castSpell(1,"Dwynen's Elite").error shouldBe null
                game.resolveStack()
                game.state.getBattlefield(game.player1Id).count {
                    game.state.getEntity(it)?.get<CardComponent>()?.name=="Elf Warrior Token"
                } shouldBe if(other=="own Elf")1 else 0
            }
        }
        test("intervening condition is checked again after the other Elf dies") {
            val game=scenario().withPlayers("Alice","Bob").withCardInHand(1,"Dwynen's Elite")
                .withLandsOnBattlefield(1,"Forest",2)
                .withCardOnBattlefield(1,"Llanowar Elves")
                .withCardInHand(2,"Lightning Bolt").withLandsOnBattlefield(2,"Mountain",1)
                .withActivePlayer(1).withPriorityPlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN).build()
            val other=game.findPermanent("Llanowar Elves")!!
            game.castSpell(1,"Dwynen's Elite").error shouldBe null
            game.passPriority();game.passPriority()
            game.state.stack.isNotEmpty() shouldBe true
            game.passPriority()
            game.castSpell(2,"Lightning Bolt",other).error shouldBe null
            game.resolveStack()
            game.state.getBattlefield(game.player1Id).count {
                game.state.getEntity(it)?.get<CardComponent>()?.name=="Elf Warrior Token"
            } shouldBe 0
        }
    }
}
