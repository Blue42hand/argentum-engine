package com.wingedsheep.gameserver.matchmaking

import com.wingedsheep.gameserver.handler.MessageSender
import com.wingedsheep.gameserver.handler.QuickGameLobbyHandler
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.ErrorCode
import com.wingedsheep.gameserver.protocol.ServerMessage
import com.wingedsheep.gameserver.ranking.Elo
import com.wingedsheep.gameserver.ranking.Ranked
import com.wingedsheep.gameserver.repository.GameRepository
import com.wingedsheep.gameserver.repository.LobbyRepository
import com.wingedsheep.gameserver.session.PlayerIdentity
import com.wingedsheep.gameserver.session.SessionRegistry
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.core.GameRules
import com.wingedsheep.sdk.model.EntityId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/**
 * "Find opponent": lets players who don't know each other get a game. Owns a [MatchmakingQueue] and
 * connects it to the outside world — reads who is asking from the socket, looks up ranked ratings,
 * ticks the queue once a second, turns its events into [ServerMessage]s, and hands a confirmed pair
 * to [QuickGameLobbyHandler.createMatchmadeLobby]. From the lobby on it is an ordinary quick game.
 *
 * Every queue call happens under [lock]; messages are sent and lobbies created after it is released.
 */
@Component
class MatchmakingService(
    private val sessionRegistry: SessionRegistry,
    private val sender: MessageSender,
    private val quickGameLobbyHandler: QuickGameLobbyHandler,
    private val quickGameLobbies: QuickGameLobbyRepository,
    private val tournamentLobbies: LobbyRepository,
    private val gameRepository: GameRepository,
    private val ratingLookup: RatingLookup,
) {
    private val logger = LoggerFactory.getLogger(MatchmakingService::class.java)
    private val queue = MatchmakingQueue()
    private val lock = Any()
    private var lastBroadcastCounts: Map<QueueKey, Int> = emptyMap()

    fun handle(session: WebSocketSession, message: ClientMessage) {
        val identity = sessionRegistry.getIdentityByWsId(session.id) ?: run {
            sender.sendError(session, ErrorCode.NOT_CONNECTED, "Not connected"); return
        }
        when (message) {
            is ClientMessage.JoinMatchmaking -> join(session, identity, message.format, message.ranked)
            is ClientMessage.LeaveMatchmaking -> dispatch(synchronized(lock) { queue.leave(identity.playerId) })
            is ClientMessage.RespondToMatch -> dispatch(
                synchronized(lock) { queue.respond(identity.playerId, message.matchId, message.accept) },
            )
            else -> {}
        }
    }

    /** Searching players per queue, for the home screen's first paint (later changes are pushed). */
    fun queueCounts(): List<ServerMessage.MatchmakingQueueCount> =
        toCounts(synchronized(lock) { queue.counts() })

    @Scheduled(fixedDelay = TICK_MS, initialDelay = TICK_MS)
    fun tick() {
        val available = sessionRegistry.getAllIdentities()
            .filter { !it.isAi && it.isConnected && !isBusyElsewhere(it) }
            .mapTo(HashSet()) { it.playerId }
        dispatch(synchronized(lock) { queue.tick(System.currentTimeMillis()) { it in available } })
    }

    private fun join(session: WebSocketSession, identity: PlayerIdentity, format: DeckFormat?, ranked: Boolean) {
        val userId = identity.userId
        if (ranked && userId == null) {
            sender.sendError(session, ErrorCode.INVALID_ACTION, "Sign in to play ranked")
            return
        }
        if (isBusyElsewhere(identity)) {
            sender.sendError(session, ErrorCode.INVALID_ACTION, "Leave your current lobby or game before searching")
            return
        }
        val key = QueueKey(format, ranked)
        val rating = if (ranked && userId != null) {
            ratingLookup.ratingOf(userId, Ranked.modeForQuickGame(GameRules.inferred(false, format), format, false))
        } else {
            Elo.STARTING_RATING
        }
        val events = synchronized(lock) {
            if (queue.pendingMatchOf(identity.playerId) != null) {
                null
            } else {
                // Re-sending the same queue keeps your place; switching queues starts the clock over.
                val joinedAt = queue.entryOf(identity.playerId)?.takeIf { it.key == key }?.joinedAt
                    ?: System.currentTimeMillis()
                queue.join(QueueEntry(identity.playerId, userId, identity.playerName, key, rating, joinedAt))
            }
        }
        if (events == null) {
            sender.sendError(session, ErrorCode.INVALID_ACTION, "Answer the match you were offered first")
            return
        }
        dispatch(events)
    }

    /** In a lobby or an unfinished game — somewhere a match would pull them out of. */
    private fun isBusyElsewhere(identity: PlayerIdentity): Boolean =
        identity.currentQuickGameLobbyId?.let { quickGameLobbies.findById(it) } != null ||
            identity.currentLobbyId?.let { tournamentLobbies.findLobbyById(it) } != null ||
            identity.currentGameSessionId?.let { gameRepository.findById(it) }?.isGameOver() == false

    private fun dispatch(events: List<MatchmakingEvent>) {
        for (event in events) {
            when (event) {
                is MatchmakingEvent.Searching -> sendTo(
                    event.entry.playerId,
                    ServerMessage.MatchmakingStatus(
                        searching = true,
                        format = event.entry.key.format,
                        ranked = event.entry.key.ranked,
                        searchingSince = event.entry.joinedAt,
                        notice = event.notice,
                    ),
                )
                is MatchmakingEvent.Idle ->
                    sendTo(event.playerId, ServerMessage.MatchmakingStatus(searching = false, notice = event.notice))
                is MatchmakingEvent.Found -> event.match.players.forEach { sendMatchFound(event.match, it.playerId) }
                is MatchmakingEvent.Accepted -> sendMatchFound(event.match, event.playerId)
                is MatchmakingEvent.Confirmed -> seat(event.match)
            }
        }
        broadcastCountsIfChanged()
    }

    private fun sendMatchFound(match: PendingMatch, playerId: EntityId) {
        val opponent = match.opponentOf(playerId)
        val key = opponent.key
        sendTo(
            playerId,
            ServerMessage.MatchFound(
                matchId = match.matchId,
                opponentName = opponent.playerName,
                format = key.format,
                ranked = key.ranked,
                opponentRating = if (key.ranked) opponent.rating.toInt() else null,
                acceptWindowMs = (match.expiresAt - System.currentTimeMillis()).coerceAtLeast(0),
                youAccepted = playerId in match.accepted,
            ),
        )
    }

    /**
     * Both accepted: make their lobby. A player who vanished or got busy in the accept window is
     * dropped, and the other goes back in the queue at their original place.
     */
    private fun seat(match: PendingMatch) {
        val identities = match.players.associate { entry ->
            entry.playerId to sessionRegistry.getAllIdentities().firstOrNull { it.playerId == entry.playerId }
        }
        val unavailable = match.players.filter { entry ->
            val identity = identities[entry.playerId]
            identity == null || !identity.isConnected || isBusyElsewhere(identity)
        }
        val seated = unavailable.isEmpty() && quickGameLobbyHandler.createMatchmadeLobby(
            players = match.players.map { it.playerId to (identities[it.playerId]?.playerName ?: it.playerName) },
            format = match.first.key.format,
            ranked = match.first.key.ranked,
        )
        if (seated) {
            match.players.forEach { sendTo(it.playerId, ServerMessage.MatchmakingStatus(searching = false)) }
            return
        }
        logger.info("Match ${match.matchId}: could not seat ${unavailable.map { it.playerName }}; requeueing the rest")
        val requeue = match.players.filterNot { it in unavailable }
        val events = synchronized(lock) {
            requeue.flatMap { queue.requeue(it, "Your opponent is no longer available — back in the queue") }
        }
        unavailable.forEach { sendTo(it.playerId, ServerMessage.MatchmakingStatus(searching = false)) }
        dispatch(events)
    }

    private fun sendTo(playerId: EntityId, message: ServerMessage) {
        val ws = sessionRegistry.getAllIdentities().firstOrNull { it.playerId == playerId }?.webSocketSession
        if (ws != null && ws.isOpen) sender.send(ws, message)
    }

    private fun broadcastCountsIfChanged() {
        val counts = synchronized(lock) {
            val current = queue.counts()
            if (current == lastBroadcastCounts) return
            lastBroadcastCounts = current
            current
        }
        val message = ServerMessage.MatchmakingQueues(toCounts(counts))
        sessionRegistry.getAllIdentities().forEach { identity ->
            if (identity.isAi) return@forEach
            val ws = identity.webSocketSession
            if (ws != null && ws.isOpen) sender.send(ws, message)
        }
    }

    private fun toCounts(counts: Map<QueueKey, Int>) = counts.map { (key, n) ->
        ServerMessage.MatchmakingQueueCount(format = key.format, ranked = key.ranked, searching = n)
    }

    companion object {
        const val TICK_MS: Long = 1_000
    }
}
