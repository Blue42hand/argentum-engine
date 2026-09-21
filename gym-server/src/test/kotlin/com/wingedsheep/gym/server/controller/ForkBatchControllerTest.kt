package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.server.dto.ForkBatchItem
import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ForkBatchControllerTest : FunSpec({
    test("empty fork batch maps to an empty response") {
        val controller = ForkBatchController(MultiEnvService(CardRegistry()))

        controller.forkBatch(emptyList()) shouldBe emptyList()
    }

    test("fork batch dto keeps source id and count") {
        val item = ForkBatchItem(EnvId("source"), count = 3)
        item.envId shouldBe EnvId("source")
        item.count shouldBe 3
    }
})
