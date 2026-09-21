package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ResetBatchControllerTest : FunSpec({
    test("empty reset batch maps to an empty response") {
        val controller = ResetBatchController(MultiEnvService(CardRegistry()))

        controller.resetBatch(emptyList()) shouldBe emptyList()
    }
})