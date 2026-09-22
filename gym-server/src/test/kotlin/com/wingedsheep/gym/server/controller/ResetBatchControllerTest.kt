package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.server.lifecycle.EnvLeaseManager
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ResetBatchControllerTest : FunSpec({
    test("empty reset batch maps to an empty response") {
        val service = MultiEnvService(CardRegistry())
        val controller = ResetBatchController(service, EnvLeaseManager(service, ttlMs = 0))

        controller.resetBatch(emptyList()) shouldBe emptyList()
    }
})
