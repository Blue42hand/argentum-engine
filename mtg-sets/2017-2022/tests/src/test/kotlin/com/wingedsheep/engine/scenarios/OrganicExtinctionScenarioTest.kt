package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.AlternativePaymentChoice
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class OrganicExtinctionScenarioTest : ScenarioTestBase() {
    init {
        test("destroys nonartifact creatures on both sides and spares artifact creatures") {
            val game = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Organic Extinction")
                .withLandsOnBattlefield(1, "Plains", 10)
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(1, "Memnite")
                .withCardOnBattlefield(2, "Hill Giant")
                .withCardOnBattlefield(2, "Ornithopter")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val result = game.castSpell(1, "Organic Extinction")
            withClue("cast succeeds: ${result.error}") { result.error shouldBe null }
            game.resolveStack()

            game.isInGraveyard(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe true
            game.isOnBattlefield("Memnite") shouldBe true
            game.isOnBattlefield("Ornithopter") shouldBe true
        }

        test("four artifacts improvise four generic mana while white mana is still required") {
            val scrap = card("Extinction Scrap") {
                manaCost = "{1}"
                colorIdentity = ""
                typeLine = "Artifact"
                oracleText = ""
            }
            cardRegistry.register(scrap)

            var builder = scenario()
                .withPlayers("P1", "P2")
                .withCardInHand(1, "Organic Extinction")
                .withLandsOnBattlefield(1, "Plains", 6)
            repeat(4) { builder = builder.withCardOnBattlefield(1, "Extinction Scrap") }
            val game = builder.withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val scraps = game.findAllPermanents("Extinction Scrap")
            val action = game.getLegalActions(1).first {
                it.actionType == "CastSpell" && it.action is CastSpell &&
                    it.description.contains("Organic Extinction")
            }
            action.isAffordable shouldBe true
            action.hasTapForGeneric shouldBe true

            val cast = (action.action as CastSpell).copy(
                alternativePayment = AlternativePaymentChoice(tapForGenericPermanents = scraps.toSet())
            )
            val result = game.execute(cast)
            withClue("improvise pays four of eight generic mana: ${result.error}") {
                result.error shouldBe null
            }
            game.resolveStack()
            scraps.all { game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
            game.isInGraveyard(1, "Organic Extinction") shouldBe true
        }
    }
}
