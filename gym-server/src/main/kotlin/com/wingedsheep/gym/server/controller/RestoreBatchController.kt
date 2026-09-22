package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.server.dto.RestoreBatchItem
import com.wingedsheep.gym.server.dto.RestoreBatchResult
import com.wingedsheep.gym.server.lifecycle.EnvLeaseManager
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.RestoreRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Thin HTTP mapping for vectorized snapshot restore. */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class RestoreBatchController(
    private val multiEnvService: MultiEnvService,
    private val leaseManager: EnvLeaseManager,
) {
    @Operation(
        summary = "Restore many envs from snapshots in parallel",
        description = """
            Parallel counterpart to `POST /envs/{id}/restore`. Results preserve request order and
            each environment keeps its existing envId. Duplicate envIds in one batch are rejected.
        """
    )
    @PostMapping("/restore-batch")
    fun restoreBatch(@RequestBody items: List<RestoreBatchItem>): List<RestoreBatchResult> =
        leaseManager.withLeases(items.map { it.envId }) {
            val requests = items.map { RestoreRequest(it.envId, it.handle) }
            multiEnvService.restoreBatch(requests).map { (envId, obs) ->
                RestoreBatchResult(envId, obs.observation)
            }
        }
}
