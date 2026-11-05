package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.rollout.FastDecisionResponder
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.handlers.actions.decision.DecisionValidators
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CounterAllocationTest : FunSpec({
    val player = EntityId.of("player")
    val first = EntityId.of("first")
    val second = EntityId.of("second")
    fun decision(firstCap: Int, secondCap: Int, minimum: Int = 0) = SelectCardsDecision(
        id = "counter-payment", playerId = player, prompt = "Remove three counters?",
        context = DecisionContext(), options = listOf(first, second),
        minSelections = minimum, maxSelections = 3, selectionLimits = mapOf(first to firstCap, second to secondCap)
    )
    test("allocation pays with repeated IDs on one permanent or across permanents") {
        for ((d, expected) in listOf(
            decision(3, 0) to listOf(first, first, first),
            decision(1, 2) to listOf(first, second, second)
        )) {
            val allocation = CounterAllocation.fullPayment(d)
            allocation shouldBe expected
            DecisionValidators.validate(d, CardsSelectedResponse(d.id, allocation!!)) shouldBe null
        }
    }
    test("insufficient capacity does not return a partial illegal payment") {
        CounterAllocation.fullPayment(decision(1, 1)) shouldBe null
    }
    test("fast responders keep optional costs declined and pay required costs in full") {
        for (minimum in listOf(0, 3)) {
            val d = decision(1, 2, minimum)
            val answer = FastDecisionResponder().respond(GameState(), d, player) as CardsSelectedResponse
            answer.selectedCards shouldBe if (minimum == 0) emptyList() else listOf(first, second, second)
            DecisionValidators.validate(d, answer) shouldBe null
        }
    }
})
