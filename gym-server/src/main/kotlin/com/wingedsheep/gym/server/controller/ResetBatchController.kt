package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.ResetRequest
import com.wingedsheep.gym.server.dto.ResetBatchItem
import com.wingedsheep.gym.server.dto.ResetBatchResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Thin HTTP mapping for vectorized episode reset. */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class ResetBatchController(
    private val multiEnvService: MultiEnvService
) {
    @Operation(
        summary = "Reset many envs in parallel",
        description = """
            Parallel counterpart to `POST /envs/{id}/reset`. Results preserve request order and
            each environment keeps its existing envId. Duplicate envIds in one batch are rejected.
        """
    )
    @PostMapping("/reset-batch")
    fun resetBatch(@RequestBody items: List<ResetBatchItem>): List<ResetBatchResult> {
        val requests = items.map { ResetRequest(it.envId, it.config) }
        return multiEnvService.resetBatch(requests).map { (envId, obs) ->
            ResetBatchResult(envId, obs.observation)
        }
    }
}