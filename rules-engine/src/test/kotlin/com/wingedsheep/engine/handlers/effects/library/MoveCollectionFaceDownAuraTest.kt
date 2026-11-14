package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PipelineState
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.engine.state.components.identity.FaceDownModeComponent
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.FaceDownMode
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoveCollectionFaceDownAuraTest : FunSpec({
    val aura=card("Face Down Entry Aura") {
        manaCost="{1}{W}"
        typeLine="Enchantment — Aura"
        auraTarget=TargetObject(filter=TargetFilter.Creature)
        replacementEffect(EntersWithCounters(count=3,selfOnly=true))
    }
    for(mode in listOf(FaceDownMode.MANIFEST,FaceDownMode.CLOAK))for(host in listOf(false,true)) {
        test("$mode enters as a creature without Aura attachment or printed entry effect, host=$host") {
            val d=GameTestDriver().also { it.registerCards(TestCards.all+listOf(aura));it.initMirrorMatch(Deck.of("Island" to 40),skipMulligans=true) }
            if(host)d.putPermanentOnBattlefield(d.player1,"Grizzly Bears")
            val entrant=d.putCardOnTopOfLibrary(d.player1,aura.name)
            val result=MoveCollectionExecutor(d.services.zones,d.cardRegistry,d.services.targetFinder).execute(
                d.state,MoveCollectionEffect(from="selected",destination=CardDestination.ToZone(Zone.BATTLEFIELD),faceDown=mode),
                EffectContext(sourceId=null,controllerId=d.player1,pipeline=PipelineState(storedCollections=mapOf("selected" to listOf(entrant)))))
            result.state.pendingDecision shouldBe null
            result.state.getBattlefield(d.player1).contains(entrant) shouldBe true
            val entity=result.state.getEntity(entrant)!!
            entity.has<FaceDownComponent>() shouldBe true
            entity.get<FaceDownModeComponent>()!!.mode shouldBe mode
            entity.has<AttachedToComponent>() shouldBe false
            (entity.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            result.state.projectedState.isCreature(entrant) shouldBe true
            result.state.projectedState.getPower(entrant) shouldBe 2
            result.state.projectedState.getToughness(entrant) shouldBe 2
        }
    }
    test("face-up Aura entry still requests an attachment") {
        val d=GameTestDriver().also { it.registerCards(TestCards.all+listOf(aura));it.initMirrorMatch(Deck.of("Island" to 40),skipMulligans=true) }
        d.putPermanentOnBattlefield(d.player1,"Grizzly Bears")
        val entrant=d.putCardOnTopOfLibrary(d.player1,aura.name)
        val result=MoveCollectionExecutor(d.services.zones,d.cardRegistry,d.services.targetFinder).execute(
            d.state,MoveCollectionEffect(from="selected",destination=CardDestination.ToZone(Zone.BATTLEFIELD)),
            EffectContext(sourceId=null,controllerId=d.player1,pipeline=PipelineState(storedCollections=mapOf("selected" to listOf(entrant)))))
        (result.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision) shouldBe true
    }
})
