package com.wingedsheep.engine.mana

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

/** Manual non-spell bills must use the selected producers' real colours and amounts. */
class SelectedNonSpellManaPaymentTest : FunSpec({
    val surplus = card("Manual Surplus") {
        typeLine = "Land"
        activatedAbility { cost=Costs.Tap; effect=Effects.AddMana(Color.GREEN,3); manaAbility=true }
    }
    val treasure = card("Manual Treasure") {
        typeLine = "Artifact"
        activatedAbility { cost=Costs.Composite(Costs.Tap,Costs.SacrificeSelf); effect=Effects.AddAnyColorMana(1); manaAbility=true }
    }
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(surplus,treasure))
        it.initMirrorMatch(Deck.of("Forest" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun land(d:GameTestDriver,name:String):EntityId = d.putLandOnBattlefield(d.player1,name).also { id ->
        d.replaceState(d.state.updateEntity(id) { it.without<TappedComponent>() })
    }
    fun open(d:GameTestDriver,cost:String) {
        val bill=ManaCost.parse(cost)
        val effect=Effects.MayPay(bill,Effects.GainLife(1))
        val result=d.services.effectExecutorRegistry.execute(d.state,effect,EffectContext(sourceId=d.player1,controllerId=d.player1))
        d.replaceState(result.state)
        d.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(d.player1,true).error.shouldBeNull()
        d.state.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
    }
    fun pay(d:GameTestDriver,ids:List<EntityId>):ExecutionResult {
        val q=d.state.pendingDecision as SelectManaSourcesDecision
        return d.submitDecision(d.player1,ManaSourcesSelectedResponse(q.id,ids,autoPay=false))
    }
    run {
        test("a selected dual produces green for a green bill") {
            val d=driver();val dual=land(d,"Temple of Malady")
            open(d,"{G}")
            pay(d,listOf(dual)).error.shouldBeNull()
            d.state.pendingDecision.shouldBeNull()
            d.state.getEntity(dual)!!.has<TappedComponent>() shouldBe true
            d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 21
        }
        test("a selected producer preserves its surplus") {
            val d=driver();val source=land(d,surplus.name)
            open(d,"{G}")
            pay(d,listOf(source)).error.shouldBeNull()
            d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.green shouldBe 2
        }
    }
    test("resolution payment preserves the absence of priority") {
        val d=driver();val dual=land(d,"Temple of Malady")
        open(d,"{G}")
        val q=d.state.pendingDecision as SelectManaSourcesDecision
        val (frame,unpaused)=d.state.popContinuation()
        val continuation=(frame as Suspension).answer as MayPayManaSelectionContinuation
        val result=com.wingedsheep.engine.handlers.continuations.ManaPaymentContinuationResumer(d.services)
            .resumeMayPayManaSelection(unpaused.copy(priorityPlayerId=null),continuation,
                ManaSourcesSelectedResponse(q.id,listOf(dual),autoPay=false)) { state,events ->
                    state.priorityPlayerId.shouldBeNull()
                    ExecutionResult.success(state,events)
                }
        result.error.shouldBeNull()
        d.replaceState(result.state)
        d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 21
    }
    test("floating generic mana and a selected dual pay a mixed bill") {
        val d=driver();val dual=land(d,"Temple of Malady")
        d.replaceState(d.state.updateEntity(d.player1) { it.with(ManaPoolComponent(colorless=1)) })
        open(d,"{1}{G}")
        pay(d,listOf(dual)).error.shouldBeNull()
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.total shouldBe 0
    }
    test("two selected flexible sources pay distinct coloured pips") {
        val d=driver();val a=land(d,"Temple of Malady");val b=land(d,"Temple of Malady")
        open(d,"{B}{G}")
        pay(d,listOf(a,b)).error.shouldBeNull()
        d.state.getEntity(a)!!.has<TappedComponent>() shouldBe true
        d.state.getEntity(b)!!.has<TappedComponent>() shouldBe true
    }
    test("a selected any-colour sacrifice produces the bill's colour") {
        val d=driver();val source=d.putPermanentOnBattlefield(d.player1,treasure.name)
        open(d,"{G}")
        (d.state.pendingDecision as SelectManaSourcesDecision).availableSources.any { it.entityId == source } shouldBe true
        val result=pay(d,listOf(source));result.error.shouldBeNull()
        (source in d.state.getBattlefield()) shouldBe false
        result.events.filterIsInstance<PermanentsSacrificedEvent>().size shouldBe 1
        d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 21
    }
    test("selection never borrows a suitable unselected source") {
        val d=driver();val wrong=land(d,"Island");val right=land(d,"Forest")
        open(d,"{G}")
        pay(d,listOf(wrong)).outcome.shouldBeInstanceOf<Outcome.Rejected>()
        d.state.getEntity(wrong)!!.has<TappedComponent>() shouldBe false
        d.state.getEntity(right)!!.has<TappedComponent>() shouldBe false
        d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 20
    }
    test("declining an optional bill preserves sources and does not grant the effect") {
        val d=driver();val dual=land(d,"Temple of Malady");open(d,"{G}")
        val q=d.state.pendingDecision as SelectManaSourcesDecision
        d.submitDecision(d.player1,ManaSourcesSelectedResponse(q.id,emptyList(),autoPay=false,declined=true)).error.shouldBeNull()
        d.state.getEntity(dual)!!.has<TappedComponent>() shouldBe false
        d.state.getEntity(d.player1)!!.get<LifeTotalComponent>()!!.life shouldBe 20
    }
})
