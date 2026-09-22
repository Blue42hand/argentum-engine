package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.service.DecisionRequest
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.server.dto.DecisionBatchItem
import com.wingedsheep.gym.server.dto.DecisionBatchResult
import com.wingedsheep.gym.server.lifecycle.EnvLeaseManager
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Batched structured-decision façade kept separate from [EnvController] so the HTTP mapping stays
 * thin: transport DTOs are converted directly into the transport-agnostic service request type.
 */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class DecisionBatchController(
    private val multiEnvService: MultiEnvService,
    private val leaseManager: EnvLeaseManager,
) {
    @Operation(
        summary = "Submit structured decisions to many envs in parallel",
        description = """
            Parallel counterpart to `POST /envs/{id}/decision`. Results preserve request order.
            Each env remains single-threaded: callers must not overlap another operation naming the
            same envId while its batch item is running. Optional `expectedStateDigest` entries reject
            responses produced from stale observations before they reach authoritative decision
            validation.
        """
    )
    @PostMapping("/decision-batch")
    fun submitDecisionBatch(@RequestBody items: List<DecisionBatchItem>): List<DecisionBatchResult> =
        leaseManager.withLeases(items.map { it.envId }) {
            val requests = items.map {
                DecisionRequest(it.envId, it.response, it.expectedStateDigest)
            }
            multiEnvService.submitDecisionBatch(requests).map { (envId, obs) ->
                DecisionBatchResult(envId, obs.observation)
            }
        }
}
