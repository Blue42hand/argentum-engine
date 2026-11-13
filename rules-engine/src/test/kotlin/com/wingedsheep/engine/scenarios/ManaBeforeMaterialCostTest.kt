package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Mana abilities precede material payments; additional tap costs still reserve their resources. */
class ManaBeforeMaterialCostTest : ScenarioTestBase() {
    init {
        for (spell in listOf("Diabolic Intent", "Deadly Dispute", "Village Rites")) {
            test("$spell can use the sacrificed creature's mana and applies its side effect once") {
                val game = scenario().withPlayers()
                    .withCardInHand(1, spell)
                    .withCardOnBattlefield(1, "Elves of Deep Shadow")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val beforeLife = game.getLifeTotal(1)
                val result = game.castSpellWithAdditionalSacrifice(1, spell, "Elves of Deep Shadow")
                result.error.shouldBeNull()
                game.isInGraveyard(1, "Elves of Deep Shadow") shouldBe true
                game.findCardsInHand(1, spell).size shouldBe 0
                game.state.stack.size shouldBe 1
                game.getLifeTotal(1) shouldBe beforeLife - 1
            }
        }

        test("an unaffordable material payment leaves the original resources intact") {
            val game = scenario().withPlayers()
                .withCardInHand(1, "Diabolic Intent")
                .withCardOnBattlefield(1, "Elves of Deep Shadow")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val before = game.state
            game.castSpellWithAdditionalSacrifice(1, "Diabolic Intent", "Elves of Deep Shadow")
                .error.shouldNotBeNull()
            game.state shouldBe before
        }

        for (explicit in listOf(false, true)) {
            for (island in listOf(false, true)) {
                test("additional tap resources remain reserved explicit=$explicit island=$island") {
                    val game = scenario().withPlayers()
                        .withCardInHand(1, "Shared Discovery")
                        .apply { repeat(4) { withCardOnBattlefield(1, "Birds of Paradise") } }
                        .apply { if (island) withLandsOnBattlefield(1, "Island", 1) }
                        .withActivePlayer(1)
                        .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                    val birds = game.findPermanents("Birds of Paradise")
                    val before = game.state
                    val result = game.execute(CastSpell(
                        game.player1Id, game.findCardsInHand(1, "Shared Discovery").single(), emptyList(),
                        paymentStrategy = if (explicit) PaymentStrategy.Explicit(listOf(
                            if (island) game.findPermanents("Island").single() else birds.first()
                        )) else PaymentStrategy.AutoPay,
                        additionalCostPayment = AdditionalCostPayment(tappedPermanents = birds),
                    ))
                    if (island) {
                        result.error.shouldBeNull()
                        birds.all { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe true
                        game.state.stack.size shouldBe 1
                    } else {
                        result.error.shouldNotBeNull()
                        game.state shouldBe before
                    }
                }
            }
        }
    }
}
