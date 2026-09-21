package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DecisionBatchControllerTest : FunSpec({
    test("empty decision batch maps to an empty response") {
        val controller = DecisionBatchController(MultiEnvService(CardRegistry()))

        controller.submitDecisionBatch(emptyList()) shouldBe emptyList()
    }
})