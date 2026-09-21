package com.wingedsheep.gym.server.dto

import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.gym.contract.ActionParams
import com.wingedsheep.gym.contract.Observation
import com.wingedsheep.gym.service.EnvConfig
import com.wingedsheep.gym.service.EnvId
import com.wingedsheep.gym.service.SnapshotHandle
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Response for `POST /envs` (and `POST /envs/deckbuild`). Combines the new env's ID
 * with its opening observation so a caller only has to round-trip once to start.
 * The [observation] is a discriminated union — `TrainingObservation` for a game env,
 * `DeckbuildObservation` for a deckbuild env (see the `type` field).
 */
@Serializable
data class CreateEnvResponse(
    val envId: EnvId,
    val observation: Observation
)

/**
 * Body for `POST /envs/{id}/step`.
 *
 * [params] completes an action whose enumerated form is a template — which creatures attack and
 * whom, which blocks are made, a spell's targets, X. Omit it for actions that need no choice beyond
 * their ID; see [ActionParams] for what is (and isn't) expressible here.
 *
 * [expectedStateDigest] is an optional optimistic-concurrency guard. When supplied, the service
 * rejects the step if the current authoritative observation no longer has that digest, preventing
 * a delayed per-observation action ID from being reused against newer state.
 */
@Serializable
data class StepBody(
    val actionId: Int,
    val params: ActionParams = ActionParams.EMPTY,
    val expectedStateDigest: String? = null
)

/** Single entry for `POST /envs/observe-batch`. */
@Serializable
data class ObserveBatchItem(
    val envId: EnvId,
    val revealAll: Boolean? = null,
    val perspectivePlayerId: EntityId? = null
)

/** Result entry for `POST /envs/observe-batch`. */
@Serializable
data class ObserveBatchResult(
    val envId: EnvId,
    val perspectivePlayerId: EntityId? = null,
    val observation: Observation
)

/** Single entry for `POST /envs/reset-batch`. */
@Serializable
data class ResetBatchItem(
    val envId: EnvId,
    val config: EnvConfig
)

/** Result entry for `POST /envs/reset-batch`. */
@Serializable
data class ResetBatchResult(
    val envId: EnvId,
    val observation: Observation
)

/** Single entry for `POST /envs/step-batch`. */
@Serializable
data class StepBatchItem(
    val envId: EnvId,
    val actionId: Int,
    val params: ActionParams = ActionParams.EMPTY,
    val expectedStateDigest: String? = null
)

/** Result entry for `POST /envs/step-batch`. */
@Serializable
data class StepBatchResult(
    val envId: EnvId,
    val observation: Observation
)

/**
 * Single entry for `POST /envs/decision-batch`.
 * [expectedStateDigest] optionally binds the response to the observation that exposed the pending
 * decision, matching the stale-state protection available for ordinary actions.
 */
@Serializable
data class DecisionBatchItem(
    val envId: EnvId,
    val response: DecisionResponse,
    val expectedStateDigest: String? = null
)

/** Result entry for `POST /envs/decision-batch`. */
@Serializable
data class DecisionBatchResult(
    val envId: EnvId,
    val observation: Observation
)

/** Result entry for `POST /envs/snapshot-batch`. */
@Serializable
data class SnapshotBatchResult(
    val envId: EnvId,
    val handle: SnapshotHandle
)

/** Body for `POST /envs/{id}/restore`. */
@Serializable
data class RestoreBody(val handle: SnapshotHandle)

/** Single entry for `POST /envs/restore-batch`. */
@Serializable
data class RestoreBatchItem(
    val envId: EnvId,
    val handle: SnapshotHandle
)

/** Result entry for `POST /envs/restore-batch`. */
@Serializable
data class RestoreBatchResult(
    val envId: EnvId,
    val observation: Observation
)

/** Body for `DELETE /envs`. List of envs to dispose. */
@Serializable
data class DisposeBody(val envIds: List<EnvId>)

/** Response for `GET /schema-hash` and `GET /health`. */
@Serializable
data class SchemaHashResponse(val schemaHash: String)

@Serializable
data class HealthResponse(val status: String = "ok")

/** Inspectable service identity for persistent/trainer deployments. */
@Serializable
data class ServiceStatusResponse(
    val status: String = "ok",
    val service: String = "argentum-gym-server",
    val schemaHash: String,
    val buildRevision: String
)

/** Shared error envelope for `@ExceptionHandler` responses. */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)