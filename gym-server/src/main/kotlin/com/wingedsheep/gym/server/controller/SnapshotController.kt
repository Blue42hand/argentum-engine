package com.wingedsheep.gym.server.controller

import com.wingedsheep.gym.service.MultiEnvService
import com.wingedsheep.gym.service.SnapshotHandle
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP lifecycle operations for snapshots that are not scoped to a single environment. */
@RestController
@RequestMapping("/snapshots")
@Tag(name = "Snapshots", description = "Release Gym snapshots when a trainer no longer needs them.")
class SnapshotController(
    private val multiEnvService: MultiEnvService
) {
    @Operation(
        summary = "Dispose a snapshot",
        description = "Releases the snapshot's retained immutable game state. Idempotent for an already-disposed handle."
    )
    @DeleteMapping
    fun dispose(@RequestBody handle: SnapshotHandle): ResponseEntity<Unit> {
        multiEnvService.disposeSnapshot(handle)
        return ResponseEntity.noContent().build()
    }
}
