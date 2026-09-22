package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.service.EnvId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Renews Gym environment leases at the HTTP boundary without changing the Gym wire contract.
 *
 * Singular `/envs/{id}/...` requests infer their environment automatically. Batch callers should
 * send `X-Argentum-Gym-Env-Ids` with a comma-separated list of every environment participating in
 * the request. The header may also be used on singular requests and is de-duplicated with the path.
 */
@Component
class EnvLeaseFilter(
    private val leaseManager: EnvLeaseManager,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!leaseManager.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val envIds = envIdsFor(request)
        leaseManager.begin(envIds)
        try {
            filterChain.doFilter(request, response)
        } finally {
            leaseManager.end(envIds)
        }
    }

    internal fun envIdsFor(request: HttpServletRequest): Set<EnvId> {
        val ids = linkedSetOf<EnvId>()

        request.getHeader(LEASE_HEADER)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.mapTo(ids, ::EnvId)

        val path = request.requestURI.removePrefix(request.contextPath)
        val segments = path.split('/').filter(String::isNotEmpty)
        if (segments.size >= 2 && segments[0] == "envs" && segments[1] !in RESERVED_ENV_PATHS) {
            ids += EnvId(segments[1])
        }

        return ids
    }

    companion object {
        const val LEASE_HEADER = "X-Argentum-Gym-Env-Ids"

        private val RESERVED_ENV_PATHS = setOf(
            "create-batch",
            "deckbuild",
            "decision-batch",
            "fork-batch",
            "observe-batch",
            "reset-batch",
            "restore-batch",
            "snapshot-batch",
            "step-batch",
        )
    }
}
