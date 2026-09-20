package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.SnapshotCodec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SnapshotControllerTest : FunSpec({
    test("dispose releases the supplied snapshot handle") {
        val codec = SnapshotCodec()
        val service = MultiEnvService(CardRegistry(), snapshotCodec = codec)
        val handle = codec.save(GameState(), emptyList(), 0)
        val controller = SnapshotController(service)

        controller.dispose(handle).statusCode.value() shouldBe 204
        codec.size() shouldBe 0
    }
})
