package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DiscipleOfBolasScenarioTest : ScenarioTestBase() {
    private val negative = card("Bolas Test Negative Power") {
        manaCost = "{0}"; typeLine = "Creature — Beast"; power = -2; toughness = 3
    }
    private fun board(other: String? = "Craw Wurm") = scenario().withPlayers("Player", "Opponent")
        .withCardInHand(1, "Disciple of Bolas").withLandsOnBattlefield(1, "Swamp", 4)
        .apply { if (other != null) withCardOnBattlefield(1, other) }
        .withCardInLibrary(1, "Plains").withCardInLibrary(1, "Plains").withCardInLibrary(1, "Plains")
        .withCardInLibrary(1, "Plains").withCardInLibrary(1, "Plains").withCardInLibrary(1, "Plains")
        .withCardInLibrary(1, "Plains").withCardInLibrary(2, "Island")
        .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
    init {
        cardRegistry.register(negative)
        test("sacrifice uses the other creature's last known power for both life and cards") {
            val game = board(); val wurm = game.findPermanent("Craw Wurm")!!
            game.castSpell(1, "Disciple of Bolas").error shouldBe null
            game.resolveStack()
            if (game.getPendingDecision() != null) game.selectCards(listOf(wurm)).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 26
            game.state.getHand(game.player1Id).size shouldBe 6
            game.isInGraveyard(1, "Craw Wurm") shouldBe true
            game.isOnBattlefield("Disciple of Bolas") shouldBe true
        }
        test("no other creature means no sacrifice and no payoff") {
            val game = board(null)
            game.castSpell(1, "Disciple of Bolas").error shouldBe null; game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.state.getHand(game.player1Id).size shouldBe 0
            game.isOnBattlefield("Disciple of Bolas") shouldBe true
        }
        test("negative power gains no life and draws no cards") {
            val game = board(negative.name); val body = game.findPermanent(negative.name)!!
            game.castSpell(1, "Disciple of Bolas").error shouldBe null; game.resolveStack()
            if (game.getPendingDecision() != null) game.selectCards(listOf(body)).error shouldBe null
            game.resolveStack()
            game.getLifeTotal(1) shouldBe 20
            game.state.getHand(game.player1Id).size shouldBe 0
            game.isInGraveyard(1, negative.name) shouldBe true
        }
    }
}
