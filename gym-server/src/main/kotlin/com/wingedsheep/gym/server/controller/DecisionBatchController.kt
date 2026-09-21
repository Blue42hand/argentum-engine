package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.service.DecisionRequest
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.server.dto.DecisionBatchItem
import com.wingedsheep.gym.server.dto.DecisionBatchResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Thin HTTP façade for batched structured decisions. */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class DecisionBatchController(
    private val multiEnvService: MultiEnvService
) {
    @Operation(
        summary = "Submit structured decisions to many envs in parallel",
        description = """
            Parallel counterpart to `POST /envs/{id}/decision`. Results preserve request order.
            Each env remains single-threaded: callers must not overlap another operation naming the
            same envId while its batch item is running.
        """
    )
    @PostMapping("/decision-batch")
    fun submitDecisionBatch(@RequestBody items: List<DecisionBatchItem>): List<DecisionBatchResult> {
        val requests = items.map { DecisionRequest(it.envId, it.response) }
        return multiEnvService.submitDecisionBatch(requests).map { (envId, obs) ->
            DecisionBatchResult(envId, obs.observation)
        }
    }
}
