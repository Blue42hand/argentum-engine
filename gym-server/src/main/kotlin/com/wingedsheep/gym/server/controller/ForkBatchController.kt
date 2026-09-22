package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.server.dto.ForkBatchItem
import com.wingedsheep.gym.server.dto.ForkBatchResult
import com.wingedsheep.gym.server.lifecycle.EnvLeaseManager
import com.wingedsheep.gym.service.ForkRequest
import com.wingedsheep.gym.service.MultiEnvService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Thin HTTP mapping for vectorized environment branching. */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class ForkBatchController(
    private val multiEnvService: MultiEnvService,
    private val leaseManager: EnvLeaseManager,
) {
    @Operation(
        summary = "Fork many source envs in parallel",
        description = """
            Parallel counterpart to `POST /envs/{id}/fork`. Each item may request multiple children
            from one source. Results preserve source request order and child order. Duplicate source
            envIds are rejected before any children are allocated.
        """
    )
    @PostMapping("/fork-batch")
    fun forkBatch(@RequestBody items: List<ForkBatchItem>): List<ForkBatchResult> =
        leaseManager.withLeases(items.map { it.envId }) {
            multiEnvService.forkBatch(items.map { ForkRequest(it.envId, it.count) }).map { (envId, children) ->
                ForkBatchResult(envId, children)
            }
        }
}
