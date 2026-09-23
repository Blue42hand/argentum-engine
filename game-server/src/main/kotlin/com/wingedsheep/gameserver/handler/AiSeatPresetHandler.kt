package com.wingedsheep.gameserver.handler

import com.wingedsheep.gameserver.ai.TournamentAiSeatPresetService
import com.wingedsheep.gameserver.lobby.LobbyState
import com.wingedsheep.gameserver.lobby.TournamentFormat
import com.wingedsheep.gameserver.protocol.ClientMessage
import com.wingedsheep.gameserver.protocol.ErrorCode
import com.wingedsheep.gameserver.protocol.SetLobbyAiController
import com.wingedsheep.sdk.model.EntityId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession

/**
 * WebSocket-facing host mutations for coherent tournament/FFA AI seat configuration.
 *
 * Controller/profile semantics remain provider-owned. This handler only enforces host authority,
 * delegates atomic controller+provider-deck application to [TournamentAiSeatPresetService], and
 * prevents the legacy independent deck picker from splitting a provider-owned preset.
 */
@Component
class AiSeatPresetHandler(
    private val ctx: LobbySharedContext,
    private val presetService: TournamentAiSeatPresetService,
    private val lobbyHandler: LobbyHandler,
) {
    private val logger = LoggerFactory.getLogger(AiSeatPresetHandler::class.java)

    fun handleSetController(session: WebSocketSession, message: SetLobbyAiController) {
        val (identity, lobby) = ctx.getIdentityAndLobby(session) ?: return
        if (!lobby.isHost(identity.playerId)) {
            ctx.sender.sendError(session, ErrorCode.INVALID_ACTION, "Only the host can choose an AI controller")
            return
        }

        val aiPlayerId = EntityId(message.playerId)
        when (val result = presetService.applyPremadePreset(lobby, aiPlayerId, message.spec)) {
            is TournamentAiSeatPresetService.ApplyResult.Rejected -> {
                ctx.sender.sendError(session, ErrorCode.INVALID_ACTION, result.message)
            }

            is TournamentAiSeatPresetService.ApplyResult.Applied -> {
                ctx.lobbyRepository.saveLobby(lobby)
                logger.info(
                    "Host set AI {} controller to {} / {} in lobby {}",
                    aiPlayerId.value,
                    result.controllerSpec?.mode ?: "default",
                    result.controllerSpec?.profileId ?: "default",
                    lobby.lobbyId,
                )
                ctx.broadcastLobbyUpdate(lobby)
            }
        }
    }

    /**
     * Guard the existing independent deck picker when a provider profile owns this seat's deck.
     * All unrelated validation and mutation stays in [LobbyHandler] so there is one legacy deck path.
     */
    fun handleSetDeck(session: WebSocketSession, message: ClientMessage.SetLobbyAiDeck) {
        val pair = ctx.getIdentityAndLobby(session) ?: return
        val (identity, lobby) = pair

        // Preserve the legacy handler's authority/format diagnostics before inspecting seat config.
        if (!lobby.isHost(identity.playerId) ||
            lobby.state != LobbyState.WAITING_FOR_PLAYERS ||
            lobby.format != TournamentFormat.PREMADE_DECKS
        ) {
            lobbyHandler.handle(session, message)
            return
        }

        val aiPlayerId = EntityId(message.playerId)
        val playerState = lobby.players[aiPlayerId]
        if (playerState == null || !ctx.aiGameManager.isAiPlayer(aiPlayerId)) {
            lobbyHandler.handle(session, message)
            return
        }

        val providerOwnedDeck = try {
            presetService.providerOwnedDeck(playerState.identity.aiControllerSpec)
        } catch (error: IllegalArgumentException) {
            ctx.sender.sendError(
                session,
                ErrorCode.INVALID_ACTION,
                error.message ?: "The selected AI controller is unavailable",
            )
            return
        }

        if (providerOwnedDeck != null) {
            ctx.sender.sendError(
                session,
                ErrorCode.INVALID_ACTION,
                "This AI controller profile owns the seat deck; change the controller profile instead",
            )
            return
        }

        lobbyHandler.handle(session, message)
    }
}
