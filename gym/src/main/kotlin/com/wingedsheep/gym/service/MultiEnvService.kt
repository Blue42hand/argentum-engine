package com.wingedsheep.gym.service

import com.wingedsheep.engine.limited.BoosterGenerator
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.gym.GameGymEnv
import com.wingedsheep.gym.GymEnv
import com.wingedsheep.gym.contract.ObservationResult
import com.wingedsheep.gym.deckbuild.DeckbuildEnvironment
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.EntityId
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/**
 * In-JVM registry for many concurrent gym environments. Transport-agnostic:
 * exposes create / reset / observe / step / fork / snapshot / dispose methods
 * that HTTP, gRPC, or an in-process training loop can call directly.
 *
 * Environments are polymorphic ([GymEnv]): game envs ([GameGymEnv]) and deckbuild
 * envs ([DeckbuildEnvironment]) share the observe/step/fork surface. Operations that
 * only make sense for a game (decision submission, snapshot/restore, reset) require a
 * [GameGymEnv] and reject other env kinds.
 *
 * ## Threading
 *
 * Each env is single-threaded. [MultiEnvService] serializes operations that name the
 * same [EnvId], including singular calls racing a batch item, while different envs run
 * independently in parallel via [observeBatch], [resetBatch], [stepBatch], [forkBatch],
 * [snapshotBatch], [restoreBatch], or [submitDecisionBatch]. Read-only [observeBatch] may intentionally
 * name the same env more than once (for example to request multiple seat perspectives); those items
 * serialize on that env. This keeps mutable per-env state and action registries race-free without
 * imposing a global lock.
 *
 * ## Registry regeneration
 *
 * Every `reset` / `step` rebuilds the env's action mapping. Action IDs from a
 * previous step are invalidated. Remote/asynchronous callers can additionally set
 * [StepRequest.expectedStateDigest] to make stale-action rejection explicit even if
 * an integer action ID happens to be reused by the newly-built registry.
 */
class MultiEnvService(
    val cardRegistry: CardRegistry,
    boosterGenerator: BoosterGenerator? = null,
    val workerPool: EnvWorkerPool = EnvWorkerPool(),
    val snapshotCodec: SnapshotCodec = SnapshotCodec()
) {
    private val envs = ConcurrentHashMap<EnvId, GymEnv>()
    val deckResolver: DeckResolver = DeckResolver(cardRegistry, boosterGenerator)

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Create a new game env and run `reset` immediately. The returned observation
     * is the opening state (post-mulligan if [EnvConfig.skipMulligans]).
     */
    fun create(config: EnvConfig): CreatedEnv {
        val gameConfig = config.toGameConfig()
        val env = GameEnvironment.create(cardRegistry)
        env.reset(gameConfig)
        val gymEnv = GameGymEnv(env, config.perspectivePlayerIndex, config.revealAll)
        val observation = gymEnv.observe()
        val envId = EnvId.generate()
        envs[envId] = gymEnv
        return CreatedEnv(envId, observation)
    }

    /**
     * Create a new **deckbuild** env: open a sealed pool from [DeckbuildConfig.setCode]
     * and hand the agent the build interface. The opening observation lists the pool.
     */
    fun createDeckbuild(config: DeckbuildConfig): CreatedEnv {
        val sealed = deckResolver.openSealedPool(config.setCode, config.boosterCount)
        val env = DeckbuildEnvironment(
            pool = sealed.pool,
            basics = sealed.basics,
            targetSize = config.targetSize
        )
        val observation = env.observe()
        val envId = EnvId.generate()
        envs[envId] = env
        return CreatedEnv(envId, observation)
    }

    /** Reset an existing game env while keeping the same [EnvId]. */
    fun reset(envId: EnvId, config: EnvConfig): ObservationResult =
        withGameEnv(envId) { it.reset(config.toGameConfig()) }

    /** Reset N distinct game envs in parallel while preserving each [EnvId]. */
    fun resetBatch(requests: List<ResetRequest>): List<Pair<EnvId, ObservationResult>> {
        if (requests.isEmpty()) return emptyList()
        requireDistinctEnvIds("reset", requests.map { it.envId })
        val tasks = requests.map { req ->
            Callable {
                withBatchContext("reset", req.envId) {
                    req.envId to reset(req.envId, req.config)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    /** Drop envs from the registry. Idempotent and ordered after in-flight operations. */
    fun dispose(envIds: Collection<EnvId>) {
        envIds.forEach { envId ->
            val env = envs[envId] ?: return@forEach
            synchronized(env) {
                envs.remove(envId, env)
            }
        }
    }

    fun listEnvs(): Set<EnvId> = envs.keys.toSet()

    // =========================================================================
    // Observations / stepping
    // =========================================================================

    /** Get the current observation without advancing state. */
    fun observe(
        envId: EnvId,
        revealAll: Boolean? = null,
        perspectivePlayerId: EntityId? = null
    ): ObservationResult = withEnv(envId) { env ->
        if (perspectivePlayerId == null) {
            env.observe(revealAll)
        } else {
            (env as? GameGymEnv
                ?: throw IllegalStateException(
                    "Env $envId is not a game env; player perspective is not supported"
                )).observeForPlayer(perspectivePlayerId, revealAll)
        }
    }

    /**
     * Observe N environments in parallel without advancing them. Items preserve request order and
     * delegate to [observe], so seat validation and hidden-information projection stay authoritative
     * in one place. Unlike mutating batches, repeated env IDs are allowed so callers can request
     * multiple player perspectives of one game in a single round-trip.
     */
    fun observeBatch(requests: List<ObserveRequest>): List<Pair<EnvId, ObservationResult>> {
        if (requests.isEmpty()) return emptyList()
        val tasks = requests.map { req ->
            Callable {
                withBatchContext("observe", req.envId) {
                    req.envId to observe(req.envId, req.revealAll, req.perspectivePlayerId)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    /**
     * Advance a single env by the given [StepRequest.actionId]. The ID must
     * come from the most-recent observation for that env. If [StepRequest.expectedStateDigest]
     * is supplied, verify it against the digest paired with that same observation/action mapping
     * before resolving the action ID. Do not rebuild an observation here: game observations can be
     * seat-specific, and rebuilding from the configured default perspective would compare a
     * different information set and replace the registry the submitted action ID belongs to.
     */
    fun step(request: StepRequest): ObservationResult =
        withEnv(request.envId) { env ->
            request.expectedStateDigest?.let { expected ->
                val actual = checkNotNull(env.actionStateDigest) {
                    "Env ${request.envId} has no action-producing observation"
                }
                check(actual == expected) {
                    "Stale step for env ${request.envId}: expected stateDigest=$expected, current=$actual"
                }
            }
            env.step(request.actionId, request.params)
        }

    /** Advance N distinct envs in parallel; each env is serialized against other calls naming it. */
    fun stepBatch(requests: List<StepRequest>): List<Pair<EnvId, ObservationResult>> {
        if (requests.isEmpty()) return emptyList()
        requireDistinctEnvIds("step", requests.map { it.envId })
        val tasks = requests.map { req ->
            Callable {
                withBatchContext("step", req.envId) {
                    req.envId to step(req)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    /**
     * Submit a raw `DecisionResponse` for a game env paused on a complex pending
     * decision. Simple decisions are driven via [step] with a folded action ID. Optional
     * [expectedStateDigest] rejects a delayed response before it reaches the engine.
     */
    fun submitDecision(
        envId: EnvId,
        response: DecisionResponse,
        expectedStateDigest: String? = null
    ): ObservationResult =
        withGameEnv(envId) { it.submitDecision(response, expectedStateDigest) }

    /**
     * Submit structured decisions to N game envs in parallel. Results preserve request order,
     * matching [stepBatch]. Duplicate env IDs are rejected; independent calls naming one of the
     * same envs are serialized at the service boundary.
     */
    fun submitDecisionBatch(requests: List<DecisionRequest>): List<Pair<EnvId, ObservationResult>> {
        if (requests.isEmpty()) return emptyList()
        requireDistinctEnvIds("decision", requests.map { it.envId })
        val tasks = requests.map { req ->
            Callable {
                withBatchContext("decision", req.envId) {
                    req.envId to submitDecision(req.envId, req.response, req.expectedStateDigest)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    // =========================================================================
    // Fork / snapshot / restore
    // =========================================================================

    /** Fork an env N times. Children diverge independently from the next step on. */
    fun fork(srcEnvId: EnvId, count: Int = 1): List<EnvId> {
        require(count > 0) { "fork count must be positive" }
        return withEnv(srcEnvId) { src ->
            List(count) {
                val newId = EnvId.generate()
                envs[newId] = src.fork()
                newId
            }
        }
    }

    /**
     * Fork multiple distinct source envs in parallel. Each result preserves the source request order
     * and the child order/count returned by singular [fork]. Duplicate source env IDs are rejected
     * before scheduling so callers cannot accidentally expand the same branch point twice.
     */
    fun forkBatch(requests: List<ForkRequest>): List<Pair<EnvId, List<EnvId>>> {
        if (requests.isEmpty()) return emptyList()
        requireDistinctEnvIds("fork", requests.map { it.envId })
        val tasks = requests.map { req ->
            Callable {
                withBatchContext("fork", req.envId) {
                    req.envId to fork(req.envId, req.count)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    fun snapshot(envId: EnvId): SnapshotHandle =
        withGameEnv(envId) { it.snapshot(snapshotCodec) }

    /**
     * Capture snapshots for N distinct game envs in parallel. Duplicate env IDs are rejected so a
     * caller cannot accidentally retain multiple snapshot slots for the same branch point.
     */
    fun snapshotBatch(envIds: List<EnvId>): List<Pair<EnvId, SnapshotHandle>> {
        if (envIds.isEmpty()) return emptyList()
        requireDistinctEnvIds("snapshot", envIds)
        val tasks = envIds.map { envId ->
            Callable {
                withBatchContext("snapshot", envId) {
                    envId to snapshot(envId)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    /** Restore a game env to a previously-snapshotted state. */
    fun restore(envId: EnvId, handle: SnapshotHandle): ObservationResult =
        withGameEnv(envId) { it.restore(snapshotCodec, handle) }

    /** Restore N distinct game envs in parallel while preserving request order and env IDs. */
    fun restoreBatch(requests: List<RestoreRequest>): List<Pair<EnvId, ObservationResult>> {
        if (requests.isEmpty()) return emptyList()
        requireDistinctEnvIds("restore", requests.map { it.envId })
        val tasks = requests.map { req ->
            Callable {
                withBatchContext("restore", req.envId) {
                    req.envId to restore(req.envId, req.handle)
                }
            }
        }
        return workerPool.invokeAll(tasks)
    }

    /** Release a snapshot slot so long-lived trainers do not retain old game states indefinitely. */
    fun disposeSnapshot(handle: SnapshotHandle) {
        snapshotCodec.dispose(handle)
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private fun EnvConfig.toGameConfig(): GameConfig = GameConfig(
        players = players.map { spec ->
            PlayerConfig(
                name = spec.name,
                deck = deckResolver.resolve(spec.deck),
                startingLife = spec.startingLife,
                playerId = spec.playerId,
                commanderCardName = spec.commanderCardName
            )
        },
        startingHandSize = startingHandSize,
        skipMulligans = skipMulligans,
        useHandSmoother = useHandSmoother,
        startingPlayerIndex = startingPlayerIndex,
        format = format,
        seed = seed
    )

    /**
     * Run one operation under the environment's own monitor. Re-check the registry after taking the
     * monitor so a request that fetched an env just before [dispose] cannot operate on it afterward.
     */
    private inline fun <T> withEnv(envId: EnvId, block: (GymEnv) -> T): T {
        val env = requireEnv(envId)
        return synchronized(env) {
            if (envs[envId] !== env) {
                throw NoSuchElementException("Unknown envId: $envId")
            }
            block(env)
        }
    }

    private inline fun <T> withGameEnv(envId: EnvId, block: (GameGymEnv) -> T): T =
        withEnv(envId) { env ->
            block(
                env as? GameGymEnv
                    ?: throw IllegalStateException("Env $envId is not a game env; operation not supported")
            )
        }

    /**
     * Preserve the singular operation's error category while identifying which batch item failed.
     * The HTTP layer maps these three exception types to 404 / 400 / 409 respectively, so changing
     * their type here would make a batched request behave differently from the equivalent singular
     * request. Unexpected failures are left untouched and remain server errors.
     */
    private inline fun <T> withBatchContext(operation: String, envId: EnvId, block: () -> T): T {
        val prefix = "$operation batch item envId=$envId failed"
        return try {
            block()
        } catch (e: NoSuchElementException) {
            throw NoSuchElementException("$prefix: ${e.message}").also { it.initCause(e) }
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$prefix: ${e.message}", e)
        } catch (e: IllegalStateException) {
            throw IllegalStateException("$prefix: ${e.message}", e)
        }
    }

    private fun requireDistinctEnvIds(operation: String, envIds: List<EnvId>) {
        val seen = HashSet<EnvId>(envIds.size)
        val duplicate = envIds.firstOrNull { !seen.add(it) }
        require(duplicate == null) {
            "$operation batch contains duplicate envId: $duplicate"
        }
    }

    private fun requireEnv(envId: EnvId): GymEnv =
        envs[envId] ?: throw NoSuchElementException("Unknown envId: $envId")
}

/** Result of [MultiEnvService.create] — the new env's ID plus its opening observation. */
data class CreatedEnv(
    val envId: EnvId,
    val observation: ObservationResult
)

/** One read-only observation request for [MultiEnvService.observeBatch]. */
data class ObserveRequest(
    val envId: EnvId,
    val revealAll: Boolean? = null,
    val perspectivePlayerId: EntityId? = null
)

/** One reset request for [MultiEnvService.resetBatch]. */
data class ResetRequest(
    val envId: EnvId,
    val config: EnvConfig
)

/** One fork request for [MultiEnvService.forkBatch]. */
data class ForkRequest(
    val envId: EnvId,
    val count: Int = 1
)

/** One snapshot-restore request for [MultiEnvService.restoreBatch]. */
data class RestoreRequest(
    val envId: EnvId,
    val handle: SnapshotHandle
)

/** One structured-decision submission for [MultiEnvService.submitDecisionBatch]. */
data class DecisionRequest(
    val envId: EnvId,
    val response: DecisionResponse,
    val expectedStateDigest: String? = null
)
