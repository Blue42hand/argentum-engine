package com.wingedsheep.gameserver.repository

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gameserver.config.RedisProperties
import com.wingedsheep.gameserver.persistence.dto.PersistentGameSession
import com.wingedsheep.gameserver.persistence.persistenceJson
import com.wingedsheep.gameserver.persistence.restoreGameSession
import com.wingedsheep.gameserver.persistence.toPersistent
import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.session.PlayerIdentity
import com.wingedsheep.gameserver.session.SessionRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis-backed implementation of GameRepository.
 *
 * Game sessions are persisted to Redis on every state change.
 * On startup, sessions are restored from Redis.
 *
 * Key pattern: {prefix}game:{sessionId}
 */
@Component
@ConditionalOnProperty(name = ["cache.redis.enabled"], havingValue = "true")
@Primary
class RedisGameRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val cardRegistry: CardRegistry,
    private val printingRegistry: com.wingedsheep.engine.registry.PrintingRegistry,
    private val tokenArtRegistry: com.wingedsheep.engine.registry.TokenArtRegistry,
    private val sessionRegistry: SessionRegistry,
    private val redisProperties: RedisProperties,
    // Replay recordings are no longer part of the session blob — they live in the replay store, so a
    // session recovered from Redis picks its recording back up from there.
    private val replayService: com.wingedsheep.gameserver.replay.ReplayService,
    private val qaPersistence: com.wingedsheep.gameserver.persistence.QaRedisPersistence = com.wingedsheep.gameserver.persistence.QaRedisPersistence(),
    private val nativeAdmission: com.wingedsheep.gameserver.lifecycle.NativeGameAdmission = com.wingedsheep.gameserver.lifecycle.NativeGameAdmission(),
) : GameRepository {

    private val logger = LoggerFactory.getLogger(RedisGameRepository::class.java)

    // In-memory cache for active sessions (avoiding repeated deserialization)
    private val sessionCache = ConcurrentHashMap<String, GameSession>()
    private val gameToLobby = ConcurrentHashMap<String, String>()

    private val committedSnapshots = ConcurrentHashMap<String, String>()
    private fun sha(value: String) = com.wingedsheep.gameserver.recording.PrivateGameEvidence.sha256(value.toByteArray(Charsets.UTF_8))
    fun qaLifecycleEnabled(): Boolean = qaPersistence.enabled
    fun validateQaRecoveryInventory() {
        if (!qaPersistence.enabled) return
        try {
            val keys = redisTemplate.keys("${keyPrefix}*") ?: error("Missing QA Redis inventory")
            require(keys.size <= 64 && keys.all { key ->
                key.startsWith("${keyPrefix}game:") || key.startsWith("${keyPrefix}game-lobby:") ||
                    key.startsWith("${keyPrefix}lobby:") || key.startsWith("${keyPrefix}tournament:")
            }) { "Unsupported QA Redis recovery inventory" }
        } catch (failure: Exception) { qaPersistence.fail(); throw failure }
    }

    /** Read actual Redis bytes, rejecting lossy String decoding before any digest claim. */
    private fun readSnapshot(key: String): String? {
        if (!qaPersistence.enabled) return redisTemplate.opsForValue().get(key)
        val raw = redisTemplate.connectionFactory!!.connection.use {
            it.stringCommands().get(key.toByteArray(Charsets.UTF_8))
        } ?: return null
        require(raw.size <= 8 * 1024 * 1024)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val value = decoder.decode(java.nio.ByteBuffer.wrap(raw)).toString()
        require(value.toByteArray(Charsets.UTF_8).contentEquals(raw))
        return value
    }

    fun qaSnapshot(gameId: String): kotlinx.serialization.json.JsonObject {
        check(qaPersistence.healthy())
        require(gameId.matches(Regex("[A-Za-z0-9-]{1,128}")))
        val session = sessionCache[gameId] ?: error("QA game absent")
        return session.withPersistenceLock {
            val current = persistenceJson.encodeToString(PersistentGameSession.serializer(), session.toPersistent(gameToLobby[gameId]))
            val stored = readSnapshot(gameKey(gameId)) ?: error("QA snapshot absent")
            val size = stored.toByteArray(Charsets.UTF_8).size
            require(size <= 8 * 1024 * 1024 && stored == current && sha(stored) == committedSnapshots[gameId])
            val persistent = persistenceJson.decodeFromString(PersistentGameSession.serializer(), stored)
            require(persistent.manualHumanStart && persistent.gameState != null && persistent.playerInfos.any { !it.isAi } &&
                persistent.playerInfos.filter { it.isAi }.all { it.aiControllerSpec != null })
            kotlinx.serialization.json.buildJsonObject {
                put("gameId", kotlinx.serialization.json.JsonPrimitive(gameId))
                put("snapshotSha256", kotlinx.serialization.json.JsonPrimitive(sha(stored)))
                put("snapshotBytes", kotlinx.serialization.json.JsonPrimitive(size))
                put("stateDigest", kotlinx.serialization.json.JsonPrimitive(sha(persistenceJson.encodeToString(com.wingedsheep.engine.state.GameState.serializer(), persistent.gameState))))
                put("manualHumanStart", kotlinx.serialization.json.JsonPrimitive(true))
                put("playerIdentitySha256", kotlinx.serialization.json.JsonPrimitive(sha(persistenceJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.wingedsheep.gameserver.persistence.dto.PersistentPlayerInfo.serializer()), persistent.playerInfos))))
            }
        }
    }

    fun qaPersistenceHealthy(): Boolean {
        if (!qaPersistence.healthy()) return false
        return try {
            redisTemplate.connectionFactory!!.connection.use { require(it.ping() == "PONG") }
            var total = 0L
            for (session in sessionCache.values) {
                // Pregame saves are allowed, but still must exactly match stored bytes.
                session.withPersistenceLock {
                    val stored = readSnapshot(gameKey(session.sessionId)) ?: error("QA snapshot absent")
                    val size = stored.toByteArray(Charsets.UTF_8).size; total += size
                    require(size <= 8 * 1024 * 1024 && total <= 16 * 1024 * 1024 && sha(stored) == committedSnapshots[session.sessionId])
                }
            }
            true
        } catch (_: Exception) { qaPersistence.fail(); false }
    }

    private val keyPrefix = redisProperties.keyPrefix

    private fun gameKey(sessionId: String) = "${keyPrefix}game:$sessionId"
    private fun lobbyLinkKey(sessionId: String) = "${keyPrefix}game-lobby:$sessionId"

    override fun save(gameSession: GameSession) {
        if (qaPersistence.enabled) nativeAdmission.register({ !sessionCache.containsKey(gameSession.sessionId) }) {
            sessionCache[gameSession.sessionId] = gameSession
        } else sessionCache[gameSession.sessionId] = gameSession

        val lobbyId = gameToLobby[gameSession.sessionId]

        try {
            gameSession.withPersistenceLock {
                val json = persistenceJson.encodeToString(PersistentGameSession.serializer(), gameSession.toPersistent(lobbyId))
                if (qaPersistence.enabled) require(json.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024)
                redisTemplate.opsForValue().set(gameKey(gameSession.sessionId), json, Duration.ofMinutes(redisProperties.ttlMinutes))
                if (qaPersistence.enabled) committedSnapshots[gameSession.sessionId] = sha(json)
            }
            logger.debug("Persisted game session ${gameSession.sessionId} to Redis")
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to persist game session ${gameSession.sessionId}", e)
        }
    }

    override fun findById(sessionId: String): GameSession? {
        // Check in-memory cache first
        sessionCache[sessionId]?.let { return it }

        // The QA startup recovery is the only permitted restore admission path.
        if (qaPersistence.enabled) return null

        // Try to load from Redis
        return try {
            val json = redisTemplate.opsForValue().get(gameKey(sessionId)) ?: return null
            val persistent = persistenceJson.decodeFromString(PersistentGameSession.serializer(), json)
            val (session, _) = restoreGameSession(persistent, cardRegistry, printingRegistry, tokenArtRegistry)
            resumeReplayRecording(session)

            // Cache the restored session
            sessionCache[sessionId] = session

            // Restore lobby link if present
            persistent.lobbyId?.let { gameToLobby[sessionId] = it }

            logger.debug("Loaded game session $sessionId from Redis")
            session
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to load game session $sessionId from Redis", e)
            null
        }
    }

    override fun remove(sessionId: String): GameSession? {
        val session = sessionCache.remove(sessionId)
        session?.closeEvidence()
        gameToLobby.remove(sessionId)

        try {
            redisTemplate.delete(gameKey(sessionId))
            redisTemplate.delete(lobbyLinkKey(sessionId))
            logger.debug("Removed game session $sessionId from Redis")
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to remove game session $sessionId from Redis", e)
        }

        return session
    }

    override fun findAll(): Collection<GameSession> {
        // Return cached sessions - for full enumeration, use loadAllFromRedis()
        return sessionCache.values
    }

    override fun linkToLobby(gameSessionId: String, lobbyId: String) {
        gameToLobby[gameSessionId] = lobbyId

        try {
            redisTemplate.opsForValue().set(
                lobbyLinkKey(gameSessionId),
                lobbyId,
                Duration.ofMinutes(redisProperties.ttlMinutes)
            )
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to persist lobby link for game $gameSessionId", e)
        }

        // Re-save the game session to include the lobby link
        sessionCache[gameSessionId]?.let { save(it) }
    }

    override fun getLobbyForGame(gameSessionId: String): String? {
        gameToLobby[gameSessionId]?.let { return it }

        return try {
            redisTemplate.opsForValue().get(lobbyLinkKey(gameSessionId))?.also {
                gameToLobby[gameSessionId] = it
            }
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to get lobby link for game $gameSessionId", e)
            null
        }
    }

    override fun removeLobbyLink(gameSessionId: String): String? {
        val lobbyId = gameToLobby.remove(gameSessionId)

        try {
            redisTemplate.delete(lobbyLinkKey(gameSessionId))
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to remove lobby link for game $gameSessionId", e)
        }

        return lobbyId
    }

    /**
     * Pick a recovered session's replay recording back up from the replay store.
     *
     * The store's copy can be a few actions behind the recovered live state (it is flushed on a
     * timer, not per action), so [GameSession.restoreReplayRecording] compares the fingerprint
     * written with the flush against the recovered position and declines to continue when they
     * disagree — a short honest replay beats a long fictional one.
     */
    private fun resumeReplayRecording(session: GameSession) {
        val stored = replayService.findStored(session.sessionId) ?: return
        if (stored.status != com.wingedsheep.gameserver.replay.ReplayStatus.IN_PROGRESS) return
        val resumed = session.restoreReplayRecording(stored.replay, stored.resumeFingerprint)
        logger.info(
            "Replay recording for ${session.sessionId}: ${if (resumed) "resumed" else "stopped"} " +
                "at ${stored.replay.actions.size} recorded actions"
        )
        // Nothing more will ever be appended, so close the partial record out now rather than
        // leaving it mid-write for the lifetime of a game that is still being played.
        if (!resumed) replayService.finalizePartial(session.sessionId)
    }

    /**
     * Load all game sessions from Redis.
     * Called during startup recovery.
     *
     * @return List of (GameSession, List<PlayerIdentity>) pairs
     */
    fun loadAllFromRedis(): List<Pair<GameSession, List<PlayerIdentity>>> {
        val results = mutableListOf<Pair<GameSession, List<PlayerIdentity>>>()

        try {
            val keys = redisTemplate.keys("${keyPrefix}game:*") ?: error("Missing Redis key observation")
            qaPersistence.recoveryKeys(keys.size)
            var total = 0L

            for (key in keys) {
                try {
                    val json = readSnapshot(key) ?: if (qaPersistence.enabled) error("QA snapshot disappeared") else continue
                    if (qaPersistence.enabled) {
                        val bytes = json.toByteArray(Charsets.UTF_8).size
                        total += bytes
                        require(bytes <= 8 * 1024 * 1024 && total <= 16 * 1024 * 1024)
                    }
                    qaPersistence.recoveryValue(json)
                    val persistent = persistenceJson.decodeFromString(PersistentGameSession.serializer(), json)
                    val (session, identities) = restoreGameSession(persistent, cardRegistry, printingRegistry, tokenArtRegistry)
                    resumeReplayRecording(session)

                    if (qaPersistence.enabled) {
                        require(key == gameKey(session.sessionId))
                        val current = session.withPersistenceLock { persistenceJson.encodeToString(PersistentGameSession.serializer(), session.toPersistent(persistent.lobbyId)) }
                        require(current == json) { "QA Redis restored snapshot changed" }
                        committedSnapshots[session.sessionId] = sha(json)
                    }
                    sessionCache[session.sessionId] = session
                    persistent.lobbyId?.let { gameToLobby[session.sessionId] = it }

                    results.add(session to identities)
                    logger.info("Restored game session ${session.sessionId} from Redis")
                } catch (e: Exception) {
                    qaPersistence.fail()
                    logger.error("Failed to restore game session from key $key", e)
                }
            }
        } catch (e: Exception) {
            qaPersistence.fail()
            logger.error("Failed to enumerate game sessions from Redis", e)
        }

        return results
    }
}
