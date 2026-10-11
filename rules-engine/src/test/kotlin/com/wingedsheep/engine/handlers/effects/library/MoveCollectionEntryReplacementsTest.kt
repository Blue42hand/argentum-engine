package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CantEnterTheBattlefield
import com.wingedsheep.sdk.scripting.DoubleCounterPlacement
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.EntersUntapped
import com.wingedsheep.sdk.scripting.PermanentsEnterTapped
import com.wingedsheep.sdk.scripting.GrantSubtype
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.events.Recipient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Replacement sources are evaluated before the simultaneous collection entry. */
class MoveCollectionEntryReplacementsTest : FunSpec({
    val counterCreature = card("Batch Counter Creature") {
        manaCost = "{0}"
        typeLine = "Artifact Creature — Construct"
        power = 1
        toughness = 1
        replacementEffect(EntersWithCounters(count = 2))
    }
    val doubler = card("Batch Counter Doubler") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(DoubleCounterPlacement(
            placedByYou = false,
            appliesTo = EventPattern.CounterPlacementEvent(
                counterType = null, recipient = Recipient.PermanentYouControl
            )
        ))
    }
    val graveyardGrant = card("Batch Graveyard Grant") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(EntersWithDynamicCounters(
            count = DynamicAmounts.fixed(1), otherOnly = true,
            appliesTo = EventPattern.ZoneChangeEvent(
                filter = GameObjectFilter.Creature.youControl(), to = Zone.BATTLEFIELD
            ),
            activeZones = setOf(Zone.GRAVEYARD)
        ))
    }
    val cage = card("Batch Entry Lock") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility {
            ability = CantEnterTheBattlefield(GameObjectFilter.Creature, fromZones = setOf(Zone.GRAVEYARD))
        }
    }
    val tapper = card("Batch Entry Tapper") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(PermanentsEnterTapped())
    }
    val untapper = card("Batch Entry Untapper") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(EntersUntapped())
    }
    val ownTapped = card("Batch Own Tapped") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(EntersTapped())
    }
    val humanifier = card("Batch Human Grant") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility { ability = GrantSubtype("Human", GroupFilter(GameObjectFilter.Creature.youControl())) }
    }
    val humanCounter = card("Batch Human Counter") {
        manaCost = "{0}"
        typeLine = "Artifact"
        replacementEffect(EntersWithCounters(count = 1, otherOnly = true,
            appliesTo = EventPattern.ZoneChangeEvent(
                filter = GameObjectFilter.Creature.youControl().withSubtype(Subtype.HUMAN), to = Zone.BATTLEFIELD
            )))
    }
    val selfHuman = card("Batch Own Human") {
        manaCost = "{0}"
        typeLine = "Artifact Creature — Construct"
        power = 1
        toughness = 1
        staticAbility { ability = GrantSubtype("Human") }
        replacementEffect(EntersWithCounters(count = 2))
    }

    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(counterCreature, doubler, graveyardGrant, cage, tapper, untapper, ownTapped,
            humanifier, humanCounter, selfHuman))
        it.initMirrorMatch(Deck.of("Island" to 40), skipMulligans = true)
    }
    fun move(d: GameTestDriver, ids: List<EntityId>) = MoveCollectionExecutor(
        d.services.zones, d.cardRegistry
    ).execute(d.state, MoveCollectionEffect(
        from = "return", destination = CardDestination.ToZone(Zone.BATTLEFIELD), storeMovedAs = "returned"
    ), EffectContext(sourceId = null, controllerId = d.player1,
        pipeline = PipelineState(storedCollections = mapOf("return" to ids))))

    listOf(false, true).forEach { reverse ->
        test("a simultaneously entering counter doubler does not modify another entrant, reverse=$reverse") {
            val d = driver()
            val source = d.putCardInGraveyard(d.player1, doubler.name)
            val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
            val ids = listOf(source, creature).let { if (reverse) it.reversed() else it }
            val result = move(d, ids)
            result.state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            result.updatedCollections["returned"] shouldBe ids
        }
    }
    test("a preexisting counter doubler modifies the entrant's own counters") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, doubler.name)
        val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
        val result = move(d, listOf(creature))
        result.state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 4
    }
    listOf(false, true).forEach { reverse ->
        test("a pre-event graveyard replacement applies to the other entrant, reverse=$reverse") {
            val d = driver()
            val source = d.putCardInGraveyard(d.player1, graveyardGrant.name)
            val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
            val result = move(d, listOf(source, creature).let { if (reverse) it.reversed() else it })
            result.state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
        }
    }
    test("an entry lock produces no counters, movement receipt, or tracked return for the blocked card") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, cage.name)
        val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
        val artifact = d.putCardInGraveyard(d.player1, doubler.name)
        val result = move(d, listOf(creature, artifact))
        (creature in result.state.getGraveyard(d.player1)) shouldBe true
        result.state.getEntity(creature)!!.get<CountersComponent>() shouldBe null
        result.updatedCollections["returned"] shouldBe listOf(artifact)
        result.events.filterIsInstance<ZoneChangeEvent>().map { it.entityId } shouldBe listOf(artifact)
    }
    listOf(false, true).forEach { reverse ->
        test("a simultaneous entry lock cannot block another entrant, reverse=$reverse") {
            val d = driver()
            val source = d.putCardInGraveyard(d.player1, cage.name)
            val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
            val ids = listOf(source, creature).let { if (reverse) it.reversed() else it }
            val result = move(d, ids)
            result.state.getBattlefield().containsAll(ids) shouldBe true
            result.updatedCollections["returned"] shouldBe ids
        }
        test("a simultaneous global tap replacement cannot tap another entrant, reverse=$reverse") {
            val d = driver()
            val source = d.putCardInGraveyard(d.player1, tapper.name)
            val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
            val result = move(d, listOf(source, creature).let { if (reverse) it.reversed() else it })
            result.state.getEntity(creature)!!.has<TappedComponent>() shouldBe false
        }
        test("a simultaneous global untap replacement cannot override an entrant's own tapped entry, reverse=$reverse") {
            val d = driver()
            val source = d.putCardInGraveyard(d.player1, untapper.name)
            val artifact = d.putCardInGraveyard(d.player1, ownTapped.name)
            val result = move(d, listOf(source, artifact).let { if (reverse) it.reversed() else it })
            result.state.getEntity(artifact)!!.has<TappedComponent>() shouldBe true
        }
    }
    test("a preexisting global tapped replacement still applies") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, tapper.name)
        val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
        move(d, listOf(creature)).state.getEntity(creature)!!.has<TappedComponent>() shouldBe true
    }
    test("a preexisting global untapped replacement still overrides own tapped entry") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, untapper.name)
        val artifact = d.putCardInGraveyard(d.player1, ownTapped.name)
        move(d, listOf(artifact)).state.getEntity(artifact)!!.has<TappedComponent>() shouldBe false
    }
    listOf(false, true).forEach { reverse ->
        test("another simultaneous entrant's type grant does not change entry replacement matching, reverse=$reverse") {
            val d = driver()
            d.putPermanentOnBattlefield(d.player1, humanCounter.name)
            val source = d.putCardInGraveyard(d.player1, humanifier.name)
            val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
            val result = move(d, listOf(source, creature).let { if (reverse) it.reversed() else it })
            result.state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
            result.state.projectedState.hasSubtype(creature, "Human") shouldBe true
        }
    }
    test("a preexisting type grant affects entry replacement matching") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, humanCounter.name)
        d.putPermanentOnBattlefield(d.player1, humanifier.name)
        val creature = d.putCardInGraveyard(d.player1, counterCreature.name)
        move(d, listOf(creature)).state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
    }
    test("the entrant's own static type grant affects entry replacement matching") {
        val d = driver()
        d.putPermanentOnBattlefield(d.player1, humanCounter.name)
        val creature = d.putCardInGraveyard(d.player1, selfHuman.name)
        move(d, listOf(creature)).state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
    }
})
