package com.wingedsheep.gym.server.controller

import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.gym.contract.Observation
import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.MultiEnvService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Adds the optional optimistic-concurrency form of the existing raw structured-decision route
 * without changing its `DecisionResponse` request body. Spring prefers this mapping when the
 * `expectedStateDigest` query parameter is present; requests without it continue through
 * [EnvController.submitDecision].
 */
@RestController
@RequestMapping("/envs")
class DecisionFreshnessController(
    private val multiEnvService: MultiEnvService
) {
    @PostMapping("/{id}/decision", params = ["expectedStateDigest"])
    fun submitDecisionWithDigest(
        @PathVariable id: String,
        @RequestParam expectedStateDigest: String,
        @RequestBody response: DecisionResponse
    ): Observation =
        multiEnvService.submitDecision(
            EnvId(id),
            response,
            expectedStateDigest
        ).observation
}
