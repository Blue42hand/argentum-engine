package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.TurnFaceUp
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.MorphDataComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dtk.cards.AinokSurvivalist
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class AinokSurvivalistScenarioTest : FunSpec({

    test("megamorph adds a counter and the face-up trigger destroys an opposing enchantment") {
        val cards = TestCards.all + AinokSurvivalist
        val driver = GameTestDriver().apply {
            registerCards(cards)
            initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        val survivalist = driver.putCreatureOnBattlefield(player, "Ainok Survivalist")
        val definition = cards.first { it.name == "Ainok Survivalist" }
        val morph = definition.keywordAbilities.filterIsInstance<KeywordAbility.Morph>().single()
        driver.replaceState(driver.state.updateEntity(survivalist) { entity ->
            entity.with(FaceDownComponent).with(
                MorphDataComponent(morph.morphCost, definition.name, morph.faceUpEffect)
            )
        })
        val enchantment = driver.putPermanentOnBattlefield(opponent, "Test Enchantment")

        driver.giveColorlessMana(player, 1)
        driver.giveMana(player, Color.GREEN, 1)
        driver.submit(
            TurnFaceUp(playerId = player, sourceId = survivalist, costTargetIds = emptyList())
        ).error shouldBe null

        driver.state.getEntity(survivalist)?.get<FaceDownComponent>() shouldBe null
        driver.state.getEntity(survivalist)?.get<CardComponent>()?.name shouldBe "Ainok Survivalist"
        driver.state.getEntity(survivalist)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1

        driver.submitTargetSelection(player, listOf(enchantment))
        driver.bothPass()
        driver.getGraveyard(opponent) shouldContain enchantment
    }
})
