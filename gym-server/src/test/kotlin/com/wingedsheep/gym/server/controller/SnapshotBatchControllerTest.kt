package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SnapshotBatchControllerTest : FunSpec({
    test("empty snapshot batch maps to an empty response") {
        val controller = SnapshotBatchController(MultiEnvService(CardRegistry()))

        controller.snapshotBatch(emptyList()) shouldBe emptyList()
    }
})
