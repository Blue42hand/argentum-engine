package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SagaComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ScaleCounterPlacement
import com.wingedsheep.sdk.scripting.events.Recipient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SagaEntrySourceSnapshotTest : FunSpec({
    val saga = card("Snapshot Saga") {
        typeLine = "Enchantment — Saga"
        for (chapter in 1..6) sagaChapter(chapter) { effect = Effects.GainLife(1) }
    }
    val doubler = card("Snapshot Lore Doubler") {
        typeLine = "Enchantment"
        replacementEffect(ScaleCounterPlacement(
            appliesTo = EventPattern.CounterPlacementEvent(null, Recipient.PermanentYouControl)
        ))
    }

    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(saga, doubler))
        it.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
    }

    test("a counter doubler arriving with a Saga cannot modify its lore entry") {
        val driver = driver()
        val before = driver.state
        driver.putPermanentOnBattlefield(driver.player1, doubler.name)
        val entrant = driver.putPermanentOnBattlefield(driver.player1, saga.name)
        val prepared = driver.state.updateEntity(entrant) {
            it.without<SagaComponent>().without<CountersComponent>()
        }
        val (after, events) = ZoneMovementUtils.applySagaEntryIfNeeded(
            prepared, entrant, driver.services.predicateEvaluator,
            replacementSourceState = before
        )
        after.getEntity(entrant)!!.get<CountersComponent>()!!.getCount(CounterType.LORE) shouldBe 1
        after.getEntity(entrant)!!.get<SagaComponent>()!!.triggeredChapters shouldBe setOf(1)
        events.filterIsInstance<CountersAddedEvent>().map { it.amount } shouldBe listOf(1)
        val (repeated, repeatEvents) = ZoneMovementUtils.applySagaEntryIfNeeded(
            after, entrant, driver.services.predicateEvaluator, replacementSourceState = before
        )
        repeated shouldBe after
        repeatEvents shouldBe emptyList()
    }

    test("a frozen counter source still modifies lore when it is absent from the resumed world") {
        val driver = driver()
        val source = driver.putPermanentOnBattlefield(driver.player1, doubler.name)
        val before = driver.state
        val entrant = driver.putPermanentOnBattlefield(driver.player1, saga.name)
        val prepared = driver.state.removeFromZone(ZoneKey(driver.player1, Zone.BATTLEFIELD), source)
            .updateEntity(entrant) { it.without<SagaComponent>().without<CountersComponent>() }
        val (after, events) = ZoneMovementUtils.applySagaEntryIfNeeded(
            prepared, entrant, driver.services.predicateEvaluator,
            replacementSourceState = before
        )
        after.getEntity(entrant)!!.get<CountersComponent>()!!.getCount(CounterType.LORE) shouldBe 2
        after.getEntity(entrant)!!.get<SagaComponent>()!!.triggeredChapters shouldBe setOf(1, 2)
        events.filterIsInstance<CountersAddedEvent>().map { it.amount } shouldBe listOf(2)
    }
})
