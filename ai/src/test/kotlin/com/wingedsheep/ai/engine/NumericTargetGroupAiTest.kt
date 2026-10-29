package com.wingedsheep.ai.engine

import com.wingedsheep.engine.legalactions.TargetInfo
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.handlers.actions.decision.DecisionValidators
import com.wingedsheep.ai.engine.rollout.FastDecisionResponder
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.values.CardNumericProperty
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NumericTargetGroupAiTest : FunSpec({
    test("required multi-target picks come from a compatible group even if top candidates differ") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(Deck.of("Forest" to 40))
        val a = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val b = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val c = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val info = TargetInfo(0, "two creatures with equal toughness", 2, 2, listOf(a, b, c),
            selectionGroups = listOf(listOf(a, b), listOf(c)))
        TargetSelection.pick(d.state, info, info.validTargets) { if (it == c) 100.0 else 1.0 }.toSet() shouldBe setOf(a, b)
    }
    test("minimal, rollout and full pending responders never mix equality groups") {
        val d = GameTestDriver()
        d.registerCards(TestCards.all)
        d.initMirrorMatch(Deck.of("Forest" to 40))
        val a = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val c = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val b = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val e = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val decision = ChooseTargetsDecision("equality", d.player1, "Choose equal toughness", DecisionContext(),
            listOf(TargetRequirementInfo(0, "two", 2, 2, selectionGroups = listOf(listOf(a, b), listOf(c, e)))),
            mapOf(0 to listOf(a, c, b, e)))
        val registry = CardRegistry().apply { register(TestCards.all) }
        val responses = listOf(
            TargetsResponse(decision.id, decision.minimalSelection()),
            FastDecisionResponder().respond(d.state, decision, d.player1),
            DecisionResponder(GameSimulator(registry), AIPlayer.defaultEvaluator()).respond(d.state, decision, d.player1),
        )
        responses.forEach { response ->
            DecisionValidators.validate(decision, response, d.state) shouldBe null
            (response as TargetsResponse).selectedTargets.getValue(0).size shouldBe 2
        }
    }

    test("optional equality decisions consider profitable subsets of a group across controllers") {
        val source = card("Equality Removal Test") {
            manaCost = "{0}"
            typeLine = "Enchantment"
            triggeredAbility {
                trigger = Triggers.self.enters()
                targets(TargetFilter.Creature, unlimited = true, sameNumericProperty = CardNumericProperty.TOUGHNESS)
                effect = Effects.Pipeline { val chosen = gather(CardSource.ChosenTargets); destroy(chosen) }
            }
        }
        val d = GameTestDriver()
        val registry = CardRegistry().apply { register(TestCards.all + source) }
        d.registerCards(TestCards.all + source)
        d.initMirrorMatch(Deck.of("Forest" to 40))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val p = d.activePlayer!!
        val enemy = d.putCreatureOnBattlefield(d.getOpponent(p), "Grizzly Bears")
        repeat(3) { d.putCreatureOnBattlefield(p, "Grizzly Bears") }
        d.castSpell(p, d.putCardInHand(p, source.name)).error shouldBe null
        d.bothPass()
        val decision = d.state.pendingDecision as ChooseTargetsDecision
        val response = DecisionResponder(GameSimulator(registry), AIPlayer.defaultEvaluator(), fillUpToMaxTargets = true)
            .respond(d.state, decision, p) as TargetsResponse
        response.selectedTargets.getValue(0) shouldBe listOf(enemy)
        DecisionValidators.validate(decision, response, d.state) shouldBe null
    }

})
