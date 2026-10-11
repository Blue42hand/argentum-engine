package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.token.CreatePredefinedTokenExecutor
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.PermanentsEnterTapped
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PredefinedTokenEntrySourceSnapshotTest : FunSpec({
    val tapper = card("Predefined Entry Tapper") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(PermanentsEnterTapped())
    }

    listOf(false, true).forEach { preexisting ->
        test("predefined tokens use only pre-event global tap sources, preexisting=$preexisting") {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all + tapper)
            driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
            if (preexisting) driver.putPermanentOnBattlefield(driver.player1, tapper.name)
            val before = driver.state.getBattlefield().toSet()
            val executor = CreatePredefinedTokenExecutor(
                driver.cardRegistry, StaticAbilityHandler(driver.cardRegistry),
                driver.services.dynamicAmountEvaluator,
            )
            val result = executor.execute(driver.state,
                CreatePredefinedTokenEffect(tapper.name, count = 2),
                EffectContext(sourceId = null, controllerId = driver.player1),
            )
            val created = result.state.getBattlefield().filter { it !in before }
            created.size shouldBe 2
            created.forEach { id ->
                result.state.getEntity(id)!!.has<TappedComponent>() shouldBe preexisting
            }
        }
    }
})
