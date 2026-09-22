package com.wingedsheep.gym.server.lifecycle

import com.wingedsheep.gym.service.MultiEnvService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Protects newly-created snapshot handles until their HTTP response has been published. */
@Component
class SnapshotPublicationFilter(
    private val multiEnvService: MultiEnvService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (publishesNewSnapshots(request)) {
            multiEnvService.snapshotCodec.withPublication {
                filterChain.doFilter(request, response)
            }
        } else {
            filterChain.doFilter(request, response)
        }
    }

    internal fun publishesNewSnapshots(request: HttpServletRequest): Boolean {
        if (!request.method.equals("POST", ignoreCase = true)) return false

        val path = request.requestURI.removePrefix(request.contextPath)
        val segments = path.split('/').filter(String::isNotEmpty)
        return segments == listOf("envs", "snapshot-batch") ||
            (segments.size == 3 && segments[0] == "envs" && segments[2] == "snapshot")
    }
}
