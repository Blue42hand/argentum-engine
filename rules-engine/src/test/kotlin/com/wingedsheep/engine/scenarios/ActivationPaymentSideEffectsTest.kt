package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.effects.AddColorlessManaEffect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Mana abilities resolve their full effect during outer ability payment, including damage. */
class ActivationPaymentSideEffectsTest : FunSpec({
    val painful = card("Painful Test Source") {
        typeLine="Land"
        activatedAbility {
            cost=Costs.Tap
            effect=Effects.AddMana(Color.BLACK) then Effects.DealDamage(1,EffectTarget.PlayerRef(Player.You))
            manaAbility=true
        }
    }
    val mixed = card("Mixed Test Source") {
        typeLine="Land"
        activatedAbility { cost=Costs.Tap;effect=AddColorlessManaEffect(1);manaAbility=true }
        activatedAbility {
            cost=Costs.Tap
            effect=Effects.AddMana(Color.BLACK) then Effects.DealDamage(1,EffectTarget.PlayerRef(Player.You))
            manaAbility=true
        }
    }
    val multiple = card("Multiple Test Source") {
        typeLine="Land"
        activatedAbility {
            cost=Costs.Tap
            effect=Effects.AddMana(Color.BLACK,3) then Effects.DealDamage(1,EffectTarget.PlayerRef(Player.You))
            manaAbility=true
        }
    }
    val lifeCost = card("Life Cost Test Source") {
        typeLine="Land"
        activatedAbility {
            cost=Costs.Composite(Costs.Tap,Costs.PayLife(1))
            effect=Effects.AddMana(Color.BLACK)
            manaAbility=true
        }
    }
    val sinks=listOf("1","B").map { symbol -> card("Payment Sink $symbol") {
        typeLine="Artifact"
        activatedAbility { cost=Costs.Mana("{$symbol}");effect=Effects.DrawCards(1) }
    } }
    fun setup():GameTestDriver=GameTestDriver().also { d ->
        d.registerCards(TestCards.all+listOf(painful,mixed,multiple,lifeCost)+sinks)
        d.initMirrorMatch(deck=Deck.of("Mountain" to 40),skipMulligans=true,startingPlayer=0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun pay(sourceName:String,cost:String="1",explicit:Boolean=false,seeded:Boolean=false):GameTestDriver {
        val d=setup();val me=d.player1
        val source=d.putLandOnBattlefield(me,sourceName)
        val sink=d.putPermanentOnBattlefield(me,"Payment Sink $cost")
        if(seeded)d.replaceState(d.state.updateEntity(me) { it.with(ManaPoolComponent(black=1)) })
        d.submitSuccess(ActivateAbility(playerId=me,sourceId=sink,
            abilityId=d.cardRegistry.requireCard("Payment Sink $cost").activatedAbilities.single().id,
            paymentStrategy=if(explicit)PaymentStrategy.Explicit(listOf(source)) else PaymentStrategy.AutoPay))
        d.state.getEntity(source)!!.has<TappedComponent>() shouldBe !seeded
        d.events.filterIsInstance<AbilityActivatedEvent>().count { it.sourceId==source } shouldBe if(seeded)0 else 1
        return d
    }
    test("automatic ability funding resolves mana source damage exactly once") {
        val d=pay(painful.name)
        d.getLifeTotal(d.player1) shouldBe 19
        d.events.filterIsInstance<DamageDealtEvent>().count { it.targetId==d.player1 && it.amount==1 } shouldBe 1
        d.state.stack.size shouldBe 1
    }
    test("explicit source selection also resolves damage exactly once") {
        val d=pay(painful.name,explicit=true)
        d.getLifeTotal(d.player1) shouldBe 19
    }
    test("existing floating mana leaves painful source and life untouched") {
        val d=pay(painful.name,seeded=true)
        d.getLifeTotal(d.player1) shouldBe 20
    }
    test("free colorless mode of mixed source causes no colored mode damage") {
        val d=pay(mixed.name)
        d.getLifeTotal(d.player1) shouldBe 20
    }
    test("required black mode of mixed source causes its damage") {
        val d=pay(mixed.name,cost="B")
        d.getLifeTotal(d.player1) shouldBe 19
    }
    test("multi mana production keeps excess and applies side effect once") {
        val d=pay(multiple.name)
        d.getLifeTotal(d.player1) shouldBe 19
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.black shouldBe 2
    }
    test("life paid as a cost remains charged exactly once") {
        val d=pay(lifeCost.name)
        d.getLifeTotal(d.player1) shouldBe 19
        d.events.filterIsInstance<DamageDealtEvent>().size shouldBe 0
    }
})
