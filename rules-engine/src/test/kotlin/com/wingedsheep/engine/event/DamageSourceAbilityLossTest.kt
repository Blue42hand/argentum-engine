package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.state.components.stack.captureLastKnown
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.LoseAllAbilities
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DamageSourceAbilityLossTest : FunSpec({
    val source = card("Test Damage Source") {
        typeLine = "Creature — Elf"
        power = 2
        toughness = 2
        triggeredAbility {
            trigger = Triggers.self.dealsCombatDamage()
            effect = Effects.DrawCards(1)
        }
    }
    val suppressor = card("Test Damage Ability Suppressor") {
        typeLine = "Enchantment"
        staticAbility { ability = LoseAllAbilities(GroupFilter.AllCreatures) }
    }
    fun setup(): Pair<GameTestDriver, com.wingedsheep.sdk.model.EntityId> {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(source, suppressor))
        d.initMirrorMatch(com.wingedsheep.sdk.model.Deck.of("Plains" to 40))
        return d to d.putCreatureOnBattlefield(d.player1, source.name)
    }
    fun detector(d: GameTestDriver) = TriggerDetector(
        d.cardRegistry,
        conditionEvaluator = PredicateEvaluator(cardRegistry = null).conditions,
        predicateEvaluator = PredicateEvaluator(cardRegistry = null),
    )
    fun hits(d: GameTestDriver, event: DamageDealtEvent) = detector(d).detectTriggers(d.state, listOf(event))
        .filter { it.ability.trigger is EventPattern.DealsDamageEvent }

    for (dead in listOf(false, true)) {
        for (disabled in listOf(false, true)) {
            test("damage source intrinsic trigger: dead=$dead disabled=$disabled") {
                val (d, id) = setup()
                if (disabled) d.putPermanentOnBattlefield(d.player2, suppressor.name)
                d.state.projectedState.hasLostAllAbilities(id) shouldBe disabled
                val snapshot = captureLastKnown(d.state, id)
                if (dead) d.moveToGraveyard(id)
                hits(d, DamageDealtEvent(id, d.player2, 1, isCombatDamage = true,
                    targetIsPlayer = true, sourceLastKnown = snapshot)).size shouldBe if (disabled) 0 else 1
            }
        }
    }
    test("live source ability loss is respected without a damage snapshot") {
        val (d, id) = setup()
        d.putPermanentOnBattlefield(d.player2, suppressor.name)
        hits(d, DamageDealtEvent(id, d.player2, 1, isCombatDamage = true, targetIsPlayer = true)).size shouldBe 0
    }
    test("event time loss remains authoritative when its suppressor leaves before detection") {
        val (d, id) = setup()
        val aura = d.putPermanentOnBattlefield(d.player2, suppressor.name)
        val snapshot = captureLastKnown(d.state, id)
        d.moveToGraveyard(aura)
        d.state.projectedState.hasLostAllAbilities(id) shouldBe false
        hits(d, DamageDealtEvent(id, d.player2, 1, isCombatDamage = true,
            targetIsPlayer = true, sourceLastKnown = snapshot)).size shouldBe 0
    }
    test("an independently granted trigger survives removal of intrinsic abilities") {
        val (d, id) = setup()
        d.putPermanentOnBattlefield(d.player2, suppressor.name)
        val granted = source.script.triggeredAbilities.single().copy(id = com.wingedsheep.sdk.scripting.AbilityId("new-grant"))
        d.replaceState(d.state.copy(grantedTriggeredAbilities = listOf(GrantedTriggeredAbility(id, granted, Duration.Permanent))))
        val fired = hits(d, DamageDealtEvent(id, d.player2, 1, isCombatDamage = true, targetIsPlayer = true))
        fired.map { it.ability.id.value } shouldBe listOf("new-grant")
    }
})
