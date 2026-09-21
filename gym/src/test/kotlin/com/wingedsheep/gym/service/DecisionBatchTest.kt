package com.wingedsheep.gym.service

import com.wingedsheep.engine.registry.CardRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DecisionBatchTest : FunSpec({
    test("submitDecisionBatch with an empty request list returns an empty list") {
        val service = MultiEnvService(CardRegistry())

        service.submitDecisionBatch(emptyList()) shouldBe emptyList()
    }
})