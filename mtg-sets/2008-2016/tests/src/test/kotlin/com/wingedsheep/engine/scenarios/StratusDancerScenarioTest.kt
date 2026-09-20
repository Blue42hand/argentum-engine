package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.TurnFaceUp
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.MorphDataComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dtk.cards.StratusDancer
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StratusDancerScenarioTest : FunSpec({
    val testInstant = card("Stratus Test Instant") {
        manaCost = "{U}"
        colorIdentity = "U"
        typeLine = "Instant"
        spell { effect = Effects.GainLife(5) }
    }

    test("megamorph adds a counter and counters an instant spell") {
        val cards = TestCards.all + listOf(StratusDancer, testInstant)
        val driver = GameTestDriver().apply {
            registerCards(cards)
            initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20, skipMulligans = true)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val dancer = driver.putCreatureOnBattlefield(player, "Stratus Dancer")
        val definition = cards.first { it.name == "Stratus Dancer" }
        val morph = definition.keywordAbilities.filterIsInstance<KeywordAbility.Morph>().single()
        driver.replaceState(driver.state.updateEntity(dancer) { entity ->
            entity.with(FaceDownComponent).with(
                MorphDataComponent(morph.morphCost, definition.name, morph.faceUpEffect)
            )
        })

        val instant = driver.putCardInHand(player, "Stratus Test Instant")
        driver.giveMana(player, Color.BLUE, 1)
        driver.castSpell(player, instant).error shouldBe null
        val spell = driver.state.stack.first { id ->
            driver.state.getEntity(id)?.get<CardComponent>()?.name == "Stratus Test Instant"
        }

        driver.giveColorlessMana(player, 1)
        driver.giveMana(player, Color.BLUE, 1)
        driver.submit(TurnFaceUp(playerId = player, sourceId = dancer)).error shouldBe null
        driver.submitTargetSelection(player, listOf(spell))
        driver.bothPass()

        driver.state.getEntity(dancer)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
        driver.getLifeTotal(player) shouldBe 20
        driver.getGraveyardCardNames(player).contains("Stratus Test Instant") shouldBe true
    }
})
