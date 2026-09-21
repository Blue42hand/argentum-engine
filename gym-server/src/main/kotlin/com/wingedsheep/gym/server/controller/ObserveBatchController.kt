package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.server.dto.ObserveBatchItem
import com.wingedsheep.gym.server.dto.ObserveBatchResult
import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.ObserveRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Thin HTTP mapping for vectorized seat-safe observation. */
@RestController
@RequestMapping("/envs")
@Tag(name = "Environments")
class ObserveBatchController(
    private val multiEnvService: MultiEnvService
) {
    @Operation(
        summary = "Observe many envs in parallel",
        description = """
            Parallel counterpart to `GET /envs/{id}`. Results preserve request order. Each item may
            request a player perspective using the same seat-safe projection as singular observe.
            Repeated envIds are allowed because observation is read-only, including requests for
            multiple seat perspectives of one game.
        """
    )
    @PostMapping("/observe-batch")
    fun observeBatch(@RequestBody items: List<ObserveBatchItem>): List<ObserveBatchResult> {
        val requests = items.map { item ->
            ObserveRequest(item.envId, item.revealAll, item.perspectivePlayerId)
        }
        val results = multiEnvService.observeBatch(requests)
        return items.zip(results).map { (item, result) ->
            ObserveBatchResult(
                envId = result.first,
                perspectivePlayerId = item.perspectivePlayerId,
                observation = result.second.observation
            )
        }
    }
}
