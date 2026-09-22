package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

class SnapshotPublicationFilterTest : FunSpec({
    val filter = SnapshotPublicationFilter(MultiEnvService(CardRegistry()))

    fun request(method: String, path: String) = MockHttpServletRequest(method, path)

    test("singular snapshot producers hold publication scopes") {
        filter.publishesNewSnapshots(request("POST", "/envs/env-1/snapshot")) shouldBe true
    }

    test("non-producing lifecycle requests do not hold publication scopes") {
        filter.publishesNewSnapshots(request("POST", "/envs/env-1/restore")) shouldBe false
        filter.publishesNewSnapshots(request("DELETE", "/snapshots")) shouldBe false
        filter.publishesNewSnapshots(request("POST", "/envs/env-1/step")) shouldBe false
    }
})
