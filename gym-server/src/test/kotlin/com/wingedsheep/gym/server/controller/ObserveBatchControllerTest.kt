package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ObserveBatchControllerTest : FunSpec({
    test("empty observe batch maps to an empty response") {
        val controller = ObserveBatchController(MultiEnvService(CardRegistry()))

        controller.observeBatch(emptyList()) shouldBe emptyList()
    }
})
