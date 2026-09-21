package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.server.dto.SnapshotBatchResult
import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.MultiEnvService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Thin HTTP mapping for vectorized snapshot capture. */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class SnapshotBatchController(
    private val multiEnvService: MultiEnvService
) {
    @Operation(
        summary = "Capture snapshots for many envs in parallel",
        description = """
            Parallel counterpart to `POST /envs/{id}/snapshot`. Results preserve request order.
            Duplicate envIds in one batch are rejected to avoid retaining duplicate snapshot slots
            for the same branch point.
        """
    )
    @PostMapping("/snapshot-batch")
    fun snapshotBatch(@RequestBody envIds: List<EnvId>): List<SnapshotBatchResult> =
        multiEnvService.snapshotBatch(envIds).map { (envId, handle) ->
            SnapshotBatchResult(envId, handle)
        }
}
