package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.server.lifecycle.EnvLeaseManager
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ObserveBatchControllerTest : FunSpec({
    test("empty observe batch maps to an empty response") {
        val service = MultiEnvService(CardRegistry())
        val controller = ObserveBatchController(service, EnvLeaseManager(service, ttlMs = 0))

        controller.observeBatch(emptyList()) shouldBe emptyList()
    }
})
