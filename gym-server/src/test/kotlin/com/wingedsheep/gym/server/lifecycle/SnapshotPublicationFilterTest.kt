package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.server.config.createGymCardRegistry
import com.wingedsheep.gym.service.MultiEnvService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

class SnapshotPublicationFilterTest : FunSpec({
    val filter = SnapshotPublicationFilter(MultiEnvService(createGymCardRegistry()))

    fun request(method: String, path: String) = MockHttpServletRequest(method, path)

    test("singular and batch snapshot producers hold publication scopes") {
        filter.publishesNewSnapshots(request("POST", "/envs/env-1/snapshot")) shouldBe true
        filter.publishesNewSnapshots(request("POST", "/envs/snapshot-batch")) shouldBe true
    }

    test("non-producing snapshot lifecycle requests do not hold publication scopes") {
        filter.publishesNewSnapshots(request("POST", "/envs/env-1/restore")) shouldBe false
        filter.publishesNewSnapshots(request("DELETE", "/snapshots")) shouldBe false
        filter.publishesNewSnapshots(request("POST", "/envs/env-1/step")) shouldBe false
    }
})
