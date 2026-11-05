package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MerchantScrollScenarioTest : ScenarioTestBase() {
    private val multicolorInstant = card("Merchant Scroll Multicolor Instant") {
        manaCost = "{G}{U}"
        typeLine = "Instant"
        spell { effect = Effects.DrawCards(0) }
    }

    init {
        cardRegistry.register(multicolorInstant)

        test("search offers blue instants including multicolor but rejects a nonblue instant") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Merchant Scroll")
                .withLandsOnBattlefield(1, "Island", 2)
                .withCardInLibrary(1, "Counterspell")
                .withCardInLibrary(1, multicolorInstant.name)
                .withCardInLibrary(1, "Lightning Bolt")
                .withCardInLibrary(1, "Air Elemental")
                .withCardInLibrary(1, "Ponder")
                .withActivePlayer(1)
                .withPriorityPlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bolt = game.findCardsInLibrary(1, "Lightning Bolt").single()
            game.castSpell(1, "Merchant Scroll").error shouldBe null
            game.resolveStack()
            val decision = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            val info = decision.cardInfo!!
            decision.options.map { info[it]!!.name } shouldContainExactlyInAnyOrder
                listOf("Counterspell", multicolorInstant.name)

            val beforeInvalidSelection = game.state
            val invalid = game.selectCards(listOf(bolt))
            invalid.error shouldNotBe null
            invalid.newState shouldBe beforeInvalidSelection

            val chosen = decision.options.single { info[it]!!.name == multicolorInstant.name }
            game.selectCards(listOf(chosen)).error shouldBe null
            game.resolveStack()
            game.findCardsInHand(1, multicolorInstant.name).single() shouldBe chosen
            game.findCardsInLibrary(1, "Lightning Bolt").single() shouldBe bolt
            game.findCardsInLibrary(1, "Counterspell").size shouldBe 1
            game.findCardsInLibrary(1, "Air Elemental").size shouldBe 1
            game.findCardsInLibrary(1, "Ponder").size shouldBe 1
        }
    }
}
