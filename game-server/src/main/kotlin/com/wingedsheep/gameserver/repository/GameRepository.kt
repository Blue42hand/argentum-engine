package com.wingedsheep.gameserver.repository

import com.wingedsheep.gameserver.session.GameSession
import com.wingedsheep.gameserver.lifecycle.NativeGameAdmission
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface GameRepository {
    fun save(gameSession: GameSession)
    fun findById(sessionId: String): GameSession?
    fun remove(sessionId: String): GameSession?
    fun findAll(): Collection<GameSession>
    fun linkToLobby(gameSessionId: String, lobbyId: String)
    fun getLobbyForGame(gameSessionId: String): String?
    fun removeLobbyLink(gameSessionId: String): String?
}

@Component
@ConditionalOnProperty(name = ["cache.redis.enabled"], havingValue = "false", matchIfMissing = true)
class InMemoryGameRepository @Autowired constructor(
    private val nativeAdmission: NativeGameAdmission,
) : GameRepository {
    constructor() : this(NativeGameAdmission())

    private val gameSessions = ConcurrentHashMap<String, GameSession>()
    private val gameToLobby = ConcurrentHashMap<String, String>()

    override fun save(gameSession: GameSession) {
        nativeAdmission.register({ !gameSessions.containsKey(gameSession.sessionId) }) {
            gameSessions[gameSession.sessionId] = gameSession
        }
    }

    override fun findById(sessionId: String): GameSession? = gameSessions[sessionId]

    override fun remove(sessionId: String): GameSession? = gameSessions.remove(sessionId)?.also { it.closeEvidence() }

    override fun findAll(): Collection<GameSession> = gameSessions.values

    override fun linkToLobby(gameSessionId: String, lobbyId: String) {
        gameToLobby[gameSessionId] = lobbyId
    }

    override fun getLobbyForGame(gameSessionId: String): String? = gameToLobby[gameSessionId]

    override fun removeLobbyLink(gameSessionId: String): String? = gameToLobby.remove(gameSessionId)
}
