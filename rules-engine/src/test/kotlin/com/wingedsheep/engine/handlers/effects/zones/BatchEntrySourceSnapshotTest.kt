package com.wingedsheep.engine.handlers.effects.zones

import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.PermanentsEnterTapped
import com.wingedsheep.sdk.scripting.conditions.Exists
import com.wingedsheep.sdk.scripting.references.Player
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BatchEntrySourceSnapshotTest : FunSpec({
    val tapper = card("Batch Entry Tapper") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(PermanentsEnterTapped())
    }
    val conditional = card("Batch Forest Entry Creature") {
        manaCost = "{0}"
        typeLine = "Creature — Test"
        power = 1
        toughness = 1
        replacementEffect(EntersTapped(unlessCondition = Exists(
            Player.You, Zone.BATTLEFIELD, GameObjectFilter.Land.withSubtype("Forest")
        )))
    }

    for (preexisting in listOf(false, true)) {
        test("simultaneous entrants see only pre-event global tap sources, preexisting=$preexisting") {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all + tapper)
            driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
            if (preexisting) driver.putPermanentOnBattlefield(driver.player1, tapper.name)
            val first = driver.putCardInGraveyard(driver.player1, tapper.name)
            val second = driver.putCardInGraveyard(driver.player1, tapper.name)
            val result = driver.zones.moveToZoneBatch(driver.state, listOf(first, second), Zone.BATTLEFIELD)
            for (id in listOf(first, second)) {
                (id in result.state.getBattlefield()) shouldBe true
                result.state.getEntity(id)!!.has<TappedComponent>() shouldBe preexisting
            }
        }
    }

    for (forestBeforeEvent in listOf(false, true)) {
        for (forestFirst in listOf(false, true)) {
            test("simultaneous own tap condition uses pre-event world, existingForest=$forestBeforeEvent forestFirst=$forestFirst") {
                val driver = GameTestDriver()
                driver.registerCards(TestCards.all + conditional)
                driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
                if (forestBeforeEvent) driver.putPermanentOnBattlefield(driver.player1, "Forest")
                val forest = driver.putCardInGraveyard(driver.player1, "Forest")
                val creature = driver.putCardInGraveyard(driver.player1, conditional.name)
                val entrants = if (forestFirst) listOf(forest, creature) else listOf(creature, forest)
                val result = driver.zones.moveToZoneBatch(driver.state, entrants, Zone.BATTLEFIELD)
                (forest in result.state.getBattlefield()) shouldBe true
                (creature in result.state.getBattlefield()) shouldBe true
                result.state.getEntity(creature)!!.has<TappedComponent>() shouldBe !forestBeforeEvent
            }
        }
    }
})
