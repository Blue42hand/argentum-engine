package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.PermanentsEnterTapped
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoveCollectionAuraEntrySourceTest : FunSpec({
    val aura = card("Frozen Entry Test Aura") {
        manaCost = "{1}{G}"
        typeLine = "Enchantment — Aura"
        replacementEffect(EntersWithCounters(count = 1, selfOnly = true))
    }
    val grant = card("Frozen Entry Enchantment Grant") {
        manaCost = "{2}"
        typeLine = "Artifact"
        replacementEffect(EntersWithCounters(count = 3, otherOnly = true,
            appliesTo = EventPattern.ZoneChangeEvent(
                filter = GameObjectFilter.Enchantment.youControl(), to = Zone.BATTLEFIELD,
            )))
        replacementEffect(PermanentsEnterTapped(appliesTo = EventPattern.ZoneChangeEvent(
            filter = GameObjectFilter.Enchantment.youControl(), to = Zone.BATTLEFIELD,
        )))
    }

    listOf(false, true).forEach { sourceExistedBeforeEvent ->
        test("aura attachment entry uses frozen sources, preexisting=$sourceExistedBeforeEvent") {
            val driver = GameTestDriver()
            driver.registerCards(TestCards.all + listOf(aura, grant))
            driver.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true)
            val player = driver.player1
            val host = driver.putPermanentOnBattlefield(player, "Grizzly Bears")
            val entrant = driver.putCardInGraveyard(player, aura.name)
            if (sourceExistedBeforeEvent) driver.putPermanentOnBattlefield(player, grant.name)
            val beforeEvent = driver.state
            // The other member of the simultaneous collection has already been materialized.
            if (!sourceExistedBeforeEvent) driver.putPermanentOnBattlefield(player, grant.name)
            val executor = MoveCollectionExecutor(driver.services.zones, driver.cardRegistry)
            val (afterEntry, _) = executor.moveAuraToBattlefield(
                driver.state, entrant, host, player, replacementSourceState = beforeEvent,
            )
            val permanent = afterEntry.getEntity(entrant)!!
            permanent.get<AttachedToComponent>()!!.targetId shouldBe host
            permanent.has<TappedComponent>() shouldBe sourceExistedBeforeEvent
            permanent.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe
                if (sourceExistedBeforeEvent) 4 else 1
            afterEntry.getBattlefield().toSet() shouldBe (driver.state.getBattlefield() + entrant).toSet()
        }
    }
})
