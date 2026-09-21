package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RestoreBatchControllerTest : FunSpec({
    test("empty restore batch maps to an empty response") {
        val controller = RestoreBatchController(MultiEnvService(CardRegistry()))

        controller.restoreBatch(emptyList()) shouldBe emptyList()
    }
})
